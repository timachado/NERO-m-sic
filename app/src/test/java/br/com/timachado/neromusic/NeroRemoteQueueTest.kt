package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroRemoteQueueTest{
    @Test fun advancesOnlyAfterConfirmedPlaybackAndCompletion(){
        val q=NeroRemoteQueue(listOf("one","two","three"),1)
        val a=requireNotNull(q.start())
        assertEquals("two",a.mediaId)
        assertNull(q.confirmedCompleted(a))
        assertTrue(q.confirmedPlaying(a))
        assertTrue(q.confirmedPaused(a))
        assertNull(q.confirmedCompleted(a))
        assertTrue(q.confirmedPlaying(a))
        val b=requireNotNull(q.confirmedCompleted(a))
        assertEquals("three",b.mediaId)
        assertNull(q.confirmedCompleted(a))
        assertTrue(q.confirmedPlaying(b))
        assertNull(q.confirmedCompleted(b))
        assertEquals(NeroRemoteQueue.State.FINISHED,q.state)
    }
    @Test fun repeatedTracksHaveDistinctGenerations(){
        val q=NeroRemoteQueue(listOf("same","same"))
        val a=requireNotNull(q.start())
        assertTrue(q.confirmedPlaying(a))
        val b=requireNotNull(q.confirmedCompleted(a))
        assertEquals(a.mediaId,b.mediaId)
        assertNotEquals(a.generation,b.generation)
        assertFalse(q.confirmedPlaying(a))
        assertTrue(q.confirmedPlaying(b))
        assertNull(q.confirmedCompleted(a))
        assertEquals(NeroRemoteQueue.State.PLAYING,q.state)
    }
    @Test fun failureAndDisconnectCannotAdvance(){
        val q=NeroRemoteQueue(listOf("one","two"))
        val a=requireNotNull(q.start())
        assertTrue(q.failed(a))
        assertNull(q.confirmedCompleted(a))
        assertNull(q.start())
        q.disconnect()
        assertFalse(q.confirmedPlaying(a))
        assertEquals(NeroRemoteQueue.State.DISCONNECTED,q.state)
    }
    @Test fun snapshotsInputAndRejectsInvalidQueues(){
        val list=mutableListOf("one","two")
        val q=NeroRemoteQueue(list)
        list.clear()
        val a=requireNotNull(q.start())
        assertTrue(q.confirmedPlaying(a))
        assertEquals("two",q.confirmedCompleted(a)?.mediaId)
        assertTrue(runCatching{NeroRemoteQueue(emptyList())}.isFailure)
        assertTrue(runCatching{NeroRemoteQueue(listOf("one"),1)}.isFailure)
        assertTrue(runCatching{NeroRemoteQueue(listOf(""))}.isFailure)
    }
}
