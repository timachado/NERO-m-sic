package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class NeroConnectProofTest {
    private fun pair()=KeyPairGenerator.getInstance("EC").apply{initialize(ECGenParameterSpec("secp256r1"))}.generateKeyPair()
    private val a=pair()
    private val b=pair()
    private val now=1000000L
    @Test fun validProofConsumedOnce(){
        val gate=ConnectProofGate()
        val challenge=gate.issue(a.public,b.public,now)
        val proof=signConnectChallenge(parseConnectChallenge(challenge.wire()),b.public,a.public,b.private,now)
        assertTrue(gate.consume(proof,a.public,b.public,now+100))
        assertFalse(gate.consume(proof,a.public,b.public,now+100))
    }
    @Test fun rejectsWrongSigningKey(){
        val c=newConnectChallenge(a.public,b.public,now)
        val forged=signConnectChallenge(c,b.public,a.public,pair().private,now)
        assertFalse(verifyConnectProof(c,forged,a.public,b.public,now))
    }
    @Test fun rejectsExpiredProof(){
        val c=newConnectChallenge(a.public,b.public,now)
        val proof=signConnectChallenge(c,b.public,a.public,b.private,now)
        assertFalse(verifyConnectProof(c,proof,a.public,b.public,now+120000))
    }
    @Test fun rejectsOtherChallenge(){
        val c=newConnectChallenge(a.public,b.public,now)
        val proof=signConnectChallenge(c,b.public,a.public,b.private,now)
        assertFalse(verifyConnectProof(newConnectChallenge(a.public,b.public,now),proof,a.public,b.public,now))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsReversedIdentity(){
        val c=newConnectChallenge(a.public,b.public,now)
        signConnectChallenge(c,a.public,b.public,a.private,now)
    }
    @Test fun malformedProofConsumesChallenge(){
        val gate=ConnectProofGate()
        val c=gate.issue(a.public,b.public,now)
        val proof=signConnectChallenge(c,b.public,a.public,b.private,now)
        assertFalse(gate.consume("invalid",a.public,b.public,now))
        assertFalse(gate.consume(proof,a.public,b.public,now))
    }
}
