package br.com.timachado.neromusic

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.app.AlertDialog
import android.net.wifi.WifiManager
import android.widget.ScrollView
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import kotlinx.coroutines.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteButton
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.common.util.concurrent.ListenableFuture

class NeroCastOptions:OptionsProvider{
    override fun getCastOptions(context:Context):CastOptions=CastOptions.Builder()
        .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID).build()
    override fun getAdditionalSessionProviders(context:Context):List<SessionProvider>?=null
}

// Explicit single-track sender, not a replacement
// for the Media3 queue. Only validated publicly reachable open audio is sent.
class NeroCastActivity:FragmentActivity(){
    private var controller:MediaController?=null
    private var future:ListenableFuture<MediaController>?=null
    private var cast:CastContext?=null
    private var loaded:RemoteMediaClient?=null
    private var loadedUrl:String?=null
    private var sending=false
    private var scanning=false
    private var selectedDlna:NeroDlnaDevice?=null
    private var dlnaPlaying:Pair<NeroDlnaDevice,String>?=null
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var multicast:WifiManager.MulticastLock?=null
    private lateinit var castStatus:TextView
    private lateinit var targetStatus:TextView
    private val castListener=CastStateListener{state->
        if(::castStatus.isInitialized)castStatus.text=when(state){
            CastState.NO_DEVICES_AVAILABLE->"Google Cast: nenhum dispositivo compatível detectado."
            CastState.NOT_CONNECTED->"Google Cast: dispositivos disponíveis."
            CastState.CONNECTING->"Google Cast: conectando…"
            CastState.CONNECTED->"Google Cast: conectado."
            else->"Google Cast: verificando…"
        }
    }
    private lateinit var status:TextView
    private fun message(value:String){if(!isFinishing&&!isDestroyed)status.text=value}
    private fun client()=cast?.sessionManager?.currentCastSession?.takeIf{it.isConnected}?.remoteMediaClient
    private fun ours():RemoteMediaClient?=loaded?.takeIf{it===client()&&it.mediaInfo?.contentId==loadedUrl}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this,object:androidx.activity.OnBackPressedCallback(true){
            override fun handleOnBackPressed(){
                if(sending||dlnaPlaying!=null)message("Encerre a transmissão DLNA antes de sair.")
                else finish()
            }
        })
        val panel=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(32,32,32,32)
        }
        fun text(value:String,size:Float)=TextView(this).apply{
            text=value;textSize=size;setPadding(0,12,0,12)
        }
        fun button(label:String,action:()->Unit)=Button(this).apply{
            text=label;setOnClickListener{action()};panel.addView(this)
        }
        panel.addView(text("NERO Cast",24f))
        panel.addView(text("Google Cast ou DLNA/UPnP. Conecte a TV à mesma rede Wi-Fi. Apenas faixas de áudio livre compatíveis podem ser enviadas; não transmite arquivos locais ou faixas de serviços protegidos.",15f))
        status=text("Conectando ao player…",16f)
        panel.addView(status)
        castStatus=text("Google Cast: verificando…",14f)
        targetStatus=text("Destino: Google Cast",16f)
        panel.addView(castStatus)
        panel.addView(targetStatus)
        setContentView(ScrollView(this).apply{addView(panel)})
        try{
            cast=CastContext.getSharedInstance(this)
            val route=MediaRouteButton(this)
            panel.addView(route,LinearLayout.LayoutParams(120,100))
            CastButtonFactory.setUpMediaRouteButton(this,route)
            cast?.addCastStateListener(castListener)
            cast?.let{castListener.onCastStateChanged(it.castState)}
        }catch(_:Exception){message("Google Cast indisponível neste aparelho.")}
        panel.addView(text("Smart TVs LG e Samsung: procure primeiro por DLNA. Para espelhar a tela, use a conexão sem fio do próprio Android.",14f))
        button("Buscar TVs LG / Samsung / DLNA"){discoverDlna()}
        button("Espelhar tela · Miracast / Smart View"){openMirrorSettings()}
        button("Ajuda para LG e Samsung"){showTvHelp()}
        button("Usar Google Cast"){
            if(!sending&&dlnaPlaying==null){selectedDlna=null;targetStatus.text="Destino: Google Cast"}
            else message("Encerre a transmissão atual antes de trocar.")
        }
        button("Transmitir faixa atual"){send()}
        button("Pausar na TV"){if(dlnaPlaying!=null)dlnaControl("Pause") else ours()?.pause()?:message("Nenhuma transmissão NERO ativa.")}
        button("Continuar na TV"){if(dlnaPlaying!=null)dlnaControl("Play") else ours()?.play()?:message("Nenhuma transmissão NERO ativa.")}
        button("Encerrar transmissão"){
            if(dlnaPlaying!=null)dlnaControl("Stop")
            else if(!sending){stopOwn();message("Transmissão encerrada. Retome a música no player quando quiser.")}
        }
        button("Voltar ao NERO"){if(sending||dlnaPlaying!=null)message("Encerre a transmissão DLNA antes de sair.") else finish()}
        val request=MediaController.Builder(this,SessionToken(this,ComponentName(this,PlaybackService::class.java))).buildAsync()
        future=request
        request.addListener({
            if(!isDestroyed&&!isFinishing){
                try{controller=request.get();message("Escolha o destino Google Cast ou busque TVs DLNA.")}catch(_:Exception){message("Player indisponível.")}
            }
        },ContextCompat.getMainExecutor(this))
    }
    override fun onStart(){
        super.onStart()
        runCatching{
            multicast=(applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("nero-google-cast").apply{setReferenceCounted(false);acquire()}
        }
    }
    override fun onStop(){
        runCatching{multicast?.let{if(it.isHeld)it.release()}}
        multicast=null
        super.onStop()
    }
    private fun openMirrorSettings(){
        if(sending||dlnaPlaying!=null||ours()!=null){
            message("Encerre a transmissão atual antes de espelhar.");return
        }
        try{
            startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
            message("Escolha a TV nas configurações do Android. A conexão depende do suporte do celular e da TV.")
        }catch(_:Exception){
            message("Abra as configurações rápidas do celular e procure Transmitir, Smart View ou Smart Connect.")
        }
    }
    private fun showTvHelp(){
        AlertDialog.Builder(this).setTitle("Conectar sua Smart TV")
            .setMessage("LG webOS: abra Screen Share para espelhamento, ou habilite o compartilhamento de mídia quando disponível.\n\nSamsung: habilite Screen Mirroring ou o acesso de dispositivos externos na TV. Em celulares Galaxy, procure Smart View.\n\nMotorola: quando disponível, use Smart Connect para localizar telas Miracast.\n\nDLNA transmite somente áudio compatível com o receptor. Miracast espelha a tela pelo sistema; não é um transmissor independente do NERO. Nem todos os modelos suportam os mesmos protocolos.")
            .setPositiveButton("Entendi",null).show()
    }
    private fun discoverDlna(){
        if(scanning||sending||dlnaPlaying!=null||ours()!=null){message("Encerre a transmissão atual antes de buscar.");return}
        scanning=true;message("Buscando TVs e caixas de som DLNA na rede Wi-Fi…")
        scope.launch{
            try{
                val devices=withContext(Dispatchers.IO){NeroDlna.discover(applicationContext)}
                if(isDestroyed||isFinishing)return@launch
                if(devices.isEmpty()){
                    message("Nenhum DLNA encontrado. Ative o receptor de mídia na TV; verifique Wi-Fi de convidados, isolamento de dispositivos e VPN.")
                }else{
                    message("Dispositivos DLNA encontrados: "+devices.size)
                    AlertDialog.Builder(this@NeroCastActivity).setTitle("Escolha um dispositivo DLNA")
                        .setItems(devices.map{it.name+" ("+it.endpoint.host+")"}.toTypedArray()){_,i->
                            selectedDlna=devices[i];targetStatus.text="Destino DLNA: "+devices[i].name
                            message("Dispositivo selecionado. Escolha uma faixa compatível e transmita.")
                        }.setNegativeButton("Cancelar",null).show()
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){message("Busca DLNA indisponível. Confira se o Wi-Fi está conectado.")}
            finally{scanning=false}
        }
    }
    private fun dlnaControl(action:String){
        if(sending)return
        val active=dlnaPlaying?:return
        sending=true
        scope.launch{
            try{
                withContext(Dispatchers.IO){NeroDlna.control(applicationContext,active.first,active.second,action)}
                if(action=="Stop")dlnaPlaying=null
                message(when(action){"Pause"->"Transmissão DLNA pausada.";"Play"->"Transmissão DLNA retomada.";else->"Transmissão encerrada."})
            }catch(e:CancellationException){throw e}catch(_:Exception){if(action=="Stop")dlnaPlaying=null;message("A TV não confirmou o comando. Confira a reprodução diretamente na TV.")}
            finally{sending=false}
        }
    }
    private fun send(){
        if(sending)return
        val player=controller?:run{message("Player indisponível.");return}
        val item=player.currentMediaItem
        val track=item?.let(::openRefFromItem)?.openAudio
        if(track==null||!allowed(track)||track.mime !in setOf("audio/mpeg","audio/flac","audio/wav","audio/ogg")){
            message("Escolha uma faixa de áudio livre compatível no NERO.");return
        }
        if(!player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)){message("Não foi possível pausar o player local.");return}
        val dlna=selectedDlna
        if(dlna!=null){
            if(scanning||ours()!=null||dlnaPlaying!=null){message("Encerre a transmissão anterior primeiro.");return}
            sending=true;message("Enviando faixa à TV por DLNA…")
            scope.launch{
                try{
                    withContext(Dispatchers.IO){NeroDlna.play(applicationContext,dlna,track.url,track.title,track.artist,track.mime)}
                    dlnaPlaying=dlna to track.url
                    if(player.currentMediaItem==item)player.pause()
                    message("Faixa enviada: "+track.title+" • "+dlna.name)
                }catch(e:CancellationException){throw e}catch(_:Exception){message("A TV não confirmou a reprodução. O formato pode não ser compatível.")}
                finally{sending=false}
            }
            return
        }
        val remote=client()?:run{message("Selecione uma TV primeiro.");return}
        try{
            val metadata=MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply{
                putString(MediaMetadata.KEY_TITLE,track.title)
                putString(MediaMetadata.KEY_ARTIST,track.artist)
            }
            val media=MediaInfo.Builder(track.url).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(track.mime).setMetadata(metadata).build()
            player.pause()
            sending=true
            loaded=remote;loadedUrl=track.url
            message("Enviando faixa à TV…")
            remote.load(MediaLoadRequestData.Builder().setMediaInfo(media).setAutoplay(true)
                .setCurrentTime(player.currentPosition.coerceAtLeast(0L)).build()).setResultCallback{result->
                sending=false
                if(isFinishing||isDestroyed){
                    if(result.status.isSuccess&&remote.mediaInfo?.contentId==track.url)remote.stop()
                }else if(result.status.isSuccess)message("Transmitindo: "+track.title)
                else{loaded=null;loadedUrl=null;message("A TV não conseguiu carregar a faixa. Retome no celular manualmente.")}
            }
        }catch(_:Exception){sending=false;message("Falha ao transmitir. Retome no celular manualmente.")}
    }
    private fun stopOwn(){
        runCatching{ours()?.stop()}
        loaded=null;loadedUrl=null
    }
    override fun onDestroy(){
        // This preview intentionally stops its own track when leaving.
        stopOwn()
        cast?.removeCastStateListener(castListener)
        scope.cancel()
        future?.let{MediaController.releaseFuture(it)}
        future=null;controller=null
        super.onDestroy()
    }
}
