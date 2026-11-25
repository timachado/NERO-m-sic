package br.com.timachado.neromusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal fun playlistDuration(tracks:List<Track>):String{
    val minutes=tracks.sumOf{it.duration.coerceAtLeast(0)}/60000L
    return if(minutes>=60)"${minutes/60}h ${minutes%60}min" else if(minutes==0L&&tracks.isNotEmpty())"menos de 1 min" else "${minutes} min"
}

@Composable
internal fun PlaylistArtwork(tracks:List<Track>,modifier:Modifier=Modifier){
    val covers=tracks.distinctBy{if(it.albumId>0)"album:"+it.albumId else it.artist+"|"+it.album+"|"+it.id}.take(4)
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF12313A)),contentAlignment=Alignment.Center){
        if(covers.isEmpty()){
            Icon(Icons.Default.QueueMusic,null,tint=Color(0xFF39FF14),modifier=Modifier.size(30.dp))
        }else if(covers.size<4){
            LocalArtwork(covers.first(),Modifier.fillMaxSize())
        }else{
            Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(2.dp)){
                repeat(2){row->
                    Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)){
                        repeat(2){col->LocalArtwork(covers[row*2+col],Modifier.weight(1f).fillMaxHeight())}
                    }
                }
            }
        }
    }
}
