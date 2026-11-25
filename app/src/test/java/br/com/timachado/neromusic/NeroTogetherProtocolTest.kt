package br.com.timachado.neromusic
import org.junit.Assert.*
import org.junit.Test
class NeroTogetherProtocolTest {
    private val room=TogetherRoom("host",setOf("host","guest"),emptyList())
    private val song=TogetherTrack("track1","Música","Artista")
    @Test fun guestAdds(){assertEquals(listOf(song),togetherAdd(room,"guest",0,song)?.queue)}
    @Test fun outsiderRejected(){assertNull(togetherAdd(room,"other",0,song))}
    @Test fun staleRevisionRejected(){assertNull(togetherAdd(room,"host",1,song))}
    @Test fun duplicateRejected(){val r=togetherAdd(room,"host",0,song)!!;assertNull(togetherAdd(r,"guest",1,song))}
    @Test fun hostOnlyRemoval(){val r=togetherAdd(room,"guest",0,song)!!;assertNull(togetherRemove(r,"guest",1,song.id));assertEquals(emptyList<TogetherTrack>(),togetherRemove(r,"host",1,song.id)?.queue)}
    @Test fun invalidTrackRejected(){assertNull(togetherAdd(room,"host",0,song.copy(title="")))}
}
