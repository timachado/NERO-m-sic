package br.com.timachado.neromusic

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import java.net.URI

/** Direct, authorized media only. A public watch page is not a media file.
 * Never forward native catalog stream credentials or open a YouTube page.
 */
internal object NeroWebVideoCaster {
    const val PACKAGE_NAME="com.instantbits.cast.webvideo"
    const val EXTRA_REQUEST="br.com.timachado.neromusic.WEB_VIDEO_CASTER"

    fun directMediaUrl(url:String,mime:String):String? {
        if(mime !in setOf("audio/mpeg","audio/flac","audio/wav","audio/ogg"))return null
        val uri=runCatching{URI(url)}.getOrNull()?:return null
        if(!uri.scheme.equals("https",true)||uri.host.isNullOrBlank()||uri.userInfo!=null||
            uri.rawQuery!=null||uri.rawFragment!=null||uri.port !in setOf(-1,443))return null
        val host=uri.host.lowercase().trimEnd('.')
        if(listOf("youtube.com","youtu.be","googlevideo.com","youtube-nocookie.com","ytimg.com").any{host==it||host.endsWith("."+it)})return null
        val path=uri.path.lowercase()
        val extensions=when(mime){
            "audio/mpeg"->listOf(".mp3")
            "audio/flac"->listOf(".flac")
            "audio/wav"->listOf(".wav")
            else->listOf(".ogg",".oga")
        }
        if(!extensions.any{path.endsWith(it)})return null
        return url
    }
    fun available(context:Context):Boolean=runCatching{
        context.packageManager.getPackageInfo(PACKAGE_NAME,0)
        true
    }.getOrDefault(false)

    fun handoff(context:Context,track:OpenTrack):Boolean{
        if(!allowed(track))return false
        val url=directMediaUrl(track.url,track.mime)?:return false
        return try{
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url),track.mime).setPackage(PACKAGE_NAME))
            true
        }catch(_:ActivityNotFoundException){false}catch(_:SecurityException){false}
    }

    // Historical inactive activity must never reopen catalog watch pages.
    @Suppress("UNUSED_PARAMETER")
    fun handoff(context:Context,mediaId:String?,title:String):Boolean=false
}
