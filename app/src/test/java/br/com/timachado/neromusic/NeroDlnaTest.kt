package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroDlnaTest {
    @Test fun transportAcknowledgementIsNotPlaybackConfirmation(){
        val uri="https://example.test/audio.mp3"
        assertTrue(NeroDlna.confirmedPlayback("PLAYING",uri,uri))
        assertFalse(NeroDlna.confirmedPlayback("TRANSITIONING",uri,uri))
        assertFalse(NeroDlna.confirmedPlayback("STOPPED",uri,uri))
        assertFalse(NeroDlna.confirmedPlayback("PLAYING",null,uri))
        assertFalse(NeroDlna.confirmedPlayback("PLAYING","https://example.test/another.mp3",uri))
    }

    @Test fun locationRequiresSuccessfulSsdpResponse(){
        assertEquals("http://192.168.1.20:8080/device.xml",NeroDlna.location("HTTP/1.1 200 OK\r\nLOCATION: http://192.168.1.20:8080/device.xml\r\n\r\n"))
        assertNull(NeroDlna.location("HTTP/1.1 404 Not Found\r\nLOCATION: http://192.168.1.20/device.xml"))
    }
    @Test fun endpointMustBelongToResponder(){
        val peer="192.168.1.20"
        assertNotNull(NeroDlna.safeEndpoint("http://192.168.1.20:8080/control",peer))
        for(bad in listOf("http://192.168.1.21/control","http://localhost/control","http://192.168.1.20@evil.test/control","http://192.168.1.20/control#x","https://192.168.1.20/control","file:///etc/passwd")){
            assertNull(NeroDlna.safeEndpoint(bad,peer))
        }
    }
    @Test fun metadataIncludesAudioTypeAndEscapesText(){
        val metadata=NeroDlna.mediaMetadata("https://example.test/track?a=1&b=2","Rock & Roll","Artista <A>","audio/mpeg")
        assertTrue(metadata.contains("Rock &amp; Roll"))
        assertTrue(metadata.contains("Artista &lt;A&gt;"))
        assertTrue(metadata.contains("http-get:*:audio/mpeg:*"))
        assertTrue(metadata.contains("a=1&amp;b=2"))
        assertTrue(metadata.contains("object.item.audioItem.musicTrack"))
    }
    @Test fun metadataRejectsUnsupportedMime(){
        assertTrue(runCatching{NeroDlna.mediaMetadata("https://example.test/a","A","B","video/mp4")}.isFailure)
    }
    @Test fun xmlEscapesMediaUrl(){
        assertEquals("https://example.test/audio?a=1&amp;b=2",NeroDlna.escape("https://example.test/audio?a=1&b=2"))
        assertEquals("&lt;&gt;&quot;&apos;",NeroDlna.escape("<>\"'"))
    }
}
