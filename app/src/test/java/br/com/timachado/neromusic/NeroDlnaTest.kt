package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroDlnaTest {
    @Test fun remoteVolumeRequiresValidReadback(){
        assertEquals(0,NeroDlna.parseVolume("0"))
        assertEquals(100,NeroDlna.parseVolume("100"))
        assertEquals(25,NeroDlna.parseVolume(" 25 "))
        for(bad in listOf(null,"","-1","101","bad","2.5"))assertNull(NeroDlna.parseVolume(bad))
    }
    @Test fun volumeChangesStayWithinDeviceRange(){
        assertEquals(0,NeroDlna.adjustedVolume(2,-5))
        assertEquals(100,NeroDlna.adjustedVolume(98,5))
        assertEquals(55,NeroDlna.adjustedVolume(50,5))
        assertTrue(runCatching{NeroDlna.adjustedVolume(50,99)}.isFailure)
        assertTrue(runCatching{NeroDlna.adjustedVolume(-1,5)}.isFailure)
    }
    @Test fun dialEndpointIsBoundToDiscoveredPeer(){
        val peer="192.168.1.20"
        assertEquals("http://192.168.1.20:8080/apps/YouTube",NeroDlna.applicationEndpoint("http://192.168.1.20:8080/apps/",peer)?.toString())
        assertNull(NeroDlna.applicationEndpoint("http://192.168.1.21/apps/",peer))
        assertNull(NeroDlna.applicationEndpoint("http://192.168.1.20/apps/?x=1",peer))
        assertNull(NeroDlna.applicationEndpoint("http://192.168.1.20/apps/../",peer))
        assertNull(NeroDlna.applicationEndpoint("http://192.168.1.20/apps/%2e%2e/",peer))
    }
    @Test fun nativeLaunchAcceptsOnlyValidCatalogId(){
        assertEquals("aBcD1234_-Z",NeroDlna.nativeVideoId("youtube:aBcD1234_-Z"))
        assertNull(NeroDlna.nativeVideoId("local:aBcD1234_-Z"))
        assertNull(NeroDlna.nativeVideoId("youtube:aBcD1234_-Z&v=other"))
        val token=java.util.UUID.fromString("00000000-0000-4000-8000-000000000001")
        assertEquals("pairingCode="+token+"&v=aBcD1234_-Z&t=42.0",NeroDlna.launchParameters("aBcD1234_-Z",42,token))
        assertTrue(runCatching{NeroDlna.launchParameters("invalid&v=other",0,token)}.isFailure)
    }

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
