package br.com.timachado.neromusic

import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

// The only accepted input is the opaque, short-lived code issued by the server.
// Do not accept a server-supplied URL, append an account ID, or forward a session token.
internal fun neroCommerceBindingUrl(code:String):String? =
    if(Regex("[a-f0-9]{64}").matches(code)) "https://timachado.ifree.page/?nero_connect="+code else null

internal suspend fun requestNeroCommerceBinding(context:Context,uid:String):String=withTimeoutOrNull(12000L){
    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    val current=NeroSupabase.session(context,false)
    require(current.uid==uid)
    val result=JSONObject(NeroSupabase.rpc("nero_commerce_intent_v1",JSONObject(),current.access))
    val url=neroCommerceBindingUrl(result.getString("code"))?:error("Invalid commerce intent")
    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    url
}?:error("Commerce intent timed out")
