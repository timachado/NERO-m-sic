package br.com.timachado.neromusic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@UnstableApi
internal class NeroCarLibrary(private val context:Context,private val scope:CoroutineScope):MediaLibrarySession.Callback{
    private val root="nero-car-root"
    private val local="nero-car-local"
    private val offline="nero-car-offline"
    private val favorites="nero-car-favorites"
    private fun own(controller:MediaSession.ControllerInfo)=controller.uid==android.os.Process.myUid()
    private fun trusted(controller:MediaSession.ControllerInfo)=own(controller)||controller.isTrusted
    private fun folder(id:String,title:String)=MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).build()).build()
    private fun folders()=listOf(folder(local,"Músicas do aparelho"),folder(offline,"Downloads"),folder(favorites,"Favoritos offline"))
    private fun browseItem(item:MediaItem)=MediaItem.Builder().setMediaId(item.mediaId)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(item.mediaMetadata.title).setArtist(item.mediaMetadata.artist)
            .setAlbumTitle(item.mediaMetadata.albumTitle).setIsBrowsable(false).setIsPlayable(true).build()).build()
    private data class Catalog(val local:List<MediaItem>,val offline:List<MediaItem>,val favorites:List<MediaItem>){
        val all get()=(local+offline).distinctBy{it.mediaId}
    }
    private suspend fun catalog():Catalog{
        val permission=if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        val device=if(ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED)
            runCatching{loadTracks(context).take(1500).map{it.mediaItem()}}.getOrDefault(emptyList()) else emptyList()
        val saved=(downloaded(context).map{it.item(Uri.fromFile(audioFile(context,it)))}+
            nativeOfflineTracks(context).map{it.item(context)}).distinctBy{it.mediaId}.take(1500)
        val liked=context.getSharedPreferences("nero_library",0).getStringSet("favorites",emptySet()).orEmpty()+
            runCatching{readOnlineFavorites(context).map{it.id}}.getOrDefault(emptyList())
        return Catalog(device,saved,(device+saved).filter{it.mediaId in liked}.distinctBy{it.mediaId})
    }
    private fun <T> async(block:suspend ()->T):ListenableFuture<T>{
        val future=SettableFuture.create<T>()
        val job=scope.launch(Dispatchers.IO){
            try{if(!future.isCancelled)future.set(block())}
            catch(e:CancellationException){future.cancel(false);throw e}
            catch(e:Exception){future.setException(e)}
        }
        future.addListener({if(future.isCancelled)job.cancel()},java.util.concurrent.Executor{it.run()})
        return future
    }
    override fun onGetLibraryRoot(session:MediaLibrarySession,browser:MediaSession.ControllerInfo,params:LibraryParams?):ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(if(trusted(browser))LibraryResult.ofItem(folder(root,"NERO Music"),params)else LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
    override fun onGetChildren(session:MediaLibrarySession,browser:MediaSession.ControllerInfo,parentId:String,page:Int,pageSize:Int,params:LibraryParams?):ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>{
        if(!trusted(browser))return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
        if(page<0||pageSize<=0||parentId !in setOf(root,local,offline,favorites))return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        return async{
            val rows=if(parentId==root)folders() else catalog().let{data->when(parentId){local->data.local;offline->data.offline;else->data.favorites}.map(::browseItem)}
            val start=(page.toLong()*pageSize).coerceAtMost(rows.size.toLong()).toInt()
            val end=(start.toLong()+pageSize).coerceAtMost(rows.size.toLong()).toInt()
            LibraryResult.ofItemList(rows.subList(start,end),params)
        }
    }
    override fun onGetItem(session:MediaLibrarySession,browser:MediaSession.ControllerInfo,mediaId:String):ListenableFuture<LibraryResult<MediaItem>>{
        if(!trusted(browser))return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
        return async{
            val item=(listOf(folder(root,"NERO Music"))+folders()).firstOrNull{it.mediaId==mediaId}
                ?:catalog().all.firstOrNull{it.mediaId==mediaId}?.let(::browseItem)
            if(item==null)LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)else LibraryResult.ofItem(item,null)
        }
    }
    override fun onAddMediaItems(mediaSession:MediaSession,controller:MediaSession.ControllerInfo,mediaItems:List<MediaItem>):ListenableFuture<List<MediaItem>>{
        if(own(controller))return Futures.immediateFuture(mediaItems)
        if(!trusted(controller)||mediaItems.size>1500)return Futures.immediateFailedFuture(SecurityException("Controller not authorized"))
        // Never accept arbitrary external URIs. Resolve only known playable IDs.
        return async{
            val available=catalog().all.associateBy{it.mediaId}
            mediaItems.flatMap{request->
                val exact=available[request.mediaId]
                if(exact!=null)listOf(exact)else{
                    val query=request.requestMetadata.searchQuery?.trim()?.take(120).orEmpty()
                    if(query.isBlank())throw IllegalArgumentException("Unknown media item")
                    val matches=available.values.filter{item->
                        val metadata=item.mediaMetadata
                        listOf(metadata.title,metadata.artist,metadata.albumTitle).any{it?.toString()?.contains(query,true)==true}||
                            (metadata.title.toString()+" "+metadata.artist.toString()).contains(query,true)
                    }.sortedByDescending{it.mediaMetadata.title?.toString()?.equals(query,true)==true}.take(50)
                    if(matches.isEmpty())throw IllegalArgumentException("No offline search matches")
                    matches
                }
            }.distinctBy{it.mediaId}.take(1500)
        }
    }
}
