package br.com.timachado.neromusic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val artSlots=Semaphore(2)
internal suspend fun artwork(context:Context,track:Track,size:Int):Bitmap? = artSlots.withPermit {
    withContext(Dispatchers.IO) {
        if(Build.VERSION.SDK_INT>=29) {
            val thumb=runCatching { context.contentResolver.loadThumbnail(track.uri,Size(size,size),null) }.getOrNull()
            if(thumb!=null) return@withContext thumb
        }
        runCatching {
            val r=MediaMetadataRetriever()
            try {
                r.setDataSource(context,track.uri)
                val bytes=r.embeddedPicture ?: return@runCatching null
                if(bytes.size>12*1024*1024) return@runCatching null
                val o=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,o)
                if(o.outWidth<=0 || o.outHeight<=0) return@runCatching null
                var sample=1
                while(o.outWidth/sample>size*2 || o.outHeight/sample>size*2)sample*=2
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample })
            } finally { r.release() }
        }.getOrNull()
    }
}
@Composable
internal fun LocalArtwork(track:Track?, modifier:Modifier=Modifier, large:Boolean=false) {
    val context=LocalContext.current
    var image by remember(track?.uri,large) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(track?.uri,large) {
        image=if(track!=null)artwork(context,track,if(large)720 else 256) else null
    }
    Box(modifier.clip(RoundedCornerShape(if(large)24.dp else 10.dp)).background(Color(0xFF14242A)),contentAlignment=Alignment.Center) {
        val bitmap=image
        if(bitmap!=null) Image(bitmap.asImageBitmap(),"Capa de "+(track?.album ?: "música"),Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Icon(Icons.Default.MusicNote,null,tint=Color(0xFF00E5FF),modifier=Modifier.fillMaxSize(.5f))
    }
}
private data class Group(val key:String,val name:String,val detail:String,val tracks:List<Track>)
@Composable
internal fun LocalCollectionsScreen(kind:Int,tracks:List<Track>,granted:Boolean,canPlay:Boolean,onPermission:()->Unit,onPlay:(List<Track>,Int)->Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val groups=remember(kind,tracks) {
        tracks.groupBy { t -> when(kind) {
            3 -> if(t.albumId>0) "album:"+t.albumId else "name:"+t.album+"\u0000"+t.artist
            4 -> t.artist
            else -> t.genre
        } }.map { (key,list) ->
            val ordered=if(kind==3)list.sortedWith(compareBy<Track> { if(it.trackNumber>0)it.trackNumber else Int.MAX_VALUE }.thenBy { it.title }) else list.sortedBy { it.title.lowercase() }
            Group(key,when(kind){3->list.first().album;4->list.first().artist;else->list.first().genre},
                if(kind==3)list.map{it.artist}.distinct().let{if(it.size==1)it.first() else "Vários artistas"} else "${list.size} músicas",ordered)
        }.sortedBy { it.name.lowercase() }
    }
    val group=groups.firstOrNull { it.key==selected }
    BackHandler(group!=null){selected=null}
    val label=when(kind){3->"Álbuns";4->"Artistas";else->"Gêneros"}
    LazyColumn(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=12.dp)){
        item {
            if(group==null) {
                Text(label,fontSize=24.sp,fontWeight=FontWeight.Bold)
                Text("Biblioteca do aparelho · ${groups.size} categorias",fontSize=12.sp,color=Color.LightGray)
                if(kind==5 && Build.VERSION.SDK_INT<30)Text("Neste Android, o índice local de gêneros não está disponível.",fontSize=12.sp,color=Color.LightGray)
                OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Filtrar "+label.lowercase())})
            } else {
                IconButton(onClick={selected=null}){Icon(Icons.Default.ArrowBack,"Voltar")}
                if(kind==3)LocalArtwork(group.tracks.firstOrNull(),Modifier.fillMaxWidth().height(180.dp))
                Text(group.name,fontSize=24.sp,fontWeight=FontWeight.Bold)
                Text("${group.tracks.size} músicas",color=Color.LightGray)
                Button(enabled=canPlay&&group.tracks.isNotEmpty(),onClick={onPlay(group.tracks,0)},modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Default.PlayArrow,null);Text("Reproduzir")
                }
            }
            if(!granted)TextButton(onClick=onPermission){Text("Permitir acesso às músicas")}
        }
        if(group!=null)items(group.tracks,key={it.id}){track ->
            val now=LocalNeroNowPlaying.current
            Row(Modifier.fillMaxWidth().clickable(enabled=canPlay){if(now.matches(track.id.toString()))now.open()else onPlay(group.tracks,group.tracks.indexOf(track))}.padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
                LocalArtwork(track,Modifier.size(50.dp))
                Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                    Text(track.title,maxLines=2,overflow=TextOverflow.Ellipsis,color=nowTrackColor(track.id.toString()));NowTrackStatus(track.id.toString())
                    Text(track.artist,color=Color.LightGray,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.PlayArrow,"Reproduzir",tint=Color(0xFF39FF14))
            }
        } else {
            val visible=groups.filter{it.name.contains(query,true)||it.detail.contains(query,true)}
            if(visible.isEmpty())item{Text("Nenhuma categoria disponível.",color=Color.LightGray)}
            items(visible.chunked(2)){pair ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    pair.forEach{g ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF10171C)).clickable{selected=g.key}.padding(9.dp)){
                            LocalArtwork(g.tracks.firstOrNull(),Modifier.fillMaxWidth().aspectRatio(1f))
                            Spacer(Modifier.height(6.dp))
                            Text(g.name,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(g.detail,color=Color.LightGray,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                    if(pair.size==1)Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
