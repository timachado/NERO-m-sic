package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class OnlineLyricsMatchTest{
    @Test fun rejectsWrongArtistAndVersion(){
        val rows=listOf(LyricsMatch("Faixa","Outro",180.0,"linha",true),LyricsMatch("Faixa (Ao Vivo)","Artista",180.0,"linha",true),LyricsMatch("Faixa","Artista",190.0,"linha",true))
        assertNull(chooseLyrics("Faixa","Artista",180,rows))
    }
    @Test fun prefersSyncedAndNormalizesTopic(){
        val plain=LyricsMatch("Faixa","Artista",180.0,"linha",false)
        val synced=LyricsMatch("Faixa","Artista",181.0,"[00:01]linha",true)
        assertEquals(synced,chooseLyrics("faixa","Artista - Topic",180,listOf(plain,synced)))
    }
    @Test fun requiresKnownDuration(){assertNull(chooseLyrics("Faixa","Artista",0,emptyList()))}
}
