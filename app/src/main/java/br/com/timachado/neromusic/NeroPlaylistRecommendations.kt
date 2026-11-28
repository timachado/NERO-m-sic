package br.com.timachado.neromusic

/** Suggestions depend only on the opened playlist, not on account listening history. */
internal fun playlistSuggestionSeeds(songs:List<OnlineRef>,round:Int):List<String>{
    val artists=rankNeroMixArtists(emptyList(),songs.map{it.artist},emptyList(),emptyList(),limit=12)
    if(artists.isEmpty())return emptyList()
    val known=songs.map{nativeArtistKey(it.artist)}.toSet()
    val related=neroGenres.filter{g->artists.any{g.matchesArtist(it)}}.flatMap{it.artists}
        .distinctBy(::nativeArtistKey).filter{nativeArtistKey(it) !in known}
    fun rotate(rows:List<String>,offset:Int):List<String>{
        if(rows.isEmpty())return rows
        val i=Math.floorMod(offset,rows.size)
        return rows.drop(i)+rows.take(i)
    }
    val familiar=rotate(artists,round)
    val discovery=rotate(related,round*2)
    return (familiar.take(2)+discovery.take(2)+familiar.drop(2)+discovery.drop(2))
        .distinctBy(::nativeArtistKey).take(4)
}

internal fun playlistSuggestionBatch(rows:List<List<NativeMusicHit>>,songs:List<OnlineRef>,hidden:Set<String>,shown:Set<String>,limit:Int=6):List<NativeMusicHit>{
    require(limit in 1..12)
    val known=songs.map{nativeArtistKey(it.artist)}.toSet()
    val genres=neroGenres.filter{g->songs.any{g.matchesArtist(it.artist)}}
    val excluded=(songs.map{it.id}+hidden+shown).mapNotNull(::recommendationId).toSet()
    val eligible=interleaveNeroMix(rows,excluded,emptySet(),limit=60).filter{hit->
        nativeArtistKey(hit.artist) in known||genres.any{it.matchesArtist(hit.artist)}
    }
    val familiar=eligible.filter{nativeArtistKey(it.artist) in known}.toMutableList()
    val novel=eligible.filterNot{nativeArtistKey(it.artist) in known}.toMutableList()
    val counts=mutableMapOf<String,Int>()
    val result=mutableListOf<NativeMusicHit>()
    fun take(pool:MutableList<NativeMusicHit>):NativeMusicHit?{
        while(pool.isNotEmpty()){
            val next=pool.removeAt(0);val key=nativeArtistKey(next.artist)
            if((counts[key]?:0)<2){counts[key]=(counts[key]?:0)+1;return next}
        }
        return null
    }
    while(result.size<limit){
        val row=if(result.size%2==0)take(familiar)?:take(novel)else take(novel)?:take(familiar)
        if(row==null)break
        result+=row
    }
    return result
}
