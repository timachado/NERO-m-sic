package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroArtistCreditTest{
    @Test fun collaborationUsesFeat(){
        assertEquals("Hugo e Guilherme feat. Zé Vaqueiro",displayArtistName("Hugo e Guilherme and Zé Vaqueiro"))
        assertEquals("Hugo e Guilherme",primaryArtistName("Hugo e Guilherme and Zé Vaqueiro"))
        assertEquals(listOf("Hugo e Guilherme","Zé Vaqueiro"),artistCreditParts("Hugo e Guilherme feat. Zé Vaqueiro - Topic"))
        assertEquals("A feat. B",displayArtistName("A ft B"))
    }
    @Test fun keepsOfficialGroupsAndDuos(){
        assertEquals("Florence and the Machine",displayArtistName("Florence and the Machine"))
        assertEquals("Florence and the Machine feat. Artist",displayArtistName("Florence and the Machine and Artist"))
        assertEquals("Hugo & Guilherme",displayArtistName("Hugo & Guilherme"))
        assertEquals("Hugo e Guilherme",displayArtistName("Hugo e Guilherme"))
    }
    @Test fun mixLabelsNormalizeCachedArtistCredits(){
        assertEquals("Daily Mix • Hugo e Guilherme feat. Zé Vaqueiro",artistMixLabel("Daily Mix","Hugo e Guilherme and Zé Vaqueiro"))
        assertEquals("Daily Mix • Netto Brito feat. Luan Pereira LP",displayMixLabel("Daily Mix • Netto Brito and Luan Pereira LP"))
        assertEquals("Daily Mix • Florence and the Machine",displayMixLabel("Daily Mix • Florence and the Machine"))
        assertEquals("Meu mix NERO",displayMixLabel("Meu mix NERO"))
    }
    @Test fun normalizedCreditIsIdempotent(){
        val value="A and B feat. C"
        assertEquals(displayArtistName(value),displayArtistName(displayArtistName(value)))
    }
}
