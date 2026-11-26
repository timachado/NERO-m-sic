package br.com.timachado.neromusic

import java.text.NumberFormat
import java.util.Locale

internal data class NeroOffer(val id:String,val label:String,val months:Int,val cents:Int){
    val price:String get()=NumberFormat.getCurrencyInstance(Locale("pt","BR")).format(cents/100.0)
    val savingsPercent:Int get()=((NeroPlans.monthly.cents*months-cents)*100)/(NeroPlans.monthly.cents*months)
}
internal object NeroPlans{
    const val TRIAL_DAYS=7
    const val GRACE_HOURS=48
    const val SITE="https://timachado.ifree.page/"
    val monthly=NeroOffer("premium_monthly","Mensal",1,990)
    val quarterly=NeroOffer("premium_quarterly","Trimestral",3,2490)
    val annual=NeroOffer("premium_annual","Anual",12,8990)
    val offers=listOf(monthly,quarterly,annual)
    // Preview only. Entitlement is granted exclusively by the server.
}
internal fun neroPlanStatus(status:String):String=when(status){
    "active"->"Assinatura ativa"
    "none"->"Sem assinatura ativa"
    "expired"->"Assinatura expirada"
    else->"Não verificado"
}
