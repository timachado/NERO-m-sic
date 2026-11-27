package br.com.timachado.neromusic

import androidx.media3.common.Player

// Same-process service bridge; never grants MediaSession seek or Premium commands.
// Only the authenticated Together follower invokes apply after a successful room RPC.
internal object TogetherFollowerPlayback {
    private var player:Player?=null
    private var owner:String?=null
    private var room:String?=null
    private var itemId:String?=null
    fun attach(p:Player){player=p;owner=null;room=null;itemId=null}
    fun detach(){player=null;owner=null;room=null;itemId=null}
    fun owns(p:Player):Boolean=player===p&&itemId!=null&&p.currentMediaItem?.mediaId==itemId
    fun apply(uid:String,roomId:String,hit:NativeMusicHit,target:Long,playing:Boolean):Boolean {
        if(!NeroSupabase.identityReady.value||NeroSupabase.activeUid.value!=uid||togetherCode(roomId)==null||target !in 0..86400000L)return false
        val p=player?:return false
        if(owner==uid&&room==roomId&&itemId!=null&&!owns(p))return false
        val item=hit.queueItem()
        if(!owns(p)||owner!=uid||room!=roomId||itemId!=item.mediaId){
            p.pause()
            owner=uid;room=roomId;itemId=item.mediaId
            p.setMediaItem(item,target);p.prepare()
        }else if(p.playbackState==Player.STATE_READY&&kotlin.math.abs(p.currentPosition-target)>900L){
            p.seekTo(if(p.duration>0)target.coerceAtMost(p.duration)else target)
        }
        if(playing)p.play()else p.pause()
        return true
    }
    fun pause(uid:String,roomId:String){
        val p=player?:return
        if(owner==uid&&room==roomId&&owns(p))p.pause()
    }
    fun end(uid:String,roomId:String){
        if(owner!=uid||room!=roomId)return
        pause(uid,roomId);owner=null;room=null;itemId=null
    }
}
