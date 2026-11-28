package br.com.timachado.neromusic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val WELCOME_REVISION=1
private fun welcomePrefs(c:Context)=c.getSharedPreferences("nero_welcome_v1",Context.MODE_PRIVATE)

/** Present first-use choices and subsequently ask once for each signed-in account. */
@Composable
internal fun NeroWelcomeFlow(accountUid:String?,accountReady:Boolean,foreground:Boolean,pipMode:Boolean):Boolean{
    val context=LocalContext.current
    val prefs=remember(context){welcomePrefs(context)}
    var accepted by remember{mutableStateOf(prefs.getInt("terms_revision",0)>=WELCOME_REVISION)}
    var postponed by rememberSaveable{mutableStateOf(false)}
    var termsRead by rememberSaveable{mutableStateOf(false)}
    var notifications by rememberSaveable{mutableStateOf(NeroAppUpdates.notificationsEnabled(context)&&NeroAppUpdates.notificationPermitted(context))}
    var privacyPage by remember{mutableStateOf<NeroPrivacyPage?>(null)}
    var permissionPending by rememberSaveable{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val revision by NeroPresence.revision.collectAsState()
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){
        permissionPending=false
        NeroUpdatePush.sync(context)
        if(NeroAppUpdates.notificationPermitted(context))NeroUpdatePush.requestCheck(context,0)
    }
    if(!foreground||pipMode)return permissionPending
    val page=privacyPage
    if(page!=null){
        NeroPrivacyDialog(page,onClose={privacyPage=null})
        return true
    }
    if(!accepted&&!postponed){
        AlertDialog(onDismissRequest={postponed=true},title={Text("Bem-vindo ao NERO Music",fontWeight=FontWeight.Bold)},text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Sua música, suas escolhas.",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
                Text("Conheça os termos de uso e controle as notificações e os dados que deseja compartilhar.")
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    TextButton(onClick={privacyPage=NeroPrivacyPage.TERMS}){Text("Termos de uso")}
                    TextButton(onClick={privacyPage=NeroPrivacyPage.DATA}){Text("Privacidade")}
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(checked=termsRead,onCheckedChange={termsRead=it})
                    Text("Li e concordo com os termos de uso.",modifier=Modifier.weight(1f))
                }
                HorizontalDivider()
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("Avisos de atualização",fontWeight=FontWeight.Medium)
                        Text("Receba notícias sobre novas versões oficiais.",style=MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked=notifications,onCheckedChange={notifications=it})
                }
                Text("Notificações e estatísticas de atividade são opcionais e podem ser alteradas depois.",style=MaterialTheme.typography.bodySmall)
                error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            }
        },confirmButton={
            Button(enabled=termsRead,onClick={
                if(!prefs.edit().putInt("terms_revision",WELCOME_REVISION).commit()){
                    error="Não foi possível salvar. Tente novamente."
                }else{
                    accepted=true
                    NeroAppUpdates.setNotifications(context,notifications)
                    if(notifications&&Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                        permissionPending=true
                        try{launcher.launch(Manifest.permission.POST_NOTIFICATIONS)}catch(_:Exception){permissionPending=false}
                    }
                }
            }){Text("Continuar")}
        },dismissButton={TextButton(onClick={postponed=true}){Text("Agora não")}})
        return true
    }
    if(permissionPending)return true
    val uid=accountUid
    val needsChoice=remember(uid,revision,accountReady){accountReady&&uid!=null&&!NeroPresence.hasChoice(context,uid)}
    if(accepted&&uid!=null&&needsChoice){
        fun choose(enabled:Boolean){
            if(NeroSupabase.activeUid.value==uid&&NeroSupabase.identityReady.value){
                if(!NeroPresence.setEnabled(context,uid,enabled))error="Não foi possível salvar. Tente novamente."
            }
        }
        AlertDialog(onDismissRequest={choose(false)},title={Text("Ajude a melhorar o NERO",fontWeight=FontWeight.Bold)},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("Compartilhar atividade do aplicativo")
                Text("Com sua autorização, registramos um identificador aleatório desta instalação, sua conta e o último uso do app. Isso permite contabilizar instalações e contas ativas.")
                Text("Não coletamos suas músicas, histórico de reprodução, localização ou identificadores do aparelho para essa finalidade.")
                Text("A escolha é opcional. Você pode mudar nas configurações da conta. Registros de presença expiram após 90 dias de inatividade.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={privacyPage=NeroPrivacyPage.DATA}){Text("Saiba mais sobre privacidade")}
                error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            }
        },confirmButton={Button(onClick={choose(true)}){Text("Permitir")}},dismissButton={TextButton(onClick={choose(false)}){Text("Agora não")}})
        return true
    }
    return false
}
