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



internal enum class CatalogKind(val label:String,val resource:String,val resultKey:String) {
    SONG("Músicas","recording","recordings"),
    ALBUM("Álbuns","release-group","release-groups"),
    ARTIST("Artistas","artist","artists")
}
internal data class CatalogEntry(val id:String,val title:String,val credit:String,val kind:CatalogKind,val releaseDate:String="",val artworkReleaseId:String?=null) {
    val coverUrl: String? get() = artworkReleaseId
    val searchText: String get() = listOf(title,credit).filter { it.isNotBlank() }.joinToString(" ")
}
// Compatibility model for existing screens. All musical results use NativeMusicCatalog.
internal object DevCatalog {
    private val lock=Mutex()
    private val entries=LinkedHashMap<String,NativeCollection>()
    private fun collectionEntry(row:NativeCollection):CatalogEntry {
        val id=if(row.kind==NativeCollectionKind.ALBUM) "album:"+(savedAlbumKey(row.url)?:row.url) else "artist:"+row.url
        return CatalogEntry(id,row.title,row.credit,if(row.kind==NativeCollectionKind.ALBUM)CatalogKind.ALBUM else CatalogKind.ARTIST,artworkReleaseId=row.artwork)
    }
    private suspend fun mapped(rows:List<NativeCollection>):List<CatalogEntry> = lock.withLock{
        rows.map{row->collectionEntry(row).also{entries[it.id]=row}}.distinctBy{it.id}.also{
            while(entries.size>256)entries.remove(entries.keys.first())
        }
    }
    suspend fun search(query:String,kind:CatalogKind):List<CatalogEntry> {
        val term=query.trim().take(100)
        require(term.length>=3)
        return when(kind){
            CatalogKind.SONG->NativeMusicCatalog.search(term).map{CatalogEntry("youtube:"+it.id,it.title,it.artist,kind,artworkReleaseId=it.thumbnail)}
            CatalogKind.ALBUM->mapped(NativeMusicCatalog.collections(term,NativeCollectionKind.ALBUM))
            CatalogKind.ARTIST->mapped(NativeMusicCatalog.collections(term,NativeCollectionKind.ARTIST))
        }
    }
    internal data class ReleaseSnapshot(val entries:List<CatalogEntry>,val fetchedAt:Long,val day:java.time.LocalDate)
    private val releaseLock=Mutex()
    private val releaseCache=LinkedHashMap<Pair<String?,String?>,ReleaseSnapshot>()
    // No release-date field is available from this source; these are discovery candidates, not verified recent releases.
    suspend fun releaseSnapshot(days:Int,genre:String?,force:Boolean=false,artist:String?=null):ReleaseSnapshot=releaseLock.withLock{
        require(days in setOf(7,30,90))
        val key=genre to artist
        val today=java.time.LocalDate.now()
        val previous=releaseCache[key]
        if(!force&&previous!=null&&previous.day==today&&System.currentTimeMillis()-previous.fetchedAt in 0L..900000L)return@withLock previous
        val rows=if(artist!=null){
            val name=genreKey(artist)
            NativeMusicCatalog.collections(artist,NativeCollectionKind.ALBUM).filter{genreKey(it.credit)==name||artistCreditParts(it.credit).any{part->genreKey(part)==name}}
        }else{
            val plans=if(genre==null)albumExploreGenres(today.toEpochDay(),0,emptyList()).take(4)
                else listOfNotNull(genreForQuery(genre)?:neroGenres.firstOrNull{it.releaseTag==genre})
            val groups=mutableListOf<List<NativeCollection>>()
            for(plan in plans){
                val seed=albumExploreSeeds(plan,today.toEpochDay(),0,1).firstOrNull()?:continue
                groups+=genreCollections(plan,NativeMusicCatalog.collections(seed,NativeCollectionKind.ALBUM),NativeCollectionKind.ALBUM)
            }
            diverseAlbumRows(groups)
        }
        val fresh=ReleaseSnapshot(mapped(rows.filter{savedAlbumKey(it.url)!=null}).take(30),System.currentTimeMillis(),today)
        if(releaseCache.size>=24)releaseCache.remove(releaseCache.keys.first())
        releaseCache[key]=fresh
        fresh
    }
    suspend fun discography(id:String):List<CatalogEntry>{
        val row=lock.withLock{entries[id]}?:return emptyList()
        if(row.kind!=NativeCollectionKind.ARTIST)return emptyList()
        return mapped(NativeMusicCatalog.collections(row.title,NativeCollectionKind.ALBUM).filter{genreKey(it.credit)==genreKey(row.title)})
    }
    suspend fun editions(id:String):List<AlbumEdition>{
        val row=lock.withLock{entries[id]}?:return emptyList()
        return if(row.kind==NativeCollectionKind.ALBUM)listOf(AlbumEdition(id,row.title,"","","")) else emptyList()
    }
    suspend fun albumTracks(id:String):List<AlbumSong>{
        val row=lock.withLock{entries[id]}?:return emptyList()
        return NativeMusicCatalog.albumTracks(row).mapIndexed{index,hit->AlbumSong(1,(index+1).toString(),hit.title,hit.duration*1000,"youtube:"+hit.id,hit.artist)}
    }
}

@Composable
internal fun CatalogScreen(onBack:()->Unit, initialQuery:String = "", initialKind:CatalogKind = CatalogKind.SONG,onSearch:(String)->Unit) {
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
        key(currentDetail.id){CatalogDetailsScreen(currentDetail,onBack={details=details.dropLast(1)},onAlbum={details=details+it},onSearch=onSearch)}
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
        Text("Catálogo integrado NERO Music",color=Color.LightGray,fontSize=12.sp)
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(top=8.dp),singleLine=true,placeholder={Text("Música, álbum ou artista")},leadingIcon={Icon(Icons.Default.Search,null)})
        Button(onClick={submitted=query.trim();reload++},enabled=query.isNotBlank(),modifier=Modifier.fillMaxWidth().padding(top=4.dp)){Text("Pesquisar")}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            CatalogKind.entries.forEach { k ->
                FilterChip(selected=kind==k,onClick={kind=k},label={Text(k.label)},modifier=Modifier.weight(1f))
            }
        }
        Text("Explore os resultados no NERO Music.",color=Color.LightGray,fontSize=12.sp)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=10.dp)) {
            if(busy) item { CircularProgressIndicator() }
            message?.let { msg -> item { Text(msg,color=MaterialTheme.colorScheme.secondary) } }
            if(!busy && message==null && result.isEmpty()) item { Text(if(submitted.isBlank())"Busque um artista, álbum ou faixa." else "Nenhum resultado.",color=Color.LightGray) }
            if(kind==CatalogKind.ALBUM) {
                items(result.chunked(2)) { pair ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        pair.forEach { entry ->
                            Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.surface ,RoundedCornerShape(12.dp)).padding(8.dp)) {
                                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant ),contentAlignment=Alignment.Center) {
                                    Icon(Icons.Default.Album,null,tint=MaterialTheme.colorScheme.secondary,modifier=Modifier.size(42.dp))
                                    AsyncImage(model=entry.coverUrl,contentDescription="Capa de "+entry.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                                }
                                Text(entry.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold)
                                Text(entry.credit,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=Color.LightGray)
                                TextButton(onClick={details=listOf(entry)}) { Text("Ver álbum",color=MaterialTheme.colorScheme.primary) }
                            }
                        }
                        if(pair.size==1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                items(result,key={it.id}) { entry ->
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface ,RoundedCornerShape(12.dp)).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(if(entry.kind==CatalogKind.ARTIST) Icons.Default.Person else Icons.Default.MusicNote,null,tint=MaterialTheme.colorScheme.secondary,modifier=Modifier.size(32.dp))
                        Column(Modifier.weight(1f).padding(horizontal=8.dp)) {
                            Text(entry.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                            if(entry.credit.isNotBlank())Text(entry.credit,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=Color.LightGray)
                        }
                        IconButton(onClick={if(entry.kind==CatalogKind.ARTIST) details=listOf(entry) else onSearch(entry.searchText)}){Icon(if(entry.kind==CatalogKind.ARTIST)Icons.Default.KeyboardArrowRight else Icons.Default.PlayArrow,if(entry.kind==CatalogKind.ARTIST)"Ver artista" else "Buscar no NERO",tint=MaterialTheme.colorScheme.primary)}
                    }
                }
            }
        }
    }
}

@Composable
internal fun CatalogHomeShelf(onExplore:(String,CatalogKind)->Unit,onSearch:(String)->Unit) {
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
            TextButton(onClick={onExplore(selected,CatalogKind.ALBUM)}) { Text("Ver mais",color=MaterialTheme.colorScheme.primary) }
        }
        Text("Seleção de álbuns · NERO Music",fontSize=12.sp,color=Color.LightGray)
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
                Column(Modifier.width(145.dp).background(MaterialTheme.colorScheme.surface ,RoundedCornerShape(12.dp)).padding(8.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant ),contentAlignment=Alignment.Center) {
                        Icon(Icons.Default.Album,null,tint=MaterialTheme.colorScheme.secondary,modifier=Modifier.size(40.dp))
                        AsyncImage(model=entry.coverUrl,contentDescription="Capa de "+entry.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                    }
                    Text(entry.title,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(entry.credit,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=Color.LightGray)
                    TextButton(onClick={onSearch(entry.searchText)}) { Text("Encontrar faixas",color=MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}
