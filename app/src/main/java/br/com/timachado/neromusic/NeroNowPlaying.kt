package br.com.timachado.neromusic

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// A single UI snapshot from the existing MediaController. No separate playback session.
internal data class NeroNowPlaying(val id:String="",val playing:Boolean=false,val buffering:Boolean=false,val failed:Boolean=false,val open:()->Unit={}) {
    fun matches(mediaId:String?)=id.isNotEmpty()&&mediaId==id
    val label get()=when{failed->"Falha na reprodução";buffering->"Carregando";playing->"Tocando agora";else->"Pausada"}
}
internal val LocalNeroNowPlaying=compositionLocalOf{NeroNowPlaying()}
@Composable internal fun nowTrackColor(id:String?,normal:Color=Color.Unspecified):Color =
    if(LocalNeroNowPlaying.current.matches(id))MaterialTheme.colorScheme.primary else  normal

@Composable internal fun NowTrackStatus(id:String?){
    val state=LocalNeroNowPlaying.current
    if(state.matches(id))Text(state.label,fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
}
@Composable internal fun NowTrackAction(id:String?,enabled:Boolean,onPlay:()->Unit){
    val state=LocalNeroNowPlaying.current
    val current=state.matches(id)
    IconButton(enabled=enabled,onClick={if(current)state.open()else onPlay()}){
        Icon(if(current&&state.playing)Icons.Default.Equalizer else if(current)Icons.Default.MusicNote else Icons.Default.PlayArrow,
            if(current)"Abrir player: "+state.label else "Reproduzir",
            tint=if(current)MaterialTheme.colorScheme.primary else  MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
