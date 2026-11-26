package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroHiddenSyncTest {
    private val a="abcdefghijk"
    private val b="12345678901"
    private val c="ABCDEFGHIJK"
    @Test fun independentAdditionsMerge(){
        assertEquals(setOf(a,b),mergeHiddenSongs(emptySet(),setOf(a),setOf(b)))
    }
    @Test fun restoringOnOneDevicePropagates(){
        assertEquals(emptySet<String>(),mergeHiddenSongs(setOf(a),emptySet(),setOf(a)))
        assertEquals(setOf(b),mergeHiddenSongs(setOf(a),setOf(a,b),emptySet()))
    }
    @Test fun snapshotRetainsPreferenceAndOtherCollections(){
        val base=CloudLibrarySnapshot(emptyList(),emptyList(),hiddenSongs=setOf(a))
        val result=reconcileNeroLibrary(base,base.copy(hiddenSongs=setOf(b)),base.copy(hiddenSongs=setOf(a,c)))
        assertEquals(setOf(b,c),result.snapshot.hiddenSongs)
        assertTrue(result.conflicts.isEmpty())
    }
    @Test fun overflowDoesNotPermitUpload(){
        val base=CloudLibrarySnapshot(emptyList(),emptyList())
        val local=base.copy(hiddenSongs=(0 until 2000).map{it.toString().padStart(11,'0')}.toSet())
        val remote=base.copy(hiddenSongs=setOf(a))
        assertTrue(reconcileNeroLibrary(base,local,remote).conflicts.any{it.kind=="hiddenSongs"})
    }
}
