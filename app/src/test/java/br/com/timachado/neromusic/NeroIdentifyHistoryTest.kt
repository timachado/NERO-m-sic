package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroIdentifyHistoryTest{
    @Test fun repeatMovesToTop(){
        val first=NeroIdentifyMatch("Track","Artist","Album")
        val second=NeroIdentifyMatch("Other","Artist","")
        val rows=mergeIdentifyHistory(mergeIdentifyHistory(emptyList(),first,1),second,2)
        val repeated=mergeIdentifyHistory(rows,NeroIdentifyMatch("track","ARTIST","album"),3)
        assertEquals(2,repeated.size)
        assertEquals(3L,repeated.first().at)
        assertEquals(second,repeated.last().match)
    }
    @Test fun versionsStaySeparate(){
        val a=NeroIdentifyMatch("Song","Artist","Studio")
        val b=NeroIdentifyMatch("Song","Artist","Live")
        assertEquals(2,mergeIdentifyHistory(mergeIdentifyHistory(emptyList(),a,1),b,2).size)
    }
    @Test fun historyIsBounded(){
        var rows=emptyList<NeroIdentifyHistoryRow>()
        for(i in 1..150)rows=mergeIdentifyHistory(rows,NeroIdentifyMatch("Song $i","Artist",""),i.toLong())
        assertEquals(100,rows.size)
        assertEquals(150L,rows.first().at)
        assertEquals(51L,rows.last().at)
    }
}
