package br.com.timachado.neromusic

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class OfflineState(val saved:List<OpenTrack> = emptyList(),val active:OpenTrack?=null,val progress:Float=0f,val notice:String?=null,val batchTotal:Int=0,val batchDone:Int=0)
internal class OfflineManager(private val context:Context){
    private val _state=MutableStateFlow(OfflineState())
    val state=_state.asStateFlow()
    suspend fun reload(){
        try{val rows=downloaded(context);_state.update{it.copy(saved=rows)}}
        catch(e:CancellationException){throw e}catch(_:Exception){_state.update{it.copy(notice="Não foi possível consultar os downloads.")}}
    }
    suspend fun save(track:OpenTrack){
        if(_state.value.batchTotal>0)return
        saveOne(track)
    }
    suspend fun savePlaylist(tracks:List<OpenTrack>){
        if(_state.value.active!=null||_state.value.batchTotal>0)return
        val rows=tracks.filter(::allowed).distinctBy{it.id}
        if(rows.isEmpty())return
        _state.update{it.copy(batchTotal=rows.size,batchDone=0,notice=null)}
        try{
            for(track in rows){
                if(_state.value.saved.none{it==track})saveOne(track)
                _state.update{it.copy(batchDone=it.batchDone+1)}
            }
            val count=rows.count{track->_state.value.saved.any{it==track}}
            _state.update{it.copy(notice="$count de "+rows.size+" faixas disponíveis offline.")}
        }finally{_state.update{it.copy(batchTotal=0,batchDone=0)}}
    }
    private suspend fun saveOne(track:OpenTrack){
        if(!allowed(track)||_state.value.active!=null)return
        _state.update{it.copy(active=track,progress=0f,notice=null)}
        try{
            saveAudio(context,track){fraction->_state.update{it.copy(progress=fraction)}}
            val rows=downloaded(context)
            check(rows.any{it==track})
            _state.update{it.copy(saved=rows,notice="Música disponível offline.")}
        }catch(e:CancellationException){throw e}
        catch(_:Exception){_state.update{it.copy(notice="Falha ao baixar. Confira o espaço livre e a conexão.")}}
        finally{_state.update{it.copy(active=null,progress=0f)}}
    }
}
@Composable
internal fun OfflinePlayerButton(track:OpenTrack?,local:Boolean,state:OfflineState,onSave:(OpenTrack)->Unit,onOpen:()->Unit,onInfo:()->Unit,enabled:Boolean){
    val saved=track!=null&&state.saved.any{it==track}
    val active=track!=null&&state.active?.id==track.id
    val label=when{saved->"Baixada · ouvir offline";local->"Arquivo no aparelho";active->"Baixando "+(state.progress*100).toInt()+"%";else->"Baixar para ouvir offline"}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)){
        OutlinedButton(enabled=enabled&&!active&&((state.active==null&&state.batchTotal==0)||saved||track==null),onClick={
            if(saved)onOpen()else if(track!=null&&allowed(track))onSave(track)else onInfo()
        },modifier=Modifier.fillMaxWidth()){
            Icon(if(saved||local)Icons.Default.CheckCircle else Icons.Default.Download,null,tint=if(saved||local)Color(0xFF39FF14)else Color(0xFF00E5FF))
            Spacer(Modifier.width(8.dp));Text(label)
        }
        if(active)LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth())
        if(track!=null)state.notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
    }
}
@Composable
internal fun OfflineLibrary(state:OfflineState,canPlay:Boolean,onBrowse:()->Unit,onPlay:(List<MediaItem>,Int)->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    val rows=state.saved
    var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val visible=remember(rows,filter){
        val term=filter.trim()
        if(term.isEmpty()) rows else rows.filter{it.title.contains(term,ignoreCase=true)||it.artist.contains(term,ignoreCase=true)}
    }
    val queue=remember(visible){visible.map{it.item(Uri.fromFile(audioFile(context,it)))}.withOrigin(PlaybackOrigin("offline","Downloads",""))}
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=16.dp)){
        item{
            Text("Downloads",fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text(rows.size.toString()+" músicas disponíveis offline · "+String.format(java.util.Locale.forLanguageTag("pt-BR"),"%.1f MB",rows.sumOf{it.size}/1048576.0),color=Color.LightGray,fontSize=13.sp)
            if(rows.isNotEmpty()){
                OutlinedTextField(
                    value=filter,onValueChange={filter=it.take(100)},modifier=Modifier.fillMaxWidth(),
                    singleLine=true,placeholder={Text("Buscar nos downloads")},
                    leadingIcon={Icon(Icons.Default.Search,null)},
                    trailingIcon={if(filter.isNotEmpty())IconButton(onClick={filter=""}){Icon(Icons.Default.Close,"Limpar busca")}}
                )
                if(queue.isNotEmpty()){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                        Button(enabled=canPlay,onClick={onPlay(queue,0)},modifier=Modifier.weight(1f)){
                            Icon(Icons.Default.PlayArrow,null);Spacer(Modifier.width(4.dp));Text("Reproduzir")
                        }
                        OutlinedButton(enabled=canPlay,onClick={onPlay(queue.shuffled(),0)},modifier=Modifier.weight(1f)){
                            Icon(Icons.Default.Shuffle,null);Spacer(Modifier.width(4.dp));Text("Aleatório")
                        }
                    }
                }else Text("Nenhum download corresponde à busca.",color=Color.LightGray,fontSize=13.sp)
            }
            if(state.batchTotal>0)Text("Playlist: "+state.batchDone+" de "+state.batchTotal+" verificadas",color=Color.LightGray)
            state.active?.let{track->
                Text("Baixando "+track.title,maxLines=2)
                LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth())
                Text((state.progress*100).toInt().toString()+"%",color=Color.LightGray)
            }
            state.notice?.let{Text(it,color=Color.LightGray,fontSize=12.sp)}
            if(rows.isEmpty())Card(modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Icon(Icons.Default.DownloadForOffline,null,tint=Color(0xFF00E5FF),modifier=Modifier.size(42.dp))
                    Text("Leve suas músicas com você",fontSize=19.sp,fontWeight=FontWeight.Bold)
                    Text("Baixe músicas com licença aberta para ouvir sem internet. As faixas salvas aparecem aqui.",color=Color.LightGray)
                    Button(onClick=onBrowse){Text("Encontrar músicas para baixar")}
                }
            }else TextButton(onClick=onBrowse){Text("Encontrar mais músicas")}
        }
        items(visible,key={it.id}){track->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.CheckCircle,null,tint=Color(0xFF39FF14),modifier=Modifier.size(38.dp))
                Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                    Text(track.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                    Text(track.artist,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=Color.LightGray)
                    Text("Disponível offline · "+track.license+" · "+String.format(java.util.Locale.forLanguageTag("pt-BR"),"%.1f MB",track.size/1048576.0),fontSize=11.sp,color=Color(0xFF39FF14))
                }
                IconButton(enabled=canPlay,onClick={onPlay(queue,visible.indexOf(track))}){Icon(Icons.Default.PlayArrow,"Reproduzir",tint=Color(0xFF39FF14))}
            }
        }
    }
}
