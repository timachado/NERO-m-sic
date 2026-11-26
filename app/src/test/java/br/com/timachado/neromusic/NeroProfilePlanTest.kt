package br.com.timachado.neromusic
import org.junit.Assert.assertEquals
import org.junit.Test
class NeroProfilePlanTest{
    @Test fun identifiesVerifiedPlans(){
        for(phase in listOf(NeroSubscriptionPhase.ACTIVE,NeroSubscriptionPhase.TRIAL,NeroSubscriptionPhase.GRACE))
            assertEquals("NERO Premium",neroProfilePlanLabel(phase))
        for(phase in listOf(NeroSubscriptionPhase.FREE,NeroSubscriptionPhase.EXPIRED,NeroSubscriptionPhase.REVOKED))
            assertEquals("NERO Free",neroProfilePlanLabel(phase))
    }
    @Test fun unknownPlanIsNotMisrepresented(){
        assertEquals("Plano não verificado",neroProfilePlanLabel(null))
        assertEquals("Plano não verificado",neroProfilePlanLabel(NeroSubscriptionPhase.UNKNOWN))
    }
}
