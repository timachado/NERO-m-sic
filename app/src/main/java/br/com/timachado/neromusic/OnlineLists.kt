package br.com.timachado.neromusic

import android.content.Context
import android.util.AtomicFile
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class OnlineRef(val id:String,val title:String,val artist:String)
internal data class OnlineList(val id:String,val name:String,val songs:List<OnlineRef>)
private fun listFile(context:Context)=AtomicFile(File(context.filesDir,"nero-online-lists.json"))
internal fun onlineLists(context:Context):List<OnlineList>{
    val file=listFile(context)
    if(!file.baseFile.exists())return emptyList()
    val array=JSONArray(file.readFully().toString(Charsets.UTF_8))
    return (0 until array.length()).map{i->
        val o=array.getJSONObject(i);val rows=o.getJSONArray("songs")
        OnlineList(o.getString("id"),o.getString("name"),(0 until rows.length()).map{j->
            val song=rows.getJSONObject(j);OnlineRef(song.getString("id"),song.getString("title"),song.getString("artist"))
        })
    }
}
private fun saveOnlineLists(context:Context,lists:List<OnlineList>){
    val data=JSONArray(lists.map{l->JSONObject().put("id",l.id).put("name",l.name).put("songs",JSONArray(l.songs.map{s->JSONObject().put("id",s.id).put("title",s.title).put("artist",s.artist)}))})
    val bytes=data.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=4*1024*1024)
    val file=listFile(context);val out=file.startWrite()
    try{out.write(bytes);file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e}
}
@Composable
internal fun SaveOnlineDialog(song:OnlineRef,onClose:()->Unit){
    val context=LocalContext.current
    var loaded by remember{mutableStateOf(runCatching{onlineLists(context)})}
    var name by remember{mutableStateOf("")}
    var message by remember{mutableStateOf<String?>(null)}
    fun add(list:OnlineList?){
        try{
            val current=onlineLists(context)
            val next=if(list==null)current+OnlineList(UUID.randomUUID().toString(),name.trim(),listOf(song))
            else current.map{if(it.id==list.id)it.copy(songs=(it.songs+song).distinctBy{s->s.id})else it}
            saveOnlineLists(context,next)
            android.widget.Toast.makeText(context,"Referência salva na playlist",android.widget.Toast.LENGTH_SHORT).show()
            onClose()
        }catch(_:Exception){message="Não foi possível salvar. Nenhuma lista foi substituída."}
    }
    AlertDialog(onDismissRequest=onClose,title={Text("Adicionar à playlist")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(song.title+" · "+song.artist,color=Color.LightGray)
            Text("Salva a referência musical, não o áudio.",fontSize=12.sp,color=Color.LightGray)
            if(loaded.isFailure)Text("Não foi possível carregar suas playlists.")
            else{
                LazyColumn(Modifier.heightIn(max=180.dp)){items(loaded.getOrDefault(emptyList()),key={it.id}){list->
                    TextButton(onClick={add(list)},modifier=Modifier.fillMaxWidth()){Text(list.name)}
                }}
                OutlinedTextField(name,{name=it.take(80)},label={Text("Nova playlist online")},singleLine=true)
            }
            message?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }},confirmButton={TextButton(enabled=loaded.isSuccess&&name.isNotBlank(),onClick={add(null)}){Text("Criar e adicionar")}},
        dismissButton={TextButton(onClick=onClose){Text("Cancelar")}})
}
@Composable
internal fun OnlineListsScreen(){
    val context=LocalContext.current
    var lists by remember{mutableStateOf(runCatching{onlineLists(context)})}
    var selected by remember{mutableStateOf<String?>(null)}
    var remove by remember{mutableStateOf<OnlineRef?>(null)}
    var notice by remember{mutableStateOf<String?>(null)}
    val active=lists.getOrDefault(emptyList()).firstOrNull{it.id==selected}
    BackHandler(active!=null){selected=null}
    val pending=remove
    if(pending!=null&&active!=null)AlertDialog(onDismissRequest={remove=null},title={Text("Retirar referência?")},text={Text(pending.title+" será retirada desta playlist.")},
        confirmButton={TextButton(onClick={
            try{val updated=onlineLists(context).map{if(it.id==active.id)it.copy(songs=it.songs.filterNot{s->s.id==pending.id})else it}
                saveOnlineLists(context,updated);lists=Result.success(updated)
            }catch(_:Exception){notice="Não foi possível atualizar a playlist."}
            remove=null
        }){Text("Retirar")}},dismissButton={TextButton(onClick={remove=null}){Text("Cancelar")}})
    LazyColumn(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=12.dp)){
        item{
            if(active!=null)IconButton(onClick={selected=null}){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text(active?.name?:"Playlists online",fontSize=23.sp,fontWeight=FontWeight.Bold)
            Text("Referências do catálogo, sem baixar áudio. A reprodução externa abre o YouTube Music oficial.",fontSize=12.sp,color=Color.LightGray)
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
                Column(Modifier.weight(1f)){Text(s.title,fontWeight=FontWeight.Medium);Text(s.artist,fontSize=12.sp,color=Color.LightGray)}
                IconButton(onClick={if(!openYouTubeMusic(context,s.title+" "+s.artist))notice="Não foi possível abrir o YouTube Music."}){Icon(Icons.Default.OpenInNew,"Buscar no YouTube Music")}
                IconButton(onClick={remove=s}){Icon(Icons.Default.RemoveCircleOutline,"Retirar referência")}
            }
        }
    }
}
