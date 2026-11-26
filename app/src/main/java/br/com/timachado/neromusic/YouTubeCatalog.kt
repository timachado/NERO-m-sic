package br.com.timachado.neromusic

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.text.Html
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val videoId=Regex("[A-Za-z0-9_-]{11}")
internal data class YouTubeHit(val id:String,val title:String,val channel:String){
    val thumbnail:String get()="https://i.ytimg.com/vi/$id/mqdefault.jpg"
}
internal object YouTubeCatalog{
    val configured:Boolean get()=BuildConfig.YOUTUBE_ENDPOINT.isNotBlank()
    suspend fun search(context:Context,term:String):List<YouTubeHit>{
        check(configured)
        val user=runCatching{FirebaseAuth.getInstance().currentUser}.getOrNull()
            ?:error("Entre na sua conta NERO Music para consultar o catálogo externo.")
        val token=user.getIdToken(false).await().token?:error("Entre na sua conta NERO Music para consultar o catálogo externo.")
        return withContext(Dispatchers.IO){
            val uri=Uri.parse(BuildConfig.YOUTUBE_ENDPOINT).buildUpon().appendQueryParameter("q",term.take(100)).build()
            val c=URL(uri.toString()).openConnection() as HttpURLConnection
            c.connectTimeout=12000;c.readTimeout=15000;c.instanceFollowRedirects=false
            c.setRequestProperty("Authorization","Bearer "+token)
            c.setRequestProperty("Accept","application/json")
            try{
                if(c.responseCode==429)error("Limite de consultas atingido. Tente mais tarde.")
                check(c.responseCode==200)
                val bytes=c.inputStream.use{input->
                    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=512*1024);out.write(buffer,0,n)}
                    out.toByteArray()
                }
                val rows=JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("items")
                (0 until minOf(rows.length(),20)).mapNotNull{i->
                    val row=rows.optJSONObject(i)?:return@mapNotNull null
                    val id=row.optString("id");if(!videoId.matches(id))return@mapNotNull null
                    val title=Html.fromHtml(row.optString("title"),Html.FROM_HTML_MODE_LEGACY).toString().take(200)
                    val channel=Html.fromHtml(row.optString("channel"),Html.FROM_HTML_MODE_LEGACY).toString().take(150)
                    if(title.isBlank())null else YouTubeHit(id,title,channel)
                }.distinctBy{it.id}
            }finally{c.disconnect()}
        }
    }
}
@Composable
internal fun YouTubePlayerScreen(hit:YouTubeHit,onBack:()->Unit){
    val context=LocalContext.current
    val lifecycle=(context as? LifecycleOwner)?.lifecycle
    val close by rememberUpdatedState(onBack)
    DisposableEffect(lifecycle){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)close()}
        lifecycle?.addObserver(observer)
        onDispose{lifecycle?.removeObserver(observer)}
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text("NERO Music · Vídeo musical",fontSize=20.sp)
        }
        AndroidView(factory={ctx->
            WebView(ctx).apply{
                settings.javaScriptEnabled=true
                settings.domStorageEnabled=true
                settings.allowFileAccess=false
                settings.allowContentAccess=false
                settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.mediaPlaybackRequiresUserGesture=true
                webViewClient=object:WebViewClient(){
                    override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean =
                        request.isForMainFrame
                }
                val id=hit.id
                require(videoId.matches(id))
                loadDataWithBaseURL("https://br.com.timachado.neromusic/",
                    "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='referrer' content='strict-origin-when-cross-origin'></head><body style='margin:0;background:black'><iframe style='border:0;width:100%;height:100vh;min-height:200px' src='https://www.youtube.com/embed/$id?playsinline=1' allow='encrypted-media; picture-in-picture' referrerpolicy='strict-origin-when-cross-origin'></iframe></body></html>",
                    "text/html","UTF-8",null)
            }
        },modifier=Modifier.fillMaxWidth().height(240.dp),onReset=null,onRelease={view->view.stopLoading();view.destroy()})
        Text(hit.title,fontSize=21.sp)
        Text(hit.channel)
        Text("Reproduza no player incorporado do YouTube. A disponibilidade depende do vídeo e da região.",fontSize=12.sp)
    }
}

@Composable
internal fun YouTubeWebSearchScreen(query:String,onBack:()->Unit){
    val context=LocalContext.current
    val lifecycle=(context as? LifecycleOwner)?.lifecycle
    val close by rememberUpdatedState(onBack)
    var failed by remember(query){mutableStateOf(false)}
    var reload by remember(query){mutableIntStateOf(0)}
    DisposableEffect(lifecycle){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)close()}
        lifecycle?.addObserver(observer)
        onDispose{lifecycle?.removeObserver(observer)}
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar à busca NERO")}
            Text("NERO Music · "+query,fontSize=18.sp,modifier=Modifier.weight(1f),maxLines=1)
        }
        Text("Página externa do YouTube integrada ao NERO Music. Use os controles da página.",fontSize=12.sp)
        if(failed){
            Text("A página não carregou. Verifique a conexão ou tente novamente.")
            TextButton(onClick={failed=false;reload++}){Text("Tentar novamente")}
        }
        key(query,reload){
            AndroidView(factory={ctx->
                WebView(ctx).apply{
                    settings.javaScriptEnabled=true
                    settings.domStorageEnabled=true
                    settings.allowFileAccess=false
                    settings.allowContentAccess=false
                    settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.mediaPlaybackRequiresUserGesture=true
                    settings.setSupportMultipleWindows(false)
                    webViewClient=object:WebViewClient(){
                        override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                            if(!request.isForMainFrame)return false
                            val u=request.url
                            return u.scheme!="https"||u.host !in setOf("m.youtube.com","www.youtube.com","music.youtube.com","youtube.com")
                        }
                        override fun onReceivedError(view:WebView,request:WebResourceRequest,error:android.webkit.WebResourceError){
                            if(request.isForMainFrame)failed=true
                        }
                    }
                    loadUrl(Uri.parse("https://m.youtube.com/results").buildUpon().appendQueryParameter("search_query",query+" música").build().toString())
                }
            },modifier=Modifier.weight(1f).fillMaxWidth(),onReset=null,onRelease={view->view.stopLoading();view.destroy()})
        }
    }
}
