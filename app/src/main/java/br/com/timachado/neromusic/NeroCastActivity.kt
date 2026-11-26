package br.com.timachado.neromusic

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.View
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

// Isolated debug-only sender: explicit single-track load, not a replacement
// for the Media3 queue. Only validated publicly reachable open audio is sent.
class NeroCastActivity:FragmentActivity(){
    private var controller:MediaController?=null
    private var future:ListenableFuture<MediaController>?=null
    private var cast:CastContext?=null
    private var loaded:RemoteMediaClient?=null
    private var loadedUrl:String?=null
    private var sending=false
    private lateinit var status:TextView
    private fun message(value:String){if(!isFinishing&&!isDestroyed)status.text=value}
    private fun client()=cast?.sessionManager?.currentCastSession?.takeIf{it.isConnected}?.remoteMediaClient
    private fun ours():RemoteMediaClient?=loaded?.takeIf{it===client()&&it.mediaInfo?.contentId==loadedUrl}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
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
        panel.addView(text("Selecione uma TV na mesma rede e transmita a faixa atual de áudio livre. A fila do celular fica pausada; arquivos locais e o catálogo experimental não são transmitidos.",15f))
        status=text("Conectando ao player…",16f)
        panel.addView(status)
        setContentView(panel)
        try{
            cast=CastContext.getSharedInstance(this)
            val route=MediaRouteButton(this)
            panel.addView(route,LinearLayout.LayoutParams(120,100))
            CastButtonFactory.setUpMediaRouteButton(this,route)
        }catch(_:Exception){message("Google Cast indisponível neste aparelho.")}
        button("Transmitir faixa atual"){send()}
        button("Pausar na TV"){ours()?.pause()?:message("Nenhuma transmissão NERO ativa.")}
        button("Continuar na TV"){ours()?.play()?:message("Nenhuma transmissão NERO ativa.")}
        button("Encerrar transmissão"){
            stopOwn()
            message("Transmissão encerrada. Retome a música no player quando quiser.")
        }
        button("Voltar ao NERO"){finish()}
        val request=MediaController.Builder(this,SessionToken(this,ComponentName(this,PlaybackService::class.java))).buildAsync()
        future=request
        request.addListener({
            if(!isDestroyed&&!isFinishing){
                try{controller=request.get();message(if(cast!=null)"Escolha uma TV pelo botão Cast." else "Google Cast indisponível.")}catch(_:Exception){message("Player indisponível.")}
            }
        },ContextCompat.getMainExecutor(this))
    }
    private fun send(){
        if(sending)return
        val player=controller?:run{message("Player indisponível.");return}
        val item=player.currentMediaItem
        val track=item?.let(::openRefFromItem)?.openAudio
        if(track==null||!allowed(track)||track.mime !in setOf("audio/mpeg","audio/flac","audio/wav","audio/ogg")){
            message("Escolha uma faixa de áudio livre compatível no NERO.");return
        }
        val remote=client()?:run{message("Selecione uma TV primeiro.");return}
        if(!player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)){message("Não foi possível pausar o player local.");return}
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
        future?.let{MediaController.releaseFuture(it)}
        future=null;controller=null
        super.onDestroy()
    }
}
