package br.com.timachado.neromusic

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Debug-only finite LAN fixture server. No catalogue, credentials or user files.
 * Bound to the selected Wi-Fi interface; accepts only the selected TV IP.
 */
internal class NeroLanAudioProbe(context:Context,d:NeroDlnaDevice):Closeable{
    private val peer=InetAddress.getByName(d.endpoint.host)
    private val local=DatagramSocket().use{
        require(NeroDlna.isLocalPeer(context,d.network,peer))
        d.network.bindSocket(it)
        it.connect(peer,9)
        it.localAddress
    }
    private val token=UUID.randomUUID().toString()
    private val data=mapOf("a.wav" to wav(440.0),"b.wav" to wav(660.0))
    private val server=ServerSocket().apply{
        require(BuildConfig.DEBUG&&local is Inet4Address&&!local.isAnyLocalAddress)
        val cm=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        require(cm.getLinkProperties(d.network)?.linkAddresses?.any{it.address==local}==true)
        bind(InetSocketAddress(local,0),2)
        soTimeout=500
    }
    val a="http://"+local.hostAddress+":"+server.localPort+"/"+token+"/a.wav"
    val b="http://"+local.hostAddress+":"+server.localPort+"/"+token+"/b.wav"
    @Volatile private var active:Socket?=null
    @Volatile var mediaRequested=false
        private set
    private val thread=Thread({
        val deadline=SystemClock.elapsedRealtime()+90000L
        try{
            while(!server.isClosed&&SystemClock.elapsedRealtime()<deadline){
                val socket=try{server.accept()}catch(_:SocketTimeoutException){continue}
                socket.use{
                    active=it
                    try{
                        if(!server.isClosed&&it.inetAddress==peer)serve(it)
                    }catch(_:Exception){}finally{active=null}
                }
            }
        }catch(_:Exception){}finally{runCatching{server.close()}}
    },"nero-lan-audio-probe").apply{isDaemon=true;start()}

    private fun serve(socket:Socket){
        socket.soTimeout=1200
        val deadline=SystemClock.elapsedRealtime()+2500L
        val input=BufferedInputStream(socket.getInputStream())
        fun line():String{
            val out=ByteArrayOutputStream()
            while(true){
                check(SystemClock.elapsedRealtime()<deadline&&out.size()<2048)
                val c=input.read();check(c>=0)
                if(c==10)break
                if(c!=13)out.write(c)
            }
            return out.toString("US-ASCII")
        }
        val request=line().split(' ')
        require(request.size==3&&request[0] in setOf("GET","HEAD")&&request[2] in setOf("HTTP/1.0","HTTP/1.1"))
        val prefix="/$token/"
        require(request[1].startsWith(prefix))
        val bytes=data[request[1].removePrefix(prefix)]?:return
        var range:String?=null
        var ended=false
        for(i in 0..31){
            val h=line()
            if(h.isEmpty()){ended=true;break}
            if(h.substringBefore(':').equals("Range",true))range=h.substringAfter(':').trim()
        }
        require(ended)
        val r=if(range==null)0..bytes.lastIndex else audioProbeRange(range,bytes.size)
        val output=socket.getOutputStream()
        if(r==null){
            output.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */"+bytes.size+"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
            output.flush();return
        }
        val partial=range!=null
        val length=r.last-r.first+1
        val header=buildString{
            append(if(partial)"HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            append("Content-Type: audio/wav\r\nContent-Length: $length\r\nAccept-Ranges: bytes\r\nConnection: close\r\nCache-Control: no-store\r\n")
            if(partial)append("Content-Range: bytes "+r.first+"-"+r.last+"/"+bytes.size+"\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.US_ASCII))
        if(request[0]=="GET"){
            mediaRequested=true
            output.write(bytes,r.first,length)
        }
        output.flush()
    }
    override fun close(){
        runCatching{server.close()}
        runCatching{active?.close()}
    }
}
internal fun audioProbeRange(value:String?,size:Int):IntRange?{
    if(size<=0||value==null)return null
    val m=Regex("bytes=(\\d*)-(\\d*)").matchEntire(value)?:return null
    val first=m.groupValues[1];val last=m.groupValues[2]
    if(first.isEmpty()){
        val n=last.toLongOrNull()?.takeIf{it>0}?:return null
        return (size-n.coerceAtMost(size.toLong()).toInt()) until size
    }
    val start=first.toLongOrNull()?.takeIf{it in 0 until size.toLong()}?.toInt()?:return null
    val end=if(last.isEmpty())size-1 else last.toLongOrNull()?.takeIf{it>=start}?.coerceAtMost(size.toLong()-1)?.toInt()?:return null
    return start..end
}
private fun wav(frequency:Double):ByteArray{
    val rate=44100
    val count=rate*12
    val length=count*2
    val b=ByteBuffer.allocate(44+length).order(ByteOrder.LITTLE_ENDIAN)
    fun tag(s:String){b.put(s.toByteArray(Charsets.US_ASCII))}
    tag("RIFF");b.putInt(36+length);tag("WAVE");tag("fmt ");b.putInt(16)
    b.putShort(1);b.putShort(1);b.putInt(rate);b.putInt(rate*2);b.putShort(2);b.putShort(16)
    tag("data");b.putInt(length)
    repeat(count){i->
        val t=i.toDouble()/rate
        val p=t%1.0
        val envelope=if(p<0.35)minOf(1.0,p*30,(0.35-p)*30) else 0.0
        b.putShort((1600*envelope*kotlin.math.sin(2*Math.PI*frequency*t)).toInt().toShort())
    }
    return b.array()
}
