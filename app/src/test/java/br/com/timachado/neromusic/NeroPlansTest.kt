package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroPlansTest{
    @Test fun approvedOfferAmountsAndPeriods(){
        assertEquals(listOf(990,2490,8990),NeroPlans.offers.map{it.cents})
        assertEquals(listOf(1,3,12),NeroPlans.offers.map{it.months})
        assertEquals(3,NeroPlans.offers.map{it.id}.distinct().size)
        assertEquals(listOf(0,16,24),NeroPlans.offers.map{it.savingsPercent})
        assertEquals(7,NeroPlans.TRIAL_DAYS)
    }
    @Test fun unknownStatusNeverClaimsActive(){
        assertEquals("Não verificado",neroPlanStatus("trial"))
        assertEquals("Não verificado",neroPlanStatus(""))
        assertEquals("Assinatura ativa",neroPlanStatus("active"))
        assertEquals("Assinatura expirada",neroPlanStatus("expired"))
    }
}
