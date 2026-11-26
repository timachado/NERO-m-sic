package br.com.timachado.neromusic

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

internal data class NeroAccountLibraryState(
    val revision:Long,
    val baseline:CloudLibrarySnapshot,
    val pending:CloudLibrarySnapshot
)

// This cache never reads the legacy, device-wide library. Every file is scoped
// to the authenticated UUID. Keep base and pending edits in one atomic record.
internal object NeroAccountLibraryStore{
    private val uuid=Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    internal fun fileName(uid:String):String{
        require(uuid.matches(uid))
        return "nero-account-library-"+uid.lowercase(java.util.Locale.ROOT)+".json"
    }
    private fun file(context:Context,uid:String)=AtomicFile(File(context.noBackupFilesDir,fileName(uid)))
    @Synchronized fun read(context:Context,uid:String):NeroAccountLibraryState?{
        val f=file(context,uid)
        if(!f.baseFile.exists()&&!File(f.baseFile.path+".bak").exists())return null
        val bytes=f.readFully()
        require(bytes.size<=650000)
        val row=JSONObject(bytes.toString(Charsets.UTF_8))
        require(row.getString("owner")==uid)
        return NeroAccountLibraryState(row.getLong("revision").also{require(it>=0)},
            decodeCloudLibrary(row.getJSONObject("baseline")),decodeCloudLibrary(row.getJSONObject("pending")))
    }
    @Synchronized fun write(context:Context,uid:String,state:NeroAccountLibraryState){
        require(state.revision>=0)
        val bytes=JSONObject().put("owner",uid).put("revision",state.revision)
            .put("baseline",encodeCloudLibrary(state.baseline)).put("pending",encodeCloudLibrary(state.pending))
            .toString().toByteArray(Charsets.UTF_8)
        require(bytes.size<=650000)
        val f=file(context,uid);val out=f.startWrite()
        try{out.write(bytes);f.finishWrite(out)}catch(e:Exception){f.failWrite(out);throw e}
    }
}

internal data class NeroAccountSyncOutcome(val state:NeroAccountLibraryState?,val conflicts:List<NeroSyncConflict>,val retry:Boolean=false)

internal data class NeroAccountPullStatus(val uid:String,val state:String,val favorites:Int=0,val playlists:Int=0)

internal object NeroAccountSync{
    private val mutex=Mutex()
    private val changesState=kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes:kotlinx.coroutines.flow.StateFlow<Long> = changesState
    private val editsState=kotlinx.coroutines.flow.MutableStateFlow(0L)
    val edits:kotlinx.coroutines.flow.StateFlow<Long> = editsState

    suspend fun toggleFavorite(context:Context,uid:String,ref:OnlineRef)=mutex.withLock{
        require(NeroSupabase.activeUid.value==uid)
        val app=context.applicationContext
        withContext(Dispatchers.IO){
            require(NeroSupabase.activeUid.value==uid)
            val empty=CloudLibrarySnapshot(emptyList(),emptyList())
            val current=NeroAccountLibraryStore.read(app,uid)?:NeroAccountLibraryState(0,empty,empty)
            val favorites=current.pending.favorites
            val next=if(favorites.any{it.id==ref.id})favorites.filterNot{it.id==ref.id}else listOf(ref)+favorites
            NeroAccountLibraryStore.write(app,uid,current.copy(pending=current.pending.copy(favorites=next)))
        }
        changesState.value++
        editsState.value++
    }
    private suspend fun updateCollections(context:Context,uid:String,change:(CloudLibrarySnapshot)->CloudLibrarySnapshot)=mutex.withLock{
        require(NeroSupabase.activeUid.value==uid)
        withContext(Dispatchers.IO){
            val empty=CloudLibrarySnapshot(emptyList(),emptyList())
            val current=NeroAccountLibraryStore.read(context,uid)?:NeroAccountLibraryState(0,empty,empty)
            require(NeroSupabase.activeUid.value==uid)
            NeroAccountLibraryStore.write(context,uid,current.copy(pending=change(current.pending)))
        }
        changesState.value++
        editsState.value++
    }
    suspend fun toggleArtist(context:Context,uid:String,name:String)=updateCollections(context,uid){snapshot->
        val value=NeroFollowedArtists.clean(name);require(value.isNotBlank())
        val key=nativeArtistKey(value)
        val next=if(snapshot.artists.any{nativeArtistKey(it)==key})snapshot.artists.filterNot{nativeArtistKey(it)==key}else snapshot.artists+value
        snapshot.copy(artists=next)
    }
    suspend fun toggleAlbum(context:Context,uid:String,album:NativeCollection)=updateCollections(context,uid){snapshot->
        require(album.kind==NativeCollectionKind.ALBUM&&album.title.isNotBlank())
        val id=requireNotNull(savedAlbumKey(album.url))
        val next=if(snapshot.albums.any{it.id==id})snapshot.albums.filterNot{it.id==id}
            else listOf(CloudAlbum(id,album.title.take(250),album.credit.take(250),cloudAlbumArtwork(album.artwork)))+snapshot.albums
        snapshot.copy(albums=next)
    }
    suspend fun replacePlaylists(context:Context,uid:String,expected:List<OnlineList>,next:List<OnlineList>)=mutex.withLock{
        require(NeroSupabase.activeUid.value==uid)
        withContext(Dispatchers.IO){
            val empty=CloudLibrarySnapshot(emptyList(),emptyList())
            val current=NeroAccountLibraryStore.read(context,uid)?:NeroAccountLibraryState(0,empty,empty)
            require(current.pending.playlists==expected)
            require(NeroSupabase.activeUid.value==uid)
            NeroAccountLibraryStore.write(context,uid,current.copy(pending=current.pending.copy(playlists=next)))
        }
        changesState.value++
        editsState.value++
    }
    suspend fun importGuestFavorites(context:Context,uid:String)=mutex.withLock{
        require(NeroSupabase.activeUid.value==uid)
        val app=context.applicationContext
        withContext(Dispatchers.IO){
            require(NeroSupabase.activeUid.value==uid)
            val empty=CloudLibrarySnapshot(emptyList(),emptyList())
            val current=NeroAccountLibraryStore.read(app,uid)?:NeroAccountLibraryState(0,empty,empty)
            val imported=localCloudLibrary(app).favorites
            val next=(current.pending.favorites+imported).distinctBy{it.id}
            NeroAccountLibraryStore.write(app,uid,current.copy(pending=current.pending.copy(favorites=next)))
        }
        changesState.value++
        editsState.value++
    }
    suspend fun importGuestPlaylists(context:Context,uid:String)=mutex.withLock{
        require(NeroSupabase.activeUid.value==uid)
        val app=context.applicationContext
        withContext(Dispatchers.IO){
            require(NeroSupabase.activeUid.value==uid)
            val empty=CloudLibrarySnapshot(emptyList(),emptyList())
            val current=NeroAccountLibraryStore.read(app,uid)?:NeroAccountLibraryState(0,empty,empty)
            val imported=localCloudLibrary(app).playlists
            val next=importPortablePlaylists(current.pending.playlists,imported)
            NeroAccountLibraryStore.write(app,uid,current.copy(pending=current.pending.copy(playlists=next)))
        }
        changesState.value++
        editsState.value++
    }
    private val pullState=kotlinx.coroutines.flow.MutableStateFlow<NeroAccountPullStatus?>(null)
    val status:kotlinx.coroutines.flow.StateFlow<NeroAccountPullStatus?> = pullState

    // Foreground synchronization: serialized with local edits. Never mix guest data.
    suspend fun pull(context:Context,uid:String){
        pullState.value=NeroAccountPullStatus(uid,"loading")
        try{
            val outcome=reconcile(context,uid)
            val snapshot=outcome.state?.pending
            pullState.value=NeroAccountPullStatus(uid,when{
                outcome.conflicts.isNotEmpty()->"conflict"
                outcome.retry->"pending"
                else->"ready"
            },snapshot?.favorites?.size?:0,snapshot?.playlists?.size?:0)
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){pullState.value=NeroAccountPullStatus(uid,"unavailable")}
    }

    // Only operates on isolated account cache; never imports the guest library.
    // No upload if reconciliation has conflicts, or if the server revision changed.
    suspend fun reconcile(context:Context,uid:String):NeroAccountSyncOutcome=mutex.withLock{
        require(NeroSupabase.activeUid.value==uid)
        val app=context.applicationContext
        val remote=readCloudLibrary(app,uid)
        val stored=withContext(Dispatchers.IO){NeroAccountLibraryStore.read(app,uid)}
        if(stored!=null)require(remote.revision>=stored.revision)
        val result=stored?.let{reconcileNeroLibrary(it.baseline,it.pending,remote.snapshot)}
            ?:NeroSyncResult(remote.snapshot,emptyList())
        if(result.conflicts.isNotEmpty())return@withLock NeroAccountSyncOutcome(stored,result.conflicts)
        var revision=remote.revision
        if(result.snapshot!=remote.snapshot){
            if(!writeCloudLibrary(app,uid,revision,result.snapshot))
                return@withLock NeroAccountSyncOutcome(stored,emptyList(),retry=true)
            revision++
        }
        val session=NeroSupabase.session(app,false)
        require(session.uid==uid)
        val next=NeroAccountLibraryState(revision,result.snapshot,result.snapshot)
        withContext(Dispatchers.IO){NeroAccountLibraryStore.write(app,uid,next)}
        changesState.value++
        NeroAccountSyncOutcome(next,emptyList())
    }
}
