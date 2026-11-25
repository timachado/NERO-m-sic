package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun NeroAccountCloud(user:NeroSession,enabled:Boolean){
    val context=LocalContext.current
    val pull by NeroAccountSync.status.collectAsState()
    val scope=rememberCoroutineScope()
    var remote by remember(user.uid){mutableStateOf<CloudLibraryRevision?>(null)}
    var plan by remember(user.uid){mutableStateOf("Não verificado")}
    var busy by remember(user.uid){mutableStateOf(false)}
    var notice by remember(user.uid){mutableStateOf<String?>(null)}
    var confirm by remember(user.uid){mutableStateOf<String?>(null)}
    fun refresh(){if(busy)return;busy=true;notice=null;scope.launch{
        try{
            remote=readCloudLibrary(context,user.uid)
            notice="Biblioteca da conta consultada."
        }catch(e:CancellationException){throw e}catch(_:Exception){remote=null;notice="Biblioteca na nuvem indisponível. Seus dados locais continuam intactos."}
        try{
            val current=NeroSupabase.session(context,false);require(current.uid==user.uid)
            val row=JSONObject(NeroSupabase.rpc("nero_plan_read_v1",JSONObject(),current.access))
            plan=when(row.optString("status")){"active"->"Assinatura ativa";"none"->"Sem assinatura ativa";"expired"->"Assinatura expirada";else->"Não verificado"}
        }catch(e:CancellationException){throw e}catch(_:Exception){plan="Serviço de planos ainda indisponível"}
        finally{busy=false}
    }}
    LaunchedEffect(user.uid){refresh()}
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Biblioteca na nuvem",fontSize=20.sp,fontWeight=FontWeight.Bold)
            pull?.takeIf{it.uid==user.uid}?.let{state->Text(when(state.state){"ready"->"Conta consultada automaticamente: ${state.favorites} favoritas · ${state.playlists} playlists. Cópia isolada para preparação da sincronização.";"loading"->"Consultando sua conta…";"pending"->"Alterações pendentes preservadas." ;else->"Consulta automática indisponível."},fontSize=12.sp,color=Color.LightGray)}
            Text("Cópia manual vinculada à sua conta Google. Não inclui arquivos de áudio, downloads nem músicas locais. Não há sincronização automática nesta versão.",fontSize=12.sp,color=Color.LightGray)
            remote?.let{Text("Na conta: ${it.snapshot.favorites.size} favoritas · ${it.snapshot.playlists.size} playlists",fontSize=13.sp)}
            OutlinedButton(enabled=enabled&&!busy,onClick={refresh()},modifier=Modifier.fillMaxWidth()){Text("Consultar biblioteca da conta")}
            Button(enabled=enabled&&!busy&&remote!=null,onClick={confirm="upload"},modifier=Modifier.fillMaxWidth()){Text("Salvar cópia na conta")}
            OutlinedButton(enabled=enabled&&!busy&&(remote?.revision?:0)>0,onClick={confirm="restore"},modifier=Modifier.fillMaxWidth()){Text("Restaurar cópia neste aparelho")}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
        }
    }
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Plano e assinatura",fontSize=20.sp,fontWeight=FontWeight.Bold)
            Text(plan,color=MaterialTheme.colorScheme.primary)
            Text("As assinaturas serão gerenciadas pelo servidor. Nenhuma cobrança ou compra disponível nesta versão.",fontSize=12.sp,color=Color.LightGray)
            TextButton(enabled=enabled&&!busy,onClick={refresh()}){Text("Consultar situação do plano")}
        }
    }
    confirm?.let{action->
        AlertDialog(onDismissRequest={confirm=null},
            title={Text(if(action=="upload")"Salvar biblioteca deste aparelho?" else "Restaurar biblioteca da conta?")},
            text={Text(if(action=="upload")"As referências online e playlists deste aparelho serão mescladas à conta Google conectada. Confirme somente se esta biblioteca pertence a você. Itens removidos em outro aparelho podem reaparecer." else "As referências online desta conta serão adicionadas à biblioteca local. Não serão apagados os itens existentes. A biblioteca local é compartilhada por quem utiliza este aparelho, mesmo após sair da conta.")},
            confirmButton={TextButton(enabled=enabled&&!busy,onClick={
                confirm=null;busy=true;notice=null;scope.launch{
                    try{
                        val current=readCloudLibrary(context,user.uid)
                        if(action=="upload"){
                            val merged=mergeCloudLibrary(localCloudLibrary(context),current.snapshot)
                            if(!writeCloudLibrary(context,user.uid,current.revision,merged))notice="Outra atualização ocorreu. Consulte e tente novamente."
                            else notice="Cópia salva na conta."
                            remote=readCloudLibrary(context,user.uid)
                        }else{
                            require(current.revision>0)
                            val session=NeroSupabase.session(context,false);require(session.uid==user.uid)
                            restoreCloudLibrary(context,current.snapshot)
                            remote=current;notice="Cópia mesclada à biblioteca local."
                        }
                    }catch(e:CancellationException){throw e}catch(_:Exception){notice="Operação não confirmada. Consulte novamente antes de repetir."}
                    finally{busy=false}
                }
            }){Text("Confirmar")}},
            dismissButton={TextButton(onClick={confirm=null}){Text("Cancelar")}})
    }
}
