package br.com.timachado.neromusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException

internal fun nativeArtistKey(value:String):String=value.trim().removeSuffix(" - Topic").trim().lowercase(java.util.Locale.ROOT)
internal fun artistTracks(artist:String,hits:List<NativeMusicHit>):List<NativeMusicHit>{
    val key=nativeArtistKey(artist)
    return if(key.isBlank())emptyList()else hits.filter{nativeArtistKey(it.artist)==key}.distinctBy{it.id}.take(30)
}
@Composable
internal fun NativeArtistScreen(artist:String,seed:List<NativeMusicHit>,canPlay:Boolean,onBack:()->Unit,onQueue:(List<MediaItem>,Int)->Unit,onExplore:(String)->Unit,onAlbum:((NativeCollection)->Unit)?=null){
    val profile=rememberArtistProfile(artist)
    var albums by remember(artist){mutableStateOf(emptyList<NativeCollection>())}
    LaunchedEffect(artist){if(NativeMusicCatalog.enabled&&artist.trim().length>=3){try{albums=NativeMusicCatalog.collections(artist,NativeCollectionKind.ALBUM).filter{nativeArtistKey(it.credit)==nativeArtistKey(artist)}.take(10)}catch(e:CancellationException){throw e}catch(_:Exception){}}}
    var songs by remember(artist){mutableStateOf(artistTracks(artist,seed))}
    var loading by remember(artist){mutableStateOf(true)}
    var failed by remember(artist){mutableStateOf(false)}
    var revision by remember(artist){mutableIntStateOf(0)}
    var adding by remember{mutableStateOf<List<OnlineRef>?>(null)}
    LaunchedEffect(artist,revision){
        loading=true;failed=false
        try{songs=artistTracks(artist,songs+NativeMusicCatalog.search(artist))}
        catch(e:CancellationException){throw e}
        catch(_:Exception){failed=true}
        finally{loading=false}
    }
    adding?.let{SaveOnlineDialog(it,onClose={adding=null})}
    val now=LocalNeroNowPlaying.current
    fun play(hit:NativeMusicHit){if(now.matches(hit.reference().id)){now.open();return};val (queue,index)=nativeQueue(songs,hit);onQueue(queue.withOrigin(PlaybackOrigin("artist",artist,artist)),index)}
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=12.dp)){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
                Text("Artista",fontSize=19.sp,fontWeight=FontWeight.Bold)
            }
            ArtistProfileCard(artist,profile=profile,expanded=true)
            if(albums.isNotEmpty()){
                Text("Álbuns e singles",fontSize=21.sp,fontWeight=FontWeight.Bold)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    items(albums,key={it.url}){album->
                        Card(onClick={if(onAlbum!=null)onAlbum(album)else onExplore(album.title+" "+artist)},modifier=Modifier.width(130.dp)){
                            Column(Modifier.padding(8.dp)){
                                if(album.artwork!=null)AsyncImage(model=album.artwork,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
                                else Box(Modifier.fillMaxWidth().aspectRatio(1f),contentAlignment=Alignment.Center){Icon(Icons.Default.Album,null)}
                                Text(album.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(enabled=canPlay&&songs.isNotEmpty(),onClick={songs.firstOrNull()?.let{play(it)}},modifier=Modifier.weight(1f)){Icon(Icons.Default.PlayArrow,null);Text("Reproduzir")}
                OutlinedButton(enabled=songs.isNotEmpty(),onClick={adding=songs.map{it.reference()}},modifier=Modifier.weight(1f)){Text("Salvar faixas")}
            }
            TextButton(onClick={onExplore(artist)}){Text("Explorar álbuns e outros resultados")}
            Text("Músicas",fontSize=21.sp,fontWeight=FontWeight.Bold)
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(failed){Text("Não foi possível atualizar o artista.",color=Color.LightGray);TextButton(onClick={revision++}){Text("Tentar novamente")}}
            else if(!loading&&songs.isEmpty())Text("Nenhuma faixa identificada para este artista.",color=Color.LightGray)
        }
        items(songs,key={it.id}){hit->
            Row(Modifier.fillMaxWidth().clickable(enabled=canPlay){play(hit)},verticalAlignment=Alignment.CenterVertically){
                AsyncImage(model=hit.thumbnail,contentDescription="Capa de "+hit.title,contentScale=ContentScale.Crop,modifier=Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                    Text(hit.title,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis,color=nowTrackColor(hit.reference().id))
                    Text(hit.artist,fontSize=12.sp,color=Color.LightGray);NowTrackStatus(hit.reference().id)
                }
                IconButton(onClick={adding=listOf(hit.reference())}){Icon(Icons.Default.PlaylistAdd,"Adicionar à playlist",tint=Color(0xFF39FF14))}
                NowTrackAction(hit.reference().id,canPlay){play(hit)}
            }
        }
    }
}
