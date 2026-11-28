package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroPlaylistDurationAndOfflineTest{
    @Test fun accurateDurationAndUnknownValues(){
        assertEquals("2 h 14 min",playlistDurationLabel(listOf(3600L,3600L,840L)))
        assertEquals("3 min (parcial)",playlistDurationLabel(listOf(180L,null,0L)))
        assertEquals("25 s",playlistDurationLabel(listOf(25L)))
        assertNull(playlistDurationLabel(listOf(null,0L)))
        assertNull(playlistDurationLabel(emptyList()))
    }
    @Test fun premiumAndSourceRightsAreBothRequired(){
        assertFalse(offlineDownloadAllowed(false,false))
        assertFalse(offlineDownloadAllowed(false,true))
        assertFalse(offlineDownloadAllowed(true,false))
        assertTrue(offlineDownloadAllowed(true,true))
        assertFalse(nativeOfflineAuthorized(NativeMusicHit("aaaaaaaaaaa","Song","Artist",180L)))
    }
}
