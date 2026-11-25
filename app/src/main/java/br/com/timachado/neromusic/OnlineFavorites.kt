package br.com.timachado.neromusic

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
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
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private fun favoritesFile(context:Context)=AtomicFile(File(context.filesDir,"nero-online-favorites.json"))
internal fun readOnlineFavorites(context:Context):List<OnlineRef>{
    val file=favoritesFile(context)
    if(!file.baseFile.exists())return emptyList()
    val rows=JSONArray(file.readFully().toString(Charsets.UTF_8))
    return (0 until rows.length()).map{i->
        val row=rows.getJSONObject(i)
        val id=row.getString("id")
        val open=row.optJSONObject("audio")?.let{runCatching{decodeOpenTrack(it)}.getOrNull()}?.takeIf{allowed(it)&&id=="commons:"+it.id}
        OnlineRef(id,row.getString("title"),row.getString("artist"),openAudio=open)
    }.distinctBy{it.id}
}
internal fun saveOnlineFavorites(context:Context,rows:List<OnlineRef>){
    val bytes=JSONArray(rows.distinctBy{it.id}.map{ref->JSONObject().put("id",ref.id).put("title",ref.title).put("artist",ref.artist).put("audio",ref.openAudio?.json())}).toString().toByteArray(Charsets.UTF_8)
    require(bytes.size<=4*1024*1024)
    val file=favoritesFile(context);val out=file.startWrite()
    try{out.write(bytes);file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e}
}
private data class FavoriteRow(val id:String,val title:String,val artist:String,val cover:String?,val local:Track?,val online:OnlineRef?,val item:MediaItem?)
@Composable
internal fun FavoritesScreen(localTracks:List<Track>,online:List<OnlineRef>,readError:Boolean,canPlay:Boolean,onLocalRemove:(Track)->Unit,onOnlineRemove:(OnlineRef)->Unit,onSearch:(String)->Unit,onQueue:(List<MediaItem>,Int)->Unit,offlineState:OfflineState=OfflineState(),onNativeSave:(NativeMusicHit)->Unit={},onOpenSave:(OpenTrack)->Unit={},onOfflineOpen:()->Unit={}){
    val context=LocalContext.current
    val saved=offlineState.saved
    val rows=remember(localTracks,online,saved){
        online.map{ref->
            val open=ref.openAudio?.takeIf{allowed(it)&&ref.id=="commons:"+it.id}
            val item=if(open!=null){
                val copy=saved.firstOrNull{it==open}
                open.item(if(copy!=null)Uri.fromFile(audioFile(context,copy))else Uri.parse(open.url))
            }else if(NativeMusicCatalog.enabled)musicHitFromRef(ref)?.queueItem() else null
            FavoriteRow(ref.id,ref.title,ref.artist,ref.coverUrl,null,ref,item)
        }+localTracks.map{track->FavoriteRow(track.id.toString(),track.title,track.artist,null,track,null,track.mediaItem())}
    }
    val playable=remember(rows){rows.filter{it.item!=null}}
    val queue=remember(playable){playable.mapNotNull{it.item}}
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)){
        item{
            Text("Favoritos",fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text("${rows.size} músicas · NERO Music",color=Color.LightGray,fontSize=13.sp)
            if(readError)Text("Não foi possível ler os favoritos online.",color=MaterialTheme.colorScheme.error)
            Button(enabled=canPlay&&queue.isNotEmpty(),onClick={onQueue(queue,0)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PlayArrow,null);Text("Reproduzir favoritos")}
            if(rows.isEmpty())Text("Toque no coração de uma música para salvá-la aqui.",color=Color.LightGray)
        }
        items(rows,key={it.id}){row->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                if(row.local!=null)LocalArtwork(row.local,Modifier.size(52.dp))
                else Box(Modifier.size(52.dp),contentAlignment=Alignment.Center){
                    Icon(Icons.Default.MusicNote,null,tint=Color(0xFF00E5FF))
                    row.cover?.let{AsyncImage(model=it,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))}
                }
                Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                    Text(row.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium,color=nowTrackColor(row.item?.mediaId));NowTrackStatus(row.item?.mediaId)
                    Text(row.artist,fontSize=12.sp,color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                row.online?.let{ref->
                    val hit=musicHitFromRef(ref)
                    val open=ref.openAudio?.takeIf(::allowed)
                    if(hit!=null&&NativeMusicCatalog.enabled)NativeDownloadIcon(hit,offlineState,onNativeSave,onOfflineOpen)
                    else if(open!=null){
                        val ready=saved.any{it==open}
                        IconButton(enabled=ready||(offlineState.active==null&&offlineState.nativeActive==null&&offlineState.batchTotal==0),onClick={if(ready)onOfflineOpen()else onOpenSave(open)}){
                            Icon(if(ready)Icons.Default.CheckCircle else Icons.Default.Download,if(ready)"Baixada" else "Baixar",tint=Color(0xFF39FF14))
                        }
                    }
                }
                IconButton(onClick={row.local?.let(onLocalRemove)?:row.online?.let(onOnlineRemove)}){Icon(Icons.Default.Favorite,"Retirar dos favoritos",tint=Color(0xFF39FF14))}
                val index=playable.indexOfFirst{it.id==row.id}
                if(index>=0)NowTrackAction(queue[index].mediaId,canPlay){onQueue(queue,index)}
                else IconButton(onClick={onSearch(row.title+" "+row.artist)}){Icon(Icons.Default.Search,"Encontrar no NERO")}
            }
        }
    }
}
