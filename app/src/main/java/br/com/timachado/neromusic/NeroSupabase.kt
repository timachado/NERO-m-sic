package br.com.timachado.neromusic

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.ClearCredentialStateRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class GoogleRequired:Exception()
internal class ShareNotConfigured:Exception()
private class NeroHttpException(val status:Int):Exception("Request failed")
internal fun neroGoogleLoginMessage(error:Exception):String=when(error){
    is androidx.credentials.exceptions.GetCredentialCancellationException->"Entrada cancelada."
    is androidx.credentials.exceptions.NoCredentialException->"Nenhuma conta Google disponível para entrar. Confira sua conta no Android."
    is androidx.credentials.exceptions.GetCredentialException->"O Android não conseguiu autenticar com Google (G01). Confira a assinatura do APK e a configuração OAuth."
    is NeroHttpException->when(error.status){
        400,401,403->"O servidor recusou a autenticação Google (G02). A configuração da conta precisa ser verificada."
        else->"Serviço de autenticação indisponível (G03). Tente novamente."
    }
    is java.io.IOException->"Falha de conexão com o serviço de autenticação (G04). Confira sua internet."
    else->"Não foi possível concluir a entrada (G05)."
}
internal data class NeroSession(val uid:String,val email:String,val access:String,val refresh:String,val expiresAt:Long,val displayName:String="",val avatar:String?=null)
// UI-only account metadata: never publish tokens or email in this flow.
internal data class NeroProfileAvatar(val uid:String,val url:String?)
internal object NeroSupabase{
    val configured get()=BuildConfig.SUPABASE_URL.isNotBlank()&&BuildConfig.SUPABASE_KEY.isNotBlank()&&BuildConfig.GOOGLE_CLIENT.isNotBlank()
    private val mutex=Mutex()
    private val identity=kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val activeUid: kotlinx.coroutines.flow.StateFlow<String?> = identity
    private val profileState=kotlinx.coroutines.flow.MutableStateFlow<NeroProfileAvatar?>(null)
    val profileAvatar:kotlinx.coroutines.flow.StateFlow<NeroProfileAvatar?> = profileState
    private fun updateIdentity(session:NeroSession?){
        profileState.value=session?.let{NeroProfileAvatar(it.uid,it.avatar)}
        identity.value=session?.uid
    }
    private val readyState=kotlinx.coroutines.flow.MutableStateFlow(false)
    val identityReady: kotlinx.coroutines.flow.StateFlow<Boolean> = readyState
    fun markGuestReady(){if(!configured){updateIdentity(null);readyState.value=true}}
    private const val alias="nero.supabase.session.v1"
    private fun prefs(context:Context)=context.getSharedPreferences("nero_supabase_session",Context.MODE_PRIVATE)
    private fun key():SecretKey{
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    // Provider metadata is display-only, never used to authorize operations.
    private fun avatarUrl(value:String?):String?=value?.take(2048)?.takeIf{url->runCatching{
        val u=java.net.URI(url)
        u.scheme=="https"&&u.userInfo==null&&u.port in setOf(-1,443)&&
            u.host?.endsWith(".googleusercontent.com")==true
    }.getOrDefault(false)}
    private fun displayName(value:String)=value.trim().filterNot{it.isISOControl()}.take(100)
    private fun save(context:Context,s:NeroSession){
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        cipher.updateAAD(BuildConfig.SUPABASE_URL.toByteArray(Charsets.UTF_8))
        val json=JSONObject().put("uid",s.uid).put("email",s.email).put("access",s.access).put("refresh",s.refresh).put("expiry",s.expiresAt).put("name",s.displayName).put("avatar",s.avatar.orEmpty())
        val encrypted=cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        check(prefs(context).edit().putString("session",Base64.encodeToString(cipher.iv+encrypted,Base64.NO_WRAP)).commit())
    }
    private fun load(context:Context):NeroSession?{
        val stored=prefs(context).getString("session",null)?:return null
        return runCatching{
            val bytes=Base64.decode(stored,Base64.NO_WRAP);require(bytes.size>28)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            cipher.updateAAD(BuildConfig.SUPABASE_URL.toByteArray(Charsets.UTF_8))
            val row=JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
            NeroSession(row.getString("uid"),row.getString("email"),row.getString("access"),row.getString("refresh"),row.getLong("expiry"),displayName(row.optString("name")),avatarUrl(row.optString("avatar")))
        }.getOrNull()
    }
    // Background Radar validates the stored owner without refreshing tokens or changing UI identity.
    suspend fun matchesStoredRadarOwner(context:Context,owner:String?):Boolean=mutex.withLock{
        withContext(Dispatchers.IO){
            val hasSession=prefs(context).contains("session")
            if(owner==null)!hasSession else hasSession&&load(context)?.uid==owner
        }
    }
    private fun clear(context:Context){prefs(context).edit().remove("session").commit();updateIdentity(null);NeroRadarAlerts.stop(context)}
    private suspend fun request(path:String,body:JSONObject?,access:String?=null):String=withContext(Dispatchers.IO){
        if(!configured)throw ShareNotConfigured()
        val c=URL(BuildConfig.SUPABASE_URL+path).openConnection() as HttpURLConnection
        c.connectTimeout=12000;c.readTimeout=18000;c.instanceFollowRedirects=false;c.requestMethod="POST"
        c.setRequestProperty("apikey",BuildConfig.SUPABASE_KEY)
        access?.let{c.setRequestProperty("Authorization","Bearer "+it)}
        c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json")
        try{
            if(body!=null){c.doOutput=true;c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}
            val status=c.responseCode
            if(status !in 200..299)throw NeroHttpException(status)
            if(status==204)return@withContext ""
            c.inputStream.use{input->
                val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192)
                while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=1024*1024);out.write(buf,0,n)}
                out.toString("UTF-8")
            }
        }finally{c.disconnect()}
    }
    private fun parseSession(body:String):NeroSession{
        val row=JSONObject(body);val user=row.getJSONObject("user")
        require(user.getJSONObject("app_metadata").optString("provider")=="google")
        val meta=user.optJSONObject("user_metadata")?:JSONObject()
        val name=displayName(meta.optString("full_name").ifBlank{meta.optString("name")})
        val photo=avatarUrl(meta.optString("avatar_url").ifBlank{meta.optString("picture")})
        return NeroSession(user.getString("id"),user.optString("email"),row.getString("access_token"),row.getString("refresh_token"),System.currentTimeMillis()+row.getLong("expires_in")*1000L,name,photo)
    }
    suspend fun session(context:Context,interactive:Boolean):NeroSession=mutex.withLock{
        if(!configured)throw ShareNotConfigured()
        val cached=withContext(Dispatchers.IO){load(context)}
        updateIdentity(cached)
        readyState.value=true
        if(cached!=null){
            if(cached.expiresAt-System.currentTimeMillis()>60000){updateIdentity(cached);return@withLock cached}
            try{
                val refreshed=parseSession(request("/auth/v1/token?grant_type=refresh_token",JSONObject().put("refresh_token",cached.refresh)))
                require(refreshed.uid==cached.uid)
                withContext(Dispatchers.IO){save(context,refreshed)}
                updateIdentity(refreshed)
                return@withLock refreshed
            }catch(e:NeroHttpException){
                if(e.status!=400&&e.status!=401&&e.status!=403)throw e
                withContext(Dispatchers.IO){clear(context)}
            }
        }
        updateIdentity(null)
        if(!interactive)throw GoogleRequired()
        val raw=Base64.encodeToString(ByteArray(32).also{SecureRandom().nextBytes(it)},Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val hashed=MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}
        val credential=withContext(Dispatchers.Main){
            val option=GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_CLIENT).setNonce(hashed).build()
            CredentialManager.create(context).getCredential(context,GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
        }
        require(credential is CustomCredential&&credential.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        val token=GoogleIdTokenCredential.createFrom(credential.data).idToken
        val signed=parseSession(request("/auth/v1/token?grant_type=id_token",JSONObject().put("provider","google").put("id_token",token).put("nonce",raw)))
        withContext(Dispatchers.IO){save(context,signed)}
        updateIdentity(signed)
        signed
    }
    suspend fun rpc(name:String,body:JSONObject,access:String?=null):String{
        require(name in setOf("nero_share_preview","nero_share_create","nero_share_read","nero_cloud_read_v1","nero_cloud_write_v1","nero_plan_read_v1","nero_together_v1","nero_trial_start_v1"))
        return request("/rest/v1/rpc/"+name,body,access)
    }
    suspend fun signOut(context:Context)=mutex.withLock{
        val current=withContext(Dispatchers.IO){load(context)}
        try{if(current!=null)request("/auth/v1/logout?scope=local",null,current.access)}
        finally{withContext(Dispatchers.IO){clear(context)};CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())}
        Unit
    }
}
