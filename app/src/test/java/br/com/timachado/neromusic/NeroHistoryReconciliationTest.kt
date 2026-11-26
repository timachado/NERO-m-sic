package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroHistoryReconciliationTest{
    private val a=CloudHeard(OnlineRef("youtube:abcdefghijk","A","Artist"),100)
    private val b=CloudHeard(OnlineRef("youtube:bcdefghijkl","B","Artist"),200)
    @Test fun clearDoesNotRestoreUnchangedRecords(){
        val base=CloudLibrarySnapshot(emptyList(),emptyList(),history=listOf(a))
        val remote=base.copy(history=listOf(b,a))
        val result=reconcileNeroLibrary(base,base.copy(history=emptyList()),remote)
        assertTrue(result.conflicts.isEmpty())
        assertEquals(listOf(b),result.snapshot.history)
    }
    @Test fun concurrentNewRecordsAreOrdered(){
        val base=CloudLibrarySnapshot(emptyList(),emptyList())
        val result=reconcileNeroLibrary(base,base.copy(history=listOf(a)),base.copy(history=listOf(b)))
        assertTrue(result.conflicts.isEmpty())
        assertEquals(listOf(b,a),result.snapshot.history)
    }
}
