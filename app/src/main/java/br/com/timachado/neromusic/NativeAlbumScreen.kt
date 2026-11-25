package br.com.timachado.neromusic

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException

@Composable
internal fun NativeAlbumScreen(album:NativeCollection,canPlay:Boolean,onBack:()->Unit,onQueue:(List<MediaItem>,Int)->Unit){
    var songs by remember(album.url){mutableStateOf(emptyList<NativeMusicHit>())}
    var busy by remember{mutableStateOf(true)}
    var failed by remember{mutableStateOf(false)}
    var retry by remember{mutableIntStateOf(0)}
    var adding by remember{mutableStateOf<List<OnlineRef>?>(null)}
    LaunchedEffect(album.url,retry){
        busy=true;failed=false
        try{songs=NativeMusicCatalog.albumTracks(album)}
        catch(e:CancellationException){throw e}catch(_:Exception){failed=true}
        finally{busy=false}
    }
    adding?.let{SaveOnlineDialog(it,onClose={adding=null})}
    fun play(hit:NativeMusicHit){val (queue,index)=nativeQueue(songs,hit);onQueue(queue.withOrigin(PlaybackOrigin("album",album.title,album.url,album.credit,album.artwork)),index)}
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
            AsyncImage(model=album.artwork,contentDescription="Capa do álbum",contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)))
            Text(album.title,fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text(album.credit,color=Color.LightGray)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(enabled=canPlay&&songs.isNotEmpty(),onClick={songs.firstOrNull()?.let{play(it)}}){Text("Reproduzir")}
                OutlinedButton(enabled=songs.isNotEmpty(),onClick={adding=songs.map{it.reference()}}){Text("Salvar faixas")}
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(failed){Text("Não foi possível carregar as faixas.");TextButton(onClick={retry++}){Text("Tentar novamente")}}
            else if(!busy&&songs.isEmpty())Text("Nenhuma faixa disponível.")
        }
        items(songs,key={it.id}){hit->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                AsyncImage(model=hit.thumbnail,contentDescription=null,modifier=Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),contentScale=ContentScale.Crop)
                Column(Modifier.weight(1f).padding(horizontal=8.dp)){Text(hit.title,maxLines=2,overflow=TextOverflow.Ellipsis);Text(hit.artist,fontSize=12.sp,color=Color.LightGray)}
                IconButton(onClick={adding=listOf(hit.reference())}){Icon(Icons.Default.PlaylistAdd,"Adicionar à playlist",tint=Color(0xFF39FF14))}
                IconButton(enabled=canPlay,onClick={play(hit)}){Icon(Icons.Default.PlayArrow,"Reproduzir",tint=Color(0xFF39FF14))}
            }
        }
    }
}
@Composable
internal fun NativeAlbumsShelf(onSearch:(String)->Unit,onAlbum:(NativeCollection)->Unit){
    var rows by remember{mutableStateOf(emptyList<NativeCollection>())}
    LaunchedEffect(Unit){
        try{rows=NativeMusicCatalog.collections("sertanejo",NativeCollectionKind.ALBUM).take(10)}
        catch(e:CancellationException){throw e}catch(_:Exception){}
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Explore álbuns",fontSize=21.sp,fontWeight=FontWeight.Bold)
        Text("Seleção de sertanejo · catálogo NERO Music",fontSize=12.sp,color=Color.LightGray)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            items(rows,key={it.url}){entry->
                Card(onClick={onAlbum(entry)},modifier=Modifier.width(140.dp)){
                    Column(Modifier.padding(8.dp)){
                        AsyncImage(model=entry.artwork,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
                        Text(entry.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold)
                        Text(entry.credit,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=Color.LightGray)
                    }
                }
            }
        }
    }
}
