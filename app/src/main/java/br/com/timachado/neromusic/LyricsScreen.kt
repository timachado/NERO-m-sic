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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private const val MAX_LYRICS=256*1024
private data class LyricLine(val at:Long?,val text:String)
private val stamp=Regex("\\[(\\d{1,4}):(\\d{2})(?:[.:](\\d{1,3}))?]")
private val offsetTag=Regex("(?i)\\[offset:([+-]?\\d{1,8})]")
private val metadata=Regex("^\\[[A-Za-z]+:.*]$")
private fun parseLyrics(text:String):List<LyricLine> {
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
internal fun LyricsScreen(mediaId:String,title:String,artist:String,position:Long,duration:Long,playing:Boolean,
    onTogglePlay:()->Unit,onSeek:(Long)->Unit,onBack:()->Unit,modifier:Modifier=Modifier){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val key=remember(mediaId,title,artist){lyricKey(mediaId,title,artist)}
    var lines by remember(key){mutableStateOf(emptyList<LyricLine>())}
    var notice by remember(key){mutableStateOf<String?>(null)}
    var pendingKey by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    val currentKey by rememberUpdatedState(key)
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri ->
        val target=pendingKey;pendingKey=null
        if(uri!=null&&target!=null){
            scope.launch{
                busy=true
                try{
                    val imported=importLyrics(context,uri,target)
                    if(target==currentKey){lines=imported;notice=null}
                }catch(e:CancellationException){throw e}catch(_:Exception){if(target==currentKey)notice="Não foi possível importar. Use LRC ou TXT em UTF-8, até 256 KB."}
                finally{busy=false}
            }
        }
    }
    LaunchedEffect(key){
        lines=withContext(Dispatchers.IO){runCatching{parseLyrics(lyricFile(context,key).readFully().toString(Charsets.UTF_8))}.getOrDefault(emptyList())}
    }
    val synced=lines.firstOrNull()?.at!=null
    val active=if(synced)lines.indexOfLast{(it.at?:Long.MAX_VALUE)<=position}else -1
    val state=rememberLazyListState()
    LaunchedEffect(active,key){if(active>=0&&active<lines.size)state.animateScrollToItem((active-1).coerceAtLeast(0))}
    Column(modifier.fillMaxSize().padding(18.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar ao player")}
            Text("Letras",fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            IconButton(enabled=!busy&&mediaId.isNotBlank(),onClick={pendingKey=key;picker.launch(arrayOf("*/*"))}){Icon(Icons.Default.FileOpen,"Importar LRC ou TXT")}
        }
        Text(title,fontSize=21.sp,fontWeight=FontWeight.Bold)
        Text(artist,color=Color.LightGray)
        Text(if(synced)"Letra sincronizada" else if(lines.isNotEmpty())"Letra sem marcações de tempo" else "Nenhuma letra disponível",color=Color(0xFF00E5FF),fontSize=12.sp)
        notice?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(lines.isEmpty()){
            Spacer(Modifier.weight(1f))
            Text("Importe uma letra LRC com tempos ou um arquivo TXT que você tenha autorização para usar. Busca automática de letras ainda não está integrada.",color=Color.LightGray)
            Button(enabled=!busy&&mediaId.isNotBlank(),onClick={pendingKey=key;picker.launch(arrayOf("*/*"))},modifier=Modifier.fillMaxWidth()){Text("Importar letra")}
            Spacer(Modifier.weight(1f))
        }else LazyColumn(state=state,modifier=Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(vertical=18.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
            itemsIndexed(lines){index,line->
                Text(line.text.ifBlank{"♪"},fontSize=if(index==active)26.sp else 21.sp,fontWeight=if(index==active)FontWeight.Bold else FontWeight.Normal,
                    color=if(index==active)Color(0xFF39FF14) else Color.LightGray,
                    modifier=Modifier.fillMaxWidth().clickable(enabled=line.at!=null&&duration>0){line.at?.let{onSeek(it.coerceIn(0,duration))}})
            }
        }
        Slider(value=if(duration>0)(position.toFloat()/duration).coerceIn(0f,1f)else 0f,onValueChange={if(duration>0)onSeek((it*duration).toLong())},enabled=duration>0)
        FilledIconButton(onClick=onTogglePlay,enabled=mediaId.isNotBlank(),modifier=Modifier.align(Alignment.CenterHorizontally).size(56.dp)){
            Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Reproduzir ou pausar")
        }
    }
}
