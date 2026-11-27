package br.com.timachado.neromusic

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

// Pure, bounded correction; no wall clock dependency on either device.
internal fun togetherTarget(position:Long,playing:Boolean,age:Long,rtt:Long):Long? {
    if(position !in 0..86400000L||age !in 0..12000L||rtt !in 0..2500L)return null
    return (position+if(playing)age+rtt/2 else 0L).coerceAtMost(86400000L)
}

@Composable
internal fun NeroTogetherLive(room:TogetherRoom,uid:String,player:Player){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var enabled by remember(room.id,uid){mutableStateOf(false)}
    var notice by remember(room.id,uid){mutableStateOf("Sincronização experimental nesta sala.")}
    var owned by remember(room.id,uid){mutableStateOf<String?>(null)}
    var selected by remember(room.id,uid){mutableStateOf<String?>(null)}
    val latestRoom by rememberUpdatedState(room)
    fun active()=NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid
    suspend fun rpc(action:String,video:String="",position:Long=0,playing:Boolean=false):JSONObject{
        require(active())
        val session=NeroSupabase.session(context,false);require(session.uid==uid)
        val args=JSONObject().put("p_action",action).put("p_room",room.id).put("p_video",video).put("p_position",position).put("p_playing",playing)
        return JSONObject(NeroSupabase.rpc("nero_together_sync_v2",args,session.access)).also{require(active())}
    }
    fun pauseOwned(){
        if(owned!=null&&player.currentMediaItem?.mediaId==owned&&player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))player.pause()
    }
    fun load(hit:NativeMusicHit,position:Long){
        require(player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)&&player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)&&player.isCommandAvailable(Player.COMMAND_PREPARE))
        val item=hit.queueItem()
        owned=item.mediaId
        player.setMediaItem(item,position)
        player.prepare()
    }
    LaunchedEffect(enabled,room.id,uid,player){
        while(enabled&&active()){
            try{
                if(room.host){
                    val item=player.currentMediaItem
                    val video=item?.mediaId?.removePrefix("youtube:").orEmpty()
                    if(item==null||item.mediaId!=owned||latestRoom.songs.none{it.hit.id==video}||item.isNeroVideo()){
                        enabled=false;notice="Reprodução fora da sala. Sincronização encerrada."
                        runCatching{rpc("stop")}
                        break
                    }
                    rpc("publish",video,player.currentPosition.coerceAtLeast(0),player.isPlaying)
                    notice="Transmitindo estado da reprodução aos participantes."
                }else{
                    val start=SystemClock.elapsedRealtime()
                    val state=rpc("read")
                    val rtt=SystemClock.elapsedRealtime()-start
                    if(!state.optBoolean("active")){
                        pauseOwned();notice="Aguardando o anfitrião."
                    }else{
                        val target=togetherTarget(state.getLong("position_ms"),state.getBoolean("playing"),state.getLong("age_ms"),rtt)
                        if(target==null){pauseOwned();notice="Rede lenta. Aguardando nova atualização."}
                        else{
                            val hit=NativeMusicHit(state.getString("video"),state.getString("title"),state.getString("artist"),0L)
                            val expected="youtube:"+hit.id
                            if(owned!=null&&player.currentMediaItem?.mediaId!=owned){
                                enabled=false;notice="Você saiu da reprodução da sala.";break
                            }
                            if(player.currentMediaItem?.mediaId!=expected||owned!=expected)load(hit,target)
                            else if(kotlin.math.abs(player.currentPosition-target)>900L&&player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)){
                                player.seekTo(if(player.duration>0)target.coerceAtMost(player.duration)else target)
                            }
                            if(player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)){
                                if(state.getBoolean("playing"))player.play()else player.pause()
                            }
                            notice="Acompanhando a reprodução do anfitrião."
                        }
                    }
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){
                if(!room.host)pauseOwned()
                notice="Sincronização indisponível. Tentando reconectar."
            }
            delay(3000L)
        }
    }
    DisposableEffect(room.id,uid,player){onDispose{pauseOwned()}}
    Text("Ouvir juntos · experimental")
    Text("Mantenha esta sala aberta. Cada aparelho carrega sua própria faixa; atrasos de rede e buffering podem causar diferenças.")
    if(room.host){
        room.songs.forEach{song->
            TextButton(enabled=active(),onClick={
                try{load(song.hit,0L);selected=song.hit.id;player.play();enabled=true}
                catch(_:Exception){notice="Não foi possível preparar esta faixa."}
            }){Text((if(selected==song.hit.id)"✓ " else "▶ ")+song.hit.title)}
        }
        if(enabled){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={pauseOwned()}){Text("Pausar")}
                OutlinedButton(onClick={if(player.currentMediaItem?.mediaId==owned&&player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))player.play()}){Text("Continuar")}
            }
        }
    }else if(!enabled)Button(onClick={enabled=true}){Text("Ouvir junto com anfitrião")}
    if(enabled)OutlinedButton(onClick={
        enabled=false;pauseOwned()
        if(room.host)scope.launch{try{rpc("stop")}catch(e:CancellationException){throw e}catch(_:Exception){}}
        notice="Sincronização encerrada neste aparelho."
    }){Text("Parar sincronização")}
    Text(notice)
}
