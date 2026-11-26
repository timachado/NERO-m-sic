package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroGenreDiscoveryTest{
    @Test fun pagodeRejectsUnrelatedArtists(){
        val genre=requireNotNull(genreForQuery("pagode novidades"))
        assertTrue(genre.matchesArtist("Grupo Menos É Mais - Topic"))
        assertTrue(genre.matchesArtist("Turma do Pagode"))
        assertFalse(genre.matchesArtist("Clayton & Romário"))
        assertFalse(genre.matchesArtist("Hugo & Guilherme"))
        assertFalse(genre.matchesArtist("MC Tuto"))
        assertNull(genreForQuery("pagode do ex amor"))
    }
    @Test fun genresRecognizeLabelsAndAliases(){
        assertEquals("Sertanejo",genreForQuery("sertanejo novidades")?.label)
        assertTrue(requireNotNull(genreForQuery("Sertanejo")).matchesArtist("Clayton & Romário"))
        assertEquals("Pop Brasil",genreForQuery("Pop")?.label)
        assertNotNull(genreForQuery("classica"))
    }
    @Test fun expandedGenresShareDiscoveryAndReleaseOptions(){
        assertEquals(44,neroGenres.size)
        assertEquals(44,neroGenreLabels.size)
        assertEquals(45,neroReleaseGenreOptions.size)
        assertEquals(neroGenreLabels.size,neroGenreLabels.distinct().size)
        assertTrue(neroGenreLabels.all{genreForQuery(it)!=null})
        assertEquals("Funk",genreForQuery("funk carioca novidades")?.label)
        assertEquals("Funk",djGenre("funk brasileiro pra festa")?.label)
        assertEquals("Brega funk",djGenre("brega funk para treino")?.label)
        assertEquals("Rock nacional",djGenre("rock nacional animado")?.label)
        assertEquals("Piseiro",genreForQuery("pisadinha")?.label)
        assertEquals("Pop internacional",djGenre("pop internacional")?.label)
        assertEquals("R&B",genreForQuery("rnb")?.label)
        assertEquals(listOf("funk carioca"),radarGenres(listOf("MC Tuto")))
        assertFalse(requireNotNull(genreForQuery("Funk")).matchesArtist("Clayton & Romário"))
    }
    @Test fun selectionPreservesOnlyCuratedArtists(){
        val genre=requireNotNull(genreForQuery("Pagode"))
        val good=NativeMusicHit("abcdefghijk","Pagode","Sorriso Maroto",100)
        val bad=NativeMusicHit("abcdefghijl","Sertanejo","Clayton & Romário",100)
        assertEquals(listOf(good),genreTracks(genre,listOf(bad,good,good)))
    }
}
