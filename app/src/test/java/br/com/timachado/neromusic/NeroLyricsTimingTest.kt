package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test

class NeroLyricsTimingTest{
    @Test fun preservesWordsAndTiming(){
        val words=neroLyricsTextLines("Primeira linha\n\nSegunda linha")
        val lrc=neroLyricsLrc(words,listOf(1234L,65432L),90000L)
        assertEquals(listOf(LyricLine(1234L,"Primeira linha"),LyricLine(65432L,"Segunda linha")),parseLyrics(lrc))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsBackwardMarks(){
        neroLyricsLrc(listOf("A","B"),listOf(200L,100L),1000L)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsIncomplete(){
        neroLyricsLrc(listOf("A","B"),listOf(200L),1000L)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsBeyondDuration(){
        neroLyricsLrc(listOf("A"),listOf(2000L),1000L)
    }
}
