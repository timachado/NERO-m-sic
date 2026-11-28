package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroIntelligenceTest {
    @Test fun historyExcludedUnlessRequested(){
        val result=musicalDna(listOf("Queen"),listOf("Queen","Queen"),listOf("Dilsinho"),false)
        assertEquals(listOf("Queen"),result.artists.map{it.name})
        assertEquals(2,result.artists.first().liked)
        assertEquals(0,result.artists.first().recent)
        assertTrue(result.artists.first().followed)
        assertTrue("Rock" in result.styles)
        assertFalse("Pagode" in result.styles)
    }
    @Test fun optionalHistoryAddsExplainableSignal(){
        val result=musicalDna(emptyList(),emptyList(),listOf("Dilsinho","Dilsinho"),true)
        assertEquals("Dilsinho",result.artists.first().name)
        assertEquals(2,result.artists.first().recent)
        assertTrue("Pagode" in result.styles)
    }
    @Test fun noSignalsDoNotInventPreferences(){
        val result=musicalDna(emptyList(),emptyList(),emptyList(),false)
        assertTrue(result.artists.isEmpty())
        assertTrue(result.styles.isEmpty())
    }
}
