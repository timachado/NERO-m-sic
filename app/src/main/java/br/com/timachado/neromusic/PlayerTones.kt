package br.com.timachado.neromusic
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

private val fallback=Color(0xFF233743)
internal fun coverTone(bitmap:Bitmap):Color{
    val bins=IntArray(36)
    val red=IntArray(36);val green=IntArray(36);val blue=IntArray(36)
    val hsv=FloatArray(3)
    val step=maxOf(1,minOf(bitmap.width,bitmap.height)/48)
    for(y in 0 until bitmap.height step step)for(x in 0 until bitmap.width step step){
        val pixel=bitmap.getPixel(x,y)
        if(android.graphics.Color.alpha(pixel)<160)continue
        android.graphics.Color.colorToHSV(pixel,hsv)
        if(hsv[1]<.22f||hsv[2]<.15f)continue
        val i=(hsv[0]/10).toInt().coerceIn(0,35)
        bins[i]++;red[i]+=android.graphics.Color.red(pixel);green[i]+=android.graphics.Color.green(pixel);blue[i]+=android.graphics.Color.blue(pixel)
    }
    val best=bins.indices.maxByOrNull{bins[it]}?:return fallback
    if(bins[best]==0)return fallback
    // Limit brightness for white text; keep original hue and fixed NERO accent colors.
    val count=bins[best]
    return Color((red[best]/count*.36f).toInt().coerceIn(12,92),(green[best]/count*.36f).toInt().coerceIn(12,92),(blue[best]/count*.36f).toInt().coerceIn(12,92))
}
@Composable
internal fun rememberPlayerTones(track:Track?,remoteCover:String?):Color{
    val context=LocalContext.current
    var tone by remember(track?.uri,remoteCover){mutableStateOf(fallback)}
    LaunchedEffect(track?.uri,remoteCover){
        try{
            tone=withContext(Dispatchers.IO){
                val bitmap=if(track!=null)artwork(context,track,64)
                else if(!remoteCover.isNullOrBlank()){
                    val result=context.imageLoader.execute(ImageRequest.Builder(context).data(remoteCover).size(64).allowHardware(false).build())
                    (result as? SuccessResult)?.drawable?.toBitmap(width=64,height=64)
                }else null
                bitmap?.let(::coverTone)?:fallback
            }
        }catch(e:CancellationException){throw e}catch(_:Exception){tone=fallback}
    }
    val animated by animateColorAsState(tone,tween(500),label="coverTone")
    return animated
}
