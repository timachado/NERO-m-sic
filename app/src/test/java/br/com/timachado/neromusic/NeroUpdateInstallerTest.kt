package br.com.timachado.neromusic
import java.net.URL
import org.junit.Assert.*
import org.junit.Test
class NeroUpdateInstallerTest {
    @Test fun allowsOnlyExpectedHttpsHosts(){
        assertTrue(neroTrustedApkUrl(URL("https://github.com/timachado/NERO-m-sic/releases/download/v1.0.1/NERO-Music.apk")))
        assertTrue(neroTrustedApkUrl(URL("https://release-assets.githubusercontent.com/example")))
        assertFalse(neroTrustedApkUrl(URL("http://github.com/example")))
        assertFalse(neroTrustedApkUrl(URL("https://github.com.example.org/example")))
        assertFalse(neroTrustedApkUrl(URL("https://github.com:444/example")))
        assertFalse(neroTrustedApkUrl(URL("https://user@github.com/example")))
    }
    @Test fun digestUsesUnsignedLowercaseHex(){
        assertEquals("00017f80ff",neroDigestHex(byteArrayOf(0,1,127,-128,-1)))
    }
}
