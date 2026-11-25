package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroCrossfadeTest {
    @Test fun startsWithOutgoingOnly(){
        val (outgoing,incoming)=neroFadeGains(0f)
        assertEquals(1f,outgoing,0.0001f);assertEquals(0f,incoming,0.0001f)
    }
    @Test fun endsWithIncomingOnly(){
        val (outgoing,incoming)=neroFadeGains(1f)
        assertEquals(0f,outgoing,0.0001f);assertEquals(1f,incoming,0.0001f)
    }
    @Test fun gainsStayBoundedAndSumToUnity(){
        for(i in -10..110){
            val (outgoing,incoming)=neroFadeGains(i/100f)
            assertTrue(outgoing in 0f..1f);assertTrue(incoming in 0f..1f)
            assertEquals(1f,outgoing+incoming,0.0001f)
        }
    }
}