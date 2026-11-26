package br.com.timachado.neromusic
import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test
class NeroPlaybackControlsTest{
    @Test fun bufferingCanBePaused(){
        assertEquals(NeroPlaybackAction.PAUSE,neroPlaybackAction(true,Player.STATE_BUFFERING))
        assertEquals(NeroPlaybackAction.RESUME,neroPlaybackAction(false,Player.STATE_BUFFERING))
    }
    @Test fun failedPlaybackPreparesAgainAndEndedRestarts(){
        assertEquals(NeroPlaybackAction.PREPARE,neroPlaybackAction(true,Player.STATE_IDLE))
        assertEquals(NeroPlaybackAction.RESTART,neroPlaybackAction(true,Player.STATE_ENDED))
        assertFalse(neroPlayRequested(true,Player.STATE_IDLE))
        assertFalse(neroPlayRequested(true,Player.STATE_ENDED))
    }
    @Test fun readyControlsMatchPlaybackIntent(){
        assertEquals(NeroPlaybackAction.PAUSE,neroPlaybackAction(true,Player.STATE_READY))
        assertEquals(NeroPlaybackAction.RESUME,neroPlaybackAction(false,Player.STATE_READY))
    }
}
