package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

private const val testOrigin="https://nero-native-audio-test.timachado-mcp.workers.dev"
private const val testA="$testOrigin/track-a.wav"
private const val testB="$testOrigin/track-b.wav"

@Composable
internal fun NeroNativeAudioTestDialog(onClose:()->Unit){
    val context=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    var devices by remember{mutableStateOf(emptyList<NeroDlnaDevice>())}
    var selected by remember{mutableStateOf<NeroDlnaDevice?>(null)}
    var busy by remember{mutableStateOf(false)}
    var notice by remember{mutableStateOf("Localize uma TV compatível com DLNA.")}
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text("NERO Connect · teste nativo")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Teste com dois sinais sonoros originais, de 25 segundos cada. Pause o áudio local antes de iniciar. A TV selecionada poderá interromper sua reprodução atual. Não precisa abrir o navegador.")
            OutlinedButton(enabled=BuildConfig.DEBUG&&!busy,onClick={
                busy=true;selected=null;devices=emptyList()
                scope.launch{
                    try{
                        devices=withContext(Dispatchers.IO){NeroDlna.discover(context)}
                        notice=if(devices.isEmpty())"Nenhum receptor DLNA encontrado." else "Selecione a TV."
                    }catch(e:CancellationException){throw e}catch(_:Exception){notice="Falha ao localizar a TV."}
                    finally{busy=false}
                }
            }){Text("Buscar TV")}
            devices.forEach{d->
                OutlinedButton(enabled=!busy,onClick={selected=d},modifier=Modifier.fillMaxWidth()){
                    Text(d.name+if(selected==d)" · selecionada" else "")
                }
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(notice)
        }
    },confirmButton={TextButton(enabled=BuildConfig.DEBUG&&!busy&&selected!=null,onClick={
        val d=selected?:return@TextButton
        busy=true;notice="Testando reprodução e fila nativa na TV…"
        scope.launch{
            try{
                notice=withContext(Dispatchers.IO){
                    try{
                        NeroDlna.play(context,d,testA,"NERO Teste A","NERO","audio/wav")
                        if(!NeroDlna.awaitPlaying(context,d,testA))return@withContext "A TV não confirmou a reprodução do primeiro áudio."
                        if(!NeroDlna.prepareNext(context,d,testA,testB,"NERO Teste B","NERO","audio/wav"))return@withContext "A TV reproduziu o primeiro áudio, mas não confirmou a próxima faixa."
                        val advanced=withTimeoutOrNull(45000L){
                            while(true){
                                val uri=NeroDlna.currentUri(context,d)
                                val state=NeroDlna.transportState(context,d)
                                if(uri==testB&&state=="PLAYING")return@withTimeoutOrNull true
                                if(uri!=testA&&uri!=testB)return@withTimeoutOrNull false
                                delay(700L)
                            }
                            @Suppress("UNREACHABLE_CODE")
                            false
                        }==true
                        if(advanced)"A TV confirmou as duas faixas e avançou automaticamente." else "A TV não confirmou o avanço automático."
                    }finally{
                        withContext(NonCancellable){
                            runCatching{
                                val uri=NeroDlna.currentUri(context,d)
                                if(uri==testA||uri==testB)NeroDlna.control(context,d,uri,"Stop")
                            }
                        }
                    }
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){notice="O receptor não aceitou o teste nativo ou não respondeu."}
            finally{busy=false}
        }
    }){Text("Iniciar teste")}},dismissButton={TextButton(enabled=!busy,onClick=onClose){Text("Fechar")}})
}
