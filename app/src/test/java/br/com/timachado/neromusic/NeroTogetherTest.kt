package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroTogetherTest{
    private val host=TogetherSnapshot("youtube:abcdefghijk",10000,60000,true)
    @Test fun portableSnapshotRoundTrip(){assertEquals(host,parseTogetherWire(togetherWire(host)))}
    @Test fun correctsDriftWithBoundedLatency(){
        assertEquals(TogetherPlan(10100,true),togetherPlan(host,host.copy(positionMs=5000),200))
    }
    @Test fun avoidsSmallSeeks(){assertEquals(TogetherPlan(null,true),togetherPlan(host,host.copy(positionMs=9900),100))}
    @Test fun followsPauseWithoutLatencyCompensation(){assertEquals(TogetherPlan(10000,false),togetherPlan(host.copy(playing=false),host.copy(positionMs=3000),500))}
    @Test fun rejectsDifferentTrackDurationAndSlowNetwork(){
        assertNull(togetherPlan(host,host.copy(trackId="commons:1"),100))
        assertNull(togetherPlan(host,host.copy(durationMs=100000),100))
        assertNull(togetherPlan(host,host,2001))
    }
    @Test fun rejectsLocalIdsAndInvalidBounds(){
        assertFalse(togetherValid(host.copy(trackId="12345")))
        assertFalse(togetherValid(host.copy(positionMs=-1)))
        assertFalse(togetherValid(host.copy(positionMs=61000)))
    }
}
