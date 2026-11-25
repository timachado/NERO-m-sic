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
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val neon = Color(0xFF39FF14)
private val aqua = Color(0xFF00E5FF)
private const val maxBytes = 50L * 1024 * 1024

private data class OpenTrack(val id: Long, val title: String, val artist: String, val license: String,
    val source: String, val url: String, val mime: String, val size: Long) {
    fun json() = JSONObject().put("id",id).put("title",title).put("artist",artist).put("license",license)
        .put("source",source).put("url",url).put("mime",mime).put("size",size)
    fun item(uri: Uri) = MediaItem.Builder().setMediaId("commons:"+id).setUri(uri).setMimeType(mime)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build()).build()
}
private fun decode(o: JSONObject) = OpenTrack(o.getLong("id"),o.getString("title"),o.getString("artist"),
    o.getString("license"),o.getString("source"),o.getString("url"),o.getString("mime"),o.getLong("size"))
private fun storage(context: Context) = File(context.filesDir,"open-audio").apply { mkdirs() }
private fun audioFile(context: Context,t: OpenTrack) = File(storage(context),t.id.toString()+when(t.mime) {
    "audio/mpeg" -> ".mp3"; "audio/flac","audio/x-flac" -> ".flac"; "audio/wav","audio/x-wav" -> ".wav"; else -> ".ogg"
})
private fun trusted(url: String, host: String): Boolean {
    val u = Uri.parse(url)
    return u.scheme == "https" && u.host == host
}
private fun allowed(t: OpenTrack): Boolean {
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
private suspend fun searchOpen(query: String): List<OpenTrack> = withContext(Dispatchers.IO) {
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
private suspend fun downloaded(context: Context): List<OpenTrack> = withContext(Dispatchers.IO) {
    storage(context).listFiles()?.filter { it.extension == "json" }?.mapNotNull {
        runCatching { decode(JSONObject(it.readText())) }.getOrNull()?.takeIf { t -> allowed(t) && audioFile(context,t).length() == t.size }
    }?.sortedBy { it.title.lowercase() } ?: emptyList()
}
private suspend fun saveAudio(context: Context, t: OpenTrack) = withContext(Dispatchers.IO) {
    check(allowed(t))
    val final = audioFile(context,t)
    val side = File(storage(context),t.id.toString()+".json")
    if (final.length() == t.size && side.isFile) return@withContext
    val partial = File(storage(context),t.id.toString()+".part")
    val meta = File(storage(context),t.id.toString()+".tmp")
    val c = request(t.url)
    try {
        check(c.responseCode == 200)
        val actualMime = c.contentType?.substringBefore(';')?.trim()
        check(actualMime == t.mime || actualMime == "application/octet-stream" || (actualMime == "application/ogg" && t.mime == "audio/ogg"))
        c.inputStream.use { input -> partial.outputStream().use { output ->
            val buffer = ByteArray(32768); var total = 0L
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                total += n; check(total <= t.size && total <= maxBytes); output.write(buffer,0,n)
            }
            check(total == t.size)
        } }
        meta.writeText(t.json().toString())
        check(partial.renameTo(final))
        check(meta.renameTo(side))
    } finally { c.disconnect(); partial.delete(); meta.delete() }
}
private fun openBrowser(context:Context, uri:Uri): Boolean =
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
internal fun DiscoveryScreen(offline: Boolean, canPlay: Boolean, searchOnly:Boolean = false, localTracks:List<Track> = emptyList(), favoriteIds:Set<String> = emptySet(), onRadio:(List<Track>)->Unit = {}, onPlay:(MediaItem)->Unit) {
    val context = LocalContext.current
    var showCatalog by remember { mutableStateOf(false) }
    var catalogQuery by remember { mutableStateOf("") }
    var catalogKind by remember { mutableStateOf(CatalogKind.SONG) }
    androidx.activity.compose.BackHandler(enabled = showCatalog && !offline) { showCatalog = false }
    if (showCatalog && !offline) {
        CatalogScreen(onBack = { showCatalog = false }, initialQuery = catalogQuery, initialKind = catalogKind)
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
    val genres = listOf("Rock" to "rock","Pop" to "pop","Jazz" to "jazz","Clássica" to "classical",
        "Gospel" to "gospel","Samba" to "samba","Sertanejo" to "sertanejo","Eletrônica" to "electronic")
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(if(offline) "Downloads" else if(searchOnly) "Buscar músicas" else "Descubra novos sons",fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text(if(offline) "Áudios autorizados salvos no NERO Music." else "YouTube Music e catálogo aberto", color=Color.LightGray,fontSize=13.sp)
        }
        if(!offline) {
            item {
                OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Música, artista ou gênero")})
                Button(onClick={
                    if(!openYouTubeMusic(context,query)) notice="Não foi possível abrir o YouTube Music."
                },modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Search,null)
                    Spacer(Modifier.width(8.dp))
                    Text("Buscar no YouTube Music")
                }
                OutlinedButton(onClick={submitted=query.trim(); generation++},modifier=Modifier.fillMaxWidth()) {
                    Text("Buscar áudios com licença aberta")
                }
                Text("A busca no YouTube Music abre o aplicativo oficial ou o navegador. Downloads do YouTube Music ficam no serviço oficial; o NERO salva os áudios autorizados do catálogo aberto.",fontSize=12.sp,color=Color.LightGray)
            }
            if (BuildConfig.DEBUG) item {
                OutlinedButton(onClick = { catalogQuery = query.trim(); catalogKind = CatalogKind.SONG; showCatalog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LibraryMusic, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Explorar músicas, álbuns e artistas")
                }
            }
            if (!searchOnly) item {
                PersonalRecommendations(localTracks, favoriteIds, canPlay, onPlay)
                RadioShelf(localTracks,favoriteIds,canPlay,onRadio)
            }
            if (BuildConfig.DEBUG && !searchOnly) item {
                CatalogHomeShelf(onExplore = { term, kind -> catalogQuery = term; catalogKind = kind; showCatalog = true })
            }
            item { Text("Estilos e gêneros",fontSize=19.sp,fontWeight=FontWeight.Bold) }
            items(genres.chunked(2)) { pair ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (name,term) ->
                        Button(onClick={query=name;submitted=term;generation++},modifier=Modifier.weight(1f).height(56.dp),shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF102B23),contentColor=aqua)){
                            Icon(Icons.Default.MusicNote,null,modifier=Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(name)
                        }
                    }
                }
            }
        }
        if(busy && !offline) item { CircularProgressIndicator() }
        notice?.let { msg -> item { Text(msg,color=aqua) } }
        val list=if(offline)saved else tracks
        if(list.isEmpty() && !busy) item {
            Text(if(offline) "Nenhum áudio salvo." else if(submitted==null) "Escolha um gênero ou faça uma busca." else "Nenhum áudio compatível e licenciado encontrado.",color=Color.LightGray)
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
