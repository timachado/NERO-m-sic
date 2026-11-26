package br.com.timachado.neromusic

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.clickable
import androidx.media3.common.MediaItem
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object NeroSleepTimer{
    private val state=MutableStateFlow<Long?>(null)
    val deadline=state.asStateFlow()
    fun start(minutes:Int){require(minutes in 1..120);state.value=SystemClock.elapsedRealtime()+minutes*60000L}
    fun cancel(){state.value=null}
}
private fun clock(ms:Long):String{val sec=(ms.coerceAtLeast(0)/1000);return "%d:%02d".format(sec/60,sec%60)}
@Composable
internal fun ImmersivePlayer(
    track:Track?,remoteCover:String?=null,title:String,artist:String,position:Long,duration:Long,playing:Boolean,favorite:Boolean,
    shuffle:Boolean,repeat:Int,ready:Boolean,error:String?,canSave:Boolean,onSave:()->Unit,
    onClose:()->Unit,onFavorite:()->Unit,onSeek:(Long)->Unit,onPlayPause:()->Unit,onPrevious:()->Unit,onNext:()->Unit,
    onShuffle:()->Unit,onRepeat:()->Unit,onLyrics:()->Unit,onQueue:()->Unit,onEqualizer:()->Unit,onRadio:()->Unit,
    canRadio:Boolean=track!=null,radioBusy:Boolean=false,autoplay:Boolean=false,onToggleAutoplay:()->Unit={},
    shareRef:OnlineRef?=null,mediaId:String="",origin:PlaybackOrigin?=null,onOriginClick:(PlaybackOrigin)->Unit={},onOpenAlbum:(NativeCollection)->Unit={},onExploreArtist:(String)->Unit={},onRelatedQueue:(List<MediaItem>,Int)->Unit={_,_->},videoPlayer:Player?=null,videoMode:Boolean=false,canVideo:Boolean=false,onVideoToggle:()->Unit={},videoQuality:Int=0,onVideoQuality:(Int)->Unit={},pipSupported:Boolean=false,onPip:()->Unit={},offlineTrack:OpenTrack?=null,offlineState:OfflineState=OfflineState(),onOfflineSave:(OpenTrack)->Unit={},onOfflineOpen:()->Unit={},onNativeSave:(NativeMusicHit)->Unit={},modifier:Modifier=Modifier,onRetry:()->Unit=onPlayPause,nextAvailable:Boolean=ready
){
    val premiumSignal by NeroPlaybackAccess.premium.collectAsState()
    val fullControls=premiumSignal&&NeroPlaybackAccess.allowed()
    var fullscreen by rememberSaveable{mutableStateOf(false)}
    LaunchedEffect(videoMode){if(!videoMode)fullscreen=false}
    if(fullscreen&&videoMode&&videoPlayer!=null){
        FullscreenClip(videoPlayer,title,artist,position,duration,playing,videoQuality,error,onVideoQuality,onSeek,onPlayPause,onPrevious,onNext,onClose={fullscreen=false},onAudio={fullscreen=false;onVideoToggle()},pipSupported=pipSupported,onPip=onPip,favorite=favorite,canFavorite=canSave,onFavorite=onFavorite,fullControls=fullControls,nextAvailable=nextAvailable)
        return
    }
    val appearance=rememberNeroPlayerTheme()
    val neon=Color(appearance.primary)
    val aqua=Color(appearance.secondary)
    val panel=Color(appearance.panel)
    val deadline by NeroSleepTimer.deadline.collectAsState()
    var remaining by remember(deadline){mutableLongStateOf((deadline?.minus(SystemClock.elapsedRealtime())?:0L).coerceAtLeast(0))}
    var showTimer by remember{mutableStateOf(false)}
    var showMore by remember{mutableStateOf(false)}
    val castContext=androidx.compose.ui.platform.LocalContext.current
    var showDevices by remember{mutableStateOf(false)}
    if(showDevices)NeroDeviceSheet(title,artist,playing,canCast=ready&&!videoMode,onCast={
        showDevices=false
        runCatching{castContext.startActivity(android.content.Intent().setClassName(castContext,"br.com.timachado.neromusic.NeroCastActivity"))}.onFailure{android.widget.Toast.makeText(castContext,"Transmissão indisponível.",android.widget.Toast.LENGTH_SHORT).show()}
    },onClose={showDevices=false})
    val nativeHit=shareRef?.let(::musicHitFromRef)?.takeIf{NativeMusicCatalog.enabled}
    val hiddenSongs=rememberHiddenSongs()
    val preferenceUid by NeroSupabase.activeUid.collectAsState()
    val preferenceReady by NeroSupabase.identityReady.collectAsState()
    val preferenceScope=rememberCoroutineScope()
    var downloadInfo by remember(mediaId){mutableStateOf(false)}
    if(downloadInfo)AlertDialog(onDismissRequest={downloadInfo=false},title={Text("Ouvir offline")},text={Text(if(track!=null)"Este arquivo já está no aparelho. Você pode ouvi-lo sem internet." else "Esta faixa não tem autorização de download offline fornecida ao NERO. As músicas com licença de download ficam na categoria Áudio livre.")},confirmButton={TextButton(onClick={downloadInfo=false}){Text("Entendi")}})
    var sharing by remember(title,artist){mutableStateOf(false)}
    if(sharing)TrackShareDialog(title,artist,shareRef,remoteCover,onClose={sharing=false})
    var seekPreview by remember(title,artist,track?.id,fullControls){mutableStateOf<Float?>(null)}
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
    val tones=rememberPlayerTones(track,remoteCover)
    BoxWithConstraints(modifier.fillMaxSize().background(Color(appearance.background))){
        NeroAuroraBackdrop(if(appearance==NeroPlayerTheme.AURORA)tones else if(appearance==NeroPlayerTheme.CAFE)Color(0xFF653044) else aqua,playing&&!videoMode,Modifier.matchParentSize(),primary=neon,secondary=aqua)
        val compact=maxHeight<780.dp
        val coverSize=minOf(maxWidth-36.dp,480.dp).coerceAtLeast(1.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(if(compact)6.dp else 9.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onClose){Icon(Icons.Default.KeyboardArrowDown,"Minimizar player")}
                Column(Modifier.weight(1f).then(if(origin!=null)Modifier.clickable{onOriginClick(origin)}else Modifier),horizontalAlignment=Alignment.CenterHorizontally){
                    Text(origin?.caption?:"NERO MUSIC",fontSize=12.sp,color=Color.LightGray)
                    Text(origin?.displayName?:"Reproduzindo",fontSize=14.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Box{
                    IconButton(onClick={showMore=true}){Icon(Icons.Default.MoreVert,"Mais opções")}
                    DropdownMenu(expanded=showMore,onDismissRequest={showMore=false}){
                        DropdownMenuItem(text={Text("Compartilhar música")},enabled=title.isNotBlank(),onClick={showMore=false;sharing=true},leadingIcon={Icon(Icons.Default.Share,null)})
                        DropdownMenuItem(text={Text("Baixar para ouvir offline")},enabled=title.isNotBlank(),onClick={showMore=false;if(offlineTrack!=null&&allowed(offlineTrack)){if(offlineState.saved.any{it==offlineTrack})onOfflineOpen()else onOfflineSave(offlineTrack)}else if(nativeHit!=null){if(offlineState.nativeSaved.any{it.hit.id==nativeHit.id})onOfflineOpen()else onNativeSave(nativeHit)}else downloadInfo=true},leadingIcon={Icon(Icons.Default.Download,null)})
                        DropdownMenuItem(text={Text(if(favorite)"Remover dos favoritos" else "Curtir música")},enabled=canSave,onClick={showMore=false;onFavorite()},leadingIcon={Icon(if(favorite)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null,tint=if(favorite)neon else Color.White)})
                        if(nativeHit!=null)DropdownMenuItem(text={Text(if(nativeHit.id in hiddenSongs)"Voltar a recomendar esta música" else "Não recomendar esta música")},enabled=preferenceReady,onClick={
                            showMore=false
                            val hidden=nativeHit.id in hiddenSongs
                            val owner=preferenceUid
                            preferenceScope.launch{
                                val ok=try{toggleScopedHiddenSong(castContext,owner,nativeHit.id)}catch(e:CancellationException){throw e}catch(_:Exception){false}
                                if(NeroSupabase.activeUid.value==owner)android.widget.Toast.makeText(castContext,if(!ok)"Não foi possível atualizar a preferência." else if(hidden)"Música liberada para recomendações." else "Música ocultada das recomendações.",android.widget.Toast.LENGTH_SHORT).show()
                            }
                        })
                        DropdownMenuItem(text={Text("Adicionar à playlist")},enabled=canSave,onClick={showMore=false;onSave()})
                        DropdownMenuItem(text={Text(if(radioBusy)"Preparando rádio…" else "Rádio desta faixa")},enabled=ready&&canRadio&&!radioBusy,onClick={showMore=false;onRadio()})
                        if(NativeMusicCatalog.enabled)DropdownMenuItem(text={Text(if(autoplay)"Autoplay online: ligado" else "Autoplay online: desligado")},onClick={showMore=false;onToggleAutoplay()})
                        if(BuildConfig.FULL_FEATURES)DropdownMenuItem(text={Text("Equalizador")},onClick={showMore=false;onEqualizer()})
                        DropdownMenuItem(text={Text("Dispositivos")},onClick={showMore=false;showDevices=true},leadingIcon={Icon(Icons.Default.Devices,null)})
                        DropdownMenuItem(text={Text("Fila de reprodução")},enabled=ready,onClick={showMore=false;onQueue()})
                        DropdownMenuItem(text={Text("Temporizador")},enabled=ready,onClick={showMore=false;showTimer=true})
                    }
                }
            }
            if(canVideo)Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                listOf(false,true).forEach{video->
                    val selected=videoMode==video
                    val shape=RoundedCornerShape(50)
                    val outline=if(selected)neon else aqua.copy(alpha=.7f)
                    Row(Modifier.height(40.dp).widthIn(min=108.dp).clip(shape)
                        .background(if(selected)Brush.horizontalGradient(listOf(neon,if(appearance==NeroPlayerTheme.AURORA)Color(0xFF00DDA3)else aqua))else Brush.horizontalGradient(listOf(Color(0xFF102327),Color(0xFF071619))))
                        .border(1.dp,outline,shape).clickable(enabled=ready&&!selected){onVideoToggle()}.padding(horizontal=12.dp),
                        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                        Icon(if(video)Icons.Default.OndemandVideo else Icons.Default.MusicNote,null,tint=if(selected)Color.Black else neon,modifier=Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp));Text(if(video)"Vídeo" else "Música",color=if(selected)Color.Black else Color.White,fontWeight=FontWeight.Bold,fontSize=14.sp)
                    }
                }
            }
            if(videoMode&&videoPlayer!=null){
                NativeVideoSurface(videoPlayer,Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(14.dp)))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    ClipQualityMenu(videoQuality,onVideoQuality,ready)
                    Row(verticalAlignment=Alignment.CenterVertically){
                        if(pipSupported)IconButton(onClick=onPip){Icon(Icons.Default.PictureInPictureAlt,"Janela flutuante")}
                        IconButton(onClick={fullscreen=true}){Icon(Icons.Default.Fullscreen,"Tela cheia")}
                    }
                }
            }
            else {
                val shape=RoundedCornerShape(12.dp)
                Box(Modifier.size(coverSize).shadow(6.dp,shape).background(panel,shape).clip(shape),contentAlignment=Alignment.Center){
                    if(remoteCover!=null&&track==null)NeroArtwork(model=remoteCover,contentDescription="Capa da faixa",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize(),large=true)
                    else LocalArtwork(track,Modifier.fillMaxSize(),large=true)
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(title.ifBlank{"Nenhuma faixa"},fontSize=if(compact)21.sp else 24.sp,lineHeight=if(compact)24.sp else 27.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(displayArtistName(artist),color=Color.LightGray,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    track?.album?.takeUnless{it=="Álbum desconhecido"}?.let{Text(it,color=Color.LightGray,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
                }
                FilledTonalIconButton(onClick=onSave,enabled=canSave){Icon(Icons.Default.PlaylistAdd,"Adicionar à playlist",tint=neon)}
                FilledTonalIconButton(onClick=onFavorite,enabled=canSave){Icon(if(favorite)Icons.Default.Favorite else Icons.Default.FavoriteBorder,if(favorite)"Descurtir música" else "Curtir música",tint=if(favorite)neon else Color.White)}
                FilledTonalIconButton(onClick=onLyrics,enabled=ready&&title.isNotBlank()){Icon(Icons.Default.Lyrics,"Letras",tint=aqua)}
            }
            Column(Modifier.fillMaxWidth()){
                if(!videoMode)NeroMotionBars(playing,Modifier.fillMaxWidth().height(12.dp),primary=neon,secondary=aqua)
                Slider(modifier=Modifier.fillMaxWidth().height(32.dp),value=seekPreview?:if(duration>0)(position.toFloat()/duration).coerceIn(0f,1f)else 0f,
                    onValueChange={seekPreview=it},onValueChangeFinished={
                        seekPreview?.let{if(duration>0)onSeek((it*duration).toLong())};seekPreview=null
                    },enabled=ready&&duration>0&&fullControls,colors=SliderDefaults.colors(thumbColor=Color.White,activeTrackColor=Color.White,inactiveTrackColor=Color.White.copy(alpha=.25f),disabledThumbColor=Color.White,disabledActiveTrackColor=Color.White,disabledInactiveTrackColor=Color.White.copy(alpha=.25f)))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Text(clock(seekPreview?.let{(it*duration).toLong()}?:position),fontSize=12.sp,color=Color.LightGray)
                    Text(clock(duration),fontSize=12.sp,color=Color.LightGray)
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onShuffle,enabled=ready&&fullControls){Icon(Icons.Default.Shuffle,if(fullControls)"Aleatório" else "Aleatório obrigatório no Free",tint=if(shuffle||!fullControls)neon else Color.White,modifier=Modifier.size(26.dp))}
                IconButton(onClick=onPrevious,enabled=ready&&fullControls){Icon(Icons.Default.SkipPrevious,"Anterior",tint=if(fullControls)Color.White else Color.White.copy(alpha=.32f),modifier=Modifier.size(34.dp))}
                FilledIconButton(onClick=onPlayPause,enabled=ready,modifier=Modifier.size(if(compact)66.dp else 76.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.White,contentColor=Color.Black)){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Reproduzir ou pausar",Modifier.size(36.dp))}
                IconButton(onClick=onNext,enabled=ready&&nextAvailable){Icon(Icons.Default.SkipNext,"Próxima",tint=if(ready&&nextAvailable)Color.White else Color.White.copy(alpha=.32f),modifier=Modifier.size(34.dp))}
                IconButton(onClick={showTimer=true},enabled=ready){Icon(Icons.Default.Timer,"Temporizador de sono",tint=if(deadline!=null)neon else Color.White,modifier=Modifier.size(26.dp))}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={showDevices=true}){Icon(Icons.Default.Devices,"Dispositivos de áudio",tint=Color.White)}
                Spacer(Modifier.weight(1f))
                IconButton(onClick=onRepeat,enabled=ready&&fullControls){Icon(if(repeat==Player.REPEAT_MODE_ONE)Icons.Default.RepeatOne else Icons.Default.Repeat,"Repetição",tint=if(!fullControls)Color.White.copy(alpha=.32f) else if(repeat!=Player.REPEAT_MODE_OFF)neon else Color.White)}
                if(BuildConfig.FULL_FEATURES)IconButton(onClick=onEqualizer){Icon(Icons.Default.Equalizer,"Equalizador",tint=Color.White)}
                IconButton(onClick={sharing=true},enabled=title.isNotBlank()){Icon(Icons.Default.Share,"Compartilhar",tint=Color.White)}
                IconButton(onClick=onQueue,enabled=ready){Icon(Icons.Default.QueueMusic,"Fila",tint=Color.White)}
            }
            if(!fullControls)Text("NERO Free · ordem aleatória. Próxima liberada; voltar, repetir e ajustar a posição são recursos Premium.",fontSize=12.sp,color=Color.LightGray)
            if(nativeHit!=null){
                val saved=offlineState.nativeSaved.any{it.hit.id==nativeHit.id}
                val active=offlineState.nativeActive?.id==nativeHit.id
                OutlinedButton(enabled=saved||(offlineState.active==null&&offlineState.nativeActive==null&&offlineState.batchTotal==0),onClick={if(saved)onOfflineOpen()else onNativeSave(nativeHit)},modifier=Modifier.fillMaxWidth()){
                    Icon(if(saved)Icons.Default.CheckCircle else Icons.Default.Download,null,tint=if(saved)neon else aqua)
                    Spacer(Modifier.width(8.dp))
                    Text(if(saved)"Baixada · ouvir offline" else if(active)"Baixando "+(offlineState.progress*100).toInt()+"%" else "Baixar música")
                }
                if(active)LinearProgressIndicator(progress={offlineState.progress},modifier=Modifier.fillMaxWidth())
                offlineState.notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
            }else OfflinePlayerButton(offlineTrack,track!=null,offlineState,onSave=onOfflineSave,onOpen=onOfflineOpen,onInfo={downloadInfo=true},enabled=title.isNotBlank())
            OutlinedButton(enabled=title.isNotBlank(),onClick={sharing=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("Compartilhar música")}
            if(deadline!=null)Text("Pausar em "+clock(remaining),color=neon,fontSize=12.sp)
            if(canRadio)OutlinedButton(onClick=onRadio,enabled=ready&&!radioBusy){Icon(Icons.Default.Radio,null);Spacer(Modifier.width(8.dp));Text("Rádio desta faixa")}
            error?.let{
                Text(if(videoMode)"Não foi possível reproduzir o vídeo. Tente novamente ou volte à música." else it,color=MaterialTheme.colorScheme.error)
                OutlinedButton(enabled=ready,onClick=onRetry,modifier=Modifier.fillMaxWidth()){Text("Tentar novamente")}
                if(videoMode)OutlinedButton(onClick=onVideoToggle){Text("Voltar para música")}
            }
            if(mediaId.isNotBlank()){
                PlayerLyricsPreview(mediaId,title,artist,position,duration,track?.uri,onLyrics)
                PlayerContextShelf(shareRef,artist,origin,ready,onOpenAlbum,onExploreArtist,onRelatedQueue)
            }
        }
    }
}
