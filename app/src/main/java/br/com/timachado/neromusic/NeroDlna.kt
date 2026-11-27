package br.com.timachado.neromusic

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.*

internal data class NeroDlnaDevice(val name:String,val endpoint:URI,val service:String,val network:Network)
internal data class NeroDialDevice(val name:String,val endpoint:URI,val network:Network)
internal data class NeroVolumeDevice(val name:String,val endpoint:URI,val service:String,val network:Network)
internal data class NeroTvDiscovery(val dlna:List<NeroDlnaDevice>,val dial:List<NeroDialDevice>,val volume:List<NeroVolumeDevice> = emptyList())
internal class NeroDlnaHttpException(val status:Int):java.io.IOException("DLNA HTTP $status")
internal object NeroDlna {
    private const val LIMIT=256*1024
    private val servicePattern=Regex("urn:schemas-upnp-org:service:AVTransport:[1-9][0-9]?")
    private val renderingPattern=Regex("urn:schemas-upnp-org:service:RenderingControl:[1-9][0-9]?")
    internal fun location(response:String):String? {
        if(!response.startsWith("HTTP/1.1 200 ",true)&&!response.startsWith("HTTP/1.0 200 ",true))return null
        return response.lineSequence().firstOrNull{it.substringBefore(':').trim().equals("location",true)}
            ?.substringAfter(':')?.trim()?.takeIf{it.length in 8..2048}
    }
    internal fun safeEndpoint(value:String,peer:String):URI?=runCatching{
        val uri=URI(value)
        require(uri.scheme=="http"&&uri.host==peer&&uri.rawUserInfo==null&&uri.rawFragment==null)
        require(uri.port==-1||uri.port in 1..65535)
        require(uri.rawPath.orEmpty().startsWith("/")||uri.rawPath.isNullOrEmpty())
        require(value.length<=2048)
        uri
    }.getOrNull()
    private fun privateV4(a:InetAddress):Boolean{
        if(a !is Inet4Address)return false
        val b=a.address.map{it.toInt() and 255}
        return b[0]==10||(b[0]==172&&b[1] in 16..31)||(b[0]==192&&b[1]==168)||(b[0]==169&&b[1]==254)
    }
    private fun manager(context:Context)=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    internal fun isLocalPeer(context:Context,network:Network,a:InetAddress):Boolean=local(context,network,a)
    private fun local(context:Context,network:Network,a:InetAddress):Boolean{
        val cm=manager(context)
        if(!privateV4(a)||cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)!=true)return false
        return cm.getLinkProperties(network)?.routes?.any{it.gateway?.isAnyLocalAddress!=false && it.destination.prefixLength>0 && it.destination.contains(a)}==true
    }
    fun discover(context:Context):List<NeroDlnaDevice> = discoverTargets(context).dlna
    fun discoverTargets(context:Context):NeroTvDiscovery{
        val cm=manager(context)
        val network=cm.allNetworks.firstOrNull{cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true}
            ?:error("Conecte o celular ao Wi-Fi.")
        val wifi=context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock=wifi.createMulticastLock("nero-dlna").apply{setReferenceCounted(false);acquire()}
        try {
            val found=linkedMapOf<URI,String>()
            DatagramSocket().use{socket->
                network.bindSocket(socket)
                socket.soTimeout=400
                val group=InetAddress.getByName("239.255.255.250")
                for(st in listOf("urn:schemas-upnp-org:device:MediaRenderer:1","urn:schemas-upnp-org:device:MediaRenderer:2","urn:schemas-upnp-org:service:AVTransport:1","urn:schemas-upnp-org:service:AVTransport:2","urn:schemas-upnp-org:service:RenderingControl:1","urn:schemas-upnp-org:service:RenderingControl:2","urn:dial-multiscreen-org:service:dial:1")){
                    val bytes=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: "+st+"\r\n\r\n").toByteArray(Charsets.US_ASCII)
                    socket.send(DatagramPacket(bytes,bytes.size,group,1900))
                }
                val end=SystemClock.elapsedRealtime()+3500
                while(SystemClock.elapsedRealtime()<end&&found.size<12){
                    val packet=DatagramPacket(ByteArray(8192),8192)
                    try{socket.receive(packet)}catch(_:SocketTimeoutException){continue}
                    if(!local(context,network,packet.address))continue
                    val peer=packet.address.hostAddress?:continue
                    val url=location(String(packet.data,0,packet.length,Charsets.UTF_8))?:continue
                    val endpoint=safeEndpoint(url,peer)?:continue
                    found[endpoint]=peer
                }
            }
            val results=mutableListOf<NeroDlnaDevice>()
            val dial=mutableListOf<NeroDialDevice>()
            val volume=mutableListOf<NeroVolumeDevice>()
            for((endpoint,peer) in found.entries.take(8)){
                runCatching {
                    val response=request(context,network,endpoint,null,null)
                    val xml=response.body
                    description(xml)?.let{values->
                        safeEndpoint(endpoint.resolve(values.third).toString(),peer)?.let{control->
                            results.add(NeroDlnaDevice(values.first,control,values.second,network))
                        }
                    }
                    description(xml,renderingPattern)?.let{values->
                        safeEndpoint(endpoint.resolve(values.third).toString(),peer)?.let{control->
                            volume.add(NeroVolumeDevice(values.first,control,values.second,network))
                        }
                    }
                    val app=applicationEndpoint(response.headers["application-url"],peer)
                    if(app!=null){
                        val info=http(context,network,app,null,null)
                        if(field(info,"state") in setOf("running","stopped")){
                            val name=field(xml,"friendlyName")?.trim()?.take(100)?.ifBlank{"Smart TV"}?:"Smart TV"
                            dial.add(NeroDialDevice(name,app,network))
                        }
                    }
                }
            }
            return NeroTvDiscovery(results.distinctBy{it.endpoint},dial.distinctBy{it.endpoint.host},volume.distinctBy{it.endpoint})
        } finally {if(lock.isHeld)lock.release()}
    }
    internal fun applicationEndpoint(base:String?,peer:String):URI?{
        val uri=safeEndpoint(base?:return null,peer)?:return null
        if(uri.rawQuery!=null||uri.rawPath.orEmpty().split('/').any{it==".."||it=="."}||uri.rawPath.orEmpty().contains('%'))return null
        return safeEndpoint(uri.toString().trimEnd('/')+"/YouTube",peer)
    }
    internal fun nativeVideoId(mediaId:String?):String? =
        mediaId?.takeIf{it.startsWith("youtube:")}?.removePrefix("youtube:")?.takeIf{it.matches(Regex("[A-Za-z0-9_-]{11}"))}
    internal fun launchParameters(id:String,seconds:Long,pairingCode:java.util.UUID):String{
        require(id.matches(Regex("[A-Za-z0-9_-]{11}"))&&seconds>=0)
        return "pairingCode="+pairingCode.toString()+"&v="+id+"&t="+seconds+".0"
    }
    // DIAL launches the TV's own app. Running state is NOT proof of playback.
    fun launchNative(context:Context,d:NeroDialDevice,id:String,seconds:Long):Boolean{
        request(context,d.network,d.endpoint,null,launchParameters(id,seconds,java.util.UUID.randomUUID()),true)
        val deadline=SystemClock.elapsedRealtime()+8000L
        while(SystemClock.elapsedRealtime()<deadline){
            val state=runCatching{field(http(context,d.network,d.endpoint,null,null),"state")}.getOrNull()
            if(state=="running")return true
            Thread.sleep(500)
        }
        return false
    }
    private fun parser(xml:String):XmlPullParser {
        require(!xml.contains("<!DOCTYPE",true)&&!xml.contains("<!ENTITY",true))
        return Xml.newPullParser().apply{setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);setInput(xml.reader())}
    }
    private fun description(xml:String,pattern:Regex=servicePattern):Triple<String,String,String>?{
        val p=parser(xml);var name="Dispositivo DLNA";var type="";var control="";var serviceDepth=-1
        while(p.eventType!=XmlPullParser.END_DOCUMENT){
            if(p.eventType==XmlPullParser.START_TAG){
                when(p.name){
                    "friendlyName"->if(name=="Dispositivo DLNA")name=p.nextText().trim().take(100).ifBlank{"Dispositivo DLNA"}
                    "service"->{serviceDepth=p.depth;type="";control=""}
                    "serviceType"->if(serviceDepth>0)type=p.nextText().trim()
                    "controlURL"->if(serviceDepth>0)control=p.nextText().trim()
                }
            }else if(p.eventType==XmlPullParser.END_TAG&&p.name=="service"&&p.depth==serviceDepth){
                if(pattern.matches(type)&&control.isNotBlank())return Triple(name,type,control)
                serviceDepth=-1
            }
            p.next()
        }
        return null
    }
    internal fun escape(value:String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    private fun field(xml:String,name:String):String?{
        val p=parser(xml)
        while(p.eventType!=XmlPullParser.END_DOCUMENT){
            if(p.eventType==XmlPullParser.START_TAG&&p.name==name)return p.nextText()
            p.next()
        }
        return null
    }
    private fun soap(context:Context,d:NeroDlnaDevice,action:String,args:String):String{
        require(servicePattern.matches(d.service))
        val body="<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:"+action+" xmlns:u=\""+d.service+"\"><InstanceID>0</InstanceID>"+args+"</u:"+action+"></s:Body></s:Envelope>"
        return http(context,d.network,d.endpoint,d.service+"#"+action,body)
    }
    internal fun mediaMetadata(url:String,title:String,artist:String,mime:String):String{
        require(mime in setOf("audio/mpeg","audio/flac","audio/wav","audio/ogg"))
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>"+escape(title.take(300))+"</dc:title><upnp:artist>"+escape(artist.take(300))+"</upnp:artist><upnp:class>object.item.audioItem.musicTrack</upnp:class><res protocolInfo=\"http-get:*:"+mime+":*\">"+escape(url)+"</res></item></DIDL-Lite>"
    }
    fun play(context:Context,d:NeroDlnaDevice,url:String,title:String,artist:String,mime:String){
        val metadata=mediaMetadata(url,title,artist,mime)
        soap(context,d,"SetAVTransportURI","<CurrentURI>"+escape(url)+"</CurrentURI><CurrentURIMetaData>"+escape(metadata)+"</CurrentURIMetaData>")
        soap(context,d,"Play","<Speed>1</Speed>")
    }
    /** Optional renderer-owned next-track slot (not a complete playlist API).
     * Only called after the selected receiver confirms the current test item.
     * Never substitute a successful SOAP acknowledgement for actual transition.
     */
    fun prepareNext(context:Context,d:NeroDlnaDevice,currentUrl:String,nextUrl:String,title:String,artist:String,mime:String):Boolean{
        require(currentUrl!=nextUrl)
        val before=soap(context,d,"GetMediaInfo","")
        if(field(before,"CurrentURI")!=currentUrl)return false
        val metadata=mediaMetadata(nextUrl,title,artist,mime)
        soap(context,d,"SetNextAVTransportURI","<NextURI>"+escape(nextUrl)+"</NextURI><NextURIMetaData>"+escape(metadata)+"</NextURIMetaData>")
        val after=soap(context,d,"GetMediaInfo","")
        return field(after,"CurrentURI")==currentUrl&&field(after,"NextURI")==nextUrl
    }
    fun currentUri(context:Context,d:NeroDlnaDevice):String?=
        field(soap(context,d,"GetMediaInfo",""),"CurrentURI")
    // A successful SOAP response only acknowledges the command. Confirm
    // the actual transport state and URI before pausing local playback.
    internal fun confirmedPlayback(state:String?,currentUri:String?,expectedUri:String):Boolean =
        state=="PLAYING" && currentUri==expectedUri
    fun transportState(context:Context,d:NeroDlnaDevice):String?=
        field(soap(context,d,"GetTransportInfo",""),"CurrentTransportState")
    fun awaitPlaying(context:Context,d:NeroDlnaDevice,url:String):Boolean{
        val deadline=SystemClock.elapsedRealtime()+12000L
        while(SystemClock.elapsedRealtime()<deadline){
            val state=transportState(context,d)
            if(state=="PLAYING"){
                val current=field(soap(context,d,"GetMediaInfo",""),"CurrentURI")
                return confirmedPlayback(state,current,url)
            }
            if(state=="NO_MEDIA_PRESENT")return false
            Thread.sleep(500)
        }
        return false
    }
    fun control(context:Context,d:NeroDlnaDevice,url:String,action:String){
        require(action in setOf("Pause","Play","Stop"))
        val current=field(soap(context,d,"GetMediaInfo",""),"CurrentURI")
        require(current==url)
        soap(context,d,action,if(action=="Play")"<Speed>1</Speed>" else "")
    }
    // RenderingControl is independent of AVTransport and does not imply ownership
    // of the external casting session. Only explicitly selected devices are used.
    private fun volumeSoap(context:Context,d:NeroVolumeDevice,action:String,args:String):String{
        require(renderingPattern.matches(d.service))
        require(action in setOf("GetVolume","SetVolume"))
        val body="""<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:$action xmlns:u="${d.service}"><InstanceID>0</InstanceID><Channel>Master</Channel>$args</u:$action></s:Body></s:Envelope>"""
        return http(context,d.network,d.endpoint,d.service+"#"+action,body)
    }
    internal fun parseVolume(value:String?):Int?=value?.trim()?.toIntOrNull()?.takeIf{it in 0..100}
    internal fun adjustedVolume(current:Int,delta:Int):Int{
        require(current in 0..100&&delta in -10..10)
        return (current+delta).coerceIn(0,100)
    }
    fun getVolume(context:Context,d:NeroVolumeDevice):Int =
        requireNotNull(parseVolume(field(volumeSoap(context,d,"GetVolume",""),"CurrentVolume")))
    fun changeVolume(context:Context,d:NeroVolumeDevice,delta:Int):Int{
        val current=getVolume(context,d)
        val target=adjustedVolume(current,delta)
        if(target!=current)volumeSoap(context,d,"SetVolume","<DesiredVolume>$target</DesiredVolume>")
        // Read back actual device volume; never infer success from SOAP acknowledgement.
        return getVolume(context,d)
    }
    // Only local HTTP to the verified responder on Wi-Fi. No redirects, proxy or
    // global cleartext permission. Bounded response and time.
    private data class LocalResponse(val headers:Map<String,String>,val body:String)
    private fun http(context:Context,network:Network,uri:URI,action:String?,xml:String?):String =
        request(context,network,uri,action,xml).body
    private fun request(context:Context,network:Network,uri:URI,action:String?,xml:String?,dialPost:Boolean=false):LocalResponse {
        val host=requireNotNull(uri.host)
        require(host.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")))
        val address=InetAddress.getByName(host)
        require(safeEndpoint(uri.toString(),address.hostAddress!!)!=null&&local(context,network,address))
        val port=if(uri.port==-1)80 else uri.port
        val path=uri.rawPath.orEmpty().ifEmpty{"/"}+(uri.rawQuery?.let{"?"+it}?:"")
        require(path.all{it.code in 33..126})
        val body=xml?.toByteArray(Charsets.UTF_8)?:ByteArray(0)
        network.socketFactory.createSocket().use{socket->
            socket.connect(InetSocketAddress(address,port),1800);socket.soTimeout=1800
            val deadline=SystemClock.elapsedRealtime()+3500
            val out=socket.getOutputStream()
            val headers=buildString{
                append(if(action==null&&!dialPost)"GET " else "POST ");append(path);append(" HTTP/1.1\r\nHost: $host:$port\r\nConnection: close\r\n")
                if(action!=null){append("Content-Type: text/xml; charset=utf-8\r\nSOAPAction: \"$action\"\r\nContent-Length: ");append(body.size);append("\r\n")}
                if(dialPost){append("Content-Type: text/plain; charset=utf-8\r\nContent-Length: ");append(body.size);append("\r\n")}
                append("\r\n")
            }
            out.write(headers.toByteArray(Charsets.US_ASCII));if(body.isNotEmpty())out.write(body);out.flush()
            val input=BufferedInputStream(socket.getInputStream())
            fun readByte():Int{check(SystemClock.elapsedRealtime()<deadline);return input.read()}
            fun line():String{
                val b=ByteArrayOutputStream()
                while(true){val c=readByte();check(c>=0&&b.size()<8192);if(c==10)break;if(c!=13)b.write(c)}
                return b.toString("US-ASCII")
            }
            val status=line()
            val code=Regex("HTTP/1\\.[01] ([0-9]{3})( .*)?").matchEntire(status)?.groupValues?.get(1)?.toIntOrNull()
                ?:throw java.io.IOException("Resposta HTTP inválida")
            if(code !in 200..299)throw NeroDlnaHttpException(code)
            val h=mutableMapOf<String,String>()
            for(i in 0..63){val l=line();if(l.isEmpty())break;check(i<63&&l.contains(':'));h[l.substringBefore(':').lowercase()]=l.substringAfter(':').trim()}
            if(dialPost)return LocalResponse(h,"")
            val output=ByteArrayOutputStream()
            fun readN(n:Int){require(n>=0&&n<=LIMIT-output.size());repeat(n){val c=readByte();check(c>=0);output.write(c)}}
            if(h["transfer-encoding"]?.equals("chunked",true)==true){
                while(true){val n=line().substringBefore(';').trim().toInt(16);if(n==0)break;readN(n);check(line().isEmpty())}
            }else if(h.containsKey("content-length"))readN(h.getValue("content-length").toInt())
            else while(true){val c=readByte();if(c<0)break;require(output.size()<LIMIT);output.write(c)}
            return LocalResponse(h,output.toString("UTF-8"))
        }
    }
}
