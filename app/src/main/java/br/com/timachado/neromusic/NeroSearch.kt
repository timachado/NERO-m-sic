package br.com.timachado.neromusic

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val searchAccent=Color(0xFF39FF14)
private val searchAqua=Color(0xFF00E5FF)
private val genres=listOf("Sertanejo","Pagode","Gospel","MPB","Pop","Rock","Samba","Forró","Eletrônica","Jazz","Hip-hop","Clássica")
private val colors=listOf(0xFF664828,0xFF17634B,0xFF164A66,0xFF405E3B,0xFF214B83,0xFF323E68,0xFF17615E,0xFF715026,0xFF205D76,0xFF454B62,0xFF285342,0xFF3D5266)
private enum class SearchTab(val label:String){ALL("Tudo"),SONG("Músicas"),ARTIST("Artistas"),ALBUM("Álbuns"),LOCAL("No aparelho"),OPEN("Áudio livre"),YOUTUBE("YouTube")}

@Composable
internal fun NeroSearch(tracks:List<Track>,canPlay:Boolean,onPlay:(Track)->Unit,onOpenPlay:(MediaItem)->Unit,initialQuery:String="",onPauseAudio:()->Unit={},onNativeQueue:(List<MediaItem>,Int)->Unit={_,_->}){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var query by remember{mutableStateOf(initialQuery)}
    var tab by remember{mutableStateOf(SearchTab.ALL)}
    var revision by remember{mutableIntStateOf(0)}
    var results by remember{mutableStateOf<Map<CatalogKind,List<CatalogEntry>>>(emptyMap())}
    var busy by remember{mutableStateOf(false)}
    var errors by remember{mutableStateOf<Set<CatalogKind>>(emptySet())}
    var openResults by remember{mutableStateOf(emptyList<OpenTrack>())}
    var saved by remember{mutableStateOf(emptyList<OpenTrack>())}
    var openBusy by remember{mutableStateOf(false)}
    var openError by remember{mutableStateOf(false)}
    var saving by remember{mutableStateOf<Long?>(null)}
    var message by remember{mutableStateOf<String?>(null)}
    var adding by remember{mutableStateOf<OnlineRef?>(null)}
    var addingLocal by remember{mutableStateOf<Track?>(null)}
    var detail by remember{mutableStateOf<List<CatalogEntry>>(emptyList())}
    var youtube by remember{mutableStateOf<YouTubeHit?>(null)}
    var youtubeBrowser by remember{mutableStateOf<String?>(null)}
    var youtubeResults by remember{mutableStateOf(emptyList<YouTubeHit>())}
    var youtubeBusy by remember{mutableStateOf(false)}
    var youtubeError by remember{mutableStateOf<String?>(null)}
    var nativeResults by remember{mutableStateOf(emptyList<NativeMusicHit>())}
    var nativeBusy by remember{mutableStateOf(false)}
    var nativeError by remember{mutableStateOf(false)}
    var moreArtist by remember{mutableStateOf<List<NativeMusicHit>>(emptyList())}
    var relatedArtist by remember{mutableStateOf("")}
    var resolving by remember{mutableStateOf<String?>(null)}
    val term=query.trim()
    val local=remember(tracks,term){if(term.isBlank())emptyList()else tracks.filter{t->listOf(t.title,t.artist,t.album,t.genre).any{it.contains(term,true)}}}
    LaunchedEffect(Unit){saved=downloaded(context)}
    LaunchedEffect(term,tab,revision){
        results=emptyMap();errors=emptySet();openResults=emptyList();openError=false;busy=false;openBusy=false;message=null
        if(term.length<3)return@LaunchedEffect
        delay(650)
        val kinds=when(tab){SearchTab.ALL->listOf(CatalogKind.SONG,CatalogKind.ARTIST,CatalogKind.ALBUM);SearchTab.SONG->listOf(CatalogKind.SONG);SearchTab.ARTIST->listOf(CatalogKind.ARTIST);SearchTab.ALBUM->listOf(CatalogKind.ALBUM);else->emptyList()}
        if(BuildConfig.DEBUG&&kinds.isNotEmpty()){
            busy=true
            try{for(kind in kinds){
                try{results=results+(kind to DevCatalog.search(term,kind))}
                catch(e:CancellationException){throw e}catch(_:Exception){errors=errors+kind}
            }}finally{busy=false}
        }
        if(tab==SearchTab.OPEN||tab==SearchTab.ALL){
            openBusy=true
            try{openResults=searchOpen(term)}
            catch(e:CancellationException){throw e}catch(_:Exception){openError=true}
            finally{openBusy=false}
        }
    }
    LaunchedEffect(term,tab,revision){
        youtubeResults=emptyList();youtubeError=null;youtubeBusy=false
        if(term.length<3||tab!=SearchTab.YOUTUBE)return@LaunchedEffect
        if(!YouTubeCatalog.configured)return@LaunchedEffect
        delay(700);youtubeBusy=true
        try{youtubeResults=YouTubeCatalog.search(context,term)}
        catch(e:CancellationException){throw e}catch(e:Exception){youtubeError=e.message?.takeIf{it in setOf("Entre na conta NERO para consultar o YouTube.","Limite de consultas atingido. Tente mais tarde.")}?:"Não foi possível consultar o YouTube."}
        finally{youtubeBusy=false}
    }
    LaunchedEffect(term,tab,revision){
        nativeResults=emptyList();nativeBusy=false;nativeError=false
        if(!NativeMusicCatalog.enabled||term.length<3||tab !in setOf(SearchTab.ALL,SearchTab.SONG,SearchTab.YOUTUBE))return@LaunchedEffect
        delay(850);nativeBusy=true
        try{nativeResults=NativeMusicCatalog.search(term)}
        catch(e:CancellationException){throw e}catch(_:Exception){nativeError=true}
        finally{nativeBusy=false}
    }
    LaunchedEffect(term,tab,nativeResults){
        moreArtist=emptyList();relatedArtist=""
        if(tab!=SearchTab.ALL||nativeResults.isEmpty())return@LaunchedEffect
        val artist=nativeResults.first().artist.removeSuffix(" - Topic").trim()
        if(artist.length<3||artist.equals(term,true))return@LaunchedEffect
        delay(550)
        try{
            val current=nativeResults.map{it.id}.toSet()
            val additional=NativeMusicCatalog.search(artist).filter{it.id !in current&&it.artist.removeSuffix(" - Topic").trim().equals(artist,true)}.take(8)
            if(additional.isNotEmpty()){moreArtist=additional;relatedArtist=artist}
        }catch(e:CancellationException){throw e}catch(_:Exception){}
    }
    fun playNative(hit:NativeMusicHit){
        if(!canPlay)return
        val (queue,index)=nativeQueue(nativeResults+moreArtist,hit)
        onNativeQueue(queue,index)
    }
    val browserQuery=youtubeBrowser
    BackHandler(browserQuery!=null){youtubeBrowser=null}
    if(browserQuery!=null){
        key(browserQuery){YouTubeWebSearchScreen(browserQuery,onBack={youtubeBrowser=null})}
        return
    }
    val selectedVideo=youtube
    BackHandler(selectedVideo!=null){youtube=null}
    if(selectedVideo!=null){
        key(selectedVideo.id){YouTubePlayerScreen(selectedVideo,onBack={youtube=null})}
        return
    }
    addingLocal?.let{SaveLocalTrackDialog(it,onClose={addingLocal=null})}
    adding?.let{SaveOnlineDialog(listOf(it),onClose={adding=null})}
    val entry=detail.lastOrNull()
    BackHandler(entry!=null){detail=detail.dropLast(1)}
    if(entry!=null){
        key(entry.id){CatalogDetailsScreen(entry,onBack={detail=detail.dropLast(1)},onAlbum={detail=detail+it})}
        return
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Buscar",fontSize=26.sp,fontWeight=FontWeight.Bold)
        OutlinedTextField(query,{query=it.take(100)},modifier=Modifier.fillMaxWidth(),singleLine=true,
            placeholder={Text("Músicas, artistas, álbuns")},leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}){Icon(Icons.Default.Close,"Limpar busca")}},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search))
        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            items(SearchTab.entries.filter{BuildConfig.DEBUG||it in setOf(SearchTab.ALL,SearchTab.LOCAL,SearchTab.OPEN,SearchTab.YOUTUBE)}){f->FilterChip(selected=tab==f,onClick={tab=f},label={Text(f.label)})}
        }
        if(busy||openBusy||youtubeBusy||nativeBusy||resolving!=null)LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let{Text(it,color=searchAqua,fontSize=12.sp)}
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=14.dp)){
            if(term.isBlank()){
                item{PremiumGenres(onSearch={query=it;tab=SearchTab.ALL})}
            }else{
                if(NativeMusicCatalog.enabled&&tab in setOf(SearchTab.ALL,SearchTab.SONG,SearchTab.YOUTUBE)){
                    item{Text("NERO Music",fontWeight=FontWeight.Bold,fontSize=19.sp);Text("Músicas · fonte YouTube Music",fontSize=11.sp,color=Color.LightGray)}
                    if(tab==SearchTab.ALL&&nativeResults.isNotEmpty())item{
                        val top=nativeResults.first()
                        Text("Resultado principal",fontSize=15.sp,fontWeight=FontWeight.Bold,color=Color.LightGray)
                        Card(onClick={playNative(top)},enabled=canPlay,modifier=Modifier.fillMaxWidth()){
                            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                                AsyncImage(model=top.thumbnail,contentDescription="Capa de "+top.title,contentScale=ContentScale.Crop,modifier=Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)))
                                Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(top.title,fontSize=18.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis);Text(top.artist,fontSize=12.sp,color=Color.LightGray)}
                                Icon(Icons.Default.PlayCircle,"Reproduzir",tint=searchAccent,modifier=Modifier.size(36.dp))
                            }
                        }
                        val artists=nativeResults.map{it.artist.removeSuffix(" - Topic").trim()}.filter{it.isNotBlank()}.distinct().take(6)
                        if(artists.isNotEmpty()){
                            Text("Artistas encontrados",fontSize=14.sp,color=Color.LightGray,modifier=Modifier.padding(top=8.dp))
                            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(artists){artist->AssistChip(onClick={query=artist;tab=SearchTab.ALL},label={Text(artist)})}}
                        }
                        Text("Músicas",fontSize=19.sp,fontWeight=FontWeight.Bold)
                    }
                    if(nativeError)item{TextButton(onClick={revision++}){Text("Falha na busca. Tentar novamente")}}
                    if(!nativeBusy&&!nativeError&&nativeResults.isEmpty()&&term.length>=3)item{Text("Nenhuma faixa encontrada.",color=Color.LightGray)}
                    items(if(tab==SearchTab.ALL)nativeResults.take(8)else nativeResults,key={"native:"+it.id}){hit->
                        Row(Modifier.fillMaxWidth().clickable(enabled=canPlay&&resolving==null){playNative(hit)}.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
                            AsyncImage(model=hit.thumbnail,contentDescription="Capa de "+hit.title,contentScale=ContentScale.Crop,modifier=Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)))
                            Column(Modifier.weight(1f).padding(horizontal=8.dp)){
                                Text(hit.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                                Text(hit.artist,fontSize=12.sp,color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                            IconButton(onClick={adding=hit.reference()}){Icon(Icons.Default.PlaylistAdd,"Adicionar à playlist",tint=searchAccent)}
                            IconButton(enabled=canPlay&&resolving==null,onClick={playNative(hit)}){Icon(Icons.Default.PlayArrow,"Reproduzir no NERO",tint=searchAccent)}
                        }
                    }
                    if(tab==SearchTab.ALL&&nativeResults.size>8)item{TextButton(onClick={tab=SearchTab.SONG}){Text("Ver todas as músicas")}}
                    if(tab==SearchTab.ALL&&moreArtist.isNotEmpty()){
                        item{Text("Mais de "+relatedArtist,fontSize=19.sp,fontWeight=FontWeight.Bold)}
                        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){items(moreArtist,key={it.id}){hit->
                            Card(onClick={playNative(hit)},enabled=canPlay,modifier=Modifier.width(136.dp)){
                                Column(Modifier.padding(8.dp)){
                                    AsyncImage(model=hit.thumbnail,contentDescription="Capa de "+hit.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
                                    Text(hit.title,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                                    IconButton(onClick={adding=hit.reference()}){Icon(Icons.Default.PlaylistAdd,"Adicionar à playlist",tint=searchAccent)}
                                }
                            }
                        }}}
                    }
                }
                if(tab==SearchTab.ALL||tab==SearchTab.LOCAL){
                    item{Text("No aparelho",fontWeight=FontWeight.Bold,fontSize=19.sp)}
                    val rows=if(tab==SearchTab.ALL)local.take(5)else local
                    if(rows.isEmpty())item{Text("Nenhuma faixa local encontrada.",fontSize=12.sp,color=Color.LightGray)}
                    items(rows,key={it.id}){track->
                        Row(Modifier.fillMaxWidth().clickable(enabled=canPlay){onPlay(track)},verticalAlignment=Alignment.CenterVertically){
                            LocalArtwork(track,Modifier.size(52.dp))
                            Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(track.title,maxLines=2,overflow=TextOverflow.Ellipsis);Text(track.artist,fontSize=12.sp,color=Color.LightGray)}
                            IconButton(onClick={addingLocal=track}){Icon(Icons.Default.PlaylistAdd,"Adicionar à playlist local",tint=searchAccent)}
                            IconButton(enabled=canPlay,onClick={onPlay(track)}){Icon(Icons.Default.PlayArrow,"Reproduzir arquivo local",tint=searchAccent)}
                        }
                    }
                    if(tab==SearchTab.ALL&&local.size>5)item{TextButton(onClick={tab=SearchTab.LOCAL}){Text("Ver todas as faixas locais")}}
                }
                if(BuildConfig.DEBUG&&tab !in setOf(SearchTab.LOCAL,SearchTab.OPEN,SearchTab.YOUTUBE)){
                    item{Text("Catálogo musical · MusicBrainz (prévia)",fontSize=12.sp,color=Color.LightGray);Text("Referências sem áudio disponível no NERO podem ser salvas; a reprodução externa é opcional.",fontSize=11.sp,color=Color.LightGray)}
                    for(kind in CatalogKind.entries){
                        val all=results[kind]?:continue
                        item{Text(kind.label,fontSize=19.sp,fontWeight=FontWeight.Bold)}
                        if(all.isEmpty())item{Text("Nenhum resultado.",fontSize=12.sp,color=Color.LightGray)}
                        items(if(tab==SearchTab.ALL)all.take(5)else all,key={kind.name+it.id}){song->
                            Row(Modifier.fillMaxWidth().background(Color(0xFF10171C),RoundedCornerShape(10.dp)).padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                                Box(Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF12313A)),contentAlignment=Alignment.Center){
                                    Icon(if(kind==CatalogKind.ARTIST)Icons.Default.Person else Icons.Default.MusicNote,null,tint=searchAqua)
                                    song.coverUrl?.let{AsyncImage(model=it,contentDescription="Capa de "+song.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())}
                                }
                                Column(Modifier.weight(1f).padding(horizontal=8.dp)){
                                    Text(song.title,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                                    if(song.credit.isNotBlank())Text(song.credit,fontSize=12.sp,color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis)
                                }
                                if(kind==CatalogKind.SONG){
                                    IconButton(onClick={adding=OnlineRef(song.id,song.title,song.credit,song.artworkReleaseId)}){Icon(Icons.Default.PlaylistAdd,"Adicionar à playlist online",tint=searchAccent)}
                                    val matching=tracks.filter{it.title.trim().equals(song.title.trim(),true)&&it.artist.trim().equals(song.credit.trim(),true)}.singleOrNull()
                                    if(matching!=null)IconButton(enabled=canPlay,onClick={onPlay(matching)}){Icon(Icons.Default.PlayArrow,"Reproduzir arquivo correspondente no NERO",tint=searchAccent)}
                                    else IconButton(onClick={if(!openYouTubeMusic(context,song.searchText))message="Não foi possível abrir o YouTube Music."}){Icon(Icons.Default.OpenInNew,"Abrir no YouTube Music oficial")}
                                }else IconButton(onClick={detail=listOf(song)}){Icon(Icons.Default.KeyboardArrowRight,"Abrir detalhes")}
                            }
                        }
                        if(tab==SearchTab.ALL&&all.size>5)item{TextButton(onClick={tab=when(kind){CatalogKind.SONG->SearchTab.SONG;CatalogKind.ARTIST->SearchTab.ARTIST;CatalogKind.ALBUM->SearchTab.ALBUM}}){Text("Ver mais: "+kind.label)}}
                    }
                    if(errors.isNotEmpty())item{Text("Algumas consultas falharam.",color=Color.LightGray);TextButton(onClick={revision++}){Text("Tentar novamente")}}
                    item{TextButton(onClick={onPauseAudio();youtubeBrowser=term}){Text("Pesquisar no YouTube dentro do NERO")}}
                }
                if(tab==SearchTab.OPEN||tab==SearchTab.ALL){
                    item{Text("Áudio com licença aberta",fontSize=19.sp,fontWeight=FontWeight.Bold);Text("Fonte: Wikimedia Commons",fontSize=12.sp,color=Color.LightGray)}
                    if(openError)item{TextButton(onClick={revision++}){Text("Falha na consulta. Tentar novamente")}}
                    if(!openBusy&&!openError&&openResults.isEmpty())item{Text("Nenhum áudio compatível encontrado.",color=Color.LightGray)}
                    items(if(tab==SearchTab.ALL)openResults.take(5)else openResults,key={it.id}){audio->
                        val downloaded=saved.any{it.id==audio.id}
                        Column(Modifier.fillMaxWidth().background(Color(0xFF10171C),RoundedCornerShape(10.dp)).padding(10.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically){
                                Column(Modifier.weight(1f)){Text(audio.title);Text(audio.artist,fontSize=12.sp,color=Color.LightGray);Text(audio.license,fontSize=11.sp,color=searchAccent)}
                                IconButton(onClick={adding=OnlineRef("commons:"+audio.id,audio.title,audio.artist,openAudio=audio)}){Icon(Icons.Default.PlaylistAdd,"Adicionar áudio à playlist",tint=searchAccent)}
                                IconButton(enabled=canPlay,onClick={onOpenPlay(audio.item(if(downloaded)Uri.fromFile(audioFile(context,audio))else Uri.parse(audio.url)))}){Icon(Icons.Default.PlayArrow,"Reproduzir áudio autorizado")}
                            }
                            Row(verticalAlignment=Alignment.CenterVertically){
                                TextButton(onClick={openBrowser(context,Uri.parse(audio.source))}){Text("Fonte e licença")}
                                if(downloaded)Text("Salvo")else TextButton(enabled=saving==null,onClick={
                                    saving=audio.id;scope.launch{try{saveAudio(context,audio);saved=downloaded(context);message="Áudio salvo."}catch(e:CancellationException){throw e}catch(_:Exception){message="Falha ao salvar."}finally{saving=null}}
                                }){Text(if(saving==audio.id)"Salvando…"else "Baixar")}
                            }
                        }
                    }
                }
                if(tab==SearchTab.ALL)item{OutlinedButton(onClick={tab=SearchTab.YOUTUBE},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PlayCircle,null);Spacer(Modifier.width(8.dp));Text("Vídeos musicais do YouTube")}}
                if(tab==SearchTab.YOUTUBE){
                    item{Text("YouTube · vídeos musicais",fontSize=19.sp,fontWeight=FontWeight.Bold);Text("Reprodução no player oficial incorporado.",fontSize=12.sp,color=Color.LightGray)}
                    item{
                        if(!YouTubeCatalog.configured)Text("Busca pela página oficial, dentro do NERO.",fontSize=12.sp,color=Color.LightGray)
                        Button(enabled=term.length>=3,onClick={onPauseAudio();youtubeBrowser=term},modifier=Modifier.fillMaxWidth()){
                            Icon(Icons.Default.Search,null);Spacer(Modifier.width(8.dp));Text("Pesquisar YouTube dentro do NERO")
                        }
                    }
                    youtubeError?.let{err->item{Text(err,color=Color.LightGray);if(YouTubeCatalog.configured)TextButton(onClick={revision++}){Text("Tentar novamente")}}}
                    if(YouTubeCatalog.configured&&!youtubeBusy&&youtubeError==null&&youtubeResults.isEmpty()&&term.length>=3)item{Text("Nenhum vídeo musical encontrado.",color=Color.LightGray)}
                    items(youtubeResults,key={it.id}){hit->
                        Row(Modifier.fillMaxWidth().clickable{onPauseAudio();youtube=hit}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                            AsyncImage(model=hit.thumbnail,contentDescription="Miniatura do YouTube",contentScale=ContentScale.Crop,modifier=Modifier.size(88.dp,54.dp).clip(RoundedCornerShape(8.dp)))
                            Column(Modifier.weight(1f).padding(horizontal=8.dp)){Text(hit.title,maxLines=2,overflow=TextOverflow.Ellipsis);Text(hit.channel,fontSize=12.sp,color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis)}
                            IconButton(onClick={onPauseAudio();youtube=hit}){Icon(Icons.Default.PlayCircle,"Reproduzir vídeo dentro do NERO",tint=searchAccent)}
                        }
                    }
                }
                if(term.length<3)item{Text("Digite pelo menos três caracteres para consultar o catálogo online.",fontSize=12.sp,color=Color.LightGray)}
            }
        }
    }
}
