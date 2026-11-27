package br.com.timachado.neromusic

/** Transport-independent queue foundation. The receiver must provide actual
 * item identity and an authoritative completion event. A STOPPED state alone
 * is not completion. Does not resolve media URLs, start a receiver or modify
 * the local Media3 player. All transport and authorization gates are external.
 */
internal class NeroRemoteQueue(ids:List<String>,startIndex:Int=0){
    private val items=ids.toList()
    init{
        require(items.size in 1..1000)
        require(startIndex in items.indices)
        require(items.all{it.isNotBlank()&&it.length<=512})
    }
    internal data class Load(val index:Int,val mediaId:String,val generation:Long)
    internal enum class State { IDLE, LOADING, PLAYING, PAUSED, FINISHED, FAILED, DISCONNECTED }
    var state=State.IDLE
        private set
    private var index=startIndex
    private var generation=0L
    private var current:Load?=null
    private fun load():Load{
        val next=Load(index,items[index],++generation)
        current=next
        state=State.LOADING
        return next
    }
    fun start():Load?=if(state==State.IDLE)load()else null
    private fun matches(event:Load)=current==event
    fun confirmedPlaying(event:Load):Boolean{
        if(!matches(event)||state !in setOf(State.LOADING,State.PAUSED))return false
        state=State.PLAYING
        return true
    }
    fun confirmedPaused(event:Load):Boolean{
        if(!matches(event)||state!=State.PLAYING)return false
        state=State.PAUSED
        return true
    }
    fun confirmedCompleted(event:Load):Load?{
        if(!matches(event)||state!=State.PLAYING)return null
        if(index==items.lastIndex){
            state=State.FINISHED
            current=null
            return null
        }
        index++
        return load()
    }
    fun failed(event:Load):Boolean{
        if(!matches(event)||state !in setOf(State.LOADING,State.PLAYING,State.PAUSED))return false
        state=State.FAILED
        current=null
        return true
    }
    fun disconnect(){
        current=null
        state=State.DISCONNECTED
    }
}
