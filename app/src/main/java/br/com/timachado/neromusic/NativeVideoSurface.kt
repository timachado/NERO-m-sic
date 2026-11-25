package br.com.timachado.neromusic

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout

@Composable
internal fun SingleChoiceMode(videoMode:Boolean,onToggle:()->Unit,enabled:Boolean){
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        FilterChip(selected=!videoMode,enabled=enabled,onClick={if(videoMode)onToggle()},label={Text("Música")},leadingIcon={Icon(Icons.Default.MusicNote,null)})
        FilterChip(selected=videoMode,enabled=enabled,onClick={if(!videoMode)onToggle()},label={Text("Vídeo")},leadingIcon={Icon(Icons.Default.OndemandVideo,null)})
    }
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun NativeVideoSurface(player:Player,modifier:Modifier=Modifier){
    AndroidView(
        factory={context->PlayerView(context).apply{
            useController=false
            resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(AndroidColor.BLACK)
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            this.player=player
        }},
        modifier=modifier,
        onRelease={view->view.player=null},
        update={view->if(view.player!==player)view.player=player}
    )
}
