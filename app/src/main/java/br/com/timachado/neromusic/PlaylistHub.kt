package br.com.timachado.neromusic
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
private data class HubPlaylist(val id:String,val title:String,val count:Int,val page:Int,val covers:List<String>,val local:List<Track> = emptyList(),val songIds:List<String> = emptyList(),val localSeconds:List<Long?> = emptyList())
@Composable
internal fun PlaylistHub(tracks:List<Track>,onlineFavoriteCount:Int,localFavoriteCount:Int,onAccount:()->Unit,onFavorites:()->Unit,onDownloads:()->Unit,onArtist:(String)->Unit={},onAlbum:(NativeCollection)->Unit={},onOpen:(Int,String?)->Unit,onSearch:()->Unit,onCreate:()->Unit){
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val changes by NeroAccountSync.changes.collectAsState()
    val pinPrefs=remember(context){context.getSharedPreferences("nero_playlist_pins",0)}
    var pins by remember(pinPrefs){mutableStateOf(pinPrefs.getStringSet("keys",emptySet()).orEmpty().toSet())}
    fun togglePin(row:HubPlaylist){
        val key="${row.page}:${row.id}"
        val current=pinPrefs.getStringSet("keys",emptySet()).orEmpty().toSet()
        val next=if(key in current)current-key else {if(current.size>=1000){android.widget.Toast.makeText(context,"Limite de playlists fixadas atingido.",android.widget.Toast.LENGTH_SHORT).show();return};current+key}
        pinPrefs.edit().putStringSet("keys",next).apply();pins=next
    }
    val followed=rememberFollowedArtists()
    val albums=rememberSavedAlbums()
    var artistFilter by remember{mutableStateOf(false)}
    var albumFilter by remember{mutableStateOf(false)}
    var lists by remember(uid,ready){mutableStateOf(emptyList<HubPlaylist>())}
    var failed by remember{mutableStateOf(false)}
    var loading by remember{mutableStateOf(true)}
    var searching by remember{mutableStateOf(false)}
    var term by remember{mutableStateOf("")}
    var alphabetic by remember{mutableStateOf(false)}
    var more by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    var deleteTarget by remember(uid,ready){mutableStateOf<HubPlaylist?>(null)}
    var deleting by remember(uid,ready){mutableStateOf(false)}
    var deleteNotice by remember(uid,ready){mutableStateOf<String?>(null)}
    var localRevision by remember{mutableIntStateOf(0)}
    deleteTarget?.let{target->
        AlertDialog(onDismissRequest={if(!deleting)deleteTarget=null},title={Text("Excluir playlist?")},
            text={Text("Excluir \""+target.title+"\"? Apenas a playlist será removida; músicas, favoritos e downloads serão preservados. "+if(target.page==7&&uid!=null)"A exclusão será sincronizada com sua conta." else "")},
            confirmButton={TextButton(enabled=ready&&!deleting,onClick={
                val owner=uid;deleting=true;deleteNotice=null
                scope.launch{
                    try{
                        require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner)
                        when(target.page){
                            7->deleteScopedOnlinePlaylist(context,owner,target.id)
                            1->{saveMixes(context,mixes(context).filterNot{it.id==target.id});localRevision++}
                            else->error("unsupported playlist")
                        }
                        if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner){
                            lists=lists.filterNot{it.page==target.page&&it.id==target.id}
                            val pinKey="${target.page}:${target.id}"
                            val nextPins=pinPrefs.getStringSet("keys",emptySet()).orEmpty().toSet()-pinKey
                            pinPrefs.edit().putStringSet("keys",nextPins).apply();pins=nextPins
                            localRevision++;deleteTarget=null;deleteNotice="Playlist excluída."
                        }
                    }catch(e:CancellationException){throw e}catch(_:Exception){if(NeroSupabase.activeUid.value==owner){deleteTarget=null;deleteNotice="Não foi possível excluir a playlist. Tente novamente."}}
                    finally{deleting=false}
                }
            }){Text(if(deleting)"Excluindo…" else "Excluir",color=MaterialTheme.colorScheme.error)}},
            dismissButton={TextButton(enabled=!deleting,onClick={deleteTarget=null}){Text("Cancelar")}})
    }
    LaunchedEffect(tracks,uid,ready,changes,localRevision){
        loading=true
        val result=withContext(Dispatchers.IO){
            val index=tracks.associateBy{it.id}
            runCatching{
                (if(ready)scopedOnlineLists(context,uid)else emptyList()).asReversed().map{HubPlaylist(it.id,it.name,it.songs.size,7,it.songs.mapNotNull{song->song.coverUrl}.distinct().take(4),songIds=it.songs.map{song->song.id})}+
                mixes(context).asReversed().map{HubPlaylist(it.id,it.name,it.ids.size,1,emptyList(),it.ids.mapNotNull{ id->index[id]}.take(4),localSeconds=it.ids.map{id->index[id]?.duration?.div(1000L)})}+
                readSaved(context).asReversed().map{HubPlaylist(it.id,it.name,it.items.size,6,emptyList())}
            }
        }
        lists=result.getOrDefault(emptyList());failed=result.isFailure;loading=false
    }
    val durations by NeroTrackDurations.durations.collectAsState()
    LoadPlaylistDurations(lists.flatMap{it.songIds},ready)
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            NeroHeaderProfile(onClick=onAccount)
            Text("Sua biblioteca",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
            IconButton(onClick={searching=!searching;if(!searching)term=""}){Icon(Icons.Default.Search,"Buscar playlists")}
            IconButton(onClick=onCreate){Icon(Icons.Default.Add,"Criar playlist")}
        }
        Row(verticalAlignment=Alignment.CenterVertically){
            FilterChip(selected=artistFilter,onClick={artistFilter=!artistFilter;albumFilter=false},label={Text("Artistas")})
            Spacer(Modifier.width(6.dp))
            FilterChip(selected=albumFilter,onClick={albumFilter=!albumFilter;artistFilter=false},label={Text("Álbuns")})
            Spacer(Modifier.width(6.dp))
            FilterChip(selected=false,onClick=onDownloads,label={Text("Downloads")},leadingIcon={Icon(Icons.Default.Download,null)})
            Spacer(Modifier.weight(1f))
            Box{
                IconButton(onClick={more=true}){Icon(Icons.Default.MoreVert,"Mais opções")}
                DropdownMenu(expanded=more,onDismissRequest={more=false}){
                    DropdownMenuItem(text={Text("Playlists NERO")},onClick={more=false;onOpen(7,null)})
                    DropdownMenuItem(text={Text("Importar do Spotify")},onClick={more=false;onOpen(6,null)})
                    DropdownMenuItem(text={Text("Músicas do aparelho")},onClick={more=false;onOpen(0,null)})
                    DropdownMenuItem(text={Text("Playlists locais")},onClick={more=false;onOpen(1,null)})
                    DropdownMenuItem(text={Text("Histórico")},onClick={more=false;onOpen(2,null)})
                    DropdownMenuItem(text={Text("Álbuns locais")},onClick={more=false;onOpen(3,null)})
                    DropdownMenuItem(text={Text("Artistas locais")},onClick={more=false;onOpen(4,null)})
                    DropdownMenuItem(text={Text("Gêneros locais")},onClick={more=false;onOpen(5,null)})
                }
            }
        }
        if(searching)OutlinedTextField(term,{term=it},singleLine=true,placeholder={Text("Buscar na sua biblioteca")},modifier=Modifier.fillMaxWidth())
        TextButton(onClick={alphabetic=!alphabetic}){Icon(Icons.Default.Sort,null);Spacer(Modifier.width(8.dp));Text(if(alphabetic)"Nome" else "Ordem de criação")}
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(failed)Text("Não foi possível carregar todas as playlists.",color=MaterialTheme.colorScheme.error)
        deleteNotice?.let{Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        val visible=lists.filter{it.title.contains(term,true)}.let{if(alphabetic)it.sortedBy{row->row.title.lowercase(java.util.Locale.ROOT)} else it}.sortedByDescending{"${it.page}:${it.id}" in pins}
        LazyColumn(Modifier.fillMaxWidth().weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=12.dp)){
            if(!artistFilter&&!albumFilter&&"Músicas curtidas".contains(term,true))item{
                Row(Modifier.fillMaxWidth().clickable(onClick=onFavorites),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(66.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondaryContainer ,MaterialTheme.colorScheme.secondary ,MaterialTheme.colorScheme.primary ))),contentAlignment=Alignment.Center){Icon(Icons.Default.Favorite,null,tint=Color.White,modifier=Modifier.size(30.dp))}
                    Column(Modifier.weight(1f).padding(start=12.dp)){Text("Músicas curtidas",fontSize=17.sp,fontWeight=FontWeight.Medium);Text("Playlist · "+(onlineFavoriteCount+localFavoriteCount)+" músicas",fontSize=12.sp,color=Color.LightGray)}
                }
            }
            if(!artistFilter&&!albumFilter&&"Downloads".contains(term,true))item{
                Row(Modifier.fillMaxWidth().clickable(onClick=onDownloads),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(66.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant ),contentAlignment=Alignment.Center){Icon(Icons.Default.Download,null,tint=MaterialTheme.colorScheme.primary ,modifier=Modifier.size(30.dp))}
                    Column(Modifier.weight(1f).padding(start=12.dp)){Text("Downloads",fontSize=17.sp,fontWeight=FontWeight.Medium);Text("Ouvir offline",fontSize=12.sp,color=Color.LightGray)}
                }
            }
            if(artistFilter){
                if(followed.isEmpty())item{Text("Siga seus artistas favoritos para encontrá-los aqui.",color=Color.LightGray)}
                items(followed.filter{it.contains(term,true)},key={"artist:"+nativeArtistKey(it)}){artist->
                    Row(Modifier.fillMaxWidth().clickable{onArtist(artist)}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                        Box(Modifier.size(66.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant ),contentAlignment=Alignment.Center){Icon(Icons.Default.Person,null,tint=MaterialTheme.colorScheme.primary ,modifier=Modifier.size(30.dp))}
                        Column(Modifier.weight(1f).padding(start=12.dp)){Text(displayArtistName(artist),fontSize=17.sp,fontWeight=FontWeight.Medium);Text("Artista · Seguindo",fontSize=12.sp,color=Color.LightGray)}
                    }
                }
            }
            if(albumFilter){
                if(albums.isEmpty())item{Text("Salve seus álbuns favoritos para encontrá-los aqui.",color=Color.LightGray)}
                val matching=albums.filter{it.title.contains(term,true)||it.credit.contains(term,true)}.let{if(alphabetic)it.sortedBy{album->album.title.lowercase(java.util.Locale.ROOT)}else it}
                items(matching,key={it.url}){album->
                    Row(Modifier.fillMaxWidth().clickable{onAlbum(album)}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                        HubCover(listOfNotNull(album.artwork))
                        Column(Modifier.weight(1f).padding(start=12.dp)){Text(album.title,fontSize=17.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis);Text("Álbum · "+album.credit,fontSize=12.sp,color=Color.LightGray)}
                    }
                }
            }
            if(!artistFilter&&!albumFilter)items(visible,key={"${it.page}:${it.id}"}){row->
                val pinned="${row.page}:${row.id}" in pins
                var options by remember(row.page,row.id){mutableStateOf(false)}
                Row(Modifier.fillMaxWidth().clickable{onOpen(row.page,row.id)},verticalAlignment=Alignment.CenterVertically){
                    if(row.local.isNotEmpty())PlaylistArtwork(row.local,Modifier.size(66.dp))
                    else HubCover(row.covers)
                    Column(Modifier.weight(1f).padding(start=12.dp)){
                        Text(row.title,fontSize=17.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text((if(pinned)"Fixada · " else "")+"Playlist · "+row.count+" músicas"+(playlistDurationLabel(if(row.page==1)row.localSeconds else row.songIds.map{durations[it]})?.let{" · "+it}?:"")+(if(row.page==6)" · Importada" else ""),fontSize=12.sp,color=if(pinned)MaterialTheme.colorScheme.primary else Color.LightGray)
                    }
                    Box{
                        IconButton(onClick={options=true}){Icon(Icons.Default.MoreVert,"Opções da playlist")}
                        DropdownMenu(expanded=options,onDismissRequest={options=false}){
                            DropdownMenuItem(text={Text(if(pinned)"Desafixar playlist" else "Fixar playlist")},onClick={options=false;togglePin(row)})
                            if(row.page==7||row.page==1)DropdownMenuItem(text={Text("Excluir playlist",color=MaterialTheme.colorScheme.error)},leadingIcon={Icon(Icons.Default.Delete,null,tint=MaterialTheme.colorScheme.error)},enabled=ready&&!deleting,onClick={options=false;deleteTarget=row})
                        }
                    }
                }
            }
            if(!artistFilter&&!albumFilter&&!loading&&!failed&&lists.isEmpty())item{
                Text("Suas playlists aparecem aqui.",color=Color.LightGray)
                TextButton(onClick=onCreate){Text("Criar minha primeira playlist")}
                TextButton(onClick={onOpen(6,null)}){Text("Importar do Spotify")}
                TextButton(onClick=onSearch){Text("Encontrar músicas")}
            }
        }
    }
}
@Composable
private fun HubCover(covers:List<String>){
    Box(Modifier.size(66.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant ),contentAlignment=Alignment.Center){
        if(covers.isEmpty())Icon(Icons.Default.QueueMusic,null,tint=MaterialTheme.colorScheme.secondary )
        else if(covers.size<4)NeroArtwork(model=covers.first(),contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Column(Modifier.fillMaxSize()){repeat(2){r->Row(Modifier.weight(1f)){repeat(2){c->NeroArtwork(model=covers[r*2+c],contentDescription=null,modifier=Modifier.weight(1f).fillMaxHeight(),contentScale=ContentScale.Crop)}}}}
    }
}
