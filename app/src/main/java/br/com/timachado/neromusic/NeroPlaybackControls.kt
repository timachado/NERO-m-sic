package br.com.timachado.neromusic

import androidx.media3.common.Player
import androidx.media3.common.PlaybackException

internal fun neroPlayRequested(playWhenReady:Boolean,state:Int):Boolean =
    playWhenReady && (state==Player.STATE_BUFFERING||state==Player.STATE_READY)

internal enum class NeroPlaybackAction{PAUSE,RESUME,PREPARE,RESTART}
internal fun neroPlaybackAction(playWhenReady:Boolean,state:Int):NeroPlaybackAction=when{
    neroPlayRequested(playWhenReady,state)->NeroPlaybackAction.PAUSE
    state==Player.STATE_IDLE->NeroPlaybackAction.PREPARE
    state==Player.STATE_ENDED->NeroPlaybackAction.RESTART
    else->NeroPlaybackAction.RESUME
}

// Respect actual available commands. No automatic playback retries or queue replacement.
internal fun toggleNeroPlayback(player:Player){
    if(!player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)||player.currentMediaItem==null)return
    when(neroPlaybackAction(player.playWhenReady,player.playbackState)){
        NeroPlaybackAction.PAUSE->player.pause()
        NeroPlaybackAction.PREPARE->{
            if(!player.isCommandAvailable(Player.COMMAND_PREPARE))return
            player.prepare();player.play()
        }
        NeroPlaybackAction.RESTART->{
            if(!player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))return
            player.seekTo(0L);player.play()
        }
        NeroPlaybackAction.RESUME->player.play()
    }
}
internal fun neroPlaybackErrorMessage(code:Int):String=when(code){
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT->"Conexão indisponível ou instável. Confira sua internet e toque em Play para tentar novamente."
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS->"A fonte da faixa não respondeu como esperado. Toque em Play para tentar novamente."
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND->"Arquivo indisponível. Confira se a faixa ainda está no aparelho ou na fonte musical."
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION->"Sem permissão para acessar esta faixa. Confira as permissões de áudio do aplicativo."
    else->"Falha na reprodução. Toque em Play para tentar novamente."
}
