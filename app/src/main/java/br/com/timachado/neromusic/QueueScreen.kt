package br.com.timachado.neromusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController

private data class QueueSnapshot(val items:List<MediaItem>,val current:Int,val shuffle:Boolean,val canEdit:Boolean,val canSeek:Boolean)
private fun snapshot(p:MediaController?):QueueSnapshot {
    if(p==null||!p.isCommandAvailable(Player.COMMAND_GET_TIMELINE))
        return QueueSnapshot(emptyList(),-1,false,false,false)
    return QueueSnapshot((0 until p.mediaItemCount).map{p.getMediaItemAt(it)},p.currentMediaItemIndex,p.shuffleModeEnabled,
        p.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS),p.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM))
}
@Composable
internal fun QueueScreen(controller:MediaController?,tracks:List<Track>,onBack:()->Unit,modifier:Modifier=Modifier,sourceTitle:String?=null,onHome:(()->Unit)?=null){
    var queue by remember(controller){mutableStateOf(snapshot(controller))}
    var message by remember{mutableStateOf<String?>(null)}
    var confirmClear by remember{mutableStateOf<QueueSnapshot?>(null)}
    var saveItems by remember{mutableStateOf<List<MediaItem>?>(null)}
    val context=androidx.compose.ui.platform.LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val scope=rememberCoroutineScope()
    val listState=rememberLazyListState()
    LaunchedEffect(queue.current,queue.items.size){if(queue.current in queue.items.indices)listState.scrollToItem(queue.current)}
    val byId=remember(tracks){tracks.associateBy{it.id.toString()}}
    DisposableEffect(controller){
        val player=controller
        val listener=object:Player.Listener{
            override fun onEvents(p:Player,events:Player.Events){queue=snapshot(player)}
        }
        player?.addListener(listener)
        queue=snapshot(player)
        onDispose{player?.removeListener(listener)}
    }
    fun act(expected:QueueSnapshot,edit:Boolean,action:(MediaController)->Unit){
        val p=controller?:return
        val live=snapshot(p)
        if(live!=expected || (edit && (!live.canEdit||live.shuffle)) || (!edit&&!live.canSeek)){
            queue=live;message="A fila mudou ou o comando não está disponível. Tente novamente.";return
        }
        try{action(p);queue=snapshot(p);message=null}
        catch(_:Exception){queue=snapshot(p);message="Não foi possível alterar a fila."}
    }
    saveItems?.let{items->
        var name by remember(items,uid){mutableStateOf("")}
        val localTracks=items.map{byId[it.mediaId]}
        val localOnly=localTracks.all{it!=null}
        val refs=items.mapNotNull{item->
            openRefFromItem(item)?:youtubeRefFromItem(item)?:byId[item.mediaId]?.let{track->
                OnlineRef("local:"+track.id,track.title,track.artist)
            }
        }.distinctBy{it.id}
        val mixedAccount=uid!=null&&!localOnly&&refs.any{it.id.startsWith("local:")}
        val canSave=ready&&!mixedAccount&&(localOnly||items.all{item->openRefFromItem(item)!=null||youtubeRefFromItem(item)!=null||byId[item.mediaId]!=null})
        AlertDialog(onDismissRequest={saveItems=null},title={Text("Salvar fila como playlist")},text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Cria uma nova playlist com as músicas da fila, na ordem da lista, sem alterar a reprodução.")
                Text("Faixas repetidas serão salvas uma vez.",fontSize=12.sp,color=Color.LightGray)
                if(!canSave)Text(if(mixedAccount)"A fila combina arquivos locais e músicas online. Saia da conta para salvá-la na biblioteca do aparelho, ou separe as faixas." else "Há faixas sem referência compatível nesta fila.",color=MaterialTheme.colorScheme.error)
                OutlinedTextField(name,{name=it.take(80)},singleLine=true,label={Text("Nome da playlist")})
            }
        },confirmButton={TextButton(enabled=canSave&&name.isNotBlank(),onClick={
            val owner=uid;scope.launch{try{
                val id=java.util.UUID.randomUUID().toString()
                if(localOnly)saveMixes(context,mixes(context)+Mix(id,name.trim(),localTracks.mapNotNull{it?.id}.distinct()))
                else{
                    val current=scopedOnlineLists(context,owner)
                    saveScopedOnlineLists(context,owner,current,current+OnlineList(id,name.trim(),refs))
                }
                saveItems=null;message="Playlist criada. Encontre em Sua biblioteca."
            }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){message="Não foi possível salvar a playlist.";saveItems=null}}
        }){Text("Criar playlist")}},dismissButton={TextButton(onClick={saveItems=null}){Text("Cancelar")}})
    }
    val expectedClear=confirmClear
    if(expectedClear!=null){
        val expected=expectedClear
        AlertDialog(onDismissRequest={confirmClear=null},title={Text("Limpar próximas faixas?")},
            text={Text("Remove apenas as faixas depois da música atual. A reprodução atual e os arquivos permanecem intactos.")},
            confirmButton={TextButton(onClick={
                confirmClear=null
                act(expected,true){p->if(expected.current in expected.items.indices&&expected.current+1<expected.items.size)p.removeMediaItems(expected.current+1,expected.items.size)}
            }){Text("Limpar próximas")}},dismissButton={TextButton(onClick={confirmClear=null}){Text("Cancelar")}})
    }
    Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar ao player")}
            Text(sourceTitle?:"Fila de reprodução",fontSize=23.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            if(onHome!=null)IconButton(onClick=onHome){Icon(Icons.Default.Home,"Voltar ao início")}
        }
        Text("${queue.items.size} faixas · ordem da lista",color=Color.LightGray,fontSize=12.sp)
        queue.items.getOrNull(queue.current)?.let{item->
            val name=item.mediaMetadata.title?.toString().orEmpty().ifBlank{byId[item.mediaId]?.title.orEmpty()}
            val artist=item.mediaMetadata.artist?.toString().orEmpty().ifBlank{byId[item.mediaId]?.artist.orEmpty()}
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF102B23)),modifier=Modifier.fillMaxWidth(),onClick=onBack){
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Default.Equalizer,"Faixa atual",tint=Color(0xFF39FF14))
                    Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                        Text("Tocando agora",fontSize=12.sp,color=Color(0xFF39FF14))
                        Text(name.ifBlank{"Faixa atual"},fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(displayArtistName(artist),fontSize=12.sp,color=Color.LightGray)
                    }
                    Icon(Icons.Default.KeyboardArrowRight,"Abrir player")
                }
            }
        }
        if(queue.shuffle)Text("Modo aleatório ativo. Desative-o para editar a ordem das próximas faixas.",color=Color(0xFF00E5FF),fontSize=12.sp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(enabled=queue.items.isNotEmpty()&&queue.items.size<=1000,onClick={saveItems=queue.items.toList()},modifier=Modifier.weight(1f)){Text("Salvar fila")}
            OutlinedButton(enabled=queue.canEdit&&!queue.shuffle&&queue.current in queue.items.indices&&queue.current+1<queue.items.size,onClick={confirmClear=queue},modifier=Modifier.weight(1f)){Text("Limpar próximas")}
        }
        message?.let{Text(it,color=Color.LightGray,fontSize=12.sp)}
        if(queue.items.isEmpty())Text("A fila está vazia. Adicione músicas pela biblioteca.",color=Color.LightGray)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=listState,verticalArrangement=Arrangement.spacedBy(6.dp)){
            itemsIndexed(queue.items){i,item->
                val current=i==queue.current
                val upcoming=i>queue.current&&queue.current in queue.items.indices
                val editable=upcoming&&queue.canEdit&&!queue.shuffle
                val title=item.mediaMetadata.title?.toString()?.takeIf{it.isNotBlank()}?:"Faixa sem título"
                val artist=item.mediaMetadata.artist?.toString().orEmpty()
                var menu by remember(i,item){mutableStateOf(false)}
                Row(Modifier.fillMaxWidth().background(if(current)Color(0xFF102B23)else Color(0xFF10171C),RoundedCornerShape(10.dp)).padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                    val artwork=item.mediaMetadata.artworkUri
                    if(artwork!=null)NeroArtwork(model=artwork,contentDescription=null,modifier=Modifier.size(46.dp).clip(RoundedCornerShape(6.dp)),contentScale=ContentScale.Crop)
                    else LocalArtwork(byId[item.mediaId],Modifier.size(46.dp))
                    Column(Modifier.weight(1f).padding(horizontal=8.dp)){
                        Text(title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium,color=if(current)Color(0xFF39FF14)else Color.White)
                        Text(displayArtistName(artist),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=Color.LightGray)
                        Text(if(current)"Tocando agora" else if(upcoming)"Na sequência" else "Antes da faixa atual",fontSize=11.sp,color=if(current)Color(0xFF39FF14)else Color.LightGray)
                    }
                    IconButton(enabled=queue.canSeek,onClick={if(current)onBack()else act(queue,false){p->p.seekTo(i,0L);p.prepare();p.play()}}){Icon(if(current)Icons.Default.Equalizer else Icons.Default.PlayArrow,if(current)"Abrir player" else "Reproduzir "+title,tint=if(current)Color(0xFF39FF14)else Color.LightGray)}
                    if(upcoming)Box{
                        IconButton(enabled=editable,onClick={menu=true}){Icon(Icons.Default.MoreVert,"Opções da faixa")}
                        DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                            DropdownMenuItem(text={Text("Mover para cima")},enabled=editable&&i>queue.current+1,onClick={menu=false;act(queue,true){it.moveMediaItem(i,i-1)}})
                            DropdownMenuItem(text={Text("Mover para baixo")},enabled=editable&&i<queue.items.lastIndex,onClick={menu=false;act(queue,true){it.moveMediaItem(i,i+1)}})
                            DropdownMenuItem(text={Text("Remover da fila")},enabled=editable,onClick={menu=false;act(queue,true){it.removeMediaItem(i)}})
                        }
                    }
                }
            }
        }
    }
}
// Append/insert without replacing the current timeline or changing play intent.
internal fun enqueueNeroItem(controller:MediaController?,item:MediaItem,next:Boolean):String{
    val p=controller?:return "Player indisponível."
    val state=snapshot(p)
    if(!state.canEdit)return "Fila indisponível."
    if(next&&state.shuffle&&state.items.isNotEmpty())return "Desative o aleatório para reproduzir a seguir."
    return try{
        if(next&&state.current in state.items.indices)p.addMediaItem(state.current+1,item)
        else p.addMediaItem(item)
        if(state.items.isEmpty())p.prepare()
        if(next)"Adicionada para tocar a seguir." else "Adicionada à fila."
    }catch(_:Exception){"Não foi possível adicionar à fila."}
}
@Composable
internal fun QueueAddMenu(controller:MediaController?,track:Track){
    var open by remember{mutableStateOf(false)}
    var notice by remember{mutableStateOf<String?>(null)}
    val p=controller
    fun add(next:Boolean){
        val player=p?:return
        val state=snapshot(player)
        if(!state.canEdit){notice="Fila indisponível.";return}
        if(next&&state.shuffle){notice="Desative o modo aleatório para inserir como próxima faixa.";return}
        try{
            if(state.items.isEmpty())player.addMediaItem(track.mediaItem())
            else if(next&&state.current in state.items.indices)player.addMediaItem(state.current+1,track.mediaItem())
            else player.addMediaItem(track.mediaItem())
            if(state.items.isEmpty())player.prepare()
            notice="Faixa adicionada à fila."
        }catch(_:Exception){notice="Não foi possível adicionar a faixa."}
    }
    val context=androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(notice){notice?.let{android.widget.Toast.makeText(context,it,android.widget.Toast.LENGTH_SHORT).show();notice=null}}
    Box{
        IconButton(onClick={open=true},enabled=p!=null){Icon(Icons.Default.MoreVert,"Opções de "+track.title)}
        DropdownMenu(expanded=open,onDismissRequest={open=false}){
            DropdownMenuItem(text={Text("Reproduzir em seguida")},onClick={open=false;add(true)})
            DropdownMenuItem(text={Text("Adicionar ao fim da fila")},onClick={open=false;add(false)})
        }
    }
}
