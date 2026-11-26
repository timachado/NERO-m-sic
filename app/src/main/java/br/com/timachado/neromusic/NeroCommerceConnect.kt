package br.com.timachado.neromusic

// The existing App Commerce Core resolves the NERO app and its WooCommerce product.
// No account linking, personal data, payment tokens, price or numeric ID sent by the APK.
internal fun neroCheckoutUrl(planId:String):String? {
    val plan=when(planId){
        "premium_monthly"->"monthly"
        "premium_quarterly"->"quarterly"
        "premium_annual"->"yearly"
        else->return null
    }
    return "https://timachado.ifree.page/?tiac_nero_plan="+plan
}

/** Accept only the short-lived opaque ticket URL returned by the NERO server. */
internal fun neroValidatedCheckoutHandoff(url:String):String? = url.takeIf{
    it.matches(Regex("^https://timachado\\.ifree\\.page/\\?tiac_nero_handoff=[A-Za-z0-9_-]{70,2500}$"))
}
