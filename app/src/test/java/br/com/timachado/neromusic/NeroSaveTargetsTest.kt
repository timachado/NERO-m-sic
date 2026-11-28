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
    @Test(expected=IllegalArgumentException::class) fun deletedDestinationDoesNotSilentlySave(){
        addNeroSaveTargets(NeroSaveTargets(emptyList(),listOf(a)),listOf(two),setOf("deleted"),false,null)
    }
}
