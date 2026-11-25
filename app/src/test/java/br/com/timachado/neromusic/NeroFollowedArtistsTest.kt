package br.com.timachado.neromusic

import org.junit.Assert.assertEquals
import org.junit.Test

class NeroFollowedArtistsTest {
    @Test fun followedArtistsHavePriorityAndDeduplicate(){
        assertEquals(listOf("Artista A","Artista B","Artista C"),
            preferredMixArtists(listOf("Artista A - Topic","Artista B"),listOf("artista a","Artista C","Artista D")))
    }
    @Test fun emptyFollowsKeepExistingRecommendations(){
        assertEquals(listOf("Artista A","Artista B"),preferredMixArtists(emptyList(),listOf("Artista A","Artista B")))
    }
}
