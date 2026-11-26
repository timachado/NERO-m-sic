package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroPlaylistRenameTest{
    @Test fun renameKeepsIdentityAndSongs(){
        val original=OnlineList("one","Pagodão do Bigode",listOf(OnlineRef("youtube:abcdefghijk","Música","Artista")))
        val other=OnlineList("two","Outra",emptyList())
        val next=renameOnlinePlaylist(listOf(original,other),"one"," Meu pagode ")
        assertEquals(listOf(original.copy(name="Meu pagode"),other),next)
    }
    @Test fun importedNameIsPreserved(){
        val original=OnlineList("one","Pagodão do Bigode",emptyList())
        assertEquals(listOf(original),importPortablePlaylists(emptyList(),listOf(original)))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsBlankName(){renameOnlinePlaylist(listOf(OnlineList("one","Original",emptyList())),"one"," ")}
}
