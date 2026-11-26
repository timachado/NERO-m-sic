package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private fun replayTime(ms:Long):String{
    val minutes=ms/60000L
    return if(minutes>=60) "${minutes/60} h ${minutes%60} min" else if(minutes>0) "${minutes} min" else "${ms/1000L} s"
}
@Composable
internal fun NeroReplayShelf(onSearch:(String)->Unit){
    val context=LocalContext.current
    var open by remember{mutableStateOf(false)}
    var entries by remember{mutableStateOf(NeroReplay.entries(context))}
    var enabled by remember{mutableStateOf(NeroReplay.enabled(context))}
    var days by remember{mutableIntStateOf(7)}
    var confirm by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){
        while(true){
            entries=NeroReplay.entries(context);enabled=NeroReplay.enabled(context)
            delay(10000L)
        }
    }
    val rows=replayTracks(entries,NeroReplay.today(),days)
    val artists=replayArtists(rows)
    Card(onClick={open=true},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF102B23))){
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Default.BarChart,null,tint=Color(0xFF39FF14),modifier=Modifier.size(36.dp))
            Column(Modifier.weight(1f).padding(start=12.dp)){
                Text("NERO Replay",fontSize=20.sp,fontWeight=FontWeight.Bold)
                Text(if(rows.isEmpty())"Seu ritmo, suas estatísticas." else replayTime(rows.sumOf{it.millis})+" ouvidos · últimos "+days+" dias",fontSize=13.sp,color=Color.LightGray)
            }
        }
    }
    if(open)androidx.compose.ui.window.Dialog(onDismissRequest={open=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)){
        Surface(modifier=Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text("NERO Replay",fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    TextButton(onClick={open=false}){Text("Fechar")}
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Estatísticas locais",modifier=Modifier.weight(1f))
                Switch(checked=enabled,onCheckedChange={enabled=it;NeroReplay.setEnabled(context,it)})
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf(7,30,90).forEach{period->FilterChip(selected=days==period,onClick={days=period},label={Text("${period} dias")})}
            }
            Text(replayTime(rows.sumOf{it.millis}),fontSize=28.sp,fontWeight=FontWeight.Bold,color=Color(0xFF39FF14))
            Text("Tempo estimado em reprodução · ${rows.size} faixas diferentes")
            if(rows.isEmpty())Text("Ainda não há dados suficientes. Ouça músicas para construir suas estatísticas.")
            if(artists.isNotEmpty()){
                Text("Artistas mais ouvidos",fontWeight=FontWeight.Bold)
                artists.take(5).forEachIndexed{i,(artist,ms)->
                    Text("${i+1}. "+artist+" · "+replayTime(ms))
                }
            }
            if(rows.isNotEmpty()){
                Text("Músicas mais ouvidas",fontWeight=FontWeight.Bold)
                rows.take(5).forEachIndexed{i,row->
                    TextButton(onClick={open=false;onSearch(row.title+" "+row.artist)}){
                        Text("${i+1}. "+row.title+" · "+replayTime(row.millis))
                    }
                }
            }
            Text("Amostragem de 5 segundos, sem contar pausas. Sessões curtas podem não entrar. Dados no aparelho, a partir desta versão; retenção máxima de 90 dias e 3.000 registros.",fontSize=12.sp,color=Color.LightGray)
            TextButton(enabled=entries.isNotEmpty(),onClick={confirm=true}){Text("Limpar estatísticas")}
            HorizontalDivider()
            NeroReplayPremium(days)
                }
            }
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Limpar estatísticas do Replay?")},text={Text("O tempo ouvido e os rankings locais serão apagados. Favoritos, playlists e downloads permanecem.")},confirmButton={TextButton(onClick={NeroReplay.clear(context);entries=emptyList();confirm=false}){Text("Limpar")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancelar")}})
}
