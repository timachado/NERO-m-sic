package br.com.timachado.neromusic

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

internal data class Track(val id: Long, val title: String, val artist: String, val duration: Long, val uri: Uri) {
    fun mediaItem() = MediaItem.Builder().setMediaId(id.toString()).setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build()).build()
}
internal fun loadTracks(context: Context): List<Track> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val p = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
    val tracks = mutableListOf<Track>()
    context.contentResolver.query(collection, p, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            tracks += Track(id, c.getString(1) ?: "Sem título", c.getString(2)?.takeUnless { it == "<unknown>" } ?: "Artista desconhecido", c.getLong(3), ContentUris.withAppendedId(collection, id))
        }
    }
    return tracks
}
