package br.com.timachado.neromusic

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.roundToInt

internal object NeroAudioSettings {
    @Volatile var leveling=false
    fun load(context:Context){leveling=context.getSharedPreferences("nero_audio",Context.MODE_PRIVATE).getBoolean("leveling",false)}
    fun setLeveling(context:Context,value:Boolean){
        leveling=value
        context.getSharedPreferences("nero_audio",Context.MODE_PRIVATE).edit().putBoolean("leveling",value).apply()
    }
}

// Attenuation only: conservative short-term RMS leveling, not integrated LUFS.
internal class NeroLeveler {
    private var gain=1.0
    fun reset(){gain=1.0}
    fun process(samples:ShortArray,rate:Int,channels:Int){
        if(samples.isEmpty()||rate<=0||channels<=0)return
        var energy=0.0
        for(sample in samples){val v=sample/32768.0;energy+=v*v}
        val rms=sqrt(energy/samples.size)
        val target=if(rms<0.005)gain else (0.125/rms).coerceIn(0.25,1.0)
        val seconds=samples.size.toDouble()/channels/rate
        val smoothing=1-exp(-seconds/(if(target<gain)0.08 else 1.2))
        val next=(gain+(target-gain)*smoothing).coerceIn(0.25,1.0)
        val frames=(samples.size/channels).coerceAtLeast(1)
        for(i in samples.indices){
            val frame=i/channels
            val g=gain+(next-gain)*(frame+1).coerceAtMost(frames)/frames
            samples[i]=(samples[i]*g).roundToInt().coerceIn(-32768,32767).toShort()
        }
        gain=next
    }
}
@UnstableApi
internal class NeroLevelingProcessor:BaseAudioProcessor(){
    private val leveler=NeroLeveler()
    private var enabledPreviously=false
    override fun onConfigure(inputAudioFormat:AudioProcessor.AudioFormat):AudioProcessor.AudioFormat =
        if(inputAudioFormat.encoding==C.ENCODING_PCM_16BIT)inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer:ByteBuffer){
        val output=replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        val enabled=NeroAudioSettings.leveling
        if(!enabled){
            if(enabledPreviously)leveler.reset()
            enabledPreviously=false
            output.put(inputBuffer)
        }else{
            if(!enabledPreviously)leveler.reset()
            enabledPreviously=true
            val count=inputBuffer.remaining()/2
            val samples=ShortArray(count)
            inputBuffer.order(ByteOrder.nativeOrder())
            for(i in samples.indices)samples[i]=inputBuffer.short
            leveler.process(samples,inputAudioFormat.sampleRate,inputAudioFormat.channelCount)
            for(value in samples)output.putShort(value)
            if(inputBuffer.hasRemaining())output.put(inputBuffer)
        }
        output.flip()
    }
    override fun onFlush(){leveler.reset();enabledPreviously=false}
}
