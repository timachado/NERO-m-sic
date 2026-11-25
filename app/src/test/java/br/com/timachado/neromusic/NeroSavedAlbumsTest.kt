package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroSavedAlbumsTest{
    @Test fun equivalentLinksUseSameAlbumId(){
        assertEquals("PL12345678",savedAlbumKey("https://music.youtube.com/playlist?list=PL12345678"))
        assertEquals("PL12345678",savedAlbumKey("https://www.youtube.com/playlist?list=PL12345678&si=abc"))
    }
    @Test fun invalidSourcesAreRejected(){
        assertNull(savedAlbumKey("http://music.youtube.com/playlist?list=PL12345678"))
        assertNull(savedAlbumKey("https://evil.example/playlist?list=PL12345678"))
        assertNull(savedAlbumKey("https://music.youtube.com/playlist?list=abc"))
        assertNull(savedAlbumKey("https://music.youtube.com/playlist"))
    }
}
