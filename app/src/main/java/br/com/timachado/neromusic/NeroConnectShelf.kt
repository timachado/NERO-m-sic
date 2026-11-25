package br.com.timachado.neromusic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NeroConnectShelf(){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var open by remember{mutableStateOf(false)}
    var local by remember{mutableStateOf("")}
    var peer by remember{mutableStateOf("")}
    var saved by remember{mutableStateOf<String?>(null)}
    var code by remember{mutableStateOf("")}
    var checked by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var notice by remember{mutableStateOf("")}
    var proofOpen by remember{mutableStateOf(false)}
    LaunchedEffect(open){
        if(open){
            busy=true
            try{
                val pair=withContext(Dispatchers.IO){val identity=NeroConnectIdentity(context.applicationContext);identity.offer() to identity.peerOffer()}
                local=pair.first;saved=pair.second
            }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){notice="Identidade segura indisponível neste aparelho."}
            finally{busy=false}
        }
    }
    OutlinedButton(onClick={open=true;notice="";peer="";code="";checked=false},modifier=Modifier.fillMaxWidth()){
        Icon(Icons.Default.Devices,null,tint=Color(0xFF00E5FF))
        Spacer(Modifier.width(8.dp));Text("NERO Connect · Pareamento experimental")
    }
    if(open)AlertDialog(onDismissRequest={if(!busy)open=false},title={Text("NERO Connect")},text={
        Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Prévia de pareamento local. Ainda não controla outro aparelho nem envia dados pela rede.",fontSize=13.sp)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(saved!=null){
                Text("Chave pública do outro dispositivo confirmada.")
                OutlinedButton(enabled=!busy,onClick={proofOpen=true}){Text("Verificar identidade do aparelho")}
                OutlinedButton(enabled=!busy,onClick={
                    busy=true;scope.launch{
                        try{withContext(Dispatchers.IO){NeroConnectIdentity(context.applicationContext).revoke()};saved=null;peer="";code="";checked=false;notice="Pareamento local removido."}
                        catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){notice="Não foi possível remover."}finally{busy=false}
                    }
                }){Text("Remover pareamento")}
            }
            if(local.isNotBlank()){
                Text("1. Copie e compartilhe sua chave pública com o outro aparelho.")
                SelectionContainer{Text(local,fontSize=11.sp)}
                TextButton(onClick={
                    val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Chave pública NERO",local))
                    notice="Chave pública copiada."
                }){Text("Copiar minha chave")}
            }
            if(saved==null&&local.isNotBlank()){
                Text("2. Cole a chave pública exibida no outro aparelho.")
                OutlinedTextField(peer,{peer=it.take(256);code="";checked=false},label={Text("Chave do outro aparelho")},enabled=!busy)
                OutlinedButton(enabled=!busy&&peer.isNotBlank(),onClick={
                    busy=true;code="";checked=false
                    val offer=peer
                    scope.launch{
                        try{code=withContext(Dispatchers.IO){NeroConnectIdentity(context.applicationContext).fingerprint(offer)};notice=""}
                        catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){notice="Chave inválida ou do próprio dispositivo."}finally{busy=false}
                    }
                }){Text("Gerar código de conferência")}
                if(code.isNotBlank()){
                    Text("3. Compare pessoalmente o código nos dois aparelhos.",fontSize=13.sp)
                    Text(code,color=Color(0xFF39FF14),fontSize=23.sp)
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Checkbox(checked=checked,onCheckedChange={checked=it},enabled=!busy)
                        Text("Os dois códigos são iguais.")
                    }
                    Button(enabled=checked&&!busy,onClick={
                        val offer=peer;val compared=code
                        busy=true;scope.launch{
                            try{withContext(Dispatchers.IO){NeroConnectIdentity(context.applicationContext).confirm(offer,compared)};saved=offer;notice="Chave confirmada neste dispositivo.";code="";checked=false}
                            catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){notice="Não foi possível confirmar a chave."}finally{busy=false}
                        }
                    }){Text("Confirmar dispositivo")}
                }
            }
            if(notice.isNotBlank())Text(notice,fontSize=12.sp)
        }
    },confirmButton={TextButton(enabled=!busy,onClick={open=false}){Text("Fechar")}})
    if(open&&proofOpen&&saved!=null)NeroConnectProofDialog(onClose={proofOpen=false})
}
