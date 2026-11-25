package br.com.timachado.neromusic

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.util.UUID

private fun readLocalLists(context:Context):List<Mix>{
    val prefs=context.getSharedPreferences("nero_personal",Context.MODE_PRIVATE)
    val array=JSONArray(prefs.getString("mixes","[]") ?: "[]")
    return (0 until array.length()).map{i->
        val row=array.getJSONObject(i)
        val ids=row.getJSONArray("tracks")
        Mix(row.getString("id"),row.getString("name"),(0 until ids.length()).map{ids.getLong(it)}.distinct())
    }
}
@Composable
internal fun SaveLocalTrackDialog(track:Track,onClose:()->Unit){
    val context=LocalContext.current
    val loaded=remember(track.id){runCatching{readLocalLists(context)}}
    var name by remember(track.id){mutableStateOf("")}
    var error by remember(track.id){mutableStateOf<String?>(null)}
    fun save(id:String?){
        try{
            val current=readLocalLists(context)
            val updated=if(id==null){
                require(name.isNotBlank())
                current+Mix(UUID.randomUUID().toString(),name.trim(),listOf(track.id))
            }else{
                require(current.any{it.id==id})
                current.map{if(it.id==id)it.copy(ids=(it.ids+track.id).distinct())else it}
            }
            saveMixes(context,updated)
            onClose()
        }catch(_:Exception){error="Não foi possível salvar a playlist."}
    }
    AlertDialog(onDismissRequest=onClose,title={Text("Adicionar à playlist")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(track.title+" · "+track.artist)
            if(loaded.isFailure)Text("Não foi possível ler as playlists.")
            else{
                LazyColumn(Modifier.heightIn(max=180.dp)){items(loaded.getOrDefault(emptyList()),key={it.id}){mix->
                    TextButton(onClick={save(mix.id)},modifier=Modifier.fillMaxWidth()){Text(mix.name)}
                }}
                OutlinedTextField(name,{name=it.take(80)},singleLine=true,label={Text("Nova playlist")})
            }
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }},
        confirmButton={TextButton(enabled=loaded.isSuccess&&name.isNotBlank(),onClick={save(null)}){Text("Criar e adicionar")}},
        dismissButton={TextButton(onClick=onClose){Text("Cancelar")}})
}
