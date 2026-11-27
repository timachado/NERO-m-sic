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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import java.io.IOException
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private var eq: SessionEqualizer? = null
    private var favoriteListener:android.content.SharedPreferences.OnSharedPreferenceChangeListener?=null
    private var replaySampler:NeroReplaySampler?=null
    private val timerScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val notificationProvider=androidx.media3.session.DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.ic_nero_notification)
        setMediaNotificationProvider(notificationProvider)
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
                val economical=!video&&economicalStreaming(applicationContext)
                val cacheKey=(if(video)"video:"+quality+":"else "audio:"+economical+":")+id
                val cached=synchronized(resolved){resolved[cacheKey]}
                val now=android.os.SystemClock.elapsedRealtime()
                val local=if(!video)nativeOfflineCopy(applicationContext,id)?.item(applicationContext)?.localConfiguration?.uri else null
                val address=if(local!=null)local else if(cached!=null&&now-cached.second in 0L..300000L)cached.first else try{
                    val item=runBlocking{val hit=NativeMusicHit(id,"","",0L);if(video)NativeMusicCatalog.resolveVideo(hit,quality)else NativeMusicCatalog.resolve(hit,economical=economical)}
                    val direct=item.localConfiguration?.uri?:throw IOException("Áudio indisponível")
                    synchronized(resolved){resolved[cacheKey]=direct to android.os.SystemClock.elapsedRealtime()}
                    direct
                }catch(e:Exception){throw IOException("Não foi possível carregar a faixa",e)}
                if(BuildConfig.NERO_TV_EXPERIMENT)NeroWebVideoCaster.rememberSource("youtube:"+id,video,address.toString())
                spec.withUri(address)
            }
        }
        // Recovery: use the original Media3 renderer and audio sink.
        NeroPlaybackAccess.update(null,false)
        val player = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSource)).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            shuffleModeEnabled=true
            repeatMode=Player.REPEAT_MODE_OFF
        }
        TogetherFollowerPlayback.attach(player)
        if(BuildConfig.FULL_FEATURES) {
            eq = SessionEqualizer(applicationContext)
            player.addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) { eq?.attach(audioSessionId) }
                override fun onPlaybackStateChanged(playbackState:Int) {
                    if(playbackState==Player.STATE_READY) eq?.attach(player.audioSessionId)
                }
                override fun onIsPlayingChanged(isPlaying:Boolean) {
                    if(isPlaying) eq?.attach(player.audioSessionId)
                }
            })
            eq?.attach(player.audioSessionId)
        }
        replaySampler=NeroReplaySampler(applicationContext,player,timerScope)
        player.addListener(object:Player.Listener{
            override fun onPlayerError(error:androidx.media3.common.PlaybackException){
                synchronized(resolved){resolved.clear()}
                if(BuildConfig.NERO_TV_EXPERIMENT)NeroWebVideoCaster.clearSources()
                // Do not log URLs, credentials, titles or identifiers.
                android.util.Log.w("NeroPlayback", "Playback error: "+error.errorCodeName+" / "+error.cause?.javaClass?.simpleName)
            }
        })
        var lastRecorded: String? = null
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if(events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) lastRecorded = null
                if(events.contains(Player.EVENT_IS_PLAYING_CHANGED)||events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)||events.contains(Player.EVENT_POSITION_DISCONTINUITY))NeroResumeStore.save(applicationContext,p)
                if(!p.isPlaying) return
                val id = p.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
                if(lastRecorded == id) return
                val title = p.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return
                val artist = p.mediaMetadata.artist?.toString().orEmpty()
                val owner=NeroSupabase.activeUid.value
                if(!NeroSupabase.identityReady.value)return
                if(owner==null)recordListening(applicationContext,id,title,artist)
                else{
                    val ref=p.currentMediaItem?.let{openRefFromItem(it)?:youtubeRefFromItem(it)}
                    if(ref!=null&&portableRef(ref)){
                        val at=System.currentTimeMillis()
                        NeroLocalRecent.record(applicationContext,owner,ref,at)
                        if(NeroAccountSync.historyEnabled(applicationContext,owner))timerScope.launch{
                            try{NeroAccountSync.recordHistory(applicationContext,owner,ref,at)}
                            catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){}
                        }
                    }
                }
                lastRecorded = id
            }
        })
        var autoplayJob:Job?=null
        // At most one extension per end event. A user pause, replacement or repeat
        // change invalidates the pending extension.
        player.addListener(object:Player.Listener{
            override fun onPlaybackStateChanged(state:Int){
                if(state!=Player.STATE_ENDED){autoplayJob?.cancel();return}
                if(TogetherFollowerPlayback.owns(player)||player.currentMediaItem?.isNeroVideo()==true||!NativeMusicCatalog.enabled||!NeroAutoplay.enabled(applicationContext)||!player.playWhenReady||
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
        session = MediaLibrarySession.Builder(this, player, NeroCarLibrary(applicationContext,timerScope)).setSessionActivity(activity).build()
        var lastLayout=emptyList<androidx.media3.session.CommandButton>()
        fun refreshLayout(){
            val layout=NeroMediaActions.layout(applicationContext,player)
            if(layout!=lastLayout){lastLayout=layout;session?.setCustomLayout(layout)}
        }
        favoriteListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->
            if(key=="favorites"||key=="online_favorite_revision")refreshLayout()
        }
        getSharedPreferences("nero_library",0).registerOnSharedPreferenceChangeListener(favoriteListener)
        refreshLayout()
        player.addListener(object:Player.Listener{
            override fun onEvents(p:Player,events:Player.Events){
                if(events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)||events.contains(Player.EVENT_REPEAT_MODE_CHANGED)||events.contains(Player.EVENT_TIMELINE_CHANGED))refreshLayout()
            }
        })
        fun applyPlaybackPolicy(){
            if(!NeroPlaybackAccess.allowed()){
                if(!player.shuffleModeEnabled)player.shuffleModeEnabled=true
                if(player.repeatMode!=Player.REPEAT_MODE_OFF)player.repeatMode=Player.REPEAT_MODE_OFF
            }
            session?.let{active->
                active.connectedControllers.forEach{controller->
                    active.setAvailableCommands(controller,NeroPlaybackAccess.sessionCommands(),NeroPlaybackAccess.commands())
                }
            }
            refreshLayout()
            NeroWidgetState.refresh(applicationContext,true)
        }
        // Recheck on identity changes and periodically while the media session exists.
        // A failed verification leaves playback available in Free mode.
        timerScope.launch{
            combine(NeroSupabase.activeUid,NeroSupabase.identityReady){uid,ready->uid to ready}
                .collectLatest{(uid,ready)->
                    NeroPlaybackAccess.update(null,false)
                    applyPlaybackPolicy()
                    if(ready&&uid!=null){
                        while(true){
                            val verified=verifiedPremium(applicationContext,uid)
                            if(!NeroSupabase.identityReady.value||NeroSupabase.activeUid.value!=uid)break
                            NeroPlaybackAccess.update(uid,verified)
                            applyPlaybackPolicy()
                            delay(40_000L)
                        }
                    }
                }
        }
        NeroWidgetState.attach(applicationContext,player)
        player.addListener(object:Player.Listener{
            override fun onEvents(p:Player,events:Player.Events){
                if(!NeroPlaybackAccess.allowed()){
                    if(!p.shuffleModeEnabled)p.shuffleModeEnabled=true
                    if(p.repeatMode!=Player.REPEAT_MODE_OFF)p.repeatMode=Player.REPEAT_MODE_OFF
                }
                NeroWidgetState.refresh(applicationContext)
            }
        })
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session.takeIf{controllerInfo.uid==android.os.Process.myUid()||controllerInfo.isTrusted}
    // Dismissing the task is an explicit exit. Home and screen lock do not call this.
    override fun onTaskRemoved(rootIntent: Intent?) {
        NeroSleepTimer.cancel()
        session?.player?.let { player ->
            NeroResumeStore.save(applicationContext,player)
            player.pause()
            NeroResumeStore.markExplicitExit(applicationContext)
            // Explicit exit is not a pause: an empty timeline removes the
            // native media notification without deleting the saved resume record.
            player.clearMediaItems()
            player.stop()
        }
        // Keep the session valid until Android destroys the service; this also
        // avoids releasing a player still bound during a rapid reopen.
        pauseAllPlayersAndStopSelf()
    }
    private fun closePlayback() {
        favoriteListener?.let{getSharedPreferences("nero_library",0).unregisterOnSharedPreferenceChangeListener(it)};favoriteListener=null
        TogetherFollowerPlayback.detach()
        replaySampler?.close();replaySampler=null
        timerScope.cancel(); NeroPlaybackAccess.update(null,false); NeroSleepTimer.cancel()
        eq?.close(); eq = null
        NeroWidgetState.detach(applicationContext)
        val active=session
        session=null
        active?.let { val player=it.player; it.release(); player.release() }
    }
    override fun onDestroy() {
        if(BuildConfig.NERO_TV_EXPERIMENT)NeroWebVideoCaster.clearSources()
        closePlayback()
        super.onDestroy()
    }
}
