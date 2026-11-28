package br.com.timachado.neromusic

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Explicit opt-in per account/installation; no hardware ID or playback history. */
internal object NeroPresence {
    private data class Entry(val id:String,val state:String){
        val enabled get()=state=="on"
    }
    private val revisionState=MutableStateFlow(0L)
    val revision=revisionState.asStateFlow()
    private val syncMutex=Mutex()
    private fun file(context:Context,uid:String):AtomicFile {
        require(UUID.fromString(uid).toString()==uid.lowercase())
        return AtomicFile(File(File(context.noBackupFilesDir,"nero_presence"),"$uid.txt"))
    }
    @Synchronized private fun read(context:Context,uid:String):Entry? = runCatching {
        val parts=file(context,uid).readFully().toString(Charsets.UTF_8).split(":")
        require(parts.size==2 && UUID.fromString(parts[0]).toString()==parts[0] && parts[1] in setOf("on","off","off-synced"))
        Entry(parts[0],parts[1])
    }.getOrNull()
    private fun write(context:Context,uid:String,target:Entry){
        val atomic=file(context,uid)
        val stream=atomic.startWrite()
        try {
            stream.write((target.id+":"+target.state).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        }catch(e:Exception){atomic.failWrite(stream);throw e}
    }
    fun enabled(context:Context,uid:String?):Boolean=uid!=null && read(context,uid)?.enabled==true
    fun hasChoice(context:Context,uid:String):Boolean=read(context,uid)!=null
    @Synchronized fun setEnabled(context:Context,uid:String,enabled:Boolean):Boolean=runCatching {
        val old=read(context,uid)
        if(old!=null && old.enabled==enabled)return@runCatching true
        val state=if(enabled)"on" else if(old?.enabled==true)"off" else "off-synced"
        // Keep the same ID when a remote removal is still pending.
        val target=Entry(old?.id?:UUID.randomUUID().toString(),state)
        write(context,uid,target)
        revisionState.value+=1
        true
    }.getOrDefault(false)
    @Synchronized private fun acknowledgeForget(context:Context,uid:String,entry:Entry){
        if(read(context,uid)==entry)write(context,uid,entry.copy(state="off-synced"))
    }
    suspend fun sync(context:Context,uid:String):Boolean=syncMutex.withLock{
        val entry=withContext(Dispatchers.IO){read(context,uid)}?:return@withLock true
        if(entry.state=="off-synced")return@withLock true
        try{
            val session=NeroSupabase.session(context,false)
            if(session.uid!=uid || NeroSupabase.activeUid.value!=uid)return@withLock false
            if(withContext(Dispatchers.IO){read(context,uid)}!=entry)return@withLock false
            val body=JSONObject().put("p_installation",entry.id)
                .put("p_action",if(entry.enabled)"seen" else "forget")
            val result=NeroSupabase.rpc("nero_presence_v1",body,session.access)
            if(result.trim()!="true")return@withLock false
            if(!entry.enabled)withContext(Dispatchers.IO){acknowledgeForget(context,uid,entry)}
            true
        }catch(e:CancellationException){throw e}
        catch(_:Exception){false}
    }
}
