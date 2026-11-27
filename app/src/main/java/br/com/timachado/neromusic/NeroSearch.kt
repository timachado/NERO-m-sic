package br.com.timachado.neromusic

import android.net.Uri
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.json.JSONArray
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch



private val genres=neroGenreLabels
private val colors=listOf(0xFF664828,0xFF17634B,0xFF164A66,0xFF405E3B,0xFF214B83,0xFF323E68,0xFF17615E,0xFF715026,0xFF205D76,0xFF454B62,0xFF285342,0xFF3D5266)
private enum class SearchTab(val label:String){ALL("Tudo"),SONG("Músicas"),ARTIST("Artistas"),ALBUM("Álbuns"),LOCAL("No aparelho"),OPEN("Áudio livre"),CLIPS("Clipes")}

@Composable
internal fun NeroSearch(tracks:List<Track>,canPlay:Boolean,onPlay:(Track)->Unit,onOpenPlay:(MediaItem)->Unit,initialQuery:String="",initialOpenAudio:Boolean=false,onNativeQueue:(List<MediaItem>,Int)->Unit={_,_->},autoFocus:Boolean=false,onExit:()->Unit={},onlineFavoriteIds:Set<String> = emptySet(),onFavorite:(OnlineRef)->Unit={},currentId:String="",currentTitle:String="",currentArtist:String="",currentCover:String?=null,currentPlaying:Boolean=false,currentVideo:Boolean=false,onCurrentToggle:()->Unit={},onOpenPlayer:()->Unit={},offlineState:OfflineState=OfflineState(),onOfflineSave:(OpenTrack)->Unit={},onNativeSave:(NativeMusicHit)->Unit={},onOfflineOpen:()->Unit={},onEnqueue:(MediaItem,Boolean)->Unit={_,_->}){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var query by remember{mutableStateOf(initialQuery)}
    var showSource by remember{mutableStateOf(false)}
    if(showSource)AlertDialog(onDismissRequest={showSource=false},title={Text("Sobre o catálogo")},text={Text("O NERO Music oferece uma experiência musical independente, com acesso a conteúdos de fontes externas. A disponibilidade de músicas, artistas e álbuns depende dos respectivos provedores e das permissões de acesso.")},confirmButton={TextButton(onClick={showSource=false}){Text("Fechar")}})
    val focusRequester=remember{FocusRequester()}
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    var editing by remember{mutableStateOf(false)}
    var suggestions by remember{mutableStateOf(emptyList<String>())}
    val recentPrefs=remember(context){context.getSharedPreferences("nero_search",0)}
    var recent by remember{mutableStateOf(runCatching{
        val rows=JSONArray(recentPrefs.getString("queries","[]"));(0 until rows.length()).map{rows.getString(it)}
    }.getOrDefault(emptyList()))}
    fun rememberQuery(value:String){
        val clean=value.trim().take(100);if(clean.isBlank())return
        recent=(listOf(clean)+recent.filterNot{it.equals(clean,true)}).take(12)
        recentPrefs.edit().putString("queries",JSONArray(recent).toString()).apply()
    }
    fun submit(value:String){query=value.take(100);rememberQuery(query);editing=false;focusManager.clearFocus();keyboard?.hide()}
    LaunchedEffect(Unit){if(autoFocus){focusRequester.requestFocus();keyboard?.show()}}
    var albums by remember{mutableStateOf(emptyList<NativeCollection>())}
    var artists by remember{mutableStateOf(emptyList<NativeCollection>())}
    var albumPage by remember{mutableStateOf<NativeCollection?>(null)}
    var tab by remember{mutableStateOf(if(initialOpenAudio)SearchTab.OPEN else SearchTab.ALL)}
    var revision by remember{mutableIntStateOf(0)}

    var busy by remember{mutableStateOf(false)}

    var openResults by remember{mutableStateOf(emptyList<OpenTrack>())}
    var openBusy by remember{mutableStateOf(false)}
    var openError by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    val voice=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        val spoken=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if(result.resultCode==android.app.Activity.RESULT_OK&&!spoken.isNullOrBlank()){submit(spoken);revision++}
    }
    fun startVoice(){
        try{voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR").putExtra(RecognizerIntent.EXTRA_PROMPT,"Diga o nome da música ou artista").putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1))}
        catch(_:Exception){message="Pesquisa por voz indisponível neste aparelho."}
    }
    var adding by remember{mutableStateOf<OnlineRef?>(null)}
    var sharing by remember{mutableStateOf<NativeMusicHit?>(null)}
    sharing?.let{hit->TrackShareDialog(hit.title,hit.artist,hit.reference(),hit.thumbnail,onClose={sharing=null})}
    var addingLocal by remember{mutableStateOf<Track?>(null)}

    var nativeResults by remember{mutableStateOf(emptyList<NativeMusicHit>())}
    var nativeBusy by remember{mutableStateOf(false)}
    var nativeError by remember{mutableStateOf(false)}
    var moreArtist by remember{mutableStateOf<List<NativeMusicHit>>(emptyList())}
    var relatedArtist by remember{mutableStateOf("")}
    var artistPage by remember{mutableStateOf<String?>(null)}
    var resolving by remember{mutableStateOf<String?>(null)}
    val term=query.trim()
    val activeGenre=remember(term){genreForQuery(term)}
    val local=remember(tracks,term,tab){if(term.isBlank()){if(tab==SearchTab.LOCAL)tracks else emptyList()}else tracks.filter{t->listOf(t.title,t.artist,t.album,t.genre).any{it.contains(term,true)}}}
    LaunchedEffect(term,tab,revision,editing){
        openResults=emptyList();openError=false;busy=false;openBusy=false;message=null
        if(term.length<3||editing)return@LaunchedEffect
        delay(650)
        if(tab==SearchTab.OPEN||(!NativeMusicCatalog.enabled&&tab==SearchTab.ALL)){
            openBusy=true
            try{openResults=searchOpen(term)}
            catch(e:CancellationException){throw e}catch(_:Exception){openError=true}
            finally{openBusy=false}
        }
    }
    LaunchedEffect(term,tab,revision,editing){
        nativeResults=emptyList();nativeBusy=false;nativeError=false
        if(editing||!NativeMusicCatalog.enabled||term.length<3||tab !in setOf(SearchTab.ALL,SearchTab.SONG,SearchTab.CLIPS))return@LaunchedEffect
        nativeBusy=true;delay(350)
        try{nativeResults=activeGenre?.let{discoverGenreTracks(it,tab==SearchTab.CLIPS)}?:if(tab==SearchTab.CLIPS)NativeMusicCatalog.searchVideos(term)else NativeMusicCatalog.search(term)}
        catch(e:CancellationException){throw e}catch(_:Exception){nativeError=true}
        finally{nativeBusy=false}
    }
    LaunchedEffect(term,editing){
        suggestions=emptyList()
        if(!editing||!NativeMusicCatalog.enabled||term.length<2)return@LaunchedEffect
        delay(200)
        try{suggestions=NativeMusicCatalog.suggestions(term)}catch(e:CancellationException){throw e}catch(_:Exception){}
    }
    LaunchedEffect(term,tab,editing,revision){
        albums=emptyList();artists=emptyList()
        if(editing||!NativeMusicCatalog.enabled||term.length<3)return@LaunchedEffect
        delay(450)
        try{
            if(tab in setOf(SearchTab.ALL,SearchTab.ARTIST))artists=activeGenre?.let{discoverGenreCollections(it,NativeCollectionKind.ARTIST)}?:NativeMusicCatalog.collections(term,NativeCollectionKind.ARTIST)
            if(tab in setOf(SearchTab.ALL,SearchTab.ALBUM))albums=activeGenre?.let{discoverGenreCollections(it,NativeCollectionKind.ALBUM)}?:NativeMusicCatalog.collections(term,NativeCollectionKind.ALBUM)
        }catch(e:CancellationException){throw e}catch(_:Exception){}
    }
    LaunchedEffect(term,tab,nativeResults,editing){
        moreArtist=emptyList();relatedArtist=""
        if(editing||activeGenre!=null||tab!=SearchTab.ALL||nativeResults.isEmpty())return@LaunchedEffect
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
        rememberQuery(term);focusManager.clearFocus();keyboard?.hide()
        val clip=tab==SearchTab.CLIPS
        if(currentId==hit.reference().id&&currentVideo==clip){onOpenPlayer();return}
        val (queue,index)=nativeQueue(nativeResults+moreArtist,hit,video=clip)
        onNativeQueue(queue.withOrigin(PlaybackOrigin("search",term,"")),index)
        if(clip)onOpenPlayer()
    }
    val selectedAlbum=albumPage
    BackHandler(selectedAlbum!=null){albumPage=null}
    if(selectedAlbum!=null){key(selectedAlbum.url){NativeAlbumScreen(selectedAlbum,canPlay,onBack={albumPage=null},onQueue=onNativeQueue)};return}
    val selectedArtist=artistPage
    BackHandler(selectedArtist!=null){artistPage=null}
    if(selectedArtist!=null){
        key(selectedArtist){
            NativeArtistScreen(artist=selectedArtist,seed=nativeResults+moreArtist,canPlay=canPlay,onBack={artistPage=null},
                onQueue=onNativeQueue,onExplore={term->artistPage=null;query=term;tab=SearchTab.ALL},onAlbum={albumPage=it})
        }
        return
    }
    addingLocal?.let{SaveLocalTrackDialog(it,onClose={addingLocal=null})}
    adding?.let{SaveOnlineDialog(listOf(it),onClose={adding=null})}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={startVoice()}){Icon(Icons.Default.Mic,"Pesquisar por voz",tint=MaterialTheme.colorScheme.secondary)}
            IconButton(onClick={focusManager.clearFocus();keyboard?.hide();onExit()}){Icon(Icons.Default.ArrowBack,"Voltar")}
            OutlinedTextField(query,{query=it.take(100)},modifier=Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged{editing=it.isFocused},singleLine=true,shape=RoundedCornerShape(28.dp),
                placeholder={Text("Digite para pesquisar")},leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}){Icon(Icons.Default.Close,"Limpar busca")}},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submit(query)}))
        }
        if(editing&&term.isNotBlank()){
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)){
                val ideas=(suggestions+recent.filter{it.contains(term,true)}).filterNot{it.equals(term,true)}.distinct().take(6)
                items(ideas){idea->
                    Row(Modifier.fillMaxWidth().clickable{submit(idea)}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.Search,null,tint=Color.LightGray)
                        Text(idea,Modifier.weight(1f).padding(horizontal=12.dp),maxLines=2)
                        IconButton(onClick={query=idea}){Icon(Icons.Default.NorthWest,"Completar pesquisa")}
                    }
                }
                if(nativeBusy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
                items(nativeResults.take(6),key={it.id}){hit->
                    Row(Modifier.fillMaxWidth().clickable(enabled=canPlay){playNative(hit)}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                        NeroArtwork(model=hit.thumbnail,contentDescription="Capa de "+hit.title,contentScale=ContentScale.Crop,modifier=Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
                        Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(hit.title,maxLines=2,overflow=TextOverflow.Ellipsis,color=if(currentId==hit.reference().id&&currentVideo==(tab==SearchTab.CLIPS))MaterialTheme.colorScheme.primary else Color.Unspecified);Text("Música · "+hit.artist,fontSize=12.sp,color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis)}
                        if(currentId==hit.reference().id&&currentVideo==(tab==SearchTab.CLIPS))IconButton(enabled=canPlay,onClick=onCurrentToggle){Icon(if(currentPlaying)Icons.Default.Pause else Icons.Default.PlayArrow,if(currentPlaying)"Pausar" else "Continuar",tint=MaterialTheme.colorScheme.primary)}
                        SearchTrackActions(onAdd={adding=hit.reference()},onShare={sharing=hit},canQueue=canPlay,onQueue={next->onEnqueue(nativeQueue(listOf(hit),hit,video=tab==SearchTab.CLIPS).first.first(),next)})
                    }
                }
                item{TextButton(onClick={submit(query)}){Text("Ver todos os resultados para "+term)}}
            }
            return
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            items(SearchTab.entries.filter{BuildConfig.FULL_FEATURES||it in setOf(SearchTab.ALL,SearchTab.LOCAL,SearchTab.OPEN)}){f->FilterChip(selected=tab==f,onClick={tab=f;editing=false;focusManager.clearFocus();keyboard?.hide()},label={Text(f.label)})}
        }
        if(busy||openBusy||nativeBusy||resolving!=null)LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let{Text(it,color=MaterialTheme.colorScheme.secondary,fontSize=12.sp)}
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=14.dp)){
            if(term.isBlank()&&tab!=SearchTab.LOCAL){
                if(tab!=SearchTab.ALL)item{
                    Text(tab.label,fontSize=21.sp,fontWeight=FontWeight.Bold)
                    Text(when(tab){
                        SearchTab.SONG->"Pesquise pelo nome da música ou do artista."
                        SearchTab.ARTIST->"Pesquise pelo nome do artista."
                        SearchTab.ALBUM->"Pesquise pelo nome do álbum ou do artista."
                        SearchTab.OPEN->"Pesquise áudios com licença aberta no Wikimedia Commons."
                        SearchTab.CLIPS->"Pesquise o nome de uma música ou artista para encontrar videoclipes."
                        else->"Digite para pesquisar."
                    },color=Color.LightGray,fontSize=13.sp)
                    OutlinedButton(onClick={focusRequester.requestFocus();keyboard?.show()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Search,null);Spacer(Modifier.width(8.dp));Text("Pesquisar "+tab.label.lowercase(java.util.Locale.ROOT))}
                }
                if(recent.isNotEmpty())item{
                    Row(verticalAlignment=Alignment.CenterVertically){Text("Pesquisas recentes",fontSize=19.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick={recent=emptyList();recentPrefs.edit().remove("queries").apply()}){Text("Limpar")}}
                    recent.take(6).forEach{value->Row(Modifier.fillMaxWidth().clickable{submit(value)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.History,null,tint=Color.LightGray);Text(value,Modifier.padding(start=12.dp))}}
                }
                if(tab==SearchTab.ALL)item{PremiumGenres(onSearch={submit(it);tab=SearchTab.ALL})}
                else if(tab in setOf(SearchTab.SONG,SearchTab.ARTIST,SearchTab.ALBUM,SearchTab.CLIPS))item{
                    Text("Explore nesta categoria",fontSize=18.sp,fontWeight=FontWeight.Bold)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(genres){genre->AssistChip(onClick={submit(genre)},label={Text(genre)})}}
                }
                else if(tab==SearchTab.OPEN&&offlineState.saved.isNotEmpty())item{TextButton(onClick=onOfflineOpen){Text("Ver áudios baixados")}}
            }else{
                activeGenre?.let{genre->item{
                    Text(genre.label,fontSize=24.sp,fontWeight=FontWeight.Bold)
                    Text("Seleção de artistas do gênero · sem misturar resultados genéricos",fontSize=12.sp,color=Color.LightGray)
                }}
                if(NativeMusicCatalog.enabled&&tab in setOf(SearchTab.ALL,SearchTab.SONG,SearchTab.CLIPS)){
                    item{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("NERO Music",fontWeight=FontWeight.Bold,fontSize=19.sp);Text(if(tab==SearchTab.CLIPS)"Clipes · catálogo NERO Music" else "Músicas · catálogo NERO Music",fontSize=11.sp,color=Color.LightGray)};IconButton(onClick={showSource=true}){Icon(Icons.Default.Info,"Informações sobre a fonte",tint=Color.LightGray)}}}
                    if(tab==SearchTab.ALL&&nativeResults.isNotEmpty())item{
                        val top=nativeResults.first()
                        Text("Resultado principal",fontSize=15.sp,fontWeight=FontWeight.Bold,color=Color.LightGray)
                        Card(onClick={playNative(top)},enabled=canPlay,modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
                            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                                NeroArtwork(model=top.thumbnail,contentDescription="Capa de "+top.title,contentScale=ContentScale.Crop,modifier=Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)))
                                Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(top.title,fontSize=18.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis,color=if(currentId==top.reference().id)MaterialTheme.colorScheme.primary else Color.Unspecified);Text(displayArtistName(top.artist),fontSize=12.sp,color=Color.LightGray)}
                                if(currentId==top.reference().id)IconButton(enabled=canPlay,onClick=onCurrentToggle){Icon(if(currentPlaying)Icons.Default.PauseCircle else Icons.Default.PlayCircle,if(currentPlaying)"Pausar" else "Continuar",tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(36.dp))}
                                else Icon(Icons.Default.PlayCircle,"Reproduzir",tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(36.dp))
                            }
                        }
                        val artists=nativeResults.map{it.artist.removeSuffix(" - Topic").trim()}.filter{it.isNotBlank()}.distinct().take(6)
                        if(artists.isNotEmpty()){
                            Text("Artistas encontrados",fontSize=14.sp,color=Color.LightGray,modifier=Modifier.padding(top=8.dp))
                            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(artists){artist->AssistChip(onClick={artistPage=artist},label={Text(displayArtistName(artist))})}}
                        }
                        Text("Músicas",fontSize=19.sp,fontWeight=FontWeight.Bold)
                    }
                    if(nativeError)item{TextButton(onClick={revision++}){Text("Falha na busca. Tentar novamente")}}
                    if(!nativeBusy&&!nativeError&&nativeResults.isEmpty()&&term.length>=3)item{Text(if(activeGenre!=null)"Nenhuma faixa da seleção disponível agora. Tente novamente." else "Nenhuma faixa encontrada.",color=Color.LightGray)}
                    items(if(tab==SearchTab.ALL)nativeResults.take(8)else nativeResults,key={"native:"+it.id}){hit->
                        Column(Modifier.fillMaxWidth().padding(vertical=4.dp)){
                            Row(Modifier.fillMaxWidth().clickable(enabled=canPlay&&resolving==null){playNative(hit)},verticalAlignment=Alignment.CenterVertically){
                            NeroArtwork(model=hit.thumbnail,contentDescription="Capa de "+hit.title,contentScale=ContentScale.Crop,modifier=Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)))
                            Column(Modifier.weight(1f).padding(horizontal=8.dp)){
                                Text(hit.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium,color=if(currentId==hit.reference().id&&currentVideo==(tab==SearchTab.CLIPS))MaterialTheme.colorScheme.primary else Color.Unspecified)
                                if(currentId==hit.reference().id&&currentVideo==(tab==SearchTab.CLIPS))Text(if(currentPlaying)"♫ Tocando agora" else "Em reprodução · pausada",fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
                                Text(displayArtistName(hit.artist),fontSize=12.sp,color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis)
                                if(tab==SearchTab.CLIPS)Text("Videoclipe",fontSize=11.sp,color=MaterialTheme.colorScheme.secondary)
                            }
                            IconButton(enabled=canPlay&&resolving==null,onClick={if(currentId==hit.reference().id&&currentVideo==(tab==SearchTab.CLIPS))onCurrentToggle()else playNative(hit)}){Icon(if(currentId==hit.reference().id&&currentVideo==(tab==SearchTab.CLIPS)&&currentPlaying)Icons.Default.Pause else Icons.Default.PlayArrow,if(currentId==hit.reference().id&&currentVideo==(tab==SearchTab.CLIPS)&&currentPlaying)"Pausar" else "Reproduzir no NERO",tint=MaterialTheme.colorScheme.primary)}
                            }
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically){
                            IconButton(onClick={onFavorite(hit.reference())}){Icon(if(hit.reference().id in onlineFavoriteIds)Icons.Default.Favorite else Icons.Default.FavoriteBorder,"Favoritar",tint=if(hit.reference().id in onlineFavoriteIds)MaterialTheme.colorScheme.primary else Color.LightGray)}
                            NativeDownloadIcon(hit,offlineState,onNativeSave,onOfflineOpen)
                            SearchTrackActions(onAdd={adding=hit.reference()},onShare={sharing=hit},canQueue=canPlay,onQueue={next->onEnqueue(nativeQueue(listOf(hit),hit,video=tab==SearchTab.CLIPS).first.first(),next)})
                            }
                        }
                    }
                    if(tab==SearchTab.ALL&&nativeResults.size>8)item{TextButton(onClick={tab=SearchTab.SONG}){Text("Ver todas as músicas")}}
                    if(tab==SearchTab.ALL&&moreArtist.isNotEmpty()){
                        item{Text("Mais de "+relatedArtist,fontSize=19.sp,fontWeight=FontWeight.Bold)}
                        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){items(moreArtist,key={it.id}){hit->
                            Card(onClick={playNative(hit)},enabled=canPlay,modifier=Modifier.width(136.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
                                Column(Modifier.padding(8.dp)){
                                    NeroArtwork(model=hit.thumbnail,contentDescription="Capa de "+hit.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
                                    Text(hit.title,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis,color=if(currentId==hit.reference().id&&currentVideo==(tab==SearchTab.CLIPS))MaterialTheme.colorScheme.primary else Color.Unspecified)
                                    if(currentId==hit.reference().id&&currentVideo==(tab==SearchTab.CLIPS))Text(if(currentPlaying)"Tocando agora" else "Pausada",fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
                                    SearchTrackActions(onAdd={adding=hit.reference()},onShare={sharing=hit},canQueue=canPlay,onQueue={next->onEnqueue(nativeQueue(listOf(hit),hit,video=tab==SearchTab.CLIPS).first.first(),next)})
                                }
                            }
                        }}}
                    }
                }
                if(tab==SearchTab.LOCAL||(tab==SearchTab.ALL&&activeGenre==null&&local.isNotEmpty())){
                    item{Text("No aparelho",fontWeight=FontWeight.Bold,fontSize=19.sp)}
                    val rows=if(tab==SearchTab.ALL)local.take(5)else local
                    if(rows.isEmpty())item{Text(if(term.isBlank())"Nenhuma música local disponível. Verifique a permissão de acesso ao áudio e os arquivos do aparelho." else "Nenhuma faixa local encontrada.",fontSize=12.sp,color=Color.LightGray)}
                    items(rows,key={it.id}){track->
                        Row(Modifier.fillMaxWidth().clickable(enabled=canPlay){if(currentId==track.id.toString())onOpenPlayer()else onPlay(track)},verticalAlignment=Alignment.CenterVertically){
                            LocalArtwork(track,Modifier.size(52.dp))
                            Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(track.title,maxLines=2,overflow=TextOverflow.Ellipsis,color=if(currentId==track.id.toString())MaterialTheme.colorScheme.primary else Color.Unspecified);Text(displayArtistName(track.artist),fontSize=12.sp,color=Color.LightGray)}
                            SearchTrackActions(onAdd={addingLocal=track},onShare=null,canQueue=canPlay,onQueue={next->onEnqueue(track.mediaItem(),next)})
                            IconButton(enabled=canPlay,onClick={if(currentId==track.id.toString())onCurrentToggle()else onPlay(track)}){Icon(if(currentId==track.id.toString()&&currentPlaying)Icons.Default.Pause else Icons.Default.PlayArrow,if(currentId==track.id.toString()&&currentPlaying)"Pausar" else "Reproduzir arquivo local",tint=MaterialTheme.colorScheme.primary)}
                        }
                    }
                    if(tab==SearchTab.ALL&&local.size>5)item{TextButton(onClick={tab=SearchTab.LOCAL}){Text("Ver todas as faixas locais")}}
                }
                if(NativeMusicCatalog.enabled&&tab in setOf(SearchTab.ALL,SearchTab.ARTIST,SearchTab.ALBUM)){
                    for((label,rows) in listOf("Artistas" to artists,"Álbuns" to albums)){
                        if(rows.isNotEmpty()){
                            item{Text(label,fontWeight=FontWeight.Bold,fontSize=19.sp)}
                            items(if(tab==SearchTab.ALL)rows.take(5)else rows,key={it.kind.name+it.url}){entry->
                                Card(onClick={if(entry.kind==NativeCollectionKind.ARTIST)artistPage=entry.title else albumPage=entry},modifier=Modifier.fillMaxWidth()){
                                    Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                                        NeroArtwork(model=entry.artwork,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                                        Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(entry.title,fontWeight=FontWeight.Medium);Text(displayArtistName(entry.credit),fontSize=12.sp,color=Color.LightGray)}
                                        Icon(Icons.Default.KeyboardArrowRight,"Abrir")
                                    }
                                }
                            }
                        }
                    }
                }
                if(tab==SearchTab.OPEN||(!NativeMusicCatalog.enabled&&tab==SearchTab.ALL)){
                    item{
                        Text("Áudio com licença aberta",fontSize=19.sp,fontWeight=FontWeight.Bold)
                        Text("Fonte: Wikimedia Commons",fontSize=12.sp,color=Color.LightGray)
                        offlineState.notice?.let{Text(it,fontSize=12.sp,color=Color.LightGray)}
                    }
                    if(openError)item{TextButton(onClick={revision++}){Text("Falha na consulta. Tentar novamente")}}
                    if(!openBusy&&!openError&&openResults.isEmpty())item{Text("Nenhum áudio compatível encontrado.",color=Color.LightGray)}
                    items(if(tab==SearchTab.ALL)openResults.take(5)else openResults,key={it.id}){audio->
                        val localAudio=offlineState.saved.firstOrNull{it.id==audio.id}
                        val downloaded=localAudio!=null
                        val downloading=offlineState.active?.id==audio.id
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface ,RoundedCornerShape(10.dp)).padding(10.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically){
                                Column(Modifier.weight(1f)){Text(audio.title);Text(displayArtistName(audio.artist),fontSize=12.sp,color=Color.LightGray);Text(audio.license,fontSize=11.sp,color=MaterialTheme.colorScheme.primary)}
                                SearchTrackActions(onAdd={adding=OnlineRef("commons:"+audio.id,audio.title,audio.artist,openAudio=audio)},onShare=null,canQueue=canPlay,onQueue={next->onEnqueue(if(localAudio!=null)localAudio.item(Uri.fromFile(audioFile(context,localAudio)))else audio.item(Uri.parse(audio.url)),next)})
                                IconButton(enabled=canPlay,onClick={onOpenPlay(if(localAudio!=null)localAudio.item(Uri.fromFile(audioFile(context,localAudio)))else audio.item(Uri.parse(audio.url)))}){Icon(Icons.Default.PlayArrow,"Reproduzir áudio autorizado")}
                            }
                            Row(verticalAlignment=Alignment.CenterVertically){
                                TextButton(onClick={openBrowser(context,Uri.parse(audio.source))}){Text("Fonte e licença")}
                                if(downloaded){
                                    Icon(Icons.Default.CheckCircle,"Disponível offline",tint=MaterialTheme.colorScheme.primary)
                                    Text("Baixada",color=MaterialTheme.colorScheme.primary)
                                }else TextButton(enabled=offlineState.active==null&&offlineState.nativeActive==null&&offlineState.batchTotal==0,onClick={onOfflineSave(audio)}){
                                    Icon(Icons.Default.Download,null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(if(downloading)"Baixando "+(offlineState.progress*100).toInt()+"%" else "Baixar")
                                }
                            }
                            if(downloading)LinearProgressIndicator(progress={offlineState.progress},modifier=Modifier.fillMaxWidth())
                        }
                    }
                }
                if(term.length<3&&tab!=SearchTab.LOCAL)item{Text("Digite pelo menos três caracteres para consultar o catálogo online.",fontSize=12.sp,color=Color.LightGray)}
            }
            if(currentId.isNotBlank()&&currentTitle.isNotBlank()&&tab in setOf(SearchTab.ALL,SearchTab.SONG)){
                item(key="current_playback_card"){
                    Text("Tocando agora",fontSize=19.sp,fontWeight=FontWeight.Bold)
                    Card(onClick=onOpenPlayer,modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant )){
                        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                            if(currentCover!=null)NeroArtwork(model=currentCover,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
                            else LocalArtwork(tracks.firstOrNull{it.id.toString()==currentId},Modifier.size(64.dp))
                            Column(Modifier.weight(1f).padding(horizontal=12.dp)){
                                Text(currentTitle,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text(currentArtist,fontSize=12.sp,color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(if(currentPlaying)"Em reprodução" else "Pausada",fontSize=11.sp,color=MaterialTheme.colorScheme.secondary)
                            }
                            IconButton(enabled=canPlay,onClick=onCurrentToggle){Icon(if(currentPlaying)Icons.Default.PauseCircle else Icons.Default.PlayCircle,if(currentPlaying)"Pausar" else "Continuar",tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(32.dp))}
                        }
                    }
                    TextButton(onClick=onOpenPlayer){Text("Abrir player e músicas relacionadas")}
                }
            }
        }
    }
}

@Composable
private fun SearchTrackActions(onAdd:()->Unit,onShare:(()->Unit)?,canQueue:Boolean,onQueue:(Boolean)->Unit){
    var expanded by remember{mutableStateOf(false)}
    Box{
        IconButton(onClick={expanded=true}){Icon(Icons.Default.MoreVert,"Mais opções da música",tint=MaterialTheme.colorScheme.primary)}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
            DropdownMenuItem(text={Text("Adicionar à playlist")},onClick={expanded=false;onAdd()},leadingIcon={Icon(Icons.Default.PlaylistAdd,null)})
            DropdownMenuItem(text={Text("Reproduzir a seguir")},enabled=canQueue,onClick={expanded=false;onQueue(true)},leadingIcon={Icon(Icons.Default.SkipNext,null)})
            DropdownMenuItem(text={Text("Adicionar à fila")},enabled=canQueue,onClick={expanded=false;onQueue(false)},leadingIcon={Icon(Icons.Default.QueueMusic,null)})
            if(onShare!=null)DropdownMenuItem(text={Text("Compartilhar música")},onClick={expanded=false;onShare()},leadingIcon={Icon(Icons.Default.Share,null)})
        }
    }
}
