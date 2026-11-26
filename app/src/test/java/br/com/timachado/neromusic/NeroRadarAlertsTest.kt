package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroRadarAlertsTest{
    @Test fun initialScanSilent(){assertTrue(newRadarIds(listOf("a"),emptySet(),false).isEmpty())}
    @Test fun onlyNewUniqueReleases(){assertEquals(listOf("b"),newRadarIds(listOf("a","b","b"),setOf("a"),true))}
}
