package br.com.timachado.neromusic

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.security.interfaces.ECPublicKey
import java.util.Base64

private const val offerPrefix="NERO1:"
private const val keyAlias="nero_connect_identity_v1"
internal fun connectPublicOffer(key:PublicKey):String =
    offerPrefix+Base64.getUrlEncoder().withoutPadding().encodeToString(key.encoded)

internal fun parseConnectOffer(value:String):PublicKey {
    val input=value.trim()
    require(input.startsWith(offerPrefix)&&input.length<=256)
    val encoded=Base64.getUrlDecoder().decode(input.removePrefix(offerPrefix))
    require(encoded.size in 80..128)
    val key=KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded)) as ECPublicKey
    require(key.params.curve.field.fieldSize==256&&key.params.order.bitLength()==256)
    // Require the actual named P-256 curve, not merely a 256-bit field.
    val params=java.security.AlgorithmParameters.getInstance("EC")
    params.init(ECGenParameterSpec("secp256r1"))
    val expected=params.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
    require(key.params.curve==expected.curve&&key.params.generator==expected.generator&&
        key.params.order==expected.order&&key.params.cofactor==expected.cofactor)
    require(connectPublicOffer(key)==input)
    return key
}

internal fun connectFingerprint(first:PublicKey,second:PublicKey):String {
    val offers=listOf(connectPublicOffer(first),connectPublicOffer(second)).sorted()
    require(offers[0]!=offers[1])
    val bytes=MessageDigest.getInstance("SHA-256").digest(("NERO-PAIR-V1\n"+offers.joinToString("\n")).toByteArray(Charsets.UTF_8))
    return bytes.take(6).joinToString(""){(it.toInt() and 255).toString(16).padStart(2,'0')}.uppercase(java.util.Locale.ROOT).chunked(4).joinToString(" ")
}

// This stores a manually confirmed public key, not a remote session or account grant.
internal class NeroConnectIdentity(context:Context){
    private val prefs=context.getSharedPreferences("nero_connect_pair",Context.MODE_PRIVATE)
    private val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
    fun publicKey():PublicKey{
        if(!store.containsAlias(keyAlias)){
            val generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore")
            generator.initialize(KeyGenParameterSpec.Builder(keyAlias,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256).build())
            generator.generateKeyPair()
        }
        return store.getCertificate(keyAlias).publicKey
    }
    fun offer()=connectPublicOffer(publicKey())
    fun fingerprint(peerOffer:String)=connectFingerprint(publicKey(),parseConnectOffer(peerOffer))
    fun confirm(peerOffer:String,comparedCode:String){
        val peer=parseConnectOffer(peerOffer)
        require(connectFingerprint(publicKey(),peer)==comparedCode)
        check(prefs.edit().putString("peer_public",connectPublicOffer(peer)).commit())
    }
    fun peerOffer():String?=prefs.getString("peer_public",null)?.takeIf{runCatching{parseConnectOffer(it);true}.getOrDefault(false)}
    fun issue(gate:ConnectProofGate):String{
        val peer=parseConnectOffer(requireNotNull(peerOffer()))
        return gate.issue(publicKey(),peer,System.currentTimeMillis()).wire()
    }
    fun prove(challengeWire:String):String{
        val peer=parseConnectOffer(requireNotNull(peerOffer()))
        val local=publicKey()
        val privateKey=store.getKey(keyAlias,null) as java.security.PrivateKey
        return signConnectChallenge(parseConnectChallenge(challengeWire),local,peer,privateKey,System.currentTimeMillis())
    }
    fun verify(gate:ConnectProofGate,proof:String):Boolean{
        val peer=parseConnectOffer(requireNotNull(peerOffer()))
        return gate.consume(proof,publicKey(),peer,System.currentTimeMillis())
    }
    fun revoke(){check(prefs.edit().remove("peer_public").commit())}
}
