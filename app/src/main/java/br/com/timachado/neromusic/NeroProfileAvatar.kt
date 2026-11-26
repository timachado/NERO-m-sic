package br.com.timachado.neromusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun NeroHeaderProfile(onClick:()->Unit){
    val profile by NeroSupabase.profileAvatar.collectAsState()
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val entitled by NeroPlaybackAccess.premium.collectAsState()
    val visibleProfile=profile?.takeIf{ready&&uid!=null&&it.uid==uid}
    var premium by remember(uid,ready,entitled){mutableStateOf(ready&&entitled&&NeroPlaybackAccess.allowed())}
    LaunchedEffect(uid,ready,entitled){
        while(ready&&uid!=null&&entitled){
            premium=NeroPlaybackAccess.allowed()
            delay(2000)
        }
    }
    val gold=Color(0xFFFFD170)
    val cyan=Color(0xFF00E5FF)
    val neon=Color(0xFF39FF14)
    val ring=if(premium)listOf(gold,Color(0xFFFFA65C),gold)else listOf(cyan,neon,cyan)
    val planLabel=when{!ready->"…";uid==null->"Visitante";premium->"Premium";else->"Free"}
    IconButton(onClick=onClick,modifier=Modifier.width(62.dp).height(62.dp).semantics{contentDescription="Minha conta: "+planLabel}){
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)){
        Box(Modifier.size(39.dp),contentAlignment=Alignment.Center){
            Box(Modifier.fillMaxSize().background(Brush.sweepGradient(ring),CircleShape).padding(2.dp)
                .background(Color(0xFF10171C),CircleShape).padding(2.dp).clip(CircleShape),contentAlignment=Alignment.Center){
                Icon(Icons.Default.AccountCircle,null,tint=if(premium)gold else cyan,modifier=Modifier.fillMaxSize())
                visibleProfile?.url?.let{url->
                    key(uid,url){
                        NeroArtwork(model=url,contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    }
                }
            }
            if(premium){
                Box(Modifier.align(Alignment.BottomEnd).size(16.dp).background(gold,CircleShape)
                    .border(1.dp,Color(0xFF10171C),CircleShape),contentAlignment=Alignment.Center){
                    Icon(Icons.Default.WorkspacePremium,null,tint=Color.Black,modifier=Modifier.size(11.dp))
                }
            }
        }
        Text(planLabel,fontSize=10.sp,fontWeight=FontWeight.Bold,color=if(premium)gold else cyan,maxLines=1,softWrap=false)
        }
    }
}

internal fun neroProfilePlanLabel(phase:NeroSubscriptionPhase?):String=when{
    phase==null||phase==NeroSubscriptionPhase.UNKNOWN->"Plano não verificado"
    phase.premium->"NERO Premium"
    else->"NERO Free"
}

// Account details are informational; the MediaSession retains its own authorization policy.
@Composable
internal fun NeroProfilePlan(uid:String,onPlans:()->Unit){
    val context=LocalContext.current
    val ready by NeroSupabase.identityReady.collectAsState()
    val active by NeroSupabase.activeUid.collectAsState()
    val resumed=rememberNeroCommerceResume()
    var details by remember(uid,ready,active,resumed){mutableStateOf<NeroSubscription?>(null)}
    var checking by remember(uid,ready,active,resumed){mutableStateOf(true)}
    LaunchedEffect(uid,ready,active,resumed){
        if(!ready||active!=uid){checking=false;return@LaunchedEffect}
        try{details=readNeroSubscription(context,uid)}
        catch(e:CancellationException){throw e}catch(_:Exception){details=null}
        finally{checking=false}
    }
    val current=details?.takeIf{ready&&active==uid}
    val premium=current?.phase?.premium==true
    val color=if(premium)Color(0xFFFFD170) else Color(0xFF00E5FF)
    Column(Modifier.fillMaxWidth().background(Color(0xFF10171C),androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
        .border(1.dp,color.copy(alpha=.45f),androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(if(premium)Icons.Default.WorkspacePremium else Icons.Default.AccountCircle,null,tint=color)
            Text(if(checking)"Consultando plano…" else neroProfilePlanLabel(current?.phase),color=color,fontWeight=FontWeight.Bold)
        }
        current?.let{plan->
            Text(plan.phase.label,style=MaterialTheme.typography.bodySmall)
            plan.dateLabel()?.let{Text("Acesso até: "+it,style=MaterialTheme.typography.bodySmall)}
        }
        OutlinedButton(onClick=onPlans,modifier=Modifier.fillMaxWidth()){
            Text(if(premium)"Gerenciar assinatura" else if(current!=null&&current.phase!=NeroSubscriptionPhase.UNKNOWN)"Conhecer NERO Premium" else "Consultar planos")
        }
    }
}
