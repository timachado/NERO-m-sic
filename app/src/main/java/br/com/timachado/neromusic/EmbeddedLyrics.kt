package br.com.timachado.neromusic

import java.io.DataInputStream
import java.io.InputStream
import java.nio.charset.Charset

// Conservative ID3v2.3/v2.4 reader. Unsupported tag flags, compressed/encrypted
// frames and malformed data are skipped rather than guessed. Never reads audio.
private const val MAX_TAG_BYTES=16*1024*1024
private const val MAX_TEXT_BYTES=256*1024
private fun unsigned(b:Byte)=b.toInt() and 255
private fun sizeOf(bytes:ByteArray,offset:Int,syncsafe:Boolean):Int? {
    var n=0L
    for(i in offset until offset+4){
        val b=unsigned(bytes[i])
        if(syncsafe&&b>=128)return null
        n=(n shl (if(syncsafe)7 else 8))+b
    }
    return n.takeIf{it<=Int.MAX_VALUE}?.toInt()
}
private fun readFullyOrNull(stream:DataInputStream,count:Int):ByteArray? =
    try{ByteArray(count).also{stream.readFully(it)}}catch(_:java.io.EOFException){null}
private fun skipFully(stream:DataInputStream,count:Int):Boolean {
    var remaining=count
    while(remaining>0){
        val skipped=stream.skipBytes(remaining)
        if(skipped>0)remaining-=skipped else if(stream.read()<0)return false else remaining--
    }
    return true
}
private fun decodeUslt(data:ByteArray):String?{
    if(data.size<5)return null
    val encoding=unsigned(data[0])
    val step=if(encoding==1||encoding==2)2 else 1
    val charset=when(encoding){
        0->Charsets.ISO_8859_1
        1->Charsets.UTF_16
        2->Charsets.UTF_16BE
        3->Charsets.UTF_8
        else->return null
    }
    var end=4
    while(end+step<=data.size){
        if(data[end]==0.toByte()&&(step==1||data[end+1]==0.toByte()))break
        end+=step
    }
    val start=end+step
    if(start>=data.size||(step==2&&(data.size-start)%2!=0))return null
    val chosen:Charset=if(encoding==1&&start+1<data.size){
        val a=unsigned(data[start]);val b=unsigned(data[start+1])
        if((a==255&&b==254)||(a==254&&b==255))charset
        else if(data.size>=6&&unsigned(data[4])==255&&unsigned(data[5])==254)Charsets.UTF_16LE
        else if(data.size>=6&&unsigned(data[4])==254&&unsigned(data[5])==255)Charsets.UTF_16BE
        else return null
    }else charset
    return data.copyOfRange(start,data.size).toString(chosen).trim('\u0000','\uFEFF',' ','\n','\r')
        .takeIf{it.isNotBlank()}
}
internal fun readEmbeddedLyrics(input:InputStream):String? {
    val stream=DataInputStream(input)
    val header=readFullyOrNull(stream,10)?:return null
    if(header.copyOfRange(0,3).toString(Charsets.US_ASCII)!="ID3")return null
    val version=unsigned(header[3])
    if(version !in 3..4||unsigned(header[5])!=0)return null
    var remaining=sizeOf(header,6,true)?:return null
    if(remaining>MAX_TAG_BYTES)return null
    var frames=0
    while(remaining>=10&&frames++<4096){
        val frame=readFullyOrNull(stream,10)?:return null
        remaining-=10
        val id=frame.copyOfRange(0,4).toString(Charsets.US_ASCII)
        if(!Regex("[A-Z0-9]{4}").matches(id))return null
        val count=sizeOf(frame,4,version==4)?:return null
        if(count>remaining)return null
        if(id=="USLT"&&count in 5..MAX_TEXT_BYTES&&unsigned(frame[9])==0){
            val data=readFullyOrNull(stream,count)?:return null
            decodeUslt(data)?.let{return it}
        }else if(!skipFully(stream,count))return null
        remaining-=count
    }
    return null
}
