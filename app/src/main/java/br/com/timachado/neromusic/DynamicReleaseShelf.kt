package br.com.timachado.neromusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Refresh
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun DynamicReleaseShelf(onOpen:()->Unit,onSearch:(String)->Unit){
    val lifecycle=(LocalContext.current as? LifecycleOwner)?.lifecycle
    var rows by remember{mutableStateOf(emptyList<CatalogEntry>())}
    var busy by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf(false)}
    var revision by remember{mutableIntStateOf(0)}
    LaunchedEffect(lifecycle,revision){
        var force=revision>0
        suspend fun refresh(){
            busy=true;error=false
            try{rows=DevCatalog.releaseSnapshot(30,null,force).entries.take(12)}
            catch(e:CancellationException){throw e}
            catch(_:Exception){error=true}
            finally{busy=false;force=false}
        }
        if(lifecycle==null)refresh()else lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
            while(true){refresh();delay(900000L)}
        }
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(5.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text("Descobertas musicais",fontSize=19.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            IconButton(enabled=!busy,onClick={revision++}){Icon(Icons.Default.Refresh,"Atualizar novidades")}
            TextButton(onClick=onOpen){Text("Ver tudo")}
        }
        Text("Sugestões do catálogo NERO Music · datas não verificadas",fontSize=12.sp,color=Color.LightGray)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error)Text(if(rows.isEmpty())"Consulta temporariamente indisponível." else "Mostrando a última consulta.",fontSize=12.sp,color=Color.LightGray)
        if(!busy&&!error&&rows.isEmpty())Text("Nenhuma novidade encontrada.",fontSize=12.sp,color=Color.LightGray)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            items(rows,key={it.id}){entry->
                Card(onClick={onSearch(entry.searchText)},modifier=Modifier.width(138.dp)){
                    Column(Modifier.padding(8.dp)){
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF12313A)),contentAlignment=Alignment.Center){
                            Icon(Icons.Default.Album,null,tint=Color(0xFF00E5FF))
                            AsyncImage(model=entry.coverUrl,contentDescription="Capa de "+entry.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                        }
                        Text(entry.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                        Text(entry.credit,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=Color.LightGray)
                        
                        Text("Pesquisar faixa/álbum",fontSize=11.sp,color=Color(0xFF39FF14))
                    }
                }
            }
        }
    }
}
