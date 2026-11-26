package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroSmartMixPremiumTest{
    private fun hit(i:Int,artist:String)=NativeMusicHit(i.toString().padStart(11,'0'),"Faixa "+i,artist,10)
    @Test fun modesRespectKnownArtistsAndHiddenTracks(){
        val a=hit(1,"Artista A");val b=hit(2,"Artista B");val c=hit(3,"Artista C");val d=hit(4,"Artista D")
        val groups=listOf(listOf(a,b,c,d,a))
        val seeds=listOf("Artista A");val known=listOf("Artista B")
        assertEquals(listOf(a),premiumMixRows(groups,seeds,known,PremiumMixMode.AFFINITY,emptySet(),emptySet(),15))
        assertEquals(listOf(c),premiumMixRows(groups,seeds,known,PremiumMixMode.DISCOVERY,emptySet(),setOf(d.id),15))
        assertEquals(3,premiumMixRows(groups,seeds,known,PremiumMixMode.DIVERSITY,setOf(b.id),emptySet(),15).size)
    }
    @Test fun maximumResultsAndNoDuplicates(){
        val groups=listOf((1..40).map{hit(it,"Artista "+(it%4))})
        val rows=premiumMixRows(groups,emptyList(),emptyList(),PremiumMixMode.DIVERSITY,emptySet(),emptySet(),30)
        assertEquals(30,rows.size)
        assertEquals(30,rows.distinctBy{it.id}.size)
    }
}
