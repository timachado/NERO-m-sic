package br.com.timachado.neromusic

import android.content.Context
import android.net.Network
import android.os.SystemClock
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Experimental legacy webOS SSAP browser launch. Only selected, discovered
 * Wi-Fi peers; no global cleartext configuration or certificate bypass.
 * No client key is retained or sent on this unencrypted legacy transport.
 * A registered response is required before issuing the single LAUNCH command.
 */
internal object NeroWebOsReceiver {
    private const val ORIGIN="https://nero-tv-receiver-test.timachado-mcp.workers.dev/"
    private const val MAX=65536
    private val random=SecureRandom()

    fun receiverUrl(id:String,title:String,artist:String,seconds:Long):String{
        require(id.matches(Regex("[A-Za-z0-9_-]{11}")))
        fun enc(s:String)=URLEncoder.encode(s.take(180),"UTF-8")
        return ORIGIN+"#v="+id+"&title="+enc(title)+"&artist="+enc(artist)+"&t="+seconds.coerceIn(0L,86400L)
    }
    /** Experimental receiver link, not yet wired to Android playback.
     * Only public video identifiers, no account tokens or signed media URLs.
     * A production session must use authenticated pairing, not URL fragments.
     */
    internal fun receiverQueueUrl(ids:List<String>,index:Int,seconds:Long):String{
        require(ids.size in 1..100&&index in ids.indices)
        require(ids.all{it.matches(Regex("[A-Za-z0-9_-]{11}"))})
        val encoded=URLEncoder.encode(ids.joinToString(","),"UTF-8")
        return ORIGIN+"#q="+encoded+"&i="+index+"&t="+seconds.coerceIn(0L,86400L)
    }
    fun isWebOs(name:String)=name.contains("webOS",ignoreCase=true)
    fun open(context:Context,network:Network,host:String,id:String,title:String,artist:String,seconds:Long):Boolean {
        require(host.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")))
        val address=InetAddress.getByName(host)
        require(address.hostAddress==host&&NeroDlna.isLocalPeer(context,network,address))
        val target=receiverUrl(id,title,artist,seconds)
        val deadline=SystemClock.elapsedRealtime()+35000L
        network.socketFactory.createSocket().use{socket->
            socket.connect(InetSocketAddress(address,3000),2500)
            socket.soTimeout=1500
            val input=BufferedInputStream(socket.getInputStream())
            val output=socket.getOutputStream()
            fun read():Int {
                while(SystemClock.elapsedRealtime()<deadline){
                    try{return input.read().also{check(it>=0)}}catch(_:java.net.SocketTimeoutException){}
                }
                error("Tempo de resposta excedido")
            }
            fun line():String{
                val bytes=ByteArrayOutputStream()
                while(true){
                    val b=read()
                    if(b==10)break
                    if(b!=13){check(bytes.size()<4096);bytes.write(b)}
                }
                return bytes.toString("US-ASCII")
            }
            val key=Base64.encodeToString(ByteArray(16).also{random.nextBytes(it)},Base64.NO_WRAP)
            output.write(("GET / HTTP/1.1\r\nHost: "+host+":3000\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: "+key+"\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray(Charsets.US_ASCII))
            output.flush()
            check(line().matches(Regex("HTTP/1\\.1 101( .*)?")))
            val headers=mutableMapOf<String,String>()
            for(i in 0..31){
                val value=line();if(value.isEmpty())break
                check(i<31&&value.contains(':'))
                headers[value.substringBefore(':').lowercase()]=value.substringAfter(':').trim()
            }
            val accept=Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII)),Base64.NO_WRAP)
            check(headers["sec-websocket-accept"]==accept&&headers["upgrade"].equals("websocket",true))
            check(headers["connection"]?.split(',')?.any{it.trim().equals("upgrade",true)}==true)
            check(headers["sec-websocket-extensions"].isNullOrBlank())
            fun sendJson(value:JSONObject)=frame(output,1,value.toString().toByteArray(Charsets.UTF_8))
            fun nextJson():JSONObject{
                while(true){
                    val first=read();val second=read()
                    val op=first and 15
                    check((first and 0x80)!=0&&(first and 0x70)==0&&(second and 0x80)==0)
                    var size=second and 127
                    if(size==126)size=(read() shl 8) or read()
                    else if(size==127)error("Resposta extensa")
                    check(size<=MAX&&(op<8||size<=125))
                    val payload=ByteArray(size){read().toByte()}
                    when(op){
                        1->return JSONObject(String(payload,Charsets.UTF_8))
                        8->error("Conexão encerrada pela TV")
                        9->frame(output,10,payload)
                        10->Unit
                        else->error("Resposta incompatível")
                    }
                }
            }
            val manifest=JSONObject().put("manifestVersion",1).put("permissions",JSONArray().put("LAUNCH"))
            sendJson(JSONObject().put("type","register").put("id","nero-register").put("payload",
                JSONObject().put("pairingType","PROMPT").put("manifest",manifest)))
            var registered=false
            while(!registered){
                val msg=nextJson()
                if(msg.optString("id")!="nero-register")continue
                if(msg.optString("type")=="error")return false
                if(msg.optString("type")=="registered")registered=true
            }
            check(NeroDlna.isLocalPeer(context,network,address))
            sendJson(JSONObject().put("type","request").put("id","nero-open").put("uri","ssap://system.launcher/open")
                .put("payload",JSONObject().put("target",target)))
            while(true){
                val msg=nextJson()
                if(msg.optString("id")!="nero-open")continue
                if(msg.optString("type")=="error")return false
                if(msg.optString("type")=="response"){
                    val accepted=msg.optJSONObject("payload")?.optBoolean("returnValue",false)==true
                    runCatching{frame(output,8,byteArrayOf(3,-24))}
                    return accepted
                }
            }
        }
    }
    private fun frame(output:OutputStream,opcode:Int,bytes:ByteArray){
        require(bytes.size<=65535)
        val mask=ByteArray(4).also{random.nextBytes(it)}
        output.write(0x80 or opcode)
        if(bytes.size<126)output.write(0x80 or bytes.size)
        else{output.write(0x80 or 126);output.write(bytes.size shr 8);output.write(bytes.size and 255)}
        output.write(mask)
        output.write(ByteArray(bytes.size){i->(bytes[i].toInt() xor mask[i%4].toInt()).toByte()})
        output.flush()
    }
}
