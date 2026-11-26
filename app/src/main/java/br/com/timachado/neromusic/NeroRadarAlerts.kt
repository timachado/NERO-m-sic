package br.com.timachado.neromusic

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

internal fun newRadarIds(current:List<String>,seen:Set<String>,initialized:Boolean):List<String> =
    if(initialized)current.distinct().filterNot{it in seen}else emptyList()

internal object NeroRadarAlerts{
    private const val work="nero_radar_periodic"
    private const val channel="nero_radar_releases"
    fun preferences(context:Context,owner:String?)=context.getSharedPreferences("nero_radar_"+(owner?.let{NeroAccountLibraryStore.fileName(it)}?:"guest"),Context.MODE_PRIVATE)
    fun permitted(context:Context):Boolean =
        (Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun configure(context:Context,owner:String?,enabled:Boolean,artists:List<String>,genres:List<String>){
        val prefs=preferences(context,owner)
        val active=enabled&&permitted(context)
        prefs.edit().putStringSet("alertArtists",artists.take(6).toSet()).putStringSet("alertGenres",genres.take(2).toSet()).apply()
        val manager=WorkManager.getInstance(context)
        if(!active){manager.cancelUniqueWork(work);return}
        val request=PeriodicWorkRequestBuilder<NeroRadarWorker>(24,TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("owner" to (owner?:"guest")))
            .build()
        manager.enqueueUniquePeriodicWork(work,ExistingPeriodicWorkPolicy.UPDATE,request)
    }
    fun stop(context:Context){
        WorkManager.getInstance(context).cancelUniqueWork(work)
        NotificationManagerCompat.from(context).cancel(7819)
    }
    fun publish(context:Context,rows:List<CatalogEntry>){
        if(rows.isEmpty()||!permitted(context))return
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel,"NERO Radar",NotificationManager.IMPORTANCE_DEFAULT).apply{description="Lançamentos dos seus artistas e gêneros favoritos"})
        val first=rows.first()
        val message=if(rows.size==1)first.title+" · "+displayArtistName(first.credit) else "${rows.size} lançamentos no seu Radar. Confira "+displayArtistName(first.credit)+" e mais."
        val intent=Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending=PendingIntent.getActivity(context,7819,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.ic_nero_notification)
            .setContentTitle("NERO Radar · Novidades para você").setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)).setAutoCancel(true).setContentIntent(pending).build()
        // Permission can change between the check and the framework call.
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED||Build.VERSION.SDK_INT<33){
            manager.notify(7819,notification)
        }
    }
}
class NeroRadarWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result{
        val context=applicationContext
        val owner=inputData.getString("owner")?.let{if(it=="guest")null else it}
        if(!NeroSupabase.matchesStoredRadarOwner(context,owner))return Result.success()
        val prefs=NeroRadarAlerts.preferences(context,owner)
        fun allowed()=prefs.getBoolean("enabled",false)&&prefs.getBoolean("alerts",false)&&NeroRadarAlerts.permitted(context)
        if(!allowed())return Result.success()
        val artists=prefs.getStringSet("alertArtists",emptySet()).orEmpty().take(6)
        val genres=prefs.getStringSet("alertGenres",emptySet()).orEmpty().take(2)
        val rows=mutableListOf<CatalogEntry>()
        var completed=0
        try{
            for(artist in artists){
                if(!allowed()||!NeroSupabase.matchesStoredRadarOwner(context,owner))return Result.success()
                try{rows+=radarWeekly(DevCatalog.releaseSnapshot(7,null,artist=artist).entries,LocalDate.now(),listOf(artist));completed++}
                catch(e:CancellationException){throw e}catch(_:Exception){}
            }
            for(genre in genres){
                if(!allowed()||!NeroSupabase.matchesStoredRadarOwner(context,owner))return Result.success()
                try{rows+=DevCatalog.releaseSnapshot(7,genre).entries;completed++}
                catch(e:CancellationException){throw e}catch(_:Exception){}
            }
            if(completed==0)return if(artists.isEmpty()&&genres.isEmpty())Result.success() else Result.retry()
            if(!allowed()||!NeroSupabase.matchesStoredRadarOwner(context,owner))return Result.success()
            val fresh=radarWeekly(rows,LocalDate.now())
            val seen=prefs.getStringSet("alertSeen",emptySet()).orEmpty()
            val ids=newRadarIds(fresh.map{it.id},seen,prefs.getBoolean("alertInitialized",false)).toSet()
            val unseen=fresh.filter{it.id in ids}
            if(unseen.isNotEmpty())NeroRadarAlerts.publish(context,unseen)
            prefs.edit().putBoolean("alertInitialized",true).putStringSet("alertSeen",(fresh.map{it.id}+seen).distinct().take(512).toSet()).apply()
            return Result.success()
        }catch(e:CancellationException){throw e}catch(_:Exception){return Result.retry()}
    }
}
