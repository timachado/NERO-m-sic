package br.com.timachado.neromusic

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal fun rawArtistName(value:String):String =
    value.trim().replace(Regex("(?i)\\s+-\\s+Topic$"),"").trim()

// Keep established English group names intact. For a combined catalogue credit,
// normalize only the separators, never the artist IDs or track titles.
private val officialAndNames=listOf("Florence and the Machine","Earth, Wind and Fire","Earth Wind and Fire","Mumford and Sons","Tom Petty and the Heartbreakers","Kool and the Gang","KC and the Sunshine Band","Hall and Oates","Simon and Garfunkel","Gerry and the Pacemakers","Mike and the Mechanics","Nick Cave and the Bad Seeds","Echo and the Bunnymen","Hootie and the Blowfish","The Mamas and the Papas","Of Monsters and Men","Belle and Sebastian","Tegan and Sara","Crosby Stills and Nash","Peter Bjorn and John","Daryl Hall and John Oates","Noah and the Whale","Catfish and the Bottlemen","The Head and the Heart","Portugal. The Man")
private val collaborationSeparator=Regex("(?i)\\s+(?:and|feat\\.?|ft\\.?|featuring)\\s+")
internal fun artistCreditParts(value:String):List<String>{
    var clean=rawArtistName(value)
    if(clean.isBlank())return emptyList()
    val protected=mutableMapOf<String,String>()
    officialAndNames.sortedByDescending{it.length}.forEachIndexed{i,name->
        val pattern=Regex(Regex.escape(name),RegexOption.IGNORE_CASE)
        clean=pattern.replace(clean){match->val token="__NERO_GROUP_"+i+"__";protected[token]=match.value;token}
    }
    return collaborationSeparator.split(clean).map{part->
        var restored=part
        protected.forEach{(token,name)->restored=restored.replace(token,name)}
        rawArtistName(restored)
    }.filter{it.isNotBlank()}.distinctBy{it.lowercase(Locale.ROOT)}
}
internal fun displayArtistName(value:String):String=artistCreditParts(value).joinToString(" feat. ")
internal fun primaryArtistName(value:String):String=artistCreditParts(value).firstOrNull().orEmpty()

internal data class PortugueseBio(val text:String,val source:String,val avatar:String?=null)

internal suspend fun portugueseArtistBio(artist:String):PortugueseBio?=withContext(Dispatchers.IO){
    val name=rawArtistName(artist).take(180)
    if(name.length<3)return@withContext null
    val endpoint=Uri.parse("https://pt.wikipedia.org/w/api.php").buildUpon()
        .appendQueryParameter("action","query").appendQueryParameter("format","json")
        .appendQueryParameter("formatversion","2").appendQueryParameter("prop","extracts|pageprops|pageimages").appendQueryParameter("piprop","thumbnail").appendQueryParameter("pithumbsize","640").appendQueryParameter("pilicense","free")
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
        val image=page.optJSONObject("thumbnail")?.optString("source")?.takeIf{url->
            val uri=Uri.parse(url)
            uri.scheme=="https"&&uri.host=="upload.wikimedia.org"&&uri.userInfo==null
        }
        PortugueseBio(body,"https://pt.wikipedia.org/?curid="+id,image)
    }finally{connection.disconnect()}
}
