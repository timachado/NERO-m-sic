package br.com.timachado.neromusic
import android.os.Bundle
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.CancellationException

internal data class PlaybackOrigin(val kind:String,val name:String,val id:String,val artist:String="",val cover:String?=null){
    val caption:String get()=when(kind){"album"->"Tocando do álbum";"playlist","local_playlist","imported","favorites"->"Tocando da playlist";"search"->"Tocando da pesquisa";"chart"->"Tocando das paradas";"artist"->"Tocando do artista";"related"->"Músicas relacionadas";else->"Reproduzindo"}
    fun album():NativeCollection?{
        if(kind!="album")return null
        val u=Uri.parse(id)
        if(u.scheme!="https"||u.host !in setOf("www.youtube.com","youtube.com","music.youtube.com","m.youtube.com")||u.getQueryParameter("list").isNullOrBlank())return null
        return NativeCollection(id,name,artist,cover,NativeCollectionKind.ALBUM)
    }
}
internal fun MediaItem.playbackOrigin():PlaybackOrigin?{
    val e=mediaMetadata.extras?:return null
    return PlaybackOrigin(e.getString("nero_origin_kind")?:return null,e.getString("nero_origin_name")?:return null,e.getString("nero_origin_id").orEmpty(),e.getString("nero_origin_artist").orEmpty(),e.getString("nero_origin_cover"))
}
internal fun List<MediaItem>.withOrigin(origin:PlaybackOrigin?):List<MediaItem>{
    if(origin==null)return this
    return map{item->
        val extras=Bundle(item.mediaMetadata.extras?:Bundle()).apply{
            putString("nero_origin_kind",origin.kind);putString("nero_origin_name",origin.name);putString("nero_origin_id",origin.id)
            putString("nero_origin_artist",origin.artist);putString("nero_origin_cover",origin.cover)
        }
        item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
    }
}
@Composable
internal fun PlayerLyricsPreview(mediaId:String,title:String,artist:String,position:Long,duration:Long,localUri:Uri?,onOpen:()->Unit){
    val context=LocalContext.current
    var lines by remember(mediaId,title,artist){mutableStateOf(emptyList<LyricLine>())}
    var busy by remember(mediaId){mutableStateOf(true)}
    LaunchedEffect(mediaId,title,artist,(duration+500L)/1000L){
        busy=true
        try{lines=playerPreviewLyrics(context,mediaId,title,artist,duration,localUri)}
        catch(e:CancellationException){throw e}catch(_:Exception){lines=emptyList()}
        finally{busy=false}
    }
    Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF12374A)),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Prévia da letra",fontWeight=FontWeight.Bold,fontSize=19.sp)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(lines.isEmpty()&&!busy)Text("Letra ainda indisponível.",color=Color.LightGray)
            val active=lines.indexOfLast{(it.at?:Long.MAX_VALUE)<=position}
            val start=if(active>=0)(active-1).coerceAtLeast(0) else 0
            lines.drop(start).take(4).forEachIndexed{i,line->Text(line.text,color=if(start+i==active)Color.White else Color(0xFF97C8DE),fontWeight=FontWeight.Bold,fontSize=20.sp)}
            Button(onClick=onOpen){Text("Mostrar letra")}
        }
    }
}
private data class PlayerDiscovery(val album:NativeCollection?,val albumSongs:List<NativeMusicHit>,val related:List<NativeMusicHit>)
@Composable
internal fun PlayerContextShelf(ref:OnlineRef?,artist:String,origin:PlaybackOrigin?,canPlay:Boolean,onAlbum:(NativeCollection)->Unit,onArtist:(String)->Unit,onQueue:(List<MediaItem>,Int)->Unit){
    val seed=ref?.let(::musicHitFromRef)
    var data by remember(ref?.id,origin){mutableStateOf<PlayerDiscovery?>(null)}
    var busy by remember(ref?.id,origin){mutableStateOf(false)}
    LaunchedEffect(ref?.id,origin){
        if(seed==null||!NativeMusicCatalog.enabled)return@LaunchedEffect
        busy=true
        try{
            var matched=origin?.album()
            var tracks=emptyList<NativeMusicHit>()
            if(matched!=null)tracks=NativeMusicCatalog.albumTracks(matched)
            else for(candidate in NativeMusicCatalog.collections(seed.title+" "+seed.artist,NativeCollectionKind.ALBUM).take(3)){
                val rows=try{NativeMusicCatalog.albumTracks(candidate)}catch(e:CancellationException){throw e}catch(_:Exception){emptyList()}
                if(rows.any{it.id==seed.id}){matched=candidate;tracks=rows;break}
            }
            val other=try{onlineRadio(seed,(tracks.map{it.id}+seed.id).toSet()).take(8)}catch(e:CancellationException){throw e}catch(_:Exception){emptyList()}
            data=PlayerDiscovery(matched,tracks.filter{it.id!=seed.id}.take(12),other)
        }catch(e:CancellationException){throw e}catch(_:Exception){
            val other=try{onlineRadio(seed,setOf(seed.id)).take(8)}catch(e:CancellationException){throw e}catch(_:Exception){emptyList()}
            data=PlayerDiscovery(null,emptyList(),other)
        }finally{busy=false}
    }
    if(seed!=null&&NativeMusicCatalog.enabled){
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){
            if(busy){Text("Encontrando músicas relacionadas",fontSize=16.sp,fontWeight=FontWeight.Bold);LinearProgressIndicator(Modifier.fillMaxWidth())}
            data?.let{result->
                result.album?.let{album->
                    Card(onClick={onAlbum(album)},modifier=Modifier.fillMaxWidth()){
                        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                            NeroArtwork(model=album.artwork?:seed.thumbnail,contentDescription=null,modifier=Modifier.size(76.dp).clip(RoundedCornerShape(8.dp)),contentScale=ContentScale.Crop)
                            Column(Modifier.weight(1f).padding(start=12.dp)){Text("Do mesmo álbum",color=Color(0xFF00E5FF),fontSize=12.sp);Text(album.title,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)}
                            Icon(Icons.Default.ChevronRight,"Abrir álbum")
                        }
                    }
                    if(result.albumSongs.isNotEmpty())PlayerRelatedRows(result.albumSongs,canPlay){hit->
                        val (queue,index)=nativeQueue(result.albumSongs,hit)
                        onQueue(queue.withOrigin(PlaybackOrigin("album",album.title,album.url,album.credit,album.artwork)),index)
                    }
                }
                Text("Músicas relacionadas",fontSize=20.sp,fontWeight=FontWeight.Bold)
                if(result.related.isEmpty())Text("Nenhuma sugestão disponível agora.",color=Color.LightGray)
                else PlayerRelatedRows(result.related,canPlay){hit->
                    val (queue,index)=nativeQueue(result.related,hit)
                    onQueue(queue.withOrigin(PlaybackOrigin("related","Relacionadas a "+seed.title,"")),index)
                }
            }
        }
    }
    if(artist.isNotBlank())ArtistProfileCard(artist,onOpen={onArtist(artist)})
}
@Composable
private fun PlayerRelatedRows(rows:List<NativeMusicHit>,canPlay:Boolean,onPlay:(NativeMusicHit)->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        rows.forEach{hit->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                NeroArtwork(model=hit.thumbnail,contentDescription=null,modifier=Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),contentScale=ContentScale.Crop)
                Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(hit.title,maxLines=2,overflow=TextOverflow.Ellipsis);Text(hit.artist,color=Color.LightGray,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
                IconButton(enabled=canPlay,onClick={onPlay(hit)}){Icon(Icons.Default.PlayArrow,"Reproduzir",tint=Color(0xFF39FF14))}
            }
        }
    }
}
