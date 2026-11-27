package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroLanAudioProbeTest {
    @Test fun validRanges(){
        assertEquals(0..9,audioProbeRange("bytes=0-9",100))
        assertEquals(90..99,audioProbeRange("bytes=90-",100))
        assertEquals(90..99,audioProbeRange("bytes=-10",100))
        assertEquals(90..99,audioProbeRange("bytes=90-999",100))
        assertEquals(0..99,audioProbeRange("bytes=-1000",100))
    }
    @Test fun invalidRanges(){
        listOf("bytes=-0","bytes=100-","bytes=10-5","bytes=0-1,3-4","bytes=-","bytes=999999999999999999999-").forEach{
            assertNull(audioProbeRange(it,100))
        }
    }
}
