package br.com.timachado.neromusic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class SmartLibraryGroup(val name:String,val songs:List<OnlineRef>)
internal data class SmartLibraryReport(val unique:Int,val repeated:Int,val similar:List<List<OnlineRef>>,val groups:List<SmartLibraryGroup>)
internal fun smartLibraryReport(lists:List<OnlineList>):SmartLibraryReport{
    val all=lists.flatMap{it.songs}.filter(::portableRef)
    val unique=all.distinctBy{it.id}
    val similar=unique.filter{it.artist.isNotBlank()&&it.title.isNotBlank()}
        .groupBy{genreKey(it.title) to genreKey(displayArtistName(it.artist))}
        .values.filter{it.size>1}.take(30)
    val genres=neroGenres.mapNotNull{genre->
        val songs=unique.filter{song->artistCreditParts(song.artist).any{genre.matchesArtist(it)}}
        if(songs.isEmpty())null else SmartLibraryGroup("NERO • "+genre.label,songs)
    }
    val classified=genres.flatMap{it.songs}.map{it.id}.toSet()
    val other=unique.filterNot{it.id in classified}
    return SmartLibraryReport(unique.size,all.size-unique.size,similar,genres+listOfNotNull(other.takeIf{it.isNotEmpty()}?.let{SmartLibraryGroup("NERO • Outros estilos",it)}))
}
@Composable
internal fun NeroSmartLibrary(uid:String?,ready:Boolean,lists:List<OnlineList>,onSaved:(List<OnlineList>)->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var open by remember(uid){mutableStateOf(false)}
    var busy by remember(uid){mutableStateOf(false)}
    var verified by remember(uid){mutableStateOf(false)}
    var checked by remember(uid){mutableStateOf(false)}
    var report by remember(uid){mutableStateOf<SmartLibraryReport?>(null)}
    var baseline by remember(uid){mutableStateOf(emptyList<OnlineList>())}
    var selection by remember(uid){mutableStateOf(emptySet<String>())}
    var notice by remember(uid){mutableStateOf<String?>(null)}
    fun active()=NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid
    OutlinedButton(enabled=ready&&!busy,onClick={open=true;verified=false;checked=false;report=null;notice=null;selection=emptySet()},modifier=Modifier.fillMaxWidth()){Text("NERO Smart Library · Premium")}
    if(open)AlertDialog(onDismissRequest={if(!busy)open=false},title={Text("NERO Smart Library")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Organize suas playlists por estilo, identifique faixas repetidas e crie coleções novas. Seus originais não serão alterados.",fontSize=13.sp)
            if(!verified){
                Text(if(uid==null)"Entre com Google para usar o Premium." else "Confirme sua assinatura para analisar a biblioteca.",fontSize=12.sp,color=Color.LightGray)
                OutlinedButton(enabled=uid!=null&&!busy,onClick={
                    val owner=uid?:return@OutlinedButton
                    busy=true;scope.launch{
                        try{
                            verified=verifiedPremium(context,owner)&&active();checked=true
                            if(verified){baseline=lists;report=smartLibraryReport(lists);selection=emptySet()}
                        }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível analisar a biblioteca."}
                        finally{busy=false}
                    }
                }){Text("Analisar biblioteca")}
                if(checked&&!verified)Text("Assinatura ativa não confirmada.",fontSize=12.sp)
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
            report?.takeIf{verified}?.let{r->
                Text("${r.unique} faixas únicas · ${r.repeated} ocorrências repetidas entre playlists",fontWeight=FontWeight.Bold)
                Text("${r.similar.size} grupos de possíveis versões semelhantes. Nenhuma faixa é excluída.",fontSize=12.sp)
                r.similar.take(5).forEach{group->Text(group.first().title+" · "+displayArtistName(group.first().artist)+" ("+group.size+" versões)",fontSize=11.sp)}
                Text("Criar novas coleções",fontWeight=FontWeight.Medium)
                Text("Gêneros sugeridos com base nos artistas conhecidos; classificação não exaustiva.",fontSize=11.sp,color=Color.LightGray)
                r.groups.forEach{group->
                    Row{
                        Checkbox(checked=group.name in selection,enabled=!busy&&group.songs.size<=1000,onCheckedChange={value->selection=if(value)selection+group.name else selection-group.name})
                        Column(Modifier.weight(1f)){
                            Text(group.name)
                            Text("${group.songs.size} faixas"+if(group.songs.size>1000)" · acima do limite por playlist" else "",fontSize=12.sp)
                        }
                    }
                }
            }
        }
    },confirmButton={TextButton(enabled=verified&&!busy&&selection.isNotEmpty(),onClick={
        val owner=uid?:return@TextButton
        val chosen=report?.groups.orEmpty().filter{it.name in selection}
        busy=true;scope.launch{
            try{
                if(!verifiedPremium(context,owner)||!active()){verified=false;notice="Assinatura não confirmada.";return@launch}
                val current=scopedOnlineLists(context,owner)
                if(current!=baseline){baseline=current;report=smartLibraryReport(current);selection=emptySet();notice="Sua biblioteca mudou. Revise a nova análise.";return@launch}
                val copies=chosen.map{g->require(g.songs.size<=1000);OnlineList(java.util.UUID.randomUUID().toString(),g.name,g.songs)}
                val next=importPortablePlaylists(current,copies)
                require(next.size<=100)
                saveScopedOnlineLists(context,owner,current,next)
                if(active()){onSaved(next);open=false}
            }catch(e:CancellationException){throw e}catch(_:Exception){notice="Não foi possível criar as coleções. Seus originais foram preservados."}
            finally{busy=false}
        }
    }){Text("Criar selecionadas")}},dismissButton={TextButton(enabled=!busy,onClick={open=false}){Text("Fechar")}})
}
