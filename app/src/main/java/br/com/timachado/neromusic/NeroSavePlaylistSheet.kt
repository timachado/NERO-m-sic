package br.com.timachado.neromusic

import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal data class NeroSaveTargets(val favorites:List<OnlineRef>,val playlists:List<OnlineList>)

/** Additive, deterministic and idempotent. Existing references and unrelated lists are preserved. */
internal fun addNeroSaveTargets(current:NeroSaveTargets,incoming:List<OnlineRef>,ids:Set<String>,favorite:Boolean,created:OnlineList?):NeroSaveTargets{
    val refs=incoming.distinctBy{it.id}
    require(refs.isNotEmpty())
    require(ids.all{id->current.playlists.any{it.id==id}})
    val lists=current.playlists.map{row->
        if(row.id in ids || row.id==created?.id)row.copy(songs=(row.songs+refs).distinctBy{it.id})else row
    }.let{rows->
        if(created!=null&&rows.none{it.id==created.id})rows+created.copy(songs=refs)else rows
    }
    return NeroSaveTargets(if(favorite)(current.favorites+refs).distinctBy{it.id}else current.favorites,lists)
}

private fun readTargets(context:Context,uid:String?):NeroSaveTargets=
    if(uid==null)NeroSaveTargets(readOnlineFavorites(context),onlineLists(context))
    else NeroAccountLibraryStore.read(context,uid)?.pending?.let{NeroSaveTargets(it.favorites,it.playlists)}
        ?:NeroSaveTargets(emptyList(),emptyList())

@Composable
internal fun SaveOnlineDialog(songs:List<OnlineRef>,onClose:()->Unit){
    val incoming=remember(songs){songs.distinctBy{it.id}}
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val changes by NeroAccountSync.changes.collectAsState()
    val scope=rememberCoroutineScope()
    var loaded by remember(uid,ready){mutableStateOf<Result<NeroSaveTargets>?>(null)}
    var selected by remember(uid){mutableStateOf(emptySet<String>())}
    var favorite by remember(uid){mutableStateOf(false)}
    var created by remember(uid){mutableStateOf<OnlineList?>(null)}
    var creating by remember(uid){mutableStateOf(false)}
    var name by remember(uid){mutableStateOf("")}
    var busy by remember(uid){mutableStateOf(false)}
    var message by remember(uid){mutableStateOf<String?>(null)}
    var reload by remember(uid){mutableIntStateOf(0)}
    LaunchedEffect(uid,ready,changes,reload){
        if(ready){
            val owner=uid
            val result=withContext(Dispatchers.IO){runCatching{readTargets(context,owner)}}
            if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner)loaded=result
        }
    }
    val data=loaded?.getOrNull()
    val keys=remember(incoming){incoming.map{it.id}.toSet()}
    val liked=data?.favorites?.count{it.id in keys}?:0
    fun save(){
        if(busy||!ready||data==null||incoming.isEmpty())return
        val owner=uid;val ids=selected;val includeFavorite=favorite;val draft=created
        if(ids.isEmpty()&&!includeFavorite&&draft==null){onClose();return}
        busy=true;message=null
        scope.launch{
            try{
                require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner)
                if(owner!=null){
                    NeroAccountSync.addSaveDestinations(context,owner,incoming,ids,includeFavorite,draft)
                }else{
                    // Both guest stores are individually atomic. Retry is additive and uses the same draft ID.
                    val current=readTargets(context,null)
                    val next=addNeroSaveTargets(current,incoming,ids,includeFavorite,draft)
                    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==null)
                    if(next.playlists!=current.playlists)saveOnlineLists(context,next.playlists)
                    if(next.favorites!=current.favorites)saveOnlineFavorites(context,next.favorites)
                }
                if(NeroSupabase.activeUid.value==owner){
                    Toast.makeText(context,"Músicas salvas na biblioteca",Toast.LENGTH_SHORT).show()
                    onClose()
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){
                if(NeroSupabase.activeUid.value==owner){
                    message="Não foi possível concluir todos os salvamentos. Atualize ou tente novamente; referências existentes não serão duplicadas."
                    reload++
                }
            }finally{busy=false}
        }
    }
    Dialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false,decorFitsSystemWindows=false)){
        Surface(modifier=Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(start=16.dp,end=16.dp,top=12.dp,bottom=48.dp)){
                Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp,4.dp).background(MaterialTheme.colorScheme.outline,RoundedCornerShape(50)))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    TextButton(enabled=!busy,onClick=onClose){Text("Cancelar")}
                    TextButton(enabled=!busy&&ready&&data!=null,onClick={save()}){Text("Concluir",fontWeight=FontWeight.Bold)}
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text("Salvar em",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    TextButton(enabled=ready&&data!=null&&!busy,onClick={name=created?.name.orEmpty();creating=true}){Text("Nova playlist")}
                }
                Text(if(incoming.size==1)incoming.first().title+" · "+displayArtistName(incoming.first().artist) else "${incoming.size} músicas selecionadas",maxLines=2,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                if(busy||loaded==null)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(loaded?.isFailure==true){
                    Text("Não foi possível carregar suas playlists.",color=MaterialTheme.colorScheme.error)
                    TextButton(enabled=!busy,onClick={reload++}){Text("Tentar novamente")}
                }
                message?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=12.dp)){
                    if(data!=null){
                        item(key="favorites"){
                            SaveTargetRow("Músicas curtidas",data.favorites.size,liked==keys.size&&keys.isNotEmpty(),favorite,!busy&&ready,cover={FavoriteTargetCover()},onClick={favorite=!favorite})
                        }
                        items(data.playlists.asReversed(),key={"list:"+it.id}){row->
                            val already=keys.isNotEmpty()&&row.songs.map{it.id}.containsAll(keys)
                            SaveTargetRow(row.name,row.songs.size,already,row.id in selected,!busy&&ready,cover={SaveTargetCover(row.songs.mapNotNull{it.coverUrl}.distinct().take(4))},onClick={selected=if(row.id in selected)selected-row.id else selected+row.id})
                        }
                        created?.let{draft->
                            if(data.playlists.none{it.id==draft.id})item(key="draft"){
                                SaveTargetRow(draft.name,incoming.size,false,true,!busy&&ready,cover={SaveTargetCover(incoming.mapNotNull{it.coverUrl}.distinct().take(4))},onClick={created=null})
                            }
                        }
                        item(key="new"){
                            Row(Modifier.fillMaxWidth().clickable(enabled=ready&&!busy){name=created?.name.orEmpty();creating=true}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                                Box(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){Icon(Icons.Default.Add,null)}
                                Text("Nova playlist",modifier=Modifier.padding(start=12.dp),style=MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                val count=selected.size+(if(favorite)1 else 0)+(if(created!=null)1 else 0)
                if(count>0)Text("$count destino(s) selecionado(s)",modifier=Modifier.align(Alignment.CenterHorizontally).padding(bottom=6.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick={save()},enabled=ready&&data!=null&&!busy&&incoming.isNotEmpty(),modifier=Modifier.align(Alignment.CenterHorizontally).heightIn(min=48.dp)){
                    Text(if(busy)"Salvando…" else "Concluir",fontWeight=FontWeight.Bold)
                }
            }
        }
        if(creating)AlertDialog(onDismissRequest={creating=false},title={Text("Nova playlist")},text={OutlinedTextField(name,{name=it.take(80)},label={Text("Nome da playlist")},singleLine=true,modifier=Modifier.fillMaxWidth())},confirmButton={TextButton(enabled=name.isNotBlank()&&!busy,onClick={
            created=OnlineList(created?.id?:UUID.randomUUID().toString(),name.trim(),emptyList())
            creating=false
        }){Text("Criar")}},dismissButton={TextButton(onClick={creating=false}){Text("Cancelar")}})
    }
}
@Composable private fun SaveTargetRow(title:String,count:Int,already:Boolean,selected:Boolean,enabled:Boolean,cover:@Composable ()->Unit,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().clickable(enabled=enabled&&!already,onClick=onClick).padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
        cover()
        Column(Modifier.weight(1f).padding(horizontal=12.dp)){
            Text(title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(if(already)"Já adicionada · $count músicas" else if(count==1)"1 música" else "$count músicas",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(if(already||selected)Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,contentDescription=if(already)"Já salva" else if(selected)"Selecionada; toque para desmarcar" else "Selecionar "+title,tint=if(already||selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(30.dp))
    }
}
@Composable private fun FavoriteTargetCover(){
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondaryContainer,MaterialTheme.colorScheme.secondary,MaterialTheme.colorScheme.primary))),contentAlignment=Alignment.Center){
        Icon(Icons.Default.Favorite,null,tint=MaterialTheme.colorScheme.onPrimary,modifier=Modifier.size(28.dp))
    }
}
@Composable private fun SaveTargetCover(covers:List<String>){
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){
        if(covers.isEmpty())Icon(Icons.Default.QueueMusic,null,tint=MaterialTheme.colorScheme.secondary)
        else if(covers.size<4)NeroArtwork(model=covers.first(),contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Column(Modifier.fillMaxSize()){repeat(2){r->Row(Modifier.weight(1f)){repeat(2){c->NeroArtwork(model=covers[r*2+c],contentDescription=null,modifier=Modifier.weight(1f).fillMaxHeight(),contentScale=ContentScale.Crop)}}}}
    }
}
