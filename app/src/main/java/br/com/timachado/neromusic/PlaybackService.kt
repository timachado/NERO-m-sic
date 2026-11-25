package br.com.timachado.neromusic

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var eq: SessionEqualizer? = null
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
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
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(activity).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        eq?.close(); eq = null
        session?.let { it.player.release(); it.release() }
        session = null
        super.onDestroy()
    }
}
