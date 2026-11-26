package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroReplayPremiumTest{
    @Test fun sumsOnlySelectedDaysAndCalculatesStreak(){
        val data=listOf(ReplayEntry(100,"a","A","Artist",60000),ReplayEntry(99,"a","A","Artist",30000),ReplayEntry(97,"b","B","Other",5000),ReplayEntry(93,"c","C","Other",10000),ReplayEntry(101,"d","D","Other",1000))
        val result=summarizePremiumReplay(data,100,7)
        assertEquals(95000L,result.total)
        assertEquals(3,result.activeDays)
        assertEquals(2,result.longestStreak)
        assertEquals(7,result.daily.size)
        assertEquals("a",result.tracks.first().id)
    }
    @Test fun emptyReplayHasNoActivity(){
        val result=summarizePremiumReplay(emptyList(),100,30)
        assertEquals(0L,result.total);assertEquals(0,result.activeDays);assertEquals(0,result.longestStreak)
    }
}
