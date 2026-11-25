package br.com.timachado.neromusic

internal data class TogetherSnapshot(val trackId:String,val positionMs:Long,val durationMs:Long,val playing:Boolean)
internal data class TogetherPlan(val seekMs:Long?,val play:Boolean)
private val togetherId=Regex("(youtube:[A-Za-z0-9_-]{11}|commons:[1-9][0-9]{0,17})")
internal fun togetherValid(snapshot:TogetherSnapshot):Boolean =
    togetherId.matches(snapshot.trackId)&&snapshot.durationMs in 1L..86400000L&&snapshot.positionMs in 0L..snapshot.durationMs
internal fun togetherWire(snapshot:TogetherSnapshot):String{
    require(togetherValid(snapshot))
    return "NERO-TOGETHER1|"+snapshot.trackId+"|"+snapshot.positionMs+"|"+snapshot.durationMs+"|"+(if(snapshot.playing)"1" else "0")
}
internal fun parseTogetherWire(value:String):TogetherSnapshot{
    require(value.length<=160)
    val fields=value.split('|')
    require(fields.size==5&&fields[0]=="NERO-TOGETHER1"&&fields[4] in setOf("0","1"))
    return TogetherSnapshot(fields[1],fields[2].toLong(),fields[3].toLong(),fields[4]=="1").also{
        require(togetherValid(it)&&togetherWire(it)==value)
    }
}
// Never load a new track from a peer. Match portable media ID and duration,
// then compensate only bounded measured network latency.
internal fun togetherPlan(host:TogetherSnapshot,local:TogetherSnapshot,roundTripMs:Long):TogetherPlan?{
    if(!togetherValid(host)||!togetherValid(local)||host.trackId!=local.trackId||
        kotlin.math.abs(host.durationMs-local.durationMs)>2000L||roundTripMs !in 0L..2000L)return null
    val target=(host.positionMs+if(host.playing)roundTripMs/2 else 0L).coerceIn(0L,local.durationMs)
    val seek=target.takeIf{kotlin.math.abs(it-local.positionMs)>500L}
    return TogetherPlan(seek,host.playing&&target<local.durationMs)
}
