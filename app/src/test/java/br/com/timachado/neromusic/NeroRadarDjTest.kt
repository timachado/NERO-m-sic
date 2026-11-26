package br.com.timachado.neromusic
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
class NeroRadarDjTest{
    @Test fun weeklyFiltersDatesAndArtists(){
        val today=LocalDate.parse("2026-11-26")
        val a=CatalogEntry("a","Nova","Sorriso Maroto",CatalogKind.ALBUM,"2026-11-24")
        assertEquals(listOf(a),radarWeekly(listOf(a,a.copy(id="b",releaseDate="2026-11-19"),a.copy(id="c",credit="Outro"),a.copy(id="d",releaseDate="2026-11-27")),today,listOf("Sorriso Maroto")))
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
