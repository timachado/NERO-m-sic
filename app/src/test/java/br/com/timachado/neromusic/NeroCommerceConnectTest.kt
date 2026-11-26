package br.com.timachado.neromusic

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class NeroCommerceConnectTest {
    @Test fun opensOnlyPublicNeroPage(){
        val url=URI(NERO_PUBLIC_APP_URL)
        assertEquals("https",url.scheme)
        assertEquals("timachado.ifree.page",url.host)
        assertEquals("/aplicativos/nero-music/",url.path)
        assertNull(url.rawQuery)
        assertNull(url.rawFragment)
        assertNull(url.rawUserInfo)
    }
}
