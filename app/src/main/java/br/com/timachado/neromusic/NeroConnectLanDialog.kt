package br.com.timachado.neromusic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun NeroConnectLanDialog(onClose:()->Unit,together:Boolean=false){
    val context=LocalContext.current
    val lifecycle=(context as? LifecycleOwner)?.lifecycle
    val scope=rememberCoroutineScope()
    var controller by remember{mutableStateOf<MediaController?>(null)}
    var receiver by remember{mutableStateOf<NeroConnectLanReceiver?>(null)}
    var receiverJob by remember{mutableStateOf<Job?>(null)}
    var senderJob by remember{mutableStateOf<Job?>(null)}
    var endpoint by remember{mutableStateOf("")}
    var input by remember{mutableStateOf("")}
    var notice by remember{mutableStateOf("Mantenha os dois aparelhos na mesma rede Wi-Fi.")}
    var busy by remember{mutableStateOf(false)}
    fun stop(){
        receiver?.close();receiver=null
        receiverJob?.cancel();receiverJob=null
        senderJob?.cancel();senderJob=null
        endpoint="";busy=false
    }
    DisposableEffect(lifecycle){
        val observer=LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_PAUSE||event==Lifecycle.Event.ON_STOP){stop();notice="Sessão encerrada ao sair do primeiro plano."}
        }
        lifecycle?.addObserver(observer)
        val future=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync()
        var disposed=false
        future.addListener({if(!disposed)controller=runCatching{future.get()}.getOrNull()},ContextCompat.getMainExecutor(context))
        onDispose{
            disposed=true
            stop();lifecycle?.removeObserver(observer)
            MediaController.releaseFuture(future)
        }
    }
    fun foreground()=lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)==true
    fun execute(action:ConnectAction):Boolean{
        val p=controller?:return false
        if(!foreground()||receiver==null||p.currentMediaItem==null)return false
        return when(action){
            ConnectAction.PLAY->if(p.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)&&p.playbackState!=Player.STATE_IDLE&&p.playbackState!=Player.STATE_ENDED){p.play();true}else false
            ConnectAction.PAUSE->if(p.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)){p.pause();true}else false
            ConnectAction.NEXT->if(p.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)&&p.hasNextMediaItem()){p.seekToNextMediaItem();true}else false
            ConnectAction.PREVIOUS->if(p.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)&&p.hasPreviousMediaItem()){p.seekToPreviousMediaItem();true}else false
            else->false
        }
    }
    fun snapshot():TogetherSnapshot?{
        val p=controller?:return null
        if(!foreground()||p.playbackState!=Player.STATE_READY||p.currentMediaItem?.isNeroVideo()==true||
            p.playbackParameters.speed!=1f||p.playbackParameters.pitch!=1f)return null
        val id=p.currentMediaItem?.mediaId?:return null
        return TogetherSnapshot(id,p.currentPosition,p.duration,p.isPlaying).takeIf(::togetherValid)
    }
    fun sync(){
        if(!foreground()||busy||receiver!=null||!together)return
        val initial=snapshot()?:run{notice="Abra a mesma música de catálogo nos dois aparelhos.";return}
        val address=input.trim()
        busy=true
        senderJob=scope.launch{
            try{
                val (remote,rtt)=readTogetherLan(context.applicationContext,address)
                val current=snapshot()
                val p=controller
                val plan=current?.takeIf{it.trackId==initial.trackId}?.let{togetherPlan(remote,it,rtt)}
                if(!foreground()||p==null||plan==null||!p.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)||
                    (plan.seekMs!=null&&!p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))){
                    notice="Sincronização indisponível. Confira a faixa, a duração e a conexão."
                }else{
                    plan.seekMs?.let{p.seekTo(it)}
                    if(plan.play)p.play()else p.pause()
                    notice="Posição ajustada à sessão. Sincronização aproximada; repita se necessário."
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){notice="Sessão indisponível. Confira o Wi-Fi e o pareamento."}
            finally{busy=false}
        }
    }
    fun start(){
        if(!foreground()||controller==null||receiver!=null||busy)return
        val server=NeroConnectLanReceiver(context.applicationContext)
        receiver=server;notice="Iniciando recepção…"
        receiverJob=scope.launch{
            try{server.listen(onReady={endpoint=it;notice="Recepção ativa por até 5 minutos."},onCommand={action->
                val ok=execute(action)
                notice=if(ok)"Comando recebido: "+action.name else "Comando não disponível na faixa atual."
                ok
            },onSnapshot=if(together)({snapshot()})else null)}
            catch(e:CancellationException){throw e}
            catch(_:Exception){notice="Não foi possível iniciar. Confira o Wi-Fi e o pareamento."}
            finally{server.close();if(receiver===server){receiver=null;endpoint=""}}
        }
    }
    fun send(action:ConnectAction){
        if(!foreground()||busy||receiver!=null)return
        val address=input.trim()
        busy=true
        senderJob=scope.launch{
            try{notice=if(sendConnectLan(context.applicationContext,address,action))"Comando aceito pelo outro aparelho." else "Comando recusado pelo aparelho."}
            catch(e:CancellationException){throw e}
            catch(_:Exception){notice="Conexão não confirmada. Confira o endereço, o Wi-Fi e as chaves pareadas."}
            finally{busy=false}
        }
    }
    AlertDialog(onDismissRequest={stop();onClose()},title={Text(if(together)"NERO Together · Wi-Fi" else "NERO Connect · Wi-Fi")},text={
        Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(if(together)"Abra a mesma faixa de catálogo em ambos os aparelhos. O anfitrião compartilha apenas ID, duração, posição e estado da música por até 5 minutos. O convidado solicita o ajuste manualmente. Cada aparelho reproduz sua própria fonte." else "Prévia local: o aparelho receptor deve manter esta tela aberta. Apenas o dispositivo pareado pode enviar comandos. Não usa internet nem transmite arquivos de áudio.")
            if(receiver==null)Button(enabled=controller!=null&&!busy,onClick={start()}){Text(if(together)"Hospedar sessão por 5 min" else "Permitir controle por 5 min")}
            else{
                if(endpoint.isNotBlank()){
                    Text("Endereço deste aparelho: "+endpoint)
                    TextButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("NERO Connect",endpoint))}){Text("Copiar endereço")}
                }
                OutlinedButton(onClick={stop();notice="Recepção encerrada."}){Text("Encerrar recepção")}
            }
            HorizontalDivider()
            if(receiver==null){
                OutlinedTextField(input,{input=it.take(25)},label={Text("Endereço IP:porta do receptor")},singleLine=true,enabled=!busy)
                if(together)Button(enabled=!busy&&input.isNotBlank()&&controller!=null,onClick={sync()}){Text("Sincronizar com anfitrião")}
                else {
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    listOf(ConnectAction.PREVIOUS to "Anterior",ConnectAction.NEXT to "Próxima").forEach{(action,label)->
                        OutlinedButton(enabled=!busy&&input.isNotBlank(),onClick={send(action)}){Text(label)}
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    Button(enabled=!busy&&input.isNotBlank(),onClick={send(ConnectAction.PLAY)}){Text("Play")}
                    OutlinedButton(enabled=!busy&&input.isNotBlank(),onClick={send(ConnectAction.PAUSE)}){Text("Pausar")}
                }
                }
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(notice)
        }
    },confirmButton={TextButton(onClick={stop();onClose()}){Text("Fechar")}})
}
