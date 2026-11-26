package br.com.timachado.neromusic

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

internal data class ReplayEntry(val day:Long,val id:String,val title:String,val artist:String,val millis:Long)
internal data class ReplayTrack(val id:String,val title:String,val artist:String,val millis:Long)
internal fun replayTracks(entries:List<ReplayEntry>,today:Long,days:Int,maxDays:Int=90):List<ReplayTrack> =
    entries.filter{it.day in (today-days.coerceIn(1,maxDays)+1)..today&&it.millis>0}
        .groupBy{it.id}.map{(id,rows)->
            val latest=rows.maxByOrNull{it.day}!!
            ReplayTrack(id,latest.title,latest.artist,rows.sumOf{it.millis})
        }.sortedWith(compareByDescending<ReplayTrack>{it.millis}.thenBy{it.id})

internal fun replayArtists(rows:List<ReplayTrack>):List<Pair<String,Long>> =
    rows.filter{it.artist.isNotBlank()}.groupBy{it.artist.trim().lowercase(Locale.ROOT)}
        .values.map{group->group.first().artist.trim() to group.sumOf{it.millis}}
        .sortedByDescending{it.second}

internal fun mixArtists(played:List<ReplayTrack>,liked:List<String>,recent:List<String>):List<String> =
    (replayArtists(played).map{it.first}+liked+recent)
        .map{it.removeSuffix(" - Topic").trim()}.filter{it.length>=3}
        .distinctBy{it.lowercase(Locale.ROOT)}.take(3)

internal object NeroReplay {
    fun prefs(context:Context)=context.getSharedPreferences("nero_replay",Context.MODE_PRIVATE)
    fun today()=LocalDate.now().toEpochDay()
    fun enabled(context:Context)=prefs(context).getBoolean("enabled",true)
    fun setEnabled(context:Context,value:Boolean){prefs(context).edit().putBoolean("enabled",value).apply()}
    fun clear(context:Context){val p=prefs(context);val enabled=enabled(context);p.edit().clear().putBoolean("enabled",enabled).apply()}
    fun entries(context:Context):List<ReplayEntry>{
        val now=today()
        return prefs(context).all.mapNotNull{(key,value)->
            if(!key.startsWith("r:")||value !is String)return@mapNotNull null
            runCatching{
                val j=JSONObject(value)
                ReplayEntry(j.getLong("day"),j.getString("id"),j.getString("title"),j.optString("artist"),j.getLong("ms"))
            }.getOrNull()?.takeIf{it.day in now-89..now&&it.millis in 1L..86400000L}
        }
    }
    fun prune(context:Context){
        val p=prefs(context);val now=today();val edit=p.edit()
        val keys=p.all.keys.filter{it.startsWith("r:")}
        keys.filter{key->key.substringAfter("r:").substringBefore(':').toLongOrNull()?.let{it in now-89..now}!=true}.forEach{edit.remove(it)}
        val retained=keys.filter{key->key.substringAfter("r:").substringBefore(':').toLongOrNull()?.let{it in now-89..now}==true}
            .sortedByDescending{it.substringAfter("r:").substringBefore(':').toLongOrNull()?:0L}
        retained.drop(3000).forEach{edit.remove(it)}
        edit.apply()
    }
    fun add(context:Context,id:String,title:String,artist:String,millis:Long){
        if(!enabled(context)||id.isBlank()||id.length>512||title.isBlank()||millis !in 1L..6000L)return
        val day=today();val p=prefs(context);val key="r:"+day+":"+id
        val old=runCatching{JSONObject(p.getString(key,null)?:"{}").optLong("ms")}.getOrDefault(0L)
        if(old==0L&&p.all.size>=3001){prune(context);if(p.all.size>=3001)return}
        val entry=JSONObject().put("day",day).put("id",id).put("title",title.take(200)).put("artist",artist.take(200)).put("ms",(old+millis).coerceIn(0L,86400000L))
        p.edit().putString(key,entry.toString()).apply()
    }
}

// Count only intervals during which the primary player was continuously playing.
// Discontinuities, pauses and lifecycle changes reset the sample.
internal class NeroReplaySampler(private val context:Context,private val player:Player,scope:CoroutineScope):AutoCloseable{
    private var since=SystemClock.elapsedRealtime()
    private var id:String?=null
    private var accountOwner:String?=null
    private var accountGeneration:Long=0L
    private val prefs=NeroReplay.prefs(context)
    private val prefListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key=="enabled"||key==null)reset()}
    private fun reset(){
        since=SystemClock.elapsedRealtime()
        id=player.currentMediaItem?.mediaId?.takeIf{player.isPlaying&&NeroReplay.enabled(context)}
        accountOwner=NeroReplayPremiumStore.recordingOwner(context)
        accountGeneration=NeroReplayPremiumStore.generation
    }
    private val listener=object:Player.Listener{
        override fun onEvents(player:Player,events:Player.Events){
            if(events.contains(Player.EVENT_IS_PLAYING_CHANGED)||events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY))reset()
        }
    }
    private val job:Job
    private val identityJob:Job
    init{
        NeroReplay.prune(context)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        player.addListener(listener);reset()
        identityJob=scope.launch{NeroSupabase.activeUid.combine(NeroSupabase.identityReady){uid,ready->uid to ready}.collect{reset()}}
        job=scope.launch{
            while(isActive){
                delay(5000)
                val now=SystemClock.elapsedRealtime()
                val current=player.currentMediaItem
                val previous=id
                if(previous!=null&&current!=null&&current.mediaId==previous&&player.isPlaying){
                    val title=player.mediaMetadata.title?.toString().orEmpty().ifBlank{current.mediaMetadata.title?.toString().orEmpty()}
                    val artist=player.mediaMetadata.artist?.toString().orEmpty().ifBlank{current.mediaMetadata.artist?.toString().orEmpty()}
                    NeroReplay.add(context,previous,title,artist,now-since)
                    val owner=accountOwner
                    if(owner!=null&&accountGeneration==NeroReplayPremiumStore.generation)NeroReplayPremiumStore.add(context,owner,previous,title,artist,now-since)
                }
                reset()
            }
        }
    }
    override fun close(){job.cancel();identityJob.cancel();player.removeListener(listener);prefs.unregisterOnSharedPreferenceChangeListener(prefListener)}
}
