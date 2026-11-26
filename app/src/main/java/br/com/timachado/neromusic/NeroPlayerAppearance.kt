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

internal enum class NeroPlayerTheme(val label:String,val primary:Long,val secondary:Long,val panel:Long,val background:Long,val description:String){
    AURORA("Aurora Neon",0xFF39FF14,0xFF00E5FF,0xFF17262D,0xFF000000,"Energia neon e identidade original NERO."),
    OCEAN("Ocean Pulse",0xFF38CFFF,0xFF9AB6FF,0xFF14263D,0xFF080F20,"Azul oceânico, frescor e tranquilidade."),
    VIOLET("Violet Wave",0xFFC9A5FF,0xFFFF91D4,0xFF2B193F,0xFF120B21,"Violeta envolvente e personalidade moderna."),
    GOLD("Midnight Gold",0xFFFFD170,0xFFFFA66B,0xFF31271A,0xFF16100A,"Dourado noturno, brilho e elegância."),
    CAFE("Midnight Café",0xFFE8BB87,0xFFE9A3B6,0xFF3B2028,0xFF170E0D,"Espresso, vinho e rosé: romance à meia-noite."),
    RUBY("Ruby Night",0xFFFF6983,0xFFFFB37A,0xFF351922,0xFF17080F,"Vermelho intenso e brilho de palco."),
    ICE("Glacier",0xFFB9F5FF,0xFF8AC1E6,0xFF172B35,0xFF090F16,"Gelo, azul e minimalismo.")
}
internal fun effectivePlayerTheme(stored:String?,verified:Boolean):NeroPlayerTheme {
    val theme=NeroPlayerTheme.entries.firstOrNull{it.name==stored}?:NeroPlayerTheme.AURORA
    return if(verified)theme else NeroPlayerTheme.AURORA
}
private object PlayerAppearanceStore{
    fun prefs(context:Context)=context.getSharedPreferences("nero_player_appearance",Context.MODE_PRIVATE)
    private fun key(uid:String)=NeroAccountLibraryStore.fileName(uid)
    fun read(context:Context,uid:String)=prefs(context).getString(key(uid),null)
    fun write(context:Context,uid:String,theme:NeroPlayerTheme){
        require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
        prefs(context).edit().putString(key(uid),theme.name).apply()
    }
}
internal suspend fun verifiedPremium(context:Context,uid:String):Boolean{
    if(!NeroSupabase.identityReady.value||NeroSupabase.activeUid.value!=uid)return false
    return try{
        readNeroSubscription(context,uid)?.phase?.premium==true
    }catch(e:CancellationException){throw e}catch(_:Exception){false}
}

@Composable
internal fun rememberNeroPlayerTheme():NeroPlayerTheme{
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    val prefs=remember(context){PlayerAppearanceStore.prefs(context)}
    var revision by remember{mutableIntStateOf(0)}
    DisposableEffect(prefs){
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++}
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val stored=remember(uid,ready,revision){if(ready&&uid!=null)PlayerAppearanceStore.read(context,requireNotNull(uid))else null}
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
    Text("NERO Studio",fontSize=20.sp,color=Color(0xFF39FF14))
    Text("Personalize a aparência do player. Aurora Neon é gratuito; os temas exclusivos exigem Premium verificado.",fontSize=12.sp,color=Color.LightGray)
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
                    Text(theme.description,fontSize=12.sp,color=Color.LightGray)
                }
                if(theme==selected)Text("Preferido",fontSize=11.sp)
            }
        }
    }
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
}
