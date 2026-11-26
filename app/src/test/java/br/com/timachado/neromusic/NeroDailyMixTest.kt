package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroDailyMixTest{
    private fun hit(n:Int)=NativeMusicHit(n.toString().padStart(11,'0'),"Faixa "+n,"Artista",10L)
    @Test fun deterministicWithinDayAndRotatesNextDay(){
        val input=listOf("Artista" to (1..20).map(::hit))
        val first=buildNeroDailyMixes(input,100,emptySet(),emptySet())
        assertEquals(first,buildNeroDailyMixes(input,100,emptySet(),emptySet()))
        assertNotEquals(first,buildNeroDailyMixes(input,101,emptySet(),emptySet()))
        assertEquals(12,first.single().tracks.size)
    }
    @Test fun excludesHiddenKnownAndCrossCollectionDuplicates(){
        val input=listOf("Artista A" to (1..10).map(::hit),"Artista B" to (5..20).map(::hit))
        val rows=buildNeroDailyMixes(input,100,setOf(hit(1).id),setOf(hit(5).id)).flatMap{it.tracks}
        assertEquals(rows.size,rows.distinctBy{it.id}.size)
        assertFalse(rows.any{it.id==hit(1).id||it.id==hit(5).id})
    }
    @Test fun noResultsDoNotProduceEmptyCollections(){
        assertTrue(buildNeroDailyMixes(listOf("Artista" to emptyList()),100,emptySet(),emptySet()).isEmpty())
    }
}
