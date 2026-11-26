package br.com.timachado.neromusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun NeroHeaderProfile(onClick:()->Unit){
    val profile by NeroSupabase.profileAvatar.collectAsState()
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val entitled by NeroPlaybackAccess.premium.collectAsState()
    val visibleProfile=profile?.takeIf{ready&&uid!=null&&it.uid==uid}
    var premium by remember(uid,ready,entitled){mutableStateOf(ready&&entitled&&NeroPlaybackAccess.allowed())}
    LaunchedEffect(uid,ready,entitled){
        while(ready&&uid!=null&&entitled){
            premium=NeroPlaybackAccess.allowed()
            delay(2000)
        }
    }
    val gold=Color(0xFFFFD170)
    val cyan=Color(0xFF00E5FF)
    val neon=Color(0xFF39FF14)
    val ring=if(premium)listOf(gold,Color(0xFFFFA65C),gold)else listOf(cyan,neon,cyan)
    IconButton(onClick=onClick,modifier=Modifier.size(48.dp).semantics{contentDescription=if(premium)"Minha conta NERO Premium" else "Minha conta"}){
        Box(Modifier.size(42.dp),contentAlignment=Alignment.Center){
            Box(Modifier.fillMaxSize().background(Brush.sweepGradient(ring),CircleShape).padding(2.dp)
                .background(Color(0xFF10171C),CircleShape).padding(2.dp).clip(CircleShape),contentAlignment=Alignment.Center){
                Icon(Icons.Default.AccountCircle,null,tint=if(premium)gold else cyan,modifier=Modifier.fillMaxSize())
                visibleProfile?.url?.let{url->
                    key(uid,url){
                        NeroArtwork(model=url,contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    }
                }
            }
            if(premium){
                Box(Modifier.align(Alignment.BottomEnd).size(16.dp).background(gold,CircleShape)
                    .border(1.dp,Color(0xFF10171C),CircleShape),contentAlignment=Alignment.Center){
                    Icon(Icons.Default.WorkspacePremium,null,tint=Color.Black,modifier=Modifier.size(11.dp))
                }
            }
        }
    }
}
