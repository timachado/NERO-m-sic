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
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.text.style.TextOverflow
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
    val prefs=context.getSharedPreferences("nero_library",Context.MODE_PRIVATE)
    prefs.edit().putLong("online_playlist_revision",prefs.getLong("online_playlist_revision",0L)+1L).apply()
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
    var create by remember(uid){mutableStateOf(createOnOpen)}
    var newName by remember{mutableStateOf("")}
    if(create)AlertDialog(onDismissRequest={create=false;onCreated()},title={Text("Nova playlist")},text={OutlinedTextField(newName,{newName=it.take(80)},label={Text("Nome da playlist")},singleLine=true)},confirmButton={TextButton(enabled=ready&&newName.isNotBlank(),onClick={
        val owner=uid;scope.launch{try{val new=OnlineList(UUID.randomUUID().toString(),newName.trim(),emptyList());val current=scopedOnlineLists(context,owner);val next=current+new;saveScopedOnlineLists(context,owner,current,next);lists=Result.success(next);selected=new.id;newName="";create=false;onCreated()}
        catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível criar a playlist.";create=false;onCreated()}}
    }){Text("Criar")}},dismissButton={TextButton(onClick={create=false;onCreated()}){Text("Cancelar")}})
    sharing?.let{PlaylistShareDialog(it,onClose={sharing=null})}
    var importingShared by remember(uid){mutableStateOf(false)}
    val importShared=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null&&ready&&!importingShared){
            val owner=uid
            importingShared=true
            scope.launch{
                try{
                    val incoming=readSharedPlaylist(context,uri)
                    if(!NeroSupabase.identityReady.value||NeroSupabase.activeUid.value!=owner)return@launch
                    val current=scopedOnlineLists(context,owner)
                    val copy=OnlineList(UUID.randomUUID().toString(),incoming.name,sharedPlaylistRefs(incoming))
                    val next=importPortablePlaylists(current,listOf(copy))
                    saveScopedOnlineLists(context,owner,current,next)
                    if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner){lists=Result.success(next);notice="Playlist importada."}
                }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível importar este arquivo NERO."}
                finally{importingShared=false}
            }
        }
    }
    val active=lists.getOrDefault(emptyList()).firstOrNull{it.id==selected}
    var deleteTarget by remember(uid,ready){mutableStateOf<OnlineList?>(null)}
    var deleting by remember(uid,ready){mutableStateOf(false)}
    deleteTarget?.let{target->AlertDialog(onDismissRequest={if(!deleting)deleteTarget=null},title={Text("Excluir playlist?")},
        text={Text("Excluir \""+target.name+"\"? As músicas, favoritos e downloads serão preservados.")},
        confirmButton={TextButton(enabled=ready&&!deleting,onClick={
            val owner=uid;deleting=true
            scope.launch{
                try{
                    val next=deleteScopedOnlinePlaylist(context,owner,target.id)
                    if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner){lists=Result.success(next);if(selected==target.id)selected=null;deleteTarget=null;notice="Playlist excluída."}
                }catch(e:CancellationException){throw e}catch(_:Exception){if(NeroSupabase.activeUid.value==owner){deleteTarget=null;notice="Não foi possível excluir a playlist. Tente novamente."}}
                finally{deleting=false}
            }
        }){Text(if(deleting)"Excluindo…" else "Excluir",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(enabled=!deleting,onClick={deleteTarget=null}){Text("Cancelar")}})}
    var renameTarget by remember(uid,selected){mutableStateOf<String?>(null)}
    var renameName by remember(uid,selected){mutableStateOf("")}
    var renaming by remember(uid){mutableStateOf(false)}
    val renameId=renameTarget
    if(renameId!=null&&active?.id==renameId)AlertDialog(
        onDismissRequest={if(!renaming)renameTarget=null},
        title={Text("Renomear playlist")},
        text={OutlinedTextField(renameName,{renameName=it.take(100)},singleLine=true,label={Text("Nome da playlist")},modifier=Modifier.fillMaxWidth())},
        confirmButton={TextButton(enabled=ready&&!renaming&&renameName.isNotBlank(),onClick={
            val owner=uid;val name=renameName.trim();renaming=true
            scope.launch{
                try{
                    val current=scopedOnlineLists(context,owner)
                    val next=renameOnlinePlaylist(current,renameId,name)
                    saveScopedOnlineLists(context,owner,current,next)
                    if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner){
                        lists=Result.success(next);renameTarget=null;notice="Nome da playlist atualizado."
                    }
                }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível renomear a playlist."}
                finally{renaming=false}
            }
        }){Text("Salvar")}},dismissButton={TextButton(enabled=!renaming,onClick={renameTarget=null}){Text("Cancelar")}})

    val suggestionPrefs=remember(uid){context.getSharedPreferences("nero_playlist_suggestions_"+(uid?.let{NeroAccountLibraryStore.fileName(it)}?:"guest"),Context.MODE_PRIVATE)}
    var autoSuggestions by remember(uid){mutableStateOf(suggestionPrefs.getBoolean("auto",false))}
    var suggestions by remember(uid,ready,active?.id){mutableStateOf(emptyList<NativeMusicHit>())}
    var suggestionsBusy by remember(uid,ready,active?.id){mutableStateOf(false)}
    var suggestionsNotice by remember(uid,ready,active?.id){mutableStateOf<String?>(null)}
    var suggestionRequest by remember(uid,ready,active?.id){mutableIntStateOf(0)}
    var suggestionRound by remember(uid,ready,active?.id){mutableIntStateOf(0)}
    var shownSuggestions by remember(uid,ready,active?.id){mutableStateOf(emptySet<String>())}
    var suggestionPool by remember(uid,ready,active?.id){mutableStateOf(emptyList<List<NativeMusicHit>>())}
    var addingSuggestion by remember(uid,ready,active?.id){mutableStateOf<String?>(null)}
    val latestActive by rememberUpdatedState(active)
    val hidden=rememberHiddenSongs()
    val visibleSuggestions=filterRecommendedSongs(suggestions,hidden).filter{hit->active?.songs?.none{it.id==hit.reference().id}==true}
    val canSuggest=ready&&NativeMusicCatalog.enabled&&active?.songs?.any{it.artist.trim().length>=3}==true
    LaunchedEffect(uid,ready,active?.id,suggestionRequest){
        val playlist=active?:return@LaunchedEffect
        if(!canSuggest||(suggestionRequest==0&&!autoSuggestions))return@LaunchedEffect
        val owner=uid
        fun valid()=NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner&&latestActive?.id==playlist.id
        suggestionsBusy=true;suggestionsNotice=null
        try{
            if(!valid())return@LaunchedEffect
            val songs=latestActive?.songs?:return@LaunchedEffect
            val hiddenNow=scopedHiddenSongs(context,owner)
            var next=playlistSuggestionBatch(suggestionPool,songs,hiddenNow,shownSuggestions)
            if(next.size<6){
                val groups=mutableListOf<List<NativeMusicHit>>()
                val seeds=playlistSuggestionSeeds(songs,suggestionRound++)
                var failures=0
                for(artist in seeds){
                    if(!valid())return@LaunchedEffect
                    try{groups+=NativeMusicCatalog.search(artist).take(20)}
                    catch(e:CancellationException){throw e}catch(_:Exception){failures++}
                }
                if(!valid())return@LaunchedEffect
                suggestionPool=(suggestionPool+groups).takeLast(16)
                next=playlistSuggestionBatch(suggestionPool,latestActive?.songs.orEmpty(),scopedHiddenSongs(context,owner),shownSuggestions)
                if(failures>0)suggestionsNotice=if(failures==seeds.size)"Não foi possível consultar o catálogo." else "Algumas consultas ficaram indisponíveis."
            }
            if(!valid())return@LaunchedEffect
            if(next.isNotEmpty()){
                suggestions=next
                shownSuggestions=(shownSuggestions+next.map{it.id}).toList().takeLast(240).toSet()
            }else if(suggestionsNotice==null)suggestionsNotice="Nenhuma sugestão diferente disponível. Tente novamente mais tarde."
        }catch(e:CancellationException){throw e}catch(_:Exception){if(valid())suggestionsNotice="Não foi possível buscar recomendações."}
        finally{suggestionsBusy=false}
    }
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
            Text("Referências musicais e áudios autorizados. As faixas disponíveis tocam no NERO Music.",fontSize=12.sp,color=Color.LightGray)
            if(active==null){TextButton(onClick={create=true}){Icon(Icons.Default.Add,null);Text("Criar playlist")};OutlinedButton(enabled=ready&&!importingShared,onClick={importShared.launch(arrayOf("application/json","application/octet-stream","text/plain"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FileOpen,null);Spacer(Modifier.width(8.dp));Text(if(importingShared)"Importando…" else "Importar playlist NERO")}}
            if(active==null)NeroSmartLibrary(uid,ready,lists.getOrDefault(emptyList())){next->lists=Result.success(next);notice="Coleções inteligentes salvas."}
            if(active!=null){
                TextButton(enabled=ready&&!renaming,onClick={renameName=active.name;renameTarget=active.id}){Icon(Icons.Default.Edit,null);Spacer(Modifier.width(8.dp));Text("Renomear playlist")}
                TextButton(enabled=ready&&!deleting,onClick={deleteTarget=active}){Icon(Icons.Default.Delete,null,tint=MaterialTheme.colorScheme.error);Spacer(Modifier.width(8.dp));Text("Excluir playlist",color=MaterialTheme.colorScheme.error)}
                OutlinedButton(enabled=active.songs.isNotEmpty(),onClick={sharing=SharedPlaylist(active.name,active.songs.map{sharedSong(it)})},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("Compartilhar playlist")}
                Text("${queue.size} de ${active.songs.size} faixas disponíveis para reprodução interna.",fontSize=12.sp,color=Color.LightGray)
                val eligible=active.songs.mapNotNull{ref->ref.openAudio?.takeIf{allowed(it)&&ref.id=="commons:"+it.id}}.distinctBy{it.id}
                val ready=eligible.count{audio->saved.any{it==audio}}
                val busy=offlineState.active!=null||offlineState.batchTotal>0
                if(eligible.isNotEmpty()){
                    OutlinedButton(enabled=!busy&&ready<eligible.size,onClick={onDownloadPlaylist(eligible)},modifier=Modifier.fillMaxWidth()){
                        Icon(if(ready==eligible.size)Icons.Default.CheckCircle else Icons.Default.Download,null,tint=MaterialTheme.colorScheme.primary )
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
                    Icon(Icons.Default.QueueMusic,null,tint=MaterialTheme.colorScheme.primary )
                    Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(l.name,fontWeight=FontWeight.Bold);Text("${l.songs.size} referências",fontSize=12.sp)}
                    Icon(Icons.Default.KeyboardArrowRight,"Abrir")
                }
            }
        }else items(active.songs,key={it.id}){s->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant ),contentAlignment=Alignment.Center){
                    Icon(Icons.Default.MusicNote,null,tint=MaterialTheme.colorScheme.secondary )
                    s.coverUrl?.let{NeroArtwork(model=it,contentDescription="Capa de "+s.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())}
                }
                Column(Modifier.weight(1f).padding(horizontal=8.dp)){
                    Text(s.title,fontWeight=FontWeight.Medium,color=nowTrackColor(byId[s.id]?.mediaId));Text(displayArtistName(s.artist),fontSize=12.sp,color=Color.LightGray);NowTrackStatus(byId[s.id]?.mediaId)
                    Text(if(s.id in byId)"Disponível no NERO Music" else if(NativeMusicCatalog.enabled&&musicHitFromRef(s)!=null)"NERO Music · online" else "Somente referência",fontSize=11.sp,color=if(s.id in byId)MaterialTheme.colorScheme.primary else Color.LightGray)
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
        if(active!=null&&canSuggest){
            item(key="recommendation-header"){
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                Text("NERO PLAYLIST INTELLIGENCE",color=MaterialTheme.colorScheme.secondary,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
                Text("Músicas recomendadas",fontSize=22.sp,fontWeight=FontWeight.Bold)
                Text("Com base nas músicas desta playlist",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Toque na música para ouvir ou no + para adicionar.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("Sugestões automáticas",style=MaterialTheme.typography.bodyMedium)
                        Text("Consultar artistas da playlist ao abrir",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked=autoSuggestions,enabled=!suggestionsBusy,onCheckedChange={enabled->
                        if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid){
                            suggestionPrefs.edit().putBoolean("auto",enabled).apply();autoSuggestions=enabled
                            if(enabled&&suggestions.isEmpty())suggestionRequest++
                        }
                    })
                }
                if(suggestionsBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(visibleSuggestions.isEmpty()&&!suggestionsBusy&&suggestionsNotice==null)Text("Descubra outras faixas e artistas relacionados.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                suggestionsNotice?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            items(visibleSuggestions,key={"suggestion:"+it.id}){hit->
                val mediaId=hit.queueItem().mediaId
                val nowPlaying=LocalNeroNowPlaying.current
                fun playSuggestion(){
                    if(!canPlay||!NeroSupabase.identityReady.value||NeroSupabase.activeUid.value!=uid)return
                    if(nowPlaying.matches(mediaId)){nowPlaying.open();return}
                    val position=visibleSuggestions.indexOfFirst{it.id==hit.id}
                    if(position<0)return
                    val recommendedQueue=visibleSuggestions.map{it.queueItem()}.withOrigin(PlaybackOrigin("mix","Recomendações · "+active.name,""))
                    onPlay(recommendedQueue,position,false)
                }
                Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(enabled=canPlay,role=Role.Button,onClickLabel="Ouvir "+hit.title,onClick={playSuggestion()}).padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                        NeroArtwork(model=hit.thumbnail,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)))
                        Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                            Text(hit.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium,color=nowTrackColor(mediaId))
                            Text(displayArtistName(hit.artist),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            NowTrackStatus(mediaId)
                        }
                    }
                    IconButton(enabled=ready&&addingSuggestion==null,onClick={
                        val owner=uid;val target=active.id;addingSuggestion=hit.id
                        scope.launch{try{
                            require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner)
                            val current=scopedOnlineLists(context,owner)
                            val live=current.firstOrNull{it.id==target}?:throw IllegalStateException()
                            val ref=hit.reference()
                            val updated=current.map{if(it.id==live.id)it.copy(songs=(it.songs+ref).distinctBy{song->song.id})else it}
                            if(updated!=current)saveScopedOnlineLists(context,owner,current,updated)
                            if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner){
                                lists=Result.success(updated)
                                if(latestActive?.id==target)android.widget.Toast.makeText(context,"Adicionada à playlist",android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }catch(e:CancellationException){throw e}catch(_:Exception){if(NeroSupabase.activeUid.value==owner&&latestActive?.id==target)suggestionsNotice="Não foi possível adicionar a sugestão."}
                        finally{addingSuggestion=null}}
                    }){
                        if(addingSuggestion==hit.id)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
                        else Icon(Icons.Default.AddCircleOutline,"Adicionar "+hit.title,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(28.dp))
                    }
                }
            }
            item(key="recommendation-refresh"){
                Box(Modifier.fillMaxWidth().padding(vertical=12.dp),contentAlignment=Alignment.Center){
                    OutlinedButton(enabled=!suggestionsBusy&&addingSuggestion==null,onClick={suggestionRequest++},shape=RoundedCornerShape(28.dp)){
                        Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(8.dp))
                        Text(if(suggestionsBusy)"Buscando…" else if(suggestions.isEmpty())"Encontrar músicas" else "Atualizar")
                    }
                }
                Text("As consultas usam os nomes dos artistas desta playlist; não utilizam o seu histórico de reprodução.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
