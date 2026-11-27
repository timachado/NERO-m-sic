package br.com.timachado.neromusic

import android.content.Context
import android.util.Patterns
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


internal fun authOrNull(context:Context): FirebaseAuth? = runCatching {
    val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        ?: FirebaseApp.initializeApp(context)
    app?.let { FirebaseAuth.getInstance(it) }
}.getOrNull()
internal fun webClientId(context:Context):String {
    val id=context.resources.getIdentifier("default_web_client_id","string",context.packageName)
    return if(id==0) "" else context.getString(id)
}

@Composable
internal fun AccountScreen(onBack:()->Unit,onPlans:()->Unit,onCloud:()->Unit,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val auth=remember { authOrNull(context) }
    val manager=remember { CredentialManager.create(context) }
    val clientId=remember { webClientId(context) }
    val scope=rememberCoroutineScope()
    var user by remember { mutableStateOf<FirebaseUser?>(auth?.currentUser) }
    var creating by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    DisposableEffect(auth) {
        val listener=FirebaseAuth.AuthStateListener { user=it.currentUser }
        auth?.addAuthStateListener(listener)
        onDispose { auth?.removeAuthStateListener(listener) }
    }
    fun execute(block:suspend ()->Unit) {
        if(busy) return
        busy=true; message=null
        scope.launch {
            try { block() }
            catch(e:CancellationException) { throw e }
            catch(e:GetCredentialCancellationException) { message="Login cancelado." }
            catch(e:Exception) { message="Não foi possível concluir. Confira a conexão, os dados e a configuração da conta." }
            finally { busy=false }
        }
    }
    if(deleteConfirm) AlertDialog(onDismissRequest={deleteConfirm=false},title={Text("Excluir sua conta?")},text={Text("Isso exclui sua conta de autenticação. Os arquivos e playlists locais permanecem no aparelho. Pode ser necessário entrar novamente antes da exclusão.")},
        confirmButton={TextButton(onClick={
            deleteConfirm=false
            execute {
                val current=auth?.currentUser ?: return@execute
                current.delete().await()
                runCatching { manager.clearCredentialState(ClearCredentialStateRequest()) }
                user=null;password="";message="Conta excluída."
            }
        }){Text("Excluir conta")}},dismissButton={TextButton(onClick={deleteConfirm=false}){Text("Cancelar")}})
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text("Minha conta",fontWeight=FontWeight.Bold,fontSize=24.sp)
        }
        Icon(Icons.Default.AccountCircle,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.align(Alignment.CenterHorizontally).size(72.dp))
        if(auth==null) {
            Text("Acesso como visitante",fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text("A autenticação está indisponível nesta versão. Você pode continuar usando o NERO Music normalmente.",color=Color.LightGray)
            Button(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("Continuar ouvindo")}
        } else if(user!=null) {
            val current=user!!
            Text(current.displayName?.takeIf{it.isNotBlank()} ?: "Minha conta NERO Music",fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text(current.email.orEmpty(),color=Color.LightGray)
            if(!current.isEmailVerified) {
                Text("E-mail ainda não verificado.",color=Color.LightGray)
                OutlinedButton(enabled=!busy,onClick={execute { current.sendEmailVerification().await();message="Solicitação de verificação enviada." }},modifier=Modifier.fillMaxWidth()){Text("Enviar verificação")}
                TextButton(enabled=!busy,onClick={execute { current.reload().await();user=auth.currentUser;message=if(auth.currentUser?.isEmailVerified==true)"E-mail verificado." else "Verificação pendente." }}){Text("Atualizar verificação")}
            }
            Text("Favoritos, histórico e playlists permanecem no dispositivo. A sincronização entre aparelhos ainda não está disponível.",color=Color.LightGray)
            OutlinedButton(enabled=!busy,onClick={execute {
                auth.signOut();password="";email=""
                manager.clearCredentialState(ClearCredentialStateRequest())
                message="Sessão encerrada."
            }},modifier=Modifier.fillMaxWidth()){Text("Sair da conta")}
            TextButton(enabled=!busy,onClick={deleteConfirm=true}){Text("Excluir minha conta",color=MaterialTheme.colorScheme.error)}
        } else {
            Text(if(creating)"Crie sua conta NERO Music" else "Bem-vindo ao NERO Music",fontSize=24.sp,fontWeight=FontWeight.Bold)
            Text("O som da sua liberdade.",color=Color.LightGray)
            if(clientId.isNotBlank()) OutlinedButton(enabled=!busy,onClick={execute {
                val option=GetSignInWithGoogleOption.Builder(clientId).build()
                val request=GetCredentialRequest.Builder().addCredentialOption(option).build()
                val credential=manager.getCredential(context,request).credential
                require(credential is CustomCredential && credential.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                val token=GoogleIdTokenCredential.createFrom(credential.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token,null)).await()
                password=""
            }},modifier=Modifier.fillMaxWidth()){Text("Continuar com Google")}
            else Text("Login Google aguarda configuração OAuth.",color=Color.LightGray,fontSize=12.sp)
            OutlinedTextField(email,{email=it},modifier=Modifier.fillMaxWidth(),label={Text("E-mail")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),enabled=!busy)
            OutlinedTextField(password,{password=it},modifier=Modifier.fillMaxWidth(),label={Text("Senha")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),visualTransformation=PasswordVisualTransformation(),enabled=!busy)
            val valid=Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
            Button(enabled=!busy&&valid&&password.length>=6,onClick={execute {
                val result=if(creating) auth.createUserWithEmailAndPassword(email.trim(),password).await()
                    else auth.signInWithEmailAndPassword(email.trim(),password).await()
                password=""
                if(creating && result.user?.isEmailVerified==false) {
                    message="Conta criada. Solicite a verificação do e-mail em Minha conta."
                }
            }},modifier=Modifier.fillMaxWidth()){Text(if(creating)"Criar conta" else "Entrar")}
            TextButton(enabled=!busy,onClick={creating=!creating;password="";message=null}){Text(if(creating)"Já tenho uma conta" else "Criar uma conta")}
            if(!creating) TextButton(enabled=!busy&&valid,onClick={execute {
                auth.sendPasswordResetEmail(email.trim()).await();message="Se a conta estiver cadastrada, confira seu e-mail."
            }}){Text("Esqueci minha senha")}
            TextButton(onClick=onBack){Text("Continuar como visitante")}
        }
        if(BuildConfig.DEBUG && user != null) OutlinedButton(enabled=!busy,onClick=onCloud,modifier=Modifier.fillMaxWidth()) { Text("Backup da biblioteca · teste") }
        OutlinedButton(enabled=!busy,onClick=onPlans,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Star,null); Spacer(Modifier.width(8.dp)); Text("Conhecer planos NERO Music") }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let{Text(it,color=Color.LightGray)}
    }
}
