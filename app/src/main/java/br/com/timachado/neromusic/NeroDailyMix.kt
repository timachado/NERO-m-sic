package br.com.timachado.neromusic

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class NeroDailyCollection(val artist:String,val tracks:List<NativeMusicHit>)

// The catalog results are already scoped to a user-initiated query. Do not
// classify genres or invent artist relationships without catalog metadata.
internal fun buildNeroDailyMixes(groups:List<Pair<String,List<NativeMusicHit>>>,day:Long,excluded:Set<String>,hidden:Set<String>):List<NeroDailyCollection>{
    val used=mutableSetOf<String>()
    return groups.distinctBy{nativeArtistKey(it.first)}.take(4).mapNotNull{(artist,rows)->
        val candidates=rows.filter{recommendationId(it.id)==it.id&&it.id !in excluded&&it.id !in hidden&&it.title.isNotBlank()}.distinctBy{it.id}
        if(candidates.isEmpty())return@mapNotNull null
        val offset=Math.floorMod(day+nativeArtistKey(artist).hashCode().toLong(),candidates.size.toLong()).toInt()
        val rotated=candidates.drop(offset)+candidates.take(offset)
        val tracks=rotated.filter{it.id !in used}.take(12)
        used.addAll(tracks.map{it.id})
        if(tracks.isEmpty())null else NeroDailyCollection(artist,tracks)
    }
}

// Derived catalog metadata only. No audio or credentials. Keep cache local,
// separate for every account and for the guest; never include it in cloud sync.
internal object NeroDailyCache{
    private fun file(context:Context,uid:String?):AtomicFile{
        val scope=if(uid==null)"guest" else NeroAccountLibraryStore.fileName(uid)
        return AtomicFile(File(context.noBackupFilesDir,"nero-daily-"+scope+".json"))
    }
    private fun checkOwner(uid:String?){
        require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    }
    @Synchronized fun read(context:Context,uid:String?,day:Long):List<NeroDailyCollection>{
        checkOwner(uid)
        val f=file(context,uid)
        if(!f.baseFile.exists()&&!File(f.baseFile.path+".bak").exists())return emptyList()
        val bytes=f.readFully();require(bytes.size<=200000)
        val json=JSONObject(bytes.toString(Charsets.UTF_8))
        if(json.getLong("day")!=day)return emptyList()
        val groups=json.getJSONArray("groups");require(groups.length()<=4)
        val result=(0 until groups.length()).map{i->
            val group=groups.getJSONObject(i);val rows=group.getJSONArray("tracks");require(rows.length()<=12)
            val artist=group.getString("artist");require(artist.isNotBlank()&&artist.length<=180)
            val tracks=(0 until rows.length()).map{j->
                val row=rows.getJSONObject(j)
                val id=row.getString("id");val title=row.getString("title");val credit=row.getString("artist")
                require(recommendationId(id)==id&&title.isNotBlank()&&title.length<=300&&credit.length<=300)
                NativeMusicHit(id,title,credit,row.getLong("duration").coerceAtLeast(0L))
            }
            NeroDailyCollection(artist,tracks)
        }
        checkOwner(uid);return result
    }
    @Synchronized fun write(context:Context,uid:String?,day:Long,groups:List<NeroDailyCollection>){
        checkOwner(uid)
        require(groups.size<=4)
        val data=JSONObject().put("day",day).put("groups",JSONArray(groups.map{group->
            require(group.tracks.size<=12)
            JSONObject().put("artist",group.artist.take(180)).put("tracks",JSONArray(group.tracks.map{hit->
                require(recommendationId(hit.id)==hit.id)
                JSONObject().put("id",hit.id).put("title",hit.title.take(300)).put("artist",hit.artist.take(300)).put("duration",hit.duration.coerceAtLeast(0L))
            }))
        })).toString().toByteArray(Charsets.UTF_8)
        require(data.size<=200000)
        checkOwner(uid)
        val f=file(context,uid);val output=f.startWrite()
        try{output.write(data);f.finishWrite(output)}catch(e:Exception){f.failWrite(output);throw e}
    }
}
