package br.com.timachado.neromusic

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class IdentifyMatch(val title:String,val artist:String,val album:String)
internal const val IdentifyRate=16000
internal const val IdentifySeconds=8

internal fun identifyWav(pcm:ShortArray):ByteArray {
    val size=pcm.size*2
    return ByteBuffer.allocate(44+size).order(ByteOrder.LITTLE_ENDIAN).apply{
        put("RIFF".toByteArray(Charsets.US_ASCII));putInt(36+size)
        put("WAVEfmt ".toByteArray(Charsets.US_ASCII));putInt(16)
        putShort(1);putShort(1);putInt(IdentifyRate);putInt(IdentifyRate*2);putShort(2);putShort(16)
        put("data".toByteArray(Charsets.US_ASCII));putInt(size)
        pcm.forEach{putShort(it)}
    }.array()
}

@SuppressLint("MissingPermission")
internal suspend fun captureIdentify(context:Context):ByteArray=withContext(Dispatchers.IO){
    check(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)
    val min=AudioRecord.getMinBufferSize(IdentifyRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
    if(min<=0)throw IOException("Microfone incompatível.")
    val pcm=ShortArray(IdentifyRate*IdentifySeconds)
    val record=AudioRecord(MediaRecorder.AudioSource.MIC,IdentifyRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min*2,4096))
    try{
        if(record.state!=AudioRecord.STATE_INITIALIZED)throw IOException("Microfone indisponível.")
        record.startRecording()
        if(record.recordingState!=AudioRecord.RECORDSTATE_RECORDING)throw IOException("Não foi possível gravar.")
        var offset=0
        val deadline=SystemClock.elapsedRealtime()+11000L
        while(offset<pcm.size){
            currentCoroutineContext().ensureActive()
            if(SystemClock.elapsedRealtime()>deadline)throw IOException("Tempo de captura excedido.")
            val n=record.read(pcm,offset,minOf(2048,pcm.size-offset),AudioRecord.READ_NON_BLOCKING)
            if(n<0)throw IOException("Falha na captura.")
            if(n==0)delay(20L)else offset+=n
        }
        currentCoroutineContext().ensureActive()
        identifyWav(pcm)
    }finally{
        runCatching{record.stop()};runCatching{record.release()};pcm.fill(0)
    }
}

// Public, shared AudD test credential documented for development only.
// No private provider token is embedded. Commercial use requires an authenticated backend.
internal suspend fun recognizeIdentifyDemo(wav:ByteArray):IdentifyMatch?=withContext(Dispatchers.IO){
    currentCoroutineContext().ensureActive()
    require(BuildConfig.DEBUG&&wav.size==44+IdentifyRate*IdentifySeconds*2)
    val boundary="nero-"+java.util.UUID.randomUUID().toString()
    val prefix=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"api_token\"\r\n\r\ntest\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sample.wav\"\r\nContent-Type: audio/wav\r\n\r\n").toByteArray(Charsets.UTF_8)
    val suffix=("\r\n--"+boundary+"--\r\n").toByteArray(Charsets.UTF_8)
    val conn=URL("https://api.audd.io/").openConnection() as HttpURLConnection
    try{
        conn.requestMethod="POST";conn.doOutput=true;conn.instanceFollowRedirects=false
        conn.connectTimeout=10000;conn.readTimeout=15000
        conn.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary)
        conn.setFixedLengthStreamingMode(prefix.size+wav.size+suffix.size)
        currentCoroutineContext().ensureActive()
        conn.outputStream.use{out->out.write(prefix);out.write(wav);out.write(suffix)}
        val code=conn.responseCode
        if(code !in 200..299)throw IOException("Serviço de teste indisponível (HTTP "+code+").")
        val response=conn.inputStream.use{input->
            val data=ByteArrayOutputStream();val buffer=ByteArray(4096)
            while(true){
                currentCoroutineContext().ensureActive()
                val n=input.read(buffer);if(n<0)break
                if(data.size()+n>65536)throw IOException("Resposta inválida.")
                data.write(buffer,0,n)
            }
            data.toString("UTF-8")
        }
        parseIdentifyResponse(response)
    }finally{conn.disconnect()}
}
internal fun parseIdentifyResponse(response:String):IdentifyMatch?{
    val json=JSONObject(response)
    if(json.optString("status")!="success")throw IOException("Reconhecimento de teste indisponível ou limite atingido.")
    if(json.isNull("result"))return null
    val result=json.optJSONObject("result")?:throw IOException("Resposta inválida.")
    val title=result.optString("title").trim().take(200)
    val artist=result.optString("artist").trim().take(200)
    if(title.isBlank()||artist.isBlank())throw IOException("Identificação incompleta.")
    return IdentifyMatch(title,artist,result.optString("album").trim().take(200))
}
