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

internal data class OfflineState(val saved:List<OpenTrack> = emptyList(),val active:OpenTrack?=null,val progress:Float=0f,val notice:String?=null,val batchTotal:Int=0,val batchDone:Int=0,val nativeSaved:List<NativeOfflineTrack> = emptyList(),val nativeActive:NativeMusicHit?=null,val wifiOnly:Boolean=true,val removing:Boolean=false)
internal class OfflineManager(private val context:Context){
    private val _state=MutableStateFlow(OfflineState(wifiOnly=wifiOnlyDownloads(context)))
    val state=_state.asStateFlow()
    fun setWifiOnly(value:Boolean){
        setWifiOnlyDownloads(context,value)
        _state.update{it.copy(wifiOnly=value,notice=null)}
    }
    fun notify(message:String){_state.update{it.copy(notice=message)}}
    suspend fun remove(id:String){
        if(_state.value.active!=null||_state.value.nativeActive!=null||_state.value.batchTotal>0||_state.value.removing)return
        _state.update{it.copy(removing=true,notice=null)}
        try{
            val native=_state.value.nativeSaved.firstOrNull{"youtube:"+it.hit.id==id}
            val open=_state.value.saved.firstOrNull{"commons:"+it.id==id}
            if(native!=null)removeNativeDownload(context,native.hit.id)
            else if(open!=null)removeOpenDownload(context,open)
            else return
            reload()
            _state.update{it.copy(notice="Download removido. Seus favoritos e playlists foram preservados.")}
        }catch(e:CancellationException){throw e}
        catch(_:Exception){reload();_state.update{it.copy(notice="Não foi possível remover o arquivo.")}}
        finally{_state.update{it.copy(removing=false)}}
    }
    suspend fun reload(){
        try{val rows=downloaded(context);val native=nativeOfflineTracks(context);_state.update{it.copy(saved=rows,nativeSaved=native)}}
        catch(e:CancellationException){throw e}catch(_:Exception){_state.update{it.copy(notice="Não foi possível consultar os downloads.")}}
    }
    suspend fun save(track:OpenTrack){
        if(_state.value.batchTotal>0||_state.value.nativeActive!=null||_state.value.removing)return
        saveOne(track)
    }
    suspend fun savePlaylist(tracks:List<OpenTrack>){
        if(_state.value.active!=null||_state.value.nativeActive!=null||_state.value.batchTotal>0||_state.value.removing)return
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
    suspend fun saveNative(hit:NativeMusicHit){
        if(_state.value.active!=null||_state.value.nativeActive!=null||_state.value.batchTotal>0||_state.value.removing)return
        _state.update{it.copy(nativeActive=hit,progress=0f,notice=null)}
        try{
            saveNativeAudio(context,hit){fraction->_state.update{it.copy(progress=fraction)}}
            val rows=nativeOfflineTracks(context)
            check(rows.any{it.hit.id==hit.id})
            _state.update{it.copy(nativeSaved=rows,notice="Música disponível offline.")}
        }catch(e:CancellationException){throw e}
        catch(_:WifiDownloadRequired){notify("Conecte-se ao Wi-Fi ou altere a preferência em Downloads.")}
        catch(_:Exception){_state.update{it.copy(notice="Não foi possível baixar esta música. Verifique a conexão e tente novamente.")}}
        finally{_state.update{it.copy(nativeActive=null,progress=0f)}}
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
        catch(_:WifiDownloadRequired){notify("Conecte-se ao Wi-Fi ou altere a preferência em Downloads.")}
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
        OutlinedButton(enabled=enabled&&!active&&((state.active==null&&state.nativeActive==null&&state.batchTotal==0)||saved||track==null),onClick={
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
internal fun NativeDownloadIcon(hit:NativeMusicHit,state:OfflineState,onSave:(NativeMusicHit)->Unit,onOpen:()->Unit){
    val saved=state.nativeSaved.any{it.hit.id==hit.id}
    val active=state.nativeActive?.id==hit.id
    val busy=state.nativeActive!=null||state.active!=null||state.batchTotal>0
    IconButton(enabled=saved||!busy,onClick={if(saved)onOpen()else onSave(hit)}){
        if(active)CircularProgressIndicator(modifier=Modifier.size(22.dp),strokeWidth=2.dp)
        else Icon(if(saved)Icons.Default.CheckCircle else Icons.Default.Download,if(saved)"Baixada · abrir downloads" else "Baixar música",tint=if(saved)Color(0xFF39FF14)else Color(0xFF00E5FF))
    }
}
@Composable
internal fun OfflineLibrary(state:OfflineState,canPlay:Boolean,onBrowse:()->Unit,onPlay:(List<MediaItem>,Int)->Unit,onWifiChange:(Boolean)->Unit={},onRemove:(String)->Unit={}){
    val context=androidx.compose.ui.platform.LocalContext.current
    val rows=state.saved
    var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val visible=remember(rows,filter){
        val term=filter.trim()
        if(term.isEmpty()) rows else rows.filter{it.title.contains(term,ignoreCase=true)||it.artist.contains(term,ignoreCase=true)}
    }
    val nativeVisible=remember(state.nativeSaved,filter){
        val term=filter.trim()
        state.nativeSaved.filter{term.isEmpty()||it.hit.title.contains(term,true)||it.hit.artist.contains(term,true)}
    }
    val queue=remember(visible,nativeVisible){(visible.map{it.item(Uri.fromFile(audioFile(context,it)))}+nativeVisible.map{it.item(context)}).withOrigin(PlaybackOrigin("offline","Downloads",""))}
    val total=rows.size+state.nativeSaved.size
    val canRemove=state.active==null&&state.nativeActive==null&&state.batchTotal==0&&!state.removing
    var pendingRemoval by remember{mutableStateOf<Pair<String,String>?>(null)}
    pendingRemoval?.let{entry->
        AlertDialog(onDismissRequest={pendingRemoval=null},title={Text("Remover download?")},
            text={Text(entry.second+" será removida do armazenamento offline, mas continuará nos seus favoritos e playlists.")},
            confirmButton={TextButton(enabled=canRemove,onClick={pendingRemoval=null;onRemove(entry.first)}){Text("Remover")}},
            dismissButton={TextButton(onClick={pendingRemoval=null}){Text("Cancelar")}})
    }
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=16.dp)){
        item{
            Text("Downloads",fontSize=25.sp,fontWeight=FontWeight.Bold)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("Baixar somente pelo Wi-Fi",fontWeight=FontWeight.Medium)
                    Text("Economize seus dados móveis",fontSize=12.sp,color=Color.LightGray)
                }
                Switch(checked=state.wifiOnly,onCheckedChange=onWifiChange)
            }
            Text(total.toString()+" músicas disponíveis offline · "+String.format(java.util.Locale.forLanguageTag("pt-BR"),"%.1f MB",(rows.sumOf{it.size}+state.nativeSaved.sumOf{it.size})/1048576.0),color=Color.LightGray,fontSize=13.sp)
            if(total>0){
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
            state.nativeActive?.let{hit->
                Text("Baixando "+hit.title,maxLines=2)
                LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth())
                Text((state.progress*100).toInt().toString()+"%",color=Color.LightGray)
            }
            state.active?.let{track->
                Text("Baixando "+track.title,maxLines=2)
                LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth())
                Text((state.progress*100).toInt().toString()+"%",color=Color.LightGray)
            }
            state.notice?.let{Text(it,color=Color.LightGray,fontSize=12.sp)}
            if(total==0)Card(modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Icon(Icons.Default.DownloadForOffline,null,tint=Color(0xFF00E5FF),modifier=Modifier.size(42.dp))
                    Text("Leve suas músicas com você",fontSize=19.sp,fontWeight=FontWeight.Bold)
                    Text("Baixe músicas para ouvir sem internet. As faixas salvas aparecem aqui.",color=Color.LightGray)
                    Button(onClick=onBrowse){Text("Encontrar músicas para baixar")}
                }
            }else TextButton(onClick=onBrowse){Text("Encontrar mais músicas")}
        }
        items(nativeVisible,key={"native:"+it.hit.id}){entry->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.CheckCircle,null,tint=Color(0xFF39FF14),modifier=Modifier.size(38.dp))
                Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                    Text(entry.hit.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                    Text(entry.hit.artist,fontSize=12.sp,color=Color.LightGray)
                    Text("Disponível offline · "+String.format(java.util.Locale.forLanguageTag("pt-BR"),"%.1f MB",entry.size/1048576.0),fontSize=11.sp,color=Color(0xFF39FF14))
                }
                IconButton(enabled=canRemove,onClick={pendingRemoval=("youtube:"+entry.hit.id) to entry.hit.title}){Icon(Icons.Default.DeleteOutline,"Remover download")}
                IconButton(enabled=canPlay,onClick={onPlay(queue,visible.size+nativeVisible.indexOf(entry))}){Icon(Icons.Default.PlayArrow,"Reproduzir",tint=Color(0xFF39FF14))}
            }
        }
        items(visible,key={it.id}){track->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.CheckCircle,null,tint=Color(0xFF39FF14),modifier=Modifier.size(38.dp))
                Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                    Text(track.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                    Text(track.artist,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=Color.LightGray)
                    Text("Disponível offline · "+track.license+" · "+String.format(java.util.Locale.forLanguageTag("pt-BR"),"%.1f MB",track.size/1048576.0),fontSize=11.sp,color=Color(0xFF39FF14))
                }
                IconButton(enabled=canRemove,onClick={pendingRemoval=("commons:"+track.id) to track.title}){Icon(Icons.Default.DeleteOutline,"Remover download")}
                IconButton(enabled=canPlay,onClick={onPlay(queue,visible.indexOf(track))}){Icon(Icons.Default.PlayArrow,"Reproduzir",tint=Color(0xFF39FF14))}
            }
        }
    }
}
