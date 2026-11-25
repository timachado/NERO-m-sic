package br.com.timachado.neromusic

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var eq: SessionEqualizer? = null
    private var crossfade: NeroLocalCrossfade? = null
    private var replaySampler:NeroReplaySampler?=null
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
                if(!NativeMusicCatalog.enabled||uri.host !in setOf("track","video")||!Regex("[A-Za-z0-9_-]{11}").matches(id))throw IOException("Fonte musical indisponível")
                val video=uri.host=="video"
                val quality=if(video)uri.getQueryParameter("quality")?.toIntOrNull()?.takeIf{it in listOf(0,360,480,720,1080)}?:0 else 0
                val cacheKey=(if(video)"video:"+quality+":"else "audio:")+id
                val cached=synchronized(resolved){resolved[cacheKey]}
                val now=android.os.SystemClock.elapsedRealtime()
                val local=if(!video)nativeOfflineCopy(applicationContext,id)?.item(applicationContext)?.localConfiguration?.uri else null
                val address=if(local!=null)local else if(cached!=null&&now-cached.second in 0L..300000L)cached.first else try{
                    val item=runBlocking{val hit=NativeMusicHit(id,"","",0L);if(video)NativeMusicCatalog.resolveVideo(hit,quality)else NativeMusicCatalog.resolve(hit)}
                    val direct=item.localConfiguration?.uri?:throw IOException("Áudio indisponível")
                    synchronized(resolved){resolved[cacheKey]=direct to android.os.SystemClock.elapsedRealtime()}
                    direct
                }catch(e:Exception){throw IOException("Não foi possível carregar a faixa",e)}
                spec.withUri(address)
            }
        }
        NeroAudioSettings.load(applicationContext)
        val renderers=object:DefaultRenderersFactory(this){
            override fun buildAudioSink(context:android.content.Context,enableFloatOutput:Boolean,enableAudioTrackPlaybackParams:Boolean):AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(NeroLevelingProcessor()))
                    .build()
        }
        val player = ExoPlayer.Builder(this,renderers).setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSource)).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
        if(BuildConfig.DEBUG) {
            crossfade=NeroLocalCrossfade(applicationContext,player,timerScope)
            eq = SessionEqualizer(applicationContext)
            player.addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) { eq?.attach(audioSessionId) }
            })
            eq?.attach(player.audioSessionId)
        }
        replaySampler=NeroReplaySampler(applicationContext,player,timerScope)
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
        var autoplayJob:Job?=null
        // At most one extension per end event. A user pause, replacement or repeat
        // change invalidates the pending extension.
        player.addListener(object:Player.Listener{
            override fun onPlaybackStateChanged(state:Int){
                if(state!=Player.STATE_ENDED){autoplayJob?.cancel();return}
                if(player.currentMediaItem?.isNeroVideo()==true||!NativeMusicCatalog.enabled||!NeroAutoplay.enabled(applicationContext)||!player.playWhenReady||
                    player.repeatMode!=Player.REPEAT_MODE_OFF||NeroSleepTimer.deadline.value!=null)return
                val seed=player.currentMediaItem?.let(::youtubeRefFromItem)?.let(::musicHitFromRef)?:return
                val ids=(0 until player.mediaItemCount).map{player.getMediaItemAt(it).mediaId}
                val excluded=(ids+history(applicationContext).map{it.id}).map{it.removePrefix("youtube:")}.toSet()
                autoplayJob?.cancel()
                autoplayJob=timerScope.launch{
                    try{
                        val next=onlineRadio(seed,excluded).take(10)
                        if(next.isEmpty()||!NeroAutoplay.enabled(applicationContext)||!player.playWhenReady||
                            player.playbackState!=Player.STATE_ENDED||player.repeatMode!=Player.REPEAT_MODE_OFF||
                            NeroSleepTimer.deadline.value!=null||
                            (0 until player.mediaItemCount).map{player.getMediaItemAt(it).mediaId}!=ids)return@launch
                        // Keep a bounded timeline, including the last track for Previous.
                        player.setMediaItems((listOf(seed)+next).map{it.queueItem()},1,0L)
                        player.prepare()
                        player.play()
                    }catch(e:CancellationException){throw e}catch(_:Exception){/* End normally if no more tracks can be found. */}
                }
            }
            override fun onPlayWhenReadyChanged(playWhenReady:Boolean,reason:Int){
                if(!playWhenReady)autoplayJob?.cancel()
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
        replaySampler?.close();replaySampler=null
        crossfade?.close();crossfade=null
        timerScope.cancel(); NeroSleepTimer.cancel()
        eq?.close(); eq = null
        session?.let { it.player.release(); it.release() }
        session = null
        super.onDestroy()
    }
}
