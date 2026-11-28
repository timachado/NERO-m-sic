package br.com.timachado.neromusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val premiumGreen:Color @Composable get()=MaterialTheme.colorScheme.primary
private val premiumCyan:Color @Composable get()=MaterialTheme.colorScheme.secondary
private val panel:Color @Composable get()=MaterialTheme.colorScheme.surface

@Composable
internal fun PlansScreen(onBack:()->Unit,modifier:Modifier=Modifier,canPlayTogether:Boolean=false,onTogetherQueue:((List<androidx.media3.common.MediaItem>,Int)->Unit)?=null,livePlayer:androidx.media3.common.Player?=null) {
    val context=LocalContext.current
    var siteError by remember{mutableStateOf(false)}
    var preparingCheckout by remember{mutableStateOf(false)}
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val scope=rememberCoroutineScope()
    val resumed=rememberNeroCommerceResume()
    var refreshRequest by remember(uid,ready){mutableIntStateOf(0)}
    var subscription by remember(uid,ready){mutableStateOf<NeroSubscription?>(null)}
    var checking by remember(uid,ready){mutableStateOf(false)}
    var activating by remember(uid,ready){mutableStateOf(false)}
    var notice by remember(uid,ready){mutableStateOf<String?>(null)}
    var confirmTrial by remember(uid,ready){mutableStateOf(false)}
    suspend fun refresh(owner:String){
        checking=true
        try{
            val result=readNeroSubscription(context,owner)
            if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner){
                subscription=result
                notice=if(result==null)"Serviço de assinaturas indisponível." else null
            }
        }catch(e:CancellationException){throw e}catch(_:Exception){
            if(NeroSupabase.activeUid.value==owner){subscription=null;notice="Serviço de assinaturas indisponível."}
        }finally{checking=false}
    }
    LaunchedEffect(uid,ready,resumed,refreshRequest){
        val owner=uid
        if(ready&&owner!=null&&!activating)refresh(owner)
    }
    val premium=subscription?.phase?.premium==true
    if(confirmTrial)AlertDialog(onDismissRequest={confirmTrial=false},title={Text("Experimentar Premium NERO Music?")},text={Text("Sete dias gratuitos, sem cartão e sem cobrança automática. Apenas uma experiência por conta elegível.")},confirmButton={TextButton(onClick={
        val owner=uid;confirmTrial=false
        if(owner!=null&&ready&&NeroSupabase.activeUid.value==owner){
            activating=true
            scope.launch{
                try{
                    val result=startNeroTrial(context,owner)
                    if(NeroSupabase.activeUid.value==owner&&NeroSupabase.identityReady.value){
                        refresh(owner)
                        if(subscription?.phase==NeroSubscriptionPhase.TRIAL)notice="Experiência Premium ativa."
                        else if(result=="ineligible")notice="Esta conta não é elegível para uma nova experiência."
                        else if(subscription?.phase?.premium!=true)notice="Não foi possível confirmar a ativação."
                    }
                }catch(e:CancellationException){throw e}catch(_:Exception){if(NeroSupabase.activeUid.value==owner)notice="Experiência ainda indisponível."}
                finally{activating=false}
            }
        }
    }){Text("Ativar experiência")}},dismissButton={TextButton(onClick={confirmTrial=false}){Text("Cancelar")}})
    var selectedPlan by remember{mutableStateOf(NeroPlans.annual.id)}
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text("Planos NERO Music",fontSize=24.sp,fontWeight=FontWeight.Bold)
        }
        if(ready&&uid!=null)Text("Seu plano: "+(if(checking)"Consultando…" else neroProfilePlanLabel(subscription?.phase)),fontSize=14.sp,fontWeight=FontWeight.Bold,color=if(premium)MaterialTheme.colorScheme.primary else premiumCyan)
        Text("Sua música. Sua liberdade.",fontSize=18.sp,fontWeight=FontWeight.Medium,color=premiumCyan)
        Text("Escolha a experiência NERO que combina com você.",fontSize=13.sp,color=Color.LightGray)
        if(ready&&uid!=null){
            val owner=requireNotNull(uid)
            NeroStudioEntry(owner)
            onTogetherQueue?.let{playQueue->
                Text("NERO Together · salas compartilhadas",fontWeight=FontWeight.Bold,color=premiumCyan)
                Text("Salas privadas, convites e fila por votos. Convidados Free podem acompanhar o anfitrião Premium.",fontSize=12.sp,color=Color.LightGray)
                NeroTogetherShelf(canPlayTogether,playQueue,livePlayer)
            }
        }
        Column(Modifier.fillMaxWidth().background(panel,RoundedCornerShape(16.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("NERO Free",fontSize=23.sp,fontWeight=FontWeight.Bold)
            Text("Acesso gratuito",color=premiumCyan)
            listOf("Player de música e busca","Reprodução aleatória","Login Google","Favoritos e playlists","Sincronização básica da biblioteca","NERO Radar","NERO DJ · degustação","NERO Together · participar por convite").forEach { PlanFeature(it,true) }
            PlanFeature("Smart Mix, Daily Mix e Discovery online",NativeMusicCatalog.enabled)
            if(!NativeMusicCatalog.enabled)Text("Recursos online indisponíveis nesta versão.",fontSize=12.sp,color=Color.LightGray)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Icon(Icons.Default.Lock,null,tint=premiumCyan,modifier=Modifier.size(20.dp))
                Text("Downloads offline · exclusivo Premium",fontSize=14.sp,color=Color.LightGray)
            }
            Text("Ouça em modo aleatório e avance para a próxima faixa. Voltar e arrastar a barra da música são recursos Premium.",fontSize=12.sp,color=Color.LightGray)
        }
        Column(Modifier.fillMaxWidth().border(1.dp,premiumGreen,RoundedCornerShape(16.dp)).background(panel,RoundedCornerShape(16.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Premium NERO Music",color=premiumGreen,fontSize=23.sp,fontWeight=FontWeight.Bold)
            Text(when{
                !ready||checking->"Consultando assinatura…"
                uid==null->"Entre com Google para consultar seu plano."
                subscription!=null->subscription!!.phase.label
                else->"Serviço de assinaturas ainda indisponível"
            },fontWeight=FontWeight.Medium,color=if(premium)premiumGreen else Color.LightGray)
            subscription?.dateLabel()?.let{Text("Acesso até: "+it,fontSize=12.sp,color=Color.LightGray)}
            subscription?.let{details->
                neroExpiryNotice(details.phase,details.until,System.currentTimeMillis()/1000L)?.let{
                    Text(it,fontSize=13.sp,color=premiumCyan)
                }
            }
            subscription?.planLabel?.let{Text("Seu plano: "+it,fontSize=13.sp)}
            if(checking||activating)LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().background(premiumGreen.copy(alpha=.10f),RoundedCornerShape(12.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Icon(Icons.Default.DownloadForOffline,null,tint=premiumGreen,modifier=Modifier.size(28.dp))
                Column(Modifier.weight(1f)){
                    Text("Downloads para ouvir offline",fontSize=15.sp,fontWeight=FontWeight.Bold,color=premiumGreen)
                    Text("Sua música, onde você estiver.",fontSize=12.sp,color=Color.LightGray)
                }
                Icon(if(premium)Icons.Default.CheckCircle else Icons.Default.WorkspacePremium,null,tint=premiumGreen,modifier=Modifier.size(20.dp))
            }
            listOf("Tudo do Free","Reprodução na ordem escolhida","Voltar, repetir e ajustar posição","Controle da fila","NERO DJ · mixes personalizados","NERO Studio · temas exclusivos","Personalização avançada do player","NERO Replay 365","NERO Smart Library","Backup e exportação avançada","Smart Mix e Daily Mix avançados").forEach { PlanFeature(it,premium) }
            PlanFeature("NERO Together · criar salas e sincronizar músicas",premium)
            PlanFeature("Suporte prioritário · previsto",false)
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Text("Coleção de temas do player",fontWeight=FontWeight.Bold,color=premiumCyan)
            NeroPlayerTheme.entries.forEach{theme->
                Row(Modifier.fillMaxWidth().background(Color(theme.background),RoundedCornerShape(12.dp)).padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Box(Modifier.size(20.dp).background(Color(theme.primary),RoundedCornerShape(5.dp)))
                    Box(Modifier.size(20.dp).background(Color(theme.secondary),RoundedCornerShape(5.dp)))
                    Column(Modifier.weight(1f)){
                        Text(theme.label,color=Color(theme.primary),fontWeight=FontWeight.Medium)
                        Text(theme.description,fontSize=12.sp,color=Color.LightGray)
                        Text(if(theme==NeroPlayerTheme.AURORA)"Free" else "Premium",fontSize=11.sp,color=Color.LightGray)
                    }
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Text("Escolha seu plano",fontWeight=FontWeight.Bold)
            NeroPlans.offers.forEach{offer->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    RadioButton(selected=selectedPlan==offer.id,enabled=!preparingCheckout,onClick={selectedPlan=offer.id})
                    Column(Modifier.weight(1f)){
                        Text(offer.label,fontWeight=FontWeight.Bold)
                        if(offer.savingsPercent>0)Text("Economia de aproximadamente "+offer.savingsPercent+"%",fontSize=12.sp,color=premiumCyan)
                    }
                    Text(offer.price,fontWeight=FontWeight.Bold,color=premiumGreen,fontSize=20.sp)
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
            val selection=NeroPlans.offers.first{it.id==selectedPlan}
            Text("Selecionado: "+selection.label+" · "+selection.price,fontWeight=FontWeight.Medium,color=premiumGreen)
            Button(enabled=ready&&uid!=null&&!checking&&!activating&&subscription?.phase==NeroSubscriptionPhase.FREE,onClick={confirmTrial=true},modifier=Modifier.fillMaxWidth()){Text(if(activating)"Ativando…" else if(premium)"Premium ativo" else "Experimentar 7 dias grátis")}
            OutlinedButton(enabled=ready&&uid!=null&&!checking&&!activating,onClick={refreshRequest++},modifier=Modifier.fillMaxWidth()){Text("Consultar / restaurar assinatura")}
            notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
            Button(enabled=ready&&!preparingCheckout&&!activating,onClick={
                val plan=selection.id
                preparingCheckout=true;siteError=false
                scope.launch {
                    try {
                        val url=NeroSupabase.checkoutHandoff(context,plan)
                        siteError=!openBrowser(context,android.net.Uri.parse(url))
                    }catch(e:CancellationException){throw e}catch(_:Exception){siteError=true}
                    finally{preparingCheckout=false}
                }
            },modifier=Modifier.fillMaxWidth()){Text(if(preparingCheckout)"Abrindo pagamento…" else "Assinar Premium · "+selection.label)}
            Text("Você irá direto para o checkout oficial com a conta do NERO. Ao retornar, a assinatura será consultada automaticamente.",fontSize=12.sp,color=Color.LightGray)
            Text("${NeroPlans.TRIAL_DAYS} dias de experiência, quando o serviço estiver habilitado, sem cartão obrigatório ou cobrança automática ao término.",color=Color.LightGray,fontSize=13.sp)
            Text("O site confirma os valores, disponibilidade e pagamentos.",color=Color.LightGray,fontSize=13.sp)
            if(siteError)Text("Não foi possível abrir o pagamento. Verifique sua conexão e tente novamente.",color=Color.LightGray,fontSize=12.sp)
        }
        Text("O Premium NERO Music é uma assinatura independente. A disponibilidade de downloads varia conforme a música.",fontSize=12.sp,color=Color.LightGray)
        OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("Voltar")}
    }
}

@Composable
private fun PlanFeature(label:String,available:Boolean) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Icon(if(available)Icons.Default.CheckCircle else Icons.Default.Schedule,null,tint=if(available)premiumCyan else Color.LightGray,modifier=Modifier.size(20.dp))
        Text(label,fontSize=14.sp)
    }
}
