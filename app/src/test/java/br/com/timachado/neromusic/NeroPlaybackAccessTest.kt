package br.com.timachado.neromusic
import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test
class NeroPlaybackAccessTest{
    @Test fun freeCannotSeekOrChangeShuffle(){
        for(command in restrictedPlaybackCommands){
            assertFalse(neroPlaybackCommandAllowed(false,command))
            assertTrue(neroPlaybackCommandAllowed(true,command))
        }
        assertTrue(neroPlaybackCommandAllowed(false,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(neroPlaybackCommandAllowed(false,Player.COMMAND_SEEK_TO_NEXT))
        assertFalse(neroPlaybackCommandAllowed(false,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertFalse(neroPlaybackCommandAllowed(false,Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertTrue(neroPlaybackCommandAllowed(false,Player.COMMAND_PLAY_PAUSE))
        assertTrue(neroPlaybackCommandAllowed(false,Player.COMMAND_SET_MEDIA_ITEM))
    }
    @Test fun freeCollectionStartsRandomlyAndPremiumKeepsRequestedIndex(){
        assertEquals(3,neroPlaybackStartIndex(5,0,false){3})
        assertEquals(0,neroPlaybackStartIndex(5,0,true){3})
        assertEquals(0,neroPlaybackStartIndex(1,0,false){error("not needed")})
    }
}
