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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.media3.common.MediaItem
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Locale

private const val MAX_BYTES=12*1024*1024
internal data class ImportedItem(val title:String,val artist:String,val album:String,val uri:String,val kind:String){
    fun json()=JSONObject().put("title",title).put("artist",artist).put("album",album).put("uri",uri).put("kind",kind)
}
internal data class ImportedList(val id:String,val name:String,val items:List<ImportedItem>){
    fun json()=JSONObject().put("id",id).put("name",name).put("items",JSONArray(items.map{it.json()}))
}
private fun store(context:Context)=AtomicFile(File(context.filesDir,"nero-imported-playlists.json"))
internal fun readSaved(context:Context):List<ImportedList>{
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
private fun readBounded(input:java.io.InputStream,max:Int):ByteArray {
    val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=max);out.write(buffer,0,n)}
    return out.toByteArray()
}
private suspend fun importFile(context:Context,uri:Uri):List<ImportedList> = withContext(Dispatchers.IO){
    context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
        input.mark(4)
        val header=ByteArray(4);val size=input.read(header)
        input.reset()
        if(size==4&&header[0]==0x50.toByte()&&header[1]==0x4b.toByte()&&header[2]==3.toByte()&&header[3]==4.toByte()){
            java.util.zip.ZipInputStream(input).use { zip ->
                val imported=mutableListOf<ImportedList>()
                var totalBytes=0L
                var files=0
                var entries=0
                var totalItems=0
                while(true){
                    val entry=zip.nextEntry?:break
                    require(++entries<=200)
                    val name=entry.name.substringAfterLast('/')
                    // All entries are bounded, including unrelated account data.
                    val remaining=(MAX_BYTES*2L-totalBytes).coerceAtLeast(0).coerceAtMost(MAX_BYTES.toLong()).toInt()
                    val data=readBounded(zip,remaining)
                    totalBytes+=data.size
                    if(!entry.isDirectory&&Regex("(?i)Playlist[^/]*[.]json").matches(name)){
                        require(++files<=25)
                        val parsed=parseExport(data.toString(Charsets.UTF_8))
                        totalItems+=parsed.sumOf{it.items.size};require(totalItems<=50000)
                        imported.addAll(parsed)
                    }
                    zip.closeEntry()
                }
                require(files>0)
                imported.distinctBy{it.id}
            }
        }else parseExport(readBounded(input,MAX_BYTES).toString(Charsets.UTF_8))
    }?:error("Arquivo indisponível")
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
private fun localFingerprint(track:Track):String =
    JSONObject().put("id",track.id).put("uri",track.uri.toString()).put("title",track.title)
        .put("artist",track.artist).put("album",track.album).put("duration",track.duration).toString()

private fun spotifyLink(uri:String):String?{
    val parts=uri.split(":")
    if(parts.size==3&&parts[0]=="spotify"&&parts[1] in setOf("track","episode")&&Regex("[A-Za-z0-9]{22}").matches(parts[2]))
        return "https://open.spotify.com/"+parts[1]+"/"+parts[2]
    return null
}
@Composable
internal fun TransferScreen(tracks:List<Track>,canPlay:Boolean,onPlay:(List<MediaItem>,Int,Boolean,PlaybackOrigin?)->Unit,initialSelection:String?=null){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var lists by remember{mutableStateOf(emptyList<ImportedList>())}
    var pending by remember{mutableStateOf<List<ImportedList>?>(null)}
    var selected by remember(initialSelection){mutableStateOf(initialSelection)}
    var busy by remember{mutableStateOf(true)}
    var notice by remember{mutableStateOf<String?>(null)}
    val index=remember(tracks){tracks.groupBy{norm(it.title) to norm(it.artist)}}
    val trackByFingerprint=remember(tracks){tracks.associateBy{localFingerprint(it)}}
    val links=remember(context){context.getSharedPreferences("nero_transfer_links",Context.MODE_PRIVATE)}
    var linkRevision by remember{mutableIntStateOf(0)}
    var linkIndex by remember{mutableStateOf<Int?>(null)}
    var linkQuery by remember{mutableStateOf("")}
    var choice by remember{mutableStateOf<Track?>(null)}
    var missingOnly by remember{mutableStateOf(false)}
    var sharing by remember{mutableStateOf<SharedPlaylist?>(null)}
    sharing?.let{PlaylistShareDialog(it,onClose={sharing=null})}
    val current=lists.firstOrNull{it.id==selected}
    val matchedEntries=remember(current,index,trackByFingerprint,linkRevision){
        current?.items?.mapIndexedNotNull{i,item->
            val linked=links.getString(current.id+":"+i,null)?.let{trackByFingerprint[it]}
            (linked ?: match(item,index))?.let{i to it}
        }.orEmpty()
    }
    val matchedByPosition=remember(matchedEntries){matchedEntries.toMap()}
    val catalogPrefs=remember(context){SpotifyCatalogLinks.prefs(context)}
    var catalogRevision by remember{mutableIntStateOf(0)}
    var matchingJob by remember{mutableStateOf<Job?>(null)}
    var matchProgress by remember{mutableStateOf<String?>(null)}
    var onlinePosition by remember{mutableStateOf<Int?>(null)}
    var onlineQuery by remember{mutableStateOf("")}
    var onlineCandidates by remember{mutableStateOf(emptyList<NativeMusicHit>())}
    var onlineChoice by remember{mutableStateOf<NativeMusicHit?>(null)}
    var onlineBusy by remember{mutableStateOf(false)}
    var onlineNotice by remember{mutableStateOf<String?>(null)}
    var confirmMatch by remember{mutableStateOf(false)}
    var confirmSave by remember{mutableStateOf(false)}
    var playlistName by remember{mutableStateOf("")}
    var saving by remember{mutableStateOf(false)}
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val catalogMatches=remember(current,catalogRevision){
        current?.items?.indices?.mapNotNull{i->SpotifyCatalogLinks.read(catalogPrefs,current.id,i)?.let{i to it}}?.toMap().orEmpty()
    }
    val playable=remember(current,matchedByPosition,catalogMatches){
        current?.items?.indices?.mapNotNull{i->
            (matchedByPosition[i]?.mediaItem()?:catalogMatches[i]?.takeIf{NativeMusicCatalog.enabled}?.queueItem())?.let{i to it}
        }.orEmpty()
    }
    val playablePositions=remember(playable){playable.map{it.first}.toSet()}
    val visibleEntries=remember(current,playablePositions,missingOnly){
        current?.items?.withIndex()?.filter{!missingOnly||it.index !in playablePositions}.orEmpty()
    }
    val queue=remember(playable){playable.map{it.second}}
    val onlineRefs=remember(current,catalogMatches){current?.items?.indices?.mapNotNull{i->catalogMatches[i]?.reference()}?.distinctBy{it.id}.orEmpty()}
    fun leavePlaylist(){
        matchingJob?.cancel();matchingJob=null;matchProgress=null;selected=null;missingOnly=false
    }
    BackHandler(current!=null&&linkIndex==null&&onlinePosition==null){leavePlaylist()}
    if(confirmMatch&&current!=null)AlertDialog(onDismissRequest={confirmMatch=false},title={Text("Localizar músicas no NERO Music?")},
        text={Text("O NERO consultará somente título e artista das músicas desta playlist na fonte musical. Correspondências exatas e únicas serão vinculadas; versões ambíguas ficam disponíveis para revisão. Nenhum áudio será baixado.")},
        confirmButton={TextButton(onClick={
            confirmMatch=false
            val list=current
            val targets=list.items.withIndex().filter{it.value.kind=="track"&&it.value.artist.isNotBlank()&&it.index !in catalogMatches}
            if(targets.isEmpty()){notice="Nenhuma faixa pendente de busca no catálogo."}
            else matchingJob=scope.launch{
                var found=0;var errors=0;var done=0
                val cache=mutableMapOf<String,List<NativeMusicHit>>()
                try{
                    for(entry in targets){
                        val term=spotifySearchTerm(entry.value)
                        matchProgress="Buscando ${done+1} de ${targets.size} · ${found} encontradas"
                        try{
                            val hits=cache[term]?:NativeMusicCatalog.search(term).also{
                                if(cache.size>=100)cache.clear()
                                cache[term]=it
                            }
                            spotifyAutomaticMatch(entry.value,hits)?.let{hit->
                                SpotifyCatalogLinks.save(catalogPrefs,list.id,entry.index,hit)
                                catalogRevision++;found++
                            }
                        }catch(e:CancellationException){throw e}catch(_:Exception){errors++}
                        done++;delay(350)
                    }
                    notice="${found} novas correspondências online. "+if(errors>0)"${errors} consultas falharam; tente novamente." else "Revise as faixas restantes."
                }finally{matchProgress=null;matchingJob=null}
            }
        }){Text("Buscar músicas")}},dismissButton={TextButton(onClick={confirmMatch=false}){Text("Cancelar")}})
    if(confirmSave&&current!=null)AlertDialog(onDismissRequest={confirmSave=false},title={Text("Salvar playlist no NERO Music?")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Nome original: "+current.name,fontSize=13.sp,color=Color.LightGray)
            OutlinedTextField(playlistName,{playlistName=it.take(100)},label={Text("Nome da playlist")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("${onlineRefs.size} faixas online únicas serão copiadas na ordem original para "+(if(uid!=null)"a biblioteca da conta conectada, com sincronização quando disponível." else "a biblioteca de visitante deste aparelho.")+" Itens não localizados e duplicatas permanecem preservados na importação. Nenhuma playlist existente será sobrescrita.")
        }},
        confirmButton={TextButton(enabled=ready&&!saving&&playlistName.isNotBlank(),onClick={
            val owner=uid;val list=current;val rows=onlineRefs;val name=playlistName.trim()
            confirmSave=false;saving=true
            scope.launch{
                try{
                    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner)
                    require(rows.isNotEmpty()&&rows.size<=1000)
                    val existing=withContext(Dispatchers.IO){scopedOnlineLists(context,owner)}
                    val newId=java.util.UUID.nameUUIDFromBytes(("spotify-transfer:"+list.id).toByteArray(Charsets.UTF_8)).toString()
                    val next=importPortablePlaylists(existing,listOf(OnlineList(newId,name,rows)))
                    require(next.size<=100)
                    saveScopedOnlineLists(context,owner,existing,next)
                    notice="Playlist \""+name+"\" disponível na biblioteca NERO Music."
                }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível salvar a playlist. Confira os limites da biblioteca (100 playlists e 1.000 faixas por playlist); a importação original foi preservada."}
                finally{saving=false}
            }
        }){Text("Salvar")}},dismissButton={TextButton(onClick={confirmSave=false}){Text("Cancelar")}})
    val position=onlinePosition
    if(current!=null&&position!=null){
        val original=current.items.getOrNull(position)
        if(original!=null)AlertDialog(onDismissRequest={if(!onlineBusy)onlinePosition=null},title={Text("Vincular música do catálogo")},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(original.title+" · "+displayArtistName(original.artist),color=Color.LightGray)
                OutlinedTextField(onlineQuery,{onlineQuery=it.take(100);onlineChoice=null},label={Text("Título e artista")},singleLine=true)
                OutlinedButton(enabled=!onlineBusy&&onlineQuery.trim().length>=3,onClick={
                    onlineBusy=true;onlineNotice=null;onlineChoice=null;onlineCandidates=emptyList()
                    val term=onlineQuery.trim()
                    scope.launch{
                        try{onlineCandidates=NativeMusicCatalog.search(term).take(25);if(onlineCandidates.isEmpty())onlineNotice="Nenhum resultado."}
                        catch(e:CancellationException){throw e}catch(_:Exception){onlineNotice="Falha na consulta. Tente novamente."}
                        finally{onlineBusy=false}
                    }
                }){Text("Buscar no NERO Music")}
                if(onlineBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
                onlineNotice?.let{Text(it,fontSize=12.sp)}
                LazyColumn(Modifier.heightIn(max=280.dp)){
                    itemsIndexed(onlineCandidates){_,hit->
                        Row(verticalAlignment=Alignment.CenterVertically){
                            RadioButton(selected=onlineChoice==hit,onClick={onlineChoice=hit})
                            Column(Modifier.weight(1f)){Text(hit.title);Text(displayArtistName(hit.artist),fontSize=12.sp,color=Color.LightGray)}
                        }
                    }
                }
            }},
            confirmButton={TextButton(enabled=onlineChoice!=null&&!onlineBusy,onClick={
                onlineChoice?.let{SpotifyCatalogLinks.save(catalogPrefs,current.id,position,it);catalogRevision++}
                onlinePosition=null
            }){Text("Vincular")}},
            dismissButton={Row{
                if(position in catalogMatches)TextButton(enabled=!onlineBusy,onClick={catalogPrefs.edit().remove(SpotifyCatalogLinks.key(current.id,position)).apply();catalogRevision++;onlinePosition=null}){Text("Desvincular")}
                TextButton(enabled=!onlineBusy,onClick={onlinePosition=null}){Text("Cancelar")}
            }})
    }
    val linkingPosition=linkIndex
    if(current!=null&&linkingPosition!=null){
        val original=current.items.getOrNull(linkingPosition)
        if(original!=null){
            val options=remember(tracks,linkQuery){
                val term=linkQuery.trim()
                if(term.isBlank())emptyList() else tracks.asSequence().filter{track->
                    track.title.contains(term,true)||track.artist.contains(term,true)||track.album.contains(term,true)
                }.take(40).toList()
            }
            AlertDialog(onDismissRequest={linkIndex=null;choice=null},title={Text("Vincular faixa local")},
                text={
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(original.title+" · "+original.artist, color=Color.LightGray)
                        Text("Confira a música e a versão antes de vincular.",fontSize=12.sp)
                        OutlinedTextField(linkQuery,{linkQuery=it;choice=null},singleLine=true,label={Text("Buscar na biblioteca")},modifier=Modifier.fillMaxWidth())
                        LazyColumn(Modifier.heightIn(max=240.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                            itemsIndexed(options){_,track->
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    RadioButton(selected=choice==track,onClick={choice=track})
                                    Column(Modifier.weight(1f)){
                                        Text(track.title,fontWeight=FontWeight.Medium)
                                        Text(track.artist+" · "+track.album,fontSize=12.sp,color=Color.LightGray)
                                    }
                                }
                            }
                        }
                        if(options.isEmpty())Text("Nenhuma faixa local encontrada.",fontSize=12.sp,color=Color.LightGray)
                    }
                },
                confirmButton={TextButton(enabled=choice!=null,onClick={
                    choice?.let{links.edit().putString(current.id+":"+linkingPosition,localFingerprint(it)).apply();linkRevision++}
                    linkIndex=null;choice=null
                }){Text("Vincular")}},
                dismissButton={
                    Row{
                        if(links.contains(current.id+":"+linkingPosition))TextButton(onClick={
                            links.edit().remove(current.id+":"+linkingPosition).apply();linkRevision++;linkIndex=null;choice=null
                        }){Text("Desvincular")}
                        TextButton(onClick={linkIndex=null;choice=null}){Text("Cancelar")}
                    }
                })
        }
    }
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
            }catch(e:CancellationException){throw e}catch(_:Exception){notice="Importação não concluída. Selecione o ZIP de dados ou os JSON de playlists do Spotify. Arquivos grandes ou formatos incompatíveis não são importados."}
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
                    try{lists=saveImport(context,preview);notice="Playlists importadas. Abra uma lista para localizar as músicas no catálogo NERO Music."}
                    catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível salvar a importação. Os dados anteriores foram preservados."}
                    finally{busy=false}
                }
            }){Text("Importar")}},dismissButton={TextButton(onClick={pending=null}){Text("Cancelar")}})
    }
    LazyColumn(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)){
        item{
            if(current!=null)IconButton(onClick={leavePlaylist()}){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text(current?.name ?: "Importar do Spotify",fontSize=24.sp,fontWeight=FontWeight.Bold)
            Text("NERO Transfer · Playlists do Spotify",color=Color(0xFF00E5FF),fontSize=12.sp)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let{Text(it,color=Color.LightGray)}
            if(current==null){
                Text("Solicite seus dados na página de privacidade do Spotify e selecione o ZIP recebido ou os arquivos Playlist*.json. Não é necessário fornecer senha ou token.",color=Color.LightGray,fontSize=13.sp)
                OutlinedButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.spotify.com/account/privacy/")))}},modifier=Modifier.fillMaxWidth()){Text("Solicitar dados ao Spotify")}
                Button(onClick={picker.launch(arrayOf("application/zip","application/x-zip-compressed","application/json","text/plain","application/octet-stream"))},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Importar playlists do Spotify")}
            }else{
                Text("${current.items.size} itens · ${queue.size} reproduzíveis",color=Color.LightGray)
                Text("${catalogMatches.size} online · ${matchedByPosition.size} locais · ${current.items.size-queue.size} pendentes",fontSize=12.sp,color=Color.LightGray)
                matchProgress?.let{Text(it,fontSize=13.sp,color=MaterialTheme.colorScheme.primary);LinearProgressIndicator(Modifier.fillMaxWidth())}
                if(matchingJob!=null)OutlinedButton(onClick={matchingJob?.cancel()},modifier=Modifier.fillMaxWidth()){Text("Parar busca (preservar resultados)")}
                else Button(enabled=!saving&&NativeMusicCatalog.enabled,onClick={confirmMatch=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Search,null);Text("Localizar músicas no NERO Music")}
                OutlinedButton(enabled=ready&&!saving&&matchingJob==null&&onlineRefs.isNotEmpty(),onClick={playlistName=current.name.take(100);confirmSave=true},modifier=Modifier.fillMaxWidth()){Text("Salvar como playlist NERO Music")}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(enabled=canPlay&&queue.isNotEmpty(),onClick={onPlay(queue,0,false,current?.let{PlaybackOrigin("imported",it.name,it.id)})},modifier=Modifier.weight(1f)){Icon(Icons.Default.PlayArrow,null);Text("Reproduzir")}
                    OutlinedButton(enabled=canPlay&&queue.isNotEmpty(),onClick={onPlay(queue,queue.indices.random(),true,current?.let{PlaybackOrigin("imported",it.name,it.id)})},modifier=Modifier.weight(1f)){Icon(Icons.Default.Shuffle,null);Text("Aleatório")}
                }
                Text("A fila preserva a ordem dos itens vinculados, locais ou online. Os demais permanecem na playlist.",fontSize=12.sp,color=Color.LightGray)
                Text("As faixas sem correspondência permanecem na lista. Importar não transfere o áudio nem a assinatura Spotify.",color=Color.LightGray,fontSize=12.sp)
                OutlinedButton(enabled=current.items.isNotEmpty(),onClick={sharing=SharedPlaylist(current.name,current.items.map{SharedSong(it.title,it.artist,publicLink=spotifyLink(it.uri))})},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("Compartilhar playlist")}
                FilterChip(selected=missingOnly,onClick={missingOnly=!missingOnly},label={Text("Mostrar apenas faixas faltantes")})
            }
        }
        if(current==null){
            itemsIndexed(lists,key={_,list->list.id}){_,list->
                Card(onClick={selected=list.id;missingOnly=false},modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.QueueMusic,null,tint=Color(0xFF39FF14))
                        Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(list.name,fontWeight=FontWeight.Bold);Text("${list.items.size} itens",fontSize=12.sp,color=Color.LightGray)}
                        Icon(Icons.Default.KeyboardArrowRight,"Abrir playlist")
                    }
                }
            }
        }else itemsIndexed(visibleEntries,key={_,entry->entry.index}){_,entry->
            val i=entry.index
            val item=entry.value
            val local=matchedByPosition[i]
            val online=catalogMatches[i]
            val link=spotifyLink(item.uri)
            Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                Text((i+1).toString(),color=Color.LightGray,modifier=Modifier.width(36.dp))
                Column(Modifier.weight(1f)){
                    Text(item.title,fontWeight=FontWeight.Medium)
                    if(item.artist.isNotBlank())Text(displayArtistName(item.artist),fontSize=12.sp,color=Color.LightGray)
                    val manual=links.getString(current.id+":"+i,null)?.let{trackByFingerprint[it]}!=null
                    Text(if(manual)"Vinculada ao arquivo local" else if(local!=null)"Disponível no aparelho" else if(online!=null)"Disponível no NERO Music" else "Aguardando correspondência",fontSize=11.sp,color=if(i in playablePositions)Color(0xFF39FF14)else Color.LightGray)
                    if(item.kind=="track")TextButton(onClick={linkQuery=item.title;choice=null;linkIndex=i},contentPadding=PaddingValues(0.dp)){Text(if(manual)"Alterar vínculo" else "Vincular faixa local",fontSize=12.sp)}
                }
                if(i in playablePositions)IconButton(enabled=canPlay,onClick={val index=playable.indexOfFirst{it.first==i};if(index>=0)onPlay(queue,index,false,PlaybackOrigin("imported",current.name,current.id))}){Icon(Icons.Default.PlayArrow,"Reproduzir a partir desta faixa")}
                if(item.kind=="track"&&NativeMusicCatalog.enabled)IconButton(enabled=matchingJob==null&&!saving,onClick={
                    onlineQuery=spotifySearchTerm(item);onlineCandidates=listOfNotNull(online);onlineChoice=online;onlineNotice=null;onlinePosition=i
                }){Icon(Icons.Default.Search,if(online==null)"Localizar no NERO" else "Revisar vínculo online")}
                if(link!=null)IconButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link)))}}){Icon(Icons.Default.OpenInNew,"Abrir faixa no Spotify")}
            }
        }
    }
}
