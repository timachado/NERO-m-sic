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
        val active=enabled
        prefs.edit().putStringSet("alertArtists",artists.take(6).toSet()).putStringSet("alertGenres",genres.take(2).toSet()).apply()
        val manager=WorkManager.getInstance(context)
        if(!active){manager.cancelUniqueWork(work);NotificationManagerCompat.from(context).cancel(7819);return}
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
        manager.createNotificationChannel(NotificationChannel(channel,"NERO Radar",NotificationManager.IMPORTANCE_DEFAULT).apply{description="Descobertas dos seus artistas e gêneros favoritos"})
        val first=rows.first()
        val message=if(rows.size==1)first.title+" · "+displayArtistName(first.credit) else "${rows.size} descobertas no seu Radar. Confira "+displayArtistName(first.credit)+" e mais."
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
        fun allowed()=prefs.getBoolean("enabled",false)
        if(!allowed())return Result.success()
        val artists=prefs.getStringSet("alertArtists",emptySet()).orEmpty().take(6)
        val genres=prefs.getStringSet("alertGenres",emptySet()).orEmpty().take(2)
        val rows=mutableListOf<CatalogEntry>()
        var completed=0
        try{
            for(artist in artists){
                if(!allowed()||!NeroSupabase.matchesStoredRadarOwner(context,owner))return Result.success()
                try{rows+=radarDiscoveries(DevCatalog.releaseSnapshot(7,null,artist=artist).entries,listOf(artist));completed++}
                catch(e:CancellationException){throw e}catch(_:Exception){}
            }
            for(genre in genres){
                if(!allowed()||!NeroSupabase.matchesStoredRadarOwner(context,owner))return Result.success()
                try{rows+=DevCatalog.releaseSnapshot(7,genre).entries;completed++}
                catch(e:CancellationException){throw e}catch(_:Exception){}
            }
            // Do not initialize a partial baseline: a failed artist query would otherwise
            // generate false "new release" alerts on the next successful scan.
            if(completed<artists.size+genres.size)return Result.retry()
            if(completed==0)return Result.success()
            if(!allowed()||!NeroSupabase.matchesStoredRadarOwner(context,owner))return Result.success()
            val fresh=radarDiscoveries(rows)
            val scopeKey=artists.sorted().joinToString("|")+"::"+genres.sorted().joinToString("|")
            val sameScope=prefs.getString("nativeAlertScope",null)==scopeKey
            val seen=if(sameScope)prefs.getStringSet("nativeAlertSeen",emptySet()).orEmpty() else emptySet()
            val ids=newRadarIds(fresh.map{it.id},seen,sameScope&&prefs.getBoolean("nativeAlertInitialized",false)).toSet()
            val unseen=fresh.filter{it.id in ids}
            if(unseen.isNotEmpty()){
                val previous=runCatching{org.json.JSONArray(prefs.getString("nativeInbox","[]"))}.getOrDefault(org.json.JSONArray())
                val inbox=org.json.JSONArray()
                unseen.take(30).forEach{row->inbox.put(org.json.JSONObject().put("id",row.id).put("title",row.title.take(200)).put("credit",displayArtistName(row.credit).take(200)).put("search",row.searchText.take(300)))}
                val keys=unseen.map{it.id}.toSet()
                for(i in 0 until previous.length()){
                    val item=previous.optJSONObject(i)?:continue
                    if(inbox.length()>=30)break
                    if(item.optString("id") !in keys)inbox.put(item)
                }
                prefs.edit().putString("nativeInbox",inbox.toString()).apply()
                if(prefs.getBoolean("alerts",false))NeroRadarAlerts.publish(context,unseen)
            }
            prefs.edit().putString("nativeAlertScope",scopeKey).putBoolean("nativeAlertInitialized",true).putStringSet("nativeAlertSeen",(fresh.map{it.id}+seen).distinct().take(512).toSet()).apply()
            return Result.success()
        }catch(e:CancellationException){throw e}catch(_:Exception){return Result.retry()}
    }
}
