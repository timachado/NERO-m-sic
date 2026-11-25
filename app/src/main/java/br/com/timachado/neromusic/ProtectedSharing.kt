package br.com.timachado.neromusic

import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
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
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val COLLECTION="nero_shared"
private const val PREVIEWS="nero_shared_previews"
private data class SharedPreview(val title:String,val subtitle:String,val count:Long,val single:Boolean)
private suspend fun fetchPreview(input:String):SharedPreview?{
    val id=ShareLinks.parse(input)?:return null
    val doc=FirebaseFirestore.getInstance().collection(PREVIEWS).document(id).get(Source.SERVER).await()
    if(!doc.exists())return null
    return SharedPreview(doc.getString("title").orEmpty(),doc.getString("subtitle").orEmpty(),doc.getLong("count")?:0L,doc.getBoolean("single")?:false)
}


private class GoogleRequired:Exception()
private class ShareNotConfigured:Exception()
private suspend fun googleSession(context:Context,interactive:Boolean):String{
    val auth=authOrNull(context)?:throw ShareNotConfigured()
    val current=auth.currentUser
    if(current!=null&&current.getIdToken(false).await().signInProvider=="google.com")return current.uid
    if(!interactive)throw GoogleRequired()
    val client=webClientId(context).takeIf{it.isNotBlank()}?:throw ShareNotConfigured()
    val manager=CredentialManager.create(context)
    val option=GetSignInWithGoogleOption.Builder(client).build()
    val credential=manager.getCredential(context,GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
    require(credential is CustomCredential&&credential.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
    val token=GoogleIdTokenCredential.createFrom(credential.data).idToken
    val user=auth.signInWithCredential(GoogleAuthProvider.getCredential(token,null)).await().user?:throw GoogleRequired()
    if(user.getIdToken(true).await().signInProvider!="google.com")throw GoogleRequired()
    return user.uid
}
private suspend fun publishShare(context:Context,playlist:SharedPlaylist,single:Boolean):String{
    if(!ShareLinks.configured)throw ShareNotConfigured()
    val uid=googleSession(context,true)
    val payload=encodePlaylist(playlist).toString(Charsets.UTF_8)
    require(payload.toByteArray(Charsets.UTF_8).size<=180000)
    val code=UUID.randomUUID().toString().replace("-","")
    val db=FirebaseFirestore.getInstance()
    val content=db.collection(COLLECTION).document(code)
    val preview=db.collection(PREVIEWS).document(code)
    // Public presentation contains only explicitly disclosed display metadata.
    // Never store media IDs, playable URLs, owner UID or the track list here.
    val batch=db.batch()
    batch.set(content,mapOf("owner" to uid,"createdAt" to FieldValue.serverTimestamp(),"payload" to payload))
    batch.set(preview,mapOf("createdAt" to FieldValue.serverTimestamp(),"title" to playlist.name.take(100),
        "subtitle" to if(single)playlist.songs.first().artist.take(300) else "Playlist NERO Music",
        "count" to playlist.songs.size,"single" to single))
    batch.commit().await()
    val doc=content.get(Source.SERVER).await()
    val publicDoc=preview.get(Source.SERVER).await()
    check(doc.exists()&&!doc.metadata.hasPendingWrites()&&publicDoc.exists()&&!publicDoc.metadata.hasPendingWrites())
    return ShareLinks.url(code)
}
private suspend fun fetchShare(context:Context,input:String,interactive:Boolean=true):Pair<String,SharedPlaylist>{
    val code=ShareLinks.parse(input)?:error("Link inválido")
    val uid=googleSession(context,interactive)
    val snap=FirebaseFirestore.getInstance().collection(COLLECTION).document(code).get(Source.SERVER).await()
    check(snap.exists())
    check(googleSession(context,false)==uid)
    return uid to decodeSharedPlaylist(snap.getString("payload")?:error("Conteúdo indisponível"))
}
private fun shareError(e:Exception):String=when(e){
    is ShareNotConfigured->"Compartilhamento protegido aguarda configuração do Firebase, domínio de compartilhamento e login Google."
    is GoogleRequired->"Entre com sua conta Google para continuar."
    else->"Não foi possível concluir. Confira a conexão, o link e a validade do compartilhamento."
}
@Composable
internal fun ProtectedShareDialog(playlist:SharedPlaylist,single:Boolean,onClose:()->Unit,send:suspend(String,Boolean)->Unit,cover:String?=null){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    var link by remember{mutableStateOf<String?>(null)}
    fun share(action:String){
        busy=true;message=null
        scope.launch{
            try{
                val url=link?:publishShare(context,playlist,single).also{link=it}
                if(action=="copy"){
                    val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("NERO Music",url))
                    message="Link copiado."
                }else{send(url,action=="card");onClose()}
            }catch(e:CancellationException){throw e}catch(e:Exception){message=shareError(e)}
            finally{busy=false}
        }
    }
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text(if(single)"Compartilhar música" else "Compartilhar playlist")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
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
                        Text(if(single)playlist.songs.firstOrNull()?.artist.orEmpty() else playlist.songs.size.toString()+" músicas",maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
            Button(enabled=!busy&&playlist.songs.isNotEmpty(),onClick={share("send")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("WhatsApp e redes sociais")}
            OutlinedButton(enabled=!busy&&playlist.songs.isNotEmpty(),onClick={share("copy")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.ContentCopy,null);Spacer(Modifier.width(8.dp));Text("Copiar link")}
            if(single)OutlinedButton(enabled=!busy,onClick={share("card")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Image,null);Spacer(Modifier.width(8.dp));Text("Status e Stories")}
            Text("Acesso com conta Google · link válido por 7 dias.",style=MaterialTheme.typography.bodySmall)
            Text("Quem recebe o link pode ver o título, artista (música) e quantidade de faixas. A lista completa e a reprodução exigem Google. O card público não inclui áudio.",style=MaterialTheme.typography.bodySmall)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let{Text(it,color=if(it=="Link copiado.")Color(0xFF39FF14)else MaterialTheme.colorScheme.error)}
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
