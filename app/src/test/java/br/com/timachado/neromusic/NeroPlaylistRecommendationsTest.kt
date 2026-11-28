package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroPlaylistRecommendationsTest {
    private val songs=listOf(OnlineRef("youtube:aaaaaaaaaaa","Pura Adrenalina","Belo"))
    private fun hit(id:String,artist:String)=NativeMusicHit(id,"Song "+id,artist,0L)
    @Test fun relatedArtistsRotateWithoutUnrelatedGenres(){
        val first=playlistSuggestionSeeds(songs,0)
        val second=playlistSuggestionSeeds(songs,1)
        assertTrue("Belo" in first)
        assertTrue(first.size<=4)
        assertTrue(first.any{it!="Belo"})
        assertNotEquals(first,second)
        assertTrue(first.all{artist->neroGenres.first{it.label=="Pagode"}.matchesArtist(artist)})
    }
    @Test fun refreshExcludesShownSavedHiddenAndDuplicates(){
        val existing=hit("aaaaaaaaaaa","Belo")
        val hidden=hit("bbbbbbbbbbb","Belo")
        val shown=hit("ccccccccccc","Belo")
        val a=hit("ddddddddddd","Belo")
        val b=hit("eeeeeeeeeee","Exaltasamba")
        val unrelated=hit("ffffffffff1","Queen")
        val rows=playlistSuggestionBatch(listOf(listOf(existing,hidden,shown,a,b,b,unrelated)),songs,setOf(hidden.id),setOf(shown.id))
        assertEquals(setOf(a.id,b.id),rows.map{it.id}.toSet())
        assertEquals(2,rows.size)
    }
    @Test fun limitsRepetitionAndAlternatesRelatedArtists(){
        val same=(1..5).map{hit("bbbbbbbbbb"+it,"Belo")}
        val others=listOf(hit("cccccccccc1","Exaltasamba"),hit("cccccccccc2","Mumuzinho"),hit("cccccccccc3","Ferrugem"))
        val rows=playlistSuggestionBatch(listOf(same,others),songs,emptySet(),emptySet())
        assertTrue(rows.count{it.artist=="Belo"}<=2)
        assertTrue(rows.size<=6)
        assertTrue(rows.any{it.artist!="Belo"})
    }
}
