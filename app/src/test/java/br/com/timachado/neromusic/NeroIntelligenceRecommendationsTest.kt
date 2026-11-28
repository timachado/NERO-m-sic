package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroIntelligenceRecommendationsTest {
    private val dna=musicalDna(listOf("Dilsinho"),emptyList(),emptyList(),false)
    private val known=setOf(nativeArtistKey("Dilsinho"))
    private fun hit(id:String,artist:String)=NativeMusicHit(id,"Song "+id,artist,0L)
    @Test fun discoveryQueriesUseCuratedArtistsAndBoundedBudget(){
        val queries=intelligenceQueries(dna,known,NeroDiscoveryMode.EXPLORE)
        assertTrue(queries.size<=4)
        assertTrue(queries.any{nativeArtistKey(it)==nativeArtistKey("Dilsinho")})
        assertTrue(queries.count{nativeArtistKey(it) !in known}>=2)
        assertTrue(intelligenceQueries(musicalDna(emptyList(),emptyList(),emptyList(),false),emptySet(),NeroDiscoveryMode.BALANCED).isEmpty())
    }
    @Test fun filtersDuplicatesHiddenAndUnsupportedArtists(){
        val liked=hit("aaaaaaaaaaa","Dilsinho")
        val hidden=hit("bbbbbbbbbbb","Dilsinho")
        val familiar=hit("ccccccccccc","Dilsinho")
        val novel=hit("ddddddddddd","Ferrugem")
        val unknown=hit("eeeeeeeeeee","Unrelated")
        val rows=intelligenceRank(listOf(listOf(liked,hidden,familiar,novel,unknown,novel)),dna,known,setOf("youtube:"+liked.id),setOf(hidden.id),NeroDiscoveryMode.BALANCED)
        assertEquals(setOf(familiar.id,novel.id),rows.map{it.hit.id}.toSet())
        assertEquals(2,rows.size)
        assertTrue(rows.first{it.hit.id==familiar.id}.reason.contains("segue"))
        assertTrue(rows.first{it.hit.id==novel.id}.reason.contains("Pagode"))
    }
    @Test fun modesChangePriorityAndLimitArtistRepetition(){
        val familiar=(1..5).map{hit("aaaaaaaaaa"+it,"Dilsinho")}
        val new=listOf(hit("bbbbbbbbbb1","Ferrugem"),hit("cccccccccc1","Sorriso Maroto"),hit("dddddddddd1","Péricles"))
        val groups=listOf(familiar,new)
        val a=intelligenceRank(groups,dna,known,emptySet(),emptySet(),NeroDiscoveryMode.FAMILIAR)
        val b=intelligenceRank(groups,dna,known,emptySet(),emptySet(),NeroDiscoveryMode.EXPLORE)
        assertFalse(a.first().unfamiliar)
        assertTrue(b.first().unfamiliar)
        assertTrue(a.count{it.hit.artist=="Dilsinho"}<=3)
    }
}
