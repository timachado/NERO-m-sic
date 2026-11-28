package br.com.timachado.neromusic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException

// Only public recording durations, not listening history or account information.
internal object NeroTrackDurations {
    private const val maxEntries=2500
    private val values=LinkedHashMap<String,Long>(128,.75f,true)
    private val state=MutableStateFlow<Map<String,Long>>(emptyMap())
    val durations=state.asStateFlow()
    private var prefs:SharedPreferences?=null
    @Synchronized fun initialize(context:Context){
        if(prefs!=null)return
        val p=context.applicationContext.getSharedPreferences("nero_track_durations_v1",Context.MODE_PRIVATE)
        p.all.forEach{(id,value)->if(validId(id)&&value is Long&&value in 1..86400L)values[id]=value}
        while(values.size>maxEntries)values.remove(values.keys.first())
        prefs=p
        state.value=values.toMap()
    }
    private fun validId(id:String)=id.matches(Regex("youtube:[A-Za-z0-9_-]{11}|commons:[0-9]{1,19}"))
    @Synchronized fun record(id:String,seconds:Long){
        if(!validId(id)||seconds !in 1..86400L||values[id]==seconds)return
        values[id]=seconds
        val edit=prefs?.edit()?.putLong(id,seconds)
        while(values.size>maxEntries){val oldest=values.keys.first();values.remove(oldest);edit?.remove(oldest)}
        edit?.apply()
        state.value=values.toMap()
    }
}
internal fun playlistDurationLabel(seconds:List<Long?>):String?{
    val known=seconds.filterNotNull().filter{it>0}
    if(known.isEmpty())return null
    val total=known.sum()
    val minutes=total/60
    val text=when{
        total<60->"$total s"
        minutes<60->"$minutes min"
        else->"${minutes/60} h ${minutes%60} min"
    }
    return if(known.size==seconds.size)text else "$text (parcial)"
}
internal fun onlinePlaylistSummary(songs:List<OnlineRef>,durations:Map<String,Long>):String{
    val count=if(songs.size==1)"1 música" else "${songs.size} músicas"
    return count+(playlistDurationLabel(songs.map{durations[it.id]})?.let{" · "+it}?:"")
}
@Composable
internal fun LoadPlaylistDurations(ids:List<String>,enabled:Boolean=true){
    val requested=remember(ids){ids.distinct().filter{it.startsWith("youtube:")}}
    LaunchedEffect(requested,enabled){
        if(!enabled)return@LaunchedEffect
        for(id in requested.filter{NeroTrackDurations.durations.value[it]==null}.take(40)){
            if(NeroTrackDurations.durations.value[id]!=null)continue
            try{NativeMusicCatalog.durationSeconds(id.removePrefix("youtube:"))?.let{NeroTrackDurations.record(id,it)}}
            catch(e:CancellationException){throw e}catch(_:Exception){}
            kotlinx.coroutines.delay(150L)
        }
    }
}
