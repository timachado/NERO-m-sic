package br.com.timachado.neromusic

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class NeroIdentifySampleTest {
    @Test fun pcmWavFormat(){
        val result=NeroIdentifySample.wav(shortArrayOf(123,-456))
        val buffer=ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF",String(result,0,4,Charsets.US_ASCII))
        assertEquals("WAVE",String(result,8,4,Charsets.US_ASCII))
        assertEquals(40,buffer.getInt(4))
        assertEquals(16000,buffer.getInt(24))
        assertEquals(4,buffer.getInt(40))
        assertEquals(123,buffer.getShort(44).toInt())
        assertEquals(-456,buffer.getShort(46).toInt())
    }
    @Test fun maximumSampleSize(){
        assertEquals(256044,NeroIdentifySample.wav(ShortArray(NeroIdentifySample.MAX_SAMPLES)).size)
    }
    @Test(expected=IllegalArgumentException::class)
    fun rejectsOversizedSample(){NeroIdentifySample.wav(ShortArray(NeroIdentifySample.MAX_SAMPLES+1))}
    @Test(expected=IllegalArgumentException::class)
    fun rejectsEmptySample(){NeroIdentifySample.wav(shortArrayOf())}
}
