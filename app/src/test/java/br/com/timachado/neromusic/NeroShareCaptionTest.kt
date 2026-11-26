package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroShareCaptionTest{
    @Test fun metadataWithoutServer(){
        val p=SharedPlaylist("Música",listOf(SharedSong("Música","Artista","abcdefghijk")))
        val caption=neroShareCaption(p,true,null)
        assertTrue(caption.contains("Música"));assertTrue(caption.contains("Artista"))
        assertFalse(caption.contains("http"));assertFalse(caption.contains("Google"))
    }
    @Test fun normalTrackLinkNeedsNoLogin(){
        val song=SharedSong("Música","Artista","abcdefghijk")
        val link=shareLink(song)
        assertEquals("https://music.youtube.com/watch?v=abcdefghijk",link)
        val caption=neroShareCaption(SharedPlaylist(song.title,listOf(song)),true,link)
        assertTrue(caption.contains(requireNotNull(link)));assertFalse(caption.contains("Google"))
    }
    @Test fun localSongHasNoFabricatedUrl(){
        assertNull(shareLink(SharedSong("Local","Artista")))
    }
}
