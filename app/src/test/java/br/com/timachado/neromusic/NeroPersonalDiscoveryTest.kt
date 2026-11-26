package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

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
    @Test fun releasesRequireMatchingArtistAndValidRecentDate(){
        val day=LocalDate.of(2026,11,25)
        val a=CatalogEntry("a","Álbum","Artista A",CatalogKind.ALBUM,"2026-11-20")
        assertEquals(listOf(a),discoveryReleases(listOf(a,a.copy(id="b",releaseDate="2026-09-01"),a.copy(id="c",releaseDate="2026"),a.copy(id="d",credit="Outro"),a.copy(id="e",releaseDate="2026-12-01")),listOf("Artista A"),day))
    }
}
