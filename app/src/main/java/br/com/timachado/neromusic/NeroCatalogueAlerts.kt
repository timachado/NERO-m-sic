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
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Public catalogue discoveries, not listening history or verified release dates. */
internal object NeroCatalogueAlerts {
    private const val work="nero_catalogue_discoveries_v1"
    private const val channel="nero_catalogue_discoveries"
    private const val notificationId=7831
    private fun prefs(c:Context)=c.getSharedPreferences("nero_catalogue_alerts_v1",Context.MODE_PRIVATE)
    fun enabled(c:Context)=prefs(c).getBoolean("enabled",false)
    fun setEnabled(c:Context,value:Boolean){
        val p=prefs(c)
        if(!value || !enabled(c)){
            p.edit().putBoolean("enabled",value).remove("seen").remove("last_day").commit()
        }
        val manager=WorkManager.getInstance(c)
        if(!value){
            manager.cancelUniqueWork(work)
            c.getSystemService(NotificationManager::class.java).cancel(notificationId)
        }else{
            val request=PeriodicWorkRequestBuilder<NeroCatalogueWorker>(24,TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            manager.enqueueUniquePeriodicWork(work,ExistingPeriodicWorkPolicy.KEEP,request)
        }
    }
    fun process(c:Context,ids:List<String>,day:String){
        if(!enabled(c)||ids.isEmpty()||!NeroRadarAlerts.permitted(c))return
        val p=prefs(c)
        if(day==p.getString("last_day",null))return
        val seen=p.getStringSet("seen",null)
        val unique=ids.distinct().take(30)
        val fresh=seen?.let{old->unique.filterNot{it in old}}.orEmpty()
        if(fresh.isNotEmpty()){
            val manager=c.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(channel,"Descobertas e novidades",NotificationManager.IMPORTANCE_DEFAULT).apply{
                description="Novos itens encontrados na seleção musical do NERO"
            })
            if(!enabled(c)||!NeroRadarAlerts.permitted(c))return
            if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
            val intent=Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending=PendingIntent.getActivity(c,notificationId,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(notificationId,NotificationCompat.Builder(c,channel)
                .setSmallIcon(R.drawable.ic_nero_notification)
                .setContentTitle("Tem novidade na grade NERO")
                .setContentText("Descubra os novos destaques da seleção musical.")
                .setAutoCancel(true).setContentIntent(pending).build())
        }
        if(enabled(c))p.edit().putString("last_day",day).putStringSet("seen",(unique+seen.orEmpty()).distinct().take(512).toSet()).commit()
    }
}

class NeroCatalogueWorker(c:Context,p:WorkerParameters):CoroutineWorker(c,p){
    override suspend fun doWork():Result{
        if(!NeroCatalogueAlerts.enabled(applicationContext)||!NeroRadarAlerts.permitted(applicationContext))return Result.success()
        return try{
            val snapshot=DevCatalog.releaseSnapshot(30,null)
            val ids=snapshot.entries.map{it.id}
            NeroCatalogueAlerts.process(applicationContext,ids,snapshot.day.toString())
            Result.success()
        }catch(e:CancellationException){throw e}catch(_:Exception){Result.retry()}
    }
}
