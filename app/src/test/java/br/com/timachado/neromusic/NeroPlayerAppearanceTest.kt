package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroPlayerAppearanceTest{
    @Test fun unverifiedOrUnknownFallsBack(){
        assertEquals(NeroPlayerTheme.AURORA,effectivePlayerTheme("VIOLET",false))
        assertEquals(NeroPlayerTheme.AURORA,effectivePlayerTheme("invalid",true))
        assertEquals(NeroPlayerTheme.AURORA,effectivePlayerTheme(null,true))
    }
    @Test fun verifiedThemesPreservePreference(){
        NeroPlayerTheme.entries.forEach{assertEquals(it,effectivePlayerTheme(it.name,true))}
    }
    @Test fun cafeRequiresVerifiedPremium(){
        assertEquals(NeroPlayerTheme.CAFE,effectivePlayerTheme("CAFE",true))
        assertEquals(NeroPlayerTheme.AURORA,effectivePlayerTheme("CAFE",false))
        assertEquals(setOf("AURORA","OCEAN","VIOLET","GOLD","CAFE","RUBY","ICE"),NeroPlayerTheme.entries.map{it.name}.toSet())
        NeroPlayerTheme.entries.filter{it!=NeroPlayerTheme.AURORA}.forEach{
            assertEquals(NeroPlayerTheme.AURORA,effectivePlayerTheme(it.name,false))
        }
    }
    @Test fun defaultColorsPreserveOriginalPlayer(){
        assertEquals(0xFF39FF14,NeroPlayerTheme.AURORA.primary)
        assertEquals(0xFF00E5FF,NeroPlayerTheme.AURORA.secondary)
        assertEquals(0xFF17262D,NeroPlayerTheme.AURORA.panel)
    }
}
