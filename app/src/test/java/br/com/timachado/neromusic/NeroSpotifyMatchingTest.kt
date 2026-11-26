package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroSpotifyMatchingTest{
    private val item=ImportedItem("Canção","Artista A","Álbum","spotify:track:example","track")
    private val hit=NativeMusicHit("abcdefghijk","Canção","Artista A - Topic",180)
    @Test fun exactArtistAndTitleMatch(){assertEquals(hit,spotifyAutomaticMatch(item,listOf(hit)))}
    @Test fun rejectsWrongArtistAndVersion(){
        assertNull(spotifyAutomaticMatch(item,listOf(hit.copy(artist="Artista B"),hit.copy(title="Canção (Ao Vivo)"))))
    }
    @Test fun ambiguousVersionsRequireReview(){
        assertNull(spotifyAutomaticMatch(item,listOf(hit,hit.copy(id="abcdefghijl"))))
        assertEquals(hit,spotifyAutomaticMatch(item,listOf(hit,hit)))
    }
    @Test fun ignoresEpisodes(){assertNull(spotifyAutomaticMatch(item.copy(kind="episode"),listOf(hit)))}
}
