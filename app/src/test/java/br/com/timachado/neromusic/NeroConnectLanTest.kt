package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class NeroConnectLanTest{
    @Test fun restrictsPrivateIpv4(){
        listOf("10.0.0.1","172.16.0.1","172.31.255.1","192.168.1.1").forEach{assertNotNull(connectPrivateIpv4(it))}
        listOf("8.8.8.8","127.0.0.1","169.254.1.1","172.32.0.1","192.168.1.256","192.168.01.1","example.com","::1").forEach{assertNull(connectPrivateIpv4(it))}
    }
    @Test fun checksSubnet(){
        val a=connectPrivateIpv4("192.168.1.10")!!
        assertTrue(connectSameSubnet(a,connectPrivateIpv4("192.168.1.50")!!,24))
        assertFalse(connectSameSubnet(a,connectPrivateIpv4("192.168.2.50")!!,24))
        assertFalse(connectSameSubnet(a,a,0))
    }
    @Test fun restrictsTlsVersions(){
        assertArrayEquals(arrayOf("TLSv1.3","TLSv1.2"),connectSecureProtocols(arrayOf("TLSv1.3","TLSv1.2","TLSv1.1","SSLv3")))
    }
    @Test fun pinsExactPublicKey(){
        val gen=KeyPairGenerator.getInstance("EC").apply{initialize(ECGenParameterSpec("secp256r1"))}
        val a=gen.generateKeyPair().public
        val b=gen.generateKeyPair().public
        assertTrue(sameConnectKey(a,parseConnectOffer(connectPublicOffer(a))))
        assertFalse(sameConnectKey(a,b))
    }
}
