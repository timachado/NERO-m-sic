package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroSaveTargetsTest {
    private val one=OnlineRef("youtube:abcdefghijk","One","Artist")
    private val two=OnlineRef("youtube:lmnopqrstuv","Two","Artist")
    private val a=OnlineList("a","A",listOf(one))
    private val b=OnlineList("b","B",emptyList())
    private val c=OnlineList("c","C",listOf(two))
    @Test fun multipleTargetsKeepUnselectedListsAndExistingReferences(){
        val input=NeroSaveTargets(listOf(one),listOf(a,b,c))
        val next=addNeroSaveTargets(input,listOf(one,two,one),setOf("a","b"),true,null)
        assertEquals(listOf(one,two),next.favorites)
        assertEquals(listOf(one,two),next.playlists[0].songs)
        assertEquals(listOf(one,two),next.playlists[1].songs)
        assertEquals(c,next.playlists[2])
        assertEquals(next,addNeroSaveTargets(next,listOf(one,two),setOf("a","b"),true,null))
    }
    @Test fun newPlaylistIsCreatedOnlyOnce(){
        val input=NeroSaveTargets(emptyList(),listOf(a))
        val draft=OnlineList("new","New",emptyList())
        val next=addNeroSaveTargets(input,listOf(two),emptySet(),false,draft)
        assertEquals(listOf(a,draft.copy(songs=listOf(two))),next.playlists)
        assertEquals(next,addNeroSaveTargets(next,listOf(two),emptySet(),false,draft))
        assertTrue(next.favorites.isEmpty())
    }
    @Test fun quickSaveSearchAndRecentOrder(){
        val rows=listOf(a.copy(name="Pagode"),b.copy(name="Rock"),c.copy(name="Pagode 2"))
        assertEquals(listOf("a","c","b"),quickSaveOrder(rows,listOf("a","deleted"),"").map{it.id})
        assertEquals(listOf("a","c"),quickSaveOrder(rows,listOf("a")," PAGODE ").map{it.id})
        assertTrue(quickSaveOrder(rows,emptyList(),"Jazz").isEmpty())
    }
    @Test fun recentTargetsDeduplicateAndStayBounded(){
        assertEquals(listOf("b","c","a"),quickSaveRecents(listOf("a","b"),listOf("b","c")))
        assertEquals(12,quickSaveRecents((1..20).map{it.toString()},listOf("new")).size)
    }
    @Test(expected=IllegalArgumentException::class) fun deletedDestinationDoesNotSilentlySave(){
        addNeroSaveTargets(NeroSaveTargets(emptyList(),listOf(a)),listOf(two),setOf("deleted"),false,null)
    }
}
