package br.com.timachado.neromusic

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private const val MAX_LYRICS=256*1024
internal data class LyricLine(val at:Long?,val text:String)
private val stamp=Regex("\\[(\\d{1,4}):(\\d{2})(?:[.:](\\d{1,3}))?]")
private val offsetTag=Regex("(?i)\\[offset:([+-]?\\d{1,8})]")
private val metadata=Regex("^\\[[A-Za-z]+:.*]$")
internal fun parseLyrics(text:String):List<LyricLine> {
    val offset=offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val timed=mutableListOf<LyricLine>()
    val plain=mutableListOf<LyricLine>()
    for(line in text.removePrefix("\uFEFF").lineSequence().take(5000)){
        val marks=stamp.findAll(line).toList()
        if(marks.isNotEmpty()) {
            val words=stamp.replace(line,"").trim()
            for(mark in marks.take(64)){
                val mins=mark.groupValues[1].toLong();val secs=mark.groupValues[2].toLong()
                if(secs>=60)continue
                val fraction=mark.groupValues[3].padEnd(3,'0').take(3).toLongOrNull()?:0L
                timed.add(LyricLine(((mins*60+secs)*1000+fraction+offset).coerceAtLeast(0),words))
            }
        }else if(line.isNotBlank()&&!metadata.matches(line.trim()))plain.add(LyricLine(null,line.trim()))
    }
    return if(timed.isNotEmpty())timed.sortedBy{it.at}.take(10000) else plain
}
private fun lyricFile(context:Context,key:String)=AtomicFile(File(File(context.filesDir,"lyrics").apply{mkdirs()},key+".txt"))
private fun lyricKey(mediaId:String,title:String,artist:String)=MessageDigest.getInstance("SHA-256")
    .digest((mediaId+"\u0000"+title+"\u0000"+artist).toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
private fun aiLyricKey(key:String,owner:String)=key+"_ai_"+MessageDigest.getInstance("SHA-256")
    .digest(owner.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}
private suspend fun importLyrics(context:Context,uri:Uri,key:String):List<LyricLine> = withContext(Dispatchers.IO){
    val bytes=context.contentResolver.openInputStream(uri)?.use{input ->
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=MAX_LYRICS);out.write(buffer,0,n)}
        out.toByteArray()
    }?:error("Arquivo indisponível")
    val parsed=parseLyrics(bytes.toString(Charsets.UTF_8))
    require(parsed.any{it.text.isNotBlank()})
    val file=lyricFile(context,key);val stream=file.startWrite()
    try{stream.write(bytes);file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}
    parsed
}
@Composable
internal fun LyricsScreen(mediaId:String,title:String,artist:String,position:Long,duration:Long,playing:Boolean,localUri:Uri?,
    onTogglePlay:()->Unit,onSeek:(Long)->Unit,onBack:()->Unit,modifier:Modifier=Modifier){
    val context=LocalContext.current
    val premiumSignal by NeroPlaybackAccess.premium.collectAsState()
    val fullControls=premiumSignal&&NeroPlaybackAccess.allowed()
    val scope=rememberCoroutineScope()
    val key=remember(mediaId,title,artist){lyricKey(mediaId,title,artist)}
    val aiUid by NeroSupabase.activeUid.collectAsState()
    val aiReady by NeroSupabase.identityReady.collectAsState()
    var lines by remember(key,aiUid,aiReady){mutableStateOf(emptyList<LyricLine>())}
    var source by remember(key,aiUid,aiReady){mutableStateOf<String?>(null)}
    var loading by remember(key,localUri){mutableStateOf(true)}
    var importRevision by remember(key){mutableIntStateOf(0)}
    var onlineRevision by remember(key){mutableIntStateOf(0)}
    var onlineNotice by remember(key){mutableStateOf<String?>(null)}
    var notice by remember(key){mutableStateOf<String?>(null)}
    var pendingKey by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    var editorOpen by remember(key,aiUid,aiReady){mutableStateOf(false)}
    var editorText by remember(key,aiUid,aiReady){mutableStateOf("")}
    val currentKey by rememberUpdatedState(key)
    var aiConfirm by remember(key,aiUid,aiReady){mutableStateOf(false)}
    var aiBusy by remember(key,aiUid,aiReady){mutableStateOf(false)}
    var aiNotice by remember(key,aiUid,aiReady){mutableStateOf<String?>(null)}
    var aiJob by remember(key,aiUid,aiReady){mutableStateOf<Job?>(null)}
    var aiDraft by remember(key,aiUid,aiReady){mutableStateOf(false)}
    var pendingAudio by remember{mutableStateOf<Pair<String,String>?>(null)}
    DisposableEffect(key,aiUid,aiReady){onDispose{aiJob?.cancel()}}
    val audioPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        val target=pendingAudio;pendingAudio=null
        if(uri!=null&&target!=null&&target.first==currentKey&&aiReady&&NeroSupabase.activeUid.value==target.second&&!busy&&!aiBusy){
            aiBusy=true;aiNotice="Transcrevendo o áudio…"
            aiJob=scope.launch{
                try{
                    val transcript=NeroLyricsAiClient.transcribe(context,target.second,uri)
                    if(target.first==currentKey&&NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==target.second){
                        editorText=transcript;aiDraft=true;editorOpen=true
                        aiNotice="Transcrição aproximada por IA. Confira as palavras e a faixa antes de salvar."
                    }
                }catch(e:CancellationException){throw e}
                catch(_:Exception){aiNotice="Transcrição indisponível. Use WAV PCM mono de 16 kHz, até cinco minutos, e tente novamente."}
                finally{aiBusy=false}
            }
        }
    }
    if(aiConfirm)AlertDialog(onDismissRequest={aiConfirm=false},title={Text("Transcrever áudio com IA")},
        text={Text("Selecione uma gravação própria ou que você possa enviar para transcrição. O arquivo será enviado ao serviço de IA. Use WAV PCM mono de 16 kHz, até cinco minutos. A transcrição pode conter erros; confira o texto antes de associá-lo à música.")},
        confirmButton={TextButton(onClick={
            aiConfirm=false
            val owner=aiUid
            if(owner!=null&&aiReady&&!busy&&!aiBusy&&NeroLyricsAiClient.enabled){
                pendingAudio=key to owner
                audioPicker.launch(arrayOf("audio/wav","audio/x-wav","audio/vnd.wave"))
            }
        }){Text("Selecionar áudio")}},dismissButton={TextButton(onClick={aiConfirm=false}){Text("Cancelar")}})
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri ->
        val target=pendingKey;pendingKey=null
        if(uri!=null&&target!=null){
            scope.launch{
                busy=true
                try{
                    val imported=importLyrics(context,uri,target)
                    if(target==currentKey){lines=imported;source="Arquivo importado";notice=null;importRevision++}
                }catch(e:CancellationException){throw e}catch(_:Exception){if(target==currentKey)notice="Não foi possível importar. Use LRC ou TXT em UTF-8, até 256 KB."}
                finally{busy=false}
            }
        }
    }
    LaunchedEffect(key,localUri,importRevision,aiUid,aiReady){
        loading=true
        try {
            val result=withContext(Dispatchers.IO){
                val manual=runCatching{lyricFile(context,key).readFully().toString(Charsets.UTF_8)}.getOrDefault("")
                val owner=aiUid
                val generated=if(manual.isBlank()&&aiReady&&owner!=null)
                    runCatching{lyricFile(context,aiLyricKey(key,owner)).readFully().toString(Charsets.UTF_8)}.getOrDefault("")
                else ""
                val stored=manual.ifBlank{generated}
                val imported=parseLyrics(stored)
                if(imported.any{it.text.isNotBlank()}) imported to if(stored.startsWith("[source:NERO_AI_TRANSCRIPT]"))"Transcrição aproximada por IA · revisada pelo usuário" else "Arquivo importado"
                else {
                    val embedded=if(localUri!=null)runCatching{
                        context.contentResolver.openInputStream(localUri)?.use{readEmbeddedLyrics(it)}
                    }.getOrNull() else null
                    val parsed=embedded?.let{parseLyrics(it)}.orEmpty()
                    parsed to if(parsed.any{it.text.isNotBlank()})"Letra incorporada ao áudio" else null
                }
            }
            lines=result.first;source=result.second
        } finally {loading=false}
    }
    val durationSeconds=(duration+500L)/1000L
    LaunchedEffect(key,durationSeconds,loading,importRevision,onlineRevision){
        if(loading||lines.isNotEmpty()||durationSeconds<=0||title.isBlank()||artist.isBlank()||!BuildConfig.DEBUG)return@LaunchedEffect
        onlineNotice="Buscando letra online…"
        try{
            val result=OnlineLyrics.lookup(title,artist,durationSeconds)
            if(result!=null&&currentKey==key&&lines.isEmpty()){
                val parsed=parseLyrics(result.text)
                if(parsed.any{it.text.isNotBlank()}){
                    lines=parsed;source="LRCLIB · consulta online";onlineNotice=null
                }else onlineNotice="Nenhuma letra compatível encontrada."
            }else onlineNotice="Nenhuma letra compatível encontrada."
        }catch(e:CancellationException){throw e}
        catch(e:LyricsAuthException){onlineNotice="O serviço de letras exige ativação de acesso."}
        catch(_:Exception){onlineNotice="Serviço de letras temporariamente indisponível."}
    }
    if(editorOpen)NeroLyricsEditor(editorText,position,duration,playing,onTogglePlay,busy,onRestart=if(fullControls){{onSeek(0L)}}else null,aiDraft=aiDraft,onDismiss={editorOpen=false;aiDraft=false},onSave={value->
        val target=key
        val fromAi=aiDraft
        val owner=aiUid
        if(!busy&&target==currentKey&&(!fromAi||(aiReady&&owner!=null&&NeroSupabase.activeUid.value==owner))){
            busy=true
            scope.launch{
                try{
                    val parsed=withContext(Dispatchers.IO){
                        val bytes=(if(fromAi)"[source:NERO_AI_TRANSCRIPT]\n"+value else value).toByteArray(Charsets.UTF_8)
                        require(bytes.size<=MAX_LYRICS)
                        val result=parseLyrics(value)
                        require(result.any{it.text.isNotBlank()})
                        val saveKey=if(fromAi)aiLyricKey(target,requireNotNull(owner)) else target
                        val file=lyricFile(context,saveKey);val stream=file.startWrite()
                        try{stream.write(bytes);file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}
                        result
                    }
                    if(target==currentKey){lines=parsed;source=if(fromAi)"Transcrição aproximada por IA · revisada pelo usuário" else "Letra salva no aparelho";editorOpen=false;aiDraft=false;notice=null;importRevision++}
                }catch(e:CancellationException){throw e}
                catch(_:Exception){if(target==currentKey)notice="Não foi possível salvar a letra."}
                finally{busy=false}
            }
        }
    })
    val synced=lines.firstOrNull()?.at!=null
    val active=if(synced)activeLyricIndex(lines,position)else -1
    var follow by remember(key){mutableStateOf(true)}
    val state=rememberLazyListState()
    LaunchedEffect(active,key,follow){if(follow&&active>=0&&active<lines.size)state.animateScrollToItem((active-1).coerceAtLeast(0))}
    Column(modifier.fillMaxSize().padding(18.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar ao player")}
            Text("Letras",fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            IconButton(enabled=!busy&&!aiBusy&&!loading&&mediaId.isNotBlank(),onClick={pendingKey=key;picker.launch(arrayOf("*/*"))}){Icon(Icons.Default.FileOpen,"Importar LRC ou TXT")}
        }
        Text(title,fontSize=21.sp,fontWeight=FontWeight.Bold)
        Text(displayArtistName(artist),color=Color.LightGray)
        Text(if(synced)"Letra sincronizada" else if(lines.isNotEmpty())"Letra sem marcações de tempo" else "Nenhuma letra disponível",color=MaterialTheme.colorScheme.secondary,fontSize=12.sp)
        if(synced)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(if(follow)"Acompanhar música" else "Leitura livre",modifier=Modifier.weight(1f),fontSize=13.sp)
            Switch(checked=follow,onCheckedChange={follow=it})
        }
        source?.let{Text(it,color=Color.LightGray,fontSize=11.sp)}
        onlineNotice?.let{Text(it,color=Color.LightGray,fontSize=12.sp)}
        notice?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        aiNotice?.let{Text(it,color=MaterialTheme.colorScheme.secondary,fontSize=12.sp)}
        if(loading){
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            Text("Procurando letra no arquivo...",color=Color.LightGray)
            Spacer(Modifier.weight(1f))
        }else if(lines.isEmpty()){
            Spacer(Modifier.weight(1f))
            Text("Nenhuma letra disponível no momento. Importe um arquivo LRC/TXT ou insira sua letra.",color=Color.LightGray)
            if(NeroLyricsAiClient.enabled){
                if(aiBusy){
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick={aiJob?.cancel();aiNotice=null}){Text("Cancelar transcrição")}
                }else Button(enabled=!busy&&aiReady&&aiUid!=null&&mediaId.isNotBlank(),onClick={aiConfirm=true},modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text("Transcrever com IA")
                }
            }
            OutlinedButton(enabled=!busy&&!aiBusy&&mediaId.isNotBlank(),onClick={editorText="";aiDraft=false;editorOpen=true},modifier=Modifier.fillMaxWidth()){Text("Inserir e sincronizar letra")}
            if(BuildConfig.DEBUG)TextButton(enabled=durationSeconds>0&&onlineNotice!="Buscando letra online…",onClick={onlineRevision++}){Text("Buscar letra novamente")}
            Button(enabled=!busy&&!aiBusy&&mediaId.isNotBlank(),onClick={pendingKey=key;picker.launch(arrayOf("*/*"))},modifier=Modifier.fillMaxWidth()){Text("Importar letra")}
            Spacer(Modifier.weight(1f))
        }else {
            if(!synced&&source!="LRCLIB · consulta online")TextButton(enabled=!busy&&duration>0&&mediaId.isNotBlank(),onClick={
                editorText=lines.joinToString("\n"){it.text};aiDraft=source?.startsWith("Transcrição aproximada por IA")==true;editorOpen=true
            }){Text("Sincronizar esta letra")}
            LazyColumn(state=state,modifier=Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(vertical=18.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
            itemsIndexed(lines){index,line->
                Text(line.text.ifBlank{"♪"},fontSize=if(index==active)26.sp else 21.sp,fontWeight=if(index==active)FontWeight.Bold else FontWeight.Normal,
                    color=if(index==active)MaterialTheme.colorScheme.primary else Color.LightGray,
                    modifier=Modifier.fillMaxWidth().clickable(enabled=fullControls&&line.at!=null&&duration>0){line.at?.let{onSeek(it.coerceIn(0,duration))}})
            }
        }
        }
        Slider(value=if(duration>0)(position.toFloat()/duration).coerceIn(0f,1f)else 0f,onValueChange={if(duration>0)onSeek((it*duration).toLong())},enabled=duration>0&&fullControls)
        FilledIconButton(onClick=onTogglePlay,enabled=mediaId.isNotBlank(),modifier=Modifier.align(Alignment.CenterHorizontally).size(56.dp)){
            Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Reproduzir ou pausar")
        }
    }
}

internal suspend fun playerPreviewLyrics(context:Context,mediaId:String,title:String,artist:String,duration:Long,localUri:Uri?):List<LyricLine>{
    val local=withContext(Dispatchers.IO){
        val key=lyricKey(mediaId,title,artist)
        val imported=runCatching{parseLyrics(lyricFile(context,key).readFully().toString(Charsets.UTF_8))}.getOrDefault(emptyList())
        val owner=NeroSupabase.activeUid.value
        val ai=if(imported.isEmpty()&&owner!=null&&NeroSupabase.identityReady.value)
            runCatching{parseLyrics(lyricFile(context,aiLyricKey(key,owner)).readFully().toString(Charsets.UTF_8))}.getOrDefault(emptyList())
        else emptyList()
        if(imported.isNotEmpty())imported else if(ai.isNotEmpty()&&NeroSupabase.activeUid.value==owner&&NeroSupabase.identityReady.value)ai else if(localUri!=null)runCatching{context.contentResolver.openInputStream(localUri)?.use{readEmbeddedLyrics(it)}?.let{parseLyrics(it)}.orEmpty()}.getOrDefault(emptyList())else emptyList()
    }
    if(local.isNotEmpty()||!BuildConfig.DEBUG||duration<=0)return local
    return OnlineLyrics.lookup(title,artist,(duration+500L)/1000L)?.let{parseLyrics(it.text)}.orEmpty()
}

// Timed lyrics are sorted by parseLyrics. Return the last matching timestamp,
// including equal timestamps, without scanning the full lyric every player tick.
internal fun activeLyricIndex(lines:List<LyricLine>,position:Long):Int {
    var low=0
    var high=lines.lastIndex
    var result=-1
    while(low<=high){
        val mid=low+(high-low)/2
        val time=lines[mid].at?:Long.MAX_VALUE
        if(time<=position){result=mid;low=mid+1}else high=mid-1
    }
    return result
}
