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
    @Test fun protectedLinkRemainsExplicit(){
        val p=SharedPlaylist("Playlist",listOf(SharedSong("Música","Artista")))
        val link="https://nero-test.example/s/0123456789abcdef0123456789abcdef"
        assertTrue(neroShareCaption(p,false,link).contains(link))
        assertTrue(neroShareCaption(p,false,link).contains("Google"))
    }
}
