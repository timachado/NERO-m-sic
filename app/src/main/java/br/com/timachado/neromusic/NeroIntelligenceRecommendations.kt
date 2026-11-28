package br.com.timachado.neromusic

import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal enum class NeroDiscoveryMode(val label:String,val description:String,val newQuota:Int){
    FAMILIAR("Familiar","Mais faixas dos artistas que você conhece.",1),
    BALANCED("Equilibrado","Combine afinidade e novos artistas.",2),
    EXPLORE("Descoberta","Dê prioridade a artistas ainda não presentes nos seus sinais.",3)
}
internal data class NeroRecommendation(val hit:NativeMusicHit,val reason:String,val unfamiliar:Boolean)

/** At most four searches, using only artists from the selected DNA and curated style. */
internal fun intelligenceQueries(dna:NeroMusicalDna,known:Set<String>,mode:NeroDiscoveryMode):List<String>{
    val familiar=dna.artists.map{it.name}.distinctBy(::nativeArtistKey)
    val genres=dna.styles.mapNotNull{style->neroGenres.firstOrNull{it.label==style}}
    val novel=genres.flatMap{it.artists}.distinctBy(::nativeArtistKey).filter{nativeArtistKey(it) !in known}
    val n=mode.newQuota
    val first=familiar.take(4-n)
    return (first+novel.take(n)+familiar.drop(first.size)+novel.drop(n)).distinctBy(::nativeArtistKey).take(4)
}
/** No fabricated affinity scores. Strictly exclude hidden and already-known recording IDs. */
internal fun intelligenceRank(groups:List<List<NativeMusicHit>>,dna:NeroMusicalDna,known:Set<String>,excluded:Set<String>,hidden:Set<String>,mode:NeroDiscoveryMode,limit:Int=12):List<NeroRecommendation>{
    require(limit in 1..24)
    val artists=dna.artists.associateBy{nativeArtistKey(it.name)}
    val genres=dna.styles.mapNotNull{style->neroGenres.firstOrNull{it.label==style}}
    val denied=(excluded+hidden).mapNotNull(::recommendationId).toSet()
    val candidates=interleaveNeroMix(groups,denied,emptySet(),limit=60).mapNotNull{hit->
        val key=nativeArtistKey(hit.artist)
        if(key.isBlank())return@mapNotNull null
        val artist=artists[key]
        val genre=genres.firstOrNull{it.matchesArtist(hit.artist)}
        val unfamiliar=key !in known
        val reason=when{
            artist?.followed==true->"Você segue este artista"
            artist!=null&&artist.liked>0->"Você curtiu músicas deste artista"
            artist!=null&&artist.recent>0->"Você ouviu este artista recentemente"
            unfamiliar&&genre!=null->"Outro artista da seleção de "+genre.label
            key in known->"Artista presente na sua biblioteca"
            else->return@mapNotNull null
        }
        NeroRecommendation(hit,reason,unfamiliar)
    }
    val familiar=candidates.filterNot{it.unfamiliar}.toMutableList()
    val novel=candidates.filter{it.unfamiliar}.toMutableList()
    val result=mutableListOf<NeroRecommendation>()
    val counts=mutableMapOf<String,Int>()
    fun take(rows:MutableList<NeroRecommendation>):NeroRecommendation?{
        while(rows.isNotEmpty()){
            val row=rows.removeAt(0)
            val k=nativeArtistKey(row.hit.artist)
            if((counts[k]?:0)<3){counts[k]=(counts[k]?:0)+1;return row}
        }
        return null
    }
    while(result.size<limit){
        val preferNovel=if(mode==NeroDiscoveryMode.EXPLORE)result.size%4!=3 else result.size%4>=4-mode.newQuota
        val row=if(preferNovel)take(novel)?:take(familiar)else take(familiar)?:take(novel)
        if(row==null)break
        result+=row
    }
    return result
}

@Composable
internal fun NeroIntelligenceRecommendations(uid:String?,dna:NeroMusicalDna,known:Set<String>,excluded:Set<String>,mode:NeroDiscoveryMode,onMode:(NeroDiscoveryMode)->Unit,canPlay:Boolean,onQueue:(List<MediaItem>,Int)->Unit,onSearch:(String)->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val hidden=rememberHiddenSongs()
    // Discard old results when a preference, account, or history signal changes.
    var results by remember(uid,dna,known,excluded,mode){mutableStateOf(emptyList<NeroRecommendation>())}
    var busy by remember(uid){mutableStateOf(false)}
    var notice by remember(uid,dna,known,excluded,mode){mutableStateOf<String?>(null)}
    val request=remember(uid,dna,known,excluded,mode){Any()}
    val currentRequest by rememberUpdatedState(request)
    val queries=remember(dna,known,mode){intelligenceQueries(dna,known,mode)}
    val visible=results.filter{recommendationId(it.hit.id) !in hidden}
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("FEITO PARA VOCÊ",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary,fontWeight=FontWeight.Bold)
        Text("Descubra no seu ritmo",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("Escolha como o NERO equilibra afinidade e descoberta.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            items(NeroDiscoveryMode.entries){option->FilterChip(selected=mode==option,enabled=!busy,onClick={onMode(option)},label={Text(option.label)})}
        }
        Text(mode.description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(enabled=!busy&&queries.isNotEmpty()&&NativeMusicCatalog.enabled,onClick={
            val token=request;busy=true;notice=null
            scope.launch{
                try{
                    val groups=mutableListOf<List<NativeMusicHit>>()
                    var failed=0
                    for(artist in queries){
                        if(!NeroSupabase.identityReady.value||NeroSupabase.activeUid.value!=uid||currentRequest!==token)return@launch
                        try{groups+=NativeMusicCatalog.search(artist).take(20)}
                        catch(e:CancellationException){throw e}catch(_:Exception){failed++}
                    }
                    if(!NeroSupabase.identityReady.value||NeroSupabase.activeUid.value!=uid||currentRequest!==token)return@launch
                    results=intelligenceRank(groups,dna,known,excluded,scopedHiddenSongs(context,uid),mode)
                    notice=when{failed==queries.size->"Catálogo temporariamente indisponível."
                        results.isEmpty()->"Nenhuma faixa compatível encontrada. Tente outro modo."
                        failed>0->"Algumas consultas não ficaram disponíveis."
                        else->null}
                }catch(e:CancellationException){throw e}catch(_:Exception){if(currentRequest===token)notice="Não foi possível gerar sugestões."}
                finally{busy=false}
            }
        },modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text(if(busy)"Preparando sugestões…" else if(results.isEmpty())"Gerar recomendações" else "Atualizar recomendações")}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(queries.isEmpty())Text("Siga artistas ou curta músicas para receber recomendações.",style=MaterialTheme.typography.bodySmall)
        Text("Ao gerar, apenas nomes dos artistas usados na busca musical são consultados. Nenhuma consulta automática.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        notice?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
        if(visible.isNotEmpty()){
            val queue=visible.map{it.hit.queueItem()}.withOrigin(PlaybackOrigin("mix","NERO Intelligence",""))
            OutlinedButton(enabled=canPlay,onClick={onQueue(queue,0)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PlayArrow,null);Text("Reproduzir recomendações")}
            visible.forEachIndexed{index,row->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                    NeroArtwork(model=row.hit.thumbnail,contentDescription=null,modifier=Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)),contentScale=ContentScale.Crop)
                    Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                        Text(row.hit.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                        Text(displayArtistName(row.hit.artist),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                        Text(row.reason,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(enabled=canPlay,onClick={onQueue(queue,index)}){Icon(Icons.Default.PlayArrow,"Reproduzir "+row.hit.title)}
                }
            }
        }
        if(dna.styles.isNotEmpty()){
            Text("EXPANDA SEU UNIVERSO",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary,fontWeight=FontWeight.Bold)
            Text("Explore também os estilos sugeridos pelo seu DNA.",style=MaterialTheme.typography.bodySmall)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(dna.styles){style->AssistChip(onClick={onSearch(style)},label={Text(style)})}}
        }
    }
}
