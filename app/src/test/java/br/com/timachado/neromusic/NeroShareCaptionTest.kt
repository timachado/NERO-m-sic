package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroShareCaptionTest{
    private val host="nero-test.example"
    private val native="https://"+host+"/s/0123456789abcdef0123456789abcdef"
    private val song=SharedSong("Música","Artista","abcdefghijk")
    private val p=SharedPlaylist("Música",listOf(song))
    @Test fun metadataWithoutServer(){
        val caption=neroShareCaption(p,true,null,host)
        assertTrue(caption.contains("Música"));assertTrue(caption.contains("Artista"))
        assertFalse(caption.contains("http"))
    }
    @Test fun nativeLinkForTrackAndPlaylist(){
        assertTrue(neroShareCaption(p,true,native,host).contains(native))
        assertTrue(neroShareCaption(p,false,native,host).contains(native))
    }
    @Test fun externalLinksNeverAppear(){
        for(link in listOf("https://music.youtube.com/watch?v=abcdefghijk","https://open.spotify.com/track/abc","https://"+host+".evil.test/s/0123456789abcdef0123456789abcdef")){
            assertFalse(neroShareCaption(p,true,link,host).contains("https://"))
            assertFalse(neroShareCaption(p,false,link,host).contains("https://"))
        }
        assertFalse(neroShareCaption(p,true,native,"").contains("https://"))
    }
    @Test fun statusAndCaptionNormalizeArtistAndRemoveDuplicatePrefix(){
        val track=SharedSong("Hugo e Guilherme, Zé Vaqueiro - Flash (Minha Mente)","Hugo e Guilherme and Zé Vaqueiro","abcdefghijk")
        assertEquals("Flash (Minha Mente)",shareDisplayTitle(track))
        val caption=neroShareCaption(SharedPlaylist(track.title,listOf(track)),true,native,host)
        assertTrue(caption.contains("Hugo e Guilherme feat. Zé Vaqueiro"))
        assertFalse(caption.contains(" and "))
        assertTrue(caption.contains(native))
        assertEquals("A - B",shareDisplayTitle(SharedSong("A - B","Different Artist")))
    }
}
