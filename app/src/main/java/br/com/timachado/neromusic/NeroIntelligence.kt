package br.com.timachado.neromusic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem

internal data class NeroDnaArtist(val name:String,val followed:Boolean,val liked:Int,val recent:Int)
internal data class NeroMusicalDna(val artists:List<NeroDnaArtist>,val styles:List<String>)

/** Explainable local profile. Never infer listening duration or demographic traits. */
internal fun musicalDna(followed:List<String>,liked:List<String>,recent:List<String>,includeHistory:Boolean):NeroMusicalDna{
    val history=if(includeHistory)recent.take(100)else emptyList()
    val follows=followed.map(::nativeArtistKey).toSet()
    val likes=liked.take(1000).groupingBy(::nativeArtistKey).eachCount()
    val heard=history.groupingBy(::nativeArtistKey).eachCount()
    val artists=rankNeroMixArtists(followed,liked,history,emptyList(),limit=6).map{name->
        val key=nativeArtistKey(name)
        NeroDnaArtist(name,key in follows,likes[key]?:0,heard[key]?:0)
    }
    val styles=neroGenres.mapNotNull{genre->
        val count=artists.count{genre.matchesArtist(it.name)}
        if(count>0)genre.label to count else null
    }.sortedByDescending{it.second}.take(4).map{it.first}
    return NeroMusicalDna(artists,styles)
}
private fun dnaPrefs(context:Context,uid:String?)=context.getSharedPreferences(
    "nero_dna_"+(uid?.let{NeroAccountLibraryStore.fileName(it)}?:"guest"),Context.MODE_PRIVATE)

@Composable
internal fun NeroIntelligenceEntry(onOpen:()->Unit){
    val colors=MaterialTheme.colorScheme
    Card(onClick=onOpen,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=colors.surfaceVariant)){
        Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(colors.primary.copy(alpha=.12f),colors.surfaceVariant,colors.secondary.copy(alpha=.10f)))).padding(16.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(colors.primary.copy(alpha=.15f)),contentAlignment=Alignment.Center){
                Icon(Icons.Default.AutoAwesome,null,tint=colors.primary,modifier=Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal=12.dp)){
                Text("NERO INTELLIGENCE",style=MaterialTheme.typography.labelMedium,color=colors.secondary,fontWeight=FontWeight.Bold)
                Text("Seu universo musical",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text("DNA Musical, mixes, Radar e Replay",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowRight,"Abrir NERO Intelligence",tint=colors.primary)
        }
    }
}
private enum class IntelligenceTab(val label:String,val icon:ImageVector){DNA("Para você",Icons.Default.AutoAwesome),MIX("Mixes e DJ",Icons.Default.GraphicEq),RADAR("Radar",Icons.Default.Notifications),REPLAY("Replay",Icons.Default.History),LOCAL("No aparelho",Icons.Default.LibraryMusic)}

@Composable
internal fun NeroIntelligenceScreen(favorites:List<OnlineRef>,localTracks:List<Track>,favoriteIds:Set<String>,canPlay:Boolean,onBack:()->Unit,onQueue:(List<MediaItem>,Int)->Unit,onSearch:(String)->Unit,onAlbum:(NativeCollection)->Unit,onRadio:(List<Track>)->Unit,onPlay:(MediaItem)->Unit){
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    var tab by remember(uid,ready){mutableStateOf(IntelligenceTab.DNA)}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
            Column(Modifier.weight(1f)){
                Text("NERO INTELLIGENCE",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary,fontWeight=FontWeight.Bold)
                Text("Seu universo musical",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text("Descobertas e mixes feitos para você",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.AutoAwesome,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(28.dp))
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            items(IntelligenceTab.entries){option->
                FilterChip(selected=tab==option,onClick={tab=option},shape=RoundedCornerShape(24.dp),leadingIcon={Icon(option.icon,null,modifier=Modifier.size(16.dp))},label={Text(option.label)})
            }
        }
        HorizontalDivider()
        if(!ready){LinearProgressIndicator(Modifier.fillMaxWidth());return}
        key(uid,tab){
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=16.dp)){
                item{
                    when(tab){
                        IntelligenceTab.DNA->NeroDnaPanel(uid,favorites,onSearch,onMix={tab=IntelligenceTab.MIX},onReplay={tab=IntelligenceTab.REPLAY},onRadar={tab=IntelligenceTab.RADAR},canPlay=canPlay,onQueue=onQueue)
                        IntelligenceTab.MIX->if(NativeMusicCatalog.enabled)OnlineDiscoveryShelf(favorites,canPlay,onQueue,onSearch,onAlbum)else Text("O catálogo musical está indisponível.")
                        IntelligenceTab.RADAR->NeroRadarShelf(favorites,onSearch)
                        IntelligenceTab.REPLAY->NeroReplayShelf(onSearch)
                        IntelligenceTab.LOCAL->Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                            PersonalRecommendations(localTracks,favoriteIds,canPlay,onPlay)
                            RadioShelf(localTracks,favoriteIds,canPlay,onRadio)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NeroDnaPanel(uid:String?,favorites:List<OnlineRef>,onSearch:(String)->Unit,onMix:()->Unit,onReplay:()->Unit,onRadar:()->Unit,canPlay:Boolean,onQueue:(List<MediaItem>,Int)->Unit){
    val context=LocalContext.current
    val prefs=remember(uid){dnaPrefs(context,uid)}
    var includeHistory by remember(uid){mutableStateOf(prefs.getBoolean("include_history",false))}
    val followed=rememberFollowedArtists()
    val revision by NeroAccountSync.changes.collectAsState()
    var historyRevision by remember(uid){mutableIntStateOf(0)}
    DisposableEffect(uid){
        val p=context.getSharedPreferences("nero_personal",0)
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,k->
            if(NeroLocalRecent.isHistoryKey(k)||k?.startsWith("recent_enabled:")==true)historyRevision++
        }
        p.registerOnSharedPreferenceChangeListener(listener)
        onDispose{p.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val historyAllowed=includeHistory&&(uid==null||NeroLocalRecent.enabled(context,uid))
    val recentRows=remember(uid,historyAllowed,revision,historyRevision){
        if(historyAllowed)history(context).filter{recommendationId(it.id)!=null}else emptyList()
    }
    val recent=remember(recentRows){recentRows.map{it.artist}}
    val dna=remember(followed,favorites,recent,historyAllowed){
        musicalDna(followed,favorites.map{it.artist},recent,historyAllowed)
    }
    val known=remember(followed,favorites,recent){(followed+favorites.map{it.artist}+recent).map(::nativeArtistKey).toSet()}
    val excluded=remember(favorites,recentRows){(favorites.map{it.id}+recentRows.map{it.id}).mapNotNull(::recommendationId).toSet()}
    var mode by remember(uid){mutableStateOf(runCatching{NeroDiscoveryMode.valueOf(prefs.getString("discovery_mode","BALANCED")?:"BALANCED")}.getOrDefault(NeroDiscoveryMode.BALANCED))}
    val colors=MaterialTheme.colorScheme
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=colors.surfaceVariant)){
        Column(Modifier.background(Brush.verticalGradient(listOf(colors.primary.copy(alpha=.12f),colors.surfaceVariant))).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("DNA MUSICAL",style=MaterialTheme.typography.labelLarge,color=colors.secondary,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Icon(Icons.Default.Fingerprint,null,tint=colors.secondary,modifier=Modifier.size(30.dp))}
            Text("A música tem a sua assinatura.",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("Seu gosto, seus artistas, sua maneira de descobrir música.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
            DnaSignature(dna.artists.isNotEmpty())
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                item{DnaSignal("Curtidas",favorites.size)}
                item{DnaSignal("Seguidos",followed.size)}
                item{DnaSignal("Histórico",if(historyAllowed)recentRows.size else null)}
            }
            if(dna.artists.isEmpty())Text("Curta músicas ou siga artistas para começar a construir seu DNA.",style=MaterialTheme.typography.bodyMedium)
            else{
                Text("Artistas em destaque",fontWeight=FontWeight.Bold)
                dna.artists.take(3).forEachIndexed{index,artist->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Text((index+1).toString().padStart(2,'0'),color=colors.primary,style=MaterialTheme.typography.titleMedium)
                        Column(Modifier.weight(1f).padding(start=12.dp)){
                            Text(displayArtistName(artist.name),fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                            val reason=listOfNotNull(if(artist.followed)"Seguido" else null,if(artist.liked>0)"${artist.liked} curtida(s)" else null,if(artist.recent>0)"${artist.recent} recente(s)" else null)
                            Text(reason.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                        }
                        IconButton(onClick={onSearch(artist.name)}){Icon(Icons.Default.Search,"Buscar artista")}
                    }
                }
                if(dna.styles.isNotEmpty()){
                    Text("Estilos sugeridos",fontWeight=FontWeight.Bold)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        items(dna.styles){style->AssistChip(onClick={onSearch(style)},label={Text(style)})}
                    }
                    Text("Estimativa por artistas conhecidos, não classificação de áudio.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                }
            }
            Button(onClick=onMix,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text("Explorar meus mixes")}
        }
    }
    NeroIntelligenceRecommendations(uid,dna,known,excluded,mode,onMode={value->
        if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid){prefs.edit().putString("discovery_mode",value.name).apply();mode=value}
    },canPlay=canPlay,onQueue=onQueue,onSearch=onSearch)
    Text("SEUS MIXES E INSIGHTS",style=MaterialTheme.typography.labelMedium,color=colors.secondary,fontWeight=FontWeight.Bold)
    IntelligenceShortcut("Mixes inteligentes","Daily Mix, Smart Mix e NERO DJ",Icons.Default.GraphicEq,onMix)
    IntelligenceShortcut("Radar musical","Descobertas de artistas e catálogo",Icons.Default.Notifications,onRadar)
    IntelligenceShortcut("Seu Replay","Explore suas estatísticas disponíveis",Icons.Default.History,onReplay)
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=colors.surface)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.Shield,null,tint=colors.secondary)
                Text("Seu perfil, seu controle",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f).padding(horizontal=8.dp))
            }
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Usar histórico no DNA Musical",modifier=Modifier.weight(1f))
                Switch(checked=includeHistory,onCheckedChange={value->
                    if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid){prefs.edit().putBoolean("include_history",value).apply();includeHistory=value}
                })
            }
            if(includeHistory&&!historyAllowed)Text("Histórico pausado na conta. O DNA não usa esse sinal.",style=MaterialTheme.typography.bodySmall)
            Text("O DNA é calculado no aparelho, sem nova coleta ou envio de histórico. Esta escolha não ativa gravação nem sincronização e não altera as opções dos outros recursos.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
        }
    }
}
}

@Composable
private fun DnaSignal(label:String,count:Int?){
    Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.80f)){
        Column(Modifier.padding(horizontal=12.dp,vertical=8.dp)){
            Text(count?.toString()?:"Desativado",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun DnaSignature(active:Boolean){
    val primary=MaterialTheme.colorScheme.primary
    val secondary=MaterialTheme.colorScheme.secondary
    // Decorative brand waveform, not audio measurement or fabricated usage statistics.
    Canvas(Modifier.fillMaxWidth().height(52.dp)){
        val bars=43
        for(i in 0 until bars){
            val x=(i+.5f)*size.width/bars
            val wave=kotlin.math.abs(kotlin.math.sin(i*.39)*kotlin.math.cos(i*.14)).toFloat()
            val h=size.height*(.12f+(if(active).82f else .28f)*wave)
            drawLine(if(i%3==0)secondary else primary,Offset(x,size.height/2-h/2),Offset(x,size.height/2+h/2),strokeWidth=3.dp.toPx(),cap=StrokeCap.Round,alpha=if(active).9f else .45f)
        }
    }
}
@Composable
private fun IntelligenceShortcut(title:String,subtitle:String,icon:ImageVector,onClick:()->Unit){
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha=.12f)),contentAlignment=Alignment.Center){Icon(icon,null,tint=MaterialTheme.colorScheme.primary)}
            Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(title,fontWeight=FontWeight.Bold);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Icon(Icons.Default.KeyboardArrowRight,"Abrir "+title)
        }
    }
}
