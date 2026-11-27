package br.com.timachado.neromusic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

internal fun togetherCode(value:String):String?=value.trim().lowercase(java.util.Locale.ROOT).takeIf{Regex("[0-9a-f]{32}").matches(it)}
internal data class TogetherSong(val hit:NativeMusicHit,val votes:Int,val voted:Boolean)
internal data class TogetherRoom(val id:String,val title:String,val host:Boolean,val members:Int,val songs:List<TogetherSong>)
private fun decodeTogether(row:JSONObject):TogetherRoom{
    val id=requireNotNull(togetherCode(row.getString("id")))
    val array=row.getJSONArray("songs");require(array.length()<=100)
    val songs=(0 until array.length()).map{i->
        val song=array.getJSONObject(i);val video=song.getString("video")
        require(Regex("[A-Za-z0-9_-]{11}").matches(video))
        val title=song.getString("title");val artist=song.getString("artist")
        require(title.isNotBlank()&&title.length<=300&&artist.length<=300)
        TogetherSong(NativeMusicHit(video,title,artist,0L),song.getInt("votes").also{require(it in 0..12)},song.getBoolean("voted"))
    }.distinctBy{it.hit.id}
    return TogetherRoom(id,row.getString("title").take(80),row.getBoolean("host"),row.getInt("members").also{require(it in 1..12)},songs)
}
private suspend fun togetherCommand(context:Context,uid:String,action:String,code:String,title:String="",video:String="",artist:String=""):TogetherRoom?{
    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    val session=NeroSupabase.session(context,false);require(session.uid==uid)
    val args=JSONObject().put("p_action",action).put("p_room",requireNotNull(togetherCode(code))).put("p_title",title).put("p_video",video).put("p_artist",artist)
    val row=JSONObject(NeroSupabase.rpc("nero_together_v1",args,session.access))
    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    return if(row.optBoolean("closed"))null else decodeTogether(row)
}
// Process-only bookmark, not a persistent credential. Never reuse another account's room.
private object TogetherBookmark {
    private var owner:String?=null
    private var code:String?=null
    fun forOwner(uid:String?):String? {
        if(owner!=uid){owner=uid;code=null}
        return if(uid!=null)code else null
    }
    fun update(uid:String,room:String?){
        if(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid){
            owner=uid;code=room?.let(::togetherCode)
        }
    }
}
@Composable
internal fun NeroTogetherShelf(canPlay:Boolean,onQueue:(List<MediaItem>,Int)->Unit,livePlayer:androidx.media3.common.Player?=null){
    val context=LocalContext.current
    val uid by NeroSupabase.activeUid.collectAsState()
    val ready by NeroSupabase.identityReady.collectAsState()
    if(!ready)return
    val bookmark=TogetherBookmark.forOwner(uid)
    key(uid){
        val scope=rememberCoroutineScope()
        var open by remember{mutableStateOf(false)}
        var room by remember{mutableStateOf<TogetherRoom?>(null)}
        var busy by remember{mutableStateOf(false)}
        var refreshing by remember{mutableStateOf(false)}
        var playlistOpen by remember{mutableStateOf(false)}
        var playlists by remember{mutableStateOf(emptyList<OnlineList>())}
        var selectedPlaylist by remember{mutableStateOf<OnlineList?>(null)}
        var importProgress by remember{mutableStateOf<String?>(null)}
        var roomFresh by remember{mutableStateOf(false)}
        var title by remember{mutableStateOf("")}
        var code by remember{mutableStateOf(bookmark.orEmpty())}
        var resumeCode by remember{mutableStateOf(bookmark)}
        var query by remember{mutableStateOf("")}
        var results by remember{mutableStateOf(emptyList<NativeMusicHit>())}
        var notice by remember{mutableStateOf<String?>(null)}
        fun active()=NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid
        fun command(action:String,id:String,name:String="",video:String="",artist:String=""){
            val owner=uid?:return
            if(busy||!active())return
            busy=true;notice=null
            scope.launch{
                while(refreshing)delay(50L)
                try{
                    if(action=="create"&&!verifiedPremium(context,owner)){notice="Para criar uma sala, confirme seu NERO Premium.";return@launch}
                    val updated=togetherCommand(context,owner,action,id,name,video,artist)
                    if(active()){
                        room=updated
                        roomFresh=updated!=null
                        resumeCode=updated?.id
                        TogetherBookmark.update(owner,resumeCode)
                    }
                }catch(e:CancellationException){throw e}catch(_:Exception){roomFresh=false;notice="Sala ou serviço indisponível. Confira o código e tente novamente."}
                finally{busy=false}
            }
        }
        LaunchedEffect(open,room?.id,uid){
            val owner=uid?:return@LaunchedEffect
            val id=room?.id?:return@LaunchedEffect
            while(open&&active()){
                delay(15000)
                if(!busy&&!refreshing){
                    refreshing=true
                    try{
                        val updated=togetherCommand(context,owner,"read",id)
                        if(active()&&!busy){if(room!=updated)room=updated;roomFresh=updated!=null;resumeCode=updated?.id;TogetherBookmark.update(owner,resumeCode)}
                    }
                    catch(e:CancellationException){throw e}catch(_:Exception){if(!busy)roomFresh=false}
                    finally{refreshing=false}
                }
            }
        }
        fun importSongs(list:OnlineList,hits:List<NativeMusicHit>){
            val owner=uid?:return
            val current=room?:return
            if(busy||!active())return
            val existing=current.songs.map{it.hit.id}.toSet()
            val pending=hits.distinctBy{it.id}.filterNot{it.id in existing}.take((100-current.songs.size).coerceAtLeast(0))
            if(pending.isEmpty()){notice="Nenhuma faixa nova compatível ou a fila está cheia.";return}
            busy=true;notice=null;playlistOpen=false;selectedPlaylist=null
            scope.launch{
                var added=0
                try{
                    while(refreshing)delay(50L)
                    for(hit in pending){
                        if(!active()||room?.id!=current.id)break
                        importProgress="Adicionando "+(added+1)+"/"+pending.size
                        val updated=togetherCommand(context,owner,"add",current.id,hit.title.take(300),hit.id,hit.artist.take(300))
                        if(!active()||updated==null)break
                        room=updated;roomFresh=true;added++
                        delay(100L)
                    }
                    if(active())notice="$added faixa(s) adicionada(s) de "+list.name+"."
                }catch(e:CancellationException){throw e}catch(_:Exception){
                    if(active())notice="$added faixa(s) adicionada(s). A importação foi interrompida; tente novamente para incluir as restantes."
                }finally{busy=false;importProgress=null}
            }
        }
        OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()){Text("NERO Together")}
        LaunchedEffect(open,uid){
            if(open&&room==null&&resumeCode!=null&&active())resumeCode?.let{command("read",it)}
        }
        if(open)AlertDialog(onDismissRequest={if(!busy)open=false},title={Text("NERO Together")},text={
            Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(uid==null)Text("Entre com Google para participar de salas privadas.")
                else if(room==null){
                    Text("Crie uma sala com Premium. Amigos com conta NERO podem entrar gratuitamente pelo código.",fontSize=12.sp)
                    resumeCode?.let{saved->
                        OutlinedButton(enabled=!busy,onClick={command("read",saved)}){Text("Retomar minha sala")}
                        TextButton(enabled=!busy,onClick={uid?.let{TogetherBookmark.update(it,null)};resumeCode=null;code=""}){Text("Esquecer sala neste aparelho")}
                    }
                    OutlinedTextField(title,{title=it.take(80)},label={Text("Nome da sala")},singleLine=true,enabled=!busy)
                    Button(enabled=!busy&&title.isNotBlank(),onClick={command("create",UUID.randomUUID().toString().replace("-",""),title.trim())}){Text("Criar sala")}
                    OutlinedTextField(code,{code=it.take(32)},label={Text("Código de convite")},singleLine=true,enabled=!busy)
                    OutlinedButton(enabled=!busy&&togetherCode(code)!=null,onClick={togetherCode(code)?.let{command("join",it)}}){Text("Entrar na sala")}
                }else{
                    val current=room!!
                    Text(current.title,fontWeight=FontWeight.Bold)
                    Text("${current.members}/12 participantes · sala privada por até 24 h",fontSize=12.sp)
                    TextButton(onClick={
                        val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Convite NERO Together",current.id));notice="Código copiado."
                    }){Text("Copiar código de convite")}
                    OutlinedTextField(query,{query=it.take(100);results=emptyList()},label={Text("Buscar música para a fila")},singleLine=true,enabled=!busy)
                    OutlinedButton(enabled=!busy&&query.trim().length>=3&&NativeMusicCatalog.enabled,onClick={
                        busy=true;val term=query.trim()
                        scope.launch{
                            try{results=NativeMusicCatalog.search(term).take(10)}
                            catch(e:CancellationException){throw e}catch(_:Exception){notice="Busca indisponível."}
                            finally{busy=false}
                        }
                    }){Text("Buscar")}
                    if(results.isNotEmpty())LazyColumn(Modifier.heightIn(max=130.dp)){
                        items(results.distinctBy{it.id},key={it.id}){hit->
                            TextButton(enabled=!busy,onClick={command("add",current.id,hit.title.take(300),hit.id,hit.artist.take(300));results=emptyList()}){Text("+ "+hit.title+" · "+displayArtistName(hit.artist),fontSize=12.sp)}
                        }
                    }
                    OutlinedButton(enabled=!busy&&uid!=null,onClick={
                        val owner=uid
                        if(owner!=null&&active()){
                            playlists=runCatching{scopedOnlineLists(context,owner)}.getOrDefault(emptyList())
                            selectedPlaylist=null;playlistOpen=!playlistOpen
                        }
                    },modifier=Modifier.fillMaxWidth()){Text(if(playlistOpen)"Ocultar minhas playlists" else "Adicionar das minhas playlists")}
                    if(playlistOpen){
                        if(playlists.isEmpty())Text("Nenhuma playlist online encontrada nesta conta.",fontSize=12.sp)
                        if(selectedPlaylist==null)LazyColumn(Modifier.heightIn(max=140.dp)){
                            items(playlists.distinctBy{it.id},key={it.id}){list->
                                TextButton(enabled=!busy,onClick={selectedPlaylist=list}){Text(list.name+" · "+list.songs.size+" faixas")}
                            }
                        }else selectedPlaylist?.let{list->
                            val hits=list.songs.mapNotNull(::musicHitFromRef).distinctBy{it.id}
                            val available=hits.filterNot{hit->current.songs.any{it.hit.id==hit.id}}
                            Text(list.name,fontWeight=FontWeight.Medium)
                            Text("Somente faixas online compatíveis; sua playlist original não será alterada.",fontSize=11.sp)
                            Button(enabled=!busy&&available.isNotEmpty()&&current.songs.size<100,onClick={importSongs(list,available)}){Text("Adicionar todas ("+available.size.coerceAtMost((100-current.songs.size).coerceAtLeast(0))+")")}
                            LazyColumn(Modifier.heightIn(max=140.dp)){
                                items(hits,key={it.id}){hit->
                                    val exists=current.songs.any{it.hit.id==hit.id}
                                    TextButton(enabled=!busy&&!exists&&current.songs.size<100,onClick={importSongs(list,listOf(hit))}){Text((if(exists)"✓ " else "+ ")+hit.title)}
                                }
                            }
                            TextButton(onClick={selectedPlaylist=null}){Text("Voltar às playlists")}
                        }
                    }
                    Text("Fila compartilhada · ordem por votos",fontWeight=FontWeight.Medium)
                    LazyColumn(Modifier.heightIn(max=180.dp)){
                        items(current.songs,key={it.hit.id}){song->
                            Row(verticalAlignment=Alignment.CenterVertically){
                                Column(Modifier.weight(1f)){Text(song.hit.title,fontSize=13.sp);Text(displayArtistName(song.hit.artist),fontSize=11.sp,color=Color.LightGray)}
                                TextButton(enabled=!busy,onClick={command(if(song.voted)"unvote" else "vote",current.id,video=song.hit.id)}){Text((if(song.voted)"✓ " else "+ ")+song.votes)}
                            }
                        }
                    }
                    if(livePlayer!=null&&uid!=null)NeroTogetherLive(current,requireNotNull(uid),livePlayer)
                    Text("A fila é compartilhada; a reprodução individual também está disponível.",fontSize=11.sp,color=Color.LightGray)
                    Button(enabled=canPlay&&roomFresh&&current.songs.isNotEmpty()&&!busy,onClick={
                        if(active()&&roomFresh){onQueue(current.songs.map{it.hit.queueItem()}.withOrigin(PlaybackOrigin("mix","NERO Together","")),0);open=false}
                    }){Text("Reproduzir fila")}
                    Row{
                        TextButton(enabled=!busy,onClick={command("read",current.id)}){Text("Atualizar")}
                        TextButton(enabled=!busy,onClick={command(if(current.host)"close" else "leave",current.id)}){Text(if(current.host)"Encerrar sala" else "Sair da sala")}
                    }
                }
                // Reserve the progress slot so a user action does not resize the dialog.
                Box(Modifier.fillMaxWidth().height(4.dp)){if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())}
                importProgress?.let{Text(it,fontSize=12.sp)}
                notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
            }
        },confirmButton={TextButton(enabled=!busy,onClick={open=false}){Text("Fechar")}})
    }
}
