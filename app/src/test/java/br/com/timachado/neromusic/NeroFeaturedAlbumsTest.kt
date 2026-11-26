package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroFeaturedAlbumsTest{
    @Test fun dailyAndManualRotation(){
        val day=20000L
        val labels=featuredAlbumGenres("Todos",day,0).map{it.label}
        assertEquals(6,labels.distinct().size)
        assertNotEquals(labels,featuredAlbumGenres("Todos",day+1,0).map{it.label})
        assertNotEquals(labels,featuredAlbumGenres("Todos",day,1).map{it.label})
        val genre=requireNotNull(genreForQuery("Pagode"))
        assertEquals(listOf(genre),featuredAlbumGenres("Pagode",day,0))
        assertNotEquals(featuredAlbumSeeds(genre,day,0,1),featuredAlbumSeeds(genre,day+1,0,1))
    }
    @Test fun excludesGenericCreditsAndDuplicates(){
        fun album(id:String,title:String,artist:String)=NativeCollection("https://music.youtube.com/playlist?list="+id,title,artist,null,NativeCollectionKind.ALBUM)
        val a=album("abcdefghijk","Álbum A","Artista A")
        val b=album("abcdefghijl","Álbum B","Artista B")
        val generic=album("abcdefghijm","Top Brasil","Various Artists")
        assertEquals(listOf(a,b),featuredAlbumRows(listOf(listOf(generic,a),listOf(b,b))).let{rows->rows.sortedBy{it.title}})
    }
}
