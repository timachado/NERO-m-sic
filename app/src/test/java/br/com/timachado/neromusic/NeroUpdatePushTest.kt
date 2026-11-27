package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroUpdatePushTest{
    private fun payload(code:String)=mapOf("kind" to "nero_update_v1","applicationId" to "br.com.timachado.neromusic","versionCode" to code)
    @Test fun acceptsOnlyNewVersion(){assertEquals(167,neroPushVersion(payload("167"),166));assertNull(neroPushVersion(payload("166"),166));assertNull(neroPushVersion(payload("165"),166));assertNull(neroPushVersion(payload("invalid"),166))}
    @Test fun ignoresOtherAppsAndTopics(){assertNull(neroPushVersion(payload("167")+("applicationId" to "com.timachado.brothermatrizes"),166));assertNull(neroPushVersion(payload("167")+("kind" to "other"),166))}
    @Test fun recoveryNotifiesOnlyNewUnannouncedVersionWithPermission(){
        assertTrue(neroShouldNotify(207,206,0,true,true))
        assertFalse(neroShouldNotify(206,206,0,true,true))
        assertFalse(neroShouldNotify(207,206,207,true,true))
        assertFalse(neroShouldNotify(207,206,0,false,true))
        assertFalse(neroShouldNotify(207,206,0,true,false))
        assertTrue(neroShouldNotify(208,206,207,true,true))
    }
}
