package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroHiddenSongsTest {
    @Test fun acceptsCanonicalCatalogIds(){
        assertEquals("abcdefghijk",recommendationId("youtube:abcdefghijk"))
        assertEquals("abcdefghijk",recommendationId("abcdefghijk"))
    }
    @Test fun rejectsOtherSourcesAndMalformedIds(){
        assertNull(recommendationId("commons:123"))
        assertNull(recommendationId("https://example.com"))
        assertNull(recommendationId("abc"))
    }
    @Test fun hidesOnlySelectedRecording(){
        val a=NativeMusicHit("abcdefghijk","Faixa A","Artista",10L)
        val b=NativeMusicHit("12345678901","Faixa B","Artista",10L)
        assertEquals(listOf(b),filterRecommendedSongs(listOf(a,b),setOf(a.id)))
        assertEquals(listOf(a,b),filterRecommendedSongs(listOf(a,b),emptySet()))
    }
}
