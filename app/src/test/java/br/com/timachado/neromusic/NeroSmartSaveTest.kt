package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroSmartSaveTest {
    private val a=OnlineRef("youtube:aaaaaaaaaaa","Same title","Artist")
    private val b=OnlineRef("youtube:bbbbbbbbbbb","Same title","Artist")
    @Test fun exactIdentityAndMultipleDestinations(){
        val index=buildSavedIndex(listOf(OnlineList("1","Pagode",listOf(a,a)),OnlineList("2","Mix",listOf(a,b))),emptyList())
        assertEquals(listOf("Pagode","Mix"),index.destinations(a,null))
        assertEquals(listOf("Mix"),index.destinations(b,null))
        assertEquals("Salva em 2 playlists",savedPlaylistLabel(index.destinations(a,null)))
    }
    @Test fun localAndOnlineAreIndependent(){
        val index=buildSavedIndex(listOf(OnlineList("1","Online",listOf(a))),listOf(Mix("1","Local",listOf(7L,7L))))
        assertEquals(listOf("Local"),index.destinations(null,7L))
        assertEquals(listOf("Online"),index.destinations(a,null))
        assertTrue(index.destinations(null,8L).isEmpty())
        assertNull(savedPlaylistLabel(emptyList()))
    }
}
