package br.com.timachado.neromusic

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal object NeroLyricsAiClient{
    private const val MAX=9600044
    val enabled:Boolean get()=BuildConfig.NERO_LYRICS_AI_ENABLED&&BuildConfig.SUPABASE_URL.isNotBlank()&&BuildConfig.SUPABASE_KEY.isNotBlank()
    private val http=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(135,TimeUnit.SECONDS).build()
    suspend fun transcribe(context:Context,owner:String,uri:Uri):String{
        check(enabled&&NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner)
        require(uri.scheme=="content")
        val audio=withContext(Dispatchers.IO){
            context.contentResolver.openInputStream(uri)?.use{input->
                val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){
                    currentCoroutineContext().ensureActive()
                    val n=input.read(buffer);if(n<0)break
                    require(out.size()+n<=MAX);out.write(buffer,0,n)
                }
                out.toByteArray().also{require(it.size>=46)}
            }?:error("Audio unavailable")
        }
        try{
            val session=NeroSupabase.session(context,false)
            check(session.uid==owner&&NeroSupabase.activeUid.value==owner&&NeroSupabase.identityReady.value)
            val req=Request.Builder().url(BuildConfig.SUPABASE_URL+"/functions/v1/nero-lyrics-ai")
                .header("Authorization","Bearer "+session.access).header("apikey",BuildConfig.SUPABASE_KEY)
                .header("Accept","application/json")
                .post(audio.toRequestBody("audio/wav".toMediaType())).build()
            val text=suspendCancellableCoroutine<String>{cont->
                val call=http.newCall(req);cont.invokeOnCancellation{call.cancel()}
                call.enqueue(object:Callback{
                    override fun onFailure(call:Call,e:IOException){if(cont.isActive)cont.resumeWithException(e)}
                    override fun onResponse(call:Call,response:Response){response.use{
                        if(!cont.isActive)return
                        try{
                            check(response.code==200)
                            val source=response.body?.source()?:error("Missing response")
                            require(!source.request(262145L))
                            val json=JSONObject(source.readUtf8())
                            check(json.optString("status")=="transcript")
                            val value=json.optString("text").trim()
                            require(value.isNotBlank()&&value.length<=20000)
                            if(cont.isActive)cont.resume(value)
                        }catch(e:Exception){if(cont.isActive)cont.resumeWithException(e)}
                    }}
                })
            }
            check(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner)
            return text
        }finally{audio.fill(0)}
    }
}
