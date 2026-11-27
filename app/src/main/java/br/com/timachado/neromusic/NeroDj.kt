package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val djMoods=listOf("Churrasco","Treino","Romântico","Relaxar","Na estrada","Festa")
internal fun djGenre(prompt:String):NeroGenre?{
    val text=" "+genreKey(prompt)+" "
    return neroGenres.mapNotNull{genre->genre.searchKeys().filter{(" "+it+" ") in text}.maxByOrNull{it.length}?.let{genre to it.length}}.maxByOrNull{it.second}?.first
}
internal fun djCanCreate(prompt:String,mood:String)=prompt.isNotBlank()||mood in djMoods
internal fun djToggleMood(current:String,option:String)=if(current==option)"" else option.takeIf{it in djMoods}.orEmpty()
internal fun djQueries(prompt:String,mood:String,seeds:List<String>,premium:Boolean):List<String>{
    if(!djCanCreate(prompt,mood))return emptyList()
    val phrase=prompt.trim().take(75)
    val genre=djGenre(phrase)
    val artists=genre?.artists?.take(4)?:seeds.take(4)
    val terms=listOf(phrase.ifBlank{mood})+artists.map{(it+" "+mood.ifBlank{phrase}).trim().take(100)}
    return terms.filter{it.length>=3}.distinct().take(if(premium)5 else 1)
}
@Composable
internal fun NeroDjShelf(uid:String?,seeds:List<String>,hidden:Set<String>,canPlay:Boolean,onQueue:(List<MediaItem>,Int)->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var mood by remember(uid){mutableStateOf("")}
    var prompt by remember(uid){mutableStateOf("")}
    var busy by remember(uid){mutableStateOf(false)}
    var verified by remember(uid){mutableStateOf(false)}
    var rows by remember(uid){mutableStateOf(emptyList<NativeMusicHit>())}
    var notice by remember(uid){mutableStateOf<String?>(null)}
    fun active()=NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("NERO DJ",fontSize=22.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary )
        Text("Seu mix por momento e estilo · degustação Free de até 5 faixas, Premium de até 30.",fontSize=12.sp,color=Color.LightGray)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            items(djMoods){option->FilterChip(selected=mood==option,enabled=!busy,onClick={mood=djToggleMood(mood,option);rows=emptyList();notice=null},label={Text(option)})}
        }
        OutlinedTextField(prompt,{prompt=it.take(100);rows=emptyList();notice=null},enabled=!busy,label={Text("Qual é a sua vibe hoje?")},placeholder={Text("Ex.: pagode, rock, sertanejo")},modifier=Modifier.fillMaxWidth())
        Text("Seu pedido será usado como consulta musical. Esta versão usa busca e regras de personalização, não um modelo de IA.",fontSize=11.sp,color=Color.LightGray)
        Button(enabled=!busy&&djCanCreate(prompt,mood),onClick={
            val requested=prompt;val moment=mood
            busy=true;rows=emptyList();notice=null
            scope.launch{
                try{
                    verified=uid!=null&&verifiedPremium(context,uid)&&active()
                    if(!active())return@launch
                    val groups=mutableListOf<List<NativeMusicHit>>()
                    var failed=0
                    val genre=djGenre(requested)
                    for(query in djQueries(requested,moment,seeds,verified)){
                        if(!active())return@launch
                        try{
                            val results=NativeMusicCatalog.search(query).take(25)
                            groups+=if(genre!=null)genreTracks(genre,results)else results
                        }catch(e:CancellationException){throw e}catch(_:Exception){failed++}
                    }
                    if(!active())return@launch
                    rows=interleaveNeroMix(groups,emptySet(),hidden,if(verified)30 else 5)
                    notice=if(rows.isEmpty())"Nenhuma faixa encontrada. Experimente outro pedido." else if(failed>0)"Algumas consultas ficaram indisponíveis." else null
                }finally{busy=false}
            }
        },modifier=Modifier.fillMaxWidth()){Text(if(busy)"Preparando seu DJ…" else "Criar meu mix")}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
        if(rows.isNotEmpty()){
            Text("${rows.size} faixas · "+if(verified)"Premium" else "Degustação Free")
            val play:(List<MediaItem>,Int)->Unit={queue,index->
                if(!busy&&active()){
                    busy=true
                    scope.launch{
                        try{
                            if(verified&&(uid==null||!verifiedPremium(context,uid))){verified=false;rows=rows.take(5);notice="Assinatura não confirmada. Degustação Free disponível."}
                            else if(active())onQueue(queue,index)
                        }finally{busy=false}
                    }
                }
            }
            Button(enabled=canPlay&&!busy,onClick={play(rows.map{it.queueItem()}.withOrigin(PlaybackOrigin("mix","NERO DJ","")),0)}){Text("Reproduzir meu DJ")}
            OnlineDiscoveryRow(rows,canPlay&&!busy,play)
        }
    }
}
