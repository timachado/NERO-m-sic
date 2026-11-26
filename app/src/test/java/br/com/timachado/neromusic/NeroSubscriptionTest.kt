package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroSubscriptionTest{
    @Test fun legacyContractStillWorks(){
        assertEquals(NeroSubscriptionPhase.ACTIVE,subscriptionPhase("active",null))
        assertEquals(NeroSubscriptionPhase.EXPIRED,subscriptionPhase("expired",null))
        assertEquals(NeroSubscriptionPhase.FREE,subscriptionPhase("none",null))
    }
    @Test fun explicitServerAccessRequired(){
        assertTrue(subscriptionPhase("active","trial").premium)
        assertTrue(subscriptionPhase("active","grace").premium)
        assertFalse(subscriptionPhase("none","trial").premium)
        assertFalse(subscriptionPhase("expired","active").premium)
        assertFalse(subscriptionPhase("active","revoked").premium)
        assertFalse(subscriptionPhase("active","unexpected").premium)
        assertFalse(subscriptionPhase("pending",null).premium)
        assertEquals(NeroSubscriptionPhase.REVOKED,subscriptionPhase("none","revoked"))
    }
}
