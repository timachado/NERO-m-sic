package br.com.timachado.neromusic

import android.content.Context
import android.net.Uri
import android.content.Intent
import android.util.AtomicFile
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Locale

private const val MAX_BYTES=12*1024*1024
private data class ImportedItem(val title:String,val artist:String,val album:String,val uri:String,val kind:String){
    fun json()=JSONObject().put("title",title).put("artist",artist).put("album",album).put("uri",uri).put("kind",kind)
}
private data class ImportedList(val id:String,val name:String,val items:List<ImportedItem>){
    fun json()=JSONObject().put("id",id).put("name",name).put("items",JSONArray(items.map{it.json()}))
}
private fun store(context:Context)=AtomicFile(File(context.filesDir,"nero-imported-playlists.json"))
private fun readSaved(context:Context):List<ImportedList>{
    val file=store(context)
    if(!file.baseFile.exists())return emptyList()
    val data=JSONArray(file.readFully().toString(Charsets.UTF_8))
    return (0 until data.length()).map { i ->
        val list=data.getJSONObject(i);val rows=list.getJSONArray("items")
        ImportedList(list.getString("id"),list.getString("name"),(0 until rows.length()).map{j->
            val o=rows.getJSONObject(j)
            ImportedItem(o.getString("title"),o.optString("artist"),o.optString("album"),o.optString("uri"),o.optString("kind"))
        })
    }
}
private fun parseExport(text:String):List<ImportedList>{
    val lists=JSONObject(text.removePrefix("\uFEFF")).getJSONArray("playlists")
    require(lists.length()<=1000)
    var count=0
    return (0 until lists.length()).map { i ->
        val o=lists.getJSONObject(i)
        val name=o.optString("name").ifBlank{"Playlist sem nome"}
        val rows=o.getJSONArray("items")
        count+=rows.length();require(count<=50000)
        val entries=(0 until rows.length()).map { j ->
            val item=rows.optJSONObject(j) ?: JSONObject()
            val t=item.optJSONObject("track")
            val e=item.optJSONObject("episode")
            when {
                t!=null -> ImportedItem(t.optString("trackName").ifBlank{"Faixa sem título"},t.optString("artistName"),t.optString("albumName"),t.optString("trackUri"),"track")
                e!=null -> ImportedItem(e.optString("episodeName").ifBlank{"Episódio sem título"},e.optString("showName"),"",e.optString("episodeUri"),"episode")
                !item.isNull("localTrack") -> ImportedItem(item.optString("localTrack").ifBlank{"Arquivo local"},"","","","local")
                else -> ImportedItem("Item indisponível na exportação","","","","unknown")
            }
        }
        val canonical=JSONObject().put("name",name).put("items",JSONArray(entries.map{it.json()})).toString()
        val hash=MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
        ImportedList(hash,name,entries)
    }.distinctBy{it.id}
}
private suspend fun importFile(context:Context,uri:Uri):List<ImportedList> = withContext(Dispatchers.IO){
    val bytes=context.contentResolver.openInputStream(uri)?.use{input ->
        val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=MAX_BYTES);out.write(buffer,0,n)}
        out.toByteArray()
    }?:error("Arquivo indisponível")
    parseExport(bytes.toString(Charsets.UTF_8))
}
private suspend fun saveImport(context:Context,incoming:List<ImportedList>):List<ImportedList> = withContext(Dispatchers.IO){
    val merged=(readSaved(context)+incoming).distinctBy{it.id}
    val bytes=JSONArray(merged.map{it.json()}).toString().toByteArray(Charsets.UTF_8)
    require(bytes.size<=MAX_BYTES)
    val file=store(context);val output=file.startWrite()
    try{output.write(bytes);file.finishWrite(output)}catch(e:Exception){file.failWrite(output);throw e}
    merged
}
private fun norm(s:String)=s.trim().lowercase(Locale.ROOT)
private fun match(item:ImportedItem,index:Map<Pair<String,String>,List<Track>>):Track?{
    if(item.kind!="track"||item.artist.isBlank())return null
    val candidates=index[norm(item.title) to norm(item.artist)].orEmpty()
    val exact=if(item.album.isBlank())candidates else candidates.filter{norm(it.album)==norm(item.album)}
    return exact.singleOrNull()
}
private fun spotifyLink(uri:String):String?{
    val parts=uri.split(":")
    if(parts.size==3&&parts[0]=="spotify"&&parts[1] in setOf("track","episode")&&Regex("[A-Za-z0-9]{22}").matches(parts[2]))
        return "https://open.spotify.com/"+parts[1]+"/"+parts[2]
    return null
}
@Composable
internal fun TransferScreen(tracks:List<Track>,canPlay:Boolean,onPlay:(Track)->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var lists by remember{mutableStateOf(emptyList<ImportedList>())}
    var pending by remember{mutableStateOf<List<ImportedList>?>(null)}
    var selected by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(true)}
    var notice by remember{mutableStateOf<String?>(null)}
    val index=remember(tracks){tracks.groupBy{norm(it.title) to norm(it.artist)}}
    val current=lists.firstOrNull{it.id==selected}
    BackHandler(current!=null){selected=null}
    LaunchedEffect(Unit){
        try{lists=withContext(Dispatchers.IO){readSaved(context)}}
        catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível ler as playlists salvas."}
        finally{busy=false}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        if(uris.isNotEmpty())scope.launch{
            busy=true;notice=null
            try{
                require(uris.size<=25)
                val imported=mutableListOf<ImportedList>()
                for(uri in uris){imported.addAll(importFile(context,uri));require(imported.sumOf{it.items.size}<=50000)}
                pending=imported.distinctBy{it.id}
            }catch(e:CancellationException){throw e}catch(_:Exception){notice="Importação não concluída. Selecione os JSON de playlists extraídos do pacote oficial Spotify (até 12 MB por arquivo)."}
            finally{busy=false}
        }
    }
    pending?.let{preview->
        AlertDialog(onDismissRequest={pending=null},title={Text("Importar playlists?")},
            text={Text("${preview.size} playlists e ${preview.sumOf{it.items.size}} itens. A ordem e os itens sem correspondência serão preservados. Nenhuma música será baixada nem nenhuma playlist existente será excluída.")},
            confirmButton={TextButton(enabled=!busy,onClick={
                pending=null
                scope.launch{
                    busy=true;notice=null
                    try{lists=saveImport(context,preview);notice="Playlists importadas. Confira as correspondências locais."}
                    catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível salvar a importação. Os dados anteriores foram preservados."}
                    finally{busy=false}
                }
            }){Text("Importar")}},dismissButton={TextButton(onClick={pending=null}){Text("Cancelar")}})
    }
    LazyColumn(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)){
        item{
            if(current!=null)IconButton(onClick={selected=null}){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text(current?.name ?: "NERO Transfer",fontSize=24.sp,fontWeight=FontWeight.Bold)
            Text("Playlists importadas do Spotify · metadados locais",color=Color(0xFF00E5FF),fontSize=12.sp)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let{Text(it,color=Color.LightGray)}
            if(current==null){
                Text("Solicite seus dados na página de privacidade do Spotify, extraia o ZIP recebido e selecione os arquivos Playlist*.json. Não é necessário fornecer senha ou token.",color=Color.LightGray,fontSize=13.sp)
                OutlinedButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.spotify.com/account/privacy/")))}},modifier=Modifier.fillMaxWidth()){Text("Solicitar dados ao Spotify")}
                Button(onClick={picker.launch(arrayOf("application/json","text/plain","application/octet-stream"))},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Importar arquivos JSON")}
            }else{
                val matched=current.items.count{match(it,index)!=null}
                Text("${current.items.size} itens · $matched disponíveis na biblioteca local",color=Color.LightGray)
                Text("As faixas sem correspondência permanecem na lista. Importar não transfere o áudio nem a assinatura Spotify.",color=Color.LightGray,fontSize=12.sp)
            }
        }
        if(current==null){
            itemsIndexed(lists,key={_,list->list.id}){_,list->
                Card(onClick={selected=list.id},modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.QueueMusic,null,tint=Color(0xFF39FF14))
                        Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(list.name,fontWeight=FontWeight.Bold);Text("${list.items.size} itens",fontSize=12.sp,color=Color.LightGray)}
                        Icon(Icons.Default.KeyboardArrowRight,"Abrir playlist")
                    }
                }
            }
        }else itemsIndexed(current.items){i,item->
            val local=match(item,index)
            val link=spotifyLink(item.uri)
            Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                Text((i+1).toString(),color=Color.LightGray,modifier=Modifier.width(36.dp))
                Column(Modifier.weight(1f)){
                    Text(item.title,fontWeight=FontWeight.Medium)
                    if(item.artist.isNotBlank())Text(item.artist,fontSize=12.sp,color=Color.LightGray)
                    Text(if(local!=null)"Disponível no aparelho" else "Sem correspondência local",fontSize=11.sp,color=if(local!=null)Color(0xFF39FF14)else Color.LightGray)
                }
                if(local!=null)IconButton(enabled=canPlay,onClick={onPlay(local)}){Icon(Icons.Default.PlayArrow,"Reproduzir arquivo local")}
                if(link!=null)IconButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link)))}}){Icon(Icons.Default.OpenInNew,"Abrir faixa no Spotify")}
            }
        }
    }
}
