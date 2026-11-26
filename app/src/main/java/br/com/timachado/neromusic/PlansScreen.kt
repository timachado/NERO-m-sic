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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val premiumGreen=Color(0xFF39FF14)
private val premiumCyan=Color(0xFF00E5FF)
private val panel=Color(0xFF10171C)

@Composable
internal fun PlansScreen(onBack:()->Unit,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    var siteError by remember{mutableStateOf(false)}
    var selectedPlan by remember{mutableStateOf(NeroPlans.annual.id)}
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text("Planos NERO Music",fontSize=24.sp,fontWeight=FontWeight.Bold)
        }
        Text("Sua música. Sua liberdade.",fontSize=18.sp,fontWeight=FontWeight.Medium,color=premiumCyan)
        Text("Escolha a experiência NERO que combina com você.",fontSize=13.sp,color=Color.LightGray)
        Column(Modifier.fillMaxWidth().background(panel,RoundedCornerShape(16.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("NERO Free",fontSize=23.sp,fontWeight=FontWeight.Bold)
            Text("Acesso gratuito",color=premiumCyan)
            listOf("Player de música e busca","Login Google","Favoritos e playlists","Sincronização básica da biblioteca","NERO Radar","NERO DJ · degustação").forEach { PlanFeature(it,true) }
            PlanFeature("Smart Mix, Daily Mix e Discovery online",NativeMusicCatalog.enabled)
            if(!NativeMusicCatalog.enabled)Text("Recursos online indisponíveis nesta versão.",fontSize=12.sp,color=Color.LightGray)
            Text("Aproveite os recursos gratuitos do NERO.",fontSize=12.sp,color=Color.LightGray)
        }
        Column(Modifier.fillMaxWidth().border(1.dp,premiumGreen,RoundedCornerShape(16.dp)).background(panel,RoundedCornerShape(16.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("NERO Premium",color=premiumGreen,fontSize=23.sp,fontWeight=FontWeight.Bold)
            Text("Benefícios previstos · contratação indisponível",fontWeight=FontWeight.Medium,color=Color.LightGray)
            listOf("Tudo do Free","NERO DJ · mixes personalizados","NERO Studio · temas exclusivos","Personalização avançada do player","NERO Replay 365","NERO Smart Library","Backup e exportação avançada","Suporte prioritário").forEach { PlanFeature(it,false) }
            PlanFeature("Smart Mix e Daily Mix avançados",false)
            HorizontalDivider(color=Color(0xFF33434A))
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
            HorizontalDivider(color=Color(0xFF33434A))
            Text("Escolha seu plano",fontWeight=FontWeight.Bold)
            NeroPlans.offers.forEach{offer->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    RadioButton(selected=selectedPlan==offer.id,onClick={selectedPlan=offer.id})
                    Column(Modifier.weight(1f)){
                        Text(offer.label,fontWeight=FontWeight.Bold)
                        if(offer.savingsPercent>0)Text("Economia de aproximadamente "+offer.savingsPercent+"%",fontSize=12.sp,color=premiumCyan)
                    }
                    Text(offer.price,fontWeight=FontWeight.Bold,color=premiumGreen,fontSize=20.sp)
                }
                HorizontalDivider(color=Color(0xFF33434A))
            }
            val selection=NeroPlans.offers.first{it.id==selectedPlan}
            Text("Selecionado: "+selection.label+" · "+selection.price,fontWeight=FontWeight.Medium,color=premiumGreen)
            Button(enabled=false,onClick={},modifier=Modifier.fillMaxWidth()){Text("Assinatura indisponível")}
            Text("Nenhum pedido ou cobrança será realizado nesta versão.",fontSize=12.sp,color=Color.LightGray)
            Text("${NeroPlans.TRIAL_DAYS} dias de experiência previstos, sem cartão obrigatório ou cobrança automática ao término.",color=Color.LightGray,fontSize=13.sp)
            Text("Contratação prevista pelo site oficial da T.I. Machado, com opções de pagamento sujeitas à disponibilidade.",color=Color.LightGray,fontSize=13.sp)
            OutlinedButton(onClick={siteError=!openBrowser(context,android.net.Uri.parse(NeroPlans.SITE))},modifier=Modifier.fillMaxWidth()){Text("Conhecer o site oficial")}
            if(siteError)Text("Não foi possível abrir o site.",color=Color.LightGray,fontSize=12.sp)
        }
        Text("O NERO Premium é uma assinatura independente. Downloads sujeitos à disponibilidade e às permissões de cada faixa.",fontSize=12.sp,color=Color.LightGray)
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
