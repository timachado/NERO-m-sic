package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NativeAlbumScreen(album:NativeCollection,canPlay:Boolean,onBack:()->Unit,onQueue:(List<MediaItem>,Int)->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    val accountUid by NeroSupabase.activeUid.collectAsState()
    val accountReady by NeroSupabase.identityReady.collectAsState()
    val accountScope=rememberCoroutineScope()
    val savedAlbums=rememberSavedAlbums()
    val savedKey=savedAlbumKey(album.url)
    val saved=savedKey!=null&&savedAlbums.any{savedAlbumKey(it.url)==savedKey}
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
    val now=LocalNeroNowPlaying.current
    fun play(hit:NativeMusicHit){if(now.matches(hit.reference().id)){now.open();return};val (queue,index)=nativeQueue(songs,hit);onQueue(queue.withOrigin(PlaybackOrigin("album",album.title,album.url,album.credit,album.artwork)),index)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
            NeroArtwork(model=album.artwork,contentDescription="Capa do álbum",contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().aspectRatio(1f).heightIn(max=340.dp).clip(RoundedCornerShape(16.dp)))
            Text(album.title,fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text(displayArtistName(album.credit),color=Color.LightGray)
            OutlinedButton(enabled=savedKey!=null,onClick={
                val owner=accountUid
                if(accountReady)accountScope.launch{
                    try{
                        require(NeroSupabase.activeUid.value==owner)
                        if(owner!=null)NeroAccountSync.toggleAlbum(context,owner,album)
                        else require(NeroSavedAlbums.toggle(context,album))
                    }catch(e:CancellationException){throw e}catch(_:Exception){android.widget.Toast.makeText(context,"Não foi possível salvar o álbum.",android.widget.Toast.LENGTH_SHORT).show()}
                }
            }){
                Icon(if(saved)Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(8.dp));Text(if(saved)"Álbum salvo · remover" else "Salvar álbum na biblioteca")
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(enabled=canPlay&&songs.isNotEmpty(),onClick={songs.firstOrNull()?.let{play(it)}}){Text("Reproduzir")}
                OutlinedButton(enabled=songs.isNotEmpty(),onClick={adding=songs.map{it.reference()}}){Text("Salvar faixas")}
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(failed){Text("Não foi possível carregar as faixas.");TextButton(onClick={retry++}){Text("Tentar novamente")}}
            else if(!busy&&songs.isEmpty())Text("Nenhuma faixa disponível.")
        }
        items(songs,key={it.id}){hit->
            Row(Modifier.fillMaxWidth().clickable(enabled=canPlay){play(hit)},verticalAlignment=Alignment.CenterVertically){
                NeroArtwork(model=hit.thumbnail,contentDescription=null,modifier=Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),contentScale=ContentScale.Crop)
                Column(Modifier.weight(1f).padding(horizontal=8.dp)){Text(hit.title,maxLines=2,overflow=TextOverflow.Ellipsis,color=nowTrackColor(hit.reference().id));Text(displayArtistName(hit.artist),fontSize=12.sp,color=Color.LightGray);NowTrackStatus(hit.reference().id)}
                IconButton(onClick={adding=listOf(hit.reference())}){Icon(Icons.Default.PlaylistAdd,"Adicionar à playlist",tint=MaterialTheme.colorScheme.primary)}
                NowTrackAction(hit.reference().id,canPlay){play(hit)}
            }
        }
    }
}
