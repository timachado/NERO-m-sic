package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

// General exploration remains diverse even for accounts with a single favorite genre.
internal fun albumExploreGenres(day:Long,revision:Int,tastes:List<String>):List<NeroGenre>{
    val broad=listOf("Sertanejo","Funk","Pagode","Gospel","Forró","Rock","Pop internacional","Hip-hop","MPB","Eletrônica","Reggae","Samba","Arrocha","Trap","Piseiro","Jazz")
    val offset=Math.floorMod(day+revision.toLong(),broad.size.toLong()).toInt()
    val rotated=(broad.drop(offset)+broad.take(offset)).mapNotNull(::genreForQuery)
    val scores=neroGenres.map{genre->genre to tastes.count{genre.matchesArtist(it)}}.filter{it.second>0}.sortedByDescending{it.second}
    val personal=scores.map{it.first}.take(2)
    return (rotated.take(4)+personal+rotated).distinctBy{it.label}.take(6)
}
internal fun albumExploreSeeds(genre:NeroGenre,day:Long,revision:Int,count:Int):List<String>{
    val artists=genre.artists
    if(artists.isEmpty())return emptyList()
    val offset=Math.floorMod(day+revision.toLong(),artists.size.toLong()).toInt()
    return (artists.drop(offset)+artists.take(offset)).take(count)
}
internal fun diverseAlbumRows(groups:List<List<NativeCollection>>,limit:Int=24):List<NativeCollection>{
    val result=mutableListOf<NativeCollection>()
    val ids=mutableSetOf<String>()
    val names=mutableSetOf<String>()
    val artistCounts=mutableMapOf<String,Int>()
    val maxDepth=groups.maxOfOrNull{it.size}?:0
    for(index in 0 until maxDepth){
        for(group in groups){
            val row=group.getOrNull(index)?:continue
            if(row.kind!=NativeCollectionKind.ALBUM||row.title.isBlank()||row.credit.isBlank())continue
            val id=savedAlbumKey(row.url)?:continue
            val artist=genreKey(row.credit)
            val name=artist+"|"+genreKey(row.title)
            if(artist.isBlank()||id in ids||name in names||(artistCounts[artist]?:0)>=3)continue
            ids+=id;names+=name;artistCounts[artist]=(artistCounts[artist]?:0)+1
            result+=row
            if(result.size>=limit)return result
        }
    }
    return result
}
private data class AlbumExploreSnapshot(val rows:List<NativeCollection>,val at:Long)
private object AlbumExploreCache{
    private val lock=Mutex()
    private val cache=LinkedHashMap<String,AlbumExploreSnapshot>()
    suspend fun query(artist:String,day:Long,force:Boolean):List<NativeCollection> = lock.withLock{
        val key=day.toString()+"|"+artist
        val now=System.currentTimeMillis()
        cache[key]?.let{if(!force&&now-it.at in 0L..1800000L)return@withLock it.rows}
        val rows=NativeMusicCatalog.collections(artist,NativeCollectionKind.ALBUM)
        cache[key]=AlbumExploreSnapshot(rows,now)
        while(cache.size>80)cache.remove(cache.keys.first())
        rows
    }
}

@Composable
internal fun NativeAlbumsShelf(
    favorites:List<OnlineRef>,genre:String,onGenre:(String)->Unit,onAlbum:(NativeCollection)->Unit,
    fullScreen:Boolean=false,onOpen:()->Unit={},onBack:()->Unit={}
){
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    if(!ready)return
    val followed=rememberFollowedArtists()
    val changes by NeroAccountSync.changes.collectAsState()
    val recent=remember(uid,changes){history(context).map{it.artist}}
    val tastes=remember(uid,followed,favorites,recent){
        rankNeroMixArtists(followed,favorites.map{it.artist},recent,emptyList(),limit=12)
    }
    var day by remember{mutableLongStateOf(LocalDate.now().toEpochDay())}
    LaunchedEffect(Unit){while(true){day=LocalDate.now().toEpochDay();delay(60000L)}}
    var revision by remember(uid,genre){mutableIntStateOf(0)}
    var rows by remember(uid,genre){mutableStateOf(emptyList<NativeCollection>())}
    var busy by remember(uid,genre){mutableStateOf(true)}
    var failed by remember(uid,genre){mutableStateOf(false)}
    val plans=remember(day,revision,genre,tastes){
        if(genre=="Todos")albumExploreGenres(day,revision,tastes) else listOfNotNull(genreForQuery(genre))
    }
    LaunchedEffect(uid,day,genre,revision,plans){
        busy=true;failed=false
        val groups=mutableListOf<List<NativeCollection>>()
        var errors=0
        try{
            for(plan in plans){
                val group=mutableListOf<NativeCollection>()
                for(seed in albumExploreSeeds(plan,day,revision,if(genre=="Todos")1 else 4)){
                    if(!NeroSupabase.identityReady.value||NeroSupabase.activeUid.value!=uid)return@LaunchedEffect
                    try{
                        val found=AlbumExploreCache.query(seed,day,revision>0)
                        group+=genreCollections(plan,found,NativeCollectionKind.ALBUM).take(if(genre=="Todos")4 else 8)
                    }catch(e:CancellationException){throw e}catch(_:Exception){errors++}
                }
                groups+=group
                if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid){
                    val partial=diverseAlbumRows(groups)
                    if(partial.isNotEmpty())rows=partial
                }
            }
            if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid){
                val result=diverseAlbumRows(groups)
                if(result.isNotEmpty()||errors==0)rows=result
                failed=errors>0
            }
        }finally{busy=false}
    }
    Column(if(fullScreen)Modifier.fillMaxSize() else Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            if(fullScreen)IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text("Explore álbuns",fontSize=21.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            IconButton(enabled=!busy,onClick={revision++}){Icon(Icons.Default.Refresh,"Atualizar álbuns")}
            if(!fullScreen)TextButton(onClick=onOpen){Text("Ver tudo")}
        }
        Text(if(genre=="Todos")"Seleção variada · novas descobertas a cada dia" else "Seleção de artistas de "+genre,fontSize=12.sp,color=Color.LightGray)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            items(listOf("Todos")+neroGenreLabels){label->FilterChip(selected=genre==label,onClick={onGenre(label)},label={Text(label)})}
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(failed)Text(if(rows.isEmpty())"Não foi possível consultar os álbuns. Atualize para tentar novamente." else "Algumas consultas ficaram indisponíveis.",fontSize=12.sp,color=Color.LightGray)
        else if(!busy&&rows.isEmpty())Text("Nenhum álbum da seleção encontrado. Experimente outro gênero.",fontSize=12.sp,color=Color.LightGray)
        if(fullScreen){
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
                items(rows.chunked(2)){pair->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        pair.forEach{entry->ExploreAlbumCard(entry,Modifier.weight(1f),onAlbum)}
                        if(pair.size==1)Spacer(Modifier.weight(1f))
                    }
                }
            }
        }else LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            items(rows.take(12),key={it.url}){entry->ExploreAlbumCard(entry,Modifier.width(140.dp),onAlbum)}
        }
    }
}
@Composable
private fun ExploreAlbumCard(entry:NativeCollection,modifier:Modifier,onAlbum:(NativeCollection)->Unit){
    Card(onClick={onAlbum(entry)},modifier=modifier,shape=RoundedCornerShape(12.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Column(Modifier.padding(8.dp)){
            NeroArtwork(model=entry.artwork,contentDescription="Capa de "+entry.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
            Text(entry.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold)
            Text(displayArtistName(entry.credit),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=Color.LightGray)
        }
    }
}
