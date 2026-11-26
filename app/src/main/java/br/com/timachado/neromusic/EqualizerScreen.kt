package br.com.timachado.neromusic

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.media.audiofx.AudioEffect
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private fun settings(context:Context) = context.getSharedPreferences("nero_eq",Context.MODE_PRIVATE)
private data class EqStatus(val frequencies:List<Int> = emptyList(),val low:Int = 0,val high:Int = 0,val available:Boolean = false,val active:Boolean = false,val reason:String? = null)
internal fun neroEqualizerCanAttach(sessionId:Int):Boolean=BuildConfig.FULL_FEATURES&&sessionId>0
private object EqRuntime { var state by mutableStateOf(EqStatus()) }

// The effect belongs to the player's audio session, not the device-wide output.
internal class SessionEqualizer(context:Context) {
    private val prefs=settings(context)
    private var effect:Equalizer?=null
    private var sessionId=0
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,_ -> applySettings() }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }

    fun attach(id:Int) {
        if(id==sessionId && effect!=null) return
        releaseEffect()
        sessionId=id
        if(!neroEqualizerCanAttach(id)) return
        try {
            effect=Equalizer(0,id)
            effect?.setControlStatusListener { _,_ -> applySettings() }
            applySettings()
        } catch(_:Exception) { releaseEffect();EqRuntime.state=EqStatus(reason="Não foi possível inicializar o equalizador nesta saída de áudio. Tente reproduzir outra faixa ou trocar a saída.") }
    }

    private fun applySettings() {
        val e=effect ?: return
        try {
            val count=e.numberOfBands.toInt()
            val range=e.bandLevelRange
            val low=range[0].toInt().coerceAtLeast(-600)
            val high=range[1].toInt().coerceAtMost(600)
            if(count<=0 || low>=high) { EqRuntime.state=EqStatus(reason="A saída de áudio não disponibilizou bandas ajustáveis.");return }
            val frequencies=(0 until count).map { e.getCenterFreq(it.toShort())/1000 }
            val control=e.hasControl()
            var active=false
            if(control) {
                if(prefs.getBoolean("enabled",false)) {
                    frequencies.forEachIndexed { index,freq ->
                        val gain=prefs.getInt("gain_"+freq,0).coerceIn(low,high)
                        e.setBandLevel(index.toShort(),gain.toShort())
                    }
                    active=e.setEnabled(true)==AudioEffect.SUCCESS && e.enabled
                } else e.setEnabled(false)
            }
            EqRuntime.state=EqStatus(frequencies,low,high,control,active,if(control)null else "Outro efeito de áudio está controlando esta sessão. Feche o equalizador externo e tente novamente.")
        } catch(_:Exception) { releaseEffect();EqRuntime.state=EqStatus(reason="Não foi possível aplicar o efeito nesta sessão de áudio.") }
    }

    private fun releaseEffect() {
        val old=effect
        effect=null
        runCatching { old?.setControlStatusListener(null) }
        runCatching { if(old?.hasControl()==true)old.setEnabled(false) }
        runCatching { old?.release() }
        EqRuntime.state=EqStatus()
    }
    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        releaseEffect()
        sessionId=0
    }
}
private fun freqLabel(hz:Int)=if(hz>=1000) String.format(java.util.Locale.getDefault(),"%.1f kHz",hz/1000f) else "$hz Hz"
@Composable
internal fun EqualizerScreen(onBack:()->Unit,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val prefs=remember { settings(context) }
    val status=EqRuntime.state
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,_ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val enabled=remember(revision){prefs.getBoolean("enabled",false)}
    fun preset(name:String) {
        val edit=prefs.edit()
        status.frequencies.forEach { hz ->
            val level=when(name) {
                "Graves" -> if(hz<=250) 400 else if(hz>=4000) -100 else 0
                "Voz" -> if(hz in 500..4000) 300 else -100
                "Brilho" -> if(hz>=4000) 300 else if(hz<=250) -100 else 0
                "Rock" -> if(hz<=250||hz>=4000) 250 else -100
                "Eletrônica" -> if(hz<=250) 350 else if(hz>=4000) 150 else 0
                else -> 0
            }
            edit.putInt("gain_"+hz,level.coerceIn(status.low,status.high))
        }
        edit.apply()
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text("Equalizador",fontSize=23.sp,fontWeight=FontWeight.Bold)
        }
        Text("Ajustes de som",color=Color(0xFF00E5FF),fontSize=12.sp)
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Equalização",modifier=Modifier.weight(1f),fontSize=18.sp)
            Switch(checked=enabled,enabled=status.available,onCheckedChange={prefs.edit().putBoolean("enabled",it).apply()})
        }
        Text(when {
            !status.available -> status.reason ?: "Inicie uma faixa no NERO para conectar o equalizador à sessão de áudio."
            status.active -> "Efeito ativo na sessão de áudio do NERO."
            enabled -> "Efeito solicitado, mas ainda não ativo."
            else -> "Equalização desativada."
        },fontSize=13.sp,color=Color.LightGray)
        if(status.available) {
            Text("Predefinições",fontWeight=FontWeight.Bold)
            listOf(listOf("Plano","Graves"),listOf("Voz","Brilho"),listOf("Rock","Eletrônica")).forEach { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.forEach { name ->
                        OutlinedButton(onClick={preset(name)},enabled=enabled,modifier=Modifier.weight(1f)){Text(name)}
                    }
                }
            }
            status.frequencies.forEach { hz ->
                val value=remember(hz,revision,status){prefs.getInt("gain_"+hz,0).coerceIn(status.low,status.high)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(freqLabel(hz))
                    Text(String.format(java.util.Locale.getDefault(),"%+.1f dB",value/100f),color=Color(0xFF39FF14))
                }
                Slider(value=value.toFloat(),onValueChange={prefs.edit().putInt("gain_"+hz,it.roundToInt()).apply()},
                    valueRange=status.low.toFloat()..status.high.toFloat(),enabled=enabled)
            }
            OutlinedButton(onClick={preset("Plano")},modifier=Modifier.fillMaxWidth()){Text("Restaurar bandas")}
        }
        Text("As bandas e a disponibilidade dependem do aparelho. O efeito atua somente no áudio reproduzido pelo NERO e não altera arquivos. Use ganhos moderados para evitar distorção.",fontSize=12.sp,color=Color.LightGray)
    }
}
