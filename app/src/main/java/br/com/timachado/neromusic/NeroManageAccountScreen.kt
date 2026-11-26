package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NeroManageAccountScreen(user:NeroSession,modifier:Modifier=Modifier,enabled:Boolean,onBack:()->Unit,onPlans:()->Unit,onPrivacy:(NeroPrivacyPage)->Unit,onHistory:()->Unit,onSignOut:()->Unit){
    var tab by remember(user.uid){mutableIntStateOf(0)}
    var confirmExit by remember(user.uid){mutableStateOf(false)}
    Column(modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text("Minha conta NERO Music",fontSize=22.sp,fontWeight=FontWeight.Bold)
        }
        TabRow(selectedTabIndex=tab){
            listOf("Perfil","Plano e nuvem","Privacidade").forEachIndexed{index,label->
                Tab(selected=tab==index,onClick={tab=index},text={Text(label,fontSize=12.sp)})
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            when(tab){
                0->{
                    Card(Modifier.fillMaxWidth()){
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                            Box(Modifier.align(Alignment.CenterHorizontally).size(84.dp),contentAlignment=Alignment.Center){
                                Icon(Icons.Default.AccountCircle,null,modifier=Modifier.fillMaxSize(),tint=MaterialTheme.colorScheme.primary)
                                user.avatar?.let{NeroArtwork(model=it,contentDescription="Foto do perfil",modifier=Modifier.fillMaxSize().clip(CircleShape),contentScale=ContentScale.Crop)}
                            }
                            NeroProfilePlan(user.uid,onPlans=onPlans)
                            Text("Dados do perfil",fontSize=20.sp,fontWeight=FontWeight.Bold)
                            Text("Nome",style=MaterialTheme.typography.labelMedium)
                            Text(user.displayName.ifBlank{"Minha conta"})
                            HorizontalDivider()
                            Text("E-mail",style=MaterialTheme.typography.labelMedium)
                            Text(user.email)
                            HorizontalDivider()
                            Text("Método de acesso",style=MaterialTheme.typography.labelMedium)
                            Text("Google conectado",color=MaterialTheme.colorScheme.primary)
                            Text("O Google autentica sua identidade. Gerencie sua conta NERO Music aqui.",style=MaterialTheme.typography.bodySmall)
                            Text("Nome, e-mail e foto são dados da identidade conectada, disponíveis para consulta nesta versão.",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick={tab=1},modifier=Modifier.fillMaxWidth()){Text("Meu plano e sincronização")}
                    OutlinedButton(onClick=onHistory,modifier=Modifier.fillMaxWidth()){Text("Histórico de reprodução")}
                    OutlinedButton(enabled=enabled,onClick={confirmExit=true},modifier=Modifier.fillMaxWidth()){Text("Sair da conta")}
                }
                1->NeroAccountCloud(user,enabled,onPlans)
                else->{
                    Text("Dados e segurança",fontSize=20.sp,fontWeight=FontWeight.Bold)
                    OutlinedButton(onClick={onPrivacy(NeroPrivacyPage.DATA)},modifier=Modifier.fillMaxWidth()){Text("Privacidade e seus dados")}
                    OutlinedButton(onClick={onPrivacy(NeroPrivacyPage.TERMS)},modifier=Modifier.fillMaxWidth()){Text("Informações de uso")}
                    OutlinedButton(onClick={onPrivacy(NeroPrivacyPage.ACCOUNT)},modifier=Modifier.fillMaxWidth()){Text("Solicitar exclusão da conta")}
                }
            }
        }
    }
    if(confirmExit)AlertDialog(onDismissRequest={confirmExit=false},title={Text("Sair da conta NERO Music?")},text={Text("Sua conta e biblioteca não serão excluídas.")},confirmButton={TextButton(enabled=enabled,onClick={confirmExit=false;onSignOut()}){Text("Sair")}},dismissButton={TextButton(onClick={confirmExit=false}){Text("Cancelar")}})
}
