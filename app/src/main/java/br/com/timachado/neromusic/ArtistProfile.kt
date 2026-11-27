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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ArtistProfile(val name:String,val avatar:String?,val description:String,val bioSource:String?=null)
private object ArtistProfiles{
    private data class Entry(val at:Long,val value:ArtistProfile?)
    private val lock=Mutex()
    private val cache=LinkedHashMap<String,Entry>(32,.75f,true)
    suspend fun get(artist:String):ArtistProfile?=lock.withLock{
        val key=rawArtistName(artist).lowercase(java.util.Locale.ROOT)
        val now=SystemClock.elapsedRealtime()
        cache[key]?.let{if(now-it.at in 0L..900000L)return@withLock it.value}
        val original=try{NativeMusicCatalog.artistProfile(artist)}catch(e:CancellationException){throw e}catch(_:Exception){null}
        val bio=try{portugueseArtistBio(artist)}catch(e:CancellationException){throw e}catch(_:Exception){null}
        val profile=ArtistProfile(rawArtistName(artist),original?.avatar?:bio?.avatar,bio?.text.orEmpty(),bio?.source)
        cache[key]=Entry(SystemClock.elapsedRealtime(),profile)
        if(cache.size>32)cache.remove(cache.keys.first())
        profile
    }
}
@Composable
internal fun rememberArtistProfile(artist:String):ArtistProfile?{
    val name=primaryArtistName(artist)
    var profile by remember(name){mutableStateOf<ArtistProfile?>(null)}
    LaunchedEffect(name){
        if(NativeMusicCatalog.enabled&&name.length>=3){
            try{profile=ArtistProfiles.get(name)}
            catch(e:CancellationException){throw e}catch(_:Exception){profile=null}
        }
    }
    return profile
}
@Composable
internal fun ArtistProfileCard(artist:String,onOpen:(()->Unit)?=null,expanded:Boolean=false,profile:ArtistProfile?=rememberArtistProfile(artist),fallbackArtwork:String?=null,onArtist:((String)->Unit)?=null){
    val names=remember(artist){artistCreditParts(artist).take(4)}
    val guests=names.drop(1)
    val guestProfiles=guests.map{guest->key(guest){rememberArtistProfile(guest)}}
    Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Column{
            Box(Modifier.fillMaxWidth().height(if(expanded)220.dp else 180.dp).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center){
                Icon(Icons.Default.Person,null,tint=MaterialTheme.colorScheme.secondary,modifier=Modifier.size(64.dp))
                val photo=profile?.avatar
                if(photo!=null)NeroArtwork(model=photo,contentDescription="Foto de "+primaryArtistName(artist),contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                else if(fallbackArtwork!=null)NeroArtwork(model=fallbackArtwork,contentDescription="Capa da música (foto de perfil indisponível)",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.35f),Color.Transparent,Color.Black.copy(alpha=.65f)))))
                Text("Sobre o artista",fontWeight=FontWeight.Bold,fontSize=17.sp,color=Color.White,modifier=Modifier.align(Alignment.TopStart).padding(14.dp))
            }
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(displayArtistName(artist),fontSize=23.sp,fontWeight=FontWeight.Bold,color=Color.White)
                if(profile?.avatar==null&&fallbackArtwork!=null)Text("Capa da música · foto de perfil indisponível",fontSize=11.sp,color=Color.LightGray)
                if(guests.isNotEmpty()){
                    Text("Artistas da colaboração",fontWeight=FontWeight.Medium)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        items(guests.size){i->
                            val guest=guests[i]
                            val guestProfile=guestProfiles[i]
                            Column(Modifier.width(104.dp),horizontalAlignment=Alignment.CenterHorizontally){
                                Box(Modifier.size(88.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center){
                                    Icon(Icons.Default.Person,null,modifier=Modifier.size(36.dp))
                                    guestProfile?.avatar?.let{NeroArtwork(model=it,contentDescription="Foto de "+guest,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())}
                                }
                                if(onArtist!=null)TextButton(onClick={onArtist(guest)}){Text(guest,maxLines=2,overflow=TextOverflow.Ellipsis)}
                                else Text(guest,maxLines=2,overflow=TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if(!profile?.description.isNullOrBlank())Text("Sobre "+primaryArtistName(artist),fontWeight=FontWeight.Medium)
                }
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
