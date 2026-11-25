package br.com.timachado.neromusic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

internal fun savedAlbumKey(url:String):String? = runCatching {
    val uri=URI(url)
    require(uri.scheme=="https" && uri.host in setOf("youtube.com","www.youtube.com","music.youtube.com","m.youtube.com") && uri.userInfo==null && uri.port in setOf(-1,443))
    val id=uri.rawQuery.orEmpty().split("&").firstOrNull{it.startsWith("list=")}?.substringAfter("=")
    require(id!=null && Regex("[A-Za-z0-9_-]{8,100}").matches(id))
    id
}.getOrNull()

internal object NeroSavedAlbums{
    fun prefs(context:Context)=context.getSharedPreferences("nero_saved_albums",Context.MODE_PRIVATE)
    fun read(context:Context):List<NativeCollection> = runCatching{
        val rows=JSONArray(prefs(context).getString("albums","[]"))
        (0 until minOf(rows.length(),500)).mapNotNull{i->
            val row=rows.optJSONObject(i)?:return@mapNotNull null
            val id=savedAlbumKey(row.optString("url"))?:return@mapNotNull null
            val title=row.optString("title").take(250).trim()
            if(title.isBlank())return@mapNotNull null
            val cover=row.optString("artwork").take(2048).takeIf{value->runCatching{
                val u=URI(value);u.scheme=="https"&&u.userInfo==null&&(u.host=="i.ytimg.com"||u.host?.endsWith(".googleusercontent.com")==true||u.host?.endsWith(".ggpht.com")==true)
            }.getOrDefault(false)}
            NativeCollection("https://music.youtube.com/playlist?list="+id,title,row.optString("credit").take(250),cover,NativeCollectionKind.ALBUM)
        }.distinctBy{savedAlbumKey(it.url)}
    }.getOrDefault(emptyList())
    fun toggle(context:Context,album:NativeCollection):Boolean{
        if(album.kind!=NativeCollectionKind.ALBUM||album.title.isBlank())return false
        val key=savedAlbumKey(album.url)?:return false
        val current=read(context)
        val next=if(current.any{savedAlbumKey(it.url)==key})current.filterNot{savedAlbumKey(it.url)==key}
            else {if(current.size>=500)return false;listOf(album)+current}
        val rows=JSONArray()
        next.forEach{entry->rows.put(JSONObject().put("url",entry.url).put("title",entry.title.take(250)).put("credit",entry.credit.take(250)).put("artwork",entry.artwork.orEmpty().take(2048)))}
        prefs(context).edit().putString("albums",rows.toString()).apply()
        return true
    }
}
@Composable
internal fun rememberSavedAlbums():List<NativeCollection>{
    val context=LocalContext.current
    val prefs=remember(context){NeroSavedAlbums.prefs(context)}
    var rows by remember(prefs){mutableStateOf(NeroSavedAlbums.read(context))}
    DisposableEffect(prefs){
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key=="albums"||key==null)rows=NeroSavedAlbums.read(context)}
        prefs.registerOnSharedPreferenceChangeListener(listener)
        rows=NeroSavedAlbums.read(context)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return rows
}
