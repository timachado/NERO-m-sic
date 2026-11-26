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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import kotlinx.coroutines.launch
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
    fun notificationsEnabled(c:Context)=prefs(c).getBoolean("notifications",true)
    fun setNotifications(c:Context,enabled:Boolean){prefs(c).edit().putBoolean("notifications",enabled).apply();NeroUpdatePush.sync(c);if(!enabled)c.getSystemService(NotificationManager::class.java).cancel(7820)}
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
        if(!notificationsEnabled(c)||u.code<=p.getInt("notified",0)||!NeroRadarAlerts.permitted(c))return
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
    override suspend fun doWork():Result{
        return try{
        val expected=inputData.getInt("expectedCode",0)
        if(expected>BuildConfig.VERSION_CODE&&!NeroAppUpdates.notificationsEnabled(applicationContext))return Result.success()
        val update=NeroAppUpdates.fetch()
        if(expected>BuildConfig.VERSION_CODE&&(update==null||update.code<expected)){
            if(runAttemptCount<3)Result.retry() else Result.success()
        }else{update?.let{NeroAppUpdates.notify(applicationContext,it)};Result.success()}
    }catch(e:CancellationException){throw e}catch(_:Exception){Result.retry()}
    }
}
@Composable
internal fun NeroUpdateNotice(){
    val context=LocalContext.current
    val resume=rememberNeroCommerceResume()
    var update by remember{mutableStateOf<NeroUpdate?>(null)}
    LaunchedEffect(resume){
        NeroAppUpdates.schedule(context)
        NeroUpdatePush.sync(context)
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

@Composable
internal fun NeroUpdateNotificationPreference(){
    val context=LocalContext.current
    val resume=rememberNeroCommerceResume()
    var enabled by remember{mutableStateOf(NeroAppUpdates.notificationsEnabled(context))}
    val scope=rememberCoroutineScope()
    var checking by remember{mutableStateOf(false)}
    var result by remember{mutableStateOf<NeroUpdate?>(null)}
    var status by remember{mutableStateOf<String?>(null)}
    var allowed by remember(resume){mutableStateOf(NeroRadarAlerts.permitted(context))}
    val permission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){
        allowed=NeroRadarAlerts.permitted(context)
        NeroUpdatePush.sync(context)
    }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
            Text("Avisos de atualização do NERO",modifier=Modifier.weight(1f))
            Switch(checked=enabled,onCheckedChange={
                enabled=it;NeroAppUpdates.setNotifications(context,it)
                if(it&&Build.VERSION.SDK_INT>=33&&!allowed)permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
        }
        OutlinedButton(enabled=!checking,onClick={
            checking=true;status=null;result=null
            scope.launch{
                try{result=NeroAppUpdates.fetch();status=if(result==null)"Nenhuma atualização oficial disponível." else "Nova versão: "+result!!.name}
                catch(e:CancellationException){throw e}catch(_:Exception){status="Não foi possível consultar atualizações. Tente novamente."}
                finally{checking=false}
            }
        },modifier=Modifier.fillMaxWidth()){Text(if(checking)"Consultando…" else "Verificar atualizações")}
        status?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
        result?.let{update->
            TextButton(onClick={try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(update.url)))}catch(_:Exception){status="Não foi possível abrir o download."}}){Text("Baixar NERO Music "+update.name)}
        }
        if(BuildConfig.NERO_FCM_DIAGNOSTIC)NeroFcmDiagnosticPanel()
        if(enabled&&!allowed){
            Text("Autorize notificações do Android para receber avisos com o app fechado.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={
                if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))
            }){Text("Permitir notificações")}
        }
    }
}
