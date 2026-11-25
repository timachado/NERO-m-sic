package br.com.timachado.neromusic

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Private offline storage. Uses only the existing playable progressive audio source.
private const val nativeOfflineLimit=100L*1024*1024
private val nativeOfflineId=Regex("[A-Za-z0-9_-]{11}")
private fun nativeDirectory(context:Context)=File(context.filesDir,"native-offline").apply{mkdirs()}
internal data class NativeOfflineTrack(val hit:NativeMusicHit,val size:Long){
    fun item(context:Context):MediaItem=hit.queueItem().buildUpon()
        .setUri(Uri.fromFile(nativeAudioFile(context,hit.id))).build()
}
private fun nativeAudioFile(context:Context,id:String):File{
    require(nativeOfflineId.matches(id))
    return File(nativeDirectory(context),"$id.audio")
}
private fun nativeMetaFile(context:Context,id:String)=File(nativeDirectory(context),"$id.json")
internal fun nativeOfflineCopy(context:Context,id:String):NativeOfflineTrack?=runCatching{
    require(nativeOfflineId.matches(id))
    val meta=JSONObject(nativeMetaFile(context,id).readText())
    val size=meta.getLong("size")
    val file=nativeAudioFile(context,id)
    require(meta.getString("id")==id&&size in 1..nativeOfflineLimit&&file.isFile&&file.length()==size)
    NativeOfflineTrack(NativeMusicHit(id,meta.getString("title"),meta.getString("artist"),meta.optLong("duration")),size)
}.getOrNull()
internal suspend fun nativeOfflineTracks(context:Context):List<NativeOfflineTrack> = withContext(Dispatchers.IO){
    nativeDirectory(context).listFiles()?.filter{it.extension=="json"}?.mapNotNull{
        nativeOfflineCopy(context,it.nameWithoutExtension)
    }?.sortedBy{it.hit.title.lowercase()}?:emptyList()
}
internal suspend fun saveNativeAudio(context:Context,hit:NativeMusicHit,progress:(Float)->Unit):NativeOfflineTrack = withContext(Dispatchers.IO){
    require(nativeOfflineId.matches(hit.id)&&NativeMusicCatalog.enabled)
    nativeOfflineCopy(context,hit.id)?.let{progress(1f);return@withContext it}
    val item=NativeMusicCatalog.resolve(hit)
    val uri=item.localConfiguration?.uri?:error("Áudio indisponível")
    val host=uri.host.orEmpty()
    require(uri.scheme=="https"&&(host=="googlevideo.com"||host.endsWith(".googlevideo.com")))
    val dir=nativeDirectory(context)
    val partial=File(dir,hit.id+".part")
    val pendingMeta=File(dir,hit.id+".tmp")
    val final=nativeAudioFile(context,hit.id)
    val side=nativeMetaFile(context,hit.id)
    val connection=URL(uri.toString()).openConnection() as HttpURLConnection
    connection.connectTimeout=15000
    connection.readTimeout=20000
    connection.instanceFollowRedirects=false
    try{
        check(connection.responseCode==200)
        val mime=connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
        check(mime.startsWith("audio/")||mime in setOf("application/octet-stream","video/mp4","video/webm"))
        val expected=connection.contentLengthLong
        check(expected<=nativeOfflineLimit)
        if(expected>0)check(dir.usableSpace>expected+1024*1024)
        var size=0L
        connection.inputStream.use{input->partial.outputStream().use{output->
            val buffer=ByteArray(65536)
            while(true){
                currentCoroutineContext().ensureActive()
                val n=input.read(buffer)
                if(n<0)break
                size+=n
                check(size<=nativeOfflineLimit&&(expected<0||size<=expected))
                output.write(buffer,0,n)
                if(expected>0)progress((size.toDouble()/expected).toFloat().coerceIn(0f,0.99f))
            }
        }}
        check(size>0&&(expected<0||size==expected))
        currentCoroutineContext().ensureActive()
        pendingMeta.writeText(JSONObject().put("id",hit.id).put("title",hit.title)
            .put("artist",hit.artist).put("duration",hit.duration).put("size",size).toString())
        check(partial.renameTo(final))
        check(pendingMeta.renameTo(side))
        progress(1f)
        nativeOfflineCopy(context,hit.id)?:error("Arquivo não validado")
    }finally{
        connection.disconnect();partial.delete();pendingMeta.delete()
    }
}
