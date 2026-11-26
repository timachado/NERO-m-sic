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
    fun handoff(context:Context,mediaId:String?,title:String,video:Boolean=false):Boolean{
        val id=mediaId?:return false
        val url=currentSource(id,video)?:return false
        val deepLink=Uri.Builder().scheme("wvc-x-callback").authority("open")
            .appendQueryParameter("url",url)
            .appendQueryParameter("skip_page_load","true")
            .appendQueryParameter("secure_uri","true")
            .apply{
                if(title.isNotBlank())appendQueryParameter("title",title.take(180))
                val mime=Uri.parse(url).getQueryParameter("mime")
                if(mime in setOf("audio/mp4","audio/webm","audio/mpeg","video/mp4","video/webm"))appendQueryParameter("mime_type",mime)
            }.build()
        return try{
            context.startActivity(Intent(Intent.ACTION_VIEW,deepLink).setPackage(PACKAGE_NAME))
            true
        }catch(_:ActivityNotFoundException){false}catch(_:SecurityException){false}
    }
}
