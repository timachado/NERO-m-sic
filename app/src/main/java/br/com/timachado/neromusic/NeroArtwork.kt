package br.com.timachado.neromusic

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

// The extractor provides alternative resolutions. Keep a bounded, in-memory index,
// independent of the playback resolver and without persisting signed addresses.
internal object NeroArtworkSources{
    private val covers=object:LinkedHashMap<String,String>(128,.75f,true){
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,String>?)=size>512
    }
    @Synchronized fun record(id:String,url:String?){if(!url.isNullOrBlank())covers[id]=url}
    @Synchronized fun preferred(value:String):String{
        val id=Regex("^https://i\\.ytimg\\.com/vi(?:_webp)?/([A-Za-z0-9_-]{11})/[^/?]+(?:\\?.*)?$").matchEntire(value)?.groupValues?.get(1)
        return id?.let{covers[it]}?:value
    }
}

@Composable
internal fun NeroArtwork(model:Any?,contentDescription:String?,modifier:Modifier=Modifier,contentScale:ContentScale=ContentScale.Fit){
    val context=LocalContext.current
    val original=model
    val preferred=remember(model){when(model){is String->NeroArtworkSources.preferred(model);is android.net.Uri->NeroArtworkSources.preferred(model.toString());else->model}}
    var failed by remember(model,preferred){mutableStateOf(false)}
    val data=if(failed)original else preferred
    val request=remember(context,data){ImageRequest.Builder(context).data(data).allowRgb565(false).crossfade(120).build()}
    AsyncImage(model=request,contentDescription=contentDescription,modifier=modifier,contentScale=contentScale,
        filterQuality=FilterQuality.High,onError={if(preferred!=original)failed=true})
}
