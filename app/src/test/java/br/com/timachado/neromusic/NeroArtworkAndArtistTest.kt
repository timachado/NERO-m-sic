package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroArtworkAndArtistTest {
    @Test fun artistTopicSuffixIsDisplayOnly(){
        assertEquals("Matheus & Kauan",displayArtistName(" Matheus & Kauan - Topic "))
        assertEquals("Artist",displayArtistName("Artist - topic"))
        assertEquals("Topic Orchestra",displayArtistName("Topic Orchestra"))
        assertEquals("artist",nativeArtistKey("Artist - Topic"))
    }
    @Test fun artworkUsesKnownSourceAndPreservesFallback(){
        val id="abcdefghijk"
        val fallback="https://i.ytimg.com/vi/$id/mqdefault.jpg"
        val preferred="https://i.ytimg.com/vi/$id/hqdefault.jpg"
        assertEquals(fallback,NeroArtworkSources.preferred(fallback))
        NeroArtworkSources.record(id,preferred)
        assertEquals(preferred,NeroArtworkSources.preferred(fallback))
        assertEquals("https://example.org/photo.jpg",NeroArtworkSources.preferred("https://example.org/photo.jpg"))
        assertEquals("https://i.ytimg.com/vi/zzzzzzzzzzz/mqdefault.jpg",NeroArtworkSources.preferred("https://i.ytimg.com/vi/zzzzzzzzzzz/mqdefault.jpg"))
    }
}
