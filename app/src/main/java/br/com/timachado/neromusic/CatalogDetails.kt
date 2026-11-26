package br.com.timachado.neromusic

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException

internal data class AlbumEdition(val id:String,val title:String,val date:String,val country:String,val note:String){
    val label:String get()=listOf(title,date,country,note).filter{it.isNotBlank()}.joinToString(" · ")
}
internal data class AlbumSong(val disc:Int,val number:String,val title:String,val duration:Long,val recordingId:String?=null,val artist:String="")

@Composable
internal fun CatalogDetailsScreen(entry:CatalogEntry,onBack:()->Unit,onAlbum:(CatalogEntry)->Unit,onSearch:(String)->Unit){
    val isAlbum=entry.kind==CatalogKind.ALBUM
    var albums by remember{mutableStateOf(emptyList<CatalogEntry>())}
    var editions by remember{mutableStateOf(emptyList<AlbumEdition>())}
    var selected by remember{mutableStateOf<AlbumEdition?>(null)}
    var songs by remember{mutableStateOf(emptyList<AlbumSong>())}
    var busy by remember{mutableStateOf(true)}
    var trackBusy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    var reload by remember{mutableIntStateOf(0)}
    var adding by remember{mutableStateOf<List<OnlineRef>?>(null)}
    val editionId=selected?.id
    fun reference(song:AlbumSong):OnlineRef?=song.recordingId?.let{id->OnlineRef(id,song.title,song.artist.ifBlank{entry.credit},editionId)}
    adding?.let{SaveOnlineDialog(it,onClose={adding=null})}
    LaunchedEffect(entry.id,reload){
        busy=true;message=null
        try {
            if(isAlbum){
                editions=DevCatalog.editions(entry.id)
                if(editions.none{it.id==selected?.id}) selected=editions.firstOrNull()
            } else albums=DevCatalog.discography(entry.id)
        }catch(e:CancellationException){throw e}catch(_:Exception){message="Não foi possível consultar os detalhes."}
        finally{busy=false}
    }
    LaunchedEffect(selected?.id,reload){
        songs=emptyList()
        val edition=selected ?: return@LaunchedEffect
        trackBusy=true
        try{songs=DevCatalog.albumTracks(edition.id)}
        catch(e:CancellationException){throw e}catch(_:Exception){message="Não foi possível carregar as faixas."}
        finally{trackBusy=false}
    }
    LazyColumn(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=16.dp)){
        item {
            Row(verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
                Text(if(isAlbum)"Álbum" else "Artista",fontSize=21.sp,fontWeight=FontWeight.Bold)
            }
            if(isAlbum)Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF14242A)),contentAlignment=Alignment.Center){
                Icon(Icons.Default.Album,null,tint=Color(0xFF00E5FF),modifier=Modifier.size(64.dp))
                AsyncImage(model=entry.coverUrl,contentDescription="Capa de "+entry.title,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize())
            }
            Text(entry.title,fontSize=25.sp,fontWeight=FontWeight.Bold)
            if(entry.credit.isNotBlank())Text(entry.credit,color=Color.LightGray)
            Text("MusicBrainz · metadados de avaliação",fontSize=12.sp,color=Color.LightGray)
            OutlinedButton(onClick={onSearch(entry.searchText)},modifier=Modifier.fillMaxWidth()){Text("Encontrar músicas no NERO Music")}
        }
        if(busy||trackBusy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        message?.let{msg->item{Text(msg,color=Color(0xFF00E5FF));TextButton(onClick={reload++}){Text("Tentar novamente")}}}
        if(isAlbum){
            if(editions.isNotEmpty())item{
                Text("Edição",fontWeight=FontWeight.Bold)
                var expanded by remember{mutableStateOf(false)}
                Box{
                    OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text(selected?.label.orEmpty());Icon(Icons.Default.ArrowDropDown,null)}
                    DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
                        editions.forEach{edition->DropdownMenuItem(text={Text(edition.label)},onClick={selected=edition;expanded=false;message=null})}
                    }
                }
                Text("Faixas · "+songs.size,fontSize=19.sp,fontWeight=FontWeight.Bold)
                val references=songs.mapNotNull{reference(it)}
                OutlinedButton(enabled=!trackBusy&&references.isNotEmpty(),onClick={adding=references},modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Default.PlaylistAdd,null);Spacer(Modifier.width(6.dp));Text("Adicionar faixas à playlist")
                }
                if(!trackBusy&&references.size<songs.size)Text("Algumas faixas não possuem identificador de gravação.",fontSize=12.sp,color=Color.LightGray)
            }
            if(!busy&&!trackBusy&&songs.isEmpty())item{Text("Nenhuma faixa disponível nesta edição.",color=Color.LightGray)}
            items(songs){song->
                Row(Modifier.fillMaxWidth().background(Color(0xFF10171C),RoundedCornerShape(10.dp)).padding(9.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(if(song.disc>1)"${song.disc}.${song.number}" else song.number,color=Color.LightGray,modifier=Modifier.width(40.dp))
                    Column(Modifier.weight(1f)){
                        Text(song.title,fontWeight=FontWeight.Medium)
                        if(song.duration>0)Text(String.format("%d:%02d",song.duration/60000,(song.duration/1000)%60),fontSize=12.sp,color=Color.LightGray)
                    }
                    IconButton(enabled=song.recordingId!=null,onClick={reference(song)?.let{adding=listOf(it)}}){Icon(Icons.Default.PlaylistAdd,"Adicionar à playlist",tint=Color(0xFF39FF14))}
                    IconButton(onClick={onSearch(song.title+" "+song.artist.ifBlank{entry.credit})}){Icon(Icons.Default.Search,"Pesquisar faixa no NERO",tint=Color(0xFF00E5FF))}
                }
            }
        }else{
            item{Text("Discografia · álbuns, EPs e singles",fontSize=19.sp,fontWeight=FontWeight.Bold);Text("Até 50 resultados nesta prévia.",fontSize=12.sp,color=Color.LightGray)}
            if(!busy&&albums.isEmpty())item{Text("Nenhum lançamento encontrado.",color=Color.LightGray)}
            items(albums,key={it.id}){album->
                Card(onClick={onAlbum(album)},modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.padding(9.dp),verticalAlignment=Alignment.CenterVertically){
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF14242A)),contentAlignment=Alignment.Center){
                            Icon(Icons.Default.Album,null,tint=Color(0xFF00E5FF))
                            AsyncImage(model=album.coverUrl,contentDescription="Capa de "+album.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                        }
                        Text(album.title,modifier=Modifier.weight(1f).padding(horizontal=10.dp))
                        Icon(Icons.Default.KeyboardArrowRight,"Abrir álbum")
                    }
                }
            }
        }
    }
}
