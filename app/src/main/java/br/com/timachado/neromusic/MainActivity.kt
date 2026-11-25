package br.com.timachado.neromusic

import android.Manifest
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.util.Rational
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

private val Blue = Color(0xFF0066FF)
private val Cyan = Color(0xFF00E5FF)
private val Green = Color(0xFF39FF14)
private val SurfaceDark = Color(0xFF10171C)

class MainActivity : ComponentActivity() {
    private var pipMode by mutableStateOf(false)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode:Boolean,newConfig:Configuration){
        super.onPictureInPictureModeChanged(isInPictureInPictureMode,newConfig)
        pipMode=isInPictureInPictureMode
    }
    private fun enterClipPip():Boolean{
        if(!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))return false
        return runCatching{enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16,9)).build())}.getOrDefault(false)
    }
    private var incomingShare by mutableStateOf<String?>(null)
    private var shareRevision by mutableIntStateOf(0)
    private fun acceptShare(intent:Intent?){
        val link=intent?.dataString ?: return
        if(intent.action==Intent.ACTION_VIEW&&ShareLinks.parse(link)!=null){incomingShare=link;shareRevision++}
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);acceptShare(intent)}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pipMode=isInPictureInPictureMode
        if(savedInstanceState==null)acceptShare(intent) else savedInstanceState.getString("nero_pending_share")?.let{incomingShare=it}
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Green, secondary = Cyan,
            background = Color.Black, surface = SurfaceDark, onPrimary = Color.Black,
            primaryContainer = Color(0xFF102B23), onPrimaryContainer = Green,
            secondaryContainer = Color(0xFF12313A), onSecondaryContainer = Cyan,
            tertiary = Blue, tertiaryContainer = Color(0xFF10243E), onTertiaryContainer = Cyan,
            onBackground = Color.White, onSurface = Color.White)) { NeroApp(incomingShare,shareRevision,onShareClosed={incomingShare=null},pipMode=pipMode,pipSupported=packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE),onPip={enterClipPip()}) } }
    }
    override fun onSaveInstanceState(outState:Bundle){super.onSaveInstanceState(outState);outState.putString("nero_pending_share",incomingShare)}
}
private fun timestamp(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
@Composable
private fun NeroApp(incomingShare:String?,shareRevision:Int,onShareClosed:()->Unit,pipMode:Boolean,pipSupported:Boolean,onPip:()->Boolean) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var scan by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(granted, scan) {
        if (granted) {
            loading = true
            error = null
            try { tracks = withContext(Dispatchers.IO) { loadTracks(context) } }
            catch (e: SecurityException) { granted = false; error = "Acesso às músicas não autorizado." }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = "Não foi possível carregar a biblioteca." }
            finally { loading = false }
        } else tracks = emptyList()
    }
    val prefs = remember { context.getSharedPreferences("nero_library", 0) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    fun toggle(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        prefs.edit().putStringSet("favorites", favorites).apply()
    }
    val radioScope=rememberCoroutineScope()
    var radioBusy by remember{mutableStateOf(false)}
    var autoplay by remember{mutableStateOf(NeroAutoplay.enabled(context))}
    var onlineFavorites by remember { mutableStateOf(runCatching{readOnlineFavorites(context)}) }
    fun toggleOnline(ref:OnlineRef){
        try{val current=readOnlineFavorites(context);val next=if(current.any{it.id==ref.id})current.filterNot{it.id==ref.id}else listOf(ref)+current
            saveOnlineFavorites(context,next);onlineFavorites=Result.success(next)
        }catch(_:Exception){android.widget.Toast.makeText(context,"Não foi possível atualizar favoritos.",android.widget.Toast.LENGTH_SHORT).show()}
    }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var connectionError by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        var active = true
        future.addListener({ if (active) try { controller = future.get() } catch (_: Exception) { connectionError = true } }, ContextCompat.getMainExecutor(context))
        onDispose { active = false; MediaController.releaseFuture(future) }
    }
    var nowId by remember { mutableStateOf("") }
    var videoMode by remember{mutableStateOf(false)}
    var videoQuality by remember{mutableIntStateOf(0)}
    var nowOrigin by remember{mutableStateOf<PlaybackOrigin?>(null)}
    var nowOpenRef by remember { mutableStateOf<OnlineRef?>(null) }
    var addLocal by remember { mutableStateOf<Track?>(null) }
    var addOnline by remember { mutableStateOf<OnlineRef?>(null) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(controller) {
        val p = controller ?: return@LaunchedEffect
        while (true) {
            val currentItem=p.currentMediaItem
            nowId = currentItem?.mediaId.orEmpty()
            nowOrigin=currentItem?.playbackOrigin()
            videoMode=currentItem?.isNeroVideo()==true
            videoQuality=currentItem?.videoQuality()?:0
            nowOpenRef = currentItem?.let{openRefFromItem(it)?:youtubeRefFromItem(it)}
            title = p.mediaMetadata.title?.toString().orEmpty()
            artist = p.mediaMetadata.artist?.toString().orEmpty()
            playing = p.isPlaying
            pos = p.currentPosition.coerceAtLeast(0)
            duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            shuffle = p.shuffleModeEnabled
            repeat = p.repeatMode
            playbackError = p.playerError?.let { "Não foi possível reproduzir esta faixa." }
            delay(350)
        }
    }
    var tab by remember { mutableIntStateOf(3) }
    var searchSeed by remember { mutableStateOf("") }
    var searchAutoFocus by remember { mutableStateOf(false) }
    var searchOpenAudio by remember { mutableStateOf(false) }

    var libraryPage by remember { mutableIntStateOf(8) }
    var librarySelection by remember { mutableStateOf<String?>(null) }
    var libraryCreate by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val offlineManager=remember(context){OfflineManager(context.applicationContext)}
    val offlineState by offlineManager.state.collectAsState()
    LaunchedEffect(Unit){offlineManager.reload()}
    var fullPlayer by rememberSaveable { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showPlans by remember { mutableStateOf(false) }
    var showCloud by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var playerArtist by remember{mutableStateOf<String?>(null)}
    var playerAlbum by remember{mutableStateOf<NativeCollection?>(null)}
    BackHandler(fullPlayer || showAccount || showPlans || showEq || showCloud || showLyrics || showQueue) { if(showQueue) showQueue = false else if(showLyrics) showLyrics = false else if(showCloud) showCloud = false else if(showEq) showEq = false else if(showPlans) showPlans = false else if(showAccount) showAccount = false else fullPlayer = false }
    val filtered = remember(tracks, tab, query, favorites) {
        tracks.filter { t -> (tab != 2 || t.id.toString() in favorites) &&
            (tab != 1 || t.title.contains(query, true) || t.artist.contains(query, true) || t.album.contains(query,true) || t.genre.contains(query,true)) }
    }
    fun play(t: Track) {
        val p = controller ?: return
        val queue = filtered.ifEmpty { listOf(t) }
        p.setMediaItems(queue.map { it.mediaItem() }, queue.indexOf(t).coerceAtLeast(0), 0L)
        p.prepare(); p.play()
    }
    fun startRadio(queue:List<Track>) {
        val p=controller ?: return
        if(queue.isEmpty())return
        p.shuffleModeEnabled=false
        p.repeatMode=Player.REPEAT_MODE_OFF
        p.setMediaItems(queue.map{it.mediaItem()},0,0L)
        p.prepare();p.play()
    }
    if(pipMode){
        Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){
            val p=controller
            if(p!=null&&videoMode)NativeVideoSurface(p,Modifier.fillMaxSize())
            else if(nowOpenRef?.coverUrl!=null)AsyncImage(model=nowOpenRef?.coverUrl,contentDescription=null,modifier=Modifier.fillMaxHeight().aspectRatio(1f))
            else Icon(Icons.Default.MusicNote,null,tint=Cyan)
        }
        return
    }
    addLocal?.let { SaveLocalTrackDialog(it,onClose={addLocal=null}) }
    addOnline?.let { SaveOnlineDialog(listOf(it),onClose={addOnline=null}) }
    incomingShare?.let{link->
        key(shareRevision,link){
            ReceiveShareDialog(initialLink=link,onClose=onShareClosed,onImport={incoming->
                val next=onlineLists(context)+OnlineList(java.util.UUID.randomUUID().toString(),incoming.name,sharedPlaylistRefs(incoming))
                saveOnlineLists(context,next)
                fullPlayer=false;showAccount=false;showPlans=false;showEq=false;showCloud=false;showLyrics=false;showQueue=false
                tab=0;libraryPage=7;librarySelection=next.last().id;onShareClosed()
            },onPlay=if(controller!=null){incoming:SharedPlaylist->
                val queue=sharedPlaylistRefs(incoming).mapNotNull{if(NativeMusicCatalog.enabled)musicHitFromRef(it)?.queueItem()else null}
                if(queue.isNotEmpty()){controller?.let{player->player.shuffleModeEnabled=false;player.repeatMode=Player.REPEAT_MODE_OFF;player.setMediaItems(queue,0,0L);player.prepare();player.play()};fullPlayer=true}
                else{val first=incoming.songs.first();searchSeed=first.title+" "+first.artist;searchAutoFocus=false;tab=1;fullPlayer=false}
                showAccount=false;showPlans=false;showEq=false;showCloud=false;showLyrics=false;showQueue=false
                onShareClosed()
            }else null)
        }
    }
    Scaffold(containerColor = Color.Black, modifier = Modifier.fillMaxSize().safeDrawingPadding(), bottomBar = {
        if (playerAlbum==null && playerArtist==null && !fullPlayer && !showAccount && !showPlans && !showEq && !showCloud && !showLyrics && !showQueue) Column {
            if (nowId.isNotEmpty()) Row(Modifier.fillMaxWidth().background(SurfaceDark).clickable { fullPlayer = true }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val cover=nowOpenRef?.coverUrl
                if(cover!=null)AsyncImage(model=cover,contentDescription="Capa atual",modifier=Modifier.size(48.dp),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                else LocalArtwork(tracks.firstOrNull{it.id.toString()==nowId},Modifier.size(48.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(artist, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                }
                IconButton(onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproduzir ou pausar", tint = Green) }
                IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "Próxima") }
            }
            NavigationBar(containerColor = Color.Black) {
                NavigationBarItem(colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFF102B23), unselectedIconColor = Color(0xFFB9C6CF), unselectedTextColor = Color(0xFFB9C6CF)), selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Início") })
                NavigationBarItem(colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFF102B23), unselectedIconColor = Color(0xFFB9C6CF), unselectedTextColor = Color(0xFFB9C6CF)), selected = tab == 0, onClick = { tab = 0;libraryPage=8;librarySelection=null }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Biblioteca") })
                NavigationBarItem(colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFF102B23), unselectedIconColor = Color(0xFFB9C6CF), unselectedTextColor = Color(0xFFB9C6CF)), selected = tab == 1, onClick = { if(tab!=1){searchSeed="";searchAutoFocus=true};tab = 1 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Buscar") })
                NavigationBarItem(colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFF102B23), unselectedIconColor = Color(0xFFB9C6CF), unselectedTextColor = Color(0xFFB9C6CF)), selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Favoritos") })
                NavigationBarItem(colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFF102B23), unselectedIconColor = Color(0xFFB9C6CF), unselectedTextColor = Color(0xFFB9C6CF)), selected = tab == 4, onClick = { tab = 4 }, icon = { Icon(Icons.Default.Download, null) }, label = { Text("Offline") })
            }
        }
    }) { padding ->
        if(playerAlbum!=null){
            val album=playerAlbum!!
            BackHandler{playerAlbum=null;fullPlayer=true}
            key(album.url){NativeAlbumScreen(album,controller!=null,onBack={playerAlbum=null;fullPlayer=true},onQueue={queue,index->controller?.let{p->if(queue.isNotEmpty()){p.shuffleModeEnabled=false;p.repeatMode=Player.REPEAT_MODE_OFF;p.setMediaItems(queue,index,0L);p.prepare();p.play()}};playerAlbum=null;fullPlayer=true})}
        } else if(playerArtist!=null){
            val name=playerArtist!!
            BackHandler{playerArtist=null;fullPlayer=true}
            key(name){NativeArtistScreen(name,emptyList(),controller!=null,onBack={playerArtist=null;fullPlayer=true},onQueue={queue,index->controller?.let{p->if(queue.isNotEmpty()){p.shuffleModeEnabled=false;p.repeatMode=Player.REPEAT_MODE_OFF;p.setMediaItems(queue,index,0L);p.prepare();p.play()}};playerArtist=null;fullPlayer=true},onExplore={term->playerArtist=null;fullPlayer=false;searchSeed=term;searchAutoFocus=false;tab=1},onAlbum={playerAlbum=it})}
        } else if (showQueue) {
            QueueScreen(controller = controller, tracks = tracks, onBack = { showQueue = false }, modifier = Modifier.padding(padding))
        } else if (showLyrics) {
            LyricsScreen(mediaId = nowId, title = title, artist = artist, position = pos, duration = duration, playing = playing,
                localUri = tracks.firstOrNull { it.id.toString() == nowId }?.uri,
                onTogglePlay = { controller?.let { if(it.isPlaying) it.pause() else it.play() } },
                onSeek = { controller?.seekTo(it) }, onBack = { showLyrics = false }, modifier = Modifier.padding(padding))
        } else if (showCloud && BuildConfig.DEBUG) {
            CloudLibraryScreen(tracks = tracks, favoriteIds = favorites, granted = granted,
                onMergeFavorites = { ids -> favorites = favorites + ids; prefs.edit().putStringSet("favorites", favorites).apply() },
                onBack = { showCloud = false }, modifier = Modifier.padding(padding))
        } else if (showEq && BuildConfig.DEBUG) {
            EqualizerScreen(onBack = { showEq = false }, modifier = Modifier.padding(padding))
        } else if (showPlans) {
            PlansScreen(onBack = { showPlans = false }, modifier = Modifier.padding(padding))
        } else if (showAccount) {
            NeroSupabaseAccount(onBack={showAccount=false},modifier=Modifier.padding(padding))
        } else if (fullPlayer) {
            val currentTrack=tracks.firstOrNull{it.id.toString()==nowId}
            ImmersivePlayer(
                track=currentTrack,remoteCover=nowOpenRef?.coverUrl,shareRef=nowOpenRef,title=title,artist=artist,position=pos,duration=duration,playing=playing,mediaId=nowId,origin=nowOrigin,
                offlineTrack=nowOpenRef?.openAudio,offlineState=offlineState,onOfflineSave={t->radioScope.launch{offlineManager.save(t)}},onOfflineOpen={fullPlayer=false;tab=4},
                videoPlayer=controller,videoMode=videoMode,pipSupported=pipSupported,onPip={if(!onPip())android.widget.Toast.makeText(context,"Janela flutuante indisponível. Confira a permissão no Android.",android.widget.Toast.LENGTH_SHORT).show()},canVideo=NativeMusicCatalog.enabled&&nowOpenRef?.let(::musicHitFromRef)!=null,
                videoQuality=videoQuality,onVideoQuality={quality->controller?.let{p->
                    val item=p.currentMediaItem
                    val index=p.currentMediaItemIndex
                    val replacement=item?.takeIf{it.isNeroVideo()}?.withVideoMode(true,quality)
                    if(replacement!=null&&index>=0&&quality!=item?.videoQuality()&&p.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)){
                        val at=p.currentPosition.coerceAtLeast(0L);val resume=p.playWhenReady
                        p.replaceMediaItem(index,replacement);p.seekTo(index,at);p.prepare();p.playWhenReady=resume
                    }
                }},
                onVideoToggle={controller?.let{p->
                    val item=p.currentMediaItem
                    val index=p.currentMediaItemIndex
                    val replacement=item?.withVideoMode(item.isNeroVideo().not())
                    if(replacement!=null&&index>=0&&p.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)){
                        val at=p.currentPosition.coerceAtLeast(0L);val resume=p.playWhenReady
                        p.replaceMediaItem(index,replacement);p.seekTo(index,at);p.prepare();p.playWhenReady=resume
                    }
                }},
                onOriginClick={origin->when(origin.kind){
                    "album"->origin.album()?.let{playerAlbum=it}
                    "artist"->playerArtist=origin.name
                    "chart"->{fullPlayer=false;tab=3}
                    "playlist","local_playlist","imported"->{fullPlayer=false;tab=0;libraryPage=when(origin.kind){"playlist"->7;"local_playlist"->1;else->6};librarySelection=origin.id;libraryCreate=false}
                    "favorites"->{fullPlayer=false;tab=2}
                    "search"->{fullPlayer=false;searchSeed=origin.name;searchAutoFocus=false;tab=1}
                    else->showQueue=true
                }},onOpenAlbum={playerAlbum=it},onExploreArtist={term->playerArtist=term},
                onRelatedQueue={queue,index->controller?.let{p->if(queue.isNotEmpty()){p.shuffleModeEnabled=false;p.repeatMode=Player.REPEAT_MODE_OFF;p.setMediaItems(queue,index,0L);p.prepare();p.play()}}},
                favorite=nowId in favorites||onlineFavorites.getOrDefault(emptyList()).any{it.id==nowId},shuffle=shuffle,repeat=repeat,ready=controller!=null,error=playbackError,
                onClose={fullPlayer=false},onFavorite={if(currentTrack!=null)toggle(nowId) else nowOpenRef?.let{toggleOnline(it)}},
                canSave=currentTrack!=null||nowOpenRef!=null,onSave={if(currentTrack!=null)addLocal=currentTrack else addOnline=nowOpenRef},
                onSeek={controller?.seekTo(it)},onPlayPause={controller?.let{if(it.isPlaying)it.pause()else it.play()}},
                onPrevious={controller?.seekToPreviousMediaItem()},onNext={controller?.seekToNextMediaItem()},
                onShuffle={controller?.let{it.shuffleModeEnabled=!it.shuffleModeEnabled}},
                onRepeat={controller?.let{it.repeatMode=when(it.repeatMode){Player.REPEAT_MODE_OFF->Player.REPEAT_MODE_ALL;Player.REPEAT_MODE_ALL->Player.REPEAT_MODE_ONE;else->Player.REPEAT_MODE_OFF}}},
                onLyrics={showLyrics=true},onQueue={showQueue=true},onEqualizer={showEq=true},
                canRadio=currentTrack!=null||(NativeMusicCatalog.enabled&&nowOpenRef?.let(::musicHitFromRef)!=null),radioBusy=radioBusy,autoplay=autoplay,
                onToggleAutoplay={autoplay=!autoplay;NeroAutoplay.set(context,autoplay)},
                onRadio={
                    if(currentTrack!=null){val next=buildRadio(tracks,favorites,history(context),currentTrack);if(next.isNotEmpty())startRadio(next)}
                    else if(!radioBusy){
                        val seed=nowOpenRef?.let(::musicHitFromRef)
                        val p=controller
                        if(seed!=null&&p!=null){
                            radioBusy=true
                            radioScope.launch{
                                try{
                                    val rows=onlineRadio(seed,setOf(seed.id))
                                    if(controller===p&&p.currentMediaItem?.mediaId=="youtube:"+seed.id){
                                        if(rows.isNotEmpty()){
                                            p.shuffleModeEnabled=false;p.repeatMode=Player.REPEAT_MODE_OFF
                                            p.setMediaItems((listOf(seed)+rows).map{it.queueItem()},0,0L);p.prepare();p.play()
                                        }else android.widget.Toast.makeText(context,"Nenhuma faixa para a rádio.",android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }catch(e:CancellationException){throw e}catch(_:Exception){android.widget.Toast.makeText(context,"Rádio temporariamente indisponível.",android.widget.Toast.LENGTH_SHORT).show()}
                                finally{radioBusy=false}
                            }
                        }
                    }
                },
                modifier=Modifier.padding(padding)
            )
        } else Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(14.dp))
            if(tab!=1&&tab!=0){
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_nero), null, tint = Color.Unspecified, modifier = Modifier.size(40.dp))
                Text(" NERO", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(" MUSIC", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Green)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showAccount = true }) { Icon(Icons.Default.AccountCircle, "Minha conta", tint = Cyan) }
                IconButton(onClick = { if (granted) scan++ else request.launch(permission) }) { Icon(Icons.Default.Refresh, "Atualizar biblioteca") }
            }
            Text("O som da sua liberdade.", color = Color.LightGray, fontSize = 13.sp)
            Spacer(Modifier.height(22.dp))
            }
            if(tab==0&&libraryPage!=8){
                Row(verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick={libraryPage=8;librarySelection=null}){Icon(Icons.Default.ArrowBack,"Voltar à biblioteca")}
                    Text("Sua biblioteca",fontSize=22.sp,fontWeight=FontWeight.Bold)
                }
                BackHandler{libraryPage=8;librarySelection=null}
            }
            if(tab==0&&libraryPage==8){
                PlaylistHub(tracks=tracks,onlineFavoriteCount=onlineFavorites.getOrDefault(emptyList()).size,localFavoriteCount=favorites.size,
                    onAccount={showAccount=true},onFavorites={tab=2},onOpen={page,id->libraryPage=page;librarySelection=id;libraryCreate=false},
                    onSearch={searchSeed="";searchAutoFocus=true;tab=1},onCreate={libraryPage=7;librarySelection=null;libraryCreate=true})
            }else if(tab == 0 && libraryPage == 7) {
                OnlineListsScreen(tracks=tracks,canPlay=controller!=null,initialSelection=librarySelection,createOnOpen=libraryCreate,onCreated={libraryCreate=false},onSearch={term->searchSeed=term;searchAutoFocus=false;tab=1},onPlay={queue,index,random->controller?.let{p->
                    if(queue.isNotEmpty()){p.shuffleModeEnabled=random;p.setMediaItems(queue,index.coerceIn(0,queue.lastIndex),0L);p.prepare();p.play()}
                }})
            } else if(tab == 0 && libraryPage == 6) {
                TransferScreen(tracks = tracks, canPlay = controller != null, initialSelection=librarySelection, onPlay = { queue, index, random, origin -> controller?.let { p ->
                    if(queue.isNotEmpty()) { p.shuffleModeEnabled = random; p.setMediaItems(queue.map { it.mediaItem() }.withOrigin(origin), index.coerceIn(0,queue.lastIndex),0L); p.prepare();p.play() }
                } })
            } else if(tab == 0 && libraryPage >= 3) {
                key(libraryPage) {
                    LocalCollectionsScreen(kind = libraryPage, tracks = tracks, granted = granted, canPlay = controller != null,
                        onPermission = { request.launch(permission) }, onPlay = { queue, index -> controller?.let { p ->
                            if(queue.isNotEmpty()) { p.setMediaItems(queue.map { it.mediaItem() }, index.coerceIn(0,queue.lastIndex),0L);p.prepare();p.play() }
                        } })
                }
            } else if(tab == 0 && libraryPage != 0) {
                PersonalLibraryScreen(historyMode = libraryPage == 2, tracks = tracks, granted = granted, initialSelection=librarySelection,
                    canPlay = controller != null, onPermission = { request.launch(permission) },
                    onPlay = { queue, index, origin -> controller?.let { p ->
                        if(queue.isNotEmpty()) { p.shuffleModeEnabled=false; p.setMediaItems(queue.map { it.mediaItem() }.withOrigin(origin), index.coerceIn(0,queue.lastIndex), 0L); p.prepare(); p.play() }
                    } }, onShuffle = { queue, origin -> controller?.let { p ->
                        if(queue.isNotEmpty()) { p.shuffleModeEnabled=true; p.setMediaItems(queue.map{it.mediaItem()}.withOrigin(origin),queue.indices.random(),0L);p.prepare();p.play() }
                    } })
            } else if(tab == 1) {
                NeroSearch(tracks=tracks,canPlay=controller!=null,offlineState=offlineState,onOfflineSave={t->radioScope.launch{offlineManager.save(t)}},initialQuery=searchSeed,initialOpenAudio=searchOpenAudio,autoFocus=searchAutoFocus,onExit={searchOpenAudio=false;tab=3},onlineFavoriteIds=onlineFavorites.getOrDefault(emptyList()).map{it.id}.toSet(),onFavorite={toggleOnline(it)},currentId=nowId,currentTitle=title,currentArtist=artist,currentCover=nowOpenRef?.coverUrl,currentPlaying=playing,currentVideo=videoMode,onCurrentToggle={controller?.let{p->if(p.playWhenReady)p.pause()else p.play()}},onOpenPlayer={fullPlayer=true},onNativeQueue={queue,index->controller?.let{p->
                    if(queue.isNotEmpty()){p.shuffleModeEnabled=false;p.repeatMode=Player.REPEAT_MODE_OFF;p.setMediaItems(queue,index,0L);p.prepare();p.play()}
                }},
                    onPlay={track->controller?.let{p->p.setMediaItem(track.mediaItem());p.prepare();p.play()}},
                    onOpenPlay={item->controller?.let{p->p.setMediaItem(item);p.prepare();p.play()}})
            } else if(tab==2){
                FavoritesScreen(localTracks=filtered,online=onlineFavorites.getOrDefault(emptyList()),readError=onlineFavorites.isFailure,canPlay=controller!=null,
                    onLocalRemove={toggle(it.id.toString())},onOnlineRemove={toggleOnline(it)},onSearch={term->searchSeed=term;searchAutoFocus=false;tab=1},onQueue={queue,index->controller?.let{p->
                        if(queue.isNotEmpty()){p.shuffleModeEnabled=false;p.repeatMode=Player.REPEAT_MODE_OFF;p.setMediaItems(queue.withOrigin(PlaybackOrigin("favorites","Músicas curtidas","")),index,0L);p.prepare();p.play()}
                    }})
            } else if(tab==4){
                LaunchedEffect(Unit){offlineManager.reload()}
                OfflineLibrary(offlineState,canPlay=controller!=null,onBrowse={searchOpenAudio=true;searchSeed="";searchAutoFocus=true;tab=1},onPlay={queue,index->controller?.let{p->if(queue.isNotEmpty()){p.shuffleModeEnabled=false;p.repeatMode=Player.REPEAT_MODE_OFF;p.setMediaItems(queue,index,0L);p.prepare();p.play()}}})
            } else if (tab == 3) {
                key(tab) {
                DiscoveryScreen(offline = tab == 4, canPlay = controller != null, searchOnly = tab == 1, localTracks = tracks, favoriteIds = favorites, onlineFavorites=onlineFavorites.getOrDefault(emptyList()), onSearch = { term -> searchSeed=term;searchAutoFocus=false;tab=1 }, onSearchFocus={searchSeed="";searchAutoFocus=true;tab=1}, onNativeQueue={queue,index->controller?.let{p->
                    if(queue.isNotEmpty()){p.shuffleModeEnabled=false;p.repeatMode=Player.REPEAT_MODE_OFF;p.setMediaItems(queue,index,0L);p.prepare();p.play()}
                }}, onRadio = { queue -> startRadio(queue) }, onPlay = { item ->
                    controller?.let { p -> p.setMediaItem(item); p.prepare(); p.play() }
                })
                }
            } else {
            Text(when(tab) { 1 -> "Buscar músicas"; 2 -> "Favoritos"; else -> "Suas músicas" }, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            if (tab == 1) OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), placeholder = { Text("Música ou artista") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) })
            if (!granted) {
                Spacer(Modifier.height(24.dp)); Text("Permita o acesso ao áudio do dispositivo para montar sua biblioteca local.")
                Button(onClick = { request.launch(permission) }, modifier = Modifier.padding(top = 12.dp)) { Text("Permitir acesso") }
            } else if (loading) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            } else if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            else if (filtered.isEmpty()) Text(if (tab == 2) "Suas músicas favoritas aparecem aqui." else "Nenhuma música encontrada.", modifier = Modifier.padding(vertical = 20.dp), color = Color.LightGray)
            else {
                Text("${filtered.size} músicas", color = Color.LightGray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { t ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = controller != null) { play(t) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            LocalArtwork(t, Modifier.size(48.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(t.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (nowId == t.id.toString()) Green else Color.White)
                                Text(t.artist + " · " + timestamp(t.duration), fontSize = 12.sp, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { toggle(t.id.toString()) }) { Icon(if (t.id.toString() in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favoritar ${t.title}", tint = if (t.id.toString() in favorites) Green else Color.LightGray) }
                            QueueAddMenu(controller,t)
                        }
                    }
                }
            }
            }
            if (connectionError) Text("Falha na conexão com o player.", color = MaterialTheme.colorScheme.error)
            playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
