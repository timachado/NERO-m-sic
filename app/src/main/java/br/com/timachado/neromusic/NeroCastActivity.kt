package br.com.timachado.neromusic

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.app.AlertDialog
import android.net.wifi.WifiManager
import android.widget.ScrollView
import android.view.View
import kotlinx.coroutines.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouteSelector
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
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
    private lateinit var devicesPanel:LinearLayout
    private lateinit var controls:LinearLayout
    private var router:MediaRouter?=null
    private var selector:MediaRouteSelector?=null
    private var dlnaDevices:List<NeroDlnaDevice> = emptyList()
    private val routeCallback=object:MediaRouter.Callback(){
        override fun onRouteAdded(router:MediaRouter,route:MediaRouter.RouteInfo){renderDevices()}
        override fun onRouteRemoved(router:MediaRouter,route:MediaRouter.RouteInfo){renderDevices()}
        override fun onRouteChanged(router:MediaRouter,route:MediaRouter.RouteInfo){renderDevices()}
    }
    private var castPickerJob:Job?=null
    private lateinit var targetStatus:TextView
    private lateinit var status:TextView
    private fun message(value:String){
        if(!isFinishing&&!isDestroyed){
            status.text=value
            if(::controls.isInitialized)controls.visibility=if(dlnaPlaying!=null||ours()!=null&&!sending)View.VISIBLE else View.GONE
        }
    }
    private fun client()=cast?.sessionManager?.currentCastSession?.takeIf{it.isConnected}?.remoteMediaClient
    private fun ours():RemoteMediaClient?=loaded?.takeIf{it===client()&&it.mediaInfo?.contentId==loadedUrl}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this,object:androidx.activity.OnBackPressedCallback(true){
            override fun handleOnBackPressed(){
                if(sending||dlnaPlaying!=null||ours()!=null)message("Desconecte a transmissão antes de sair.")
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
        panel.addView(text("Transmitir para dispositivo",24f))
        status=text("Procurando dispositivos na rede Wi-Fi…",16f)
        panel.addView(status)
        targetStatus=text("",16f)
        panel.addView(targetStatus)
        devicesPanel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        panel.addView(devicesPanel)
        controls=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE}
        panel.addView(controls)
        fun controlButton(label:String,action:()->Unit){
            controls.addView(Button(this).apply{text=label;setOnClickListener{action()}})
        }
        setContentView(ScrollView(this).apply{addView(panel)})
        try{
            cast=CastContext.getSharedInstance(this)
            router=MediaRouter.getInstance(this)
            selector=cast?.mergedSelector
        }catch(_:Exception){router=null;selector=null}
        controlButton("Pausar"){if(dlnaPlaying!=null)dlnaControl("Pause") else ours()?.pause()}
        controlButton("Continuar"){if(dlnaPlaying!=null)dlnaControl("Play") else ours()?.play()}
        controlButton("Desconectar"){
            if(dlnaPlaying!=null)dlnaControl("Stop")
            else if(!sending){stopOwn();message("Transmissão encerrada.")}
        }
        button("Buscar novamente"){discoverDlna()}
        button("Espelhar tela (Android)"){openMirrorSettings()}
        button("Voltar ao NERO"){if(sending||dlnaPlaying!=null||ours()!=null)message("Desconecte a transmissão antes de sair.") else finish()}
        val request=MediaController.Builder(this,SessionToken(this,ComponentName(this,PlaybackService::class.java))).buildAsync()
        future=request
        request.addListener({
            if(!isDestroyed&&!isFinishing){
                try{controller=request.get();discoverDlna()}catch(_:Exception){message("Player indisponível.")}
            }
        },ContextCompat.getMainExecutor(this))
    }
    override fun onStart(){
        super.onStart()
        selector?.let{router?.addCallback(it,routeCallback,MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)}
        renderDevices()
        runCatching{
            multicast=(applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("nero-google-cast").apply{setReferenceCounted(false);acquire()}
        }
    }
    override fun onStop(){
        router?.removeCallback(routeCallback)
        runCatching{multicast?.let{if(it.isHeld)it.release()}}
        multicast=null
        super.onStop()
    }
    private fun openMirrorSettings(){
        castPickerJob?.cancel()
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
    private fun discoverDlna(){
        if(scanning||sending||dlnaPlaying!=null||ours()!=null)return
        castPickerJob?.cancel()
        selectedDlna=null
        targetStatus.text=""
        dlnaDevices=emptyList()
        renderDevices()
        scanning=true;message("Procurando dispositivos na rede Wi-Fi…")
        scope.launch{
            try{
                val devices=withContext(Dispatchers.IO){NeroDlna.discover(applicationContext)}
                if(isDestroyed||isFinishing)return@launch
                scanning=false
                dlnaDevices=devices
                renderDevices()
                message(if(devices.isEmpty()&&castRoutes().isEmpty())"Nenhum dispositivo disponível encontrado." else "Selecione uma TV para transmitir.")
            }catch(e:CancellationException){throw e}catch(_:Exception){message("Não foi possível procurar TVs na rede. Confira o Wi-Fi.")}
            finally{scanning=false}
        }
    }
    private fun castRoutes():List<MediaRouter.RouteInfo>{
        val filter=selector?:return emptyList()
        return router?.routes.orEmpty().filter{!it.isDefault&&it.isEnabled&&it.matchesSelector(filter)}
    }
    private fun renderDevices(){
        if(!::devicesPanel.isInitialized||isFinishing||isDestroyed)return
        devicesPanel.removeAllViews()
        val routes=castRoutes()
        fun choice(label:String,action:()->Unit){
            devicesPanel.addView(Button(this).apply{
                text=label;isAllCaps=false
                setOnClickListener{if(controller!=null&&!sending&&dlnaPlaying==null&&ours()==null)action()}
            })
        }
        routes.forEach{route->
            choice(route.name.toString()+" · Cast"){
                castPickerJob?.cancel()
                selectedDlna=null
                targetStatus.text=route.name.toString()
                val item=controller?.currentMediaItem
                val track=item?.let(::openRefFromItem)?.openAudio
                if(track==null||!allowed(track)||track.mime !in setOf("audio/mpeg","audio/flac","audio/wav","audio/ogg")){
                    send();return@choice
                }
                route.select()
                message("Conectando à TV…")
                castPickerJob=scope.launch{
                    val connected=withTimeoutOrNull(20000L){
                        while(client()==null||router?.selectedRoute?.id!=route.id)delay(300L)
                        true
                    }==true
                    if(connected&&!sending&&selectedDlna==null)send()
                    else if(!connected)message("Não foi possível conectar à TV.")
                }
            }
        }
        dlnaDevices.forEach{device->
            choice(device.name){
                castPickerJob?.cancel()
                selectedDlna=device
                targetStatus.text=device.name
                send()
            }
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
            if(item==null){message("Inicie uma música no NERO antes de transmitir.");return}
            message("A TV foi localizada, mas esta faixa não oferece uma fonte compatível para transmissão direta.")
            AlertDialog.Builder(this).setTitle("Faixa incompatível com transmissão direta")
                .setMessage("O NERO encontrou o dispositivo, mas não pode enviar esta faixa como áudio DLNA ou Google Cast. Se seu celular e sua TV oferecerem espelhamento, você pode utilizar a conexão nativa do Android.")
                .setPositiveButton("Abrir espelhamento"){_,_->openMirrorSettings()}
                .setNegativeButton("Continuar no celular",null).show()
            return
        }
        if(!player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)){message("Não foi possível pausar o player local.");return}
        val dlna=selectedDlna
        if(dlna!=null){
            if(ours()!=null||dlnaPlaying!=null){message("Encerre a transmissão anterior primeiro.");return}
            sending=true;message("Enviando faixa à TV por DLNA…")
            scope.launch{
                try{
                    val confirmed=withContext(Dispatchers.IO){
                        NeroDlna.play(applicationContext,dlna,track.url,track.title,track.artist,track.mime)
                        NeroDlna.awaitPlaying(applicationContext,dlna,track.url)
                    }
                    if(confirmed&&player.currentMediaItem==item){
                        dlnaPlaying=dlna to track.url
                        player.pause()
                        message("TV confirmou a reprodução: "+track.title+" • "+dlna.name)
                    }else{
                        withContext(Dispatchers.IO){runCatching{NeroDlna.control(applicationContext,dlna,track.url,"Stop")}}
                        message("A TV não confirmou esta faixa. O áudio do celular foi preservado.")
                    }
                }catch(e:CancellationException){throw e}catch(_:Exception){
                    withContext(Dispatchers.IO){runCatching{NeroDlna.control(applicationContext,dlna,track.url,"Stop")}}
                    message("A TV não confirmou a reprodução. Verifique o formato e o acesso à mídia. O celular não foi pausado.")
                }
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
            sending=true
            loaded=remote;loadedUrl=track.url
            message("Enviando faixa à TV…")
            remote.load(MediaLoadRequestData.Builder().setMediaInfo(media).setAutoplay(true)
                .setCurrentTime(player.currentPosition.coerceAtLeast(0L)).build()).setResultCallback{result->
                if(isFinishing||isDestroyed){
                    sending=false
                    if(result.status.isSuccess&&remote.mediaInfo?.contentId==track.url)remote.stop()
                }else if(result.status.isSuccess){
                    scope.launch{
                        try{
                            val confirmed=withTimeoutOrNull(12000L){
                                while(client()===remote){
                                    if(remote.playerState==MediaStatus.PLAYER_STATE_PLAYING&&remote.mediaInfo?.contentId==track.url)return@withTimeoutOrNull true
                                    delay(400L)
                                }
                                false
                            }==true
                            if(confirmed&&player.currentMediaItem==item){
                                player.pause()
                                sending=false
                                message("TV confirmou a reprodução: "+track.title)
                            }else{
                                stopOwn()
                                message("A TV não confirmou a faixa. O áudio do celular foi preservado.")
                            }
                        }finally{sending=false}
                    }
                }else{
                    sending=false;loaded=null;loadedUrl=null
                    message("A TV não conseguiu carregar a faixa. O áudio do celular foi preservado.")
                }
            }
        }catch(_:Exception){sending=false;message("Falha ao transmitir. O áudio do celular foi preservado.")}
    }
    private fun stopOwn(){
        runCatching{ours()?.stop()}
        loaded=null;loadedUrl=null
    }
    override fun onDestroy(){
        // This preview intentionally stops its own track when leaving.
        stopOwn()
        router?.removeCallback(routeCallback)
        scope.cancel()
        future?.let{MediaController.releaseFuture(it)}
        future=null;controller=null
        super.onDestroy()
    }
}
