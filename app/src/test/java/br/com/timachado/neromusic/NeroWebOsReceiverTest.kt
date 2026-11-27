package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroWebOsReceiverTest {
    @Test fun receiverQueuePreservesOrderAndStartIndex(){
        val ids=listOf("aBcD1234_-Z","zYxW9876_-Q","aBcD1234_-Z")
        val result=NeroWebOsReceiver.receiverQueueUrl(ids,1,42)
        assertEquals("https://nero-connect-tv-195-test.timachado-mcp.workers.dev/#q=aBcD1234_-Z%2CzYxW9876_-Q%2CaBcD1234_-Z&i=1&t=42",result)
        assertTrue(runCatching{NeroWebOsReceiver.receiverQueueUrl(ids,3,0)}.isFailure)
        assertTrue(runCatching{NeroWebOsReceiver.receiverQueueUrl(listOf("bad&token"),0,0)}.isFailure)
        assertTrue(runCatching{NeroWebOsReceiver.receiverQueueUrl(List(101){"aBcD1234_-Z"},0,0)}.isFailure)
    }
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
