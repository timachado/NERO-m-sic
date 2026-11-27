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

/** Optional, authenticated installation presence; never reads playback history or hardware IDs. */
internal object NeroPresence {
    private data class Entry(val id:String,val enabled:Boolean)
    private val revisionState=MutableStateFlow(0L)
    val revision=revisionState.asStateFlow()
    private val syncMutex=Mutex()
    private fun file(context:Context,uid:String):AtomicFile {
        require(UUID.fromString(uid).toString()==uid.lowercase())
        return AtomicFile(File(File(context.noBackupFilesDir,"nero_presence"),"$uid.txt"))
    }
    private fun read(context:Context,uid:String):Entry? = runCatching {
        val parts=file(context,uid).readFully().toString(Charsets.UTF_8).split(":")
        require(parts.size==2 && UUID.fromString(parts[0]).toString()==parts[0] && parts[1] in setOf("on","off"))
        Entry(parts[0],parts[1]=="on")
    }.getOrNull()
    fun enabled(context:Context,uid:String?):Boolean=uid!=null && read(context,uid)?.enabled==true
    fun setEnabled(context:Context,uid:String,enabled:Boolean):Boolean=runCatching {
        val old=read(context,uid)
        if(old==null&&!enabled)return@runCatching true
        val atomic=file(context,uid)
        val target=Entry(old?.id?:UUID.randomUUID().toString(),enabled)
        val stream=atomic.startWrite()
        try {
            stream.write((target.id+if(enabled) ":on" else ":off").toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch(e:Exception){atomic.failWrite(stream);throw e}
        revisionState.value+=1
        true
    }.getOrDefault(false)
    suspend fun sync(context:Context,uid:String):Boolean=syncMutex.withLock{
        val entry=withContext(Dispatchers.IO){read(context,uid)}?:return@withLock true
        try{
            val session=NeroSupabase.session(context,false)
            if(session.uid!=uid || NeroSupabase.activeUid.value!=uid)return@withLock false
            if(withContext(Dispatchers.IO){read(context,uid)}!=entry)return@withLock false
            val body=JSONObject().put("p_installation",entry.id)
                .put("p_action",if(entry.enabled)"seen" else "forget")
            val result=NeroSupabase.rpc("nero_presence_v1",body,session.access)
            if(result.trim()!="true")return@withLock false
            if(!entry.enabled)withContext(Dispatchers.IO){
                if(read(context,uid)==entry)file(context,uid).delete()
            }
            true
        }catch(e:CancellationException){throw e}
        catch(_:Exception){false}
    }
}
