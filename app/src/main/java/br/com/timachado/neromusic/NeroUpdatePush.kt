package br.com.timachado.neromusic

import android.content.Context
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

internal const val NERO_UPDATE_TOPIC="nero_music_stable_updates_v1"
internal fun neroPushVersion(data:Map<String,String>,installed:Int):Int?{
    if(data["kind"]!="nero_update_v1"||data["applicationId"]!="br.com.timachado.neromusic")return null
    return data["versionCode"]?.toIntOrNull()?.takeIf{it>installed}
}
internal object NeroUpdatePush{
    fun sync(context:Context){
        if(FirebaseApp.getApps(context).none{it.options.projectId=="nero-music-c8654"})return
        if(!NeroAppUpdates.notificationsEnabled(context)||!NeroRadarAlerts.permitted(context)){
            if(FirebaseApp.getApps(context).isNotEmpty())
                runCatching{FirebaseMessaging.getInstance().unsubscribeFromTopic(NERO_UPDATE_TOPIC)}
            return
        }
        if(FirebaseApp.getApps(context).isEmpty())return
        runCatching{FirebaseMessaging.getInstance().subscribeToTopic(NERO_UPDATE_TOPIC)}
    }
    fun requestCheck(context:Context,code:Int){
        if(!NeroAppUpdates.notificationsEnabled(context))return
        val request=OneTimeWorkRequestBuilder<NeroUpdateWorker>()
            .setInputData(workDataOf("expectedCode" to code))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("nero_push_update_"+code,ExistingWorkPolicy.KEEP,request)
    }
}
class NeroUpdateMessagingService:FirebaseMessagingService(){
    override fun onNewToken(token:String){NeroUpdatePush.sync(applicationContext)}
    override fun onMessageReceived(message:RemoteMessage){
        if(BuildConfig.NERO_FCM_DIAGNOSTIC&&message.data["kind"]=="nero_fcm_test_v1"){
            NeroFcmDiagnostic.receive(applicationContext)
            return
        }
        val code=neroPushVersion(message.data,BuildConfig.VERSION_CODE)?:return
        // Never trust an FCM title, URL or entitlement. Re-read the official stable feed.
        NeroUpdatePush.requestCheck(applicationContext,code)
    }
}
