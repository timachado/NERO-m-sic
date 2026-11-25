package br.com.timachado.neromusic

import android.net.Uri
import android.os.SystemClock
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val green = Color(0xFF39FF14)
private val cyan = Color(0xFF00E5FF)
private enum class CatalogKind(val label:String,val resource:String,val resultKey:String) {
    SONG("Músicas","recording","recordings"),
    ALBUM("Álbuns","release-group","release-groups"),
    ARTIST("Artistas","artist","artists")
}
private data class CatalogEntry(val id:String,val title:String,val credit:String,val kind:CatalogKind) {
    val coverUrl: String? get() = if(kind == CatalogKind.ALBUM) "https://coverartarchive.org/release-group/$id/front-250" else null
    val searchText: String get() = listOf(title,credit).filter { it.isNotBlank() }.joinToString(" ")
}
private val idPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
private object DevCatalog {
    private val lock = Mutex()
    private var lastRequest = 0L
    private val cache = LinkedHashMap<String,List<CatalogEntry>>()
    suspend fun search(query:String,kind:CatalogKind):List<CatalogEntry> = withContext(Dispatchers.IO) {
        check(BuildConfig.DEBUG)
        val term = query.trim().take(100)
        require(term.isNotBlank())
        lock.withLock {
            val key = kind.name+":"+term.lowercase()
            cache[key]?.let { return@withLock it }
            val remaining = 1100 - (SystemClock.elapsedRealtime()-lastRequest)
            if(remaining>0) delay(remaining)
            val phrase = term.replace("\\"," ").replace("\""," ")
            val expression = when(kind) {
                CatalogKind.SONG -> "recording:\"$phrase\" OR artist:\"$phrase\""
                CatalogKind.ALBUM -> "(releasegroup:\"$phrase\" OR artist:\"$phrase\") AND primarytype:album"
                CatalogKind.ARTIST -> "artist:\"$phrase\""
            }
            val uri = Uri.parse("https://musicbrainz.org/ws/2/"+kind.resource).buildUpon()
                .appendQueryParameter("query",expression).appendQueryParameter("fmt","json")
                .appendQueryParameter("limit","24").build()
            val connection = URL(uri.toString()).openConnection() as HttpURLConnection
            connection.connectTimeout=15000; connection.readTimeout=20000
            connection.setRequestProperty("User-Agent","NeroMusic/0.3-dev (https://github.com/timachado/NERO-m-sic)")
            connection.setRequestProperty("Accept","application/json")
            connection.instanceFollowRedirects=false
            lastRequest=SystemClock.elapsedRealtime()
            try {
                check(connection.responseCode==200)
                val root=JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val results=root.optJSONArray(kind.resultKey) ?: return@withLock emptyList<CatalogEntry>()
                val entries=(0 until results.length()).mapNotNull { i ->
                    val obj=results.optJSONObject(i) ?: return@mapNotNull null
                    val id=obj.optString("id")
                    val title=obj.optString(if(kind==CatalogKind.ARTIST)"name" else "title")
                    if(!idPattern.matches(id)||title.isBlank()) return@mapNotNull null
                    val creditArray=obj.optJSONArray("artist-credit")
                    val credit=if(kind==CatalogKind.ARTIST) "" else if(creditArray==null) "" else
                        (0 until creditArray.length()).joinToString("") { idx ->
                            val part=creditArray.optJSONObject(idx)
                            if(part==null) "" else part.optString("name")+part.optString("joinphrase")
                        }.trim()
                    CatalogEntry(id,title,credit,kind)
                }.distinctBy { it.id }
                if(cache.size>=32) cache.remove(cache.keys.first())
                cache[key]=entries
                entries
            } finally { connection.disconnect() }
        }
    }
}

@Composable
internal fun CatalogScreen(onBack:()->Unit) {
    val context=LocalContext.current
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CatalogKind.SONG) }
    var submitted by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf(emptyList<CatalogEntry>()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(submitted,kind,reload) {
        result=emptyList();message=null
        if(submitted.isBlank()) return@LaunchedEffect
        busy=true
        try { result=DevCatalog.search(submitted,kind) }
        catch(e:CancellationException){ throw e }
        catch(e:Exception){ message="Catálogo indisponível. Tente novamente." }
        finally { busy=false }
    }
    Column(Modifier.fillMaxWidth().padding(bottom=4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.Default.ArrowBack,"Voltar") }
            Text("Explorar catálogo",fontSize=23.sp,fontWeight=FontWeight.Bold)
        }
        Text("MusicBrainz · prévia de desenvolvimento não comercial",color=Color.LightGray,fontSize=12.sp)
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(top=8.dp),singleLine=true,placeholder={Text("Música, álbum ou artista")},leadingIcon={Icon(Icons.Default.Search,null)})
        Button(onClick={submitted=query.trim();reload++},enabled=query.isNotBlank(),modifier=Modifier.fillMaxWidth().padding(top=4.dp)){Text("Pesquisar")}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            CatalogKind.entries.forEach { k ->
                FilterChip(selected=kind==k,onClick={kind=k},label={Text(k.label)},modifier=Modifier.weight(1f))
            }
        }
        Text("Os resultados são metadados; ouvir abre o YouTube Music oficial.",color=Color.LightGray,fontSize=12.sp)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=10.dp)) {
            if(busy) item { CircularProgressIndicator() }
            message?.let { msg -> item { Text(msg,color=cyan) } }
            if(!busy && message==null && result.isEmpty()) item { Text(if(submitted.isBlank())"Busque um artista, álbum ou faixa." else "Nenhum resultado.",color=Color.LightGray) }
            if(kind==CatalogKind.ALBUM) {
                items(result.chunked(2)) { pair ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        pair.forEach { entry ->
                            Column(Modifier.weight(1f).background(Color(0xFF10171C),RoundedCornerShape(12.dp)).padding(8.dp)) {
                                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(9.dp)).background(Color(0xFF12313A)),contentAlignment=Alignment.Center) {
                                    Icon(Icons.Default.Album,null,tint=cyan,modifier=Modifier.size(42.dp))
                                    AsyncImage(model=entry.coverUrl,contentDescription="Capa de "+entry.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                                }
                                Text(entry.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold)
                                Text(entry.credit,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=Color.LightGray)
                                TextButton(onClick={if(!openYouTubeMusic(context,entry.searchText))message="Não foi possível abrir o YouTube Music."}) { Text("Ouvir",color=green) }
                            }
                        }
                        if(pair.size==1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                items(result,key={it.id}) { entry ->
                    Row(Modifier.fillMaxWidth().background(Color(0xFF10171C),RoundedCornerShape(12.dp)).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(if(entry.kind==CatalogKind.ARTIST) Icons.Default.Person else Icons.Default.MusicNote,null,tint=cyan,modifier=Modifier.size(32.dp))
                        Column(Modifier.weight(1f).padding(horizontal=8.dp)) {
                            Text(entry.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                            if(entry.credit.isNotBlank())Text(entry.credit,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=Color.LightGray)
                        }
                        IconButton(onClick={if(!openYouTubeMusic(context,entry.searchText))message="Não foi possível abrir o YouTube Music."}){Icon(Icons.Default.PlayArrow,"Buscar no YouTube Music",tint=green)}
                    }
                }
            }
        }
    }
}
