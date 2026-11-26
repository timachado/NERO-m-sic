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
