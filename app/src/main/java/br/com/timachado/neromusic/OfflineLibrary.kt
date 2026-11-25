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

internal data class OfflineState(val saved:List<OpenTrack> = emptyList(),val active:OpenTrack?=null,val progress:Float=0f,val notice:String?=null)
internal class OfflineManager(private val context:Context){
    private val _state=MutableStateFlow(OfflineState())
    val state=_state.asStateFlow()
    suspend fun reload(){
        try{val rows=downloaded(context);_state.update{it.copy(saved=rows)}}
        catch(e:CancellationException){throw e}catch(_:Exception){_state.update{it.copy(notice="Não foi possível consultar os downloads.")}}
    }
    suspend fun save(track:OpenTrack){
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
        OutlinedButton(enabled=enabled&&!active&&(state.active==null||saved||track==null),onClick={
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
    val queue=remember(rows){rows.map{it.item(Uri.fromFile(audioFile(context,it)))}.withOrigin(PlaybackOrigin("offline","Downloads",""))}
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=16.dp)){
        item{
            Text("Downloads",fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text(rows.size.toString()+" músicas disponíveis offline · "+String.format(java.util.Locale.forLanguageTag("pt-BR"),"%.1f MB",rows.sumOf{it.size}/1048576.0),color=Color.LightGray,fontSize=13.sp)
            if(rows.isNotEmpty())Button(enabled=canPlay,onClick={onPlay(queue,0)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PlayArrow,null);Text("Reproduzir downloads")}
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
        items(rows,key={it.id}){track->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.CheckCircle,null,tint=Color(0xFF39FF14),modifier=Modifier.size(38.dp))
                Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                    Text(track.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                    Text(track.artist,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=Color.LightGray)
                    Text("Disponível offline · "+track.license,fontSize=11.sp,color=Color(0xFF39FF14))
                }
                IconButton(enabled=canPlay,onClick={onPlay(queue,rows.indexOf(track))}){Icon(Icons.Default.PlayArrow,"Reproduzir",tint=Color(0xFF39FF14))}
            }
        }
    }
}
