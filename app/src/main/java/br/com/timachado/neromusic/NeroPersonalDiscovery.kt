package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

internal fun discoveredArtists(rows:List<NativeMusicHit>,known:List<String>,hidden:Set<String>):List<NativeMusicHit>{
    val keys=known.map(::nativeArtistKey).toSet()
    return rows.filter{row->
        val key=nativeArtistKey(row.artist)
        recommendationId(row.id)==row.id&&row.id !in hidden&&key.length>=3&&key !in keys&&
            key !in setOf("unknown","unknown artist","artista desconhecido","<unknown>")
    }.distinctBy{nativeArtistKey(it.artist)}.take(8)
}
internal fun discoveryAlbums(rows:List<NativeCollection>,artists:List<String>,saved:Set<String>):List<NativeCollection>{
    val keys=artists.map(::nativeArtistKey).toSet()
    return rows.mapNotNull{row->
        val id=savedAlbumKey(row.url)?:return@mapNotNull null
        if(row.kind!=NativeCollectionKind.ALBUM||row.title.isBlank()||nativeArtistKey(row.credit) !in keys||id in saved)return@mapNotNull null
        row.copy(url="https://music.youtube.com/playlist?list="+id,artwork=cloudAlbumArtwork(row.artwork))
    }.distinctBy{savedAlbumKey(it.url)}.take(12)
}
// The existing music-album search has no verified release date field.
internal fun discoveryReleaseQuery(artist:String,year:Int):String =
    "lançamentos "+year+" "+NeroFollowedArtists.clean(artist).take(75)

internal fun discoveryReleaseCandidates(rows:List<NativeCollection>,artists:List<String>,excluded:Set<String>):List<NativeCollection> =
    discoveryAlbums(rows,artists,excluded).take(10)

@Composable
internal fun NeroPersonalDiscovery(artists:List<String>,known:List<String>,tracks:List<NativeMusicHit>,hidden:Set<String>,uid:String?,onSearch:(String)->Unit,onAlbum:(NativeCollection)->Unit){
    val seeds=artists.take(3)
    val saved=rememberSavedAlbums().mapNotNull{savedAlbumKey(it.url)}.toSet()
    val suggestions=remember(tracks,known,artists,hidden){discoveredArtists(tracks,known+artists,hidden)}
    key(uid,seeds){
        val scope=rememberCoroutineScope()
        var rawAlbums by remember{mutableStateOf(emptyList<NativeCollection>())}
        var rawReleases by remember{mutableStateOf(emptyList<NativeCollection>())}
        var releaseBusy by remember{mutableStateOf(false)}
        var releaseQueried by remember{mutableStateOf(false)}
        var busy by remember{mutableStateOf(false)}
        var queried by remember{mutableStateOf(false)}
        var albumError by remember{mutableStateOf(false)}
        var releaseError by remember{mutableStateOf(false)}
        val albums=remember(rawAlbums,seeds,saved){discoveryAlbums(rawAlbums,seeds,saved)}
        val releases=remember(rawReleases,seeds,saved,albums){discoveryReleaseCandidates(rawReleases,seeds,saved+albums.mapNotNull{savedAlbumKey(it.url)})}
        fun active()=NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("NERO Discovery",fontSize=21.sp,fontWeight=FontWeight.Bold)
            Text("Explore artistas, álbuns e lançamentos com base no seu gosto.",fontSize=12.sp,color=Color.LightGray)
            if(suggestions.isNotEmpty()){
                Text("Outros artistas encontrados no seu mix",fontWeight=FontWeight.Medium)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    items(suggestions,key={nativeArtistKey(it.artist)}){hit->
                        DiscoveryTile(hit.artist,"Pesquisar artista",hit.thumbnail,"Capa de faixa de "+hit.artist){if(active())onSearch(hit.artist)}
                    }
                }
            }
            OutlinedButton(enabled=!busy&&!releaseBusy&&seeds.isNotEmpty(),onClick={
                if(active()){
                    busy=true;albumError=false
                    scope.launch{
                        try{
                            val rows=mutableListOf<NativeCollection>()
                            for(artist in seeds){
                                if(!active())return@launch
                                try{rows+=NativeMusicCatalog.collections(artist,NativeCollectionKind.ALBUM).take(15)}
                                catch(e:CancellationException){throw e}catch(_:Exception){albumError=true}
                            }
                            if(!active())return@launch
                            if(rows.isNotEmpty()||!albumError)rawAlbums=rows
                            queried=true
                        }finally{busy=false}
                    }
                }
            }){Text(if(busy)"Consultando…" else if(queried)"Atualizar descobertas" else "Explorar meu gosto")}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(queried||albums.isNotEmpty()){
                Text("Álbuns para explorar",fontWeight=FontWeight.Medium)
                if(albumError)Text("Algumas consultas de álbuns ficaram indisponíveis.",fontSize=12.sp,color=Color.LightGray)
                if(albums.isEmpty()&&!busy&&!albumError)Text("Nenhum álbum novo para você nesta consulta.",fontSize=12.sp,color=Color.LightGray)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    items(albums,key={it.url}){album->DiscoveryTile(album.title,album.credit,album.artwork,"Capa de "+album.title){if(active())onAlbum(album)}}
                }
            }
            Text("Explorar lançamentos",fontWeight=FontWeight.Medium)
            Text("Busca de álbuns no mesmo catálogo musical do NERO. Os resultados podem incluir álbuns antigos; datas de lançamento não verificadas.",fontSize=11.sp,color=Color.LightGray)
            OutlinedButton(enabled=!releaseBusy&&!busy&&seeds.isNotEmpty(),onClick={
                if(active()){
                    releaseBusy=true;releaseError=false
                    scope.launch{
                        try{
                            val rows=mutableListOf<NativeCollection>()
                            val year=LocalDate.now().year
                            for(artist in seeds){
                                if(!active())return@launch
                                try{rows+=NativeMusicCatalog.collections(discoveryReleaseQuery(artist,year),NativeCollectionKind.ALBUM).take(15)}
                                catch(e:CancellationException){throw e}catch(_:Exception){releaseError=true}
                            }
                            if(!active())return@launch
                            if(rows.isNotEmpty()||!releaseError)rawReleases=rows
                            releaseQueried=true
                        }finally{releaseBusy=false}
                    }
                }
            }){Text(if(releaseBusy)"Consultando…" else if(releaseQueried)"Atualizar busca de lançamentos" else "Buscar lançamentos dos meus artistas")}
            if(releaseBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(releaseError)Text("Algumas consultas ficaram indisponíveis.",fontSize=12.sp,color=Color.LightGray)
            if(releaseQueried&&releases.isEmpty()&&!releaseBusy&&!releaseError)Text("Nenhum resultado adicional encontrado nesta busca.",fontSize=12.sp,color=Color.LightGray)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                items(releases,key={it.url}){album->DiscoveryTile(album.title,album.credit,album.artwork,"Capa de "+album.title){if(active())onAlbum(album)}}
            }
        }
    }
}
@Composable
private fun DiscoveryTile(title:String,subtitle:String,artwork:String?,description:String,onClick:()->Unit){
    Card(onClick=onClick,modifier=Modifier.width(136.dp)){
        Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            NeroArtwork(model=artwork,contentDescription=description,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
            Text(title,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(subtitle,fontSize=12.sp,color=Color.LightGray,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}
