package br.com.timachado.neromusic

import android.net.Network
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TvQueueSnapshot(val ids:List<String>,val index:Int,val seconds:Long)
private data class TvQueueTarget(val name:String,val host:String,val network:Network)
private fun snapshotQueue(player:Player?):TvQueueSnapshot?=runCatching{
    if(player==null||!player.isCommandAvailable(Player.COMMAND_GET_TIMELINE)||
        !player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)||
        player.shuffleModeEnabled||player.repeatMode!=Player.REPEAT_MODE_OFF||
        player.mediaItemCount !in 1..100)return@runCatching null
    val index=player.currentMediaItemIndex
    if(index !in 0 until player.mediaItemCount)return@runCatching null
    val ids=(0 until player.mediaItemCount).map{
        NeroDlna.nativeVideoId(player.getMediaItemAt(it).mediaId)?:return@runCatching null
    }
    TvQueueSnapshot(ids,index,player.currentPosition.coerceAtLeast(0L)/1000L)
}.getOrNull()

@Composable
internal fun NeroQueueTvDialog(player:Player?,onClose:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val initial=remember(player){snapshotQueue(player)}
    val owner=remember{NeroSupabase.activeUid.value}
    var targets by remember{mutableStateOf(emptyList<TvQueueTarget>())}
    var selected by remember{mutableStateOf<TvQueueTarget?>(null)}
    var busy by remember{mutableStateOf(false)}
    var notice by remember{mutableStateOf<String?>(null)}
    fun authorized()=BuildConfig.DEBUG&&NeroPlaybackAccess.allowed()&&NeroSupabase.activeUid.value==owner
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text("NERO Connect TV · homologação")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Teste independente do Web Video Caster. Envia apenas identificadores do catálogo à TV selecionada na rede local, via protocolo legado sem criptografia. A fila usa o player oficial incorporado. O áudio no celular não será pausado automaticamente.")
            if(initial==null)Text("Use uma fila de 1 a 100 faixas do catálogo, em ordem normal, sem repetição ou modo aleatório.")
            else Text("Fila: ${initial.ids.size} faixas · inicia na faixa ${initial.index+1}.")
            OutlinedButton(enabled=!busy&&initial!=null&&authorized(),onClick={
                busy=true;notice=null;targets=emptyList();selected=null
                scope.launch{
                    try{
                        targets=withContext(Dispatchers.IO){
                            val d=NeroDlna.discoverTargets(context.applicationContext)
                            (d.dlna.filter{NeroWebOsReceiver.isWebOs(it.name)}.map{TvQueueTarget(it.name,it.endpoint.host,it.network)}+
                                d.dial.filter{NeroWebOsReceiver.isWebOs(it.name)}.map{TvQueueTarget(it.name,it.endpoint.host,it.network)}).distinctBy{it.host}
                        }
                        if(targets.isEmpty())notice="Nenhuma LG webOS compatível encontrada."
                    }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível localizar a TV."}
                    finally{busy=false}
                }
            }){Text("Buscar TV LG")}
            targets.forEach{target->
                OutlinedButton(enabled=!busy,onClick={selected=target},modifier=Modifier.fillMaxWidth()){
                    Text(target.name+if(selected==target)" · selecionada" else "")
                }
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let{Text(it)}
        }
    },confirmButton={
        TextButton(enabled=!busy&&selected!=null&&initial!=null&&authorized(),onClick={
            val target=selected?:return@TextButton
            val live=snapshotQueue(player)
            if(!authorized()||live==null||initial==null||live.ids!=initial.ids||live.index!=initial.index){
                notice="A fila ou a autorização mudou. Feche e abra o teste novamente.";return@TextButton
            }
            busy=true;notice="Confirme a autorização na TV, se solicitado."
            scope.launch{
                try{
                    val accepted=withContext(Dispatchers.IO){
                        NeroWebOsReceiver.openQueue(context.applicationContext,target.network,target.host,live.ids,live.index,live.seconds)
                    }
                    notice=if(accepted)"A TV aceitou abrir o receptor. Pressione Reproduzir na TV e confira a fila. O NERO não confirmou reprodução remota." else "A TV recusou ou não aceitou a abertura."
                }catch(e:CancellationException){throw e}catch(_:Exception){notice="A TV não respondeu ao lançamento. Abra o endereço de homologação manualmente no navegador."}
                finally{busy=false}
            }
        }){Text("Enviar fila de teste")}
    },dismissButton={TextButton(enabled=!busy,onClick=onClose){Text("Fechar")}})
}
