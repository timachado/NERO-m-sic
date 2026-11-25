package br.com.timachado.neromusic

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun NeroIdentifyDialog(onClose:()->Unit,onSearch:(String)->Unit){
    val context=LocalContext.current
    val lifecycle=(context as? LifecycleOwner)?.lifecycle
    val scope=rememberCoroutineScope()
    var job by remember{mutableStateOf<Job?>(null)}
    var phase by remember{mutableStateOf("")}
    var message by remember{mutableStateOf("Aproxime o celular da música. Evite conversas durante a captura.")}
    var match by remember{mutableStateOf<IdentifyMatch?>(null)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        message=if(granted)"Permissão concedida. Toque em identificar para começar." else "Permissão de microfone não concedida."
    }
    DisposableEffect(lifecycle){
        val observer=LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_PAUSE||event==Lifecycle.Event.ON_STOP){
                job?.cancel();job=null;phase="";message="Captura interrompida."
            }
        }
        lifecycle?.addObserver(observer)
        onDispose{lifecycle?.removeObserver(observer);job?.cancel()}
    }
    fun start(){
        if(lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)!=true){
            message="Abra o NERO em primeiro plano.";return
        }
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            permission.launch(Manifest.permission.RECORD_AUDIO);return
        }
        if(job?.isActive==true)return
        match=null
        job=scope.launch{
            var wav:ByteArray?=null
            try{
                phase="Ouvindo por 8 segundos…"
                wav=captureIdentify(context)
                phase="Identificando música…"
                match=recognizeIdentifyDemo(wav)
                message=if(match==null)"Nenhuma música identificada. Tente outro trecho." else "Música identificada pelo serviço."
            }catch(e:CancellationException){throw e}
            catch(_:Exception){message="Não foi possível identificar. Verifique o microfone, a conexão ou a disponibilidade do serviço de teste."}
            finally{wav?.fill(0);phase=""}
        }
    }
    AlertDialog(
        onDismissRequest={job?.cancel();onClose()},
        icon={Icon(Icons.Default.Mic,null,tint=Color(0xFF00E5FF))},
        title={Text("NERO Identify")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Prévia experimental · AudD")
                Text("Ao identificar, o NERO grava 8 segundos do ambiente e envia o trecho pela internet ao AudD. Nenhum arquivo é salvo pelo NERO. O serviço externo tem suas próprias regras de privacidade e limite de testes compartilhado.")
                if(phase.isNotEmpty()){LinearProgressIndicator(Modifier.fillMaxWidth());Text(phase)}else Text(message)
                match?.let{
                    Text(it.title,style=MaterialTheme.typography.titleLarge)
                    Text(it.artist)
                    if(it.album.isNotBlank())Text(it.album)
                }
            }
        },
        confirmButton={
            if(match!=null)TextButton(onClick={val result=match?:return@TextButton;onSearch(result.title+" "+result.artist);onClose()}){Text("Buscar no NERO")}
            else TextButton(enabled=phase.isEmpty(),onClick={start()}){Text("Identificar")}
        },
        dismissButton={TextButton(onClick={job?.cancel();onClose()}){Text(if(phase.isEmpty())"Fechar" else "Cancelar")}}
    )
}
