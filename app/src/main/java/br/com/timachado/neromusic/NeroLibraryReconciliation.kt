package br.com.timachado.neromusic

// Three-way reconciliation: the base is the last acknowledged server snapshot,
// not the current local library. Missing entries are deliberate deletions.
// Conflicts must be resolved before uploading a replacement snapshot.
internal data class NeroSyncConflict(val kind:String,val id:String)
internal data class NeroSyncResult(val snapshot:CloudLibrarySnapshot,val conflicts:List<NeroSyncConflict>)

internal fun reconcileNeroLibrary(base:CloudLibrarySnapshot,local:CloudLibrarySnapshot,remote:CloudLibrarySnapshot):NeroSyncResult{
    val conflicts=mutableListOf<NeroSyncConflict>()
    fun <T> merge(kind:String,b:List<T>,l:List<T>,r:List<T>,key:(T)->String):List<T>{
        val bm=b.associateBy(key);val lm=l.associateBy(key);val rm=r.associateBy(key)
        require(bm.size==b.size&&lm.size==l.size&&rm.size==r.size)
        return (lm.keys+rm.keys+bm.keys).mapNotNull{id->
            val before=bm[id];val here=lm[id];val there=rm[id]
            when{
                here==there->here
                here==before->there
                there==before->here
                else->{conflicts+=NeroSyncConflict(kind,id);here}
            }
        }
    }
    val favorites=merge("favorite",base.favorites,local.favorites,remote.favorites){it.id}
    // A playlist is an ordered entity. Concurrent edits to the same playlist
    // are explicit conflicts rather than silently losing order or resurrecting tracks.
    val playlists=merge("playlist",base.playlists,local.playlists,remote.playlists){it.id}
    val artists=merge("artist",base.artists,local.artists,remote.artists,::nativeArtistKey)
    val albums=merge("album",base.albums,local.albums,remote.albums){it.id}
    val history=merge("history",base.history,local.history,remote.history){it.ref.id}.sortedByDescending{it.at}.take(100)
    val hidden=mergeHiddenSongs(base.hiddenSongs,local.hiddenSongs,remote.hiddenSongs)
    if(hidden.size>2000)conflicts+=NeroSyncConflict("hiddenSongs","limit")
    return NeroSyncResult(CloudLibrarySnapshot(favorites,playlists,artists,albums,history,hidden),conflicts)
}

// Set membership is binary: if both devices changed the same item, they agree.
internal fun mergeHiddenSongs(base:Set<String>,local:Set<String>,remote:Set<String>):Set<String> =
    (base intersect local intersect remote)+(local-base)+(remote-base)
