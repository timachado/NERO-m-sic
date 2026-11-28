package br.com.timachado.neromusic

import android.content.Context
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class NeroIdentifyMatch(val title:String,val artist:String,val album:String){
    val query:String get()="$title $artist".take(100)
}
internal sealed interface NeroIdentifyResult{
    data class Match(val value:NeroIdentifyMatch):NeroIdentifyResult
    data object NoMatch:NeroIdentifyResult
    data object Limit:NeroIdentifyResult
    data object Unavailable:NeroIdentifyResult
}
internal object NeroIdentifyClient{
    // The token is sent only to the existing configured NERO project.
    val enabled:Boolean get()=BuildConfig.NERO_IDENTIFY_ENABLED&&BuildConfig.SUPABASE_URL.isNotBlank()&&BuildConfig.SUPABASE_KEY.isNotBlank()
    private val http=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(25,TimeUnit.SECONDS).build()
    suspend fun identify(context:Context,owner:String,audio:ByteArray):NeroIdentifyResult{
        check(enabled)
        require(audio.size==44+NeroIdentifySample.MAX_SAMPLES*2)
        check(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner)
        val session=NeroSupabase.session(context,false)
        check(session.uid==owner&&NeroSupabase.activeUid.value==owner)
        val request=Request.Builder().url(BuildConfig.SUPABASE_URL+"/functions/v1/nero-identify")
            .header("Authorization","Bearer "+session.access).header("apikey",BuildConfig.SUPABASE_KEY)
            .header("Accept","application/json").post(audio.toRequestBody("audio/wav".toMediaType())).build()
        val result=suspendCancellableCoroutine<NeroIdentifyResult>{ continuation->
            val call=http.newCall(request)
            continuation.invokeOnCancellation{call.cancel()}
            call.enqueue(object:Callback{
                override fun onFailure(call:Call,e:IOException){
                    if(continuation.isActive)continuation.resumeWithException(e)
                }
                override fun onResponse(call:Call,response:Response){
                    response.use{
                        if(!continuation.isActive)return
                        try{
                            val value=when(response.code){
                                429->NeroIdentifyResult.Limit
                                200->{
                                    val body=response.body?:error("Missing response")
                                    val source=body.source()
                                    if(source.request(16385L))error("Oversized response")
                                    val json=JSONObject(source.readUtf8())
                                    when(json.optString("status")){
                                        "no_match"->NeroIdentifyResult.NoMatch
                                        "match"->{
                                            val title=json.optString("title").trim()
                                            val artist=json.optString("artist").trim()
                                            require(title.isNotBlank()&&title.length<=300&&artist.isNotBlank()&&artist.length<=300)
                                            NeroIdentifyResult.Match(NeroIdentifyMatch(title,artist,json.optString("album").take(300)))
                                        }
                                        else->NeroIdentifyResult.Unavailable
                                    }
                                }
                                else->NeroIdentifyResult.Unavailable
                            }
                            if(continuation.isActive)continuation.resume(value)
                        }catch(e:Exception){if(continuation.isActive)continuation.resumeWithException(e)}
                    }
                }
            })
        }
        check(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==owner)
        return result
    }
}
