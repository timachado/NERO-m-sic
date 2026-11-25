package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class NeroConnectIdentityTest {
    private fun key(curve:String="secp256r1")=KeyPairGenerator.getInstance("EC").apply{initialize(ECGenParameterSpec(curve))}.generateKeyPair().public
    @Test fun offerRoundTrip(){
        val a=key();assertArrayEquals(a.encoded,parseConnectOffer(connectPublicOffer(a)).encoded)
    }
    @Test fun bothDevicesComputeSameCode(){
        val a=key();val b=key()
        assertEquals(connectFingerprint(a,b),connectFingerprint(b,a))
        assertTrue(connectFingerprint(a,b).matches(Regex("[A-F0-9]{4} [A-F0-9]{4} [A-F0-9]{4}")))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsOwnKey(){val a=key();connectFingerprint(a,a)}
    @Test(expected=IllegalArgumentException::class) fun rejectsMalformedOffer(){parseConnectOffer("not a key")}
    @Test(expected=IllegalArgumentException::class) fun rejectsOtherCurve(){parseConnectOffer(connectPublicOffer(key("secp384r1")))}
}
