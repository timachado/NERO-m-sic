package br.com.timachado.neromusic

import android.Manifest
import android.content.ComponentName
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
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.withContext

private val Blue = Color(0xFF0066FF)
private val Cyan = Color(0xFF00E5FF)
private val Green = Color(0xFF39FF14)
private val SurfaceDark = Color(0xFF10171C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Green, secondary = Cyan,
            background = Color.Black, surface = SurfaceDark, onPrimary = Color.Black,
            primaryContainer = Color(0xFF102B23), onPrimaryContainer = Green,
            secondaryContainer = Color(0xFF12313A), onSecondaryContainer = Cyan,
            tertiary = Blue, tertiaryContainer = Color(0xFF10243E), onTertiaryContainer = Cyan,
            onBackground = Color.White, onSurface = Color.White)) { NeroApp() } }
    }
}
private fun timestamp(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
@Composable
private fun NeroApp() {
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
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var connectionError by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        var active = true
        future.addListener({ if (active) try { controller = future.get() } catch (_: Exception) { connectionError = true } }, ContextCompat.getMainExecutor(context))
        onDispose { active = false; MediaController.releaseFuture(future) }
    }
    var nowId by remember { mutableStateOf("") }
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
            nowId = p.currentMediaItem?.mediaId.orEmpty()
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
    var localSearch by remember { mutableStateOf(false) }
    var libraryPage by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var fullPlayer by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showPlans by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    BackHandler(fullPlayer || showAccount || showPlans || showEq) { if(showEq) showEq = false else if(showPlans) showPlans = false else if(showAccount) showAccount = false else fullPlayer = false }
    val filtered = remember(tracks, tab, query, favorites) {
        tracks.filter { t -> (tab != 2 || t.id.toString() in favorites) &&
            (tab != 1 || t.title.contains(query, true) || t.artist.contains(query, true)) }
    }
    fun play(t: Track) {
        val p = controller ?: return
        val queue = filtered.ifEmpty { listOf(t) }
        p.setMediaItems(queue.map { it.mediaItem() }, queue.indexOf(t).coerceAtLeast(0), 0L)
        p.prepare(); p.play()
    }
    Scaffold(containerColor = Color.Black, modifier = Modifier.fillMaxSize().safeDrawingPadding(), bottomBar = {
        if (!fullPlayer && !showAccount && !showPlans && !showEq) Column {
            if (nowId.isNotEmpty()) Row(Modifier.fillMaxWidth().background(SurfaceDark).clickable { fullPlayer = true }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, tint = Cyan)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(artist, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                }
                IconButton(onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproduzir ou pausar", tint = Green) }
                IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "Próxima") }
            }
            NavigationBar(containerColor = Color.Black) {
                NavigationBarItem(colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFF102B23), unselectedIconColor = Color(0xFFB9C6CF), unselectedTextColor = Color(0xFFB9C6CF)), selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Início") })
                NavigationBarItem(colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFF102B23), unselectedIconColor = Color(0xFFB9C6CF), unselectedTextColor = Color(0xFFB9C6CF)), selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Biblioteca") })
                NavigationBarItem(colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFF102B23), unselectedIconColor = Color(0xFFB9C6CF), unselectedTextColor = Color(0xFFB9C6CF)), selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Buscar") })
                NavigationBarItem(colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFF102B23), unselectedIconColor = Color(0xFFB9C6CF), unselectedTextColor = Color(0xFFB9C6CF)), selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Favoritos") })
                NavigationBarItem(colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFF102B23), unselectedIconColor = Color(0xFFB9C6CF), unselectedTextColor = Color(0xFFB9C6CF)), selected = tab == 4, onClick = { tab = 4 }, icon = { Icon(Icons.Default.Download, null) }, label = { Text("Offline") })
            }
        }
    }) { padding ->
        if (showEq && BuildConfig.DEBUG) {
            EqualizerScreen(onBack = { showEq = false }, modifier = Modifier.padding(padding))
        } else if (showPlans) {
            PlansScreen(onBack = { showPlans = false }, modifier = Modifier.padding(padding))
        } else if (showAccount) {
            AccountScreen(onBack = { showAccount = false }, onPlans = { showPlans = true }, modifier = Modifier.padding(padding))
        } else if (fullPlayer) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { fullPlayer = false }) { Icon(Icons.Default.KeyboardArrowDown, "Minimizar") }
                    Text("REPRODUZINDO", color = Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if(BuildConfig.DEBUG) IconButton(onClick = { showEq = true }) { Icon(Icons.Default.Equalizer, "Equalizador", tint = Green) }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Brush.linearGradient(listOf(Blue.copy(alpha=.65f), SurfaceDark, Green.copy(alpha=.35f))), RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.GraphicEq, null, Modifier.size(116.dp), tint = Cyan)
                }
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(artist, color = Color.LightGray, maxLines = 1) }
                    IconButton(enabled = nowId.toLongOrNull() != null, onClick = { if (nowId.toLongOrNull() != null) toggle(nowId) }) { Icon(if (nowId in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favoritar", tint = Green) }
                }
                Slider(value = if (duration > 0) (pos.toFloat()/duration).coerceIn(0f,1f) else 0f, onValueChange = { if (duration > 0) controller?.seekTo((it*duration).toLong()) }, enabled = duration > 0)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(timestamp(pos)); Text(timestamp(duration)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { controller?.shuffleModeEnabled = !shuffle }) { Icon(Icons.Default.Shuffle, "Aleatório", tint = if (shuffle) Green else Color.White) }
                    IconButton(onClick = { controller?.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, "Anterior") }
                    FilledIconButton(onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } }, modifier = Modifier.size(72.dp)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproduzir ou pausar", Modifier.size(34.dp)) }
                    IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "Próxima") }
                    IconButton(onClick = { controller?.repeatMode = when (repeat) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } }) { Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repetir", tint = if (repeat != Player.REPEAT_MODE_OFF) Green else Color.White) }
                }
                playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
            }
        } else Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(14.dp))
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
            if(tab == 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !localSearch, onClick = { localSearch = false }, label = { Text("Online") })
                FilterChip(selected = localSearch, onClick = { localSearch = true }, label = { Text("No aparelho") })
            }
            if(tab == 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Músicas","Playlists","Histórico").forEachIndexed { index, label ->
                    FilterChip(selected = libraryPage == index, onClick = { libraryPage = index }, label = { Text(label) })
                }
            }
            if(tab == 0 && libraryPage != 0) {
                PersonalLibraryScreen(historyMode = libraryPage == 2, tracks = tracks, granted = granted,
                    canPlay = controller != null, onPermission = { request.launch(permission) },
                    onPlay = { queue, index -> controller?.let { p ->
                        if(queue.isNotEmpty()) { p.setMediaItems(queue.map { it.mediaItem() }, index.coerceIn(0,queue.lastIndex), 0L); p.prepare(); p.play() }
                    } })
            } else if (tab == 3 || tab == 4 || (tab == 1 && !localSearch)) {
                key(tab) {
                DiscoveryScreen(offline = tab == 4, canPlay = controller != null, searchOnly = tab == 1, onPlay = { item ->
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
                            Box(Modifier.size(48.dp).background(SurfaceDark, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = if (nowId == t.id.toString()) Green else Cyan) }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(t.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (nowId == t.id.toString()) Green else Color.White)
                                Text(t.artist + " · " + timestamp(t.duration), fontSize = 12.sp, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { toggle(t.id.toString()) }) { Icon(if (t.id.toString() in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favoritar ${t.title}", tint = if (t.id.toString() in favorites) Green else Color.LightGray) }
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
