package br.com.timachado.neromusic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val WELCOME_REVISION=1
private val Aurora=Color(0xFFC1ABFF)
private val Ink=Color(0xFF0B0B10)
private val Mist=Color(0xFFB1B1BE)
private fun welcomePrefs(c:Context)=c.getSharedPreferences("nero_welcome_v1",Context.MODE_PRIVATE)

/** Three full-screen stages. Consent is never inferred from terms or notifications. */
@Composable
internal fun NeroWelcomeFlow(accountUid:String?,accountReady:Boolean,foreground:Boolean,pipMode:Boolean):Boolean{
    val context=LocalContext.current
    val hostView=LocalView.current
    val density=LocalDensity.current
    val hostNavBottom=with(density){(ViewCompat.getRootWindowInsets(hostView)?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0).toDp()}
    val safeNavBottom=maxOf(hostNavBottom,24.dp)
    val prefs=remember(context){welcomePrefs(context)}
    var accepted by remember{mutableStateOf(prefs.getInt("terms_revision",0)>=WELCOME_REVISION)}
    var notificationChoice by remember{mutableStateOf(prefs.getBoolean("catalogue_choice_v1",false))}
    var guestIntro by remember{mutableStateOf(prefs.getBoolean("guest_privacy_intro",false))}
    var postponed by rememberSaveable{mutableStateOf(false)}
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
    if(permissionPending)return true
    val page=privacyPage
    if(page!=null){
        NeroPrivacyDialog(page,onClose={privacyPage=null})
        return true
    }
    val uid=accountUid
    val needsChoice=remember(uid,revision,accountReady){accountReady&&uid!=null&&!NeroPresence.hasChoice(context,uid)}
    val stage=when{
        !accepted&&!postponed->1
        accepted&&!notificationChoice->2
        accepted&&notificationChoice&&(needsChoice||(accountReady&&uid==null&&!guestIntro))->3
        else->0
    }
    if(stage==0)return false
    fun chooseNotifications(enabled:Boolean){
        if(!prefs.edit().putBoolean("catalogue_choice_v1",true).commit()){
            error="Não foi possível salvar. Tente novamente.";return
        }
        NeroAppUpdates.setNotifications(context,enabled)
        NeroCatalogueAlerts.setEnabled(context,enabled)
        notificationChoice=true;error=null
        if(enabled&&Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            permissionPending=true
            try{launcher.launch(Manifest.permission.POST_NOTIFICATIONS)}catch(_:Exception){permissionPending=false}
        }
    }
    fun chooseActivity(enabled:Boolean){
        if(uid==null){
            if(!prefs.edit().putBoolean("guest_privacy_intro",true).commit()){error="Não foi possível salvar.";return}
            guestIntro=true;error=null
        }else if(NeroSupabase.activeUid.value==uid&&NeroSupabase.identityReady.value){
            if(!NeroPresence.setEnabled(context,uid,enabled))error="Não foi possível salvar. Tente novamente."
            else error=null
        }
    }
    fun dismiss(){
        when(stage){1->postponed=true;2->chooseNotifications(false);3->chooseActivity(false)}
    }
    val heading=when(stage){1->"A música é sua.\nA experiência também.";2->"Não perca o que\nacabou de chegar.";else->"Sua privacidade.\nSeu controle."}
    val eyebrow=when(stage){1->"SEU SOM. SEU UNIVERSO.";2->"SEMPRE UM PASSO À FRENTE";else->"CONFIANÇA POR PRINCÍPIO"}
    Dialog(onDismissRequest={dismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false,decorFitsSystemWindows=false)){
        MaterialTheme(colorScheme=darkColorScheme(primary=Aurora,onPrimary=Ink,background=Ink,surface=Color(0xFF14121B),onSurface=Color.White)){
            Surface(Modifier.fillMaxSize(),color=Ink){
                Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171022),Ink,Ink))).windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.navigationBarsIgnoringVisibility).union(WindowInsets(bottom=safeNavBottom))).padding(horizontal=24.dp,vertical=16.dp)){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                        Text("NERO MUSIC",fontWeight=FontWeight.Bold,letterSpacing=2.sp,color=Color.White,fontSize=14.sp)
                        Text("0"+stage+" / 03",color=Mist,fontSize=12.sp)
                    }
                    val bodyScroll=rememberScrollState()
                    LaunchedEffect(stage){bodyScroll.scrollTo(0)}
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(bodyScroll),verticalArrangement=Arrangement.Center){
                        Spacer(Modifier.height(12.dp))
                        AuroraHero(stage)
                        Text(eyebrow,color=Aurora,fontWeight=FontWeight.SemiBold,fontSize=10.sp,letterSpacing=1.6.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(heading,fontSize=32.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold,color=Color.White)
                        Spacer(Modifier.height(14.dp))
                        when(stage){
                            1->{
                                Text("Descubra novas possibilidades e viva a música do seu jeito.",color=Mist,style=MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(12.dp))
                                Text("Ao concordar, você aceita os termos de uso. As escolhas de privacidade e notificações são separadas.",color=Mist,style=MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                                    TextButton(onClick={privacyPage=NeroPrivacyPage.TERMS}){Text("Termos de uso")}
                                    TextButton(onClick={privacyPage=NeroPrivacyPage.DATA}){Text("Privacidade")}
                                }
                            }
                            2->{
                                Text("Fique conectado ao universo NERO.",color=Mist,style=MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(14.dp))
                                WelcomeFeature("Novidades na grade",Icons.Default.Album)
                                WelcomeFeature("Descobertas e destaques musicais",Icons.Default.AutoAwesome)
                                WelcomeFeature("Atualizações oficiais e novidades do app",Icons.Default.Notifications)
                                Spacer(Modifier.height(10.dp))
                                Text("Avisos opcionais. A seleção musical é renovada periodicamente; as datas de lançamento não são verificadas.",color=Mist,style=MaterialTheme.typography.bodySmall)
                            }
                            else->{
                                Text("Ajude a evoluir o NERO com estatísticas básicas de utilização.",color=Mist,style=MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(12.dp))
                                WelcomeFeature("Sem coleta de músicas ou histórico",Icons.Default.CheckCircle)
                                WelcomeFeature("Sem rastreamento de localização",Icons.Default.CheckCircle)
                                WelcomeFeature("Sem dados sensíveis ou IDs de hardware",Icons.Default.CheckCircle)
                                Spacer(Modifier.height(10.dp))
                                Text("Para esta finalidade, registramos somente a conta, um identificador aleatório por instalação e o último uso. Você pode mudar a escolha depois. Registros expiram após 90 dias de inatividade.",color=Mist,style=MaterialTheme.typography.bodySmall)
                                if(uid==null)Text("Nenhuma atividade será compartilhada sem sua autorização após entrar com Google.",color=Mist,style=MaterialTheme.typography.bodySmall)
                                TextButton(onClick={privacyPage=NeroPrivacyPage.DATA}){Text("Conheça seus controles de privacidade")}
                            }
                        }
                        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                        Spacer(Modifier.height(12.dp))
                    }
                    Button(onClick={
                        when(stage){
                            1->if(prefs.edit().putInt("terms_revision",WELCOME_REVISION).commit()){accepted=true;error=null}else error="Não foi possível salvar."
                            2->chooseNotifications(true)
                            3->chooseActivity(uid!=null)
                        }
                    },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=CircleShape,colors=ButtonDefaults.buttonColors(containerColor=Aurora,contentColor=Ink)){
                        Text(when(stage){1->"Concordar e continuar";2->"Quero receber novidades";else->if(uid==null)"Começar a ouvir" else "Permitir compartilhamento"},fontWeight=FontWeight.Bold)
                    }
                    if(stage!=3||uid!=null)TextButton(onClick={dismiss()},modifier=Modifier.fillMaxWidth()){Text(if(stage==3)"Continuar sem compartilhar" else "Agora não",color=Mist)}
                    Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=4.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                        for(i in 1..3){
                            val w by animateFloatAsState(if(stage==i)24f else 6f,tween(220),label="welcome progress")
                            Box(Modifier.padding(horizontal=3.dp).width(w.dp).height(5.dp).background(if(stage==i)Aurora else Color(0xFF373342),CircleShape))
                        }
                    }
                }
            }
        }
    }
    return true
}

@Composable private fun WelcomeFeature(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector){
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Icon(icon,contentDescription=null,tint=Aurora,modifier=Modifier.size(20.dp))
        Text(label,color=Color(0xFFE5E1EE),style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun AuroraHero(stage:Int){
    Box(Modifier.fillMaxWidth().height(176.dp),contentAlignment=Alignment.Center){
        Canvas(Modifier.fillMaxSize()){
            val r=size.minDimension*.49f
            val center=Offset(size.width/2,size.height/2)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF8055EC).copy(alpha=.35f),Color.Transparent),center,r),radius=r,center=center)
            for(ratio in listOf(.51f,.70f,.88f))drawCircle(Aurora.copy(alpha=.16f),radius=r*ratio,center=center,style=Stroke(1.dp.toPx()))
            if(stage==1){
                val heights=listOf(.16f,.32f,.52f,.78f,.52f,.92f,.62f,.34f,.19f)
                heights.forEachIndexed{i,h->
                    val x=center.x+(i-4)*r*.13f
                    drawLine(Aurora,Offset(x,center.y-r*h/2),Offset(x,center.y+r*h/2),strokeWidth=4.dp.toPx(),cap=StrokeCap.Round)
                }
            }
        }
        if(stage!=1)Icon(if(stage==2)Icons.Default.Notifications else Icons.Default.Shield,contentDescription=null,tint=Aurora,modifier=Modifier.size(72.dp))
    }
}
