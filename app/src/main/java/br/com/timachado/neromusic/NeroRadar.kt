package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
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

internal fun radarDiscoveries(rows:List<CatalogEntry>,artists:List<String> = emptyList()):List<CatalogEntry>{
    val keys=artists.flatMap(::artistCreditParts).map(::genreKey).filter{it.isNotBlank()}.toSet()
    return rows.filter{row->row.kind==CatalogKind.ALBUM&&row.id.startsWith("album:")&&
        (keys.isEmpty()||genreKey(row.credit) in keys||artistCreditParts(row.credit).any{genreKey(it) in keys})
    }.distinctBy{it.id}
}
internal fun radarGenres(artists:List<String>):List<String>{
    return neroGenres.mapNotNull{genre->
        val score=artists.sumOf{artist->artistCreditParts(artist).count{genre.matchesArtist(it)}}
        genre.releaseTag.takeIf{score>0}?.let{it to score}
    }.sortedByDescending{it.second}.map{it.first}.distinct().take(2)
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
    var alerts by remember(uid){mutableStateOf(prefs.getBoolean("alerts",false))}
    var alertNotice by remember(uid){mutableStateOf<String?>(null)}
    val requestPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid){
            alerts=granted&&NeroRadarAlerts.permitted(context)
            prefs.edit().putBoolean("alerts",alerts).apply()
            alertNotice=if(alerts)null else "Permissão de notificações não concedida."
        }
    }
    LaunchedEffect(uid,enabled,alerts,artists,genres){
        if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
            NeroRadarAlerts.configure(context,uid,enabled,artists,genres)
    }
    var inbox by remember(uid){mutableStateOf(prefs.getString("nativeInbox","[]").orEmpty())}
    DisposableEffect(prefs){
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->
            if(key=="nativeInbox")inbox=prefs.getString("nativeInbox","[]").orEmpty()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
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
                try{matches+=DevCatalog.releaseSnapshot(7,null,revision>0,artist).entries.let{radarDiscoveries(it,listOf(artist))}}
                catch(e:CancellationException){throw e}catch(_:Exception){errors++}
            }
            for(genre in genres.ifEmpty{listOf(null)}){
                if(!NeroSupabase.identityReady.value||NeroSupabase.activeUid.value!=owner)return@LaunchedEffect
                try{other+=DevCatalog.releaseSnapshot(7,genre,revision>0).entries}
                catch(e:CancellationException){throw e}catch(_:Exception){errors++}
            }
            if(NeroSupabase.activeUid.value!=owner||!NeroSupabase.identityReady.value)return@LaunchedEffect
            personal=radarDiscoveries(matches).take(30)
            weekly=radarDiscoveries(other).filterNot{row->personal.any{it.id==row.id}}.take(30)
            if(errors>0)notice="Algumas consultas ficaram indisponíveis."
        }finally{busy=false}
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("NERO Radar",fontSize=22.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary )
        Text("Descobertas do catálogo · gratuito",fontSize=13.sp,color=Color.LightGray)
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("Personalizar meu Radar",modifier=Modifier.weight(1f))
            Switch(checked=enabled,onCheckedChange={enabled=it;prefs.edit().putBoolean("enabled",it).apply()})
        }
        if(!enabled)Text("Ative para consultar novidades pelos nomes dos artistas seguidos, favoritos e ouvidos recentemente. Você pode desativar quando quiser.",fontSize=12.sp,color=Color.LightGray)
        else{
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Alertas de descobertas",modifier=Modifier.weight(1f))
                Switch(checked=alerts&&NeroRadarAlerts.permitted(context),onCheckedChange={value->
                    if(!value){alerts=false;prefs.edit().putBoolean("alerts",false).apply();androidx.core.app.NotificationManagerCompat.from(context).cancel(7819)}
                    else if(Build.VERSION.SDK_INT>=33&&!NeroRadarAlerts.permitted(context))requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else{
                        alerts=NeroRadarAlerts.permitted(context);prefs.edit().putBoolean("alerts",alerts).apply()
                        alertNotice=if(alerts)null else "Ative notificações nas configurações do Android."
                    }
                })
            }
            Text("Consulta diária em segundo plano quando o Android permitir. O primeiro levantamento cria uma referência; somente resultados ainda não vistos geram alertas.",fontSize=11.sp,color=Color.LightGray)
            alertNotice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
            OutlinedButton(enabled=!busy,onClick={revision++},modifier=Modifier.fillMaxWidth()){Text("Atualizar Radar")}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
            val news=remember(inbox){runCatching{org.json.JSONArray(inbox)}.getOrDefault(org.json.JSONArray())}
            if(news.length()>0){
                Text("Novidades para você",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary )
                for(i in 0 until minOf(news.length(),10)){
                    val item=news.optJSONObject(i)?:continue
                    Card(onClick={onSearch(item.optString("search"))},modifier=Modifier.fillMaxWidth()){
                        Column(Modifier.padding(12.dp)){
                            Text(item.optString("title"),fontWeight=FontWeight.Bold,maxLines=2)
                            Text(item.optString("credit"),fontSize=12.sp,color=Color.LightGray)
                        }
                    }
                }
                TextButton(onClick={prefs.edit().remove("nativeInbox").apply()}){Text("Limpar novidades")}
            }
            Text("Dos seus artistas",fontWeight=FontWeight.Bold)
            if(personal.isEmpty()&&!busy)Text(if(artists.isEmpty())"Siga artistas ou ouça músicas para personalizar." else "Nenhum álbum encontrado nesta consulta.",fontSize=12.sp,color=Color.LightGray)
            RadarRows(personal,onSearch)
            Text(if(genres.isEmpty())"Descobertas para explorar" else "Radar dos seus gêneros",fontWeight=FontWeight.Bold)
            RadarRows(weekly,onSearch)
            Text("Descobertas da fonte musical atual. A data de lançamento não é verificada. Toque para pesquisar no NERO Music.",fontSize=11.sp,color=Color.LightGray)
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
                    
                }
            }
        }
    }
}
