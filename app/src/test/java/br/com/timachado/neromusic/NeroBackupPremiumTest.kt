package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroBackupPremiumTest{
    @Test fun restorePreservesExistingAndAddsOnlyMissing(){
        val a=OnlineRef("youtube:abcdefghijk","A","Artist")
        val b=OnlineRef("youtube:12345678901","B","Artist")
        val current=CloudLibrarySnapshot(listOf(a),listOf(OnlineList("list","Original",listOf(a))),listOf("Artista A"),history=listOf(CloudHeard(a,100)),hiddenSongs=setOf("abcdefghijk"))
        val incoming=CloudLibrarySnapshot(listOf(a,b),listOf(OnlineList("list","Outro nome",listOf(b))),listOf("artista a","Artista B"))
        val result=mergeNeroBackup(current,incoming)
        assertEquals(listOf(a,b),result.favorites)
        assertEquals("Original",result.playlists.single().name)
        assertEquals(listOf(a,b),result.playlists.single().songs)
        assertEquals(listOf("Artista A","Artista B"),result.artists)
        assertEquals(current.history,result.history)
        assertEquals(current.hiddenSongs,result.hiddenSongs)
        assertEquals(result,mergeNeroBackup(result,incoming))
    }
    @Test fun exportOmitsPrivateListeningPreferences(){
        val a=OnlineRef("youtube:abcdefghijk","A","Artist")
        val source=CloudLibrarySnapshot(listOf(a),emptyList(),history=listOf(CloudHeard(a,100)),hiddenSongs=setOf("abcdefghijk"))
        val backup=backupCollections(source)
        assertEquals(source.favorites,backup.favorites)
        assertTrue(backup.history.isEmpty());assertTrue(backup.hiddenSongs.isEmpty())
    }
    @Test fun ownerBindingStableAndSeparated(){
        val a="11111111-1111-1111-1111-111111111111"
        val b="22222222-2222-2222-2222-222222222222"
        assertEquals(64,backupOwner(a).length)
        assertNotEquals(backupOwner(a),backupOwner(b))
        assertFalse(backupOwner(a).contains(a))
    }
}
