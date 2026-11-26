package br.com.timachado.neromusic

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class NeroCommerceConnectTest {
    @Test fun selectedPlanIsSentOnlyToOfficialCore(){
        val mappings=mapOf("premium_monthly" to "monthly","premium_quarterly" to "quarterly","premium_annual" to "yearly")
        for((id,plan) in mappings){
            val url=URI(requireNotNull(neroCheckoutUrl(id)))
            assertEquals("https",url.scheme)
            assertEquals("timachado.ifree.page",url.host)
            assertEquals("/",url.path)
            assertEquals("tiac_nero_plan="+plan,url.rawQuery)
            assertNull(url.rawFragment)
            assertNull(url.rawUserInfo)
        }
    }
    @Test fun rejectsUnknownOrInjectedPlans(){
        for(id in listOf("", "free", "premium_lifetime", "monthly", "premium_monthly&product_id=308")){
            assertNull(neroCheckoutUrl(id))
        }
    }
}
