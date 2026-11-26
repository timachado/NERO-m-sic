package br.com.timachado.neromusic

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

internal object NeroFcmDiagnostic{
    private fun prefs(c:Context)=c.getSharedPreferences("nero_fcm_diagnostic",Context.MODE_PRIVATE)
    fun arm(c:Context){prefs(c).edit().putLong("until",System.currentTimeMillis()+900000).remove("received").apply()}
    fun received(c:Context)=prefs(c).getLong("received",0)
    fun receive(c:Context){
        if(!BuildConfig.NERO_FCM_DIAGNOSTIC||prefs(c).getLong("until",0)<System.currentTimeMillis())return
        prefs(c).edit().putLong("received",System.currentTimeMillis()).apply()
        if(!NeroRadarAlerts.permitted(c))return
        val manager=c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("nero_fcm_test","NERO · Diagnóstico",NotificationManager.IMPORTANCE_DEFAULT))
        manager.notify(7821,NotificationCompat.Builder(c,"nero_fcm_test").setSmallIcon(R.drawable.ic_nero_notification)
            .setContentTitle("NERO · Teste Firebase recebido").setContentText("Mensagem de homologação neste aparelho.").setAutoCancel(true).build())
    }
}
@Composable
internal fun NeroFcmDiagnosticPanel(){
    val context=LocalContext.current
    val configured=remember{FirebaseApp.getApps(context).any{it.options.projectId=="nero-music-c8654"}}
    var notice by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var received by remember{mutableLongStateOf(NeroFcmDiagnostic.received(context))}
    val resume=rememberNeroCommerceResume()
    LaunchedEffect(resume){received=NeroFcmDiagnostic.received(context)}
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text("Diagnóstico Firebase (temporário)",style=MaterialTheme.typography.titleMedium)
        Text(if(configured)"Projeto Firebase NERO identificado." else "Firebase não configurado.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled=configured&&!busy,onClick={
            busy=true
            try{
                FirebaseMessaging.getInstance().token.addOnCompleteListener{task->
                    busy=false
                    if(task.isSuccessful&&!task.result.isNullOrBlank()){
                        val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Token de teste FCM",task.result))
                        NeroFcmDiagnostic.arm(context)
                        received=0
                        notice="Token copiado. Cole somente no teste do Console Firebase. Diagnóstico ativo por 15 minutos."
                    }else notice="Não foi possível registrar o dispositivo."
                }
            }catch(_:Exception){busy=false;notice="Falha ao consultar o Firebase."}
        },modifier=Modifier.fillMaxWidth()){Text(if(busy)"Registrando…" else "Copiar token de teste")}
        TextButton(onClick={received=NeroFcmDiagnostic.received(context)}){Text("Verificar recebimento")}
        if(received>0)Text("Mensagem de diagnóstico recebida neste aparelho.")
        if(notice.isNotBlank())Text(notice,style=MaterialTheme.typography.bodySmall)
    }
}
