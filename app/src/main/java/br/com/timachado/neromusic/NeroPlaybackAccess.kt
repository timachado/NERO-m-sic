package br.com.timachado.neromusic

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommands
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// A short in-memory lease from the existing verified account endpoint.
// Unknown, guest, expired or signed-out accounts use Free. Never persist a paid flag.
@UnstableApi
internal object NeroPlaybackAccess {
    private val state=MutableStateFlow(false)
    val premium=state.asStateFlow()
    private var owner:String?=null
    private var deadline=0L
    fun allowed():Boolean=state.value&&owner!=null&&NeroSupabase.identityReady.value&&
        NeroSupabase.activeUid.value==owner&&android.os.SystemClock.elapsedRealtime()<deadline
    fun update(uid:String?,verified:Boolean){
        owner=uid
        deadline=if(verified&&uid!=null)android.os.SystemClock.elapsedRealtime()+60_000L else 0L
        state.value=verified&&uid!=null&&NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid
    }
    fun commands():Player.Commands{
        val builder=MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
        if(!allowed())for(command in restrictedPlaybackCommands)builder.remove(command)
        return builder.build()
    }
    fun sessionCommands():SessionCommands{
        val builder=MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon().add(NeroMediaActions.favorite)
        if(allowed())builder.add(NeroMediaActions.repeat)
        return builder.build()
    }
}
internal val restrictedPlaybackCommands=setOf(
    Player.COMMAND_SEEK_TO_DEFAULT_POSITION,Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,Player.COMMAND_SEEK_TO_PREVIOUS,
    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,Player.COMMAND_SEEK_TO_NEXT,
    Player.COMMAND_SEEK_TO_MEDIA_ITEM,Player.COMMAND_SEEK_BACK,Player.COMMAND_SEEK_FORWARD,
    Player.COMMAND_SET_SHUFFLE_MODE,Player.COMMAND_SET_REPEAT_MODE
)
internal fun neroPlaybackCommandAllowed(premium:Boolean,command:Int)=premium||command !in restrictedPlaybackCommands
internal fun neroPlaybackStartIndex(size:Int,requested:Int,premium:Boolean,randomIndex:(Int)->Int={kotlin.random.Random.nextInt(it)}):Int =
    if(size<=0)requested else if(premium)requested else if(size==1)0 else randomIndex(size)
