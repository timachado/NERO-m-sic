package br.com.timachado.neromusic

// Explicit import only. Never overwrite an existing account playlist or undo
// its edits. Exact duplicates are skipped; same-ID differing lists are copied
// under deterministic import IDs so repeating the import is idempotent.
internal fun importPortablePlaylists(existing:List<OnlineList>,incoming:List<OnlineList>):List<OnlineList>{
    val next=existing.toMutableList()
    for(list in incoming){
        if(next.any{it.name==list.name&&it.songs==list.songs})continue
        val candidate=if(next.none{it.id==list.id})list else {
            val fingerprint=java.util.UUID.nameUUIDFromBytes(
                ("nero-import:"+list.id+":"+list.name+":"+list.songs.joinToString(","){it.id}).toByteArray(Charsets.UTF_8)
            ).toString()
            list.copy(id=fingerprint)
        }
        if(next.none{it.id==candidate.id})next.add(candidate)
    }
    return next
}

// Rename only the selected playlist, preserving its identity, references and order.
internal fun renameOnlinePlaylist(existing:List<OnlineList>,id:String,name:String):List<OnlineList>{
    val clean=name.trim()
    require(clean.isNotBlank()&&clean.length<=100)
    require(existing.count{it.id==id}==1)
    return existing.map{if(it.id==id)it.copy(name=clean)else it}
}
