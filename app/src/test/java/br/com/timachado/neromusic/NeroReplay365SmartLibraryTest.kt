package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroReplay365SmartLibraryTest{
    @Test fun annualIncludesOldRecordsWithoutChangingFree(){
        val rows=listOf(ReplayEntry(1000,"a","A","Artista",5000),ReplayEntry(800,"b","B","Outro",10000),ReplayEntry(635,"c","C","Outro",5000))
        assertEquals(15000L,summarizePremiumReplay(rows,1000,365).total)
        assertEquals(2,summarizePremiumReplay(rows,1000,365).tracks.size)
        assertEquals(1,replayTracks(rows,1000,365).size)
    }
    @Test fun groupsPreserveOriginals(){
        val song=OnlineRef("youtube:abcdefghijk","Canção","Sorriso Maroto")
        val variant=OnlineRef("youtube:abcdefghijl","Canção","Sorriso Maroto")
        val original=listOf(OnlineList("one","Original",listOf(song,variant)),OnlineList("two","Outra",listOf(song)))
        val report=smartLibraryReport(original)
        assertEquals(2,report.unique);assertEquals(1,report.repeated)
        assertEquals(1,report.similar.size)
        assertEquals(listOf(song,variant),report.groups.first{it.name=="NERO • Pagode"}.songs)
        assertEquals(2,original.first().songs.size)
    }
}
