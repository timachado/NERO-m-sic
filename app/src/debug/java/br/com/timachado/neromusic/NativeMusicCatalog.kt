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
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem

// Personal debug build. Publicly accessible catalog only; no account credentials.
internal object NativeMusicCatalog{
    val enabled=true
    private val lock=Mutex()
    private var initialized=false
    private data class CachedSearch(val at:Long,val hits:List<NativeMusicHit>)
    private val searchCache=LinkedHashMap<String,CachedSearch>(32,0.75f,true)
    @Synchronized private fun initialize(){
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
            val cacheKey=term.lowercase(java.util.Locale.ROOT)
            val cached=searchCache[cacheKey]
            val now=android.os.SystemClock.elapsedRealtime()
            if(cached!=null&&now-cached.at in 0L..900000L)return@withLock cached.hits
            val service=ServiceList.YouTube
            val handler=service.searchQHFactory.fromQuery(term,listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),"")
            val info=SearchInfo.getInfo(service,handler)
            val hits=info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull{item->
                val id=idFromUrl(item.url)?:return@mapNotNull null
                val title=item.name.takeIf{it.isNotBlank()}?:return@mapNotNull null
                NativeMusicHit(id,title,item.uploaderName.orEmpty(),item.duration.coerceAtLeast(0))
            }.distinctBy{it.id}.take(30)
            searchCache[cacheKey]=CachedSearch(android.os.SystemClock.elapsedRealtime(),hits)
            if(searchCache.size>32)searchCache.remove(searchCache.keys.first())
            hits
        }
    }

    suspend fun suggestions(query:String):List<String> = withContext(Dispatchers.IO){
        initialize()
        val term=query.trim().take(100);require(term.length>=2)
        ServiceList.YouTube.suggestionExtractor.suggestionList(term)
            .map{it.trim().take(100)}.filter{it.isNotBlank()}.distinct().take(6)
    }
    suspend fun collections(query:String,kind:NativeCollectionKind):List<NativeCollection> = withContext(Dispatchers.IO){
        lock.withLock{
            initialize()
            val term=query.trim().take(100);require(term.length>=3)
            val filter=when(kind){
                NativeCollectionKind.ARTIST->YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS
                NativeCollectionKind.ALBUM->YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
            }
            val service=ServiceList.YouTube
            val info=SearchInfo.getInfo(service,service.searchQHFactory.fromQuery(term,listOf(filter),""))
            info.relatedItems.mapNotNull{row->
                val credit=when(kind){
                    NativeCollectionKind.ARTIST->if(row is ChannelInfoItem)"" else return@mapNotNull null
                    NativeCollectionKind.ALBUM->if(row is PlaylistInfoItem)row.uploaderName.orEmpty() else return@mapNotNull null
                }
                val url=Uri.parse(row.url)
                if(url.scheme!="https"||url.host !in setOf("youtube.com","www.youtube.com","music.youtube.com","m.youtube.com")||row.name.isBlank())return@mapNotNull null
                val cover=row.thumbnails.firstOrNull()?.url?.takeIf{value->
                    val u=Uri.parse(value);u.scheme=="https"&&(u.host=="i.ytimg.com"||u.host?.endsWith(".googleusercontent.com")==true||u.host?.endsWith(".ggpht.com")==true)
                }
                NativeCollection(row.url,row.name,credit,cover,kind)
            }.distinctBy{it.url}.take(20)
        }
    }
    suspend fun albumTracks(album:NativeCollection):List<NativeMusicHit> = withContext(Dispatchers.IO){
        lock.withLock{
            initialize()
            require(album.kind==NativeCollectionKind.ALBUM)
            val url=Uri.parse(album.url)
            val id=url.getQueryParameter("list").orEmpty()
            require(url.scheme=="https"&&url.host in setOf("youtube.com","www.youtube.com","music.youtube.com","m.youtube.com")&&Regex("[A-Za-z0-9_-]{8,100}").matches(id))
            PlaylistInfo.getInfo(ServiceList.YouTube,"https://www.youtube.com/playlist?list="+id).relatedItems.mapNotNull{item->
                val trackId=idFromUrl(item.url)?:return@mapNotNull null
                if(item.name.isBlank())return@mapNotNull null
                NativeMusicHit(trackId,item.name,item.uploaderName.orEmpty().ifBlank{album.credit},item.duration.coerceAtLeast(0))
            }.distinctBy{it.id}.take(30)
        }
    }
    suspend fun brazilChart():List<ChartSong> = withContext(Dispatchers.IO){
        lock.withLock{
            initialize()
            val info=PlaylistInfo.getInfo(ServiceList.YouTube,"https://www.youtube.com/playlist?list=PL4fGSI1pDJn7rGBE8kEC0CqTa1nMh9AKB")
            // Preserve source positions, even when a track cannot be displayed.
            val rows=info.relatedItems.toMutableList()
            var page=info.nextPage
            var requests=0
            while(rows.size<100&&page!=null&&requests<5){
                val next=PlaylistInfo.getMoreItems(ServiceList.YouTube,info.url,page)
                if(next.items.isEmpty())break
                rows.addAll(next.items)
                page=next.nextPage
                requests++
            }
            rows.take(100).mapIndexedNotNull{i,item->
                val id=idFromUrl(item.url)?:return@mapIndexedNotNull null
                if(item.name.isBlank())return@mapIndexedNotNull null
                ChartSong(i+1,id,item.name,item.uploaderName.orEmpty(),"https://i.ytimg.com/vi/"+id+"/mqdefault.jpg","https://music.youtube.com/watch?v="+id)
            }.distinctBy{it.id}
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
