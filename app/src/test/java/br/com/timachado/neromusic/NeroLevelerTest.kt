package br.com.timachado.neromusic

import org.junit.Assert.*
import org.junit.Test

class NeroLevelerTest {
    @Test fun quietAudioRemainsUnchanged(){
        val leveler=NeroLeveler()
        val samples=ShortArray(4800){1000}
        leveler.process(samples,48000,1)
        assertTrue(samples.all{it==1000.toShort()})
    }
    @Test fun loudAudioAttenuatesWithoutClipping(){
        val leveler=NeroLeveler()
        val samples=ShortArray(48000){if(it%2==0)30000 else -30000}
        leveler.process(samples,48000,1)
        assertTrue(samples.all{kotlin.math.abs(it.toInt())<=30000})
        assertTrue(kotlin.math.abs(samples.last().toInt())<10000)
    }
    @Test fun stereoChannelsUseSameGain(){
        val samples=ShortArray(9600){12000}
        NeroLeveler().process(samples,48000,2)
        for(i in 0 until samples.size step 2)assertEquals(samples[i],samples[i+1])
    }
    @Test fun resetRestoresUnityForQuietAudio(){
        val leveler=NeroLeveler()
        leveler.process(ShortArray(48000){30000},48000,1)
        leveler.reset()
        val samples=ShortArray(4800){500}
        leveler.process(samples,48000,1)
        assertTrue(samples.all{it==500.toShort()})
    }
}
