package br.com.timachado.neromusic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import java.util.Locale

private fun artistKey(value:String):String? = value.trim().lowercase(Locale.ROOT).takeIf {
    it.isNotBlank() && it !in setOf("<unknown>","artista desconhecido","unknown artist","unknown")
}

@Composable
internal fun PersonalRecommendations(tracks:List<Track>, favorites:Set<String>, canPlay:Boolean, onPlay:(MediaItem)->Unit) {
    val context=LocalContext.current
    val prefs=remember(context) { context.getSharedPreferences("nero_personal",Context.MODE_PRIVATE) }
    var recent by remember { mutableStateOf(history(context)) }
    DisposableEffect(prefs) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,key ->
            if(key=="history" || key==null) recent=history(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        recent=history(context)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val trackMap=remember(tracks) { tracks.associateBy { it.id } }
    val heard=remember(recent,trackMap) { recent.mapNotNull { h -> h.id.toLongOrNull()?.let { trackMap[it] } }.distinctBy { it.id }.take(12) }
    val suggestions=remember(tracks,recent,favorites) {
        val score=mutableMapOf<String,Int>()
        val recentIds=recent.map { it.id }.toSet()
        recent.take(40).forEachIndexed { index,h ->
            artistKey(h.artist)?.let { key -> score[key]=(score[key]?:0)+(40-index) }
        }
        tracks.filter { it.id.toString() in favorites }.forEach { t ->
            artistKey(t.artist)?.let { key -> score[key]=(score[key]?:0)+20 }
        }
        tracks.filter { t -> t.id.toString() !in recentIds && t.id.toString() !in favorites && (score[artistKey(t.artist)]?:0)>0 }
            .sortedWith(compareByDescending<Track> { score[artistKey(it.artist)]?:0 }.thenBy { it.title.lowercase(Locale.ROOT) }.thenBy { it.id })
            .take(12)
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(heard.isNotEmpty()) {
            Text("Ouça novamente",fontSize=20.sp,fontWeight=FontWeight.Bold)
            RecommendationRow(heard,canPlay,onPlay)
        }
        Text("Feito para você",fontSize=20.sp,fontWeight=FontWeight.Bold)
        if(suggestions.isNotEmpty()) {
            Text("Outras faixas locais dos artistas que você ouve ou favorita.",color=Color.LightGray,fontSize=12.sp)
            RecommendationRow(suggestions,canPlay,onPlay)
        } else Text("As sugestões aparecem quando houver outras faixas no aparelho de artistas do seu histórico ou favoritos.",color=Color.LightGray,fontSize=12.sp)
        Text("Personalização no dispositivo. Nenhum histórico enviado a serviços externos.",color=Color.LightGray,fontSize=11.sp)
    }
}
@Composable
private fun RecommendationRow(tracks:List<Track>, canPlay:Boolean,onPlay:(MediaItem)->Unit) {
    LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        items(tracks,key={it.id}) { track ->
            Column(Modifier.width(132.dp).background(Color(0xFF10171C),RoundedCornerShape(12.dp)).padding(10.dp),
                verticalArrangement=Arrangement.spacedBy(6.dp)) {
                LocalArtwork(track,Modifier.fillMaxWidth().aspectRatio(1f))
                Text(track.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium,fontSize=14.sp)
                Text(track.artist,maxLines=1,overflow=TextOverflow.Ellipsis,color=Color.LightGray,fontSize=12.sp)
                TextButton(enabled=canPlay,onClick={onPlay(track.mediaItem())},contentPadding=PaddingValues(0.dp)) {
                    Icon(Icons.Default.PlayArrow,null,tint=Color(0xFF39FF14)); Text("Ouvir",color=Color(0xFF39FF14))
                }
            }
        }
    }
}
