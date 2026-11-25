package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class ShareLinksTest{
    private val host="nero-test.example"
    private val token="0123456789abcdef0123456789abcdef"
    @Test fun acceptsExactHttpsLink(){assertEquals(token,ShareLinks.parseForHost("https://"+host+"/s/"+token,host))}
    @Test fun rejectsInvalidLinks(){
        for(url in listOf("http://"+host+"/s/"+token,"https://"+host+".evil.test/s/"+token,"https://"+host+"/s/"+token+"?x=1","https://"+host+"/s/"+token+"/extra","https://"+host+"/s/short","https://user@"+host+"/s/"+token)){
            assertNull(ShareLinks.parseForHost(url,host))
        }
    }
    @Test fun rejectsUnconfiguredHost(){assertNull(ShareLinks.parseForHost("https://"+host+"/s/"+token,""))}
}
