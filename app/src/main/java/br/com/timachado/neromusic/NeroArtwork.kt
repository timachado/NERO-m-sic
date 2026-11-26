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
internal fun NeroArtwork(model:Any?,contentDescription:String?,modifier:Modifier=Modifier,contentScale:ContentScale=ContentScale.Fit,large:Boolean=false){
    val context=LocalContext.current
    val original=model
    val preferred=remember(model){when(model){is String->NeroArtworkSources.preferred(model);is android.net.Uri->NeroArtworkSources.preferred(model.toString());else->model}}
    val candidates=remember(model,preferred,large){
        val url=original?.toString().orEmpty()
        val id=Regex("^https://i\\.ytimg\\.com/vi(?:_webp)?/([A-Za-z0-9_-]{11})/[^/?]+(?:\\?.*)?$").matchEntire(url)?.groupValues?.get(1)
        if(large&&id!=null)listOf("https://i.ytimg.com/vi/$id/maxresdefault.jpg","https://i.ytimg.com/vi/$id/sddefault.jpg",preferred,"https://i.ytimg.com/vi/$id/hqdefault.jpg",original).distinct()
        else listOf(preferred,original).distinct()
    }
    var index by remember(candidates){mutableIntStateOf(0)}
    val data=candidates[index]
    val request=remember(context,data,large){ImageRequest.Builder(context).data(data).allowRgb565(false).crossfade(if(large)120 else 0).build()}
    AsyncImage(model=request,contentDescription=contentDescription,modifier=modifier,contentScale=contentScale,
        filterQuality=FilterQuality.High,onError={if(index<candidates.lastIndex)index++},
        onSuccess={state->if(large&&index<candidates.lastIndex&&state.result.drawable.intrinsicWidth in 1..160)index++})
}
