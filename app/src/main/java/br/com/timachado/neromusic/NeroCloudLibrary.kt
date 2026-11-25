package br.com.timachado.neromusic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class CloudLibrarySnapshot(val favorites:List<OnlineRef>,val playlists:List<OnlineList>)
internal data class CloudLibraryRevision(val revision:Long,val snapshot:CloudLibrarySnapshot)

private val cloudReference=Regex("[A-Za-z0-9_:-]{1,128}")
private fun cloudRef(ref:OnlineRef):JSONObject{
    require(cloudReference.matches(ref.id)&&ref.title.isNotBlank()&&ref.title.length<=300&&ref.artist.length<=300)
    val row=JSONObject().put("id",ref.id).put("title",ref.title).put("artist",ref.artist)
    ref.openAudio?.let{require(allowed(it)&&ref.id=="commons:"+it.id);row.put("audio",it.json())}
    return row
}
private fun readCloudRef(row:JSONObject):OnlineRef{
    val id=row.getString("id");val title=row.getString("title");val artist=row.getString("artist")
    require(cloudReference.matches(id)&&title.isNotBlank()&&title.length<=300&&artist.length<=300)
    val audio=row.optJSONObject("audio")?.let{decodeOpenTrack(it).also{t->require(allowed(t)&&id=="commons:"+t.id)}}
    return OnlineRef(id,title,artist,openAudio=audio)
}
internal fun encodeCloudLibrary(snapshot:CloudLibrarySnapshot):JSONObject{
    require(snapshot.favorites.size<=1000&&snapshot.playlists.size<=100)
    val json=JSONObject().put("format","nero-cloud-v1")
        .put("favorites",JSONArray(snapshot.favorites.map(::cloudRef)))
        .put("playlists",JSONArray(snapshot.playlists.map{list->
            require(cloudReference.matches(list.id)&&list.name.isNotBlank()&&list.name.length<=100&&list.songs.size<=1000)
            JSONObject().put("id",list.id).put("name",list.name).put("songs",JSONArray(list.songs.map(::cloudRef)))
        }))
    require(json.toString().toByteArray(Charsets.UTF_8).size<=250000)
    return json
}
internal fun decodeCloudLibrary(json:JSONObject):CloudLibrarySnapshot{
    require(json.toString().toByteArray(Charsets.UTF_8).size<=300000&&json.getString("format")=="nero-cloud-v1")
    fun refs(rows:JSONArray):List<OnlineRef>{
        require(rows.length()<=1000)
        return (0 until rows.length()).map{readCloudRef(rows.getJSONObject(it))}.distinctBy{it.id}
    }
    val lists=json.getJSONArray("playlists");require(lists.length()<=100)
    val playlists=(0 until lists.length()).map{i->
        val row=lists.getJSONObject(i)
        val id=row.getString("id");val name=row.getString("name")
        require(cloudReference.matches(id)&&name.isNotBlank()&&name.length<=100)
        OnlineList(id,name,refs(row.getJSONArray("songs")))
    }
    require(playlists.distinctBy{it.id}.size==playlists.size)
    return CloudLibrarySnapshot(refs(json.getJSONArray("favorites")),playlists)
}
// Additive merge only: keep local names and order; no propagation of deletions.
internal fun mergeCloudLibrary(local:CloudLibrarySnapshot,remote:CloudLibrarySnapshot):CloudLibrarySnapshot{
    val remoteLists=remote.playlists.associateBy{it.id}
    val lists=local.playlists.map{l->l.copy(songs=(l.songs+remoteLists[l.id]?.songs.orEmpty()).distinctBy{it.id})}
    return CloudLibrarySnapshot((local.favorites+remote.favorites).distinctBy{it.id},lists+remote.playlists.filter{r->lists.none{it.id==r.id}})
}
internal fun localCloudLibrary(context:Context)=CloudLibrarySnapshot(readOnlineFavorites(context),onlineLists(context))
internal suspend fun readCloudLibrary(context:Context,uid:String):CloudLibraryRevision{
    val session=NeroSupabase.session(context,false)
    require(session.uid==uid)
    val row=JSONObject(NeroSupabase.rpc("nero_cloud_read_v1",JSONObject(),session.access))
    val revision=row.getLong("revision")
    require(revision>=0)
    val payload=row.optJSONObject("payload")
    require((revision==0L)==(payload==null))
    return CloudLibraryRevision(revision,payload?.let(::decodeCloudLibrary)?:CloudLibrarySnapshot(emptyList(),emptyList()))
}
internal suspend fun writeCloudLibrary(context:Context,uid:String,revision:Long,snapshot:CloudLibrarySnapshot):Boolean{
    val session=NeroSupabase.session(context,false)
    require(session.uid==uid)
    val payload=encodeCloudLibrary(snapshot)
    val row=JSONObject(NeroSupabase.rpc("nero_cloud_write_v1",JSONObject().put("p_expected",revision).put("p_payload",payload),session.access))
    return row.getBoolean("saved")
}
