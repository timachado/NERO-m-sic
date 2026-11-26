package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroWebVideoCasterTest {
    @Test fun onlySharesCanonicalPublicWatchPages(){
        assertEquals("https://www.youtube.com/watch?v=aBcD1234_-Z",NeroWebVideoCaster.publicWatchUrl("youtube:aBcD1234_-Z"))
        assertNull(NeroWebVideoCaster.publicWatchUrl(null))
        assertNull(NeroWebVideoCaster.publicWatchUrl("local:aBcD1234_-Z"))
        assertNull(NeroWebVideoCaster.publicWatchUrl("youtube:aBcD1234_-Z&token=secret"))
        assertNull(NeroWebVideoCaster.publicWatchUrl("https://example.com/internal-stream"))
    }
}
