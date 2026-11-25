package br.com.timachado.neromusic

import androidx.media3.common.MediaItem

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
