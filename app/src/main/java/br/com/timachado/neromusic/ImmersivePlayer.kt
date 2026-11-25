package br.com.timachado.neromusic

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object NeroSleepTimer{
    private val state=MutableStateFlow<Long?>(null)
    val deadline=state.asStateFlow()
    fun start(minutes:Int){require(minutes in 1..120);state.value=SystemClock.elapsedRealtime()+minutes*60000L}
    fun cancel(){state.value=null}
}
private val neon=Color(0xFF39FF14)
private val aqua=Color(0xFF00E5FF)
private val panel=Color(0xFF17262D)
private fun clock(ms:Long):String{val sec=(ms.coerceAtLeast(0)/1000);return "%d:%02d".format(sec/60,sec%60)}
@Composable
internal fun ImmersivePlayer(
    track:Track?,remoteCover:String?=null,title:String,artist:String,position:Long,duration:Long,playing:Boolean,favorite:Boolean,
    shuffle:Boolean,repeat:Int,ready:Boolean,error:String?,canSave:Boolean,onSave:()->Unit,
    onClose:()->Unit,onFavorite:()->Unit,onSeek:(Long)->Unit,onPlayPause:()->Unit,onPrevious:()->Unit,onNext:()->Unit,
    onShuffle:()->Unit,onRepeat:()->Unit,onLyrics:()->Unit,onQueue:()->Unit,onEqualizer:()->Unit,onRadio:()->Unit,
    modifier:Modifier=Modifier
){
    val deadline by NeroSleepTimer.deadline.collectAsState()
    var remaining by remember(deadline){mutableLongStateOf((deadline?.minus(SystemClock.elapsedRealtime())?:0L).coerceAtLeast(0))}
    var showTimer by remember{mutableStateOf(false)}
    var showMore by remember{mutableStateOf(false)}
    var seekPreview by remember(title,artist,track?.id){mutableStateOf<Float?>(null)}
    LaunchedEffect(deadline){
        val end=deadline?:return@LaunchedEffect
        while(true){remaining=(end-SystemClock.elapsedRealtime()).coerceAtLeast(0);if(remaining==0L)break;delay(1000)}
    }
    if(showTimer)AlertDialog(onDismissRequest={showTimer=false},title={Text("Temporizador de sono")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text("Pausa o áudio após o intervalo escolhido, enquanto o serviço estiver ativo.")
                if(deadline!=null)Text("Restante: "+clock(remaining),color=neon)
                listOf(10,15,30,45,60,90).forEach{minutes->
                    TextButton(enabled=ready,onClick={NeroSleepTimer.start(minutes);showTimer=false},modifier=Modifier.fillMaxWidth()){Text("$minutes minutos")}
                }
            }
        },confirmButton={TextButton(onClick={showTimer=false}){Text("Fechar")}},
        dismissButton={if(deadline!=null)TextButton(onClick={NeroSleepTimer.cancel();showTimer=false}){Text("Cancelar timer")}})
    BoxWithConstraints(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF233743),Color(0xFF0D181D),Color.Black)))){
        val coverSize=minOf(maxWidth-40.dp,maxHeight*.46f,410.dp).coerceAtLeast(1.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onClose){Icon(Icons.Default.KeyboardArrowDown,"Minimizar player")}
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){
                    Text("NERO MUSIC",fontSize=12.sp,color=aqua,fontWeight=FontWeight.Bold)
                    Text("Reproduzindo",fontSize=15.sp,fontWeight=FontWeight.Medium)
                }
                Box{
                    IconButton(onClick={showMore=true}){Icon(Icons.Default.MoreVert,"Mais opções")}
                    DropdownMenu(expanded=showMore,onDismissRequest={showMore=false}){
                        DropdownMenuItem(text={Text("Adicionar à playlist")},enabled=canSave,onClick={showMore=false;onSave()})
                        DropdownMenuItem(text={Text("Rádio desta faixa")},enabled=ready&&track!=null,onClick={showMore=false;onRadio()})
                        if(BuildConfig.DEBUG)DropdownMenuItem(text={Text("Equalizador")},onClick={showMore=false;onEqualizer()})
                        DropdownMenuItem(text={Text("Fila de reprodução")},enabled=ready,onClick={showMore=false;onQueue()})
                        DropdownMenuItem(text={Text("Temporizador")},enabled=ready,onClick={showMore=false;showTimer=true})
                    }
                }
            }
            if(remoteCover!=null&&track==null)AsyncImage(model=remoteCover,contentDescription="Capa da faixa",contentScale=ContentScale.Crop,modifier=Modifier.size(coverSize).clip(RoundedCornerShape(24.dp)))
            else LocalArtwork(track,Modifier.size(coverSize),large=true)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(title.ifBlank{"Nenhuma faixa"},fontSize=26.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(artist,color=Color.LightGray,fontSize=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    track?.album?.takeUnless{it=="Álbum desconhecido"}?.let{Text(it,color=Color.LightGray,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
                }
                FilledTonalIconButton(onClick=onSave,enabled=canSave){Icon(Icons.Default.PlaylistAdd,"Adicionar à playlist",tint=neon)}
                FilledTonalIconButton(onClick=onFavorite,enabled=canSave){Icon(if(favorite)Icons.Default.Favorite else Icons.Default.FavoriteBorder,"Favoritar",tint=if(favorite)neon else Color.White)}
                FilledTonalIconButton(onClick=onLyrics,enabled=ready&&title.isNotBlank()){Icon(Icons.Default.Lyrics,"Letras",tint=aqua)}
            }
            Column(Modifier.fillMaxWidth()){
                Slider(value=seekPreview?:if(duration>0)(position.toFloat()/duration).coerceIn(0f,1f)else 0f,
                    onValueChange={seekPreview=it},onValueChangeFinished={
                        seekPreview?.let{if(duration>0)onSeek((it*duration).toLong())};seekPreview=null
                    },enabled=ready&&duration>0)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Text(clock(seekPreview?.let{(it*duration).toLong()}?:position),fontSize=12.sp,color=Color.LightGray)
                    Text(clock(duration),fontSize=12.sp,color=Color.LightGray)
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically){
                FilledTonalIconButton(onClick=onPrevious,enabled=ready,modifier=Modifier.size(62.dp),shape=RoundedCornerShape(20.dp),colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=panel)){Icon(Icons.Default.SkipPrevious,"Anterior",Modifier.size(30.dp))}
                FilledIconButton(onClick=onPlayPause,enabled=ready,modifier=Modifier.size(78.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=neon,contentColor=Color.Black)){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Reproduzir ou pausar",Modifier.size(40.dp))}
                FilledTonalIconButton(onClick=onNext,enabled=ready,modifier=Modifier.size(62.dp),shape=RoundedCornerShape(20.dp),colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=panel)){Icon(Icons.Default.SkipNext,"Próxima",Modifier.size(30.dp))}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onQueue,enabled=ready){Icon(Icons.Default.QueueMusic,"Fila",tint=aqua)}
                IconButton(onClick={showTimer=true},enabled=ready){Icon(Icons.Default.Bedtime,"Temporizador de sono",tint=if(deadline!=null)neon else Color.White)}
                IconButton(onClick=onShuffle,enabled=ready){Icon(Icons.Default.Shuffle,"Aleatório",tint=if(shuffle)neon else Color.White)}
                IconButton(onClick=onRepeat,enabled=ready){Icon(if(repeat==Player.REPEAT_MODE_ONE)Icons.Default.RepeatOne else Icons.Default.Repeat,"Repetição",tint=if(repeat!=Player.REPEAT_MODE_OFF)neon else Color.White)}
                if(BuildConfig.DEBUG)IconButton(onClick=onEqualizer){Icon(Icons.Default.Equalizer,"Equalizador",tint=aqua)}
            }
            if(deadline!=null)Text("Pausar em "+clock(remaining),color=neon,fontSize=12.sp)
            if(track!=null)OutlinedButton(onClick=onRadio,enabled=ready){Icon(Icons.Default.Radio,null);Spacer(Modifier.width(8.dp));Text("Rádio desta faixa")}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
}
