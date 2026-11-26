package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

internal fun radarWeekly(rows:List<CatalogEntry>,today:LocalDate,artists:List<String> = emptyList()):List<CatalogEntry>{
    val keys=artists.flatMap(::artistCreditParts).map(::genreKey).filter{it.isNotBlank()}.toSet()
    return rows.filter{row->
        val date=runCatching{LocalDate.parse(row.releaseDate)}.getOrNull()
        date!=null&&!date.isBefore(today.minusDays(6))&&!date.isAfter(today)&&
            (keys.isEmpty()||genreKey(row.credit) in keys||artistCreditParts(row.credit).any{genreKey(it) in keys})
    }.distinctBy{it.id}.sortedByDescending{it.releaseDate}
}
internal fun radarGenres(artists:List<String>):List<String>{
    val labels=mapOf("Pagode" to "pagode","Sertanejo" to "sertanejo","Pop Brasil" to "pop","Gospel" to "gospel","Samba" to "samba","Rock" to "rock","MPB" to "mpb","Eletrônica" to "electronic","Hip-hop" to "hip hop","Jazz" to "jazz")
    return neroGenres.mapNotNull{genre->
        val score=artists.sumOf{artist->artistCreditParts(artist).count{genre.matchesArtist(it)}}
        labels[genre.label]?.takeIf{score>0}?.let{it to score}
    }.sortedByDescending{it.second}.map{it.first}.take(2)
}
@Composable
internal fun NeroRadarShelf(favorites:List<OnlineRef>,onSearch:(String)->Unit){
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    if(!ready)return
    val followed=rememberFollowedArtists()
    val changes by NeroAccountSync.changes.collectAsState()
    val recent=remember(uid,changes){history(context).map{it.artist}}
    val artists=remember(uid,followed,favorites,recent){
        rankNeroMixArtists(followed,favorites.map{it.artist},recent,emptyList(),limit=12)
            .flatMap(::artistCreditParts).distinctBy(::genreKey).take(6)
    }
    val genres=remember(artists){radarGenres(artists)}
    val prefs=remember(uid){context.getSharedPreferences("nero_radar_"+(uid?.let{NeroAccountLibraryStore.fileName(it)}?:"guest"),0)}
    var enabled by remember(uid){mutableStateOf(prefs.getBoolean("enabled",false))}
    var revision by remember(uid){mutableIntStateOf(0)}
    var busy by remember(uid){mutableStateOf(false)}
    var personal by remember(uid){mutableStateOf(emptyList<CatalogEntry>())}
    var weekly by remember(uid){mutableStateOf(emptyList<CatalogEntry>())}
    var notice by remember(uid){mutableStateOf<String?>(null)}
    LaunchedEffect(uid,enabled,artists,genres,revision){
        if(!enabled){personal=emptyList();weekly=emptyList();notice=null;busy=false;return@LaunchedEffect}
        val owner=uid
        busy=true;notice=null
        try{
            val matches=mutableListOf<CatalogEntry>()
            val other=mutableListOf<CatalogEntry>()
            var errors=0
            for(artist in artists){
                if(!NeroSupabase.identityReady.value||NeroSupabase.activeUid.value!=owner)return@LaunchedEffect
                try{matches+=DevCatalog.releaseSnapshot(7,null,revision>0,artist).entries.let{radarWeekly(it,LocalDate.now(),listOf(artist))}}
                catch(e:CancellationException){throw e}catch(_:Exception){errors++}
            }
            for(genre in genres.ifEmpty{listOf(null)}){
                if(!NeroSupabase.identityReady.value||NeroSupabase.activeUid.value!=owner)return@LaunchedEffect
                try{other+=DevCatalog.releaseSnapshot(7,genre,revision>0).entries}
                catch(e:CancellationException){throw e}catch(_:Exception){errors++}
            }
            if(NeroSupabase.activeUid.value!=owner||!NeroSupabase.identityReady.value)return@LaunchedEffect
            personal=radarWeekly(matches,LocalDate.now()).take(30)
            weekly=radarWeekly(other,LocalDate.now()).filterNot{row->personal.any{it.id==row.id}}.take(30)
            if(errors>0)notice="Algumas consultas ficaram indisponíveis."
        }finally{busy=false}
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("NERO Radar",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Color(0xFF39FF14))
        Text("Lançamentos dos últimos 7 dias · gratuito",fontSize=13.sp,color=Color.LightGray)
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("Personalizar meu Radar",modifier=Modifier.weight(1f))
            Switch(checked=enabled,onCheckedChange={enabled=it;prefs.edit().putBoolean("enabled",it).apply()})
        }
        if(!enabled)Text("Ative para consultar novidades pelos nomes dos artistas seguidos, favoritos e ouvidos recentemente. Você pode desativar quando quiser.",fontSize=12.sp,color=Color.LightGray)
        else{
            OutlinedButton(enabled=!busy,onClick={revision++},modifier=Modifier.fillMaxWidth()){Text("Atualizar Radar")}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
            Text("Dos seus artistas",fontWeight=FontWeight.Bold)
            if(personal.isEmpty()&&!busy)Text(if(artists.isEmpty())"Siga artistas ou ouça músicas para personalizar." else "Nenhum lançamento recente encontrado nesta consulta.",fontSize=12.sp,color=Color.LightGray)
            RadarRows(personal,onSearch)
            Text(if(genres.isEmpty())"Novidades da semana" else "Radar dos seus gêneros",fontWeight=FontWeight.Bold)
            RadarRows(weekly,onSearch)
            Text("Datas de primeira publicação registradas no MusicBrainz. Toque em um lançamento para pesquisar no NERO Music.",fontSize=11.sp,color=Color.LightGray)
        }
    }
}
@Composable
private fun RadarRows(rows:List<CatalogEntry>,onSearch:(String)->Unit){
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        items(rows,key={it.id}){entry->
            Card(onClick={onSearch(entry.searchText)},modifier=Modifier.width(142.dp)){
                Column(Modifier.padding(8.dp)){
                    AsyncImage(model=entry.coverUrl,contentDescription="Capa de "+entry.title,modifier=Modifier.fillMaxWidth().aspectRatio(1f),contentScale=ContentScale.Crop)
                    Text(entry.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                    Text(displayArtistName(entry.credit),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp)
                    Text(entry.releaseDate,fontSize=11.sp,color=Color(0xFF00E5FF))
                }
            }
        }
    }
}
