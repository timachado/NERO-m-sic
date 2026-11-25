package br.com.timachado.neromusic

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal fun wifiOnlyDownloads(context:Context):Boolean =
    context.getSharedPreferences("nero_downloads",Context.MODE_PRIVATE).getBoolean("wifi_only",true)

internal fun setWifiOnlyDownloads(context:Context,value:Boolean){
    context.getSharedPreferences("nero_downloads",Context.MODE_PRIVATE).edit().putBoolean("wifi_only",value).apply()
}

internal class WifiDownloadRequired:IllegalStateException("Conecte-se a uma rede Wi-Fi para baixar.")

internal fun requireDownloadNetwork(context:Context){
    if(!wifiOnlyDownloads(context))return
    val manager=context.getSystemService(ConnectivityManager::class.java)
    val network=manager?.activeNetwork
    val caps=network?.let{manager.getNetworkCapabilities(it)}
    if(caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)!=true)throw WifiDownloadRequired()
}

internal class DownloadNetworkGuard(private val context:Context){
    private var lastCheck=0L
    fun check(force:Boolean=false){
        val now=android.os.SystemClock.elapsedRealtime()
        if(force||now-lastCheck>=1000L){
            requireDownloadNetwork(context)
            lastCheck=now
        }
    }
}
