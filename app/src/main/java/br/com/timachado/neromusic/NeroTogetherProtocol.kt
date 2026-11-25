package br.com.timachado.neromusic

// Pure session state reducer. Transport and player integration are intentionally absent.
internal data class TogetherTrack(val id:String,val title:String,val artist:String)
internal data class TogetherRoom(val host:String,val members:Set<String>,val queue:List<TogetherTrack>,val revision:Long=0)
internal enum class TogetherRole { HOST, GUEST }
internal fun togetherRole(room:TogetherRoom,actor:String):TogetherRole? =
    if(actor==room.host&&actor in room.members)TogetherRole.HOST else if(actor in room.members)TogetherRole.GUEST else null
internal fun togetherAdd(room:TogetherRoom,actor:String,expected:Long,track:TogetherTrack):TogetherRoom? {
    if(togetherRole(room,actor)==null||expected!=room.revision||room.revision==Long.MAX_VALUE||room.queue.size>=100)return null
    if(!track.id.matches(Regex("[A-Za-z0-9_:-]{1,128}"))||track.title.isBlank()||track.title.length>300||track.artist.length>300)return null
    if(room.queue.any{it.id==track.id})return null
    return room.copy(queue=room.queue+track,revision=room.revision+1)
}
internal fun togetherRemove(room:TogetherRoom,actor:String,expected:Long,id:String):TogetherRoom? {
    if(togetherRole(room,actor)!=TogetherRole.HOST||expected!=room.revision||room.revision==Long.MAX_VALUE||room.queue.none{it.id==id})return null
    return room.copy(queue=room.queue.filterNot{it.id==id},revision=room.revision+1)
}
