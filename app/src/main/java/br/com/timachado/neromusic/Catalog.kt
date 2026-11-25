package br.com.timachado.neromusic

import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
internal enum class CatalogKind(val label:String,val resource:String,val resultKey:String) {
    SONG("Músicas","recording","recordings"),
    ALBUM("Álbuns","release-group","release-groups"),
    ARTIST("Artistas","artist","artists")
}
internal data class CatalogEntry(val id:String,val title:String,val credit:String,val kind:CatalogKind,val releaseDate:String="",val artworkReleaseId:String?=null) {
    val coverUrl: String? get() = if(kind == CatalogKind.ALBUM) "https://coverartarchive.org/release-group/$id/front-250" else artworkReleaseId?.let{"https://coverartarchive.org/release/$it/front-250"}
    val searchText: String get() = listOf(title,credit).filter { it.isNotBlank() }.joinToString(" ")
}
private val idPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
internal object DevCatalog {
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
                    val releaseId=if(kind==CatalogKind.SONG)obj.optJSONArray("releases")?.optJSONObject(0)?.optString("id")?.takeIf{idPattern.matches(it)} else null
                    CatalogEntry(id,title,credit,kind,artworkReleaseId=releaseId)
                }.distinctBy { it.id }
                if(cache.size>=32) cache.remove(cache.keys.first())
                cache[key]=entries
                entries
            } finally { connection.disconnect() }
        }
    }
    private suspend fun fetchDetails(path:String, params:Map<String,String>):JSONObject = withContext(Dispatchers.IO) {
        check(BuildConfig.DEBUG)
        lock.withLock {
            val remaining=1100-(SystemClock.elapsedRealtime()-lastRequest)
            if(remaining>0)delay(remaining)
            val u=Uri.parse("https://musicbrainz.org/ws/2/"+path).buildUpon().appendQueryParameter("fmt","json")
            params.forEach{(key,value)->u.appendQueryParameter(key,value)}
            val connection=URL(u.build().toString()).openConnection() as HttpURLConnection
            connection.connectTimeout=15000;connection.readTimeout=20000
            connection.instanceFollowRedirects=false
            connection.setRequestProperty("User-Agent","NeroMusic/0.8-dev (https://github.com/timachado/NERO-m-sic)")
            connection.setRequestProperty("Accept","application/json")
            lastRequest=SystemClock.elapsedRealtime()
            try {
                check(connection.responseCode==200)
                JSONObject(connection.inputStream.bufferedReader().use{it.readText()})
            }finally{connection.disconnect()}
        }
    }

    internal data class ReleaseSnapshot(val entries:List<CatalogEntry>,val fetchedAt:Long,val day:java.time.LocalDate)
    private val releaseLock=Mutex()
    private val releaseCache=LinkedHashMap<Pair<Int,String?>,ReleaseSnapshot>()
    suspend fun releaseSnapshot(days:Int,genre:String?,force:Boolean=false):ReleaseSnapshot=releaseLock.withLock{
        val key=days to genre
        val previous=releaseCache[key]
        val today=java.time.LocalDate.now()
        if(!force&&previous!=null&&previous.day==today&&System.currentTimeMillis()-previous.fetchedAt in 0L..900000L)return@withLock previous
        val fresh=ReleaseSnapshot(releases(days,genre),System.currentTimeMillis(),today)
        if(releaseCache.size>=24)releaseCache.remove(releaseCache.keys.first())
        releaseCache[key]=fresh
        fresh
    }
    private suspend fun releases(days:Int,genre:String?):List<CatalogEntry>{
        require(days in setOf(7,30,90))
        require(genre==null || genre in setOf("pop","rock","jazz","gospel","samba","sertanejo","pagode","electronic","hip hop","mpb"))
        val today=java.time.LocalDate.now()
        val from=today.minusDays((days-1).toLong())
        val tags=genre?.let{" AND tag:\"$it\""} ?: ""
        val query="firstreleasedate:[$from TO $today] AND (primarytype:album OR primarytype:single OR primarytype:ep) AND status:official"+tags
        val data=fetchDetails("release-group",mapOf("query" to query,"limit" to "50"))
        val rows=data.optJSONArray("release-groups")?:return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            val o=rows.optJSONObject(i)?:return@mapNotNull null
            val id=o.optString("id");val title=o.optString("title");val date=o.optString("first-release-date")
            val parsed=runCatching{java.time.LocalDate.parse(date)}.getOrNull()?:return@mapNotNull null
            if(!idPattern.matches(id)||title.isBlank()||parsed.isBefore(from)||parsed.isAfter(today))return@mapNotNull null
            val arr=o.optJSONArray("artist-credit")
            val artist=if(arr==null)"" else (0 until arr.length()).joinToString(""){j->
                arr.optJSONObject(j)?.let{it.optString("name")+it.optString("joinphrase")} ?: ""
            }.trim()
            CatalogEntry(id,title,artist,CatalogKind.ALBUM,date)
        }.distinctBy{it.id}.sortedWith(compareByDescending<CatalogEntry>{it.releaseDate}.thenBy{it.title})
    }
    suspend fun discography(id:String):List<CatalogEntry> {
        require(idPattern.matches(id))
        val data=fetchDetails("release-group",mapOf("artist" to id,"type" to "album|ep|single","inc" to "artist-credits","limit" to "50"))
        val list=data.optJSONArray("release-groups") ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val row=list.optJSONObject(index)?:return@mapNotNull null
            val rid=row.optString("id");val title=row.optString("title")
            if(!idPattern.matches(rid)||title.isBlank())return@mapNotNull null
            val credits=row.optJSONArray("artist-credit")
            val artist=if(credits==null)"" else (0 until credits.length()).joinToString(""){i->
                credits.optJSONObject(i)?.let{it.optString("name")+it.optString("joinphrase")} ?: ""
            }
            CatalogEntry(rid,title,artist,CatalogKind.ALBUM)
        }.distinctBy{it.id}
    }
    suspend fun editions(id:String):List<AlbumEdition> {
        require(idPattern.matches(id))
        val data=fetchDetails("release",mapOf("release-group" to id,"status" to "official","limit" to "25"))
        val list=data.optJSONArray("releases")?:return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val o=list.optJSONObject(i)?:return@mapNotNull null
            val rid=o.optString("id")
            if(!idPattern.matches(rid))return@mapNotNull null
            AlbumEdition(rid,o.optString("title"),o.optString("date"),o.optString("country"),o.optString("disambiguation"))
        }.distinctBy{it.id}.sortedWith(compareBy<AlbumEdition>{it.date.ifBlank{"9999"}}.thenBy{it.title})
    }
    suspend fun albumTracks(id:String):List<AlbumSong> {
        require(idPattern.matches(id))
        val data=fetchDetails("release/"+id,mapOf("inc" to "recordings+artist-credits"))
        val media=data.optJSONArray("media")?:return emptyList()
        val result=mutableListOf<AlbumSong>()
        for(i in 0 until media.length()){
            val disc=media.optJSONObject(i)?:continue
            val tracks=disc.optJSONArray("tracks")?:continue
            for(j in 0 until tracks.length()){
                val track=tracks.optJSONObject(j)?:continue
                val recording=track.optJSONObject("recording")
                val title=track.optString("title").ifBlank{recording?.optString("title").orEmpty()}
                if(title.isBlank())continue
                val recordingId=recording?.optString("id")?.takeIf{idPattern.matches(it)}
                val credits=track.optJSONArray("artist-credit")?:recording?.optJSONArray("artist-credit")
                val artist=if(credits==null)"" else (0 until credits.length()).joinToString(""){k->
                    credits.optJSONObject(k)?.let{it.optString("name")+it.optString("joinphrase")} ?: ""
                }.trim()
                result.add(AlbumSong(i+1,track.optString("number").ifBlank{(j+1).toString()},title,track.optLong("length",recording?.optLong("length",0L)?:0L),recordingId,artist))
            }
        }
        return result
    }

}

@Composable
internal fun CatalogScreen(onBack:()->Unit, initialQuery:String = "", initialKind:CatalogKind = CatalogKind.SONG) {
    val context=LocalContext.current
    var query by remember { mutableStateOf(initialQuery) }
    var kind by remember { mutableStateOf(initialKind) }
    var submitted by remember { mutableStateOf(initialQuery) }
    var reload by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf(emptyList<CatalogEntry>()) }
    var busy by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<List<CatalogEntry>>(emptyList()) }
    val currentDetail=details.lastOrNull()
    androidx.activity.compose.BackHandler(currentDetail!=null){details=details.dropLast(1)}
    if(currentDetail!=null){
        key(currentDetail.id){CatalogDetailsScreen(currentDetail,onBack={details=details.dropLast(1)},onAlbum={details=details+it})}
        return
    }
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
                                TextButton(onClick={details=listOf(entry)}) { Text("Ver álbum",color=green) }
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
                        IconButton(onClick={if(entry.kind==CatalogKind.ARTIST) details=listOf(entry) else if(!openYouTubeMusic(context,entry.searchText))message="Não foi possível abrir o YouTube Music."}){Icon(if(entry.kind==CatalogKind.ARTIST)Icons.Default.KeyboardArrowRight else Icons.Default.PlayArrow,if(entry.kind==CatalogKind.ARTIST)"Ver artista" else "Buscar no YouTube Music",tint=green)}
                    }
                }
            }
        }
    }
}

@Composable
internal fun CatalogHomeShelf(onExplore:(String,CatalogKind)->Unit) {
    val context=LocalContext.current
    val picks=listOf("Gilberto Gil","Queen","Marisa Monte","Daft Punk")
    var selected by remember { mutableStateOf(picks.first()) }
    var entries by remember { mutableStateOf(emptyList<CatalogEntry>()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(selected,reload) {
        entries=emptyList(); loading=true; failed=false
        try { entries=DevCatalog.search(selected,CatalogKind.ALBUM).take(10) }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { failed=true }
        finally { loading=false }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Explore álbuns",fontWeight=FontWeight.Bold,fontSize=20.sp,modifier=Modifier.weight(1f))
            TextButton(onClick={onExplore(selected,CatalogKind.ALBUM)}) { Text("Ver mais",color=green) }
        }
        Text("Seleção editorial · prévia MusicBrainz",fontSize=12.sp,color=Color.LightGray)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            items(picks) { pick -> FilterChip(selected=pick==selected,onClick={selected=pick},label={Text(pick)}) }
        }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if(failed) {
            Text("Não foi possível carregar os álbuns.",color=Color.LightGray)
            TextButton(onClick={reload++}) { Text("Tentar novamente") }
        } else if(entries.isEmpty()) Text("Nenhum álbum encontrado.",color=Color.LightGray)
        else LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            items(entries,key={it.id}) { entry ->
                Column(Modifier.width(145.dp).background(Color(0xFF10171C),RoundedCornerShape(12.dp)).padding(8.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF12313A)),contentAlignment=Alignment.Center) {
                        Icon(Icons.Default.Album,null,tint=cyan,modifier=Modifier.size(40.dp))
                        AsyncImage(model=entry.coverUrl,contentDescription="Capa de "+entry.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                    }
                    Text(entry.title,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(entry.credit,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=Color.LightGray)
                    TextButton(onClick={openYouTubeMusic(context,entry.searchText)}) { Text("Ouvir",color=green) }
                }
            }
        }
    }
}
