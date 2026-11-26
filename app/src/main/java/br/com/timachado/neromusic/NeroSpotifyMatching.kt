package br.com.timachado.neromusic

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

// Match conservatively. Do not strip live/remix/acoustic/version qualifiers.
internal fun spotifyTrackMatches(item:ImportedItem,hits:List<NativeMusicHit>):List<NativeMusicHit>{
    if(item.kind!="track"||item.artist.isBlank()||item.title.isBlank())return emptyList()
    val title=genreKey(item.title)
    val artist=genreKey(displayArtistName(item.artist))
    if(title.isBlank()||artist.isBlank())return emptyList()
    return hits.filter{hit->
        Regex("[A-Za-z0-9_-]{11}").matches(hit.id)&&
        genreKey(hit.title)==title&&genreKey(displayArtistName(hit.artist))==artist
    }.distinctBy{it.id}
}
internal fun spotifyAutomaticMatch(item:ImportedItem,hits:List<NativeMusicHit>):NativeMusicHit? =
    spotifyTrackMatches(item,hits).singleOrNull()
internal fun spotifySearchTerm(item:ImportedItem):String =
    (item.title.take(65)+" "+item.artist.take(34)).trim().take(100)
internal object SpotifyCatalogLinks{
    fun prefs(context:Context):SharedPreferences=context.getSharedPreferences("nero_transfer_catalog_links",Context.MODE_PRIVATE)
    fun key(listId:String,position:Int)=listId+":"+position
    fun read(prefs:SharedPreferences,listId:String,position:Int):NativeMusicHit?=runCatching{
        val row=JSONObject(prefs.getString(key(listId,position),null)?:return null)
        val id=row.getString("id")
        val title=row.getString("title");val artist=row.getString("artist")
        require(Regex("[A-Za-z0-9_-]{11}").matches(id)&&title.isNotBlank()&&title.length<=300&&artist.length<=300)
        NativeMusicHit(id,title,artist,row.optLong("duration"))
    }.getOrNull()
    fun save(prefs:SharedPreferences,listId:String,position:Int,hit:NativeMusicHit){
        require(Regex("[A-Za-z0-9_-]{11}").matches(hit.id)&&hit.title.isNotBlank())
        val row=JSONObject().put("id",hit.id).put("title",hit.title.take(300)).put("artist",hit.artist.take(300)).put("duration",hit.duration)
        prefs.edit().putString(key(listId,position),row.toString()).apply()
    }
}
