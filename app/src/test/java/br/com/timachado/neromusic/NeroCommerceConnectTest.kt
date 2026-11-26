package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroCommerceConnectTest {
    @Test fun validCodeUsesOnlyOfficialHost(){
        val code="a1".repeat(32)
        assertEquals("https://timachado.ifree.page/?nero_connect="+code,neroCommerceBindingUrl(code))
    }
    @Test fun rejectsMalformedOrInjectedCode(){
        for(code in listOf("", "a".repeat(63), "a".repeat(65), "A".repeat(64),
            "https://example.com", "a".repeat(64)+"&redirect=https://example.com",
            "a".repeat(63)+"\n", "a".repeat(63)+"%")){
            assertNull(neroCommerceBindingUrl(code))
        }
    }
}
