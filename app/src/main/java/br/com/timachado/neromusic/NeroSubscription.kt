package br.com.timachado.neromusic

import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class NeroSubscriptionPhase(val label:String,val premium:Boolean){
    FREE("Sem assinatura ativa",false),
    ACTIVE("Assinatura ativa",true),
    TRIAL("Experiência Premium ativa",true),
    GRACE("Em período de carência",true),
    EXPIRED("Assinatura expirada",false),
    REVOKED("Acesso revogado",false),
    UNKNOWN("Não verificado",false)
}
internal data class NeroSubscription(val phase:NeroSubscriptionPhase,val planId:String?,val until:Long?,val autoRenew:Boolean?){
    val planLabel:String? get()=NeroPlans.offers.firstOrNull{it.id==planId}?.label
    fun dateLabel():String?=until?.let{runCatching{Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",Locale("pt","BR")))}.getOrNull()}
}
// Accept the existing read-only RPC contract; new fields are optional for
// older servers. Only an explicit active status can authorize Premium.
internal fun subscriptionPhase(status:String,phase:String?):NeroSubscriptionPhase=when(status){
    "active"->when(phase){
        null,"active"->NeroSubscriptionPhase.ACTIVE
        "trial"->NeroSubscriptionPhase.TRIAL
        "grace"->NeroSubscriptionPhase.GRACE
        else->NeroSubscriptionPhase.UNKNOWN
    }
    "expired"->if(phase==null||phase=="expired")NeroSubscriptionPhase.EXPIRED else NeroSubscriptionPhase.UNKNOWN
    "none"->when(phase){null,"free"->NeroSubscriptionPhase.FREE;"revoked"->NeroSubscriptionPhase.REVOKED;else->NeroSubscriptionPhase.UNKNOWN}
    else->NeroSubscriptionPhase.UNKNOWN
}
internal fun decodeNeroSubscription(row:JSONObject):NeroSubscription{
    val phase=subscriptionPhase(row.optString("status"),if(row.has("phase")&&!row.isNull("phase"))row.optString("phase")else null)
    val plan=row.optString("plan_id").takeIf{id->NeroPlans.offers.any{it.id==id}}
    val expires=row.optLong("access_until",0L).takeIf{it in 1L..253402300799L}
    val auto=row.opt("auto_renew") as? Boolean
    return NeroSubscription(phase,plan,expires,auto)
}
internal suspend fun readNeroSubscription(context:Context,uid:String):NeroSubscription?=withTimeoutOrNull(12000L){
    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    val current=NeroSupabase.session(context,false)
    require(current.uid==uid)
    val result=decodeNeroSubscription(JSONObject(NeroSupabase.rpc("nero_plan_read_v1",JSONObject(),current.access)))
    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    result
}

// Only a trusted server can create a trial; the client sends no plan, UID or duration.
internal suspend fun startNeroTrial(context:Context,uid:String):String=withTimeoutOrNull(12000L){
    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    val current=NeroSupabase.session(context,false)
    require(current.uid==uid)
    val result=JSONObject(NeroSupabase.rpc("nero_trial_start_v1",JSONObject(),current.access)).getString("result")
    require(result in setOf("started","ineligible"))
    require(NeroSupabase.identityReady.value&&NeroSupabase.activeUid.value==uid)
    result
}?:error("Trial service timed out")
