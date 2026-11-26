package br.com.timachado.neromusic

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.Base64
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

internal fun connectPrivateIpv4(value:String):Inet4Address?{
    val parts=value.split('.')
    if(parts.size!=4||parts.any{it.isEmpty()||it.length>3||it.any{c->c !in '0'..'9'}||it.length>1&&it.startsWith("0")})return null
    val nums=parts.map{it.toInt()}
    if(nums.any{it !in 0..255})return null
    if(!(nums[0]==10||nums[0]==172&&nums[1] in 16..31||nums[0]==192&&nums[1]==168))return null
    return InetAddress.getByAddress(nums.map{it.toByte()}.toByteArray()) as Inet4Address
}
internal fun connectSameSubnet(a:Inet4Address,b:Inet4Address,prefix:Int):Boolean{
    if(prefix !in 1..32)return false
    for(i in 0 until prefix){
        val mask=1 shl (7-i%8)
        if((a.address[i/8].toInt() and mask)!=(b.address[i/8].toInt() and mask))return false
    }
    return true
}
private data class ConnectWifi(val network:Network,val address:Inet4Address,val prefix:Int)
private fun connectWifi(context:Context):ConnectWifi{
    val manager=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network=manager.activeNetwork?:error("Wi-Fi indisponível")
    val caps=manager.getNetworkCapabilities(network)?:error("Rede indisponível")
    require(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
    val link=manager.getLinkProperties(network)?.linkAddresses?.firstOrNull{
        it.address is Inet4Address&&connectPrivateIpv4(it.address.hostAddress.orEmpty())!=null
    }?:error("Endereço Wi-Fi privado indisponível")
    return ConnectWifi(network,link.address as Inet4Address,link.prefixLength)
}
private fun connectNonce()=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also{SecureRandom().nextBytes(it)})
private val lanActions=setOf(ConnectAction.PLAY,ConnectAction.PAUSE,ConnectAction.NEXT,ConnectAction.PREVIOUS)

// Explicit, foreground-only receiver. One authenticated connection/command, fresh
// nonce per connection. Bound to private Wi-Fi address, no discovery or relay.
internal class NeroConnectLanReceiver(private val context:Context,private val uid:String){
    @Volatile private var closed=false
    @Volatile private var server:SSLServerSocket?=null
    @Volatile private var active:SSLSocket?=null
    fun close(){
        closed=true
        runCatching{active?.close()}
        runCatching{server?.close()}
    }
    suspend fun listen(onReady:(String)->Unit,onCommand:(ConnectAction)->Boolean)=withContext(Dispatchers.IO){
        val identity=NeroConnectIdentity(context,uid).tlsIdentity()
        val wifi=connectWifi(context)
        val tls=connectTlsContext(identity)
        val deadline=SystemClock.elapsedRealtime()+300000L
        var lastCommand=0L
        val listener=tls.serverSocketFactory.createServerSocket() as SSLServerSocket
        server=listener
        try{
            if(closed)return@withContext
            listener.enabledProtocols=connectSecureProtocols(listener.supportedProtocols)
            listener.needClientAuth=true
            listener.soTimeout=1000
            listener.bind(InetSocketAddress(wifi.address,0),2)
            withContext(Dispatchers.Main){if(!closed)onReady(wifi.address.hostAddress+":"+listener.localPort)}
            while(!closed&&connectOwnerActive(uid)&&SystemClock.elapsedRealtime()<deadline){
                currentCoroutineContext().ensureActive()
                if(connectWifi(context)!=wifi)break
                val socket=try{listener.accept() as SSLSocket}catch(_:SocketTimeoutException){continue}
                active=socket
                try{
                    socket.use{
                        val address=socket.inetAddress as? Inet4Address?:return@use
                        if(connectPrivateIpv4(address.hostAddress.orEmpty())==null||!connectSameSubnet(wifi.address,address,wifi.prefix)||closed)return@use
                        socket.soTimeout=3000
                        socket.requireConnectPeer(identity.peer)
                        val nonce=connectNonce()
                        val out=DataOutputStream(socket.outputStream)
                        val input=DataInputStream(socket.inputStream)
                        out.writeUTF("NERO-LAN1:"+nonce);out.flush()
                        val received=input.readUTF()
                        val parts=received.split(':')
                        val action=parts.getOrNull(1)?.let{raw->ConnectAction.entries.firstOrNull{it.name==raw}}
                        val now=SystemClock.elapsedRealtime()
                        val valid=received.length<=80&&parts.size==2&&parts[0]==nonce&&
                            action in lanActions&&connectOwnerActive(uid)&&
                            now<deadline&&now-lastCommand>=500&&!closed&&connectWifi(context)==wifi&&
                            NeroConnectIdentity(context,uid).peerOffer()==connectPublicOffer(identity.peer)
                        val response=if(valid){
                            lastCommand=now
                            withContext(Dispatchers.Main){
                                if(closed||!connectOwnerActive(uid)||SystemClock.elapsedRealtime()>=deadline)"REJECTED"
                                else if(action!=null&&onCommand(action))"OK" else "REJECTED"
                            }
                        }else "REJECTED"
                        out.writeUTF(response);out.flush()
                    }
                }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){/* Reject connection; never run unverified commands. */}
                finally{active=null}
            }
        }finally{close();server=null}
    }
}
internal suspend fun sendConnectLan(context:Context,uid:String,endpoint:String,action:ConnectAction):Boolean{
    require(action in lanActions)
    return exchangeConnectLan(context,uid,endpoint,action.name).first=="OK"
}
private suspend fun exchangeConnectLan(context:Context,uid:String,endpoint:String,request:String):Pair<String,Long> = withContext(Dispatchers.IO){
    require(endpoint.length<=25&&lanActions.any{it.name==request}&&connectOwnerActive(uid))
    val parts=endpoint.trim().split(':')
    require(parts.size==2)
    val address=requireNotNull(connectPrivateIpv4(parts[0]))
    val port=parts[1].toInt()
    require(port in 1024..65535)
    val wifi=connectWifi(context)
    require(address!=wifi.address&&connectSameSubnet(wifi.address,address,wifi.prefix))
    val identity=NeroConnectIdentity(context,uid).tlsIdentity()
    val tls=connectTlsContext(identity)
    wifi.network.socketFactory.createSocket().use{raw->
        raw.connect(InetSocketAddress(address,port),3000)
        raw.soTimeout=3000
        (tls.socketFactory.createSocket(raw,address.hostAddress.orEmpty(),port,true) as SSLSocket).use{socket->
            socket.enabledProtocols=connectSecureProtocols(socket.supportedProtocols)
            socket.soTimeout=3000
            socket.requireConnectPeer(identity.peer)
            val input=DataInputStream(socket.inputStream)
            val out=DataOutputStream(socket.outputStream)
            val greeting=input.readUTF()
            require(greeting.matches(Regex("NERO-LAN1:[A-Za-z0-9_-]{43}")))
            currentCoroutineContext().ensureActive()
            require(connectOwnerActive(uid)&&NeroConnectIdentity(context,uid).peerOffer()==connectPublicOffer(identity.peer)&&connectWifi(context)==wifi)
            val started=SystemClock.elapsedRealtime()
            out.writeUTF(greeting.substringAfter(':')+":"+request);out.flush()
            val response=input.readUTF()
            require(response.length<=160)
            response to (SystemClock.elapsedRealtime()-started)
        }
    }
}
