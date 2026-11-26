package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroExpiryNoticeTest {
    @Test fun warningBoundaries(){
        val now=1700000000L
        assertNull(neroExpiryNotice(NeroSubscriptionPhase.ACTIVE,now+259201,now))
        assertTrue(neroExpiryNotice(NeroSubscriptionPhase.ACTIVE,now+259200,now)!!.contains("3 dias"))
        assertTrue(neroExpiryNotice(NeroSubscriptionPhase.ACTIVE,now+86400,now)!!.contains("24 horas"))
        assertNull(neroExpiryNotice(NeroSubscriptionPhase.ACTIVE,now,now))
        assertNull(neroExpiryNotice(NeroSubscriptionPhase.FREE,now+60,now))
        assertNull(neroExpiryNotice(NeroSubscriptionPhase.REVOKED,now+60,now))
        assertNull(neroExpiryNotice(NeroSubscriptionPhase.ACTIVE,null,now))
        assertTrue(neroExpiryNotice(NeroSubscriptionPhase.TRIAL,now+60,now)!!.contains("experiência"))
    }
}
