package br.com.timachado.neromusic

import android.content.Context
import android.view.KeyEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Independent RenderingControl only. Never claims ownership of WVC playback.
 * Main-thread entry points; one bounded request at a time; no persistent target.
 */
internal object NeroWvcVolume {
    data class State(val target:NeroVolumeDevice?=null,val value:Int?=null,val busy:Boolean=false,val notice:String?=null)
    private val mutable=MutableStateFlow(State())
    val state=mutable.asStateFlow()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var generation=0L
    private var pending=0
    private var worker:Job?=null
    private var consumedKey:Int?=null

    fun clear(){
        generation++
        pending=0
        mutable.value=State()
    }
    fun select(context:Context,target:NeroVolumeDevice){
        if(worker?.isActive==true)return
        val id=++generation
        pending=0
        mutable.value=State(target=target,busy=true)
        val app=context.applicationContext
        worker=scope.launch{
            try{
                val value=withContext(Dispatchers.IO){NeroDlna.getVolume(app,target)}
                if(id==generation)mutable.value=State(target,value)
            }catch(e:CancellationException){throw e}
            catch(_:Exception){if(id==generation)mutable.value=State(notice="TV não confirmou a leitura do volume.")}
        }
    }
    fun adjust(context:Context,delta:Int):Boolean{
        require(delta in -5..5&&delta!=0)
        val current=mutable.value
        val target=current.target?:return false
        if(current.value==null)return false
        pending=(pending+delta).coerceIn(-10,10)
        if(worker?.isActive==true)return true
        val id=generation
        val app=context.applicationContext
        worker=scope.launch{
            try{
                while(id==generation&&pending!=0){
                    val step=pending
                    pending=0
                    mutable.value=mutable.value.copy(busy=true,notice=null)
                    val confirmed=withContext(Dispatchers.IO){NeroDlna.changeVolume(app,target,step)}
                    if(id==generation)mutable.value=State(target,confirmed)
                }
            }catch(e:CancellationException){throw e}
            catch(_:Exception){
                if(id==generation){
                    pending=0
                    mutable.value=State(notice="TV não confirmou o volume. Controle do telefone restaurado.")
                }
            }
        }
        return true
    }
    fun key(context:Context,event:KeyEvent):Boolean{
        val delta=when(event.keyCode){
            KeyEvent.KEYCODE_VOLUME_UP->2
            KeyEvent.KEYCODE_VOLUME_DOWN->-2
            else->return false
        }
        if(event.action==KeyEvent.ACTION_UP&&consumedKey==event.keyCode){
            consumedKey=null
            return true
        }
        if(event.action!=KeyEvent.ACTION_DOWN)return false
        if(!adjust(context,delta))return false
        consumedKey=event.keyCode
        return true
    }
}
