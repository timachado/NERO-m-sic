package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroDailyMixPremiumTest{
    private fun hit(i:Int)=NativeMusicHit(i.toString().padStart(11,'0'),"Faixa "+i,"Artista",10)
    @Test fun dailySeedsRotateWithoutDuplicates(){
        val artists=listOf("Artista A","Artista B","Artista C","Artista D","Artista A - Topic")
        val a=dailyPremiumSeeds(artists,100)
        assertEquals(a,dailyPremiumSeeds(artists,100))
        assertNotEquals(a,dailyPremiumSeeds(artists,101))
        assertEquals(3,a.size)
        assertEquals(3,a.distinctBy(::nativeArtistKey).size)
    }
    @Test fun cafeSearchIsBoundedAndExplicit(){
        assertEquals("Artista A músicas românticas",dailyMomentQuery("Artista A - Topic",DailyMoment.CAFE))
        assertTrue(dailyMomentQuery("A".repeat(180),DailyMoment.FOCUS).length<=100)
    }
    @Test fun collectionsExcludeHiddenKnownAndDuplicateTracks(){
        val input=listOf("Artista A" to (1..20).map(::hit),"Artista B" to (10..35).map(::hit))
        val groups=premiumDailyCollections(input,100,setOf(hit(1).id),setOf(hit(2).id))
        val rows=groups.flatMap{it.tracks}
        assertTrue(groups.all{it.tracks.size<=15})
        assertEquals(rows.size,rows.distinctBy{it.id}.size)
        assertFalse(rows.any{it.id==hit(1).id||it.id==hit(2).id})
    }
}
