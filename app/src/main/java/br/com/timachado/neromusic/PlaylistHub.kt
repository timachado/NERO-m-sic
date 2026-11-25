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
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
private data class HubPlaylist(val id:String,val title:String,val count:Int,val page:Int,val covers:List<String>,val local:List<Track> = emptyList())
@Composable
internal fun PlaylistHub(tracks:List<Track>,onlineFavoriteCount:Int,localFavoriteCount:Int,onAccount:()->Unit,onFavorites:()->Unit,onDownloads:()->Unit,onOpen:(Int,String?)->Unit,onSearch:()->Unit,onCreate:()->Unit){
    val context=LocalContext.current
    var lists by remember{mutableStateOf(emptyList<HubPlaylist>())}
    var failed by remember{mutableStateOf(false)}
    var loading by remember{mutableStateOf(true)}
    var searching by remember{mutableStateOf(false)}
    var term by remember{mutableStateOf("")}
    var alphabetic by remember{mutableStateOf(false)}
    var more by remember{mutableStateOf(false)}
    LaunchedEffect(tracks){
        loading=true
        val result=withContext(Dispatchers.IO){
            val index=tracks.associateBy{it.id}
            runCatching{
                onlineLists(context).asReversed().map{HubPlaylist(it.id,it.name,it.songs.size,7,it.songs.mapNotNull{song->song.coverUrl}.distinct().take(4))}+
                mixes(context).asReversed().map{HubPlaylist(it.id,it.name,it.ids.size,1,emptyList(),it.ids.mapNotNull{ id->index[id]}.take(4))}+
                readSaved(context).asReversed().map{HubPlaylist(it.id,it.name,it.items.size,6,emptyList())}
            }
        }
        lists=result.getOrDefault(emptyList());failed=result.isFailure;loading=false
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onAccount){Icon(Icons.Default.AccountCircle,"Minha conta",tint=Color(0xFF00E5FF),modifier=Modifier.size(34.dp))}
            Text("Sua biblioteca",fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            IconButton(onClick={searching=!searching;if(!searching)term=""}){Icon(Icons.Default.Search,"Buscar playlists")}
            IconButton(onClick=onCreate){Icon(Icons.Default.Add,"Criar playlist")}
        }
        Row(verticalAlignment=Alignment.CenterVertically){
            FilterChip(selected=false,onClick=onDownloads,label={Text("Downloads")},leadingIcon={Icon(Icons.Default.Download,null)})
            Spacer(Modifier.weight(1f))
            Box{
                IconButton(onClick={more=true}){Icon(Icons.Default.MoreVert,"Mais opções")}
                DropdownMenu(expanded=more,onDismissRequest={more=false}){
                    DropdownMenuItem(text={Text("Abrir compartilhamento")},onClick={more=false;onOpen(7,null)})
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
        val visible=lists.filter{it.title.contains(term,true)}.let{if(alphabetic)it.sortedBy{row->row.title.lowercase(java.util.Locale.ROOT)} else it}
        LazyColumn(Modifier.fillMaxWidth().weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=12.dp)){
            if("Músicas curtidas".contains(term,true))item{
                Row(Modifier.fillMaxWidth().clickable(onClick=onFavorites),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(66.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(Color(0xFF0066FF),Color(0xFF00E5FF),Color(0xFF39FF14)))),contentAlignment=Alignment.Center){Icon(Icons.Default.Favorite,null,tint=Color.White,modifier=Modifier.size(30.dp))}
                    Column(Modifier.weight(1f).padding(start=12.dp)){Text("Músicas curtidas",fontSize=17.sp,fontWeight=FontWeight.Medium);Text("Playlist · "+(onlineFavoriteCount+localFavoriteCount)+" músicas",fontSize=12.sp,color=Color.LightGray)}
                }
            }
            if("Downloads".contains(term,true))item{
                Row(Modifier.fillMaxWidth().clickable(onClick=onDownloads),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(66.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF12313A)),contentAlignment=Alignment.Center){Icon(Icons.Default.Download,null,tint=Color(0xFF39FF14),modifier=Modifier.size(30.dp))}
                    Column(Modifier.weight(1f).padding(start=12.dp)){Text("Downloads",fontSize=17.sp,fontWeight=FontWeight.Medium);Text("Ouvir offline",fontSize=12.sp,color=Color.LightGray)}
                }
            }
            items(visible,key={"${it.page}:${it.id}"}){row->
                Row(Modifier.fillMaxWidth().clickable{onOpen(row.page,row.id)},verticalAlignment=Alignment.CenterVertically){
                    if(row.local.isNotEmpty())PlaylistArtwork(row.local,Modifier.size(66.dp))
                    else HubCover(row.covers)
                    Column(Modifier.weight(1f).padding(start=12.dp)){
                        Text(row.title,fontSize=17.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("Playlist · "+row.count+" músicas"+if(row.page==6)" · Importada" else "",fontSize=12.sp,color=Color.LightGray)
                    }
                }
            }
            if(!loading&&!failed&&lists.isEmpty())item{
                Text("Suas playlists aparecem aqui.",color=Color.LightGray)
                TextButton(onClick=onCreate){Text("Criar minha primeira playlist")}
                TextButton(onClick={onOpen(6,null)}){Text("Importar minhas playlists")}
                TextButton(onClick=onSearch){Text("Encontrar músicas")}
            }
        }
    }
}
@Composable
private fun HubCover(covers:List<String>){
    Box(Modifier.size(66.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF12313A)),contentAlignment=Alignment.Center){
        if(covers.isEmpty())Icon(Icons.Default.QueueMusic,null,tint=Color(0xFF00E5FF))
        else if(covers.size<4)AsyncImage(model=covers.first(),contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Column(Modifier.fillMaxSize()){repeat(2){r->Row(Modifier.weight(1f)){repeat(2){c->AsyncImage(model=covers[r*2+c],contentDescription=null,modifier=Modifier.weight(1f).fillMaxHeight(),contentScale=ContentScale.Crop)}}}}
    }
}
