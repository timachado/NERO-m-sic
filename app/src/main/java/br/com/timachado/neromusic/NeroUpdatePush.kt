package br.com.timachado.neromusic

import android.content.Context
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

internal const val NERO_UPDATE_TOPIC="nero_music_stable_updates_v1"
internal fun neroPushVersion(data:Map<String,String>,installed:Int):Int?{
    if(data["kind"]!="nero_update_v1"||data["applicationId"]!="br.com.timachado.neromusic")return null
    return data["versionCode"]?.toIntOrNull()?.takeIf{it>installed}
}
internal object NeroUpdatePush{
    private val registrationState=MutableStateFlow("Inscrição ainda não verificada.")
    val registration=registrationState.asStateFlow()
    private val generation=AtomicInteger()
    fun sync(context:Context){
        val app=context.applicationContext
        val request=generation.incrementAndGet()
        fun report(status:String){if(generation.get()==request)registrationState.value=status}
        val firebase=runCatching{FirebaseApp.getInstance()}.getOrNull()
        if(firebase?.options?.projectId!="nero-music-c8654"){
            report("Firebase de atualizações indisponível.");return
        }
        val enabled=NeroAppUpdates.notificationsEnabled(app)
        val permitted=NeroAppUpdates.notificationPermitted(app)
        try{
            val messaging=FirebaseMessaging.getInstance()
            if(!enabled||!permitted){
                report(if(!enabled)"Avisos desativados." else "Notificações bloqueadas no Android.")
                messaging.unsubscribeFromTopic(NERO_UPDATE_TOPIC)
                return
            }
            report("Verificando inscrição no Firebase…")
            messaging.subscribeToTopic(NERO_UPDATE_TOPIC).addOnCompleteListener{task->
                report(if(task.isSuccessful)"Inscrição no Firebase confirmada." else "Inscrição não confirmada. Tente novamente.")
            }
        }catch(_:Exception){report("Inscrição não confirmada. Tente novamente.")}
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
        val code=neroPushVersion(message.data,BuildConfig.VERSION_CODE)?:return
        // Never trust an FCM title, URL or entitlement. Re-read the official stable feed.
        NeroUpdatePush.requestCheck(applicationContext,code)
    }
}
