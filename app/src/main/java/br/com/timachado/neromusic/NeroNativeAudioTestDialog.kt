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
            Text("Teste local com dois sinais originais de 12 segundos cada, sem depender de HTTPS externo. Pause o áudio local antes de iniciar. A TV selecionada poderá interromper sua reprodução atual. Não precisa abrir o navegador.")
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
        busy=true;notice="Testando áudio local e fila nativa na TV…"
        scope.launch{
            try{
                notice=withContext(Dispatchers.IO){
                    var stage="criar servidor local"
                    try{
                        NeroLanAudioProbe(context,d).use{probe->
                            val testA=probe.a
                            val testB=probe.b
                            try{
                                stage="SetAVTransportURI / Play"
                                NeroDlna.play(context,d,testA,"NERO Teste A","NERO","audio/wav")
                                stage="GetTransportInfo / GetMediaInfo"
                                if(!NeroDlna.awaitPlaying(context,d,testA))return@withContext "TV não confirmou o primeiro áudio. Requisição ao celular: "+if(probe.mediaRequested)"sim" else "não"
                                stage="SetNextAVTransportURI"
                                if(!NeroDlna.prepareNext(context,d,testA,testB,"NERO Teste B","NERO","audio/wav"))return@withContext "Primeiro áudio confirmado; próxima faixa não confirmada."
                                stage="verificar avanço"
                                val advanced=withTimeoutOrNull(25000L){
                                    while(true){
                                        val uri=NeroDlna.currentUri(context,d)
                                        val state=NeroDlna.transportState(context,d)
                                        if(uri==testB&&state=="PLAYING")return@withTimeoutOrNull true
                                        if(uri!=testA&&uri!=testB)return@withTimeoutOrNull false
                                        delay(500L)
                                    }
                                    @Suppress("UNREACHABLE_CODE")
                                    false
                                }==true
                                if(advanced)"TV confirmou as duas faixas e avançou automaticamente." else "Primeiro áudio confirmado; avanço automático não confirmado."
                            }finally{
                                withContext(NonCancellable){
                                    runCatching{
                                        val uri=NeroDlna.currentUri(context,d)
                                        if(uri==testA||uri==testB)NeroDlna.control(context,d,uri,"Stop")
                                    }
                                }
                            }
                        }
                    }catch(e:CancellationException){throw e}
                    catch(e:NeroDlnaHttpException){"Falha em "+stage+" · HTTP "+e.status}
                    catch(e:java.net.SocketTimeoutException){"Sem resposta em "+stage+" (timeout)."}
                    catch(_:Exception){"Falha em "+stage+". A TV ou a rede não confirmou esta etapa."}
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){notice="O receptor não aceitou o teste nativo ou não respondeu."}
            finally{busy=false}
        }
    }){Text("Iniciar teste")}},dismissButton={TextButton(enabled=!busy,onClick=onClose){Text("Fechar")}})
}
