package br.com.timachado.neromusic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal fun cloudAlbumArtwork(url:String?):String?=url?.takeIf{value->
    value.length<=2048&&runCatching{
        val u=java.net.URI(value)
        u.scheme=="https"&&u.userInfo==null&&u.port in listOf(-1,443)&&
            (u.host=="i.ytimg.com"||u.host?.endsWith(".googleusercontent.com")==true||u.host?.endsWith(".ggpht.com")==true)
    }.getOrDefault(false)
}
internal data class CloudAlbum(val id:String,val title:String,val credit:String,val artwork:String?=null){
    fun collection()=NativeCollection("https://music.youtube.com/playlist?list="+id,title,credit,cloudAlbumArtwork(artwork),NativeCollectionKind.ALBUM)
}
internal data class CloudLibrarySnapshot(val favorites:List<OnlineRef>,val playlists:List<OnlineList>,val artists:List<String> = emptyList(),val albums:List<CloudAlbum> = emptyList(),val history:List<CloudHeard> = emptyList(),val hiddenSongs:Set<String> = emptySet())
internal data class CloudHeard(val ref:OnlineRef,val at:Long)
internal data class CloudLibraryRevision(val revision:Long,val snapshot:CloudLibrarySnapshot)

private val cloudReference=Regex("[A-Za-z0-9_:-]{1,128}")
internal fun portableRef(ref:OnlineRef):Boolean =
    ref.id.matches(Regex("youtube:[A-Za-z0-9_-]{11}")) ||
        ref.openAudio?.let{allowed(it)&&ref.id=="commons:"+it.id}==true

private fun cloudRef(ref:OnlineRef):JSONObject{
    require(portableRef(ref)&&cloudReference.matches(ref.id)&&ref.title.isNotBlank()&&ref.title.length<=300&&ref.artist.length<=300)
    val row=JSONObject().put("id",ref.id).put("title",ref.title).put("artist",ref.artist)
    ref.openAudio?.let{require(allowed(it)&&ref.id=="commons:"+it.id);row.put("audio",it.json())}
    return row
}
private fun readCloudRef(row:JSONObject):OnlineRef{
    val id=row.getString("id");val title=row.getString("title");val artist=row.getString("artist")
    require(cloudReference.matches(id)&&title.isNotBlank()&&title.length<=300&&artist.length<=300)
    val audio=row.optJSONObject("audio")?.let{decodeOpenTrack(it).also{t->require(allowed(t)&&id=="commons:"+t.id)}}
    return OnlineRef(id,title,artist,openAudio=audio).also{require(portableRef(it))}
}
internal fun encodeCloudLibrary(snapshot:CloudLibrarySnapshot):JSONObject{
    require(snapshot.favorites.size<=1000&&snapshot.playlists.size<=100&&snapshot.artists.size<=500&&snapshot.albums.size<=500&&snapshot.history.size<=100&&snapshot.hiddenSongs.size<=2000)
    val json=JSONObject().put("format","nero-cloud-v1")
        .put("hiddenSongs",JSONArray(snapshot.hiddenSongs.sorted().map{require(recommendationId(it)==it);it}))
        .put("history",JSONArray(snapshot.history.map{require(it.at>0);JSONObject().put("ref",cloudRef(it.ref)).put("at",it.at)}))
        .put("artists",JSONArray(snapshot.artists.map{require(it.isNotBlank()&&it.length<=180);it}))
        .put("albums",JSONArray(snapshot.albums.map{
            require(it.id.matches(Regex("[A-Za-z0-9_-]{8,100}"))&&it.title.isNotBlank()&&it.title.length<=250&&it.credit.length<=250)
            JSONObject().put("id",it.id).put("title",it.title).put("credit",it.credit).put("artwork",cloudAlbumArtwork(it.artwork))
        }))
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
    val ar=json.optJSONArray("artists")?:JSONArray();require(ar.length()<=500)
    val artists=(0 until ar.length()).map{ar.getString(it).also{a->require(a.isNotBlank()&&a.length<=180)}}.distinctBy(::nativeArtistKey)
    val al=json.optJSONArray("albums")?:JSONArray();require(al.length()<=500)
    val albums=(0 until al.length()).map{i->
        val a=al.getJSONObject(i);CloudAlbum(a.getString("id"),a.getString("title"),a.getString("credit"),cloudAlbumArtwork(a.optString("artwork"))).also{
            require(it.id.matches(Regex("[A-Za-z0-9_-]{8,100}"))&&it.title.isNotBlank()&&it.title.length<=250&&it.credit.length<=250)
        }
    }.distinctBy{it.id}
    val h=json.optJSONArray("history")?:JSONArray();require(h.length()<=100)
    val history=(0 until h.length()).map{i->val row=h.getJSONObject(i);CloudHeard(readCloudRef(row.getJSONObject("ref")),row.getLong("at").also{require(it>0)})}.distinctBy{it.ref.id}.sortedByDescending{it.at}
    val hidden=json.optJSONArray("hiddenSongs")?:JSONArray();require(hidden.length()<=2000)
    val hiddenSongs=(0 until hidden.length()).map{hidden.getString(it).also{id->require(recommendationId(id)==id)}}.toSet()
    return CloudLibrarySnapshot(refs(json.getJSONArray("favorites")),playlists,artists,albums,history,hiddenSongs)
}
// Additive merge only: keep local names and order; no propagation of deletions.
internal fun mergeCloudLibrary(local:CloudLibrarySnapshot,remote:CloudLibrarySnapshot):CloudLibrarySnapshot{
    val remoteLists=remote.playlists.associateBy{it.id}
    val lists=local.playlists.map{l->l.copy(songs=(l.songs+remoteLists[l.id]?.songs.orEmpty()).distinctBy{it.id})}
    return CloudLibrarySnapshot((local.favorites+remote.favorites).distinctBy{it.id},lists+remote.playlists.filter{r->lists.none{it.id==r.id}})
}
// Do not upload device MediaStore IDs or any local files.
internal fun localCloudLibrary(context:Context)=CloudLibrarySnapshot(
    readOnlineFavorites(context).filter(::portableRef),
    onlineLists(context).filter{list->list.songs.isEmpty()||list.songs.any(::portableRef)}
        .map{it.copy(songs=it.songs.filter(::portableRef))}
)
internal fun restoreCloudLibrary(context:Context,snapshot:CloudLibrarySnapshot){
    // Preserve device-only references and playlists. Explicit additive restore, not automatic sync.
    val current=CloudLibrarySnapshot(readOnlineFavorites(context),onlineLists(context))
    val merged=mergeCloudLibrary(current,snapshot)
    saveOnlineFavorites(context,merged.favorites)
    saveOnlineLists(context,merged.playlists)
}

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
