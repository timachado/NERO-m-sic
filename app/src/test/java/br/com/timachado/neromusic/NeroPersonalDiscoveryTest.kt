package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroPersonalDiscoveryTest{
    @Test fun artistsExcludeKnownHiddenAndDuplicates(){
        val a=NativeMusicHit("abcdefghijk","A","Artista A",1)
        val b=NativeMusicHit("12345678901","B","Artista B",1)
        val c=NativeMusicHit("ABCDEFGHIJK","C","Artista C",1)
        val d=NativeMusicHit("01234567890","D","Artista C",1)
        assertEquals(listOf(c),discoveredArtists(listOf(a,b,c,d),listOf("Artista A - Topic"),setOf(b.id)))
    }
    @Test fun albumsMatchArtistAndExcludeSaved(){
        val a=NativeCollection("https://music.youtube.com/playlist?list=PL12345678","Album A","Artista A",null,NativeCollectionKind.ALBUM)
        val b=a.copy(url="https://music.youtube.com/playlist?list=PL87654321",title="Album B")
        assertEquals(listOf(b),discoveryAlbums(listOf(a,b,b.copy(credit="Outro")),listOf("Artista A"),setOf("PL12345678")))
    }
    @Test fun releaseSearchUsesCurrentYearAndCanonicalArtist(){
        assertEquals("lançamentos 2026 Artista A",discoveryReleaseQuery("Artista A - Topic",2026))
        assertTrue(discoveryReleaseQuery("A".repeat(180),2026).length<=100)
    }
    @Test fun releaseCandidatesUseSameAlbumCatalogAndExcludeAlreadyShown(){
        val a=NativeCollection("https://music.youtube.com/playlist?list=PL12345678","Album A","Artista A",null,NativeCollectionKind.ALBUM)
        val b=a.copy(url="https://music.youtube.com/playlist?list=PL87654321",title="Album B")
        val bad=b.copy(url="https://example.com/playlist?list=PL11111111")
        assertEquals(listOf(b),discoveryReleaseCandidates(listOf(a,b,b,bad,b.copy(credit="Outro")),listOf("Artista A"),setOf("PL12345678")))
    }
}
