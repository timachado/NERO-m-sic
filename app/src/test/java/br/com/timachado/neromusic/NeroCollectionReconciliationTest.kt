package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test

class NeroCollectionReconciliationTest {
    private val album=CloudAlbum("album_id_123","Album","Artist")
    @Test fun independentCollectionsMergeAndRemovedArtistsStayRemoved(){
        val base=CloudLibrarySnapshot(emptyList(),emptyList(),listOf("Artist A"))
        val local=base.copy(artists=emptyList(),albums=listOf(album))
        val remote=base.copy(artists=listOf("Artist A","Artist B"))
        val result=reconcileNeroLibrary(base,local,remote)
        assertTrue(result.conflicts.isEmpty())
        assertEquals(listOf("Artist B"),result.snapshot.artists)
        assertEquals(listOf(album),result.snapshot.albums)
    }
    @Test fun removedAlbumDoesNotReappear(){
        val base=CloudLibrarySnapshot(emptyList(),emptyList(),albums=listOf(album))
        val remote=base.copy(artists=listOf("Artist B"))
        val result=reconcileNeroLibrary(base,base.copy(albums=emptyList()),remote)
        assertTrue(result.conflicts.isEmpty())
        assertTrue(result.snapshot.albums.isEmpty())
        assertEquals(listOf("Artist B"),result.snapshot.artists)
    }
    @Test fun artworkRejectsUntrustedHosts(){
        assertNull(cloudAlbumArtwork("https://example.org/cover.jpg"))
        assertEquals("https://i.ytimg.com/vi/id/hqdefault.jpg",cloudAlbumArtwork("https://i.ytimg.com/vi/id/hqdefault.jpg"))
    }
}
