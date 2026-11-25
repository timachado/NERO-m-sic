package br.com.timachado.neromusic

import android.content.Context
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
private suspend fun publishShare(context:Context,playlist:SharedPlaylist):String{
    if(!ShareLinks.configured)throw ShareNotConfigured()
    val uid=googleSession(context,true)
    val payload=encodePlaylist(playlist).toString(Charsets.UTF_8)
    require(payload.toByteArray(Charsets.UTF_8).size<=180000)
    val code=UUID.randomUUID().toString().replace("-","")
    FirebaseFirestore.getInstance().collection(COLLECTION).document(code)
        .set(mapOf("owner" to uid,"createdAt" to FieldValue.serverTimestamp(),"payload" to payload)).await()
    // Do not use a locally queued write as a public share.
    val doc=FirebaseFirestore.getInstance().collection(COLLECTION).document(code).get(Source.SERVER).await()
    check(doc.exists()&&!doc.metadata.hasPendingWrites())
    return ShareLinks.url(code)
}
private suspend fun fetchShare(context:Context,input:String):Pair<String,SharedPlaylist>{
    val code=ShareLinks.parse(input)?:error("Link inválido")
    val uid=googleSession(context,true)
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
internal fun ProtectedShareDialog(playlist:SharedPlaylist,single:Boolean,onClose:()->Unit,send:suspend(String,Boolean)->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    fun share(card:Boolean){
        busy=true;message=null
        scope.launch{
            try{val code=publishShare(context,playlist);send(code,card);onClose()}
            catch(e:CancellationException){throw e}catch(e:Exception){message=shareError(e)}
            finally{busy=false}
        }
    }
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text(if(single)"Compartilhar música" else "Compartilhar playlist")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(playlist.name+" · "+playlist.songs.size+" faixa(s)")
            Text("Somente pessoas com o link e uma sessão Google válida podem abrir o conteúdo compartilhado no NERO. Link válido por 7 dias.")
            Text("Qualquer pessoa com o link pode entrar usando a própria conta Google. Não é um convite restrito a um e-mail específico.",style=MaterialTheme.typography.bodySmall)
            Button(enabled=!busy&&playlist.songs.isNotEmpty(),onClick={share(false)},modifier=Modifier.fillMaxWidth()){Text("Compartilhar link protegido")}
            if(single)OutlinedButton(enabled=!busy,onClick={share(true)},modifier=Modifier.fillMaxWidth()){Text("Card para Status e Stories")}
            if(single)Text("A arte mostra título, artista e capa publicamente; só o acesso pelo link exige login. Sem áudio no card.",style=MaterialTheme.typography.bodySmall)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(enabled=!busy,onClick=onClose){Text("Fechar")}})
}
@Composable
internal fun ReceiveShareDialog(onClose:()->Unit,onImport:(SharedPlaylist)->Unit,onPlay:((SharedPlaylist)->Unit)?,initialLink:String=""){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var code by remember(initialLink){mutableStateOf(initialLink)}
    var result by remember{mutableStateOf<Pair<String,SharedPlaylist>?>(null)}
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    fun open(){
        busy=true;message=null;result=null
        scope.launch{
            try{result=fetchShare(context,code)}
            catch(e:CancellationException){throw e}catch(e:Exception){message=shareError(e)}
            finally{busy=false}
        }
    }
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
            Text("Cole o link recebido e entre com sua conta Google.")
            if(initialLink.isBlank())OutlinedTextField(code,{code=it.take(512);result=null},enabled=!busy,singleLine=true,label={Text("Link NERO")})
            Button(enabled=!busy&&ShareLinks.parse(code)!=null,onClick={open()},modifier=Modifier.fillMaxWidth()){Text("Acessar com Google")}
            result?.second?.let{playlist->
                Text(playlist.name,style=MaterialTheme.typography.titleMedium)
                Text(playlist.songs.size.toString()+" faixa(s)")
                if(onPlay!=null)Button(enabled=!busy,onClick={access(onPlay)},modifier=Modifier.fillMaxWidth()){Text("Ouvir no NERO")}
                OutlinedButton(enabled=!busy,onClick={access(onImport)},modifier=Modifier.fillMaxWidth()){Text("Salvar cópia na biblioteca")}
                Text("A cópia salva fica no aparelho; a validade do link não apaga a cópia.",style=MaterialTheme.typography.bodySmall)
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(enabled=!busy,onClick=onClose){Text("Fechar")}})
}
