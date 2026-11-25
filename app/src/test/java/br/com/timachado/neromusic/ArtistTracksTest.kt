package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class ArtistTracksTest {
    @Test fun matchesExactArtistAndTopicSuffix() {
        val a=NativeMusicHit("abcdefghijk","A","Zé Neto & Cristiano - Topic",120)
        val b=NativeMusicHit("lmnopqrstuv","B","Zé Neto & Cristiano",130)
        val c=NativeMusicHit("12345678901","C","Outro artista",140)
        assertEquals(listOf(a,b),artistTracks("Zé Neto & Cristiano",listOf(a,b,a,c)))
    }
    @Test fun rejectsBlankArtist(){assertTrue(artistTracks("",listOf(NativeMusicHit("abcdefghijk","A","",1))).isEmpty())}
}
