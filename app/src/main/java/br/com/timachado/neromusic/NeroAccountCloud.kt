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

@Composable
internal fun NeroAccountCloud(user:NeroSession,enabled:Boolean,onPlans:()->Unit={}){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val pull by NeroAccountSync.status.collectAsState()
    val changes by NeroAccountSync.changes.collectAsState()
    var cached by remember(user.uid){mutableStateOf<NeroAccountLibraryState?>(null)}
    var plan by remember(user.uid){mutableStateOf<NeroSubscription?>(null)}
    var planError by remember(user.uid){mutableStateOf(false)}
    var planBusy by remember(user.uid){mutableStateOf(false)}
    var busy by remember(user.uid){mutableStateOf(false)}
    var notice by remember(user.uid){mutableStateOf<String?>(null)}
    var confirm by remember(user.uid){mutableStateOf<String?>(null)}
    var historyEnabled by remember(user.uid){mutableStateOf(NeroAccountSync.historyEnabled(context,user.uid))}
    var localRecent by remember(user.uid){mutableStateOf(NeroLocalRecent.enabled(context,user.uid))}
    LaunchedEffect(user.uid,changes){
        cached=withContext(Dispatchers.IO){runCatching{NeroAccountLibraryStore.read(context,user.uid)}.getOrNull()}
    }
    suspend fun refreshPlan(){
        if(planBusy)return
        planBusy=true
        plan=null;planError=false
        try{
            plan=readNeroSubscription(context,user.uid)
            if(plan==null)planError=true
        }catch(e:CancellationException){throw e}catch(_:Exception){planError=true}
        finally{planBusy=false}
    }
    LaunchedEffect(user.uid){refreshPlan()}
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("NERO Cloud",fontSize=20.sp,fontWeight=FontWeight.Bold)
            Text("Favoritos, playlists, artistas e álbuns online da conta ficam separados da biblioteca de visitante. Alterações locais são preservadas mesmo quando a conexão falha.",fontSize=12.sp,color=Color.LightGray)
            val state=pull?.takeIf{it.uid==user.uid}?.state
            val pending=cached?.let{it.pending!=it.baseline}==true
            Text(when(state){
                "loading"->"Sincronizando…"
                "conflict"->"Conflito de alterações. Dados locais preservados."
                "pending"->"Alterações pendentes. Tente sincronizar novamente."
                "ready"->if(pending)"Alterações aguardando envio" else "Última consulta sincronizada"
                else->"Nuvem indisponível ou ainda não configurada"
            },color=if(state=="ready"&&!pending)MaterialTheme.colorScheme.primary else Color.LightGray,fontSize=13.sp)
            cached?.let{Text("${it.pending.favorites.size} favoritos · ${it.pending.playlists.size} playlists · ${it.pending.artists.size} artistas · ${it.pending.albums.size} álbuns da conta",fontSize=13.sp)}
            Text("Playlists, artistas seguidos, álbuns e histórico online pertencem à conta. Arquivos offline e Replay ficam no aparelho.",fontSize=12.sp,color=Color.LightGray)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("Ouça novamente")
                    Text("Registra músicas recentes desta conta apenas no aparelho. Pausar não apaga registros anteriores.",fontSize=12.sp,color=Color.LightGray)
                }
                Switch(checked=localRecent,enabled=enabled,onCheckedChange={value->
                    if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==user.uid){NeroLocalRecent.setEnabled(context,user.uid,value);localRecent=value}
                })
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("Sincronizar histórico")
                    Text("Registra até 100 faixas online neste aparelho para sincronização. Pausar não apaga registros anteriores.",fontSize=12.sp,color=Color.LightGray)
                }
                Switch(checked=historyEnabled,enabled=enabled,onCheckedChange={value->
                    if(NeroSupabase.activeUid.value==user.uid){NeroAccountSync.setHistoryEnabled(context,user.uid,value);historyEnabled=value}
                })
            }
            OutlinedButton(enabled=enabled&&!busy&&state!="loading",onClick={scope.launch{NeroAccountSync.pull(context,user.uid)}},modifier=Modifier.fillMaxWidth()){Text("Sincronizar agora")}
            TextButton(enabled=enabled&&!busy,onClick={confirm="favorites"}){Text("Importar favoritos anteriores do aparelho")}
            TextButton(enabled=enabled&&!busy,onClick={confirm="playlists"}){Text("Importar playlists anteriores do aparelho")}
            if(state=="loading"||busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
        }
    }
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Plano e assinatura",fontSize=20.sp,fontWeight=FontWeight.Bold)
            Text(when{planBusy->"Consultando assinatura…";planError->"Serviço de planos ainda indisponível";else->plan?.phase?.label?:"Não verificado"},color=MaterialTheme.colorScheme.primary)
            plan?.let{details->
                details.planLabel?.let{Text("Plano: "+it,fontSize=13.sp)}
                details.dateLabel()?.let{Text("Acesso até: "+it,fontSize=13.sp)}
                details.autoRenew?.let{Text(if(it)"Renovação automática informada pelo servidor" else "Sem renovação automática",fontSize=12.sp,color=Color.LightGray)}
            }
            Text("Experiência prevista: 7 dias. Carência de até 48 horas somente para falha de renovação elegível. A ativação depende do servidor.",fontSize=12.sp,color=Color.LightGray)
            Text("Assinaturas vinculadas à sua conta e gerenciadas pelo servidor. Contratação futura pelo site oficial, sem cobrança nesta versão.",fontSize=12.sp,color=Color.LightGray)
            OutlinedButton(onClick=onPlans,modifier=Modifier.fillMaxWidth()){Text("Conhecer NERO Premium")}
            TextButton(enabled=enabled&&!planBusy,onClick={scope.launch{refreshPlan()}}){Text("Consultar / restaurar assinatura")}
            if(planBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
            HorizontalDivider()
            NeroPlayerAppearance(user.uid,enabled)
            HorizontalDivider()
            NeroBackupPremium(user.uid,enabled)
        }
    }
    confirm?.let{kind->
        val playlists=kind=="playlists"
        AlertDialog(onDismissRequest={confirm=null},title={Text(if(playlists)"Importar playlists anteriores?" else "Importar favoritos anteriores?")},
            text={Text("As referências online compatíveis serão copiadas para esta conta Google. Confirme somente se esses dados pertencem a você. As coleções anteriores não serão apagadas; arquivos locais não serão enviados.")},
            confirmButton={TextButton(enabled=enabled&&!busy,onClick={confirm=null;busy=true;scope.launch{
                try{
                    if(playlists)NeroAccountSync.importGuestPlaylists(context,user.uid)else NeroAccountSync.importGuestFavorites(context,user.uid)
                    notice="Coleções copiadas para a conta neste aparelho."
                }
                catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível importar as coleções."}
                finally{busy=false}
            }}){Text("Importar")}},dismissButton={TextButton(onClick={confirm=null}){Text("Cancelar")}})
    }
}
