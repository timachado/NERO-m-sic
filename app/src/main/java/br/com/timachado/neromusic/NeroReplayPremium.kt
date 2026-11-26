package br.com.timachado.neromusic

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ReplayPremiumSummary(val total:Long,val activeDays:Int,val daily:List<Pair<Long,Long>>,val longestStreak:Int,val tracks:List<ReplayTrack>,val artists:List<Pair<String,Long>>)
internal fun summarizePremiumReplay(entries:List<ReplayEntry>,today:Long,days:Int):ReplayPremiumSummary{
    val period=days.coerceIn(1,90)
    val valid=entries.filter{it.day in today-period+1..today&&it.millis in 1L..86400000L}
    val totals=valid.groupBy{it.day}.mapValues{(_,v)->v.sumOf{it.millis}}
    val daily=(today-period+1..today).map{it to (totals[it]?:0L)}
    var streak=0;var longest=0
    daily.forEach{(_,ms)->streak=if(ms>0)streak+1 else 0;longest=maxOf(streak,longest)}
    val tracks=replayTracks(valid,today,period)
    return ReplayPremiumSummary(valid.sumOf{it.millis},totals.size,daily,longest,tracks,replayArtists(tracks))
}

// Account-specific metadata stored on this device only. Legacy device Replay is
// never imported into an account. Recording is explicit and can be stopped or
// erased even when the subscription cannot be verified.
internal object NeroReplayPremiumStore{
    @Volatile var generation=0L
        private set
    private fun prefs(context:Context,uid:String)=context.getSharedPreferences("nero_replay_account_"+NeroAccountLibraryStore.fileName(uid),Context.MODE_PRIVATE)
    private fun active(uid:String)=NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid
    @Synchronized fun enabled(context:Context,uid:String)=active(uid)&&prefs(context,uid).getBoolean("enabled",false)
    @Synchronized fun recordingOwner(context:Context):String?{
        val uid=NeroSupabase.activeUid.value?:return null
        return uid.takeIf{enabled(context,it)}
    }
    @Synchronized fun setEnabled(context:Context,uid:String,value:Boolean){
        require(active(uid));generation++
        prefs(context,uid).edit().putBoolean("enabled",value).apply()
    }
    @Synchronized fun clear(context:Context,uid:String){
        require(active(uid));generation++
        val p=prefs(context,uid);val enabled=p.getBoolean("enabled",false)
        p.edit().clear().putBoolean("enabled",enabled).apply()
    }
    @Synchronized fun entries(context:Context,uid:String):List<ReplayEntry>{
        if(!active(uid))return emptyList()
        val day=NeroReplay.today()
        return prefs(context,uid).all.mapNotNull{(key,value)->
            if(!key.startsWith("r:")||value !is String)return@mapNotNull null
            runCatching{val j=JSONObject(value);ReplayEntry(j.getLong("day"),j.getString("id"),j.getString("title"),j.optString("artist"),j.getLong("ms"))}.getOrNull()
                ?.takeIf{it.day in day-89..day&&it.millis in 1L..86400000L}
        }.takeIf{active(uid)}?:emptyList()
    }
    @Synchronized fun add(context:Context,uid:String,id:String,title:String,artist:String,ms:Long){
        if(!enabled(context,uid)||!NeroReplay.enabled(context)||id.isBlank()||id.length>512||title.isBlank()||ms !in 1L..6000L)return
        val day=NeroReplay.today();val p=prefs(context,uid);val key="r:"+day+":"+id
        val edit=p.edit()
        val keys=p.all.keys.filter{it.startsWith("r:")}
        val expired=keys.filter{it.substringAfter("r:").substringBefore(':').toLongOrNull()?.let{d->d in day-89..day}!=true}
        expired.forEach{edit.remove(it)}
        val remaining=keys.size-expired.size
        if(key !in keys&&remaining>=3000){edit.apply();return}
        val old=runCatching{JSONObject(p.getString(key,null)?:"{}").optLong("ms")}.getOrDefault(0L).coerceIn(0L,86400000L)
        val row=JSONObject().put("day",day).put("id",id).put("title",title.take(200)).put("artist",artist.take(200)).put("ms",(old+ms).coerceAtMost(86400000L))
        if(active(uid))edit.putString(key,row.toString()).apply()
    }
}

@Composable
internal fun NeroReplayPremium(days:Int){
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    Text("NERO Replay Premium",fontWeight=FontWeight.Bold,color=Color(0xFF39FF14))
    val owner=uid
    if(!ready||owner==null){Text("Entre com Google para conhecer as estatísticas da sua conta neste aparelho.",fontSize=12.sp);return}
    key(owner){
        val scope=rememberCoroutineScope()
        var enabled by remember{mutableStateOf(NeroReplayPremiumStore.enabled(context,owner))}
        var verified by remember{mutableStateOf(false)}
        var busy by remember{mutableStateOf(false)}
        var checked by remember{mutableStateOf(false)}
        var confirm by remember{mutableStateOf(false)}
        var entries by remember{mutableStateOf(emptyList<ReplayEntry>())}
        var today by remember{mutableLongStateOf(NeroReplay.today())}
        LaunchedEffect(owner,verified){
            if(!verified){entries=emptyList();return@LaunchedEffect}
            while(true){
                entries=withContext(Dispatchers.IO){NeroReplayPremiumStore.entries(context,owner)}
                today=NeroReplay.today()
                delay(10000L)
            }
        }
        LaunchedEffect(verified){if(verified){delay(300000L);verified=false;checked=false}}
        Text("Registro separado por conta neste aparelho, sem sincronização e sem importar o Replay antigo. A coleta é opcional; o painel avançado exige assinatura ativa.",fontSize=12.sp,color=Color.LightGray)
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("Registrar estatísticas da conta",modifier=Modifier.weight(1f),fontSize=13.sp)
            Switch(checked=enabled,enabled=!busy&&(enabled||verified),onCheckedChange={value->
                if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner){
                    NeroReplayPremiumStore.setEnabled(context,owner,value);enabled=value
                }
            })
        }
        if(enabled)Text("O registro continua até você desativá-lo; desligar o Replay local também pausa a coleta.",fontSize=11.sp,color=Color.LightGray)
        OutlinedButton(enabled=!busy,onClick={busy=true;verified=false;scope.launch{
            try{verified=verifiedPremium(context,owner);checked=true}
            finally{busy=false}
        }},modifier=Modifier.fillMaxWidth()){Text(if(busy)"Verificando…" else if(verified)"Revalidar assinatura" else "Consultar Premium")}
        if(checked&&!verified)Text("Assinatura ativa não confirmada. As estatísticas básicas continuam disponíveis.",fontSize=12.sp,color=Color.LightGray)
        if(verified){
            val summary=remember(entries,today,days){summarizePremiumReplay(entries,today,days)}
            Text("${summary.activeDays} dias ativos · maior sequência: ${summary.longestStreak} dias",fontSize=13.sp)
            Text("Tempo ouvido: "+premiumReplayTime(summary.total),fontWeight=FontWeight.Bold)
            Text("Evolução diária · últimos ${minOf(days,14)} dias",fontWeight=FontWeight.Medium)
            val visible=summary.daily.takeLast(14)
            val max=visible.maxOfOrNull{it.second}?.coerceAtLeast(1L)?:1L
            val dateFormat=remember{DateTimeFormatter.ofPattern("dd/MM",Locale("pt","BR"))}
            visible.forEach{(day,ms)->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(LocalDate.ofEpochDay(day).format(dateFormat),fontSize=11.sp)
                    LinearProgressIndicator(progress={(ms.toDouble()/max).toFloat()},modifier=Modifier.weight(1f))
                    Text(premiumReplayTime(ms),fontSize=11.sp)
                }
            }
            if(summary.total==0L)Text("Ainda não há registros desta conta. Ative a coleta e ouça músicas.",fontSize=12.sp)
            if(summary.artists.isNotEmpty()){
                Text("Top 10 artistas",fontWeight=FontWeight.Bold)
                summary.artists.take(10).forEachIndexed{i,(name,ms)->Text("${i+1}. "+name+" · "+premiumReplayTime(ms),fontSize=12.sp)}
            }
            if(summary.tracks.isNotEmpty()){
                Text("Top 10 faixas",fontWeight=FontWeight.Bold)
                summary.tracks.take(10).forEachIndexed{i,row->Text("${i+1}. "+row.title+" · "+premiumReplayTime(row.millis),fontSize=12.sp)}
            }
        }
        TextButton(onClick={confirm=true}){Text("Apagar registros desta conta")}
        if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Apagar Replay da conta neste aparelho?")},text={Text("Apaga apenas o registro separado desta conta. Não altera o Replay básico, favoritos ou playlists.")},confirmButton={TextButton(onClick={if(NeroSupabase.activeUid.value==owner&&NeroSupabase.identityReady.value){NeroReplayPremiumStore.clear(context,owner);entries=emptyList()};confirm=false}){Text("Apagar")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancelar")}})
        Text("Estimativas com amostragem de 5 segundos; retenção de até 90 dias e 3.000 registros.",fontSize=11.sp,color=Color.LightGray)
    }
}
private fun premiumReplayTime(ms:Long):String{
    val m=ms/60000L
    return if(m>=60)"${m/60} h ${m%60} min" else if(m>0)"$m min" else "${ms/1000L} s"
}
