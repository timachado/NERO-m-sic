package br.com.timachado.neromusic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val neon = Color(0xFF39FF14)
private val aqua = Color(0xFF00E5FF)
private const val maxBytes = 50L * 1024 * 1024

internal data class OpenTrack(val id: Long, val title: String, val artist: String, val license: String,
    val source: String, val url: String, val mime: String, val size: Long) {
    fun json() = JSONObject().put("id",id).put("title",title).put("artist",artist).put("license",license)
        .put("source",source).put("url",url).put("mime",mime).put("size",size)
    fun item(uri: Uri) = MediaItem.Builder().setMediaId("commons:"+id).setUri(uri).setMimeType(mime)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist)
            .setExtras(android.os.Bundle().apply{putString("nero_open_audio",json().toString())}).build()).build()
}
internal fun openRefFromItem(item:MediaItem):OnlineRef? = runCatching {
    val raw=item.mediaMetadata.extras?.getString("nero_open_audio") ?: return null
    val audio=decodeOpenTrack(JSONObject(raw))
    if(!allowed(audio)||item.mediaId!="commons:"+audio.id)return null
    OnlineRef(item.mediaId,audio.title,audio.artist,openAudio=audio)
}.getOrNull()
internal fun decodeOpenTrack(o: JSONObject) = OpenTrack(o.getLong("id"),o.getString("title"),o.getString("artist"),
    o.getString("license"),o.getString("source"),o.getString("url"),o.getString("mime"),o.getLong("size"))
private fun storage(context: Context) = File(context.filesDir,"open-audio").apply { mkdirs() }
internal fun audioFile(context: Context,t: OpenTrack) = File(storage(context),t.id.toString()+when(t.mime) {
    "audio/mpeg" -> ".mp3"; "audio/flac","audio/x-flac" -> ".flac"; "audio/wav","audio/x-wav" -> ".wav"; else -> ".ogg"
})
private fun trusted(url: String, host: String): Boolean {
    val u = Uri.parse(url)
    return u.scheme == "https" && u.host == host
}
internal fun allowed(t: OpenTrack): Boolean {
    val license = t.license.trim()
    val open = license.equals("Public domain",true) || license.equals("CC0",true) ||
        license.equals("CC0 1.0",true) || Regex("CC BY(?:-SA)? [1-4]\\.[0-9]",RegexOption.IGNORE_CASE).matches(license)
    return open && t.id > 0 && t.size in 1..maxBytes && t.artist.isNotBlank() &&
        t.mime in setOf("audio/ogg","application/ogg","audio/mpeg","audio/flac","audio/x-flac","audio/wav","audio/x-wav") &&
        trusted(t.url,"upload.wikimedia.org") && trusted(t.source,"commons.wikimedia.org")
}
private fun request(url: String): HttpURLConnection {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 15000; c.readTimeout = 20000; c.instanceFollowRedirects = false
    c.setRequestProperty("User-Agent","NeroMusic/0.2 (https://github.com/timachado/NERO-m-sic)")
    return c
}
private fun plain(s:String) = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().trim()
internal suspend fun searchOpen(query: String): List<OpenTrack> = withContext(Dispatchers.IO) {
    val u = Uri.parse("https://commons.wikimedia.org/w/api.php").buildUpon()
        .appendQueryParameter("action","query").appendQueryParameter("format","json")
        .appendQueryParameter("formatversion","2").appendQueryParameter("generator","search")
        .appendQueryParameter("gsrnamespace","6").appendQueryParameter("gsrlimit","30")
        .appendQueryParameter("gsrsearch","filetype:audio music "+query.take(120))
        .appendQueryParameter("prop","imageinfo").appendQueryParameter("iiprop","url|mime|size|extmetadata")
        .appendQueryParameter("iiextmetadatafilter","LicenseShortName|Artist|ObjectName").build().toString()
    val c = request(u)
    try {
        check(c.responseCode == 200)
        val data = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        check(!data.has("error"))
        val pages = data.optJSONObject("query")?.optJSONArray("pages") ?: return@withContext emptyList()
        (0 until pages.length()).mapNotNull { index ->
            val p = pages.optJSONObject(index) ?: return@mapNotNull null
            val info = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@mapNotNull null
            val meta = info.optJSONObject("extmetadata") ?: return@mapNotNull null
            fun field(name: String) = plain(meta.optJSONObject(name)?.optString("value").orEmpty())
            val title = field("ObjectName").ifBlank { p.optString("title").removePrefix("File:").substringBeforeLast('.') }
            val track = OpenTrack(p.optLong("pageid"),title,field("Artist"),field("LicenseShortName"),
                info.optString("descriptionurl"),info.optString("url"),info.optString("mime"),info.optLong("size"))
            track.takeIf(::allowed)
        }
    } finally { c.disconnect() }
}
internal suspend fun downloaded(context: Context): List<OpenTrack> = withContext(Dispatchers.IO) {
    storage(context).listFiles()?.filter { it.extension == "json" }?.mapNotNull {
        runCatching { decodeOpenTrack(JSONObject(it.readText())) }.getOrNull()?.takeIf { t -> allowed(t) && audioFile(context,t).length() == t.size }
    }?.sortedBy { it.title.lowercase() } ?: emptyList()
}
private val downloadLock=Mutex()
internal suspend fun saveAudio(context: Context, t: OpenTrack, progress:(Float)->Unit={}):Unit = downloadLock.withLock { withContext(Dispatchers.IO) {
    check(allowed(t))
    val final = audioFile(context,t)
    val side = File(storage(context),t.id.toString()+".json")
    if (final.length() == t.size && side.isFile) {progress(1f);return@withContext}
    val partial = File(storage(context),t.id.toString()+".part")
    val meta = File(storage(context),t.id.toString()+".tmp")
    val networkGuard=DownloadNetworkGuard(context)
    networkGuard.check(force=true)
    val c = request(t.url)
    try {
        check(c.responseCode == 200)
        val actualMime = c.contentType?.substringBefore(';')?.trim()
        check(actualMime == t.mime || actualMime == "application/octet-stream" || (actualMime == "application/ogg" && t.mime == "audio/ogg"))
        c.inputStream.use { input -> partial.outputStream().use { output ->
            val buffer = ByteArray(32768); var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                networkGuard.check()
                val n = input.read(buffer); if (n < 0) break
                total += n; check(total <= t.size && total <= maxBytes); output.write(buffer,0,n)
                progress((total.toDouble()/t.size).toFloat().coerceIn(0f,0.99f))
            }
            check(total == t.size)
        } }
        networkGuard.check(force=true)
        meta.writeText(t.json().toString())
        check(partial.renameTo(final))
        check(meta.renameTo(side))
        progress(1f)
    } finally { c.disconnect(); partial.delete(); meta.delete() }
} }
internal suspend fun removeOpenDownload(context:Context,t:OpenTrack):Unit=downloadLock.withLock{withContext(Dispatchers.IO){
    require(allowed(t))
    val audio=audioFile(context,t)
    val meta=File(storage(context),t.id.toString()+".json")
    check(!audio.exists()||audio.delete())
    check(!meta.exists()||meta.delete())
}}
internal fun openBrowser(context:Context, uri:Uri): Boolean =
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,uri)); true }.getOrDefault(false)

internal fun openYouTubeMusic(context: Context, query: String): Boolean {
    val uri = Uri.parse("https://music.youtube.com/search").buildUpon()
        .appendQueryParameter("q", query.trim().ifBlank { "música" }).build()
    val appOpened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.youtube.music"))
        true
    }.getOrDefault(false)
    return appOpened || openBrowser(context, uri)
}

@Composable
internal fun DiscoveryScreen(offline: Boolean, canPlay: Boolean, searchOnly:Boolean = false, localTracks:List<Track> = emptyList(), favoriteIds:Set<String> = emptySet(), onlineFavorites:List<OnlineRef> = emptyList(), onRadio:(List<Track>)->Unit = {}, onSearch:(String)->Unit = {}, onSearchFocus:()->Unit={onSearch("")}, onNativeQueue:(List<MediaItem>,Int)->Unit={_,_->}, onPlay:(MediaItem)->Unit) {
    val context = LocalContext.current
    var showReleases by remember { mutableStateOf(false) }
    var showDatedReleases by remember { mutableStateOf(false) }
    var showAlbums by remember { mutableStateOf(false) }
    var albumGenre by remember { mutableStateOf("Todos") }
    var selectedAlbum by remember { mutableStateOf<NativeCollection?>(null) }
    androidx.activity.compose.BackHandler(enabled=(showReleases||showDatedReleases||showAlbums||selectedAlbum!=null)&&!offline){
        if(selectedAlbum!=null)selectedAlbum=null else if(showAlbums)showAlbums=false else if(showDatedReleases)showDatedReleases=false else showReleases=false
    }
    selectedAlbum?.let{album->
        key(album.url){NativeAlbumScreen(album,canPlay,onBack={selectedAlbum=null},onQueue=onNativeQueue)}
        return
    }
    if(showAlbums&&!offline){
        NativeAlbumsShelf(onlineFavorites,albumGenre,{albumGenre=it},{selectedAlbum=it},fullScreen=true,onBack={showAlbums=false})
        return
    }
    if(showDatedReleases&&!offline){
        RecentReleasesScreen(onBack={showDatedReleases=false},onSearch=onSearch)
        return
    }
    if(showReleases&&!offline){
        NativeReleasesScreen(onBack={showReleases=false},onAlbum={selectedAlbum=it})
        return
    }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf(emptyList<OpenTrack>()) }
    var saved by remember { mutableStateOf(emptyList<OpenTrack>()) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf<Long?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(offline, generation) { saved = downloaded(context) }
    LaunchedEffect(submitted,generation,offline) {
        if (offline || submitted == null) return@LaunchedEffect
        busy = true; notice = null
        try { tracks = searchOpen(submitted.orEmpty()) }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { tracks = emptyList(); notice = "Não foi possível consultar o catálogo. Tente novamente." }
        finally { busy = false }
    }

    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(if(offline) "Downloads" else if(searchOnly) "Buscar músicas" else "Descubra novos sons",fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text(if(offline) "Áudios autorizados salvos no NERO Music." else "Explore sua biblioteca e descubra músicas", color=Color.LightGray,fontSize=13.sp)
        }
        if(!offline) {
            item {
                OutlinedButton(onClick=onSearchFocus,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp)){
                    Icon(Icons.Default.Search,null)
                    Spacer(Modifier.width(8.dp))
                    Text("Música, artista ou gênero",modifier=Modifier.weight(1f))
                }
            }
            if (!searchOnly) item {
                PremiumDiscovery(onSearch=onSearch)
                NeroRadarShelf(onlineFavorites,onSearch)
                NeroTogetherShelf(canPlay,onNativeQueue)
                DigitalChartsShelf(onSearch=onSearch,canPlay=canPlay,onQueue=onNativeQueue)
                NeroReplayShelf(onSearch)
                OnlineDiscoveryShelf(onlineFavorites,canPlay,onNativeQueue,onSearch=onSearch,onAlbum={selectedAlbum=it})
                PersonalRecommendations(localTracks, favoriteIds, canPlay, onPlay)
                RadioShelf(localTracks,favoriteIds,canPlay,onRadio)
            }
            if (NativeMusicCatalog.enabled && !searchOnly) item {
                Card(onClick={showDatedReleases=true},modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.NewReleases,null,tint=aqua,modifier=Modifier.size(32.dp))
                        Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                            Text("Descobrir álbuns",fontWeight=FontWeight.Bold,fontSize=19.sp)
                            Text("Explore sugestões por gênero",fontSize=12.sp,color=Color.LightGray)
                        }
                        Icon(Icons.Default.KeyboardArrowRight,"Explorar lançamentos")
                    }
                }
                NativeReleaseShelf(onOpen={showReleases=true},onAlbum={selectedAlbum=it})
                NativeAlbumsShelf(onlineFavorites,albumGenre,{albumGenre=it},{selectedAlbum=it},onOpen={showAlbums=true})
            }
            item { PremiumGenres(onSearch=onSearch) }

        }
        if(busy && !offline) item { CircularProgressIndicator() }
        notice?.let { msg -> item { Text(msg,color=aqua) } }
        val list=if(offline)saved else tracks
        if(list.isEmpty() && !busy) item {
            Text(if(offline) "Nenhum áudio salvo." else if(submitted==null) "Explore um gênero ou pesquise no NERO." else "Nenhum áudio compatível e licenciado encontrado.",color=Color.LightGray)
        }
        items(list,key={it.id}) { track ->
            val isSaved = saved.any { it.id == track.id }
            Column(Modifier.fillMaxWidth().background(Color(0xFF10171C),RoundedCornerShape(14.dp)).padding(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote,null,tint=aqua,modifier=Modifier.size(32.dp))
                    Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
                        Text(track.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                        Text(track.artist,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=Color.LightGray)
                        Text(track.license,fontSize=11.sp,color=neon)
                    }
                    IconButton(enabled=canPlay,onClick={
                        val uri=if(isSaved)Uri.fromFile(audioFile(context,track)) else Uri.parse(track.url)
                        onPlay(track.item(uri))
                    }){Icon(Icons.Default.PlayArrow,"Reproduzir",tint=neon)}
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={if(!openBrowser(context,Uri.parse(track.source)))notice="Não foi possível abrir a fonte."}){Text("Fonte e licença")}
                    if(isSaved)Text("Salvo",color=neon,fontSize=12.sp)
                    else TextButton(enabled=saving==null,onClick={
                        saving=track.id;notice=null
                        scope.launch {
                            try { saveAudio(context,track);saved=downloaded(context);notice="Áudio salvo para ouvir offline." }
                            catch(e:CancellationException){throw e}
                            catch(e:Exception){notice="Falha no download. Tente novamente."}
                            finally{saving=null}
                        }
                    }){Text(if(saving==track.id)"Salvando…" else "Baixar")}
                }
            }
        }
    }
}
