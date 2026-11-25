package br.com.timachado.neromusic

import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.util.Base64

private val challengeId=Regex("[A-Za-z0-9_-]{43}")
private fun connectB64(bytes:ByteArray)=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
internal fun connectDeviceId(key:PublicKey)=connectB64(MessageDigest.getInstance("SHA-256").digest(key.encoded))

internal data class ConnectChallenge(val verifier:String,val prover:String,val nonce:String,val created:Long,val expires:Long){
    fun wire()="NERO-AUTH1:"+listOf(verifier,prover,nonce,created.toString(),expires.toString()).joinToString(":")
    fun bytes()=wire().toByteArray(Charsets.UTF_8)
    fun valid(now:Long):Boolean =
        now>0&&created>0&&expires>created&&expires-created<=120000L&&
        created<=now+5000L&&now<expires&&verifier!=prover&&
        listOf(verifier,prover,nonce).all{challengeId.matches(it)}
}
internal fun newConnectChallenge(verifier:PublicKey,prover:PublicKey,now:Long):ConnectChallenge{
    require(now in 1L..Long.MAX_VALUE-120000L)
    val nonce=ByteArray(32).also{SecureRandom().nextBytes(it)}
    return ConnectChallenge(connectDeviceId(verifier),connectDeviceId(prover),connectB64(nonce),now,now+120000L)
        .also{require(it.valid(now))}
}
internal fun parseConnectChallenge(wire:String):ConnectChallenge{
    require(wire.length<=256)
    val parts=wire.trim().split(':')
    require(parts.size==6&&parts[0]=="NERO-AUTH1")
    return ConnectChallenge(parts[1],parts[2],parts[3],parts[4].toLong(),parts[5].toLong())
        .also{require(it.wire()==wire.trim())}
}
internal fun signConnectChallenge(challenge:ConnectChallenge,local:PublicKey,peer:PublicKey,privateKey:PrivateKey,now:Long):String{
    require(challenge.valid(now)&&challenge.prover==connectDeviceId(local)&&challenge.verifier==connectDeviceId(peer))
    val signer=Signature.getInstance("SHA256withECDSA")
    signer.initSign(privateKey);signer.update(challenge.bytes())
    return "NERO-PROOF1:"+challenge.nonce+":"+connectB64(signer.sign())
}
internal fun verifyConnectProof(challenge:ConnectChallenge,proof:String,local:PublicKey,peer:PublicKey,now:Long):Boolean=runCatching{
    require(challenge.valid(now)&&challenge.verifier==connectDeviceId(local)&&challenge.prover==connectDeviceId(peer))
    require(proof.length<=180)
    val parts=proof.trim().split(':')
    require(parts.size==3&&parts[0]=="NERO-PROOF1"&&parts[1]==challenge.nonce)
    val sig=Base64.getUrlDecoder().decode(parts[2])
    require(sig.size in 8..80&&connectB64(sig)==parts[2])
    val verifier=Signature.getInstance("SHA256withECDSA")
    verifier.initVerify(peer);verifier.update(challenge.bytes());verifier.verify(sig)
}.getOrDefault(false)

// One pending challenge per screen session. Verification consumes it, even if invalid.
// It is never persisted as a remote authorization.
internal class ConnectProofGate{
    private var pending:ConnectChallenge?=null
    fun issue(local:PublicKey,peer:PublicKey,now:Long):ConnectChallenge=
        newConnectChallenge(local,peer,now).also{pending=it}
    fun consume(proof:String,local:PublicKey,peer:PublicKey,now:Long):Boolean{
        val current=pending?:return false
        pending=null
        return verifyConnectProof(current,proof,local,peer,now)
    }
    fun clear(){pending=null}
}
