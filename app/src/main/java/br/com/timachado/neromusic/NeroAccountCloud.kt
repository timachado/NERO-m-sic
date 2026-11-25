package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun NeroAccountCloud(user:NeroSession,enabled:Boolean){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val pull by NeroAccountSync.status.collectAsState()
    val changes by NeroAccountSync.changes.collectAsState()
    var cached by remember(user.uid){mutableStateOf<NeroAccountLibraryState?>(null)}
    var plan by remember(user.uid){mutableStateOf("Não verificado")}
    var busy by remember(user.uid){mutableStateOf(false)}
    var notice by remember(user.uid){mutableStateOf<String?>(null)}
    var confirm by remember(user.uid){mutableStateOf(false)}
    LaunchedEffect(user.uid,changes){
        cached=withContext(Dispatchers.IO){runCatching{NeroAccountLibraryStore.read(context,user.uid)}.getOrNull()}
    }
    suspend fun refreshPlan(){
        try{
            val current=NeroSupabase.session(context,false);require(current.uid==user.uid)
            val row=JSONObject(NeroSupabase.rpc("nero_plan_read_v1",JSONObject(),current.access))
            plan=when(row.optString("status")){"active"->"Assinatura ativa";"none"->"Sem assinatura ativa";"expired"->"Assinatura expirada";else->"Não verificado"}
        }catch(e:CancellationException){throw e}catch(_:Exception){plan="Serviço de planos ainda indisponível"}
    }
    LaunchedEffect(user.uid){refreshPlan()}
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("NERO Cloud",fontSize=20.sp,fontWeight=FontWeight.Bold)
            Text("Favoritos online da conta ficam separados da biblioteca de visitante. Alterações locais são preservadas mesmo quando a conexão falha.",fontSize=12.sp,color=Color.LightGray)
            val state=pull?.takeIf{it.uid==user.uid}?.state
            val pending=cached?.let{it.pending!=it.baseline}==true
            Text(when(state){
                "loading"->"Sincronizando…"
                "conflict"->"Conflito de alterações. Dados locais preservados."
                "pending"->"Alterações pendentes. Tente sincronizar novamente."
                "ready"->if(pending)"Alterações aguardando envio" else "Última consulta sincronizada"
                else->"Nuvem indisponível ou ainda não configurada"
            },color=if(state=="ready"&&!pending)MaterialTheme.colorScheme.primary else Color.LightGray,fontSize=13.sp)
            cached?.let{Text("${it.pending.favorites.size} favoritos da conta",fontSize=13.sp)}
            Text("Playlists, álbuns, artistas, histórico e arquivos offline ainda não integram a biblioteca automática desta versão.",fontSize=12.sp,color=Color.LightGray)
            OutlinedButton(enabled=enabled&&!busy&&state!="loading",onClick={scope.launch{NeroAccountSync.pull(context,user.uid)}},modifier=Modifier.fillMaxWidth()){Text("Sincronizar agora")}
            TextButton(enabled=enabled&&!busy,onClick={confirm=true}){Text("Importar favoritos anteriores do aparelho")}
            if(state=="loading"||busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
        }
    }
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Plano e assinatura",fontSize=20.sp,fontWeight=FontWeight.Bold)
            Text(plan,color=MaterialTheme.colorScheme.primary)
            Text("As assinaturas serão gerenciadas pelo servidor. Nenhuma cobrança ou compra disponível nesta versão.",fontSize=12.sp,color=Color.LightGray)
            TextButton(enabled=enabled&&!busy,onClick={scope.launch{refreshPlan()}}){Text("Consultar situação do plano")}
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Importar favoritos anteriores?")},
        text={Text("As referências online da biblioteca de visitante serão copiadas para esta conta Google. Confirme somente se esses dados pertencem a você. Os favoritos anteriores não serão apagados.")},
        confirmButton={TextButton(onClick={confirm=false;busy=true;scope.launch{
            try{NeroAccountSync.importGuestFavorites(context,user.uid);notice="Favoritos copiados para a conta neste aparelho."}
            catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível importar os favoritos."}
            finally{busy=false}
        }}){Text("Importar")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancelar")}})
}
