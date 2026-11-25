package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NeroCloudBackupCard(uid:String,enabled:Boolean,onRestored:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var busy by remember(uid){mutableStateOf(false)}
    var notice by remember(uid){mutableStateOf("")}
    var preview by remember(uid){mutableStateOf<CloudLibraryRevision?>(null)}
    var confirmUpload by remember(uid){mutableStateOf(false)}
    var confirmRestore by remember(uid){mutableStateOf(false)}
    fun run(task:suspend ()->Unit){
        if(busy||!enabled)return
        busy=true;notice=""
        scope.launch{
            try{task()}
            catch(e:CancellationException){throw e}
            catch(_:Exception){notice="Operação não confirmada. Confira a conta, a conexão e a configuração do NERO Cloud."}
            finally{busy=false}
        }
    }
    Card(modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("NERO Cloud · Prévia",style=MaterialTheme.typography.titleMedium)
            Text("Backup manual de favoritos e playlists online. Não envia áudio, histórico, senhas nem arquivos do aparelho.")
            OutlinedButton(enabled=enabled&&!busy,onClick={run{
                preview=readCloudLibrary(context,uid)
                notice=if(preview?.revision==0L)"Nenhum backup nesta conta." else "Backup encontrado."
            }},modifier=Modifier.fillMaxWidth()){Text("Consultar backup")}
            preview?.let{p->
                Text("Nuvem: "+p.snapshot.favorites.size+" favoritos · "+p.snapshot.playlists.size+" playlists")
                Button(enabled=enabled&&!busy,onClick={confirmUpload=true},modifier=Modifier.fillMaxWidth()){Text("Enviar e mesclar backup")}
                OutlinedButton(enabled=enabled&&!busy&&p.revision>0,onClick={confirmRestore=true},modifier=Modifier.fillMaxWidth()){Text("Restaurar e mesclar")}
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(notice.isNotBlank())Text(notice)
        }
    }
    if(confirmUpload)AlertDialog(onDismissRequest={confirmUpload=false},title={Text("Enviar biblioteca?")},text={Text("Mescla as referências locais com o backup desta conta. Itens da nuvem não serão excluídos. Alterações concorrentes exigirão nova consulta.")},confirmButton={TextButton(onClick={
        confirmUpload=false
        val p=preview?:return@TextButton
        run{
            val local=withContext(Dispatchers.IO){localCloudLibrary(context)}
            val combined=mergeCloudLibrary(local,p.snapshot)
            val ok=writeCloudLibrary(context,uid,p.revision,combined)
            preview=null
            notice=if(ok)"Backup salvo. Consulte novamente para ver a nova revisão." else "Backup alterado em outro aparelho. Consulte novamente."
        }
    }){Text("Enviar")}},dismissButton={TextButton(onClick={confirmUpload=false}){Text("Cancelar")}})
    if(confirmRestore)AlertDialog(onDismissRequest={confirmRestore=false},title={Text("Restaurar biblioteca?")},text={Text("Adiciona referências ausentes e mescla músicas nas playlists de mesmo ID. Mantém nomes locais, favoritos e músicas existentes. Itens apagados anteriormente podem reaparecer.")},confirmButton={TextButton(onClick={
        confirmRestore=false
        run{
            val fresh=readCloudLibrary(context,uid)
            require(fresh.revision>0L)
            // Both files use their existing AtomicFile writers. Each operation is additive;
            // a partial failure is recoverable by repeating the merge.
            withContext(Dispatchers.IO){
                val current=localCloudLibrary(context)
                val merged=mergeCloudLibrary(current,fresh.snapshot)
                encodeCloudLibrary(merged)
                saveOnlineFavorites(context,merged.favorites)
                saveOnlineLists(context,merged.playlists)
            }
            preview=fresh;onRestored();notice="Referências restauradas e mescladas."
        }
    }){Text("Restaurar")}},dismissButton={TextButton(onClick={confirmRestore=false}){Text("Cancelar")}})
}
