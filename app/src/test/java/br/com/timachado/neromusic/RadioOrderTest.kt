package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class RadioOrderTest {
    private val tracks=listOf(RadioCandidate(1,"A","Rock"),RadioCandidate(2,"A","Rock"),RadioCandidate(3,"B","Rock"),RadioCandidate(4,"C","Jazz"),RadioCandidate(5,"Artista desconhecido","Sem gênero"))
    @Test fun seedStartsAndUnrelatedExcluded(){
        val result=radioOrder(tracks,emptySet(),emptyList(),1)
        assertEquals(1L,result.first())
        assertEquals(setOf(1L,2L,3L),result.toSet())
        assertEquals(result.size,result.distinct().size)
    }
    @Test fun noSignalsNoFakeSuggestions(){assertTrue(radioOrder(tracks,emptySet(),emptyList(),null).isEmpty())}
    @Test fun favoritesProvideSignals(){assertEquals(setOf(1L,2L,3L),radioOrder(tracks,setOf(1L),emptyList(),null).toSet())}
    @Test fun unknownMetadataDoesNotMatch(){assertEquals(listOf(5L),radioOrder(tracks,emptySet(),emptyList(),5))}
    @Test fun limitRespected(){assertEquals(2,radioOrder(tracks,setOf(1L),emptyList(),null,2).size)}
}
