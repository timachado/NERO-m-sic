package br.com.timachado.neromusic

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.json.JSONObject

// Persist only a bounded catalog reference, not a resolved streaming URI or credentials.
internal data class NeroResumeRecord(val id:String,val title:String,val artist:String,val position:Long,val open:OpenTrack?)
internal object NeroResumeStore{
    private fun prefs(context:Context)=context.getSharedPreferences("nero_resume",Context.MODE_PRIVATE)
    fun markExplicitExit(context:Context){prefs(context).edit().putBoolean("explicit_exit",true).apply()}
    fun wasExplicitlyClosed(context:Context)=prefs(context).getBoolean("explicit_exit",false)
    fun save(context:Context,player:Player){
        if(player.isPlaying&&wasExplicitlyClosed(context))prefs(context).edit().putBoolean("explicit_exit",false).apply()
        val item=player.currentMediaItem?:return
        if(item.isNeroVideo()){prefs(context).edit().remove("last").apply();return}
        val ref=openRefFromItem(item)
        val id=item.mediaId
        if(!id.matches(Regex("youtube:[A-Za-z0-9_-]{11}"))&&id.toLongOrNull()==null&&ref?.openAudio==null)return
        val title=item.mediaMetadata.title?.toString().orEmpty().take(300)
        if(title.isBlank())return
        val row=JSONObject().put("id",id).put("title",title)
            .put("artist",item.mediaMetadata.artist?.toString().orEmpty().take(300))
            .put("position",player.currentPosition.coerceAtLeast(0L))
            .put("open",ref?.openAudio?.takeIf(::allowed)?.json())
        prefs(context).edit().putString("last",row.toString()).apply()
    }
    fun read(context:Context):NeroResumeRecord?=runCatching{
        val raw=prefs(context).getString("last",null)?:return null
        val row=JSONObject(raw)
        val id=row.getString("id")
        val open=row.optJSONObject("open")?.let(::decodeOpenTrack)?.takeIf{allowed(it)&&id=="commons:"+it.id}
        if(!id.matches(Regex("youtube:[A-Za-z0-9_-]{11}"))&&id.toLongOrNull()==null&&open==null)return null
        NeroResumeRecord(id,row.getString("title").take(300),row.optString("artist").take(300),row.optLong("position").coerceAtLeast(0L),open)
    }.getOrNull()
}
