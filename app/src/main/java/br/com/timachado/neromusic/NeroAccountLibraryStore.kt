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

internal object NeroAccountSync{
    private val mutex=Mutex()
    // Only operates on isolated account cache; never imports the guest library.
    // No upload if reconciliation has conflicts, or if the server revision changed.
    suspend fun reconcile(context:Context,uid:String):NeroAccountSyncOutcome=mutex.withLock{
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
        NeroAccountSyncOutcome(next,emptyList())
    }
}
