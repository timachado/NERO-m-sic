package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroRecentHistoryTest{
    private fun h(id:String,at:Long)=Heard(id,"Track "+id,"Artist",at)
    @Test fun newestEntryWinsAcrossLocalAndCloud(){
        assertEquals(listOf(h("b",30),h("a",20)),mergeRecentHistory(listOf(h("a",20)),listOf(h("a",10),h("b",30))))
    }
    @Test fun recentHistoryIsBoundedAndDeduplicated(){
        val rows=(1L..120L).map{h(it.toString(),it)}
        val result=mergeRecentHistory(rows,rows)
        assertEquals(100,result.size)
        assertEquals("120",result.first().id)
        assertEquals("21",result.last().id)
    }
}