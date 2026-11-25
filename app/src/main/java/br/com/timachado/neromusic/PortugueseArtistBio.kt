package br.com.timachado.neromusic

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal fun displayArtistName(value:String):String =
    value.trim().replace(Regex("(?i)\\s+-\\s+Topic$"),"").trim()

internal data class PortugueseBio(val text:String,val source:String)

internal suspend fun portugueseArtistBio(artist:String):PortugueseBio?=withContext(Dispatchers.IO){
    val name=displayArtistName(artist).take(180)
    if(name.length<3)return@withContext null
    val endpoint=Uri.parse("https://pt.wikipedia.org/w/api.php").buildUpon()
        .appendQueryParameter("action","query").appendQueryParameter("format","json")
        .appendQueryParameter("formatversion","2").appendQueryParameter("prop","extracts|pageprops")
        .appendQueryParameter("exintro","1").appendQueryParameter("explaintext","1")
        .appendQueryParameter("exchars","900").appendQueryParameter("redirects","1")
        .appendQueryParameter("titles",name).build().toString()
    val connection=URL(endpoint).openConnection() as HttpURLConnection
    try{
        connection.connectTimeout=5000;connection.readTimeout=5000
        connection.instanceFollowRedirects=false
        connection.setRequestProperty("User-Agent","NeroMusic/0.9 (artist biographies; https://timachado.ifree.page)")
        connection.setRequestProperty("Accept","application/json")
        if(connection.responseCode!=200)return@withContext null
        val json=connection.inputStream.bufferedReader(Charsets.UTF_8).use{reader->
            val buffer=CharArray(4096);val data=StringBuilder()
            while(true){val count=reader.read(buffer);if(count<0)break;data.append(buffer,0,count);if(data.length>65536)return@withContext null}
            JSONObject(data.toString())
        }
        val page=json.optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)?:return@withContext null
        if(page.has("missing")||page.optJSONObject("pageprops")?.has("disambiguation")==true)return@withContext null
        val title=page.optString("title")
        val body=page.optString("extract").trim()
        // Require an exact name match (optionally a parenthetical qualifier) and music-related text.
        if(nativeArtistKey(title.substringBefore(" ("))!=nativeArtistKey(name)||body.isBlank())return@withContext null
        val lower=body.lowercase(Locale.ROOT)
        if(listOf("cantor","cantora","músic","dupla","banda","compositor","rapper","grupo musical","disc jockey").none{it in lower})return@withContext null
        val id=page.optLong("pageid",-1)
        if(id<=0)return@withContext null
        PortugueseBio(body,"https://pt.wikipedia.org/?curid="+id)
    }finally{connection.disconnect()}
}
