package br.com.timachado.neromusic

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import android.net.Uri
import android.os.Bundle

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
    return OnlineRef(id,title,item.mediaMetadata.extras?.getString("nero_artist_original")?:item.mediaMetadata.artist?.toString().orEmpty())
}

internal fun NativeMusicHit.queueItem(video:Boolean=false):MediaItem {
    require(Regex("[A-Za-z0-9_-]{11}").matches(id))
    return MediaItem.Builder().setMediaId("youtube:"+id).setUri("nero-music://"+(if(video)"video" else "track")+"/"+id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(displayArtistName(artist))
            .setArtworkUri(Uri.parse(thumbnail)).setExtras(Bundle().apply{putBoolean("nero_video_mode",video);putString("nero_artist_original",artist)}).build()).build()
}
internal fun nativeQueue(hits:List<NativeMusicHit>,selected:NativeMusicHit,video:Boolean=false):Pair<List<MediaItem>,Int>{
    val all=hits.distinctBy{it.id}.take(30)
    val rows=if(all.any{it.id==selected.id})all else listOf(selected)+all.take(29)
    return rows.map{it.queueItem(video)} to rows.indexOfFirst{it.id==selected.id}.coerceAtLeast(0)
}

internal enum class NativeCollectionKind { ARTIST, ALBUM }
internal data class NativeCollection(val url:String,val title:String,val credit:String,val artwork:String?,val kind:NativeCollectionKind)

internal fun MediaItem.isNeroVideo():Boolean=mediaMetadata.extras?.getBoolean("nero_video_mode",false)==true
internal fun MediaItem.videoQuality():Int=mediaMetadata.extras?.getInt("nero_video_quality",0)?.takeIf{it in listOf(0,360,480,720,1080)}?:0
internal fun MediaItem.withVideoMode(video:Boolean,quality:Int=videoQuality()):MediaItem?{
    val hit=youtubeRefFromItem(this)?.let(::musicHitFromRef)?:return null
    val extras=Bundle(mediaMetadata.extras?:Bundle()).apply{putBoolean("nero_video_mode",video);putInt("nero_video_quality",quality)}
    require(quality in listOf(0,360,480,720,1080))
    return hit.queueItem(video).buildUpon().setUri("nero-music://"+(if(video)"video" else "track")+"/"+hit.id+if(video)"?quality="+quality else "").setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build()).build()
}
