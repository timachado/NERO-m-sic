package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroWebVideoCasterTest {
    @Test fun acceptsOnlyExistingProviderMediaNotWatchPages(){
        val id="youtube:aBcD1234_-Z"
        val url="https://r1.example.googlevideo.com/videoplayback?mime=audio%2Fmp4&expire=123&sig=example"
        assertEquals(url,NeroWebVideoCaster.validatedSource(id,false,url))
        assertNull(NeroWebVideoCaster.validatedSource(id,false,"https://www.youtube.com/watch?v=aBcD1234_-Z"))
        assertNull(NeroWebVideoCaster.validatedSource(id,false,"https://googlevideo.com.evil.example/videoplayback?mime=audio%2Fmp4"))
        assertNull(NeroWebVideoCaster.validatedSource(id,false,"http://googlevideo.com/videoplayback?mime=audio%2Fmp4"))
        assertNull(NeroWebVideoCaster.validatedSource(id,false,"https://user:pass@googlevideo.com/videoplayback?mime=audio%2Fmp4"))
        assertNull(NeroWebVideoCaster.validatedSource("local:track",false,url))
        assertNull(NeroWebVideoCaster.validatedSource(id,false,"https://googlevideo.com/videoplayback"))
        assertNull(NeroWebVideoCaster.validatedSource(id,false,"https://googlevideo.com/videoplayback?mime=audio%2Fmp4#secret"))
    }
}
