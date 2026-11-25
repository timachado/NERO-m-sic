package br.com.timachado.neromusic

import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class RadioCandidate(val id:Long,val artist:String,val genre:String)
private fun normalized(s:String):String?=s.trim().lowercase(Locale.ROOT).takeIf{
    it.isNotBlank()&&it !in setOf("<unknown>","unknown","unknown artist","artista desconhecido","sem gênero","unknown genre")
}

// A finite, deterministic queue of related local recordings; no fabricated online suggestions.
internal fun radioOrder(candidates:List<RadioCandidate>,favoriteIds:Set<Long>,recentIds:List<Long>,seedId:Long?,limit:Int=40):List<Long>{
    if(limit<=0)return emptyList()
    val unique=candidates.distinctBy{it.id}
    val byId=unique.associateBy{it.id}
    val seed=seedId?.let{byId[it]}
    val artistWeights=mutableMapOf<String,Int>()
    val genreWeights=mutableMapOf<String,Int>()
    fun add(c:RadioCandidate,weight:Int){
        normalized(c.artist)?.let{artistWeights[it]=(artistWeights[it]?:0)+weight}
        normalized(c.genre)?.let{genreWeights[it]=(genreWeights[it]?:0)+weight}
    }
    if(seed!=null)add(seed,100)
    else{
        recentIds.distinct().take(30).forEachIndexed{i,id->byId[id]?.let{add(it,30-i)}}
        favoriteIds.forEach{id->byId[id]?.let{add(it,20)}}
    }
    val recent=recentIds.toSet()
    fun score(c:RadioCandidate):Int {
        val artist=artistWeights[normalized(c.artist)]?:0
        val genre=genreWeights[normalized(c.genre)]?:0
        if(artist==0&&genre==0)return 0
        return artist*2+genre + if(c.id !in recent) 10 else 0
    }
    val related=unique.filter{it.id!=seedId&&score(it)>0}.sortedWith(compareByDescending<RadioCandidate>{score(it)}.thenBy{it.id}).toMutableList()
    val result=mutableListOf<Long>()
    if(seed!=null)result.add(seed.id)
    var lastArtist=seed?.let{normalized(it.artist)}
    while(result.size<limit&&related.isNotEmpty()){
        val different=related.indexOfFirst{normalized(it.artist)!=null&&normalized(it.artist)!=lastArtist}
        val chosen=related.removeAt(if(different>=0)different else 0)
        result.add(chosen.id);lastArtist=normalized(chosen.artist)
    }
    return result
}
internal fun buildRadio(tracks:List<Track>,favorites:Set<String>,recent:List<Heard>,seed:Track?=null):List<Track>{
    val map=tracks.associateBy{it.id}
    return radioOrder(tracks.map{RadioCandidate(it.id,it.artist,it.genre)},favorites.mapNotNull{it.toLongOrNull()}.toSet(),
        recent.mapNotNull{it.id.toLongOrNull()},seed?.id).mapNotNull{map[it]}
}

@Composable
internal fun RadioShelf(tracks:List<Track>,favorites:Set<String>,canPlay:Boolean,onRadio:(List<Track>)->Unit){
    val context=LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("nero_personal",0)}
    var recent by remember{mutableStateOf(history(context))}
    DisposableEffect(prefs){
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key=="history"||key==null)recent=history(context)}
        prefs.registerOnSharedPreferenceChangeListener(listener)
        recent=history(context)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val queue=remember(tracks,favorites,recent){buildRadio(tracks,favorites,recent)}
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.Radio,null,tint=Color(0xFF00E5FF),modifier=Modifier.size(32.dp))
                Text("NERO Radio",fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(start=8.dp))
            }
            Text("Rádio personalizada · biblioteca local",fontSize=12.sp,color=Color.LightGray)
            Text(if(queue.isEmpty())"Ouça ou favorite músicas para criar uma seleção relacionada." else "${queue.size} faixas selecionadas por artistas e gêneros do seu histórico e favoritos.",fontSize=13.sp)
            Button(enabled=canPlay&&queue.isNotEmpty(),onClick={onRadio(queue)},modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Default.PlayArrow,null);Text("Iniciar rádio")
            }
            Text("Fila finita de arquivos do aparelho, sem transmissão de rádio online.",fontSize=11.sp,color=Color.LightGray)
        }
    }
}
