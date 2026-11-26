package br.com.timachado.neromusic

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

internal data class NeroUpdate(val code:Int,val name:String,val url:String,val notes:String)
internal fun neroParseUpdate(body:String,installed:Int):NeroUpdate?{
    val row=JSONObject(body)
    if(row.optString("applicationId")!="br.com.timachado.neromusic"||row.optString("channel")!="stable"||!row.optBoolean("published",false))return null
    val code=row.optInt("versionCode",0)
    val version=row.optString("versionName")
    val url=row.optString("downloadUrl")
    if(code<=installed||!version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))||
        !row.optString("sha256").matches(Regex("[a-f0-9]{64}"))||
        url!="https://github.com/timachado/NERO-m-sic/releases/download/v"+version+"/NERO-Music.apk")return null
    return NeroUpdate(code,version,url,row.optString("releaseNotes").take(1200))
}
internal object NeroAppUpdates{
    private const val feed="https://raw.githubusercontent.com/timachado/NERO-m-sic/main/updates/stable.json"
    private const val channel="nero_app_updates"
    private fun prefs(c:Context)=c.getSharedPreferences("nero_official_updates",Context.MODE_PRIVATE)
    fun schedule(c:Context){
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("nero_official_update",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NeroUpdateWorker>(12,TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    suspend fun fetch():NeroUpdate?=withContext(Dispatchers.IO){
        val conn=URL(feed).openConnection() as HttpURLConnection
        conn.connectTimeout=8000;conn.readTimeout=8000;conn.instanceFollowRedirects=false
        try{
            if(conn.responseCode==404)return@withContext null
            check(conn.responseCode==200)
            val output=java.io.ByteArrayOutputStream()
            conn.inputStream.use{input->
                val buffer=ByteArray(4096)
                while(true){val n=input.read(buffer);if(n<0)break;check(output.size()+n<=16384);output.write(buffer,0,n)}
            }
            neroParseUpdate(output.toString("UTF-8"),BuildConfig.VERSION_CODE)
        }finally{conn.disconnect()}
    }
    @Synchronized fun notify(c:Context,u:NeroUpdate){
        val p=prefs(c)
        if(u.code<=p.getInt("notified",0)||!NeroRadarAlerts.permitted(c))return
        val manager=c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel,"Atualizações do NERO",NotificationManager.IMPORTANCE_DEFAULT))
        val pending=PendingIntent.getActivity(c,7820,Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        manager.notify(7820,NotificationCompat.Builder(c,channel).setSmallIcon(R.drawable.ic_nero_notification)
            .setContentTitle("NERO Music "+u.name+" disponível").setContentText("Abra o NERO para conferir a atualização oficial.")
            .setAutoCancel(true).setContentIntent(pending).build())
        p.edit().putInt("notified",u.code).commit()
    }
    fun dismissed(c:Context,code:Int)=prefs(c).getInt("dismissed",0)>=code
    fun dismiss(c:Context,code:Int){prefs(c).edit().putInt("dismissed",code).apply()}
}
class NeroUpdateWorker(c:Context,p:WorkerParameters):CoroutineWorker(c,p){
    override suspend fun doWork():Result=try{
        NeroAppUpdates.fetch()?.let{NeroAppUpdates.notify(applicationContext,it)}
        Result.success()
    }catch(e:CancellationException){throw e}catch(_:Exception){Result.retry()}
}
@Composable
internal fun NeroUpdateNotice(){
    val context=LocalContext.current
    val resume=rememberNeroCommerceResume()
    var update by remember{mutableStateOf<NeroUpdate?>(null)}
    LaunchedEffect(resume){
        NeroAppUpdates.schedule(context)
        try{update=NeroAppUpdates.fetch()?.takeUnless{NeroAppUpdates.dismissed(context,it.code)}}
        catch(e:CancellationException){throw e}catch(_:Exception){}
    }
    update?.let{u->
        AlertDialog(onDismissRequest={NeroAppUpdates.dismiss(context,u.code);update=null},
            title={Text("Nova versão: NERO Music "+u.name)},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Uma atualização oficial está disponível.")
                if(u.notes.isNotBlank())Text(u.notes,maxLines=10)
            }},
            confirmButton={TextButton(onClick={
                try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(u.url)));NeroAppUpdates.dismiss(context,u.code);update=null}catch(_:Exception){}
            }){Text("Baixar atualização")}},
            dismissButton={TextButton(onClick={NeroAppUpdates.dismiss(context,u.code);update=null}){Text("Agora não")}})
    }
}
