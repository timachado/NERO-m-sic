package br.com.timachado.neromusic

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import org.json.JSONArray
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

internal fun quickSaveOrder(rows:List<OnlineList>,recent:List<String>,query:String):List<OnlineList>{
    val q=query.trim()
    val rank=recent.distinct().withIndex().associate{it.value to it.index}
    return rows.asReversed().filter{it.name.contains(q,ignoreCase=true)}
        .sortedBy{rank[it.id]?:Int.MAX_VALUE}
}
internal fun quickSaveRecents(previous:List<String>,used:List<String>):List<String> =
    (used+previous).distinct().take(12)

private fun quickSavePrefs(context:Context,uid:String?)=context.getSharedPreferences(
    "nero_quick_save_"+(uid?.let{NeroAccountLibraryStore.fileName(it)}?:"guest"),Context.MODE_PRIVATE)
private fun readQuickSaveRecents(context:Context,uid:String?):List<String> = runCatching{
    val a=JSONArray(quickSavePrefs(context,uid).getString("recent","[]")?:"[]")
    (0 until minOf(a.length(),12)).mapNotNull{a.optString(it).takeIf{v->v.isNotBlank()}}
}.getOrDefault(emptyList())
private fun rememberQuickSaveTargets(context:Context,uid:String?,used:List<String>){
    if(used.isNotEmpty())quickSavePrefs(context,uid).edit().putString("recent",JSONArray(quickSaveRecents(readQuickSaveRecents(context,uid),used)).toString()).apply()
}

@Composable
internal fun SaveOnlineDialog(songs:List<OnlineRef>,onClose:()->Unit){
    val incoming=remember(songs){songs.distinctBy{it.id}}
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val changes by NeroAccountSync.changes.collectAsState()
    val scope=rememberCoroutineScope()
    val haptic=LocalHapticFeedback.current
    val focus=LocalFocusManager.current
    var query by remember(uid){mutableStateOf("")}
    val recent=remember(uid){readQuickSaveRecents(context,uid)}
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
    val visible=remember(data,recent,query){quickSaveOrder(data?.playlists.orEmpty(),recent,query)}
    val recentRows=visible.filter{it.id in recent}
    val otherRows=visible.filterNot{it.id in recent}
    val count=selected.size+(if(favorite)1 else 0)+(if(created!=null)1 else 0)
    fun feedback(){haptic.performHapticFeedback(HapticFeedbackType.LongPress)}
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
                    val used=(listOfNotNull(draft?.id)+data.playlists.filter{it.id in ids}.map{it.id}).distinct()
                    runCatching{rememberQuickSaveTargets(context,owner,used)}
                    val names=listOfNotNull(if(includeFavorite)"Músicas curtidas" else null,draft?.name)+data.playlists.filter{it.id in ids}.map{it.name}
                    val summary=if(names.size==1)"Salvo em "+names.first() else "Salvo em ${names.size} coleções"
                    Toast.makeText(context,summary,Toast.LENGTH_SHORT).show()
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
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(start=20.dp,end=20.dp,top=12.dp,bottom=48.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text("NERO QUICK SAVE",color=MaterialTheme.colorScheme.secondary,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    IconButton(enabled=!busy,onClick=onClose){Icon(Icons.Default.Close,"Cancelar")}
                }
                if(busy||loaded==null)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(loaded?.isFailure==true){
                    Text("Não foi possível carregar suas playlists.",color=MaterialTheme.colorScheme.error)
                    TextButton(enabled=!busy,onClick={reload++}){Text("Tentar novamente")}
                }
                message?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=12.dp)){
                    item(key="intro"){
                        Text("Seu som, suas coleções.",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                            SaveTargetCover(incoming.mapNotNull{it.coverUrl}.distinct().take(4))
                            Column(Modifier.weight(1f).padding(horizontal=12.dp)){
                                Text(if(incoming.size==1)incoming.first().title else "${incoming.size} músicas",style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text(if(incoming.size==1)displayArtistName(incoming.first().artist) else "Seleção para salvar",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.GraphicEq,null,tint=MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(query,{query=it.take(100)},modifier=Modifier.fillMaxWidth(),enabled=!busy,placeholder={Text("Buscar nas suas playlists")},leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}){Icon(Icons.Default.Close,"Limpar busca")}},singleLine=true,shape=RoundedCornerShape(28.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={focus.clearFocus()}))
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Text("Salvar em",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                            TextButton(enabled=ready&&data!=null&&!busy,onClick={name=created?.name.orEmpty();creating=true}){Text("+ Nova playlist")}
                        }
                    }
                    if(data!=null){
                        if("Músicas curtidas".contains(query.trim(),ignoreCase=true))item(key="favorites"){
                            SaveTargetRow("Músicas curtidas",data.favorites.size,liked==keys.size&&keys.isNotEmpty(),favorite,!busy&&ready,cover={FavoriteTargetCover()},onClick={feedback();favorite=!favorite})
                        }
                        if(recentRows.isNotEmpty())item(key="recent-title"){Text("USADAS RECENTEMENTE",color=MaterialTheme.colorScheme.secondary,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=6.dp))}
                        items(recentRows,key={"list:"+it.id}){row->
                            val already=keys.isNotEmpty()&&row.songs.map{it.id}.containsAll(keys)
                            SaveTargetRow(row.name,row.songs.size,already,row.id in selected,!busy&&ready,cover={SaveTargetCover(row.songs.mapNotNull{it.coverUrl}.distinct().take(4))},onClick={feedback();selected=if(row.id in selected)selected-row.id else selected+row.id})
                        }
                        if(otherRows.isNotEmpty())item(key="other-title"){Text("SUAS PLAYLISTS",color=MaterialTheme.colorScheme.secondary,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=6.dp))}
                        items(otherRows,key={"list:"+it.id}){row->
                            val already=keys.isNotEmpty()&&row.songs.map{it.id}.containsAll(keys)
                            SaveTargetRow(row.name,row.songs.size,already,row.id in selected,!busy&&ready,cover={SaveTargetCover(row.songs.mapNotNull{it.coverUrl}.distinct().take(4))},onClick={feedback();selected=if(row.id in selected)selected-row.id else selected+row.id})
                        }
                        if(query.isNotBlank()&&visible.isEmpty())item(key="empty"){Text("Nenhuma playlist encontrada.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)}
                        created?.let{draft->
                            if(data.playlists.none{it.id==draft.id})item(key="draft"){
                                SaveTargetRow(draft.name,incoming.size,false,true,!busy&&ready,cover={SaveTargetCover(incoming.mapNotNull{it.coverUrl}.distinct().take(4))},onClick={feedback();created=null})
                            }
                        }
                        item(key="new"){
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled=ready&&!busy){name=created?.name.orEmpty();creating=true}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                                Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){Icon(Icons.Default.Add,null,tint=MaterialTheme.colorScheme.secondary)}
                                Text("Nova playlist",modifier=Modifier.padding(start=12.dp),style=MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical=10.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                    if(count>0){Icon(Icons.Default.CheckCircle,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(16.dp));Spacer(Modifier.width(6.dp))}
                    Text(if(count==0)"Escolha onde salvar" else if(count==1)"1 coleção selecionada" else "$count coleções selecionadas",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick={focus.clearFocus();save()},enabled=ready&&data!=null&&!busy&&incoming.isNotEmpty()&&count>0,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                    Text(if(busy)"Salvando…" else "Salvar seleção",fontWeight=FontWeight.Bold)
                }
                Text("Suas músicas. Sua organização.",modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=8.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(creating)AlertDialog(onDismissRequest={creating=false},title={Text("Nova playlist")},text={OutlinedTextField(name,{name=it.take(80)},label={Text("Nome da playlist")},singleLine=true,modifier=Modifier.fillMaxWidth())},confirmButton={TextButton(enabled=name.isNotBlank()&&!busy,onClick={
            created=OnlineList(created?.id?:UUID.randomUUID().toString(),name.trim(),emptyList())
            query="";feedback();creating=false
        }){Text("Criar")}},dismissButton={TextButton(onClick={creating=false}){Text("Cancelar")}})
    }
}
@Composable private fun SaveTargetRow(title:String,count:Int,already:Boolean,selected:Boolean,enabled:Boolean,cover:@Composable ()->Unit,onClick:()->Unit){
    val highlight by animateColorAsState(if(selected&&!already)MaterialTheme.colorScheme.primary.copy(alpha=.10f) else Color.Transparent,label="selection background")
    val iconScale by animateFloatAsState(if(selected&&!already)1.10f else 1f,label="selection icon")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(highlight).toggleable(value=already||selected,enabled=enabled&&!already,role=Role.Checkbox,onValueChange={onClick()}).padding(horizontal=6.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
        cover()
        Column(Modifier.weight(1f).padding(horizontal=12.dp)){
            Text(title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(if(already)"Já adicionada · $count músicas" else if(selected)"Selecionada · $count músicas" else if(count==1)"1 música" else "$count músicas",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(if(already||selected)Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,contentDescription=if(already)"Já salva" else if(selected)"Selecionada; toque para desmarcar" else "Selecionar "+title,tint=if(already||selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(30.dp).scale(iconScale))
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
