package br.com.timachado.neromusic

// Transport-independent command contract. A future authenticated transport
// must establish both owner identity and device authorization before calling it.
// No socket, background service or remote execution is enabled by this file.
internal enum class ConnectAction { PLAY, PAUSE, NEXT, PREVIOUS, SEEK }
internal data class ConnectCommand(
    val id:String,
    val senderDevice:String,
    val targetDevice:String,
    val sessionId:String,
    val sequence:Long,
    val issuedAtMillis:Long,
    val expiresAtMillis:Long,
    val action:ConnectAction,
    val positionMillis:Long?=null
)
internal data class ConnectAuthorization(
    val localDevice:String,
    val authorizedSender:String,
    val activeSession:String,
    val lastSequence:Long=0L,
    val enabled:Boolean=false
)
internal enum class ConnectDecision { ACCEPT, DISABLED, WRONG_DEVICE, WRONG_SESSION, INVALID, EXPIRED, REPLAY }

private val connectId=Regex("[A-Za-z0-9_-]{16,128}")

internal fun validateConnectCommand(
    command:ConnectCommand,
    authorization:ConnectAuthorization,
    nowMillis:Long,
    durationMillis:Long?
):ConnectDecision {
    if(!authorization.enabled)return ConnectDecision.DISABLED
    if(!listOf(command.id,command.senderDevice,command.targetDevice,command.sessionId).all{connectId.matches(it)})return ConnectDecision.INVALID
    if(command.targetDevice!=authorization.localDevice||command.senderDevice!=authorization.authorizedSender||command.senderDevice==command.targetDevice)return ConnectDecision.WRONG_DEVICE
    if(command.sessionId!=authorization.activeSession)return ConnectDecision.WRONG_SESSION
    if(command.sequence<=0L||nowMillis<=0L||command.issuedAtMillis<=0L||command.expiresAtMillis<=command.issuedAtMillis)return ConnectDecision.INVALID
    if(command.expiresAtMillis-command.issuedAtMillis>30000L)return ConnectDecision.INVALID
    if(command.expiresAtMillis<=nowMillis||command.issuedAtMillis-nowMillis>5000L)return ConnectDecision.EXPIRED
    if(command.sequence<=authorization.lastSequence)return ConnectDecision.REPLAY
    if(command.action==ConnectAction.SEEK){
        val position=command.positionMillis?:return ConnectDecision.INVALID
        if(durationMillis==null||durationMillis<=0L||position !in 0L..durationMillis)return ConnectDecision.INVALID
    }else if(command.positionMillis!=null)return ConnectDecision.INVALID
    return ConnectDecision.ACCEPT
}
