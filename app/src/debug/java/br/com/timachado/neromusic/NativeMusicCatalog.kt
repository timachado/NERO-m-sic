package br.com.timachado.neromusic

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

// Personal debug build. Publicly accessible catalog only; no account credentials.
internal object NativeMusicCatalog{
    val enabled=true
    private val lock=Mutex()
    private var initialized=false
    private fun initialize(){
        if(!initialized){NewPipe.init(NeroExtractorDownloader());initialized=true}
    }
    private fun idFromUrl(value:String):String?{
        val uri=Uri.parse(value)
        if(uri.scheme!="https"||uri.host !in setOf("www.youtube.com","youtube.com","music.youtube.com","m.youtube.com","youtu.be"))return null
        val id=if(uri.host=="youtu.be")uri.pathSegments.firstOrNull()else uri.getQueryParameter("v")
        return id?.takeIf{Regex("[A-Za-z0-9_-]{11}").matches(it)}
    }
    suspend fun search(query:String):List<NativeMusicHit> = withContext(Dispatchers.IO){
        lock.withLock{
            initialize()
            val term=query.trim().take(100);require(term.length>=3)
            val service=ServiceList.YouTube
            val handler=service.searchQHFactory.fromQuery(term,listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),"")
            val info=SearchInfo.getInfo(service,handler)
            info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull{item->
                val id=idFromUrl(item.url)?:return@mapNotNull null
                val title=item.name.takeIf{it.isNotBlank()}?:return@mapNotNull null
                NativeMusicHit(id,title,item.uploaderName.orEmpty(),item.duration.coerceAtLeast(0))
            }.distinctBy{it.id}.take(30)
        }
    }
    suspend fun resolve(hit:NativeMusicHit):MediaItem = withContext(Dispatchers.IO){
        lock.withLock{
            initialize()
            require(Regex("[A-Za-z0-9_-]{11}").matches(hit.id))
            val info=StreamInfo.getInfo(ServiceList.YouTube,"https://www.youtube.com/watch?v="+hit.id)
            require(info.duration>0){"Faixa indisponível"}
            val stream=info.audioStreams.filter{stream->
                val uri=Uri.parse(stream.content)
                stream.isUrl&&stream.deliveryMethod==DeliveryMethod.PROGRESSIVE_HTTP&&uri.scheme=="https"&&
                    (uri.host=="googlevideo.com"||uri.host?.endsWith(".googlevideo.com")==true)
            }.maxByOrNull{it.averageBitrate}?:error("Áudio indisponível nesta faixa")
            MediaItem.Builder().setMediaId("youtube:"+hit.id).setUri(stream.content)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(info.name.ifBlank{hit.title})
                    .setArtist(info.uploaderName.orEmpty().ifBlank{hit.artist})
                    .setArtworkUri(Uri.parse(hit.thumbnail)).build()).build()
        }
    }
}
