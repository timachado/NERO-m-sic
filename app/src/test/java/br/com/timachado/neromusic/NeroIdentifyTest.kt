package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NeroIdentifyTest {
    @Test fun encodesMonoPcm16WithCorrectWavHeader(){
        val wav=identifyWav(shortArrayOf(0,32767,-32768))
        val b=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF",String(wav,0,4,Charsets.US_ASCII))
        assertEquals("WAVE",String(wav,8,4,Charsets.US_ASCII))
        assertEquals(42,b.getInt(4))
        assertEquals(1,b.getShort(20).toInt())
        assertEquals(1,b.getShort(22).toInt())
        assertEquals(16000,b.getInt(24))
        assertEquals(16,b.getShort(34).toInt())
        assertEquals(6,b.getInt(40))
        assertEquals(32767,b.getShort(46).toInt())
        assertEquals(-32768,b.getShort(48).toInt())
    }
    @Test fun eightSecondClipHasBoundedSize(){
        assertEquals(256044,identifyWav(ShortArray(IdentifyRate*IdentifySeconds)).size)
    }
}
