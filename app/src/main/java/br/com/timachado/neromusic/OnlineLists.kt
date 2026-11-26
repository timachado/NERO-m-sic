package br.com.timachado.neromusic

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import android.util.AtomicFile
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class OnlineRef(val id:String,val title:String,val artist:String,val artworkReleaseId:String?=null,val openAudio:OpenTrack?=null){
    val coverUrl:String? get()=musicHitFromRef(this)?.thumbnail ?: artworkReleaseId?.takeIf{Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(it)}?.let{"https://coverartarchive.org/release/$it/front-250"}
}
internal data class OnlineList(val id:String,val name:String,val songs:List<OnlineRef>)
private fun listFile(context:Context)=AtomicFile(File(context.filesDir,"nero-online-lists.json"))
internal fun onlineLists(context:Context):List<OnlineList>{
    val file=listFile(context)
    if(!file.baseFile.exists())return emptyList()
    val array=JSONArray(file.readFully().toString(Charsets.UTF_8))
    return (0 until array.length()).map{i->
        val o=array.getJSONObject(i);val rows=o.getJSONArray("songs")
        OnlineList(o.getString("id"),o.getString("name"),(0 until rows.length()).map{j->
            val song=rows.getJSONObject(j);OnlineRef(song.getString("id"),song.getString("title"),song.getString("artist"),song.optString("artworkReleaseId").takeIf{it.isNotBlank()&&it!="null"},song.optJSONObject("openAudio")?.let{runCatching{decodeOpenTrack(it)}.getOrNull()}?.takeIf{allowed(it)&&song.getString("id")=="commons:"+it.id})
        })
    }
}
internal fun saveOnlineLists(context:Context,lists:List<OnlineList>){
    val data=JSONArray(lists.map{l->JSONObject().put("id",l.id).put("name",l.name).put("songs",JSONArray(l.songs.map{s->JSONObject().put("id",s.id).put("title",s.title).put("artist",s.artist).put("artworkReleaseId",s.artworkReleaseId).put("openAudio",s.openAudio?.json())}))})
    val bytes=data.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=4*1024*1024)
    val file=listFile(context);val out=file.startWrite()
    try{out.write(bytes);file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e}
}
@Composable
internal fun SaveOnlineDialog(songs:List<OnlineRef>,onClose:()->Unit){
    val incoming=remember(songs){songs.distinctBy{it.id}}
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val changes by NeroAccountSync.changes.collectAsState()
    val scope=rememberCoroutineScope()
    var loaded by remember(uid,ready){mutableStateOf(runCatching{if(ready)scopedOnlineLists(context,uid)else emptyList()})}
    LaunchedEffect(uid,ready,changes){if(ready)loaded=runCatching{scopedOnlineLists(context,uid)}}
    var name by remember{mutableStateOf("")}
    var message by remember{mutableStateOf<String?>(null)}
    fun add(list:OnlineList?){
        val owner=uid
        scope.launch{try{
            val current=scopedOnlineLists(context,owner)
            val next=if(list==null)current+OnlineList(UUID.randomUUID().toString(),name.trim(),incoming)
            else current.map{if(it.id==list.id)it.copy(songs=(it.songs+incoming).distinctBy{s->s.id})else it}
            saveScopedOnlineLists(context,owner,current,next)
            android.widget.Toast.makeText(context,"Referência salva na playlist",android.widget.Toast.LENGTH_SHORT).show()
            onClose()
        }catch(e:CancellationException){throw e}catch(_:Exception){message="Não foi possível salvar. Nenhuma lista foi substituída."}}
    }
    AlertDialog(onDismissRequest=onClose,title={Text("Adicionar à playlist")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(if(incoming.size==1)incoming.first().title+" · "+displayArtistName(incoming.first().artist) else "${incoming.size} gravações para adicionar",color=Color.LightGray)
            Text("Salva as referências musicais, não o áudio. Gravações já existentes não serão duplicadas.",fontSize=12.sp,color=Color.LightGray)
            if(loaded.isFailure)Text("Não foi possível carregar suas playlists.")
            else{
                LazyColumn(Modifier.heightIn(max=180.dp)){items(loaded.getOrDefault(emptyList()),key={it.id}){list->
                    TextButton(onClick={add(list)},modifier=Modifier.fillMaxWidth()){Text(list.name)}
                }}
                OutlinedTextField(name,{name=it.take(80)},label={Text("Nova playlist online")},singleLine=true)
            }
            message?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }},confirmButton={TextButton(enabled=ready&&loaded.isSuccess&&name.isNotBlank()&&incoming.isNotEmpty(),onClick={add(null)}){Text("Criar e adicionar")}},
        dismissButton={TextButton(onClick=onClose){Text("Cancelar")}})
}
@Composable
internal fun OnlineListsScreen(tracks:List<Track>,canPlay:Boolean,onPlay:(List<MediaItem>,Int,Boolean)->Unit,onSearch:(String)->Unit={},initialSelection:String?=null,createOnOpen:Boolean=false,onCreated:()->Unit={},offlineState:OfflineState=OfflineState(),onDownloadPlaylist:(List<OpenTrack>)->Unit={}){
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val changes by NeroAccountSync.changes.collectAsState()
    var lists by remember(uid,ready){mutableStateOf(runCatching{if(ready)scopedOnlineLists(context,uid)else emptyList()})}
    LaunchedEffect(uid,ready,changes){if(ready)lists=runCatching{scopedOnlineLists(context,uid)}}
    var selected by remember(uid,initialSelection){mutableStateOf(initialSelection)}
    var remove by remember(uid){mutableStateOf<OnlineRef?>(null)}
    var notice by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    var resolving by remember{mutableStateOf<String?>(null)}
    var sharing by remember(uid){mutableStateOf<SharedPlaylist?>(null)}
    var openShared by remember(uid){mutableStateOf(false)}
    var create by remember(uid){mutableStateOf(createOnOpen)}
    var newName by remember{mutableStateOf("")}
    if(create)AlertDialog(onDismissRequest={create=false;onCreated()},title={Text("Nova playlist")},text={OutlinedTextField(newName,{newName=it.take(80)},label={Text("Nome da playlist")},singleLine=true)},confirmButton={TextButton(enabled=ready&&newName.isNotBlank(),onClick={
        val owner=uid;scope.launch{try{val new=OnlineList(UUID.randomUUID().toString(),newName.trim(),emptyList());val current=scopedOnlineLists(context,owner);val next=current+new;saveScopedOnlineLists(context,owner,current,next);lists=Result.success(next);selected=new.id;newName="";create=false;onCreated()}
        catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível criar a playlist.";create=false;onCreated()}}
    }){Text("Criar")}},dismissButton={TextButton(onClick={create=false;onCreated()}){Text("Cancelar")}})
    sharing?.let{PlaylistShareDialog(it,onClose={sharing=null})}
    if(openShared)ReceiveShareDialog(onClose={openShared=false},
        onImport={incoming->
            val owner=uid;scope.launch{try{
                val current=scopedOnlineLists(context,owner)
                val next=current+OnlineList(UUID.randomUUID().toString(),incoming.name,sharedPlaylistRefs(incoming))
                saveScopedOnlineLists(context,owner,current,next);lists=Result.success(next);selected=next.last().id;openShared=false
            }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível importar a playlist.";openShared=false}}
        },
        onPlay=if(canPlay) { incoming:SharedPlaylist ->
            val queue=sharedPlaylistRefs(incoming).mapNotNull{if(NativeMusicCatalog.enabled)musicHitFromRef(it)?.queueItem() else null}
            if(queue.isNotEmpty()){onPlay(queue,0,false);openShared=false}
            else {val first=incoming.songs.first();openShared=false;onSearch(first.title+" "+first.artist)}
        } else null)
    val active=lists.getOrDefault(emptyList()).firstOrNull{it.id==selected}
    var suggestions by remember(active?.id){mutableStateOf(emptyList<NativeMusicHit>())}
    var suggestionsBusy by remember(active?.id){mutableStateOf(false)}
    var suggestionsNotice by remember(active?.id){mutableStateOf<String?>(null)}
    val hidden=rememberHiddenSongs()
    val visibleSuggestions=filterRecommendedSongs(suggestions,hidden).filter{hit->active?.songs?.none{it.id==hit.reference().id}==true}
    val saved=offlineState.saved
    val localIndex=remember(tracks){tracks.groupBy{it.title.trim().lowercase(java.util.Locale.ROOT) to it.artist.trim().lowercase(java.util.Locale.ROOT)}}
    val available=remember(active,localIndex,saved){
        active?.songs?.mapNotNull{s->
            val audio=s.openAudio?.takeIf{allowed(it)&&s.id=="commons:"+it.id}
            val local=if(s.id.startsWith("local:")){
                val id=s.id.removePrefix("local:").toLongOrNull()
                tracks.firstOrNull{it.id==id&&it.title==s.title&&it.artist==s.artist}
            }else if(s.artist.isBlank())null else localIndex[s.title.trim().lowercase(java.util.Locale.ROOT) to s.artist.trim().lowercase(java.util.Locale.ROOT)]?.singleOrNull()
            val media=if(audio!=null){
                val copy=saved.firstOrNull{it==audio}
                audio.item(if(copy!=null)Uri.fromFile(audioFile(context,copy))else Uri.parse(audio.url))
            }else if(NativeMusicCatalog.enabled&&musicHitFromRef(s)!=null)musicHitFromRef(s)?.queueItem() else local?.mediaItem()
            media?.let{s.id to it}
        }.orEmpty()
    }
    val queue=remember(available,active){available.map{it.second}.withOrigin(active?.let{PlaybackOrigin("playlist",it.name,it.id)})}
    val byId=remember(available){available.toMap()}
    BackHandler(active!=null){selected=null}
    fun moveSong(id:String,delta:Int){
        val list=active?:return
        val owner=uid;scope.launch{try{
            val current=scopedOnlineLists(context,owner)
            val live=current.firstOrNull{it.id==list.id}?:return@launch
            if(live.songs!=list.songs){lists=Result.success(current);notice="A playlist mudou. Tente novamente.";return@launch}
            val from=live.songs.indexOfFirst{it.id==id};val to=from+delta
            if(from<0||to !in live.songs.indices)return@launch
            val songs=live.songs.toMutableList();songs.add(to,songs.removeAt(from))
            val updated=current.map{if(it.id==live.id)it.copy(songs=songs)else it}
            saveScopedOnlineLists(context,owner,current,updated);lists=Result.success(updated);notice=null
        }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível reorganizar a playlist."}}
    }
    val pending=remove
    if(pending!=null&&active!=null)AlertDialog(onDismissRequest={remove=null},title={Text("Retirar referência?")},text={Text(pending.title+" será retirada desta playlist.")},
        confirmButton={TextButton(onClick={
            val owner=uid;scope.launch{try{val current=scopedOnlineLists(context,owner);val updated=current.map{if(it.id==active.id)it.copy(songs=it.songs.filterNot{s->s.id==pending.id})else it}
                saveScopedOnlineLists(context,owner,current,updated);lists=Result.success(updated)
            }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível atualizar a playlist."}
            remove=null}
        }){Text("Retirar")}},dismissButton={TextButton(onClick={remove=null}){Text("Cancelar")}})
    LazyColumn(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=12.dp)){
        item{
            if(active!=null)IconButton(onClick={selected=null}){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text(active?.name?:"Playlists online",fontSize=23.sp,fontWeight=FontWeight.Bold)
            Text("Referências musicais e áudios autorizados. As faixas disponíveis tocam no NERO.",fontSize=12.sp,color=Color.LightGray)
            if(active==null){TextButton(onClick={create=true}){Icon(Icons.Default.Add,null);Text("Criar playlist")};OutlinedButton(onClick={openShared=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.LockOpen,null);Spacer(Modifier.width(8.dp));Text("Abrir compartilhamento")}}
            if(active!=null){
                OutlinedButton(enabled=active.songs.isNotEmpty(),onClick={sharing=SharedPlaylist(active.name,active.songs.map{sharedSong(it)})},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("Compartilhar playlist")}
                Text("${queue.size} de ${active.songs.size} faixas disponíveis para reprodução interna.",fontSize=12.sp,color=Color.LightGray)
                if(NativeMusicCatalog.enabled&&active.songs.any{it.artist.trim().length>=3}){
                    OutlinedButton(enabled=!suggestionsBusy,onClick={
                        val artists=active.songs.map{NeroFollowedArtists.clean(it.artist)}.filter{it.length>=3}.distinctBy(::nativeArtistKey).take(3)
                        val existing=active.songs.map{it.id}.toSet()
                        suggestionsBusy=true;suggestionsNotice=null
                        scope.launch{
                            try{
                                suggestions=filterRecommendedSongs(artists.flatMap{NativeMusicCatalog.search(it).take(12)},NeroHiddenSongs.read(context))
                                    .filter{it.reference().id !in existing}.distinctBy{it.id}.take(12)
                                if(suggestions.isEmpty())suggestionsNotice="Nenhuma faixa nova encontrada."
                            }catch(e:CancellationException){throw e}catch(_:Exception){suggestionsNotice="Não foi possível buscar sugestões."}
                            finally{suggestionsBusy=false}
                        }
                    },modifier=Modifier.fillMaxWidth()){Text(if(suggestionsBusy)"Buscando sugestões…" else "Encontrar músicas para esta playlist")}
                    suggestionsNotice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
                }
                val eligible=active.songs.mapNotNull{ref->ref.openAudio?.takeIf{allowed(it)&&ref.id=="commons:"+it.id}}.distinctBy{it.id}
                val ready=eligible.count{audio->saved.any{it==audio}}
                val busy=offlineState.active!=null||offlineState.batchTotal>0
                if(eligible.isNotEmpty()){
                    OutlinedButton(enabled=!busy&&ready<eligible.size,onClick={onDownloadPlaylist(eligible)},modifier=Modifier.fillMaxWidth()){
                        Icon(if(ready==eligible.size)Icons.Default.CheckCircle else Icons.Default.Download,null,tint=Color(0xFF39FF14))
                        Spacer(Modifier.width(8.dp));Text(if(ready==eligible.size)"Faixas autorizadas baixadas" else "Baixar playlist ($ready/${eligible.size})")
                    }
                    if(offlineState.batchTotal>0)Text("Preparando downloads: "+offlineState.batchDone+"/"+offlineState.batchTotal,fontSize=12.sp,color=Color.LightGray)
                    offlineState.notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
                }
                if(eligible.size<active.songs.size)Text("Download disponível apenas para as faixas com autorização offline.",fontSize=11.sp,color=Color.LightGray)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(enabled=canPlay&&queue.isNotEmpty(),onClick={onPlay(queue,0,false)},modifier=Modifier.weight(1f)){Text("Reproduzir")}
                    OutlinedButton(enabled=canPlay&&queue.isNotEmpty(),onClick={onPlay(queue,queue.indices.random(),true)},modifier=Modifier.weight(1f)){Text("Aleatório")}
                }
                if(queue.size<active.songs.size)Text("As referências sem áudio disponível serão preservadas e não entram na fila.",fontSize=11.sp,color=Color.LightGray)
            }
            if(lists.isFailure)Text("Não foi possível ler as playlists.")
            if(active==null&&lists.getOrDefault(emptyList()).isEmpty())Text("Busque uma música e use o botão adicionar.")
            notice?.let{Text(it,color=Color.LightGray)}
        }
        if(active==null)items(lists.getOrDefault(emptyList()),key={it.id}){l->
            Card(onClick={selected=l.id},modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Default.QueueMusic,null,tint=Color(0xFF39FF14))
                    Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(l.name,fontWeight=FontWeight.Bold);Text("${l.songs.size} referências",fontSize=12.sp)}
                    Icon(Icons.Default.KeyboardArrowRight,"Abrir")
                }
            }
        }else items(active.songs,key={it.id}){s->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF12313A)),contentAlignment=Alignment.Center){
                    Icon(Icons.Default.MusicNote,null,tint=Color(0xFF00E5FF))
                    s.coverUrl?.let{NeroArtwork(model=it,contentDescription="Capa de "+s.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())}
                }
                Column(Modifier.weight(1f).padding(horizontal=8.dp)){
                    Text(s.title,fontWeight=FontWeight.Medium,color=nowTrackColor(byId[s.id]?.mediaId));Text(displayArtistName(s.artist),fontSize=12.sp,color=Color.LightGray);NowTrackStatus(byId[s.id]?.mediaId)
                    Text(if(s.id in byId)"Disponível no NERO" else if(NativeMusicCatalog.enabled&&musicHitFromRef(s)!=null)"NERO Music · online" else "Somente referência",fontSize=11.sp,color=if(s.id in byId)Color(0xFF39FF14)else Color.LightGray)
                    s.openAudio?.takeIf{allowed(it)}?.let{audio->
                        Text(audio.license,fontSize=11.sp,color=Color.LightGray)
                        TextButton(onClick={if(!openBrowser(context,Uri.parse(audio.source)))notice="Não foi possível abrir a fonte."},contentPadding=PaddingValues(0.dp)){Text("Fonte e licença",fontSize=12.sp)}
                    }
                }
                val position=available.indexOfFirst{it.first==s.id}
                if(position>=0)NowTrackAction(queue[position].mediaId,canPlay){onPlay(queue,position,false)}
                else IconButton(onClick={onSearch(s.title+" "+s.artist)}){Icon(Icons.Default.Search,"Encontrar faixa no NERO")}
                var options by remember(s.id){mutableStateOf(false)}
                Box{
                    IconButton(onClick={options=true}){Icon(Icons.Default.MoreVert,"Opções da música")}
                    DropdownMenu(expanded=options,onDismissRequest={options=false}){
                        val index=active.songs.indexOfFirst{it.id==s.id}
                        DropdownMenuItem(text={Text("Mover para cima")},enabled=index>0,onClick={options=false;moveSong(s.id,-1)})
                        DropdownMenuItem(text={Text("Mover para baixo")},enabled=index>=0&&index<active.songs.lastIndex,onClick={options=false;moveSong(s.id,1)})
                        DropdownMenuItem(text={Text("Remover da playlist")},onClick={options=false;remove=s})
                    }
                }
            }
        }
        if(active!=null&&visibleSuggestions.isNotEmpty()){
            item{Text("Sugestões para sua playlist",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Baseadas nos artistas da lista. Adicione apenas as que quiser.",fontSize=12.sp,color=Color.LightGray)}
            items(visibleSuggestions,key={"suggestion:"+it.id}){hit->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    NeroArtwork(model=hit.thumbnail,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f).padding(horizontal=8.dp)){Text(hit.title);Text(displayArtistName(hit.artist),fontSize=12.sp,color=Color.LightGray)}
                    IconButton(onClick={
                        val owner=uid;scope.launch{try{
                            val current=scopedOnlineLists(context,owner)
                            val live=current.firstOrNull{it.id==active.id}?:throw IllegalStateException()
                            val ref=hit.reference()
                            val updated=current.map{if(it.id==live.id)it.copy(songs=(it.songs+ref).distinctBy{song->song.id})else it}
                            saveScopedOnlineLists(context,owner,current,updated);lists=Result.success(updated)
                        }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível adicionar a sugestão."}}
                    }){Icon(Icons.Default.AddCircleOutline,"Adicionar sugestão",tint=Color(0xFF39FF14))}
                }
            }
        }
    }
}
