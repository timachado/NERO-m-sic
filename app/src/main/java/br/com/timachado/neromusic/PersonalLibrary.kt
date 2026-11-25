package br.com.timachado.neromusic

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.text.DateFormat
import java.util.Date

private val accent = Color(0xFF39FF14)
private val cardColor = Color(0xFF10171C)
internal data class Mix(val id:String,val name:String,val ids:List<Long>)
internal data class Heard(val id:String,val title:String,val artist:String,val at:Long)
private fun prefs(context:Context)=context.getSharedPreferences("nero_personal",Context.MODE_PRIVATE)
internal fun mixes(context:Context):List<Mix> = runCatching {
    val array=JSONArray(prefs(context).getString("mixes","[]") ?: "[]")
    (0 until array.length()).map { i ->
        val o=array.getJSONObject(i); val ids=o.getJSONArray("tracks")
        Mix(o.getString("id"),o.getString("name"),(0 until ids.length()).map { ids.getLong(it) }.distinct())
    }
}.getOrDefault(emptyList())
internal fun saveMixes(context:Context, list:List<Mix>) {
    val data=JSONArray()
    list.forEach { m -> data.put(JSONObject().put("id",m.id).put("name",m.name).put("tracks",JSONArray(m.ids))) }
    prefs(context).edit().putString("mixes",data.toString()).apply()
}
internal fun history(context:Context):List<Heard> = runCatching {
    val array=JSONArray(prefs(context).getString("history","[]") ?: "[]")
    (0 until array.length()).map { i ->
        val o=array.getJSONObject(i)
        Heard(o.getString("id"),o.getString("title"),o.optString("artist"),o.getLong("at"))
    }
}.getOrDefault(emptyList())
internal fun recordListening(context:Context,id:String,title:String,artist:String) {
    val records=(listOf(Heard(id,title,artist,System.currentTimeMillis()))+history(context).filterNot { it.id==id }).take(100)
    val data=JSONArray()
    records.forEach { h -> data.put(JSONObject().put("id",h.id).put("title",h.title).put("artist",h.artist).put("at",h.at)) }
    prefs(context).edit().putString("history",data.toString()).apply()
}

@Composable
internal fun PersonalLibraryScreen(historyMode:Boolean, tracks:List<Track>, granted:Boolean, canPlay:Boolean,
    onPermission:()->Unit, onPlay:(List<Track>,Int,PlaybackOrigin?)->Unit, onShuffle:(List<Track>,PlaybackOrigin?)->Unit,initialSelection:String?=null) {
    val context=LocalContext.current
    var playlists by remember { mutableStateOf(mixes(context)) }
    var recent by remember { mutableStateOf(history(context)) }
    var selectedId by remember(initialSelection) { mutableStateOf(initialSelection) }
    var showEditor by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var playlistFilter by remember { mutableStateOf("") }
    var sortByName by remember { mutableStateOf(false) }
    var sharing by remember{mutableStateOf<SharedPlaylist?>(null)}
    sharing?.let{PlaylistShareDialog(it,onClose={sharing=null})}
    val selected=playlists.firstOrNull { it.id==selectedId }
    val trackMap=remember(tracks) { tracks.associateBy { it.id } }
    fun persist(list:List<Mix>) { playlists=list;saveMixes(context,list) }
    fun update(mix:Mix) { persist(playlists.map { if(it.id==mix.id)mix else it }) }
    LaunchedEffect(historyMode) {
        if(historyMode) while(true) { recent=history(context);delay(1500) }
    }
    androidx.activity.compose.BackHandler(enabled=!historyMode && selected!=null) {
        if(adding) { adding=false;query="" } else selectedId=null
    }
    if(showEditor) {
        AlertDialog(onDismissRequest={showEditor=false},title={Text(if(selected==null)"Nova playlist" else "Renomear playlist")},
            text={OutlinedTextField(editName,{editName=it.take(80)},singleLine=true,label={Text("Nome")})},
            confirmButton={TextButton(enabled=editName.isNotBlank(),onClick={
                val name=editName.trim()
                if(selected==null) { val new=Mix(UUID.randomUUID().toString(),name,emptyList());persist(playlists+new);selectedId=new.id }
                else update(selected.copy(name=name))
                showEditor=false
            }){Text("Salvar")}},dismissButton={TextButton(onClick={showEditor=false}){Text("Cancelar")}})
    }
    if(deleteConfirm && selected!=null) AlertDialog(onDismissRequest={deleteConfirm=false},title={Text("Excluir playlist?")},
        text={Text("Os arquivos de música serão preservados.")},confirmButton={TextButton(onClick={
            persist(playlists.filterNot { it.id==selected.id });selectedId=null;adding=false;deleteConfirm=false
        }){Text("Excluir")}},dismissButton={TextButton(onClick={deleteConfirm=false}){Text("Cancelar")}})
    if(clearConfirm) AlertDialog(onDismissRequest={clearConfirm=false},title={Text("Limpar histórico?")},
        text={Text("O histórico e as estatísticas Replay serão limpos. As músicas e playlists não serão alteradas.")},confirmButton={TextButton(onClick={
            NeroReplay.clear(context);prefs(context).edit().remove("history").apply();recent=emptyList();clearConfirm=false
        }){Text("Limpar")}},dismissButton={TextButton(onClick={clearConfirm=false}){Text("Cancelar")}})

    LazyColumn(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=12.dp)) {
        if(historyMode) {
            item {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Ouvidas recentemente",fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    TextButton(enabled=recent.isNotEmpty(),onClick={clearConfirm=true}){Text("Limpar")}
                }
                Text("Histórico local das últimas 100 faixas distintas.",fontSize=12.sp,color=Color.LightGray)
            }
            if(recent.isEmpty()) item { Text("As músicas reproduzidas no NERO aparecerão aqui.",color=Color.LightGray) }
            if(!granted) item { TextButton(onClick=onPermission){Text("Permitir acesso às músicas locais")} }
            items(recent,key={it.id}) { h ->
                val track=h.id.toLongOrNull()?.let { trackMap[it] }
                Row(Modifier.fillMaxWidth().background(cardColor,RoundedCornerShape(12.dp)).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.History,null,tint=accent)
                    Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
                        Text(h.title,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(h.artist, fontSize=12.sp,color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(h.at)),fontSize=11.sp,color=Color.LightGray)
                    }
                    if(track!=null) IconButton(enabled=canPlay,onClick={onPlay(listOf(track),0,null)}){Icon(Icons.Default.PlayArrow,"Reproduzir novamente",tint=accent)}
                    else Text("Registro",fontSize=11.sp,color=Color.LightGray)
                }
            }
        } else if(selected==null) {
            item {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Minhas playlists",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    FilledTonalIconButton(onClick={editName="";showEditor=true}){Icon(Icons.Default.Add,"Nova playlist")}
                }
                Text("Organize suas músicas do aparelho.",fontSize=12.sp,color=Color.LightGray)
                OutlinedTextField(playlistFilter,{playlistFilter=it},singleLine=true,modifier=Modifier.fillMaxWidth(),placeholder={Text("Buscar playlists")},leadingIcon={Icon(Icons.Default.Search,null)})
                FilterChip(selected=sortByName,onClick={sortByName=!sortByName},label={Text(if(sortByName)"Ordem alfabética" else "Ordem de criação")})
            }
            if(playlists.isEmpty()) item { Text("Crie sua primeira playlist.",color=Color.LightGray) }
            val visible=playlists.filter{it.name.contains(playlistFilter,true)}.let{if(sortByName)it.sortedBy{mix->mix.name.lowercase(java.util.Locale.ROOT)} else it}
            if(playlists.isNotEmpty()&&visible.isEmpty())item{Text("Nenhuma playlist encontrada.",color=Color.LightGray)}
            items(visible,key={it.id}) { mix ->
                Card(onClick={selectedId=mix.id;adding=false;query=""},modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        PlaylistArtwork(mix.ids.mapNotNull{trackMap[it]},Modifier.size(64.dp))
                        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                            Text(mix.name,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(mix.ids.size.toString()+" músicas",fontSize=12.sp,color=Color.LightGray)
                        }
                        Icon(Icons.Default.KeyboardArrowRight,"Abrir")
                    }
                }
            }
        } else {
            val queue=selected.ids.mapNotNull { trackMap[it] }
            item {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={if(adding){adding=false;query=""}else selectedId=null}){Icon(Icons.Default.ArrowBack,"Voltar")}
                    Text(selected.name,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                    IconButton(onClick={editName=selected.name;showEditor=true}){Icon(Icons.Default.Edit,"Renomear")}
                    IconButton(onClick={deleteConfirm=true}){Icon(Icons.Default.Delete,"Excluir playlist")}
                }
                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){PlaylistArtwork(queue,Modifier.widthIn(max=210.dp).fillMaxWidth().aspectRatio(1f))}
                Text("${selected.ids.size} músicas · ${queue.size} disponíveis · "+playlistDuration(queue),color=Color.LightGray,fontSize=13.sp)
                OutlinedButton(enabled=queue.isNotEmpty(),onClick={sharing=SharedPlaylist(selected.name,queue.map{SharedSong(it.title,it.artist)})},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("Compartilhar playlist")}
                if(!granted) TextButton(onClick=onPermission){Text("Permitir acesso às músicas locais")}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(enabled=canPlay && queue.isNotEmpty(),onClick={onPlay(queue,0,PlaybackOrigin("local_playlist",selected.name,selected.id))},modifier=Modifier.weight(1f)){Icon(Icons.Default.PlayArrow,null);Text("Reproduzir")}
                    OutlinedButton(onClick={adding=!adding;query=""},enabled=granted,modifier=Modifier.weight(1f)){Text(if(adding)"Concluir" else "Adicionar")}
                }
                OutlinedButton(enabled=canPlay&&queue.isNotEmpty(),onClick={onShuffle(queue,PlaybackOrigin("local_playlist",selected.name,selected.id))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Shuffle,null);Spacer(Modifier.width(8.dp));Text("Reprodução aleatória")}
                if(adding) OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Filtrar músicas")})
                else if(queue.size<selected.ids.size) Text("Algumas faixas não estão acessíveis.",color=Color.LightGray,fontSize=12.sp)
            }
            if(adding) {
                val candidates=tracks.filter { it.id !in selected.ids && (it.title.contains(query,true)||it.artist.contains(query,true)) }
                if(candidates.isEmpty()) item { Text("Nenhuma faixa disponível para adicionar.",color=Color.LightGray) }
                items(candidates,key={it.id}) { t ->
                    TrackLine(t, Icons.Default.Add,"Adicionar à playlist") { update(selected.copy(ids=selected.ids+t.id)) }
                }
            } else {
                if(queue.isEmpty()) item { Text("Adicione músicas para começar.",color=Color.LightGray) }
                items(queue,key={it.id}) { t ->
                    Row(Modifier.fillMaxWidth().background(cardColor,RoundedCornerShape(12.dp)).padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        LocalArtwork(t,Modifier.size(44.dp))
                        IconButton(enabled=canPlay,onClick={onPlay(queue,queue.indexOf(t),PlaybackOrigin("local_playlist",selected.name,selected.id))}){Icon(Icons.Default.PlayArrow,"Reproduzir",tint=accent)}
                        Column(Modifier.weight(1f)) {
                            Text(t.title,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(t.artist,fontSize=12.sp,color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        IconButton(onClick={update(selected.copy(ids=selected.ids.filterNot { it==t.id }))}){Icon(Icons.Default.RemoveCircleOutline,"Retirar da playlist")}
                    }
                }
            }
        }
    }
}
@Composable
private fun TrackLine(t:Track,icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().background(cardColor,RoundedCornerShape(12.dp)).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
        LocalArtwork(t,Modifier.size(44.dp))
        Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
            Text(t.title,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(t.artist,fontSize=12.sp,color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        IconButton(onClick=onClick){Icon(icon,label,tint=accent)}
    }
}
