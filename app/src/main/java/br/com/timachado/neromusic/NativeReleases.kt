package br.com.timachado.neromusic

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

private data class ReleaseDiscoverySnapshot(val rows:List<NativeCollection>,val at:Long,val day:LocalDate)
private object ReleaseDiscovery {
    private val mutex=Mutex()
    private val cache=LinkedHashMap<String,ReleaseDiscoverySnapshot>()
    suspend fun query(genre:String,force:Boolean):ReleaseDiscoverySnapshot=mutex.withLock{
        val day=LocalDate.now()
        val key=genre
        val now=System.currentTimeMillis()
        cache[key]?.let{if(!force&&it.day==day&&now-it.at in 0L..900000L)return@withLock it}
        val term=listOf("lançamentos",if(genre=="Todos")"música Brasil" else genre,day.year.toString()).joinToString(" ")
        val rows=NativeMusicCatalog.collections(term,NativeCollectionKind.ALBUM)
        val snapshot=ReleaseDiscoverySnapshot(rows,System.currentTimeMillis(),day)
        cache[key]=snapshot
        if(cache.size>16)cache.remove(cache.keys.first())
        snapshot
    }
}
private data class ReleaseState(val rows:List<NativeCollection> = emptyList(),val busy:Boolean=true,val failed:Boolean=false,val at:Long=0L)

@Composable
private fun releaseState(genre:String,revision:Int):ReleaseState{
    val lifecycle=(LocalContext.current as? LifecycleOwner)?.lifecycle
    var state by remember(genre){mutableStateOf(ReleaseState())}
    LaunchedEffect(genre,revision,lifecycle){
        var force=revision>0
        suspend fun refresh(){
            state=state.copy(busy=true,failed=false)
            try{val result=ReleaseDiscovery.query(genre,force);state=ReleaseState(result.rows,false,false,result.at)}
            catch(e:CancellationException){throw e}
            catch(_:Exception){state=state.copy(busy=false,failed=true)}
            finally{force=false}
        }
        if(lifecycle==null)refresh()else lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
            while(true){refresh();delay(900000L)}
        }
    }
    return state
}

@Composable
private fun ReleaseAlbumCard(entry:NativeCollection,modifier:Modifier,onAlbum:(NativeCollection)->Unit){
    Card(onClick={onAlbum(entry)},modifier=modifier,shape=RoundedCornerShape(12.dp)){
        Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF12313A)),contentAlignment=Alignment.Center){
                Icon(Icons.Default.Album,null,tint=Color(0xFF00E5FF),modifier=Modifier.size(36.dp))
                entry.artwork?.let{AsyncImage(model=it,contentDescription="Capa de "+entry.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())}
            }
            Text(entry.title,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(entry.credit,fontSize=12.sp,color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("Abrir álbum",fontSize=11.sp,color=Color(0xFF39FF14))
        }
    }
}

@Composable
private fun ReleaseStatus(state:ReleaseState){
    if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(state.failed)Text(if(state.rows.isEmpty())"Consulta indisponível. Tente atualizar." else "Exibindo a última consulta.",fontSize=12.sp,color=Color.LightGray)
    else if(!state.busy&&state.rows.isEmpty())Text("Nenhum álbum encontrado.",fontSize=12.sp,color=Color.LightGray)
}

@Composable
internal fun NativeReleaseShelf(onOpen:()->Unit,onAlbum:(NativeCollection)->Unit){
    var revision by remember{mutableIntStateOf(0)}
    val state=releaseState("Todos",revision)
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("Descubra lançamentos",fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            IconButton(enabled=!state.busy,onClick={revision++}){Icon(Icons.Default.Refresh,"Atualizar")}
            TextButton(onClick=onOpen){Text("Ver tudo")}
        }
        Text("Busca de novidades · YouTube Music",fontSize=12.sp,color=Color.LightGray)
        ReleaseStatus(state)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            items(state.rows.take(12),key={it.url}){entry->ReleaseAlbumCard(entry,Modifier.width(138.dp),onAlbum)}
        }
    }
}

@Composable
internal fun NativeReleasesScreen(onBack:()->Unit,onAlbum:(NativeCollection)->Unit){
    val genres=listOf("Todos","Sertanejo","Pagode","Gospel","Pop","Rock","MPB","Samba","Forró","Eletrônica","Hip-hop")
    var genre by remember{mutableStateOf("Todos")}
    var revision by remember{mutableIntStateOf(0)}
    val state=releaseState(genre,revision)
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text("Lançamentos",fontSize=23.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            IconButton(enabled=!state.busy,onClick={revision++}){Icon(Icons.Default.Refresh,"Atualizar")}
        }
        Text("Resultados de busca por novidades no YouTube Music. Datas de lançamento não verificadas.",fontSize=12.sp,color=Color.LightGray)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            items(genres){label->FilterChip(selected=genre==label,onClick={genre=label},label={Text(label)})}
        }
        if(state.at>0L)Text("Consultado: "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(state.at)),fontSize=11.sp,color=Color.LightGray)
        ReleaseStatus(state)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(state.rows.chunked(2)){pair->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    pair.forEach{entry->ReleaseAlbumCard(entry,Modifier.weight(1f),onAlbum)}
                    if(pair.size==1)Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
