package br.com.timachado.neromusic
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NeroSupabaseAccount(onBack:()->Unit,modifier:Modifier=Modifier,favoriteCount:Int=0,autoplay:Boolean=false,onAutoplay:()->Unit={},onLibrary:()->Unit={},onDownloads:()->Unit={},onHistory:()->Unit={},wifiOnly:Boolean=true,onWifiChange:(Boolean)->Unit={},economicalDownload:Boolean=false,onDownloadQuality:(Boolean)->Unit={}){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var economical by remember{mutableStateOf(economicalStreaming(context))}
    val followed=rememberFollowedArtists()
    val albums=rememberSavedAlbums()
    val hidden=rememberHiddenSongs()
    var user by remember{mutableStateOf<NeroSession?>(null)}
    var busy by remember{mutableStateOf(true)}
    var restoringSession by remember{mutableStateOf(true)}
    var notice by remember{mutableStateOf<String?>(null)}
    var privacyPage by remember{mutableStateOf<NeroPrivacyPage?>(null)}
    var showPlans by remember{mutableStateOf(false)}
    var manageAccount by remember{mutableStateOf(false)}
    androidx.activity.compose.BackHandler(enabled=showPlans||manageAccount){if(showPlans)showPlans=false else manageAccount=false}
    privacyPage?.let{NeroPrivacyDialog(it,onClose={privacyPage=null})}
    LaunchedEffect(Unit){
        try{user=NeroSupabase.session(context,false)}
        catch(e:CancellationException){throw e}catch(_:Exception){}
        finally{busy=false;restoringSession=false}
    }
    if(showPlans){PlansScreen(onBack={showPlans=false},modifier=modifier);return}
    val connected=user
    if(manageAccount&&connected!=null){
        key(connected.uid){
            NeroManageAccountScreen(connected,modifier,enabled=!busy,onBack={manageAccount=false},
                onPlans={showPlans=true},onPrivacy={privacyPage=it},
                onHistory={manageAccount=false;onHistory()},
                onSignOut={busy=true;notice=null;scope.launch{
                    try{NeroSupabase.signOut(context);user=null;manageAccount=false}
                    catch(e:CancellationException){throw e}catch(_:Exception){user=null;manageAccount=false;notice="Sessão local encerrada; revogação remota não confirmada."}
                    finally{busy=false}
                }})
        }
        return
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")};Text("Conta e configurações",fontSize=23.sp,fontWeight=FontWeight.Bold)}
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.align(Alignment.CenterHorizontally).size(88.dp),contentAlignment=Alignment.Center){
            Icon(Icons.Default.AccountCircle,"Foto do perfil",tint=MaterialTheme.colorScheme.secondary,modifier=Modifier.fillMaxSize())
            user?.avatar?.let{photo->NeroArtwork(model=photo,contentDescription="Foto da conta Google",modifier=Modifier.fillMaxSize().clip(CircleShape),contentScale=ContentScale.Crop)}
        }
        if(restoringSession){
            Text("Carregando sua conta…",fontSize=20.sp,fontWeight=FontWeight.Bold)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }else if(user==null){
            Text("Bem-vindo ao NERO Music",fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text("Entre ou crie sua conta com Google para compartilhar músicas e playlists.",color=Color.LightGray)
            if(!NeroSupabase.configured)Text("O login ainda não está habilitado nesta versão. Você pode usar a biblioteca sem conta.",color=Color.LightGray,fontSize=12.sp)
            val continueWithGoogle:()->Unit={
                if(!NeroSupabase.configured){
                    notice="A autenticação Google ainda precisa ser ativada pela equipe do NERO Music."
                }else{busy=true;notice=null;scope.launch{
                try{user=NeroSupabase.session(context,true)}
                catch(e:CancellationException){throw e}catch(e:Exception){notice=neroGoogleLoginMessage(e)}
                finally{busy=false}
                }}
            }
            Button(enabled=!busy,onClick=continueWithGoogle,modifier=Modifier.fillMaxWidth()){Text("Continuar com Google")}
            OutlinedButton(enabled=!busy,onClick=continueWithGoogle,modifier=Modifier.fillMaxWidth()){Text("Criar conta com Google")}
            notice?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
        }else{
            Text(user?.displayName?.takeIf{it.isNotBlank()}?:"Minha conta",fontSize=23.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(user?.email.orEmpty(),fontSize=14.sp,color=Color.LightGray,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text("Conta Google conectada",color=MaterialTheme.colorScheme.primary)
            Text("Nome e foto fornecidos pelo Google, quando disponíveis.",fontSize=12.sp,color=Color.LightGray)
            Button(enabled=!busy,onClick={manageAccount=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.ManageAccounts,null);Spacer(Modifier.width(8.dp));Text("Gerenciar minha conta")}
            OutlinedButton(enabled=!busy,onClick={busy=true;notice=null;scope.launch{
                try{NeroSupabase.signOut(context);user=null}
                catch(e:CancellationException){throw e}catch(_:Exception){user=null;notice="Sessão local encerrada; revogação remota não confirmada."}
                finally{busy=false}
            }},modifier=Modifier.fillMaxWidth()){Text("Sair da conta")}
        }
        Text("Favoritos, playlists online, artistas seguidos e álbuns salvos ficam vinculados à conta conectada. O histórico online pode ser sincronizado mediante ativação. Arquivos locais, downloads e preferências do aparelho permanecem locais. A nuvem depende da disponibilidade do serviço.",color=Color.LightGray,fontSize=12.sp)
        }
        }
        if(user!=null)OutlinedButton(enabled=!busy,onClick={manageAccount=true},modifier=Modifier.fillMaxWidth()){Text("Perfil, plano e NERO Cloud")}
        if(user==null&&!restoringSession)OutlinedButton(onClick={showPlans=true},modifier=Modifier.fillMaxWidth()){Text("Conhecer os planos NERO")}
        Text("Sua biblioteca",fontSize=20.sp,fontWeight=FontWeight.Bold)
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("$favoriteCount músicas curtidas · ${followed.size} artistas · ${albums.size} álbuns",color=Color.LightGray,fontSize=13.sp)
                TextButton(onClick=onLibrary){Icon(Icons.Default.LibraryMusic,null);Spacer(Modifier.width(8.dp));Text("Abrir biblioteca")}
                TextButton(onClick=onDownloads){Icon(Icons.Default.Download,null);Spacer(Modifier.width(8.dp));Text("Downloads")}
                TextButton(onClick=onHistory){Icon(Icons.Default.History,null);Spacer(Modifier.width(8.dp));Text("Histórico de reprodução")}
            }
        }
        Text("Reprodução",fontSize=20.sp,fontWeight=FontWeight.Bold)
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Reprodução automática");Text("Continuar com músicas semelhantes ao terminar a fila.",fontSize=12.sp,color=Color.LightGray)};Switch(checked=autoplay,onCheckedChange={onAutoplay()})}
        }
        Text("Qualidade do áudio",fontSize=20.sp,fontWeight=FontWeight.Bold)
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Reprodução online",fontWeight=FontWeight.Medium)
                Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=!economical,onClick={economical=false;setEconomicalStreaming(context,false)});Text("Maior qualidade disponível")}
                Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=economical,onClick={economical=true;setEconomicalStreaming(context,true)});Text("Economia de dados")}
                Text("Aplica-se às próximas aberturas de áudio do catálogo compatível, sem reiniciar a música atual.",fontSize=12.sp,color=Color.LightGray)
            }
        }
        Text("Downloads",fontSize=20.sp,fontWeight=FontWeight.Bold)
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Baixar somente por Wi-Fi");Text("Evita downloads pela rede móvel.",fontSize=12.sp,color=Color.LightGray)};Switch(checked=wifiOnly,onCheckedChange=onWifiChange)}
                HorizontalDivider()
                Text("Qualidade de novos downloads",fontWeight=FontWeight.Medium)
                Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=!economicalDownload,onClick={onDownloadQuality(false)});Text("Maior qualidade disponível")}
                Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=economicalDownload,onClick={onDownloadQuality(true)});Text("Economizar armazenamento")}
                Text("Arquivos já baixados não são alterados.",fontSize=12.sp,color=Color.LightGray)
            }
        }
        Text("Privacidade e suporte",fontSize=20.sp,fontWeight=FontWeight.Bold)
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(12.dp)){
                TextButton(onClick={privacyPage=NeroPrivacyPage.DATA}){Icon(Icons.Default.Shield,null);Spacer(Modifier.width(8.dp));Text("Privacidade e seus dados")}
                TextButton(onClick={privacyPage=NeroPrivacyPage.TERMS}){Icon(Icons.Default.Description,null);Spacer(Modifier.width(8.dp));Text("Informações de uso")}
                TextButton(onClick={privacyPage=NeroPrivacyPage.ACCOUNT}){Icon(Icons.Default.ManageAccounts,null);Spacer(Modifier.width(8.dp));Text("Solicitar exclusão da conta")}
            }
        }
        Text("${hidden.size} músicas ocultadas das recomendações. As preferências de músicas ocultadas acompanham a conta conectada quando a nuvem estiver disponível.",fontSize=13.sp,color=Color.LightGray)
        TextButton(onClick={if(!openBrowser(context,android.net.Uri.parse("https://timachado.ifree.page")))notice="Não foi possível abrir o site."}){Icon(Icons.Default.Language,null);Spacer(Modifier.width(8.dp));Text("Site e suporte T.I. Machado")}
        Text("NERO Music · "+BuildConfig.VERSION_NAME,fontSize=12.sp,color=Color.LightGray)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(user!=null)notice?.let{Text(it,color=Color.LightGray)}
    }
}
