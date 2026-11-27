package br.com.timachado.neromusic

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal enum class NeroInstallPhase { OFFERED, DOWNLOADING, VERIFYING, READY, FAILED }
internal data class NeroInstallState(
    val update:NeroUpdate,
    val phase:NeroInstallPhase=NeroInstallPhase.OFFERED,
    val bytes:Long=0,
    val total:Long=0,
    val message:String?=null
)
internal fun neroTrustedApkUrl(url:URL):Boolean =
    url.protocol=="https" && url.userInfo==null && (url.port==-1||url.port==443) &&
        url.host.lowercase(java.util.Locale.ROOT) in setOf("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com","github-releases.githubusercontent.com")
internal fun neroDigestHex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it.toInt() and 0xff)}

internal object NeroUpdateInstaller {
    private const val limit=256L*1024*1024
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val mutable=MutableStateFlow<NeroInstallState?>(null)
    val state=mutable.asStateFlow()
    private var task:Job?=null
    private fun busy()=task?.isActive==true
    private fun directory(c:Context)=File(c.cacheDir,"nero-updates")
    private fun apk(c:Context,u:NeroUpdate)=File(directory(c),"nero-"+u.code+"-"+u.sha256+".apk")

    fun offer(u:NeroUpdate){if(mutable.value==null&&!busy())mutable.value=NeroInstallState(u)}
    fun close(){if(!busy())mutable.value=null}
    fun cancel(){task?.cancel()}

    fun start(context:Context,u:NeroUpdate){
        if(busy())return
        val app=context.applicationContext
        val current=mutable.value
        if(current!=null&&current.update==u&&current.phase==NeroInstallPhase.READY&&apk(app,u).isFile)return
        mutable.value=NeroInstallState(u,NeroInstallPhase.DOWNLOADING)
        task=scope.launch{
            val target=apk(app,u)
            val partial=File(target.parentFile,target.name+".part.apk")
            try{
                withContext(Dispatchers.IO){
                    require(u.code>BuildConfig.VERSION_CODE&&u.sha256.matches(Regex("[a-f0-9]{64}")))
                    val dir=directory(app);check(dir.isDirectory||dir.mkdirs())
                    if(target.isFile){
                        mutable.value=NeroInstallState(u,NeroInstallPhase.VERIFYING)
                        if(runCatching{verify(app,u,target)}.isSuccess)return@withContext
                        check(target.delete())
                    }
                    partial.delete()
                    dir.listFiles()?.filter{it.isFile&&it!=target&&it!=partial}?.forEach{it.delete()}
                    val conn=openDownload(URL(u.url))
                    try{
                        val total=conn.contentLengthLong
                        check(total<=limit)
                        var received=0L
                        var lastUpdate=0L
                        conn.inputStream.use{input->
                            partial.outputStream().buffered().use{output->
                                val buffer=ByteArray(64*1024)
                                while(true){
                                    ensureActive()
                                    val count=input.read(buffer)
                                    if(count<0)break
                                    received+=count
                                    check(received<=limit)
                                    output.write(buffer,0,count)
                                    val now=android.os.SystemClock.elapsedRealtime()
                                    if(now-lastUpdate>=150){
                                        mutable.value=NeroInstallState(u,NeroInstallPhase.DOWNLOADING,received,total)
                                        lastUpdate=now
                                    }
                                }
                            }
                        }
                        check(received>0&&(total<=0||received==total))
                        mutable.value=NeroInstallState(u,NeroInstallPhase.VERIFYING,received,total)
                        verify(app,u,partial)
                        ensureActive()
                        check(partial.renameTo(target))
                    }finally{conn.disconnect()}
                }
                mutable.value=NeroInstallState(u,NeroInstallPhase.READY,message="Download concluído. APK verificado e pronto para instalar.")
            }catch(e:CancellationException){
                mutable.value=NeroInstallState(u,message="Download cancelado.")
                throw e
            }catch(_:Exception){
                mutable.value=NeroInstallState(u,NeroInstallPhase.FAILED,message="Não foi possível baixar ou validar a atualização. Confira a conexão e tente novamente.")
            }finally{withContext(NonCancellable+Dispatchers.IO){partial.delete()}}
        }
    }
    private fun openDownload(initial:URL):HttpURLConnection{
        var url=initial
        repeat(6){
            check(neroTrustedApkUrl(url))
            val conn=url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects=false
            conn.connectTimeout=15000;conn.readTimeout=20000;conn.useCaches=false
            conn.setRequestProperty("Accept-Encoding","identity")
            try{
                when(conn.responseCode){
                    200->return conn
                    301,302,303,307,308->{
                        val next=conn.getHeaderField("Location")?:error("Missing redirect")
                        url=URL(url,next)
                    }
                    else->error("Download unavailable")
                }
            }catch(e:Exception){conn.disconnect();throw e}
            conn.disconnect()
        }
        error("Too many redirects")
    }
    @Suppress("DEPRECATION")
    private fun signatures(info:PackageInfo):Set<String>{
        val items=if(Build.VERSION.SDK_INT>=28)info.signingInfo?.apkContentsSigners else info.signatures
        return items.orEmpty().map{neroDigestHex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))}.toSet()
    }
    @Suppress("DEPRECATION")
    private fun version(info:PackageInfo)=if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION")
    private fun verify(c:Context,u:NeroUpdate,file:File){
        check(file.isFile&&file.length() in 1L..limit)
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use{input->
            val buffer=ByteArray(64*1024)
            while(true){val count=input.read(buffer);if(count<0)break;digest.update(buffer,0,count)}
        }
        check(neroDigestHex(digest.digest())==u.sha256)
        val pm=c.packageManager
        val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val incoming=pm.getPackageArchiveInfo(file.absolutePath,flags)?:error("Invalid APK")
        val installed=pm.getPackageInfo(c.packageName,flags)
        check(incoming.packageName==c.packageName&&version(incoming)==u.code.toLong()&&version(incoming)>version(installed)&&incoming.versionName==u.name)
        val expected=signatures(installed)
        check(expected.isNotEmpty()&&signatures(incoming)==expected)
    }
    fun install(context:Context,u:NeroUpdate){
        val current=mutable.value?:return
        if(current.update!=u||current.phase!=NeroInstallPhase.READY||busy())return
        val file=apk(context,u)
        if(!file.isFile){start(context,u);return}
        try{
            if(!context.packageManager.canRequestPackageInstalls()){
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                mutable.value=current.copy(message="Autorize instalações desta fonte no Android, volte ao NERO e toque em Instalar atualização.")
                return
            }
            val uri=FileProvider.getUriForFile(context,context.packageName+".sharing",file)
            val intent=Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            mutable.value=current.copy(message="Confirme a instalação na tela do Android.")
        }catch(_:Exception){mutable.value=current.copy(message="Não foi possível abrir o instalador. Tente novamente.")}
    }
}
