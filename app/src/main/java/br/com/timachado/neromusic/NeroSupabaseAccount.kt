package br.com.timachado.neromusic
import androidx.compose.foundation.layout.*
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
internal fun NeroSupabaseAccount(onBack:()->Unit,modifier:Modifier=Modifier){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var user by remember{mutableStateOf<NeroSession?>(null)}
    var busy by remember{mutableStateOf(true)}
    var notice by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){
        try{user=NeroSupabase.session(context,false)}
        catch(e:CancellationException){throw e}catch(_:Exception){}
        finally{busy=false}
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")};Text("Minha conta",fontSize=24.sp,fontWeight=FontWeight.Bold)}
        Icon(Icons.Default.AccountCircle,null,tint=Color(0xFF00E5FF),modifier=Modifier.align(Alignment.CenterHorizontally).size(72.dp))
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
            Text(user?.email.orEmpty(),fontSize=20.sp)
            Text("Sessão Google · Supabase",color=Color(0xFF39FF14))
            OutlinedButton(enabled=!busy,onClick={busy=true;notice=null;scope.launch{
                try{NeroSupabase.signOut(context);user=null}
                catch(e:CancellationException){throw e}catch(_:Exception){user=null;notice="Sessão local encerrada; revogação remota não confirmada."}
                finally{busy=false}
            }},modifier=Modifier.fillMaxWidth()){Text("Sair da conta")}
        }
        Text("Suas playlists e favoritos existentes continuam no aparelho.",color=Color.LightGray)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        notice?.let{Text(it,color=Color.LightGray)}
    }
}
