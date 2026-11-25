package br.com.timachado.neromusic
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ArtistProfile(val name:String,val avatar:String?,val description:String,val bioSource:String?=null)
private object ArtistProfiles{
    private data class Entry(val at:Long,val value:ArtistProfile?)
    private val lock=Mutex()
    private val cache=LinkedHashMap<String,Entry>(32,.75f,true)
    suspend fun get(artist:String):ArtistProfile?=lock.withLock{
        val key=nativeArtistKey(artist)
        val now=SystemClock.elapsedRealtime()
        cache[key]?.let{if(now-it.at in 0L..900000L)return@withLock it.value}
        val original=try{NativeMusicCatalog.artistProfile(artist)}catch(e:CancellationException){throw e}catch(_:Exception){null}
        val bio=try{portugueseArtistBio(artist)}catch(e:CancellationException){throw e}catch(_:Exception){null}
        val profile=ArtistProfile(displayArtistName(artist),original?.avatar,bio?.text.orEmpty(),bio?.source)
        cache[key]=Entry(SystemClock.elapsedRealtime(),profile)
        if(cache.size>32)cache.remove(cache.keys.first())
        profile
    }
}
@Composable
internal fun rememberArtistProfile(artist:String):ArtistProfile?{
    var profile by remember(artist){mutableStateOf<ArtistProfile?>(null)}
    LaunchedEffect(artist){
        if(NativeMusicCatalog.enabled&&artist.trim().length>=3){
            try{profile=ArtistProfiles.get(artist)}
            catch(e:CancellationException){throw e}catch(_:Exception){profile=null}
        }
    }
    return profile
}
@Composable
internal fun ArtistProfileCard(artist:String,onOpen:(()->Unit)?=null,expanded:Boolean=false,profile:ArtistProfile?=rememberArtistProfile(artist)){
    Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Column{
            Box(Modifier.fillMaxWidth().height(if(expanded)220.dp else 180.dp).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center){
                Icon(Icons.Default.Person,null,tint=Color(0xFF00E5FF),modifier=Modifier.size(64.dp))
                profile?.avatar?.let{AsyncImage(model=it,contentDescription="Imagem do perfil de "+artist,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize())}
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.35f),Color.Transparent,Color.Black.copy(alpha=.65f)))))
                Text("Sobre o artista",fontWeight=FontWeight.Bold,fontSize=17.sp,color=Color.White,modifier=Modifier.align(Alignment.TopStart).padding(14.dp))
            }
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(displayArtistName(profile?.name?:artist),fontSize=23.sp,fontWeight=FontWeight.Bold,color=Color.White)
                if(!profile?.description.isNullOrBlank()){
                    Text(profile!!.description,maxLines=if(expanded)10 else 4,overflow=TextOverflow.Ellipsis,color=Color.LightGray)
                    val context=androidx.compose.ui.platform.LocalContext.current
                    profile?.bioSource?.let{source->
                        TextButton(onClick={openBrowser(context,android.net.Uri.parse(source))},contentPadding=PaddingValues(0.dp)){Text("Fonte: Wikipédia · CC BY-SA",fontSize=11.sp)}
                    }
                }else Text("Biografia em português ainda indisponível.",color=Color.LightGray,fontSize=13.sp)
                if(onOpen!=null)Button(onClick=onOpen,modifier=Modifier.fillMaxWidth()){Text("Conheça o artista");Spacer(Modifier.width(6.dp));Icon(Icons.Default.ChevronRight,null)}
            }
        }
    }
}
