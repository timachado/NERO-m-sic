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
import androidx.compose.runtime.Composable
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
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text("Planos NERO",fontSize=24.sp,fontWeight=FontWeight.Bold)
        }
        Text("Sua música. Sua liberdade.",fontSize=18.sp,fontWeight=FontWeight.Medium,color=premiumCyan)
        Text("Prévia dos planos. As assinaturas ainda não estão disponíveis para contratação.",fontSize=13.sp,color=Color.LightGray)
        Column(Modifier.fillMaxWidth().background(panel,RoundedCornerShape(16.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("NERO Free",fontSize=23.sp,fontWeight=FontWeight.Bold)
            Text("Acesso gratuito",color=premiumCyan)
            listOf("Player local","Biblioteca e favoritos","Playlists locais","Busca e descoberta musical").forEach { PlanFeature(it,true) }
            Text("Continue usando os recursos atuais sem assinatura.",fontSize=12.sp,color=Color.LightGray)
        }
        Column(Modifier.fillMaxWidth().border(1.dp,premiumGreen,RoundedCornerShape(16.dp)).background(panel,RoundedCornerShape(16.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("NERO Premium",color=premiumGreen,fontSize=23.sp,fontWeight=FontWeight.Bold)
            Text("Em desenvolvimento",fontWeight=FontWeight.Medium,color=Color.LightGray)
            listOf("Equalizador avançado","Sincronização na nuvem","Playlists inteligentes","Personalização do player","Experiência sem anúncios próprios do NERO").forEach { PlanFeature(it,false) }
            HorizontalDivider(color=Color(0xFF33434A))
            Text("Planos mensal e anual previstos. Preços e condições serão divulgados antes da contratação.",color=Color.LightGray,fontSize=13.sp)
            Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth()){Text("Assinaturas indisponíveis")}
        }
        Text("A assinatura NERO não inclui YouTube Music Premium e não concede direitos de download de catálogos de terceiros.",fontSize=12.sp,color=Color.LightGray)
        Text("Distribuição oficial prevista pelo site da T.I. Machado.",fontSize=12.sp,color=Color.LightGray)
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
