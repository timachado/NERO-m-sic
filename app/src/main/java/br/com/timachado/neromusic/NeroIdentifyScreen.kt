package br.com.timachado.neromusic

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun NeroIdentifyEntry(onOpen:()->Unit){
    Card(onClick=onOpen,modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Icon(Icons.Default.GraphicEq,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(38.dp))
            Column(Modifier.weight(1f)){
                Text("NERO IDENTIFY",color=MaterialTheme.colorScheme.secondary,fontWeight=FontWeight.Bold)
                Text("Que música é essa?",style=MaterialTheme.typography.titleLarge)
                Text("Descubra o som ao seu redor.",style=MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.KeyboardArrowRight,"Abrir identificação")
        }
    }
}
@Composable
internal fun NeroIdentifyScreen(onBack:()->Unit,onSearch:(String)->Unit){
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val lifecycle=(context as? LifecycleOwner)?.lifecycle
    var foreground by remember(lifecycle){mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)==true)}
    val scope=rememberCoroutineScope()
    var job by remember(uid){mutableStateOf<Job?>(null)}
    var busy by remember(uid){mutableStateOf(false)}
    var message by remember(uid){mutableStateOf<String?>(null)}
    var match by remember(uid){mutableStateOf<NeroIdentifyMatch?>(null)}
    var consent by remember(uid){mutableStateOf(false)}
    var historyOpen by remember(uid,ready){mutableStateOf(false)}
    var confirmClear by remember(uid,ready){mutableStateOf(false)}
    var historyEnabled by remember(uid,ready){mutableStateOf(ready&&uid?.let{NeroIdentifyHistory.enabled(context,it)}==true)}
    var history by remember(uid,ready){mutableStateOf(if(ready)uid?.let{NeroIdentifyHistory.read(context,it)}.orEmpty() else emptyList())}
    var pendingOwner by remember{mutableStateOf<String?>(null)}
    fun start(owner:String){
        if(!NeroIdentifyClient.enabled||!foreground||busy||!ready||NeroSupabase.activeUid.value!=owner)return
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return
        busy=true;match=null;message="Ouvindo a música ao seu redor…"
        job=scope.launch{
            var audio:ByteArray?=null
            try{
                audio=NeroIdentifySample.record(context)
                message="Procurando a música…"
                when(val result=NeroIdentifyClient.identify(context,owner,audio)){
                    is NeroIdentifyResult.Match->{
                        if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner){
                            match=result.value;message=null
                            history=NeroIdentifyHistory.add(context,owner,result.value)
                        }
                    }
                    NeroIdentifyResult.NoMatch->message="Não encontrei uma correspondência. Tente outro trecho."
                    NeroIdentifyResult.Limit->message="Limite de identificações atingido."
                    NeroIdentifyResult.Unavailable->message="Reconhecimento indisponível no momento."
                }
            }catch(e:CancellationException){throw e}
            catch(_:Exception){message="Não foi possível identificar a música. Tente novamente."}
            finally{audio?.fill(0);busy=false}
        }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        val owner=pendingOwner;pendingOwner=null
        // Wait for explicit user tap after returning from the permission dialog.
        message=if(granted&&owner==uid)"Microfone permitido. Toque em Identificar para começar." else "Permita o microfone para identificar músicas."
    }
    DisposableEffect(lifecycle,uid){
        val observer=LifecycleEventObserver{source,_->
            foreground=source.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if(!foreground){job?.cancel();consent=false;message=null}
        }
        lifecycle?.addObserver(observer)
        onDispose{job?.cancel();lifecycle?.removeObserver(observer)}
    }
    val historyOwner=uid?.takeIf{ready}
    if(historyOpen&&historyOwner!=null){
        AlertDialog(
            onDismissRequest={historyOpen=false;confirmClear=false},
            title={Text("Histórico de identificações")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text("Salvar identificações",fontWeight=FontWeight.Medium)
                            Text("Apenas neste aparelho, na sua conta.",style=MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked=historyEnabled,onCheckedChange={enabled->
                            if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==historyOwner){
                                NeroIdentifyHistory.setEnabled(context,historyOwner,enabled)
                                historyEnabled=enabled
                                history=NeroIdentifyHistory.read(context,historyOwner)
                            }
                        })
                    }
                    if(!historyEnabled)Text("Ative para guardar novas descobertas. Desativar apaga o histórico.")
                    else if(history.isEmpty())Text("Nenhuma identificação salva.")
                    else LazyColumn(Modifier.heightIn(max=320.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        items(history){row->
                            Card(Modifier.fillMaxWidth()){
                                Column(Modifier.padding(10.dp)){
                                    Row(verticalAlignment=Alignment.CenterVertically){
                                        Column(Modifier.weight(1f)){
                                            Text(row.match.title,fontWeight=FontWeight.Bold)
                                            Text(row.match.artist,style=MaterialTheme.typography.bodyMedium)
                                            if(row.match.album.isNotBlank())Text(row.match.album,style=MaterialTheme.typography.bodySmall)
                                            Text(android.text.format.DateFormat.format("dd/MM/yyyy HH:mm",row.at).toString(),style=MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick={
                                            if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==historyOwner)
                                                history=NeroIdentifyHistory.remove(context,historyOwner,row)
                                        }){Icon(Icons.Default.DeleteOutline,"Remover identificação")}
                                    }
                                    TextButton(onClick={
                                        if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==historyOwner){
                                            historyOpen=false;onSearch(row.match.query)
                                        }
                                    }){Text("Encontrar no NERO Music")}
                                }
                            }
                        }
                    }
                }
            },
            confirmButton={TextButton(onClick={historyOpen=false}){Text("Fechar")}},
            dismissButton={if(historyEnabled&&history.isNotEmpty())TextButton(onClick={confirmClear=true}){Text("Limpar histórico")}}
        )
        if(confirmClear)AlertDialog(onDismissRequest={confirmClear=false},title={Text("Limpar histórico?")},
            text={Text("As músicas salvas neste histórico serão removidas. Favoritos e playlists não serão alterados.")},
            confirmButton={TextButton(onClick={
                if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==historyOwner){
                    NeroIdentifyHistory.clear(context,historyOwner);history=emptyList()
                }
                confirmClear=false
            }){Text("Limpar")}},
            dismissButton={TextButton(onClick={confirmClear=false}){Text("Cancelar")}})
    }
    if(consent)AlertDialog(onDismissRequest={consent=false},title={Text("Identificar música")},
        text={Text("O NERO grava oito segundos do ambiente e envia o trecho ao serviço de reconhecimento. Não use enquanto estiver dirigindo. A captura só acontece após sua confirmação.")},
        confirmButton={TextButton(onClick={
            consent=false
            val owner=uid
            if(owner!=null&&foreground&&ready){
                if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)start(owner)
                else{pendingOwner=owner;permission.launch(Manifest.permission.RECORD_AUDIO)}
            }
        }){Text("Continuar")}},dismissButton={TextButton(onClick={consent=false}){Text("Cancelar")}})
    Column(Modifier.fillMaxSize().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={job?.cancel();onBack()}){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text("NERO IDENTIFY",color=MaterialTheme.colorScheme.secondary,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            IconButton(enabled=historyOwner!=null&&!busy,onClick={historyOpen=true}){Icon(Icons.Default.History,"Histórico de identificações")}
        }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.GraphicEq,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(120.dp))
        Text("Que música é essa?",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        if(!NeroIdentifyClient.enabled)Text("Reconhecimento em preparação. O microfone não será ativado nesta versão.")
        else if(uid==null||!ready)Text("Entre na sua conta NERO para identificar músicas.")
        else Text("Identifique músicas do rádio ou do ambiente.")
        if(busy){CircularProgressIndicator();OutlinedButton(onClick={job?.cancel();message=null}){Text("Cancelar")}}
        else Button(enabled=NeroIdentifyClient.enabled&&foreground&&ready&&uid!=null,onClick={consent=true},modifier=Modifier.fillMaxWidth()){Text("Identificar música")}
        if(historyOwner!=null&&!busy)TextButton(onClick={historyOpen=true}){Text(if(historyEnabled)"Histórico · "+history.size+" músicas" else "Ativar histórico de identificações")}
        message?.let{Text(it)}
        match?.let{result->
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(result.title,style=MaterialTheme.typography.titleLarge)
                    Text(result.artist)
                    if(result.album.isNotBlank())Text(result.album,style=MaterialTheme.typography.bodySmall)
                    Button(onClick={onSearch(result.query)},modifier=Modifier.fillMaxWidth()){Text("Encontrar no NERO Music")}
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
