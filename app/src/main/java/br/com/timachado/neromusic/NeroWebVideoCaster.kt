package br.com.timachado.neromusic

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.SystemClock
import java.net.URI

/** In-process snapshot of the direct source already selected by PlaybackService.
 * No extraction, download, proxy, cookies, credentials or account headers are
 * added by this integration. Never log the short-lived media URL.
 */
internal object NeroWebVideoCaster {
    const val PACKAGE_NAME="com.instantbits.cast.webvideo"
    const val EXTRA_REQUEST="br.com.timachado.neromusic.WEB_VIDEO_CASTER"
    private const val MAX_AGE=300000L
    private data class Source(val mediaId:String,val video:Boolean,val url:String,val at:Long)
    private val sources=linkedMapOf<Pair<String,Boolean>,Source>()
    internal fun validatedSource(mediaId:String,video:Boolean,url:String):String?{
        if(!mediaId.matches(Regex("youtube:[A-Za-z0-9_-]{11}")))return null
        val u=runCatching{URI(url)}.getOrNull()?:return null
        val host=u.host?.lowercase()?:return null
        if(!u.scheme.equals("https",true)||u.userInfo!=null||u.rawFragment!=null||u.port !in setOf(-1,443))return null
        if(!(host=="googlevideo.com"||host.endsWith(".googlevideo.com"))||u.path!="/videoplayback"||u.rawQuery.isNullOrBlank())return null
        // The resolver supplies the provider-issued URL; do not rewrite, renew,
        // modify signatures or try to circumvent a denied/expired source.
        return url
    }
    @Synchronized fun rememberSource(mediaId:String,video:Boolean,url:String){
        val key=mediaId to video
        val valid=validatedSource(mediaId,video,url)
        if(valid==null){sources.remove(key);return}
        sources.remove(key)
        sources[key]=Source(mediaId,video,valid,SystemClock.elapsedRealtime())
        while(sources.size>16)sources.remove(sources.keys.first())
    }
    @Synchronized fun clearSources(){sources.clear()}
    @Synchronized fun currentSource(mediaId:String,video:Boolean):String?{
        val key=mediaId to video
        val source=sources[key]?:return null
        if(SystemClock.elapsedRealtime()-source.at !in 0L..MAX_AGE){sources.remove(key);return null}
        return source.url
    }
    fun available(context:Context):Boolean=runCatching{
        context.packageManager.getPackageInfo(PACKAGE_NAME,0)
        true
    }.getOrDefault(false)
    /** webOS 2.0 and older receivers generally support muxed MP4 better
     * than audio-only WebM/Opus. Select an already available MP4 rendition
     * of the same item via the existing video playback source, without
     * modifying the local player or retrying a rejected source. */
    internal fun compatibleTvMime(mime:String?):Boolean=mime=="video/mp4"
    private fun declaredMime(url:String):String?=runCatching{Uri.parse(url).getQueryParameter("mime")}.getOrNull()
    suspend fun prepareTvSource(mediaId:String,title:String,artist:String):String?{
        if(!mediaId.matches(Regex("youtube:[A-Za-z0-9_-]{11}")))return null
        val cached=currentSource(mediaId,true)
        if(cached!=null&&compatibleTvMime(declaredMime(cached)))return cached
        val hit=NativeMusicHit(mediaId.removePrefix("youtube:"),title,artist,0L)
        val item=NativeMusicCatalog.resolveVideo(hit,quality=720)
        val url=item.localConfiguration?.uri?.toString()?:return null
        val validated=validatedSource(mediaId,true,url)?:return null
        val mime=declaredMime(validated)
        // resolveVideo guarantees muxed MPEG_4; never mislabel an explicit
        // incompatible source to trick the receiver.
        if(mime!=null&&!compatibleTvMime(mime))return null
        return validated
    }
    fun handoffTv(context:Context,mediaId:String,title:String,url:String):Boolean{
        val source=validatedSource(mediaId,true,url)?:return false
        val mime=declaredMime(source)
        if(mime!=null&&!compatibleTvMime(mime))return false
        val deepLink=Uri.Builder().scheme("wvc-x-callback").authority("open")
            .appendQueryParameter("url",source)
            .appendQueryParameter("skip_page_load","true")
            .appendQueryParameter("secure_uri","true")
            .appendQueryParameter("mime_type","video/mp4")
            .appendQueryParameter("poster","https://i.ytimg.com/vi/"+mediaId.removePrefix("youtube:")+"/hqdefault.jpg")
            .apply{if(title.isNotBlank())appendQueryParameter("title",title.take(180))}
            .build()
        return try{
            context.startActivity(Intent(Intent.ACTION_VIEW,deepLink).setPackage(PACKAGE_NAME))
            true
        }catch(_:ActivityNotFoundException){false}catch(_:SecurityException){false}
    }
    // Legacy NERO Cast is unregistered and must not open another page.
    @Suppress("UNUSED_PARAMETER")
    fun handoff(context:Context,mediaId:String?,title:String,video:Boolean=false):Boolean=false
}
