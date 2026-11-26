package br.com.timachado.neromusic

// Three-way reconciliation: the base is the last acknowledged server snapshot,
// not the current local library. Missing entries are deliberate deletions.
// Conflicts must be resolved before uploading a replacement snapshot.
internal data class NeroSyncConflict(val kind:String,val id:String)
internal data class NeroSyncResult(val snapshot:CloudLibrarySnapshot,val conflicts:List<NeroSyncConflict>)

internal fun reconcileNeroLibrary(base:CloudLibrarySnapshot,local:CloudLibrarySnapshot,remote:CloudLibrarySnapshot):NeroSyncResult{
    val conflicts=mutableListOf<NeroSyncConflict>()
    fun <T> merge(kind:String,b:List<T>,l:List<T>,r:List<T>,resolve:((T?,T,T)->T?)?=null,key:(T)->String):List<T>{
        val bm=b.associateBy(key);val lm=l.associateBy(key);val rm=r.associateBy(key)
        require(bm.size==b.size&&lm.size==l.size&&rm.size==r.size)
        return (lm.keys+rm.keys+bm.keys).mapNotNull{id->
            val before=bm[id];val here=lm[id];val there=rm[id]
            when{
                here==there->here
                here==before->there
                there==before->here
                else->{
                    val resolved=if(here!=null&&there!=null)resolve?.invoke(before,here,there)else null
                    if(resolved!=null)resolved else {conflicts+=NeroSyncConflict(kind,id);here}
                }
            }
        }
    }
    val favorites=merge("favorite",base.favorites,local.favorites,remote.favorites){it.id}
    // Merge independent name/content edits, without guessing a playlist's order
    // when both devices changed the songs differently.
    val playlists=merge("playlist",base.playlists,local.playlists,remote.playlists,
        resolve=::mergeIndependentPlaylistEdits){it.id}
    val artists=merge("artist",base.artists,local.artists,remote.artists,key=::nativeArtistKey)
    val albums=merge("album",base.albums,local.albums,remote.albums){it.id}
    // Listening on two devices updates the same song's last-heard timestamp.
    // Keep the newest actual record; equal-time incompatible metadata and
    // delete-versus-new-listen remain conflicts rather than resurrecting data.
    val history=merge("history",base.history,local.history,remote.history,
        resolve={_,here,there->when{
            here.at>there.at->here
            there.at>here.at->there
            else->null
        }}){it.ref.id}.sortedByDescending{it.at}.take(100)
    val hidden=mergeHiddenSongs(base.hiddenSongs,local.hiddenSongs,remote.hiddenSongs)
    if(hidden.size>2000)conflicts+=NeroSyncConflict("hiddenSongs","limit")
    return NeroSyncResult(CloudLibrarySnapshot(favorites,playlists,artists,albums,history,hidden),conflicts)
}

// Set membership is binary: if both devices changed the same item, they agree.
internal fun mergeHiddenSongs(base:Set<String>,local:Set<String>,remote:Set<String>):Set<String> =
    (base intersect local intersect remote)+(local-base)+(remote-base)

// Returns null for simultaneous creation, incompatible renames or competing
// track changes. A deletion versus edit is still handled as a conflict by merge.
internal fun mergeIndependentPlaylistEdits(base:OnlineList?,local:OnlineList,remote:OnlineList):OnlineList?{
    if(base==null||base.id!=local.id||base.id!=remote.id)return null
    val name=when{
        local.name==remote.name->local.name
        local.name==base.name->remote.name
        remote.name==base.name->local.name
        else->return null
    }
    val songs=when{
        local.songs==remote.songs->local.songs
        local.songs==base.songs->remote.songs
        remote.songs==base.songs->local.songs
        else->return null
    }
    return base.copy(name=name,songs=songs)
}
