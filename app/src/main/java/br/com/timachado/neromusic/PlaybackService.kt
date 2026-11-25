package br.com.timachado.neromusic

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.runBlocking
import java.io.IOException
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var eq: SessionEqualizer? = null
    private val timerScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // Resolve online references on the playback loader, never on the UI thread.
        // Only short-lived resolved addresses are cached in memory.
        val resolved=object:LinkedHashMap<String,Pair<android.net.Uri,Long>>(16,0.75f,true){
            override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,Pair<android.net.Uri,Long>>?)=size>16
        }
        val dataSource=ResolvingDataSource.Factory(DefaultDataSource.Factory(this)){spec->
            val uri=spec.uri
            if(uri.scheme!="nero-music")spec else{
                val id=uri.lastPathSegment.orEmpty()
                if(!NativeMusicCatalog.enabled||uri.host!="track"||!Regex("[A-Za-z0-9_-]{11}").matches(id))throw IOException("Fonte musical indisponível")
                val cached=synchronized(resolved){resolved[id]}
                val now=android.os.SystemClock.elapsedRealtime()
                val address=if(cached!=null&&now-cached.second in 0L..300000L)cached.first else try{
                    val item=runBlocking{NativeMusicCatalog.resolve(NativeMusicHit(id,"","",0L))}
                    val direct=item.localConfiguration?.uri?:throw IOException("Áudio indisponível")
                    synchronized(resolved){resolved[id]=direct to android.os.SystemClock.elapsedRealtime()}
                    direct
                }catch(e:Exception){throw IOException("Não foi possível carregar a faixa",e)}
                spec.withUri(address)
            }
        }
        val player = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSource)).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
        if(BuildConfig.DEBUG) {
            eq = SessionEqualizer(applicationContext)
            player.addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) { eq?.attach(audioSessionId) }
            })
            eq?.attach(player.audioSessionId)
        }
        var lastRecorded: String? = null
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if(events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) lastRecorded = null
                if(!p.isPlaying) return
                val id = p.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
                if(lastRecorded == id) return
                val title = p.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return
                val artist = p.mediaMetadata.artist?.toString().orEmpty()
                recordListening(applicationContext, id, title, artist)
                lastRecorded = id
            }
        })
        timerScope.launch {
            NeroSleepTimer.deadline.collectLatest { deadline ->
                if(deadline!=null){
                    delay((deadline-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                    if(NeroSleepTimer.deadline.value==deadline){player.pause();NeroSleepTimer.cancel()}
                }
            }
        }
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(activity).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        timerScope.cancel(); NeroSleepTimer.cancel()
        eq?.close(); eq = null
        session?.let { it.player.release(); it.release() }
        session = null
        super.onDestroy()
    }
}
