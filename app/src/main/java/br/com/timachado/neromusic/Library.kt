package br.com.timachado.neromusic

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

internal data class Track(val id: Long, val title: String, val artist: String, val duration: Long, val uri: Uri,
    val album:String = "Álbum desconhecido", val albumId:Long = -1, val genre:String = "Sem gênero",
    val trackNumber:Int = 0) {
    fun mediaItem() = MediaItem.Builder().setMediaId(id.toString()).setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).setGenre(genre).build()).build()
}
internal fun loadTracks(context: Context): List<Track> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val columns = mutableListOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.TRACK)
    if(Build.VERSION.SDK_INT>=30) columns.add(MediaStore.Audio.Media.GENRE)
    val tracks = mutableListOf<Track>()
    fun read(c:android.database.Cursor,index:Int,fallback:String):String =
        c.getString(index)?.trim()?.takeIf { it.isNotBlank() && it!="<unknown>" } ?: fallback
    context.contentResolver.query(collection, columns.toTypedArray(), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            tracks += Track(id,read(c,1,"Sem título"),read(c,2,"Artista desconhecido"),c.getLong(3),
                ContentUris.withAppendedId(collection,id),read(c,4,"Álbum desconhecido"),c.getLong(5),
                if(Build.VERSION.SDK_INT>=30)read(c,7,"Sem gênero") else "Sem gênero",c.getInt(6))
        }
    }
    return tracks
}
