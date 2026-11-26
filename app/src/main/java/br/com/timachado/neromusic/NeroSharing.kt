package br.com.timachado.neromusic

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val MAX_SHARE_BYTES=1024*1024
private const val MAX_SHARE_SONGS=1000
private val shareVideoId=Regex("[A-Za-z0-9_-]{11}")
internal data class SharedSong(val title:String,val artist:String,val videoId:String?=null,val publicLink:String?=null)
internal data class SharedPlaylist(val name:String,val songs:List<SharedSong>)

internal fun sharedSong(ref:OnlineRef):SharedSong{
    val native=musicHitFromRef(ref)
    val open=ref.openAudio?.takeIf{allowed(it)&&ref.id=="commons:"+it.id}
    return SharedSong(ref.title,ref.artist,native?.id,open?.source)
}
internal fun shareLink(song:SharedSong):String?{
    song.videoId?.takeIf{shareVideoId.matches(it)}?.let{return "https://music.youtube.com/watch?v="+it}
    return song.publicLink?.let{value->
        val uri=Uri.parse(value)
        value.takeIf{uri.scheme=="https"&&uri.host in setOf("commons.wikimedia.org","open.spotify.com")}
    }
}
internal fun sharedPlaylistRefs(playlist:SharedPlaylist):List<OnlineRef> =
    playlist.songs.map{song->
        val id=song.videoId?.takeIf{shareVideoId.matches(it)}?.let{"youtube:"+it} ?: "shared:"+UUID.randomUUID().toString()
        OnlineRef(id,song.title,song.artist)
    }.distinctBy{it.id}

internal fun encodePlaylist(playlist:SharedPlaylist):ByteArray{
    require(playlist.name.isNotBlank()&&playlist.songs.size in 1..MAX_SHARE_SONGS)
    val bytes=JSONObject().put("format","nero-playlist-v1").put("name",playlist.name.take(100))
        .put("songs",JSONArray(playlist.songs.map{song->
            JSONObject().put("title",song.title.take(300)).put("artist",song.artist.take(300))
                .put("videoId",song.videoId?.takeIf{shareVideoId.matches(it)})
        })).toString().toByteArray(Charsets.UTF_8)
    require(bytes.size<=MAX_SHARE_BYTES);return bytes
}
internal suspend fun readSharedPlaylist(context:Context,uri:Uri):SharedPlaylist=withContext(Dispatchers.IO){
    val bytes=context.contentResolver.openInputStream(uri)?.use{input->
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=MAX_SHARE_BYTES);out.write(buffer,0,n)}
        out.toByteArray()
    }?:error("Arquivo indisponível")
    decodeSharedPlaylist(bytes.toString(Charsets.UTF_8))
}
internal fun decodeSharedPlaylist(payload:String):SharedPlaylist{
    require(payload.toByteArray(Charsets.UTF_8).size<=MAX_SHARE_BYTES)
    val json=JSONObject(payload)
    require(json.getString("format")=="nero-playlist-v1")
    val name=json.getString("name").trim();require(name.isNotEmpty()&&name.length<=100)
    val rows=json.getJSONArray("songs");require(rows.length() in 1..MAX_SHARE_SONGS)
    val songs=(0 until rows.length()).map{i->
        val row=rows.getJSONObject(i);val title=row.getString("title").trim();val artist=row.optString("artist").trim()
        require(title.isNotEmpty()&&title.length<=300&&artist.length<=300)
        val id=row.optString("videoId").takeIf{shareVideoId.matches(it)}
        SharedSong(title,artist,id)
    }
    return SharedPlaylist(name,songs)
}
private fun songLine(song:SharedSong)=listOf(song.title,song.artist).filter{it.isNotBlank()}.joinToString(" · ")
private fun trackMessage(song:SharedSong)=listOf("🎧 Ouvindo no NERO Music",songLine(song),shareLink(song)).filterNotNull().joinToString("\n")
private fun playlistMessage(playlist:SharedPlaylist):String=buildString{
    append("🎧 ").append(playlist.name).append(" | NERO Music\n").append(playlist.songs.size).append(" faixas\n")
    playlist.songs.take(50).forEachIndexed{i,song->
        append("\n").append(i+1).append(". ").append(songLine(song))
        shareLink(song)?.let{append("\n").append(it)}
    }
    if(playlist.songs.size>50)append("\n\nCompartilhe o arquivo NERO para enviar a playlist completa.")
}
internal fun sendText(context:Context,subject:String,text:String){
    val intent=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,subject).putExtra(Intent.EXTRA_TEXT,text)
    context.startActivity(Intent.createChooser(intent,"Compartilhar"))
}
private fun shareDir(context:Context):File=File(context.cacheDir,"nero-share").apply{
    mkdirs()
    listFiles()?.filter{it.isFile&&System.currentTimeMillis()-it.lastModified()>86400000L}?.forEach{it.delete()}
}
private fun sendFile(context:Context,file:File,mime:String,caption:String){
    val uri=FileProvider.getUriForFile(context,context.packageName+".sharing",file)
    val intent=Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri)
        .putExtra(Intent.EXTRA_TEXT,caption).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData=ClipData.newUri(context.contentResolver,file.name,uri)
    context.startActivity(Intent.createChooser(intent,"Compartilhar"))
}
private fun layoutText(canvas:Canvas,value:String,x:Float,y:Float,width:Int,size:Float,color:Int,bold:Boolean,maxLines:Int):Int{
    val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{
        textSize=size;this.color=color;typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
    }
    val layout=StaticLayout.Builder.obtain(value,0,value.length,paint,width)
        .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()
    canvas.save();canvas.translate(x,y);layout.draw(canvas);canvas.restore();return layout.height
}
private suspend fun createTrackCard(context:Context,song:SharedSong,cover:String?):File=withContext(Dispatchers.IO){
    // Load only the already selected cover, never the audio.
    val art=if(cover.isNullOrBlank())null else try{
        val result=context.imageLoader.execute(ImageRequest.Builder(context).data(cover).size(640).allowHardware(false).build())
        (result as? SuccessResult)?.drawable?.toBitmap()
    }catch(e:CancellationException){throw e}catch(_:Exception){null}
    val bitmap=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888)
    try{
        val c=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{shader=LinearGradient(0f,0f,1080f,1920f,intArrayOf(0xff09151e.toInt(),0xff0d2931.toInt(),0xff020606.toInt()),null,Shader.TileMode.CLAMP)}
        c.drawRect(0f,0f,1080f,1920f,paint);paint.shader=null
        layoutText(c,"NERO MUSIC",100f,180f,880,78f,0xff00e5ff.toInt(),true,1)
        layoutText(c,"O som da sua liberdade.",100f,290f,880,34f,0xffc4d0d8.toInt(),false,1)
        val box=RectF(200f,425f,880f,1105f)
        paint.color=0xff153841.toInt();c.drawRoundRect(box,48f,48f,paint)
        if(art!=null){
            val side=minOf(art.width,art.height);val left=(art.width-side)/2;val top=(art.height-side)/2
            c.save();val path=android.graphics.Path().apply{addRoundRect(box,48f,48f,android.graphics.Path.Direction.CW)}
            c.clipPath(path);c.drawBitmap(art,Rect(left,top,left+side,top+side),box,paint);c.restore()
        }else{
            paint.color=0xff00e5ff.toInt();c.drawCircle(540f,765f,214f,paint)
            paint.color=0xff10252c.toInt();c.drawCircle(540f,765f,200f,paint)
            paint.color=0xff39ff14.toInt();c.drawCircle(540f,765f,64f,paint)
        }
        val titleHeight=layoutText(c,song.title.take(300),90f,1190f,900,68f,android.graphics.Color.WHITE,true,3)
        layoutText(c,song.artist.take(300),90f,1210f+titleHeight,900,42f,0xffc4d0d8.toInt(),false,2)
        paint.color=0xff39ff14.toInt();c.drawRoundRect(150f,1635f,930f,1651f,8f,8f,paint)
        layoutText(c,"COMPARTILHADO NO NERO MUSIC",80f,1740f,920,32f,0xff39ff14.toInt(),true,1)
        val file=File(shareDir(context),"nero-status-"+UUID.randomUUID()+".png")
        file.outputStream().use{require(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
        file
    }finally{bitmap.recycle()}
}

@Composable
internal fun TrackShareDialog(title:String,artist:String,ref:OnlineRef?,cover:String?,onClose:()->Unit){
    val context=LocalContext.current
    val song=ref?.let(::sharedSong)?:SharedSong(title,artist)
    ProtectedShareDialog(SharedPlaylist(song.title,listOf(song)),single=true,onClose=onClose,cover=cover,
        send={link,card->
            val message="🎧 NERO Music · música compartilhada\n"+link+"\nAbra o link e entre com sua conta Google."
            if(card){val file=createTrackCard(context,song,cover);sendFile(context,file,"image/png",message)}
            else sendText(context,"NERO Music",message)
        })
}
@Composable
internal fun PlaylistShareDialog(playlist:SharedPlaylist,onClose:()->Unit){
    val context=LocalContext.current
    ProtectedShareDialog(playlist,single=false,onClose=onClose,send={link,_->
        sendText(context,"Playlist NERO Music","🎧 Playlist compartilhada no NERO Music\n"+link+"\nAbra o link e entre com sua conta Google.")
    })
}
