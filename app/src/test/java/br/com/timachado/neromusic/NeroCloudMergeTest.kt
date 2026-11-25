package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroCloudMergeTest{
    private fun ref(id:String,title:String=id)=OnlineRef(id,title,"Artista")
    @Test fun preservesLocalOrderAndDeduplicates(){
        val local=CloudLibrarySnapshot(listOf(ref("a","Local A"),ref("b")),listOf(OnlineList("list1","Local",listOf(ref("a")))))
        val remote=CloudLibrarySnapshot(listOf(ref("a","Remote A"),ref("c")),listOf(OnlineList("list1","Remote",listOf(ref("b"),ref("a"))),OnlineList("list2","Nova",listOf(ref("c")))))
        val result=mergeCloudLibrary(local,remote)
        assertEquals(listOf("a","b","c"),result.favorites.map{it.id})
        assertEquals("Local A",result.favorites.first().title)
        assertEquals(listOf("list1","list2"),result.playlists.map{it.id})
        assertEquals("Local",result.playlists.first().name)
        assertEquals(listOf("a","b"),result.playlists.first().songs.map{it.id})
    }
    @Test fun emptyRemoteNeverRemovesLocal(){
        val local=CloudLibrarySnapshot(listOf(ref("a")),listOf(OnlineList("list1","Local",listOf(ref("a")))))
        assertEquals(local,mergeCloudLibrary(local,CloudLibrarySnapshot(emptyList(),emptyList())))
    }
    @Test fun repeatedRestoreIsIdempotent(){
        val remote=CloudLibrarySnapshot(listOf(ref("a")),listOf(OnlineList("list1","Remote",listOf(ref("a")))))
        assertEquals(remote,mergeCloudLibrary(remote,remote))
    }
}
