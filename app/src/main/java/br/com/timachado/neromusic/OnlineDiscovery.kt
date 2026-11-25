package br.com.timachado.neromusic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
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
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal object NeroAutoplay{
    fun enabled(context:Context)=context.getSharedPreferences("nero_playback",0).getBoolean("online_autoplay",false)
    fun set(context:Context,value:Boolean){context.getSharedPreferences("nero_playback",0).edit().putBoolean("online_autoplay",value).apply()}
}
internal fun Heard.asNativeHit():NativeMusicHit?=musicHitFromRef(OnlineRef(id,title,artist))
internal suspend fun onlineRadio(seed:NativeMusicHit,excluded:Set<String>):List<NativeMusicHit>{
    val artist=seed.artist.removeSuffix(" - Topic").trim()
    val term=artist.ifBlank{seed.title}.take(100)
    if(term.length<3)return emptyList()
    val rows=NativeMusicCatalog.search(term)
    return rows.filter{it.id!=seed.id&&it.id !in excluded}.distinctBy{it.id}.take(20)
}
@Composable
internal fun OnlineDiscoveryShelf(favorites:List<OnlineRef>,canPlay:Boolean,onQueue:(List<MediaItem>,Int)->Unit){
    if(!NativeMusicCatalog.enabled)return
    val context=LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("nero_personal",0)}
    var heard by remember{mutableStateOf(history(context))}
    val scope=rememberCoroutineScope()
    var mix by remember{mutableStateOf(emptyList<NativeMusicHit>())}
    var busy by remember{mutableStateOf(false)}
    var notice by remember{mutableStateOf<String?>(null)}
    DisposableEffect(prefs){
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key=="history"||key==null)heard=history(context)}
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val recent=remember(heard){heard.mapNotNull{it.asNativeHit()}.distinctBy{it.id}.take(12)}
    val liked=remember(favorites){favorites.mapNotNull(::musicHitFromRef)}
    var played by remember{mutableStateOf(replayTracks(NeroReplay.entries(context),NeroReplay.today(),30))}
    LaunchedEffect(Unit){
        while(true){played=replayTracks(NeroReplay.entries(context),NeroReplay.today(),30);kotlinx.coroutines.delay(10000L)}
    }
    val hidden=rememberHiddenSongs()
    var resetHidden by remember{mutableStateOf(false)}
    val visibleMix=filterRecommendedSongs(mix,hidden)
    if(resetHidden)AlertDialog(onDismissRequest={resetHidden=false},title={Text("Restaurar recomendações?")},text={Text("As músicas ocultadas poderão voltar a aparecer no seu mix. Seus favoritos e playlists não serão alterados.")},confirmButton={TextButton(onClick={NeroHiddenSongs.reset(context);resetHidden=false}){Text("Restaurar")}},dismissButton={TextButton(onClick={resetHidden=false}){Text("Cancelar")}})
    val followed=rememberFollowedArtists()
    val artists=remember(recent,liked,played,followed){preferredMixArtists(followed,mixArtists(played,liked.map{it.artist},recent.map{it.artist}))}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(recent.isNotEmpty()){
            Text("Ouça novamente",fontSize=21.sp,fontWeight=FontWeight.Bold)
            OnlineDiscoveryRow(recent,canPlay,onQueue)
        }
        if(hidden.isNotEmpty())TextButton(onClick={resetHidden=true}){Text("Restaurar músicas ocultadas (${hidden.size})")}
        if(artists.isNotEmpty()){
            Text("Seu mix NERO",fontSize=21.sp,fontWeight=FontWeight.Bold)
            Text("Sugestões pelos artistas que você segue, mais ouvidos, favoritos e recentes. Ao gerar, apenas os nomes dos artistas são consultados na fonte musical.",fontSize=12.sp,color=Color.LightGray)
            OutlinedButton(enabled=!busy,onClick={
                busy=true;notice=null
                scope.launch{
                    try{
                        val excluded=(recent+liked).map{it.id}.toSet()+played.map{it.id.removePrefix("youtube:")}
                        val rows=artists.flatMap{NativeMusicCatalog.search(it).take(12)}
                        mix=filterRecommendedSongs(rows.filter{it.id !in excluded},NeroHiddenSongs.read(context)).distinctBy{it.id}.take(24)
                        if(mix.isEmpty())notice="Nenhuma nova faixa encontrada."
                    }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível atualizar o mix."}
                    finally{busy=false}
                }
            }){Text(if(busy)"Preparando mix…" else if(mix.isEmpty())"Gerar meu mix" else "Atualizar meu mix")}
            notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
            if(visibleMix.isNotEmpty()){
                Button(enabled=canPlay,onClick={onQueue(visibleMix.map{it.queueItem()}.withOrigin(PlaybackOrigin("mix","Meu mix NERO","")),0)}){Icon(Icons.Default.PlayArrow,null);Text("Reproduzir mix")}
                OnlineDiscoveryRow(visibleMix,canPlay,onQueue)
            }
        }
    }
}
@Composable
private fun OnlineDiscoveryRow(rows:List<NativeMusicHit>,canPlay:Boolean,onQueue:(List<MediaItem>,Int)->Unit){
    LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
        items(rows,key={it.id}){hit->
            val now=LocalNeroNowPlaying.current
            Card(onClick={if(now.matches(hit.reference().id))now.open()else{val (queue,index)=nativeQueue(rows,hit);onQueue(queue,index)}},enabled=canPlay,modifier=Modifier.width(132.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
                Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    NeroArtwork(model=hit.thumbnail,contentDescription="Capa de "+hit.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
                    Text(hit.title,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis,color=nowTrackColor(hit.reference().id));NowTrackStatus(hit.reference().id)
                    Text(hit.artist,fontSize=12.sp,color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}
