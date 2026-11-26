package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroMixRankingTest{
    private fun hit(id:String,artist:String)=NativeMusicHit(id,id,artist,10L)
    @Test fun signalsCombineAndNormalize(){
        val ranked=rankNeroMixArtists(listOf("Artista A - Topic"),List(4){"Artista B"},listOf("artista b","Artista B","Artista A"),emptyList())
        assertEquals(listOf("Artista A","Artista B"),ranked)
    }
    @Test fun noSignalsDoesNotInventTaste(){
        assertTrue(rankNeroMixArtists(emptyList(),listOf("Unknown Artist",""),emptyList(),emptyList()).isEmpty())
    }
    @Test fun mixesAcrossArtistsAndExcludesHiddenAndDuplicates(){
        val a=hit("abcdefghijk","Artista A")
        val b=hit("12345678901","Artista B")
        val c=hit("ABCDEFGHIJK","Artista A")
        val d=hit("01234567890","Artista B")
        assertEquals(listOf(a,b),interleaveNeroMix(listOf(listOf(a,c),listOf(b,a,d)),setOf(c.id),setOf(d.id)))
    }
    @Test fun roundRobinPreservesVariety(){
        val a=hit("abcdefghijk","Artista A")
        val b=hit("12345678901","Artista B")
        val c=hit("ABCDEFGHIJK","Artista A")
        val d=hit("01234567890","Artista B")
        assertEquals(listOf(a,b,c,d),interleaveNeroMix(listOf(listOf(a,c),listOf(b,d)),emptySet(),emptySet()))
    }
}
