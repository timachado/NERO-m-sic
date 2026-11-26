package br.com.timachado.neromusic

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.PublicKey
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.*

internal data class ConnectTlsIdentity(val certificate:X509Certificate,val privateKey:PrivateKey,val peer:PublicKey)
internal fun sameConnectKey(a:PublicKey,b:PublicKey)=MessageDigest.isEqual(a.encoded,b.encoded)
internal class ConnectPinnedTrust(private val peer:PublicKey):X509TrustManager{
    private fun verify(chain:Array<out X509Certificate>?){
        val leaf=chain?.firstOrNull()?:throw CertificateException("Missing peer certificate")
        leaf.checkValidity()
        if(!sameConnectKey(leaf.publicKey,peer))throw CertificateException("Unpaired peer")
    }
    override fun checkClientTrusted(chain:Array<out X509Certificate>?,authType:String?){verify(chain)}
    override fun checkServerTrusted(chain:Array<out X509Certificate>?,authType:String?){verify(chain)}
    override fun getAcceptedIssuers():Array<X509Certificate> = emptyArray()
}
private class ConnectKeyManager(private val identity:ConnectTlsIdentity):X509KeyManager{
    private val alias="nero"
    private fun accepts(type:String?)=type?.startsWith("EC",true)==true
    override fun getClientAliases(keyType:String?,issuers:Array<out Principal>?):Array<String>?=if(accepts(keyType))arrayOf(alias)else null
    override fun chooseClientAlias(keyType:Array<out String>?,issuers:Array<out Principal>?,socket:Socket?):String?=if(keyType?.any(::accepts)==true)alias else null
    override fun getServerAliases(keyType:String?,issuers:Array<out Principal>?):Array<String>?=if(accepts(keyType))arrayOf(alias)else null
    override fun chooseServerAlias(keyType:String?,issuers:Array<out Principal>?,socket:Socket?):String?=if(accepts(keyType))alias else null
    override fun getCertificateChain(alias:String?):Array<X509Certificate>?=if(alias==this.alias)arrayOf(identity.certificate)else null
    override fun getPrivateKey(alias:String?):PrivateKey?=if(alias==this.alias)identity.privateKey else null
}
internal fun connectTlsContext(identity:ConnectTlsIdentity):SSLContext=SSLContext.getInstance("TLS").apply{
    init(arrayOf<KeyManager>(ConnectKeyManager(identity)),arrayOf<TrustManager>(ConnectPinnedTrust(identity.peer)),null)
}
internal fun connectSecureProtocols(supported:Array<String>)=supported.filter{it=="TLSv1.3"||it=="TLSv1.2"}.toTypedArray().also{require(it.isNotEmpty())}
internal fun SSLSocket.requireConnectPeer(peer:PublicKey){
    startHandshake()
    val cert=session.peerCertificates.firstOrNull() as? X509Certificate?:throw SSLPeerUnverifiedException("Missing peer")
    if(!sameConnectKey(cert.publicKey,peer))throw SSLPeerUnverifiedException("Unpaired peer")
}
