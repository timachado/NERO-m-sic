package br.com.timachado.neromusic

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import android.net.Uri

internal data class NativeMusicHit(val id:String,val title:String,val artist:String,val duration:Long){
    val thumbnail:String get()="https://i.ytimg.com/vi/$id/mqdefault.jpg"
    fun reference()=OnlineRef("youtube:"+id,title,artist)
}
internal fun musicHitFromRef(ref:OnlineRef):NativeMusicHit? {
    val id=ref.id.removePrefix("youtube:")
    return if(ref.id.startsWith("youtube:")&&Regex("[A-Za-z0-9_-]{11}").matches(id))
        NativeMusicHit(id,ref.title,ref.artist,0L) else null
}
internal fun youtubeRefFromItem(item:MediaItem):OnlineRef?{
    val id=item.mediaId
    if(!id.startsWith("youtube:")||!Regex("[A-Za-z0-9_-]{11}").matches(id.removePrefix("youtube:")))return null
    val title=item.mediaMetadata.title?.toString()?.takeIf{it.isNotBlank()}?:return null
    return OnlineRef(id,title,item.mediaMetadata.artist?.toString().orEmpty())
}

internal fun NativeMusicHit.queueItem():MediaItem {
    require(Regex("[A-Za-z0-9_-]{11}").matches(id))
    return MediaItem.Builder().setMediaId("youtube:"+id).setUri("nero-music://track/"+id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist)
            .setArtworkUri(Uri.parse(thumbnail)).build()).build()
}
internal fun nativeQueue(hits:List<NativeMusicHit>,selected:NativeMusicHit):Pair<List<MediaItem>,Int>{
    val all=hits.distinctBy{it.id}.take(30)
    val rows=if(all.any{it.id==selected.id})all else listOf(selected)+all.take(29)
    return rows.map{it.queueItem()} to rows.indexOfFirst{it.id==selected.id}.coerceAtLeast(0)
}
