package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroTogetherLiveTest {
 @Test fun playingCompensatesAgeAndHalfRoundTrip(){assertEquals(11500L,togetherTarget(10000,true,1000,1000))}
 @Test fun pausedDoesNotAdvance(){assertEquals(10000L,togetherTarget(10000,false,1000,1000))}
 @Test fun rejectsStaleOrSlow(){assertNull(togetherTarget(0,true,12001,0));assertNull(togetherTarget(0,true,0,2501))}
 @Test fun boundsPosition(){assertEquals(86400000L,togetherTarget(86400000,true,1000,1000));assertNull(togetherTarget(-1,true,0,0))}
}
