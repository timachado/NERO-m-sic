package br.com.timachado.neromusic

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class EmbeddedLyricsTest{
    private fun size(n:Int,sync:Boolean)=ByteArray(4){i->(n shr ((3-i)*(if(sync)7 else 8)) and (if(sync)127 else 255)).toByte()}
    private fun tag(version:Int,payload:ByteArray,flags:Int=0):ByteArray{
        val frame="USLT".toByteArray()+size(payload.size,version==4)+byteArrayOf(0,0)+payload
        return "ID3".toByteArray()+byteArrayOf(version.toByte(),0,flags.toByte())+size(frame.size,true)+frame
    }
    @Test fun utf8Lyrics(){
        val data=tag(4,byteArrayOf(3)+"por".toByteArray()+byteArrayOf(0)+"Verso de teste\nOutra linha".toByteArray())
        assertEquals("Verso de teste\nOutra linha",readEmbeddedLyrics(ByteArrayInputStream(data)))
    }
    @Test fun latin1Lyrics(){
        val data=tag(3,byteArrayOf(0)+"por".toByteArray()+byteArrayOf(0)+"Canção".toByteArray(Charsets.ISO_8859_1))
        assertEquals("Canção",readEmbeddedLyrics(ByteArrayInputStream(data)))
    }
    @Test fun truncatedRejected(){
        val data=tag(4,byteArrayOf(3)+"por".toByteArray()+byteArrayOf(0)+"teste".toByteArray())
        assertNull(readEmbeddedLyrics(ByteArrayInputStream(data.copyOf(data.size-2))))
    }
    @Test fun unsupportedFlagsRejected(){assertNull(readEmbeddedLyrics(ByteArrayInputStream(tag(4,byteArrayOf(3,0,0,0,0,65),128))))}
    @Test fun nonId3Rejected(){assertNull(readEmbeddedLyrics(ByteArrayInputStream(ByteArray(50))))}
}
