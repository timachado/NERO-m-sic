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
internal fun OnlineDiscoveryShelf(favorites:List<OnlineRef>,canPlay:Boolean,onQueue:(List<MediaItem>,Int)->Unit,onSearch:(String)->Unit={},onAlbum:(NativeCollection)->Unit={}){
    if(!NativeMusicCatalog.enabled)return
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    if(!ready)return
    key(uid,ready){OnlineDiscoveryContent(favorites,canPlay,onQueue,uid,onSearch,onAlbum)}
}
@Composable
private fun OnlineDiscoveryContent(favorites:List<OnlineRef>,canPlay:Boolean,onQueue:(List<MediaItem>,Int)->Unit,historyUid:String?,onSearch:(String)->Unit,onAlbum:(NativeCollection)->Unit){
    val context=LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("nero_personal",0)}
    val historyReady=true
    val historyChanges by NeroAccountSync.changes.collectAsState()
    var heard by remember(historyUid,historyReady,historyChanges){mutableStateOf(history(context))}
    val scope=rememberCoroutineScope()
    var mix by remember(historyUid,historyReady){mutableStateOf(emptyList<NativeMusicHit>())}
    var dailyDay by remember{mutableLongStateOf(NeroReplay.today())}
    var daily by remember(historyUid,dailyDay){mutableStateOf(runCatching{NeroDailyCache.read(context,historyUid,dailyDay)}.getOrDefault(emptyList()))}
    LaunchedEffect(Unit){
        while(true){dailyDay=NeroReplay.today();kotlinx.coroutines.delay(30000L)}
    }
    var busy by remember{mutableStateOf(false)}
    var notice by remember{mutableStateOf<String?>(null)}
    DisposableEffect(prefs,historyUid,historyReady){
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key=="history"||key==null)heard=history(context)}
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val recent=remember(heard){heard.mapNotNull{it.asNativeHit()}.distinctBy{it.id}.take(12)}
    val liked=remember(favorites){favorites.mapNotNull(::musicHitFromRef)}
    var played by remember{mutableStateOf(if(historyUid==null)replayTracks(NeroReplay.entries(context),NeroReplay.today(),30)else emptyList())}
    LaunchedEffect(Unit){
        if(historyUid==null)while(true){played=replayTracks(NeroReplay.entries(context),NeroReplay.today(),30);kotlinx.coroutines.delay(10000L)}
    }
    val hidden=rememberHiddenSongs()
    var resetHidden by remember(historyUid,historyReady){mutableStateOf(false)}
    if(resetHidden)AlertDialog(onDismissRequest={resetHidden=false},title={Text("Restaurar recomendações?")},text={Text("As músicas ocultadas poderão voltar a aparecer no seu mix. Seus favoritos e playlists não serão alterados.")},confirmButton={TextButton(enabled=historyReady,onClick={val owner=historyUid;resetHidden=false;scope.launch{
        try{resetScopedHiddenSongs(context,owner)}catch(e:CancellationException){throw e}catch(_:Exception){if(NeroSupabase.activeUid.value==owner)notice="Não foi possível restaurar as recomendações."}
    }}){Text("Restaurar")}},dismissButton={TextButton(onClick={resetHidden=false}){Text("Cancelar")}})
    val followed=rememberFollowedArtists()
    val scopedPlayed=if(historyReady&&historyUid==null)played else emptyList()
    val artists=remember(heard,liked,scopedPlayed,followed){
        rankNeroMixArtists(followed,liked.map{it.artist},heard.filter{recommendationId(it.id)!=null}.map{it.artist},replayArtists(scopedPlayed).map{it.first})
    }
    val excluded=remember(heard,liked,scopedPlayed){
        (heard.map{it.id}+liked.map{it.id}+scopedPlayed.map{it.id}).mapNotNull(::recommendationId).toSet()
    }
    val visibleMix=remember(mix,hidden,excluded){filterRecommendedSongs(mix,hidden).filterNot{it.id in excluded}.take(24)}
    val dailyVisible=remember(daily,artists,hidden,excluded){
        val keys=artists.map(::nativeArtistKey).toSet()
        daily.filter{nativeArtistKey(it.artist) in keys}.map{collection->
            collection.copy(tracks=filterRecommendedSongs(collection.tracks,hidden).filterNot{it.id in excluded})
        }.filter{it.tracks.isNotEmpty()}
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(recent.isNotEmpty()){
            Text("Ouça novamente",fontSize=21.sp,fontWeight=FontWeight.Bold)
            OnlineDiscoveryRow(recent,canPlay,onQueue)
        }
        if(hidden.isNotEmpty())TextButton(onClick={resetHidden=true}){Text("Restaurar músicas ocultadas (${hidden.size})")}
        if(artists.isNotEmpty()){
            Text("Seu mix NERO",fontSize=21.sp,fontWeight=FontWeight.Bold)
            Text(if(historyUid!=null)"Sugestões dos artistas seguidos, favoritos e histórico online da sua conta, se disponível." else "Sugestões dos artistas seguidos, favoritos e histórico deste aparelho.",fontSize=12.sp,color=Color.LightGray)
            Text("Inspirado em: "+artists.take(3).joinToString(" · "),fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
            Text("Ao gerar, somente os nomes dos artistas são consultados na fonte musical.",fontSize=11.sp,color=Color.LightGray)
            OutlinedButton(enabled=!busy&&historyReady,onClick={
                val owner=historyUid
                val generatedDay=dailyDay
                busy=true;notice=null
                scope.launch{
                    try{
                        val groups=mutableListOf<List<NativeMusicHit>>()
                        val dailyGroups=mutableListOf<Pair<String,List<NativeMusicHit>>>()
                        var failures=0
                        for(artist in artists){
                            if(!NeroSupabase.identityReady.value||NeroSupabase.activeUid.value!=owner)return@launch
                            try{
                                val results=NativeMusicCatalog.search(artist).take(20)
                                groups+=results
                                dailyGroups+=artist to results
                            }
                            catch(e:CancellationException){throw e}catch(_:Exception){failures++}
                        }
                        if(!NeroSupabase.identityReady.value||NeroSupabase.activeUid.value!=owner)return@launch
                        val candidates=interleaveNeroMix(groups,excluded,emptySet(),limit=60)
                        val available=filterRecommendedSongs(candidates,scopedHiddenSongs(context,owner))
                        if(available.isNotEmpty())mix=candidates
                        if(generatedDay==dailyDay&&generatedDay==NeroReplay.today()){
                            val collections=buildNeroDailyMixes(dailyGroups,generatedDay,excluded,emptySet())
                            if(collections.isNotEmpty()){
                                daily=collections
                                try{kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){NeroDailyCache.write(context,owner,generatedDay,collections)}}
                                catch(e:CancellationException){throw e}catch(_:Exception){}
                            }
                        }
                        notice=when{
                            failures==artists.size->"Não foi possível consultar os artistas. Tente novamente."
                            available.isEmpty()->"Nenhuma nova faixa encontrada. Tente atualizar depois."
                            failures>0->"Mix criado. Algumas consultas ficaram indisponíveis."
                            else->null
                        }
                    }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível atualizar o mix."}
                    finally{busy=false}
                }
            }){Text(if(busy)"Preparando mix…" else if(visibleMix.isEmpty()&&dailyVisible.isEmpty())"Gerar meus mixes" else "Atualizar meus mixes")}
            notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
            if(visibleMix.isNotEmpty()){
                Button(enabled=canPlay,onClick={onQueue(visibleMix.map{it.queueItem()}.withOrigin(PlaybackOrigin("mix","Meu mix NERO","")),0)}){Icon(Icons.Default.PlayArrow,null);Text("Reproduzir mix")}
                OnlineDiscoveryRow(visibleMix,canPlay,onQueue)
            }
            Text("NERO Daily Mix",fontSize=21.sp,fontWeight=FontWeight.Bold)
            Text("Coleções do dia inspiradas nos artistas que combinam com seu gosto. Atualize quando quiser; nenhuma consulta automática.",fontSize=12.sp,color=Color.LightGray)
            if(dailyVisible.isEmpty())Text("Gere seus mixes para descobrir novas faixas hoje.",fontSize=12.sp,color=Color.LightGray)
            dailyVisible.forEach{collection->
                val name="Daily Mix • "+collection.artist
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text(name,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text("${collection.tracks.size} faixas sugeridas",fontSize=12.sp,color=Color.LightGray)
                    }
                    FilledTonalIconButton(enabled=canPlay,onClick={onQueue(collection.tracks.map{it.queueItem()}.withOrigin(PlaybackOrigin("mix",name,"")),0)}){
                        Icon(Icons.Default.PlayArrow,"Reproduzir "+name)
                    }
                }
                OnlineDiscoveryRow(collection.tracks,canPlay,onQueue)
            }
            NeroPersonalDiscovery(artists,followed+liked.map{it.artist}+heard.map{it.artist},visibleMix+dailyVisible.flatMap{it.tracks},hidden,historyUid,onSearch,onAlbum)
        }else{
            Text("Seu mix NERO",fontSize=21.sp,fontWeight=FontWeight.Bold)
            Text("Curta músicas ou siga artistas para descobrir novas faixas do seu gosto.",fontSize=12.sp,color=Color.LightGray)
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
