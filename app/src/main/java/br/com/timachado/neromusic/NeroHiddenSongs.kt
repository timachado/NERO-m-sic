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
internal fun scopedHiddenSongs(context:Context,uid:String?):Set<String>{
    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    return if(uid==null)NeroHiddenSongs.read(context) else NeroAccountLibraryStore.read(context,uid)?.pending?.hiddenSongs.orEmpty()
}
internal suspend fun toggleScopedHiddenSong(context:Context,uid:String?,id:String):Boolean{
    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    if(uid==null)return NeroHiddenSongs.toggle(context,id)
    NeroAccountSync.toggleHiddenSong(context,uid,id);return true
}
internal suspend fun resetScopedHiddenSongs(context:Context,uid:String?){
    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    if(uid==null)NeroHiddenSongs.reset(context) else NeroAccountSync.resetHiddenSongs(context,uid)
}
@Composable
internal fun rememberHiddenSongs():Set<String>{
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val changes by NeroAccountSync.changes.collectAsState()
    if(!ready)return emptySet()
    if(uid!=null)return remember(uid,changes){runCatching{scopedHiddenSongs(context,uid)}.getOrDefault(emptySet<String>())}
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
    rows.filterNot{row->recommendationId(row.id)?.let{it in hidden}==true}
