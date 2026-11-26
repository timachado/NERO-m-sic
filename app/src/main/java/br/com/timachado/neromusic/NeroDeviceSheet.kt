package br.com.timachado.neromusic

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class NeroOutput(val id:Int,val name:String,val type:Int)
private data class NeroOutputs(val expected:List<NeroOutput> = emptyList(),val connected:List<NeroOutput> = emptyList())
private fun AudioDeviceInfo.output():NeroOutput {
    val name=when(type){
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,AudioDeviceInfo.TYPE_BUILTIN_EARPIECE->"Este telefone"
        else->productName.toString().ifBlank{"Dispositivo de áudio"}
    }
    return NeroOutput(id,name,type)
}
// Exclude virtual routes used for screen capture and internal audio mixing.
private fun AudioDeviceInfo.isPhysicalOutput():Boolean =
    type in setOf(
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_HDMI,AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_LINE_ANALOG,AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_HEARING_AID,AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,AudioDeviceInfo.TYPE_HDMI_EARC
    )
private fun readOutputs(context:Context):NeroOutputs=runCatching{
    val manager=context.getSystemService(AudioManager::class.java)
    val connected=manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter{it.isPhysicalOutput()}.map{it.output()}
    val expected=if(Build.VERSION.SDK_INT>=33)manager.getAudioDevicesForAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    ).filter{it.isPhysicalOutput()}.map{it.output()}else emptyList()
    NeroOutputs(expected.distinctBy{it.id},connected.distinctBy{it.id})
}.getOrDefault(NeroOutputs())
private fun openOutputSettings(context:Context):Boolean{
    if(Build.VERSION.SDK_INT>=34){
        if(runCatching{MediaRouter2.getInstance(context).showSystemOutputSwitcher()}.getOrDefault(false))return true
    }
    return runCatching{context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS));true}.getOrDefault(false)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NeroDeviceSheet(title:String,artist:String,playing:Boolean,canCast:Boolean,onCast:()->Unit,onClose:()->Unit){
    val context=LocalContext.current
    val green=MaterialTheme.colorScheme.primary
    var outputs by remember{mutableStateOf(readOutputs(context))}
    val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    val scope=rememberCoroutineScope()
    var opening by remember{mutableStateOf(false)}
    fun handoff(action:()->Boolean){
        if(opening)return
        opening=true
        scope.launch{
            sheetState.hide()
            if(!action())android.widget.Toast.makeText(context,"Não foi possível abrir a seleção de dispositivos.",android.widget.Toast.LENGTH_SHORT).show()
            onClose()
        }
    }
    LaunchedEffect(Unit){while(true){outputs=readOutputs(context);delay(2000L)}}
    ModalBottomSheet(onDismissRequest=onClose,containerColor=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground,sheetState=sheetState,dragHandle={Box(Modifier.fillMaxWidth().height(32.dp).neroSwipeDown(onClose),contentAlignment=Alignment.Center){BottomSheetDefaults.DragHandle(color=MaterialTheme.colorScheme.primary)}}){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            Row(Modifier.fillMaxWidth().neroSwipeDown(onClose),verticalAlignment=Alignment.CenterVertically){Text("Dispositivos",fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));IconButton(onClick=onClose){Icon(Icons.Default.KeyboardArrowDown,"Fechar dispositivos",tint=green)}}
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text(outputs.expected.joinToString(" + "){it.name}.ifBlank{"Saída do Android"},fontSize=21.sp,fontWeight=FontWeight.Bold,color=green)
                        Text(if(outputs.expected.isEmpty())"Saída gerenciada pelo sistema" else "Saída indicada pelo Android",fontSize=12.sp,color=Color.LightGray)
                        if(title.isNotBlank())Text((if(playing)"Tocando: " else "Faixa: ")+title+" · "+displayArtistName(artist),color=green,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.Speaker,null,tint=green,modifier=Modifier.size(36.dp))
                }
            }
            Text("Saídas conectadas ao telefone",fontWeight=FontWeight.Medium)
            val other=outputs.connected.filter{it.type!=AudioDeviceInfo.TYPE_BUILTIN_SPEAKER}
            if(other.isEmpty())Text("Nenhum acessório de áudio detectado.",color=Color.LightGray,fontSize=13.sp)
            other.forEach{device->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Default.Headphones,null,tint=MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp))
                    Text(device.name,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(outputs.expected.any{it.id==device.id})Icon(Icons.Default.Check,null,tint=green)
                }
            }
            OutlinedButton(enabled=!opening,onClick={handoff{openOutputSettings(context)}},modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Default.Devices,null);Spacer(Modifier.width(8.dp));Text(if(Build.VERSION.SDK_INT>=34)"Escolher saída de áudio" else "Dispositivos Bluetooth")
            }
            OutlinedButton(enabled=!opening,onClick={handoff{runCatching{context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS));true}.getOrDefault(false)}},modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Default.Bluetooth,null);Spacer(Modifier.width(8.dp));Text("Conectar dispositivo Bluetooth")
            }
            if(BuildConfig.FULL_FEATURES){
                HorizontalDivider()
                Text("TV e Chromecast",fontWeight=FontWeight.Medium)
                OutlinedButton(enabled=canCast&&!opening,onClick={handoff{onCast();true}},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Cast,null);Spacer(Modifier.width(8.dp));Text("Procurar TVs compatíveis")}
                Text("Use a mesma rede Wi-Fi. A transmissão atual aceita faixas compatíveis de áudio livre.",fontSize=12.sp,color=Color.LightGray)
            }

        }
    }
}
