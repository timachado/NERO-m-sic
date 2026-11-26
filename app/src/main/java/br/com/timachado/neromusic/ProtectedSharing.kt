package br.com.timachado.neromusic

import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class SharedPreview(val title:String,val subtitle:String,val count:Long,val single:Boolean)
private suspend fun fetchPreview(input:String):SharedPreview?{
    val id=ShareLinks.parse(input)?:return null
    val doc=NeroSupabase.rpc("nero_share_preview",org.json.JSONObject().put("p_id",id))
    if(doc=="null")return null
    val row=org.json.JSONObject(doc)
    return SharedPreview(row.getString("title"),row.getString("subtitle"),row.getLong("count"),row.getBoolean("single"))
}
private suspend fun fetchShare(context:Context,input:String,interactive:Boolean=true):Pair<String,SharedPlaylist>{
    val id=ShareLinks.parse(input)?:error("Link inválido")
    val session=NeroSupabase.session(context,interactive)
    val response=NeroSupabase.rpc("nero_share_read",org.json.JSONObject().put("p_id",id),session.access)
    val payload=org.json.JSONObject(response).getString("payload")
    return session.uid to decodeSharedPlaylist(payload)
}
private fun shareError(e:Exception):String=when(e){
    is ShareNotConfigured->"Compartilhamento protegido aguarda configuração do Supabase, domínio de compartilhamento e login Google."
    is GoogleRequired->"Entre com sua conta Google para continuar."
    else->"Não foi possível concluir. Confira a conexão, o link e a validade do compartilhamento."
}
private suspend fun createNeroShare(context:Context,playlist:SharedPlaylist,single:Boolean):String{
    if(!ShareLinks.configured||!NeroSupabase.configured)throw ShareNotConfigured()
    val payload=encodePlaylist(playlist).toString(Charsets.UTF_8)
    require(payload.toByteArray(Charsets.UTF_8).size<=180000)
    val session=NeroSupabase.session(context,interactive=false)
    val id=java.util.UUID.randomUUID().toString().replace("-","")
    val response=NeroSupabase.rpc("nero_share_create",org.json.JSONObject().put("p_id",id).put("p_payload",payload).put("p_single",single),session.access)
    check(org.json.JSONObject(response).optBoolean("created"))
    return ShareLinks.url(id)
}
@Composable
internal fun MusicShareDialog(playlist:SharedPlaylist,single:Boolean,onClose:()->Unit,send:suspend(String?,Boolean)->Unit,cover:String?=null){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    var link by remember(playlist){mutableStateOf<String?>(null)}
    val ready=ShareLinks.configured&&NeroSupabase.configured
    val owner by NeroSupabase.activeUid.collectAsState()
    fun share(action:String,withoutLink:Boolean=false){
        busy=true;message=null
        scope.launch{
            try{
                val target=if(withoutLink)null else link?:createNeroShare(context,playlist,single).also{link=it}
                if(action=="copy"){
                    val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("NERO Music",target?:neroShareCaption(playlist,single,null)))
                    message=if(target!=null)"Link copiado." else "Texto copiado."
                }else{send(target,action=="card");onClose()}
            }catch(e:CancellationException){throw e}
            catch(e:GoogleRequired){message="Entre na sua conta para criar um link NERO."}
            catch(_:Exception){message="Não foi possível criar o link NERO. Você pode compartilhar sem link."}
            finally{busy=false}
        }
    }
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text(if(single)"Compartilhar música" else "Compartilhar playlist")},text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Card(modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                    val thumbnail=cover?:playlist.songs.firstOrNull()?.videoId?.takeIf{Regex("[A-Za-z0-9_-]{11}").matches(it)}?.let{"https://i.ytimg.com/vi/"+it+"/mqdefault.jpg"}
                    Box(Modifier.size(76.dp),contentAlignment=Alignment.Center){
                        Icon(if(single)Icons.Default.MusicNote else Icons.Default.QueueMusic,null,tint=Color(0xFF00E5FF),modifier=Modifier.size(40.dp))
                        thumbnail?.let{AsyncImage(model=it,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))}
                    }
                    Column(Modifier.weight(1f).padding(start=12.dp)){
                        Text("NERO MUSIC",color=Color(0xFF39FF14),style=MaterialTheme.typography.labelSmall)
                        Text(playlist.name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(if(single)displayArtistName(playlist.songs.firstOrNull()?.artist.orEmpty()) else playlist.songs.size.toString()+" músicas",maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
            Button(enabled=!busy&&playlist.songs.isNotEmpty()&&ready&&owner!=null,onClick={share("send")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("WhatsApp e redes sociais")}
            OutlinedButton(enabled=!busy&&playlist.songs.isNotEmpty()&&ready&&owner!=null,onClick={share("copy")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.ContentCopy,null);Spacer(Modifier.width(8.dp));Text("Copiar link NERO")}
            if(single)OutlinedButton(enabled=!busy,onClick={share("card",withoutLink=!ready||owner==null)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Image,null);Spacer(Modifier.width(8.dp));Text("Status e Stories")}
            if(!ready)Text("Link NERO indisponível no momento.",style=MaterialTheme.typography.bodySmall)
            else if(owner==null)Text("Entre na sua conta para compartilhar um link NERO.",style=MaterialTheme.typography.bodySmall)
            else Text("Quem receber pode abrir no NERO Music. Link válido por 7 dias.",style=MaterialTheme.typography.bodySmall)
            TextButton(enabled=!busy&&playlist.songs.isNotEmpty(),onClick={share("send",withoutLink=true)},modifier=Modifier.fillMaxWidth()){Text(if(single)"Compartilhar sem link" else "Compartilhar arquivo da playlist")}
            if(single&&ready&&owner!=null)TextButton(enabled=!busy,onClick={share("card",withoutLink=true)},modifier=Modifier.fillMaxWidth()){Text("Status sem link")}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let{Text(it,color=if(it=="Link copiado."||it=="Texto copiado.")Color(0xFF39FF14)else MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(enabled=!busy,onClick=onClose){Text("Fechar")}})
}
@Composable
internal fun ReceiveShareDialog(onClose:()->Unit,onImport:(SharedPlaylist)->Unit,onPlay:((SharedPlaylist)->Unit)?,initialLink:String=""){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var code by remember(initialLink){mutableStateOf(initialLink)}
    var result by remember{mutableStateOf<Pair<String,SharedPlaylist>?>(null)}
    var preview by remember{mutableStateOf<SharedPreview?>(null)}
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    fun open(interactive:Boolean=true){
        busy=true;message=null;result=null
        scope.launch{
            try{result=fetchShare(context,code,interactive)}
            catch(e:CancellationException){throw e}catch(e:GoogleRequired){message="Entre com Google para abrir este link."}catch(e:Exception){message=shareError(e)}
            finally{busy=false}
        }
    }
    LaunchedEffect(code){
        preview=null
        if(ShareLinks.parse(code)!=null){
            try{preview=fetchPreview(code)}catch(e:CancellationException){throw e}catch(_:Exception){/* No public preview for older or expired links. */}
        }
    }
    LaunchedEffect(initialLink){if(initialLink.isNotBlank()&&ShareLinks.parse(initialLink)!=null)open(false)}
    fun access(action:(SharedPlaylist)->Unit){
        val loaded=result?:return
        busy=true
        scope.launch{
            try{
                // Read again on the server, enforcing expiry and current Google identity.
                val fresh=fetchShare(context,code)
                check(fresh.first==loaded.first)
                action(fresh.second)
            }catch(e:CancellationException){throw e}catch(e:Exception){result=null;message=shareError(e)}
            finally{busy=false}
        }
    }
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text("Abrir compartilhamento")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(result==null){
                Card(modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(if(preview?.single==true)Icons.Default.MusicNote else Icons.Default.QueueMusic,null,tint=Color(0xFF00E5FF),modifier=Modifier.size(52.dp))
                        Column(Modifier.weight(1f).padding(start=12.dp)){
                            Text("NERO MUSIC",color=Color(0xFF39FF14),style=MaterialTheme.typography.labelSmall)
                            Text(preview?.title?.takeIf{it.isNotBlank()}?:"Conteúdo compartilhado",style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                            preview?.let{Text(it.subtitle,maxLines=1,overflow=TextOverflow.Ellipsis);Text(it.count.toString()+" faixa(s)",style=MaterialTheme.typography.bodySmall)}
                        }
                    }
                }
                Text(if(initialLink.isNotBlank())"Entre com Google para ouvir ou salvar na biblioteca." else "Cole o link recebido e entre com Google.")
            }
            if(initialLink.isBlank())OutlinedTextField(code,{code=it.take(512);result=null},enabled=!busy,singleLine=true,label={Text("Link NERO")})
            if(result==null)Button(enabled=!busy&&ShareLinks.parse(code)!=null,onClick={open()},modifier=Modifier.fillMaxWidth()){Text("Entrar com Google para ouvir")}
            result?.second?.let{playlist->
                Text(playlist.name,style=MaterialTheme.typography.titleMedium)
                Text(playlist.songs.size.toString()+" faixa(s)")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=220.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    itemsIndexed(playlist.songs){index,song->
                        Row(verticalAlignment=Alignment.CenterVertically){
                            val thumbnail=song.videoId?.takeIf{Regex("[A-Za-z0-9_-]{11}").matches(it)}?.let{"https://i.ytimg.com/vi/"+it+"/mqdefault.jpg"}
                            if(thumbnail!=null)AsyncImage(model=thumbnail,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)))
                            else Icon(Icons.Default.MusicNote,null,tint=Color(0xFF00E5FF),modifier=Modifier.size(44.dp))
                            Column(Modifier.weight(1f).padding(start=10.dp)){Text(song.title,maxLines=2,overflow=TextOverflow.Ellipsis);Text(song.artist,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)}
                        }
                    }
                }
                if(onPlay!=null)Button(enabled=!busy,onClick={access(onPlay)},modifier=Modifier.fillMaxWidth()){Text("Ouvir no NERO")}
                OutlinedButton(enabled=!busy,onClick={access(onImport)},modifier=Modifier.fillMaxWidth()){Text("Salvar cópia na biblioteca")}
                Text("A cópia salva fica no aparelho; a validade do link não apaga a cópia.",style=MaterialTheme.typography.bodySmall)
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(enabled=!busy,onClick=onClose){Text("Fechar")}})
}
