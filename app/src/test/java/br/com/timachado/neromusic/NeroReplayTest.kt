package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroReplayTest {
    @Test fun filtersPeriodAndRanksByTime(){
        val rows=listOf(
            ReplayEntry(100,"a","A","Artist A",5000),
            ReplayEntry(99,"a","A","Artist A",10000),
            ReplayEntry(100,"b","B","Artist B",20000),
            ReplayEntry(92,"b","B","Artist B",99999),
            ReplayEntry(101,"c","C","Artist C",99999)
        )
        val result=replayTracks(rows,100,7)
        assertEquals(listOf("b","a"),result.map{it.id})
        assertEquals(35000L,result.sumOf{it.millis})
    }
    @Test fun groupsArtistCaseInsensitively(){
        val rows=listOf(ReplayTrack("a","A","Artist",5000),ReplayTrack("b","B","artist",10000))
        assertEquals(15000L,replayArtists(rows).single().second)
    }
    @Test fun mixesPrioritizeListeningAndDeduplicate(){
        val rows=listOf(ReplayTrack("a","A","Alpha",30000),ReplayTrack("b","B","Beta",10000))
        assertEquals(listOf("Alpha","Beta","Gamma"),mixArtists(rows,listOf("alpha","Gamma"),listOf("Delta")))
    }
}