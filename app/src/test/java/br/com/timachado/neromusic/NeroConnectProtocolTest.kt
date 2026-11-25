package br.com.timachado.neromusic

import org.junit.Assert.assertEquals
import org.junit.Test

class NeroConnectProtocolTest {
    private val sender="sender_device_0001"
    private val target="target_device_0001"
    private val session="session_token_0001"
    private val now=100000L
    private val auth=ConnectAuthorization(target,sender,session,7,true)
    private val command=ConnectCommand("command_id_000001",sender,target,session,8,now-1000,now+9000,ConnectAction.PAUSE)
    private fun validate(cmd:ConnectCommand=command,a:ConnectAuthorization=auth,duration:Long?=10000L)=validateConnectCommand(cmd,a,now,duration)
    @Test fun acceptsAuthorizedFreshCommand(){assertEquals(ConnectDecision.ACCEPT,validate())}
    @Test fun rejectsDisabled(){assertEquals(ConnectDecision.DISABLED,validate(a=auth.copy(enabled=false)))}
    @Test fun rejectsUnpairedSender(){assertEquals(ConnectDecision.WRONG_DEVICE,validate(command.copy(senderDevice="unknown_device_001")))}
    @Test fun rejectsOldSession(){assertEquals(ConnectDecision.WRONG_SESSION,validate(command.copy(sessionId="session_token_0002")))}
    @Test fun rejectsExpired(){assertEquals(ConnectDecision.EXPIRED,validate(command.copy(expiresAtMillis=now)))}
    @Test fun rejectsFutureClock(){assertEquals(ConnectDecision.EXPIRED,validate(command.copy(issuedAtMillis=now+6000,expiresAtMillis=now+9000)))}
    @Test fun rejectsReplay(){assertEquals(ConnectDecision.REPLAY,validate(command.copy(sequence=7)))}
    @Test fun rejectsLongLifetime(){assertEquals(ConnectDecision.INVALID,validate(command.copy(expiresAtMillis=now+60000)))}
    @Test fun checksSeekBounds(){
        val seek=command.copy(action=ConnectAction.SEEK,positionMillis=5000)
        assertEquals(ConnectDecision.ACCEPT,validate(seek))
        assertEquals(ConnectDecision.INVALID,validate(seek.copy(positionMillis=11000)))
        assertEquals(ConnectDecision.INVALID,validate(seek,duration=null))
    }
    @Test fun rejectsPositionOnPause(){assertEquals(ConnectDecision.INVALID,validate(command.copy(positionMillis=0)))}
}
