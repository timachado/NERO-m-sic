package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroWebOsReceiverTest {
    @Test fun usesFixedOriginAndEncodesTrackMetadata(){
        val result=NeroWebOsReceiver.receiverUrl("aBcD1234_-Z","A & B","Nome #1",42)
        assertEquals("https://nero-tv-receiver-test.timachado-mcp.workers.dev/#v=aBcD1234_-Z&title=A+%26+B&artist=Nome+%231&t=42",result)
        assertTrue(NeroWebOsReceiver.receiverUrl("aBcD1234_-Z","","",-5).endsWith("&t=0"))
        assertTrue(NeroWebOsReceiver.receiverUrl("aBcD1234_-Z","","",100000).endsWith("&t=86400"))
        assertTrue(runCatching{NeroWebOsReceiver.receiverUrl("invalid&v=other","","",0)}.isFailure)
    }
    @Test fun choosesWebOsOnlyForDiscoveredWebOsName(){
        assertTrue(NeroWebOsReceiver.isWebOs("[LG] webOS TV LF5900gg"))
        assertFalse(NeroWebOsReceiver.isWebOs("Samsung TV"))
    }
}
