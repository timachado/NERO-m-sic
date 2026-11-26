package br.com.timachado.neromusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote

private data class GenreTile(val label:String,val sample:String,val color:Long)
private val styles=listOf(GenreTile("Sertanejo","Zé Neto e Cristiano",0xFF6D3D17),GenreTile("Pagode","Turma do Pagode",0xFF154E43),GenreTile("Gospel","Fernandinho",0xFF235577),GenreTile("Pop","Anitta",0xFF354D87),GenreTile("Samba","Zeca Pagodinho",0xFF426126),GenreTile("Rock","Queen",0xFF493C43),GenreTile("MPB","Marisa Monte",0xFF36534C),GenreTile("Eletrônica","Alok",0xFF195678),GenreTile("Forró","Nattan",0xFF81512A),GenreTile("Hip-hop","Emicida",0xFF4B4B66),GenreTile("Jazz","Miles Davis",0xFF3B4555),GenreTile("Clássica","Yo-Yo Ma",0xFF514839))

@Composable
internal fun PremiumGenres(onSearch:(String)->Unit){
    var artwork by remember{mutableStateOf<Map<String,String>>(emptyMap())}
    LaunchedEffect(Unit){
        if(NativeMusicCatalog.enabled){
            for(genre in styles){
                try{
                    val hits=NativeMusicCatalog.search(genre.sample)
                    val first=hits.firstOrNull{genreForQuery(genre.label)?.matchesArtist(it.artist)==true}
                    if(first!=null)artwork=artwork+(genre.label to first.thumbnail)
                }catch(e:CancellationException){throw e}catch(_:Exception){}
                delay(350)
            }
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Explore por gênero",fontSize=21.sp,fontWeight=FontWeight.Bold)
        for(pair in styles.chunked(2)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                pair.forEach{genre->
                    Card(onClick={onSearch(genre.label)},modifier=Modifier.weight(1f).height(102.dp),shape=RoundedCornerShape(14.dp)){
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(genre.color),Color(0xFF10171C))))){
                            val cover=artwork[genre.label]
                            if(cover!=null)AsyncImage(model=cover,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.7f))
                            else Icon(Icons.Default.MusicNote,null,tint=Color.White.copy(alpha=.25f),modifier=Modifier.align(Alignment.CenterEnd).padding(12.dp).size(46.dp))
                            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(genre.color),Color(genre.color).copy(alpha=.88f),Color.Black.copy(alpha=.16f)))))
                            Text(genre.label,Modifier.align(Alignment.TopStart).padding(12.dp),fontWeight=FontWeight.Bold,fontSize=17.sp,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text("Explorar",Modifier.align(Alignment.BottomStart).padding(12.dp),fontSize=11.sp,color=Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PremiumDiscovery(onSearch:(String)->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Card(onClick={onSearch("lançamentos musicais Brasil")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){
            Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF173F4B),Color(0xFF102B23),Color(0xFF10171C)))).padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("NERO MUSIC",color=Color(0xFF00E5FF),fontSize=12.sp,fontWeight=FontWeight.Bold)
                Text("Descubra seu próximo som",fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color.White)
                Text("Explore músicas e novidades no NERO Music",fontSize=13.sp,color=Color.LightGray)
            }
        }
        Text("Seleções para explorar",fontSize=20.sp,fontWeight=FontWeight.Bold)
        Text("Seleções por artistas de cada gênero · não é ranking de paradas",fontSize=11.sp,color=Color.LightGray)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            items(listOf("Sertanejo" to "Sertanejo","Pagode" to "Pagode","Pop Brasil" to "Pop Brasil","Gospel" to "Gospel","Forró" to "Forró")){(label,term)->
                Card(onClick={onSearch(term)},modifier=Modifier.width(142.dp).height(86.dp)){
                    Column(Modifier.fillMaxSize().background(Color(0xFF172D35)).padding(12.dp),verticalArrangement=Arrangement.SpaceBetween){
                        Text(label,fontWeight=FontWeight.Bold,fontSize=17.sp)
                        Text("Descobrir",color=Color(0xFF39FF14),fontSize=12.sp)
                    }
                }
            }
        }
    }
}
