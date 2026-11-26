package br.com.timachado.neromusic

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri

/** External-app handoff via the documented Web Video Caster URL scheme.
 * Only public canonical watch pages are shared, never extracted stream URLs,
 * headers, account information or credentials. A handoff is not proof of casting.
 */
internal object NeroWebVideoCaster {
    const val PACKAGE_NAME="com.instantbits.cast.webvideo"
    const val EXTRA_REQUEST="br.com.timachado.neromusic.WEB_VIDEO_CASTER"
    fun publicWatchUrl(mediaId:String?):String?{
        val id=mediaId?.takeIf{it.startsWith("youtube:")}?.removePrefix("youtube:")
            ?.takeIf{it.matches(Regex("[A-Za-z0-9_-]{11}"))}?:return null
        return "https://www.youtube.com/watch?v="+id
    }
    fun available(context:Context):Boolean=runCatching{
        context.packageManager.getPackageInfo(PACKAGE_NAME,0)
        true
    }.getOrDefault(false)
    fun handoff(context:Context,mediaId:String?,title:String):Boolean{
        val url=publicWatchUrl(mediaId)?:return false
        val deepLink=Uri.Builder().scheme("wvc-x-callback").authority("open")
            .appendQueryParameter("url",url)
            .apply{if(title.isNotBlank())appendQueryParameter("title",title.take(180))}
            .build()
        return try{
            context.startActivity(Intent(Intent.ACTION_VIEW,deepLink).setPackage(PACKAGE_NAME))
            true
        }catch(_:ActivityNotFoundException){false}catch(_:SecurityException){false}
    }
}
