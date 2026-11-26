package br.com.timachado.neromusic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable
internal fun RecentReleasesScreen(onBack:()->Unit,onSearch:(String)->Unit){
    val genres=neroReleaseGenreOptions
    val days=30
    var genre by remember{mutableStateOf<String?>(null)}
    var reload by remember{mutableIntStateOf(0)}
    var busy by remember{mutableStateOf(true)}
    var failed by remember{mutableStateOf(false)}
    var results by remember(days,genre){mutableStateOf(emptyList<CatalogEntry>())}
    var fetchedAt by remember(days,genre){mutableLongStateOf(0L)}
    val lifecycle=(LocalContext.current as? LifecycleOwner)?.lifecycle
    var selected by remember{mutableStateOf<CatalogEntry?>(null)}
    BackHandler(selected!=null){selected=null}
    LaunchedEffect(days,genre,reload,lifecycle){
        var force=reload>0
        suspend fun update(){
            busy=true;failed=false
            try{val snapshot=DevCatalog.releaseSnapshot(days,genre,force);results=snapshot.entries;fetchedAt=snapshot.fetchedAt}
            catch(e:CancellationException){throw e}
            catch(_:Exception){failed=true}
            finally{busy=false;force=false}
        }
        if(lifecycle==null){update()}else lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
            while(true){update();delay(900000L)}
        }
    }
    selected?.let{entry->
        key(entry.id){CatalogDetailsScreen(entry,onBack={selected=null},onAlbum={selected=it},onSearch=onSearch)}
        return
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text("Descobrir álbuns",fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            IconButton(enabled=!busy,onClick={reload++}){Icon(Icons.Default.Refresh,"Atualizar lançamentos")}
        }
        Text("Sugestões do catálogo musical. Datas de lançamento não verificadas.",fontSize=12.sp,color=Color.LightGray)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            items(genres){(label,tag)->FilterChip(selected=genre==tag,onClick={genre=tag},label={Text(label)})}
        }
        if(fetchedAt>0L)Text("Última consulta: "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(fetchedAt)),fontSize=11.sp,color=Color.LightGray)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(failed){
            Text(if(results.isEmpty())"Não foi possível consultar os lançamentos." else "Não foi possível atualizar. Exibindo a última consulta.",color=Color.LightGray)
            TextButton(onClick={reload++}){Text("Tentar novamente")}
        }else if(!busy&&results.isEmpty())Text("Nenhum lançamento encontrado com esses filtros.",color=Color.LightGray)
        else if(!busy)Text("${results.size} resultados da amostra (até 30), ",fontSize=12.sp,color=Color.LightGray)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=12.dp)){
            items(results.chunked(2)){pair->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    pair.forEach{entry->
                        Card(onClick={selected=entry},modifier=Modifier.weight(1f)){
                            Column(Modifier.padding(8.dp)){
                                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF12313A)),contentAlignment=Alignment.Center){
                                    Icon(Icons.Default.Album,null,tint=Color(0xFF00E5FF),modifier=Modifier.size(40.dp))
                                    AsyncImage(model=entry.coverUrl,contentDescription="Capa de "+entry.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                                }
                                Text(entry.title,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text(entry.credit,fontSize=12.sp,color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis)
                                
                            }
                        }
                    }
                    if(pair.size==1)Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
