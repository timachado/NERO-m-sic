package br.com.timachado.neromusic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

internal fun recommendationId(value:String):String? =
    value.removePrefix("youtube:").takeIf{Regex("[A-Za-z0-9_-]{11}").matches(it)}

internal object NeroHiddenSongs{
    fun prefs(context:Context)=context.getSharedPreferences("nero_hidden_songs",Context.MODE_PRIVATE)
    fun read(context:Context):Set<String> = prefs(context).getStringSet("ids",emptySet()).orEmpty().mapNotNull(::recommendationId).toSet()
    fun toggle(context:Context,id:String):Boolean{
        val key=recommendationId(id)?:return false
        val current=read(context)
        val next=if(key in current)current-key else {if(current.size>=2000)return false;current+key}
        prefs(context).edit().putStringSet("ids",next).apply()
        return true
    }
    fun reset(context:Context){prefs(context).edit().remove("ids").apply()}
}
@Composable
internal fun rememberHiddenSongs():Set<String>{
    val context=LocalContext.current
    val prefs=remember(context){NeroHiddenSongs.prefs(context)}
    var rows by remember(prefs){mutableStateOf(NeroHiddenSongs.read(context))}
    DisposableEffect(prefs){
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key=="ids"||key==null)rows=NeroHiddenSongs.read(context)}
        prefs.registerOnSharedPreferenceChangeListener(listener)
        rows=NeroHiddenSongs.read(context)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return rows
}
internal fun filterRecommendedSongs(rows:List<NativeMusicHit>,hidden:Set<String>):List<NativeMusicHit> =
    rows.filterNot{recommendationId(it.id) in hidden}
