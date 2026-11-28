package br.com.timachado.neromusic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

internal data class NeroSavedIndex(val online:Map<String,List<String>> = emptyMap(),val local:Map<Long,List<String>> = emptyMap()){
    fun destinations(ref:OnlineRef?,localId:Long?):List<String> =
        if(localId!=null)local[localId].orEmpty() else ref?.let{online[it.id].orEmpty()}.orEmpty()
}
internal fun buildSavedIndex(playlists:List<OnlineList>,local:List<Mix>):NeroSavedIndex{
    val onlineMap=linkedMapOf<String,MutableList<String>>()
    val localMap=linkedMapOf<Long,MutableList<String>>()
    playlists.forEach{list->list.songs.distinctBy{it.id}.forEach{ref->onlineMap.getOrPut(ref.id){mutableListOf()}.add(list.name)}}
    local.forEach{list->list.ids.distinct().forEach{id->localMap.getOrPut(id){mutableListOf()}.add(list.name)}}
    return NeroSavedIndex(onlineMap.mapValues{it.value.toList()},localMap.mapValues{it.value.toList()})
}
internal fun savedPlaylistLabel(names:List<String>):String?=when(names.size){
    0->null
    1->"Salva em "+names.first()
    else->"Salva em ${names.size} playlists"
}
internal val LocalNeroSavedIndex=staticCompositionLocalOf{NeroSavedIndex()}

@Composable
internal fun rememberNeroSavedIndex():NeroSavedIndex{
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val changes by NeroAccountSync.changes.collectAsState()
    var revision by remember{mutableIntStateOf(0)}
    var index by remember(uid,ready){mutableStateOf(NeroSavedIndex())}
    DisposableEffect(context){
        val library=context.getSharedPreferences("nero_library",Context.MODE_PRIVATE)
        val personal=context.getSharedPreferences("nero_personal",Context.MODE_PRIVATE)
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->
            if(key==null||key=="online_playlist_revision"||key=="mixes")revision++
        }
        library.registerOnSharedPreferenceChangeListener(listener)
        personal.registerOnSharedPreferenceChangeListener(listener)
        onDispose{library.unregisterOnSharedPreferenceChangeListener(listener);personal.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    LaunchedEffect(uid,ready,changes,revision){
        if(!ready)return@LaunchedEffect
        val owner=uid
        val result=try{withContext(Dispatchers.IO){buildSavedIndex(scopedOnlineLists(context,owner),mixes(context))}}
        catch(e:CancellationException){throw e}catch(_:Exception){NeroSavedIndex()}
        if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner)index=result
    }
    return index
}
