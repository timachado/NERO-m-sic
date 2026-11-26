package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroAlbumExplorerTest{
    private fun album(id:String,title:String,artist:String)=NativeCollection("https://music.youtube.com/playlist?list="+id,title,artist,null,NativeCollectionKind.ALBUM)
    @Test fun broadSelectionNeverBecomesOneGenre(){
        val genres=albumExploreGenres(20000,0,listOf("Zé Neto e Cristiano","Marília Mendonça"))
        assertEquals(6,genres.size)
        assertEquals(6,genres.map{it.label}.distinct().size)
        assertTrue(genres.any{it.label=="Sertanejo"})
        assertNotEquals(albumExploreGenres(20000,0,emptyList()),albumExploreGenres(20001,0,emptyList()))
    }
    @Test fun seedsRotate(){
        val genre=requireNotNull(genreForQuery("Funk"))
        assertNotEquals(albumExploreSeeds(genre,20000,0,1),albumExploreSeeds(genre,20001,0,1))
        assertEquals(4,albumExploreSeeds(genre,20000,0,4).distinct().size)
    }
    @Test fun albumsInterleaveDeduplicateAndLimitArtist(){
        val a=album("abcdefghijk","Um","Artista A")
        val b=album("abcdefghijl","Dois","Artista B")
        val groups=listOf(listOf(a,album("abcdefghijm","Três","Artista A"),album("abcdefghijn","Quatro","Artista A"),album("abcdefghijo","Cinco","Artista A")),listOf(b,a,album("abcdefghijp","Um","Artista A")))
        val rows=diverseAlbumRows(groups)
        assertEquals(listOf(a,b),rows.take(2))
        assertEquals(4,rows.size)
        assertEquals(3,rows.count{it.credit=="Artista A"})
    }
}
