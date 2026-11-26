package br.com.timachado.neromusic

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

internal enum class NeroPlayerTheme(val label:String,val primary:Long,val secondary:Long,val panel:Long,val background:Long){
    AURORA("Aurora Neon",0xFF39FF14,0xFF00E5FF,0xFF17262D,0xFF000000),
    OCEAN("Ocean Pulse",0xFF38CFFF,0xFF9AB6FF,0xFF14263D,0xFF080F20),
    VIOLET("Violet Wave",0xFFC9A5FF,0xFFFF91D4,0xFF2B193F,0xFF120B21),
    GOLD("Midnight Gold",0xFFFFD170,0xFFFFA66B,0xFF31271A,0xFF16100A)
}
internal fun effectivePlayerTheme(stored:String?,verified:Boolean):NeroPlayerTheme {
    val theme=NeroPlayerTheme.entries.firstOrNull{it.name==stored}?:NeroPlayerTheme.AURORA
    return if(verified)theme else NeroPlayerTheme.AURORA
}
private object PlayerAppearanceStore{
    private fun prefs(context:Context)=context.getSharedPreferences("nero_player_appearance",Context.MODE_PRIVATE)
    private fun key(uid:String)=NeroAccountLibraryStore.fileName(uid)
    fun read(context:Context,uid:String)=prefs(context).getString(key(uid),null)
    fun write(context:Context,uid:String,theme:NeroPlayerTheme){
        require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
        prefs(context).edit().putString(key(uid),theme.name).apply()
    }
}
private suspend fun verifiedPremium(context:Context,uid:String):Boolean{
    if(!NeroSupabase.identityReady.value||NeroSupabase.activeUid.value!=uid)return false
    return try{
        withTimeoutOrNull(12000L){
            val session=NeroSupabase.session(context,false)
            if(session.uid!=uid)return@withTimeoutOrNull false
            val row=JSONObject(NeroSupabase.rpc("nero_plan_read_v1",JSONObject(),session.access))
            row.optString("status")=="active"&&NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid
        }==true
    }catch(e:CancellationException){throw e}catch(_:Exception){false}
}

@Composable
internal fun rememberNeroPlayerTheme():NeroPlayerTheme{
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val stored=remember(uid,ready){if(ready&&uid!=null)PlayerAppearanceStore.read(context,requireNotNull(uid))else null}
    var verified by remember(uid,ready,stored){mutableStateOf(false)}
    LaunchedEffect(uid,ready,stored){
        val owner=uid
        if(!ready||owner==null||effectivePlayerTheme(stored,true)==NeroPlayerTheme.AURORA)return@LaunchedEffect
        while(true){
            verified=false
            verified=verifiedPremium(context,owner)
            delay(300000L)
        }
    }
    return effectivePlayerTheme(stored,verified)
}

@Composable
internal fun NeroPlayerAppearance(uid:String,enabled:Boolean){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var selected by remember(uid){mutableStateOf(effectivePlayerTheme(PlayerAppearanceStore.read(context,uid),true))}
    var busy by remember(uid){mutableStateOf(false)}
    var notice by remember(uid){mutableStateOf<String?>(null)}
    Text("Aparência do player",fontSize=18.sp)
    Text("Aurora Neon é gratuito. Prévia de cores dos temas Premium; a aplicação depende da assinatura verificada.",fontSize=12.sp,color=Color.LightGray)
    NeroPlayerTheme.entries.forEach{theme->
        OutlinedButton(enabled=enabled&&!busy,onClick={
            if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid){
                busy=true;notice=null
                scope.launch{
                    try{
                        if(theme==NeroPlayerTheme.AURORA||verifiedPremium(context,uid)){
                            PlayerAppearanceStore.write(context,uid,theme);selected=theme
                            notice="Preferência salva neste aparelho."
                        }else notice="Não foi possível confirmar uma assinatura ativa. Aurora Neon continua disponível."
                    }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível salvar a aparência."}
                    finally{busy=false}
                }
            }
        },modifier=Modifier.fillMaxWidth()){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Box(Modifier.size(18.dp).background(Color(theme.primary),RoundedCornerShape(5.dp)))
                Box(Modifier.size(18.dp).background(Color(theme.secondary),RoundedCornerShape(5.dp)))
                Column(Modifier.weight(1f)){
                    Text(theme.label)
                    Text(if(theme==NeroPlayerTheme.AURORA)"Gratuito" else "Premium",fontSize=11.sp)
                }
                if(theme==selected)Text("Preferido",fontSize=11.sp)
            }
        }
    }
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
}
