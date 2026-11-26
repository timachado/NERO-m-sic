package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test

class NeroPlaylistImportTest{
    private val a=OnlineRef("youtube:abcdefghijk","A","Artist")
    private val b=OnlineRef("youtube:bcdefghijkl","B","Artist")
    @Test fun preservesOriginalAndSkipsRepeatedImport(){
        val original=OnlineList("same","Playlist",listOf(a))
        val guest=original.copy(songs=listOf(b,a))
        val result=importPortablePlaylists(listOf(original),listOf(guest))
        assertEquals(original,result.first())
        assertEquals(guest.songs,result.last().songs)
        assertNotEquals(original.id,result.last().id)
        assertEquals(result,importPortablePlaylists(result,listOf(guest)))
    }
    @Test fun exactDuplicateNotCopied(){
        val original=OnlineList("same","Playlist",listOf(a))
        assertEquals(listOf(original),importPortablePlaylists(listOf(original),listOf(original)))
    }
}
