package br.com.timachado.neromusic

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

private const val MAX_PAYLOAD = 180000
private data class Ref(val title:String,val artist:String,val duration:Long) {
    fun json()=JSONObject().put("title",title).put("artist",artist).put("duration",duration)
    fun key()=Triple(title.trim().lowercase(Locale.ROOT),artist.trim().lowercase(Locale.ROOT),duration)
}
private fun ref(t:Track)=Ref(t.title,t.artist,t.duration)
private fun readRef(o:JSONObject)=Ref(o.getString("title"),o.getString("artist"),o.getLong("duration"))

private fun backup(context:Context,tracks:List<Track>,favorites:Set<String>):String {
    val byId=tracks.associateBy { it.id }
    val f=JSONArray()
    favorites.forEach { id -> id.toLongOrNull()?.let { byId[it] }?.let { f.put(ref(it).json()) } }
    val lists=JSONArray()
    mixes(context).forEach { mix ->
        val refs=JSONArray()
        mix.ids.forEach { id -> byId[id]?.let { refs.put(ref(it).json()) } }
        lists.put(JSONObject().put("name",mix.name).put("tracks",refs))
    }
    return JSONObject().put("schema",1).put("favorites",f).put("playlists",lists).toString().also {
        require(it.toByteArray(Charsets.UTF_8).size<=MAX_PAYLOAD)
    }
}
private data class Restore(val favorites:Set<String>,val mixes:List<Mix>,val missing:Int)
private fun restore(payload:String,tracks:List<Track>):Restore {
    require(payload.toByteArray(Charsets.UTF_8).size<=MAX_PAYLOAD)
    val obj=JSONObject(payload)
    require(obj.getInt("schema")==1)
    val byKey=tracks.groupBy { ref(it).key() }
    var missing=0
    fun find(o:JSONObject):Long? {
        val id=byKey[readRef(o).key()]?.singleOrNull()?.id
        if(id==null)missing++
        return id
    }
    val fav=obj.getJSONArray("favorites")
    val ids=(0 until fav.length()).mapNotNull { find(fav.getJSONObject(it))?.toString() }.toSet()
    val lists=obj.getJSONArray("playlists")
    require(lists.length()<=1000)
    val mixes=(0 until lists.length()).map { i ->
        val list=lists.getJSONObject(i)
        val refs=list.getJSONArray("tracks")
        val name=list.getString("name").trim().take(80)
        require(name.isNotBlank())
        Mix(UUID.randomUUID().toString(),name,(0 until refs.length()).mapNotNull { find(refs.getJSONObject(it)) }.distinct())
    }
    return Restore(ids,mixes,missing)
}

@Composable
internal fun CloudLibraryScreen(tracks:List<Track>,favoriteIds:Set<String>,granted:Boolean,
    onMergeFavorites:(Set<String>)->Unit,onBack:()->Unit,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val auth=remember { authOrNull(context) }
    val scope=rememberCoroutineScope()
    val uid=auth?.currentUser?.uid
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    fun run(action:suspend ()->String) {
        if(busy || uid==null)return
        busy=true;notice=null
        scope.launch {
            try { notice=withTimeout(30000L) { action() } }
            catch(e:kotlinx.coroutines.TimeoutCancellationException){notice="Operação sem confirmação. Verifique a conexão antes de repetir."}
            catch(e:CancellationException){throw e}
            catch(_:Exception){notice="Não foi possível concluir. Confirme a conta, as regras do Firestore e a conexão."}
            finally {busy=false}
        }
    }
    fun doc()=FirebaseFirestore.getInstance().collection("nero_library_backups").document(requireNotNull(uid))
    if(confirm!=null) {
        val operation=confirm
        AlertDialog(onDismissRequest={confirm=null},title={Text(when(operation){"save"->"Salvar backup?";"restore"->"Restaurar biblioteca?";else->"Excluir backup?"})},
            text={Text(when(operation){
                "save"->"Substitui a cópia anterior desta conta. Envia apenas títulos, artistas, duração e nomes de playlists das faixas locais acessíveis."
                "restore"->"Adiciona favoritos e playlists à biblioteca atual, sem apagar arquivos ou listas. Faixas ausentes ou ambíguas não serão vinculadas."
                else->"Exclui somente a cópia na nuvem, não os dados do aparelho."
            })},
            confirmButton={TextButton(onClick={
                confirm=null
                run {
                    check(auth?.currentUser?.uid==uid)
                    when(operation){
                        "save"->{
                            val payload=backup(context,tracks,favoriteIds)
                            doc().set(mapOf("schema" to 1,"payload" to payload,"updatedAt" to FieldValue.serverTimestamp())).await()
                            "Backup confirmado pelo servidor."
                        }
                        "restore"->{
                            val snap=doc().get(Source.SERVER).await()
                            if(!snap.exists()) "Nenhum backup encontrado."
                            else {
                                check(auth?.currentUser?.uid==uid)
                                val imported=restore(requireNotNull(snap.getString("payload")),tracks)
                                val existing=mixes(context)
                                val additions=imported.mixes.filter { incoming -> existing.none { it.name==incoming.name && it.ids==incoming.ids } }.distinctBy { it.name to it.ids }
                                saveMixes(context,existing+additions)
                                onMergeFavorites(imported.favorites)
                                "Restauração concluída: ${additions.size} playlists adicionadas. ${imported.missing} referências sem correspondência única."
                            }
                        }
                        else->{doc().delete().await();"Exclusão confirmada pelo servidor."}
                    }
                }
            }){Text("Confirmar")}},dismissButton={TextButton(onClick={confirm=null}){Text("Cancelar")}})
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack,enabled=!busy){Icon(Icons.Default.ArrowBack,"Voltar")}
            Text("NERO Cloud",fontSize=24.sp,fontWeight=FontWeight.Bold)
        }
        Text("Prévia de desenvolvimento · backup manual",color=Color(0xFF00E5FF))
        Text("Somente metadados da biblioteca local. Arquivos de áudio, senhas e histórico não são enviados. Não é sincronização contínua.",color=Color.LightGray)
        if(uid==null) Text("Entre na conta para acessar o backup.",color=Color.LightGray)
        else if(!granted) Text("Permita acesso à biblioteca no aparelho antes de fazer backup ou restaurar.",color=Color.LightGray)
        Button(onClick={confirm="save"},enabled=uid!=null&&granted&&!busy&&tracks.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("Salvar backup")}
        OutlinedButton(onClick={confirm="restore"},enabled=uid!=null&&granted&&!busy&&tracks.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("Restaurar e mesclar")}
        OutlinedButton(onClick={confirm="delete"},enabled=uid!=null&&!busy,modifier=Modifier.fillMaxWidth()){Text("Excluir backup da nuvem")}
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        notice?.let{Text(it,color=Color.LightGray)}
        Text("A configuração do Firestore, o isolamento entre contas e a exclusão integral de dados da conta precisam ser validados antes de liberar o recurso comercialmente.",fontSize=12.sp,color=Color.LightGray)
    }
}
