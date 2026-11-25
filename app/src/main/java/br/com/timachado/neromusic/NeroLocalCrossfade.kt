package br.com.timachado.neromusic

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun neroFadeGains(fraction:Float):Pair<Float,Float>{
    val f=fraction.coerceIn(0f,1f)
    return (1f-f) to f
}

// Experimental local-only overlap. The main player remains the sole MediaSession
// and audio-focus owner. Abort on seek, pause, queue changes or incompatible audio.
@UnstableApi
internal class NeroLocalCrossfade(
    private val context:Context,private val main:ExoPlayer,scope:CoroutineScope
):AutoCloseable {
    private var auxiliary:ExoPlayer?=null
    private var fromIndex=-1
    private var nextIndex=-1
    private var fromItem:MediaItem?=null
    private var nextItem:MediaItem?=null
    private var baseVolume=1f
    private var fadeMs=0L
    private var stage=0
    private var handoffAt=0L
    private var ownSeek=false
    private var attempted:Pair<Int,MediaItem>?=null

    private val listener=object:Player.Listener{
        override fun onPlayWhenReadyChanged(playWhenReady:Boolean,reason:Int){
            if(!playWhenReady)abort()
        }
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason:Int){
            if(playbackSuppressionReason!=Player.PLAYBACK_SUPPRESSION_REASON_NONE)abort()
        }
        override fun onPositionDiscontinuity(oldPosition:Player.PositionInfo,newPosition:Player.PositionInfo,reason:Int){
            if(ownSeek&&stage==3&&newPosition.mediaItemIndex==nextIndex&&reason==Player.DISCONTINUITY_REASON_SEEK){
                ownSeek=false
                return
            }
            if(stage==3&&reason==Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT&&newPosition.mediaItemIndex==nextIndex)return
            abort()
            attempted=null
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled:Boolean){if(shuffleModeEnabled)abort()}
        override fun onRepeatModeChanged(repeatMode:Int){if(repeatMode!=Player.REPEAT_MODE_OFF)abort()}
    }
    private val job:Job
    init{
        main.addListener(listener)
        job=scope.launch{
            while(isActive){
                try{tick()}catch(_:Exception){abort()}
                delay(if(auxiliary!=null)40L else 200L)
            }
        }
    }
    private fun local(item:MediaItem?):Boolean{
        if(item==null||item.isNeroVideo())return false
        val config=item.localConfiguration?:return false
        return config.uri.scheme in setOf("file","content")&&config.mimeType?.startsWith("video/")!=true
    }
    private fun eligible():Boolean =
        NeroAudioSettings.crossfadeSeconds>0&&!NeroAudioSettings.leveling&&
        !context.getSharedPreferences("nero_eq",Context.MODE_PRIVATE).getBoolean("enabled",false)&&
        main.playWhenReady&&main.playbackSuppressionReason==Player.PLAYBACK_SUPPRESSION_REASON_NONE&&
        !main.shuffleModeEnabled&&main.repeatMode==Player.REPEAT_MODE_OFF&&
        main.playbackParameters.speed==1f&&main.playbackParameters.pitch==1f&&NeroSleepTimer.deadline.value==null

    private fun tick(){
        if(!eligible()){abort();return}
        val index=main.currentMediaItemIndex
        val current=main.currentMediaItem
        val aux=auxiliary
        if(aux!=null){
            if(aux.playerError!=null||nextIndex !in 0 until main.mediaItemCount||
                main.getMediaItemAt(nextIndex)!=nextItem){abort();return}
            if(stage==3){
                if(index!=nextIndex||current!=nextItem){abort();return}
                if(main.isPlaying||android.os.SystemClock.elapsedRealtime()-handoffAt>1500L){abort()}
                return
            }
            if(index!=fromIndex||current!=fromItem||!main.isPlaying||
                main.nextMediaItemIndex!=nextIndex||NeroAudioSettings.crossfadeSeconds*1000L!=fadeMs){abort();return}
            val remain=main.duration-main.currentPosition
            if(stage==1){
                if(remain<=fadeMs&&remain>=fadeMs/2&&aux.playbackState==Player.STATE_READY){
                    baseVolume=main.volume
                    stage=2
                    aux.play()
                }else if(remain<fadeMs/2){abort()}
                return
            }
            if(stage==2){
                if(!aux.isPlaying){
                    if(remain<=200L)abort()
                    return
                }
                val f=(1f-remain.toFloat()/fadeMs).coerceIn(0f,1f)
                val (outgoing,incoming)=neroFadeGains(f)
                main.volume=baseVolume*outgoing
                aux.volume=baseVolume*incoming
                if(remain<=100L){
                    if(aux.duration==C.TIME_UNSET||aux.currentPosition>=aux.duration-500L){abort();return}
                    // Keep the already audible next track running while the session
                    // seeks into the same track. No second MediaSession is created.
                    stage=3
                    handoffAt=android.os.SystemClock.elapsedRealtime()
                    ownSeek=true
                    main.volume=0f
                    aux.volume=baseVolume
                    main.seekTo(nextIndex,aux.currentPosition)
                }
            }
            return
        }
        if(!main.isPlaying||current==null||!local(current))return
        val next=main.nextMediaItemIndex
        if(next !in 0 until main.mediaItemCount||next==index)return
        val item=main.getMediaItemAt(next)
        if(!local(item)||item==current||main.duration==C.TIME_UNSET)return
        val length=NeroAudioSettings.crossfadeSeconds*1000L
        val remain=main.duration-main.currentPosition
        if(main.duration<length*2||remain !in length..(length+2500L)||attempted==(index to current))return
        attempted=index to current
        fromIndex=index;nextIndex=next;fromItem=current;nextItem=item
        fadeMs=length;baseVolume=main.volume;stage=1
        val secondary=ExoPlayer.Builder(context).build()
        auxiliary=secondary
        secondary.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),false)
        secondary.volume=0f
        secondary.setMediaItem(item)
        secondary.prepare()
    }
    private fun abort(){
        val old=auxiliary?:return
        auxiliary=null
        stage=0;ownSeek=false
        runCatching{old.volume=0f;old.pause();old.release()}
        runCatching{main.volume=baseVolume}
    }
    override fun close(){
        job.cancel()
        main.removeListener(listener)
        abort()
    }
}
