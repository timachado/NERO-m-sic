package br.com.timachado.neromusic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun scopedOnlineLists(context:Context,uid:String?):List<OnlineList>{
    return if(uid==null)onlineLists(context)
    else NeroAccountLibraryStore.read(context,uid)?.pending?.playlists.orEmpty()
}

internal suspend fun saveScopedOnlineLists(context:Context,uid:String?,expected:List<OnlineList>,next:List<OnlineList>){
    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    if(uid!=null)NeroAccountSync.replacePlaylists(context,uid,expected,next)
    else withContext(Dispatchers.Main){
        require(NeroSupabase.activeUid.value==null&&onlineLists(context)==expected)
        saveOnlineLists(context,next)
    }
}

// Delete only the chosen playlist reference; the existing sync mechanism handles account edits.
internal suspend fun deleteScopedOnlinePlaylist(context:Context,uid:String?,id:String):List<OnlineList>{
    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    val current=withContext(Dispatchers.IO){scopedOnlineLists(context,uid)}
    val next=current.filterNot{it.id==id}
    if(next!=current)saveScopedOnlineLists(context,uid,current,next)
    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    return next
}
