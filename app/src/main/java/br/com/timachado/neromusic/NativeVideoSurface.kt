package br.com.timachado.neromusic

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
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

private tailrec fun findActivity(context:Context):Activity?=when(context){
    is Activity->context
    is ContextWrapper->if(context.baseContext===context)null else findActivity(context.baseContext)
    else->null
}
@Composable
internal fun ClipQualityMenu(quality:Int,onQuality:(Int)->Unit,enabled:Boolean=true){
    var expanded by remember{mutableStateOf(false)}
    Box{
        TextButton(onClick={expanded=true},enabled=enabled){
            Icon(Icons.Default.HighQuality,null)
            Spacer(Modifier.width(6.dp))
            Text(if(quality==0)"Auto (até 720p)" else "Até "+quality+"p")
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
            listOf(0,360,480,720,1080).forEach{q->
                DropdownMenuItem(text={Text(if(q==0)"Auto (até 720p)" else "Até "+q+"p")},onClick={expanded=false;if(q!=quality)onQuality(q)},leadingIcon={if(q==quality)Icon(Icons.Default.Check,null)})
            }
        }
    }
}
@Composable
internal fun FullscreenClip(player:Player,title:String,artist:String,position:Long,duration:Long,playing:Boolean,quality:Int,error:String?,onQuality:(Int)->Unit,onSeek:(Long)->Unit,onToggle:()->Unit,onPrevious:()->Unit,onNext:()->Unit,onClose:()->Unit,onAudio:()->Unit,pipSupported:Boolean=false,onPip:()->Unit={}){
    val context=LocalContext.current
    val activity=remember(context){findActivity(context)}
    DisposableEffect(activity){
        val prior=activity?.requestedOrientation
        val window=activity?.window
        val bars=window?.let{WindowCompat.getInsetsController(it,it.decorView)}
        val behavior=bars?.systemBarsBehavior
        val insets=window?.let{androidx.core.view.ViewCompat.getRootWindowInsets(it.decorView)}
        val statusVisible=insets?.isVisible(WindowInsetsCompat.Type.statusBars())?:true
        val navVisible=insets?.isVisible(WindowInsetsCompat.Type.navigationBars())?:true
        activity?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        bars?.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose{
            if(behavior!=null)bars?.systemBarsBehavior=behavior
            if(statusVisible)bars?.show(WindowInsetsCompat.Type.statusBars())else bars?.hide(WindowInsetsCompat.Type.statusBars())
            if(navVisible)bars?.show(WindowInsetsCompat.Type.navigationBars())else bars?.hide(WindowInsetsCompat.Type.navigationBars())
            if(prior!=null)activity?.requestedOrientation=prior
        }
    }
    BackHandler(onBack=onClose)
    var seek by remember(title,artist){mutableStateOf<Float?>(null)}
    Box(Modifier.fillMaxSize().background(Color.Black)){
        NativeVideoSurface(player,Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha=.65f)).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onClose){Icon(Icons.Default.FullscreenExit,"Sair da tela cheia")}
            Column(Modifier.weight(1f)){
                Text(title,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(artist,maxLines=1,overflow=TextOverflow.Ellipsis,color=Color.LightGray,style=MaterialTheme.typography.bodySmall)
            }
            ClipQualityMenu(quality,onQuality)
            if(pipSupported)IconButton(onClick=onPip){Icon(Icons.Default.PictureInPictureAlt,"Janela flutuante")}
            IconButton(onClick=onAudio){Icon(Icons.Default.MusicNote,"Voltar para música")}
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha=.65f)).padding(horizontal=20.dp),horizontalAlignment=Alignment.CenterHorizontally){
            if(error!=null){Text("Vídeo indisponível nesta qualidade.",color=Color.LightGray);TextButton(onClick=onAudio){Text("Voltar para música")}}
            Slider(value=seek?:if(duration>0)(position.toFloat()/duration).coerceIn(0f,1f)else 0f,onValueChange={seek=it},onValueChangeFinished={seek?.let{onSeek((it*duration).toLong())};seek=null},enabled=duration>0)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)){
                IconButton(onClick=onPrevious){Icon(Icons.Default.SkipPrevious,"Anterior")}
                FilledIconButton(onClick=onToggle){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Reproduzir ou pausar")}
                IconButton(onClick=onNext){Icon(Icons.Default.SkipNext,"Próximo")}
            }
        }
    }
}
