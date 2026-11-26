package br.com.timachado.neromusic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

internal object NeroFollowedArtists {
    private fun prefs(context:Context)=context.getSharedPreferences("nero_followed_artists",Context.MODE_PRIVATE)
    fun clean(name:String)=name.trim().removeSuffix(" - Topic").trim().take(180)
    fun read(context:Context):List<String> = prefs(context).getStringSet("artists",emptySet()).orEmpty().map(::clean)
        .filter{it.isNotBlank()}.distinctBy(::nativeArtistKey).sortedBy(::nativeArtistKey).take(500)
    fun toggle(context:Context,name:String):Boolean{
        val value=clean(name)
        if(value.isBlank())return false
        val key=nativeArtistKey(value)
        val current=read(context)
        val next=if(current.any{nativeArtistKey(it)==key})current.filterNot{nativeArtistKey(it)==key}
            else {if(current.size>=500)return false;current+value}
        prefs(context).edit().putStringSet("artists",next.toSet()).apply()
        return true
    }
    fun preference(context:Context)=prefs(context)
}
@Composable
internal fun rememberFollowedArtists():List<String>{
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val changes by NeroAccountSync.changes.collectAsState()
    if(!ready)return emptyList()
    if(uid!=null){
        val owner=uid
        return remember(owner,changes){runCatching{NeroAccountLibraryStore.read(context,requireNotNull(owner))?.pending?.artists.orEmpty()}.getOrDefault(emptyList<String>())}
    }
    val prefs=remember(context){NeroFollowedArtists.preference(context)}
    var rows by remember(prefs){mutableStateOf(NeroFollowedArtists.read(context))}
    DisposableEffect(prefs){
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key=="artists"||key==null)rows=NeroFollowedArtists.read(context)}
        prefs.registerOnSharedPreferenceChangeListener(listener)
        rows=NeroFollowedArtists.read(context)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return rows
}
internal fun preferredMixArtists(followed:List<String>,suggested:List<String>):List<String> =
    (followed+suggested).map(NeroFollowedArtists::clean).filter{it.length>=3}.distinctBy(::nativeArtistKey).take(3)
