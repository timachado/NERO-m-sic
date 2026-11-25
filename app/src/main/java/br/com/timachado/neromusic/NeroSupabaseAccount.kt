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
    var notice by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){
        try{user=NeroSupabase.session(context,false)}
        catch(e:CancellationException){throw e}catch(_:Exception){}
        finally{busy=false}
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")};Text("Conta e configurações",fontSize=23.sp,fontWeight=FontWeight.Bold)}
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.align(Alignment.CenterHorizontally).size(88.dp),contentAlignment=Alignment.Center){
            Icon(Icons.Default.AccountCircle,"Foto do perfil",tint=MaterialTheme.colorScheme.secondary,modifier=Modifier.fillMaxSize())
            user?.avatar?.let{photo->NeroArtwork(model=photo,contentDescription="Foto da conta Google",modifier=Modifier.fillMaxSize().clip(CircleShape),contentScale=ContentScale.Crop)}
        }
        if(!NeroSupabase.configured)Text("O login Google aguarda configuração do Supabase. Sua biblioteca local permanece disponível.",color=Color.LightGray)
        else if(user==null){
            Text("Bem-vindo ao NERO Music",fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text("Entre com Google para compartilhar e acessar músicas e playlists recebidas.",color=Color.LightGray)
            Button(enabled=!busy,onClick={busy=true;notice=null;scope.launch{
                try{user=NeroSupabase.session(context,true)}
                catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível entrar. Confira a configuração Google e a conexão."}
                finally{busy=false}
            }},modifier=Modifier.fillMaxWidth()){Text("Continuar com Google")}
        }else{
            Text(user?.displayName?.takeIf{it.isNotBlank()}?:"Minha conta",fontSize=23.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(user?.email.orEmpty(),fontSize=14.sp,color=Color.LightGray,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text("Conta Google conectada",color=MaterialTheme.colorScheme.primary)
            Text("Nome e foto fornecidos pelo Google, quando disponíveis.",fontSize=12.sp,color=Color.LightGray)
            TextButton(onClick={if(!openBrowser(context,android.net.Uri.parse("https://myaccount.google.com/personal-info")))notice="Não foi possível abrir a conta Google."}){Icon(Icons.Default.Edit,null);Spacer(Modifier.width(8.dp));Text("Gerenciar perfil Google")}
            OutlinedButton(enabled=!busy,onClick={busy=true;notice=null;scope.launch{
                try{NeroSupabase.signOut(context);user=null}
                catch(e:CancellationException){throw e}catch(_:Exception){user=null;notice="Sessão local encerrada; revogação remota não confirmada."}
                finally{busy=false}
            }},modifier=Modifier.fillMaxWidth()){Text("Sair da conta")}
        }
        Text("Suas playlists e favoritos continuam neste aparelho. A conta não sincroniza a biblioteca entre dispositivos.",color=Color.LightGray,fontSize=12.sp)
        }
        }
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
        Text("Preferências musicais armazenadas localmente. ${hidden.size} músicas ocultadas das recomendações.",fontSize=13.sp,color=Color.LightGray)
        TextButton(onClick={if(!openBrowser(context,android.net.Uri.parse("https://timachado.ifree.page")))notice="Não foi possível abrir o site."}){Icon(Icons.Default.Language,null);Spacer(Modifier.width(8.dp));Text("Site e suporte T.I. Machado")}
        Text("NERO Music · "+BuildConfig.VERSION_NAME,fontSize=12.sp,color=Color.LightGray)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        notice?.let{Text(it,color=Color.LightGray)}
    }
}
