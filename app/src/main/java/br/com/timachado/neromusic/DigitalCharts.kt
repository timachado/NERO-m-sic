package br.com.timachado.neromusic

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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

internal data class ChartSong(val rank:Int,val id:String,val title:String,val artist:String,val artwork:String?,val officialUrl:String?)
internal data class ChartSnapshot(val songs:List<ChartSong>,val fetchedAt:Long,val sourceUpdated:String)
internal object DigitalCharts{
    private val mutex=Mutex()
    private var last:ChartSnapshot?=null
    suspend fun brazil(force:Boolean=false):ChartSnapshot=withContext(Dispatchers.IO){
        mutex.withLock{
            val now=System.currentTimeMillis()
            last?.let{if(!force&&now-it.fetchedAt in 0L..3600000L)return@withLock it}
            val songs=NativeMusicCatalog.brazilChart()
            check(songs.isNotEmpty())
            ChartSnapshot(songs,System.currentTimeMillis(),"").also{last=it}
        }
    }
}
@Composable
internal fun DigitalChartsShelf(onSearch:(String)->Unit,canPlay:Boolean,onQueue:(List<androidx.media3.common.MediaItem>,Int)->Unit){
    val context=LocalContext.current
    val lifecycle=(context as? LifecycleOwner)?.lifecycle
    var snapshot by remember{mutableStateOf<ChartSnapshot?>(null)}
    var error by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(true)}
    var revision by remember{mutableIntStateOf(0)}
    var expanded by remember{mutableStateOf(false)}
    var showSource by remember{mutableStateOf(false)}
    if(showSource)AlertDialog(onDismissRequest={showSource=false},title={Text("Sobre as paradas")},text={Text("Paradas digitais do NERO Music: ranking semanal do Brasil exibido a partir de uma fonte externa (YouTube Music Global Charts). A classificação não representa reproduções medidas pelo NERO Music.")},confirmButton={TextButton(onClick={showSource=false}){Text("Fechar")}})
    LaunchedEffect(lifecycle,revision){
        var force=revision>0
        suspend fun update(){
            busy=true;error=false
            try{snapshot=DigitalCharts.brazil(force)}
            catch(e:CancellationException){throw e}
            catch(_:Exception){error=true}
            finally{busy=false;force=false}
        }
        if(lifecycle==null)update()else lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
            while(true){update();delay(3600000L)}
        }
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(7.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("Paradas digitais NERO",fontSize=21.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            IconButton(enabled=!busy,onClick={revision++}){Icon(Icons.Default.Refresh,"Atualizar parada")}
        }
        Text("Brasil · Top músicas da semana",fontSize=12.sp,color=Color.LightGray)
        snapshot?.let{data->Text("Consultado: "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(data.fetchedAt)),fontSize=11.sp,color=Color.LightGray)}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error)Text(if(snapshot==null)"Parada temporariamente indisponível." else "Falha na atualização. Exibindo a última consulta.",fontSize=12.sp,color=Color.LightGray)
        val songs=snapshot?.songs.orEmpty()
        fun playSong(song:ChartSong){
            if(!canPlay||!NativeMusicCatalog.enabled){onSearch(song.title+" "+song.artist);return}
            val rows=songs.filter{Regex("[A-Za-z0-9_-]{11}").matches(it.id)}
            val index=rows.indexOfFirst{it.id==song.id}
            if(index>=0)onQueue(rows.map{NativeMusicHit(it.id,it.title,it.artist,0L).queueItem()}.withOrigin(PlaybackOrigin("chart","Top Brasil","")),index)
        }
        if(songs.isNotEmpty()){
            LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                items(songs.take(10),key={it.id}){song->
                    Card(onClick={playSong(song)},modifier=Modifier.width(144.dp)){
                        Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF14242A)),contentAlignment=Alignment.Center){
                                Icon(Icons.Default.MusicNote,null,tint=Color(0xFF00E5FF))
                                song.artwork?.let{AsyncImage(model=it,contentDescription="Capa de "+song.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())}
                                Text("#"+song.rank,modifier=Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha=.8f),RoundedCornerShape(bottomEnd=8.dp)).padding(7.dp),fontWeight=FontWeight.Bold,color=Color(0xFF39FF14))
                            }
                            Text(song.title,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(displayArtistName(song.artist),fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=Color.LightGray)
                        }
                    }
                }
            }
            TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Recolher parada" else "Ver "+songs.size+" posições disponíveis")}
            if(expanded)for(song in songs){
                Card(onClick={playSong(song)},modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                        Text(song.rank.toString(),modifier=Modifier.width(36.dp),color=Color(0xFF39FF14),fontWeight=FontWeight.Bold)
                        Column(Modifier.weight(1f)){Text(song.title,maxLines=2,overflow=TextOverflow.Ellipsis);Text(displayArtistName(song.artist),fontSize=12.sp,color=Color.LightGray)}
                    }
                }
            }
        }
        TextButton(onClick={showSource=true}){Text("Sobre o ranking")}
        Text("Top Brasil · Toque em uma faixa para ouvir no NERO Music.",fontSize=11.sp,color=Color.LightGray)
    }
}
