package br.com.timachado.neromusic
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
class NeroRadarDjTest{
    @Test fun discoveryFiltersArtistsWithoutInventingDates(){
        val a=CatalogEntry("album:a","Álbum","Sorriso Maroto",CatalogKind.ALBUM)
        assertEquals(listOf(a),radarDiscoveries(listOf(a,a,a.copy(id="album:b",credit="Outro"),a.copy(id="legacy")),listOf("Sorriso Maroto")))
        assertTrue(a.releaseDate.isBlank())
    }
    @Test fun tasteSuggestsGenre(){assertEquals(listOf("pagode"),radarGenres(listOf("Sorriso Maroto","Dilsinho")))}
    @Test fun djRequiresChoiceAndCanDeselect(){
        assertFalse(djCanCreate("",""))
        assertTrue(djQueries("","",listOf("Artista"),true).isEmpty())
        assertEquals("Romântico",djToggleMood("","Romântico"))
        assertEquals("",djToggleMood("Romântico","Romântico"))
        assertTrue(djCanCreate("","Treino"))
        assertEquals(listOf("Treino"),djQueries("","Treino",emptyList(),false))
        assertTrue(djCanCreate("rock",""))
        assertEquals(listOf("rock"),djQueries("rock","",emptyList(),false))
    }
    @Test fun djQueryLimits(){assertEquals(1,djQueries("Pagode romântico","Festa",emptyList(),false).size);assertEquals(5,djQueries("Pagode romântico","Festa",emptyList(),true).size)}
}
