package br.com.timachado.neromusic

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

internal class LyricsAuthException:Exception()
internal data class LyricsMatch(val title:String,val artist:String,val seconds:Double,val text:String,val synced:Boolean)
internal fun lyricsName(value:String):String=value.trim().replace(Regex(" - Topic$",RegexOption.IGNORE_CASE),"").replace(Regex("\\s+")," ").lowercase(Locale.ROOT)
internal fun chooseLyrics(title:String,artist:String,seconds:Long,rows:List<LyricsMatch>):LyricsMatch?{
    if(seconds<=0||title.isBlank()||artist.isBlank())return null
    val matches=rows.filter{lyricsName(it.title)==lyricsName(title)&&lyricsName(it.artist)==lyricsName(artist)&&it.seconds.isFinite()&&abs(it.seconds-seconds)<=2.0&&it.text.isNotBlank()}
    return matches.sortedWith(compareByDescending<LyricsMatch>{it.synced}.thenBy{abs(it.seconds-seconds)}).firstOrNull()
}
internal object OnlineLyrics{
    suspend fun lookup(title:String,artist:String,seconds:Long):LyricsMatch?=withContext(Dispatchers.IO){
        if(seconds<=0||title.isBlank()||artist.isBlank())return@withContext null
        val uri=Uri.parse("https://lrclib.net/api/search").buildUpon()
            .appendQueryParameter("track_name",title.trim().take(200))
            .appendQueryParameter("artist_name",artist.trim().removeSuffix(" - Topic").take(200)).build()
        val connection=URL(uri.toString()).openConnection() as HttpURLConnection
        connection.connectTimeout=10000;connection.readTimeout=15000;connection.instanceFollowRedirects=false
        connection.setRequestProperty("Accept","application/json")
        connection.setRequestProperty("User-Agent","NERO Music/0.9.19 (https://github.com/timachado/NERO-m-sic)")
        try{
            val status=connection.responseCode
            if(status==401||status==403)throw LyricsAuthException()
            if(status==404)return@withContext null
            check(status==200)
            val bytes=connection.inputStream.use{input->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=1024*1024);out.write(buffer,0,n)}
                out.toByteArray()
            }
            val rows=JSONArray(bytes.toString(Charsets.UTF_8))
            val candidates=(0 until minOf(rows.length(),50)).mapNotNull{i->
                val row=rows.optJSONObject(i)?:return@mapNotNull null
                if(row.optBoolean("instrumental"))return@mapNotNull null
                val synced=row.optString("syncedLyrics").takeUnless{it.isBlank()||it=="null"}
                val plain=row.optString("plainLyrics").takeUnless{it.isBlank()||it=="null"}
                val lyric=synced?:plain?:return@mapNotNull null
                if(lyric.length>256*1024)return@mapNotNull null
                LyricsMatch(row.optString("trackName"),row.optString("artistName"),row.optDouble("duration",Double.NaN),lyric,synced!=null)
            }
            chooseLyrics(title,artist,seconds,candidates)
        }finally{connection.disconnect()}
    }
}
