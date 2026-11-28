package br.com.timachado.neromusic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

internal data class NeroIdentifyHistoryRow(val match:NeroIdentifyMatch,val at:Long)
internal const val IDENTIFY_HISTORY_MAX=100
private fun NeroIdentifyMatch.historyKey():List<String> =
    listOf(title,artist,album).map{it.trim().lowercase(Locale.ROOT)}
internal fun mergeIdentifyHistory(rows:List<NeroIdentifyHistoryRow>,match:NeroIdentifyMatch,at:Long):List<NeroIdentifyHistoryRow>{
    if(match.title.isBlank()||match.artist.isBlank()||at<=0)return rows
    return (listOf(NeroIdentifyHistoryRow(match,at))+rows.filter{it.match.historyKey()!=match.historyKey()}).take(IDENTIFY_HISTORY_MAX)
}
internal object NeroIdentifyHistory{
    private fun prefs(context:Context,owner:String)=context.getSharedPreferences(
        "nero_identify_history_"+UUID.fromString(owner).toString(),Context.MODE_PRIVATE)
    fun enabled(context:Context,owner:String)=prefs(context,owner).getBoolean("enabled",false)
    fun setEnabled(context:Context,owner:String,enabled:Boolean){
        // Turning off history clears saved metadata, never touches music/playlists.
        val edit=prefs(context,owner).edit().putBoolean("enabled",enabled)
        if(!enabled)edit.remove("rows")
        edit.apply()
    }
    fun read(context:Context,owner:String):List<NeroIdentifyHistoryRow>{
        if(!enabled(context,owner))return emptyList()
        return runCatching{
            val raw=prefs(context,owner).getString("rows","[]").orEmpty()
            require(raw.length<=262144)
            val arr=JSONArray(raw)
            (0 until minOf(arr.length(),IDENTIFY_HISTORY_MAX)).mapNotNull{i->
                val r=arr.optJSONObject(i)?:return@mapNotNull null
                val title=r.optString("title").take(300)
                val artist=r.optString("artist").take(300)
                val at=r.optLong("at")
                if(title.isBlank()||artist.isBlank()||at<=0)null else NeroIdentifyHistoryRow(
                    NeroIdentifyMatch(title,artist,r.optString("album").take(300)),at)
            }
        }.getOrDefault(emptyList())
    }
    private fun write(context:Context,owner:String,rows:List<NeroIdentifyHistoryRow>){
        val json=JSONArray()
        rows.take(IDENTIFY_HISTORY_MAX).forEach{row->
            json.put(JSONObject().put("title",row.match.title).put("artist",row.match.artist).put("album",row.match.album).put("at",row.at))
        }
        prefs(context,owner).edit().putString("rows",json.toString()).apply()
    }
    fun add(context:Context,owner:String,match:NeroIdentifyMatch):List<NeroIdentifyHistoryRow>{
        if(!enabled(context,owner))return emptyList()
        val rows=mergeIdentifyHistory(read(context,owner),match,System.currentTimeMillis())
        write(context,owner,rows)
        return rows
    }
    fun remove(context:Context,owner:String,row:NeroIdentifyHistoryRow):List<NeroIdentifyHistoryRow>{
        val rows=read(context,owner).filterNot{it.match.historyKey()==row.match.historyKey()}
        if(enabled(context,owner))write(context,owner,rows)
        return rows
    }
    fun clear(context:Context,owner:String){prefs(context,owner).edit().remove("rows").apply()}
}
