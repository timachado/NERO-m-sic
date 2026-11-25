package br.com.timachado.neromusic

import java.net.URI

internal object ShareLinks{
    private val token=Regex("[a-f0-9]{32}")
    val configured:Boolean get()=BuildConfig.SHARE_HOST.isNotBlank()
    fun url(id:String):String{
        require(configured&&token.matches(id))
        return "https://"+BuildConfig.SHARE_HOST+"/s/"+id
    }
    fun parse(input:String):String?=parseForHost(input,BuildConfig.SHARE_HOST)
    fun parseForHost(input:String,host:String):String?{
        if(host.isBlank()||input.length>512)return null
        val uri=runCatching{URI(input.trim())}.getOrNull()?:return null
        if(uri.scheme!="https"||uri.host!=host||uri.port!=-1||uri.userInfo!=null||uri.rawQuery!=null||uri.rawFragment!=null)return null
        val path=uri.rawPath?:return null
        if(!path.startsWith("/s/"))return null
        return path.removePrefix("/s/").takeIf{token.matches(it)}
    }
}
