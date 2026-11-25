package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroLibraryReconciliationTest{
    private val a=OnlineRef("youtube:abcdefghijk","A","Artist")
    private val b=OnlineRef("youtube:bcdefghijkl","B","Artist")
    private val c=OnlineRef("youtube:cdefghijklm","C","Artist")
    private fun snapshot(vararg songs:OnlineRef)=CloudLibrarySnapshot(songs.toList(),emptyList())
    @Test fun remoteDeletionDoesNotResurrect(){
        val result=reconcileNeroLibrary(snapshot(a),snapshot(a,b),snapshot())
        assertEquals(listOf(b),result.snapshot.favorites)
        assertTrue(result.conflicts.isEmpty())
    }
    @Test fun localDeletionDoesNotResurrect(){
        val result=reconcileNeroLibrary(snapshot(a),snapshot(),snapshot(a,c))
        assertEquals(listOf(c),result.snapshot.favorites)
        assertTrue(result.conflicts.isEmpty())
    }
    @Test fun independentAdditionsMerge(){
        val result=reconcileNeroLibrary(snapshot(a),snapshot(a,b),snapshot(a,c))
        assertEquals(setOf(a,b,c),result.snapshot.favorites.toSet())
        assertTrue(result.conflicts.isEmpty())
    }
    @Test fun concurrentPlaylistEditsAreConflicts(){
        val original=OnlineList("list","Original",listOf(a))
        val base=CloudLibrarySnapshot(emptyList(),listOf(original))
        val local=base.copy(playlists=listOf(original.copy(name="Renamed")))
        val remote=base.copy(playlists=listOf(original.copy(songs=listOf(a,b))))
        val result=reconcileNeroLibrary(base,local,remote)
        assertEquals(listOf(NeroSyncConflict("playlist","list")),result.conflicts)
    }
    @Test fun deleteVersusEditIsConflict(){
        val list=OnlineList("list","Original",listOf(a))
        val base=CloudLibrarySnapshot(emptyList(),listOf(list))
        val result=reconcileNeroLibrary(base,base.copy(playlists=emptyList()),base.copy(playlists=listOf(list.copy(songs=listOf(a,b)))))
        assertEquals(listOf(NeroSyncConflict("playlist","list")),result.conflicts)
    }
}
