package br.com.timachado.neromusic
import org.junit.Assert.assertEquals
import org.junit.Test
class NeroLyricFollowTest{
    @Test fun picksLastEqualTimestampAndHandlesSeek(){
        val lines=listOf(LyricLine(1000L,"a"),LyricLine(2000L,"b"),LyricLine(2000L,"c"),LyricLine(3500L,"d"))
        assertEquals(-1,activeLyricIndex(lines,999L))
        assertEquals(0,activeLyricIndex(lines,1000L))
        assertEquals(2,activeLyricIndex(lines,2000L))
        assertEquals(3,activeLyricIndex(lines,9000L))
        assertEquals(0,activeLyricIndex(lines,1200L))
        assertEquals(-1,activeLyricIndex(emptyList(),1200L))
    }
}
