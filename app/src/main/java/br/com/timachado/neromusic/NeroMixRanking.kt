package br.com.timachado.neromusic

// Personalization uses only the signals supplied by the active account/guest.
internal fun rankNeroMixArtists(followed:List<String>,liked:List<String>,recent:List<String>,replay:List<String>,limit:Int=4):List<String>{
    data class Artist(val name:String,var score:Int=0)
    val scores=linkedMapOf<String,Artist>()
    fun add(name:String,weight:Int){
        val clean=NeroFollowedArtists.clean(name)
        val key=nativeArtistKey(clean)
        if(clean.length<3||key in setOf("unknown","unknown artist","artista desconhecido","<unknown>"))return
        scores.getOrPut(key){Artist(clean)}.score+=weight
    }
    followed.distinctBy(::nativeArtistKey).take(500).forEach{add(it,40)}
    liked.take(1000).groupBy(::nativeArtistKey).values.forEach{add(it.first(),minOf(it.size,5)*8)}
    recent.take(100).forEachIndexed{i,name->add(name,if(i<10)5 else 1)}
    replay.take(10).forEachIndexed{i,name->add(name,20-i)}
    return scores.entries.sortedWith(compareByDescending<Map.Entry<String,Artist>>{it.value.score}.thenBy{it.key})
        .take(limit.coerceIn(1,12)).map{it.value.name}
}

// Round robin across searches, with canonical deduplication and a per-artist cap.
internal fun interleaveNeroMix(groups:List<List<NativeMusicHit>>,excluded:Set<String>,hidden:Set<String>,limit:Int=24):List<NativeMusicHit>{
    require(limit in 1..60)
    val seen=mutableSetOf<String>()
    val counts=mutableMapOf<String,Int>()
    val result=mutableListOf<NativeMusicHit>()
    val maxPerArtist=if(groups.size<=1)limit else maxOf(6,(limit+groups.size-1)/groups.size)
    for(i in 0 until (groups.maxOfOrNull{it.size}?:0)){
        for(group in groups){
            val hit=group.getOrNull(i)?:continue
            val id=recommendationId(hit.id)?:continue
            if(id!=hit.id||id in excluded||id in hidden||id in seen)continue
            val artist=nativeArtistKey(hit.artist)
            if((counts[artist]?:0)>=maxPerArtist)continue
            seen+=id;counts[artist]=(counts[artist]?:0)+1;result+=hit
            if(result.size>=limit)return result
        }
    }
    return result
}
