package br.com.timachado.neromusic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

// Foundation only: no UI entry point, no background listener and no network calls.
// The future Identify screen must obtain explicit microphone consent first.
internal object NeroIdentifySample {
    const val SAMPLE_RATE = 16000
    const val SECONDS = 8
    const val MAX_SAMPLES = SAMPLE_RATE * SECONDS

    fun wav(pcm:ShortArray):ByteArray {
        require(pcm.isNotEmpty() && pcm.size <= MAX_SAMPLES)
        val bytes = pcm.size * 2
        return ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + bytes)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1.toShort()); putShort(1.toShort())
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2)
            putShort(2.toShort()); putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII)); putInt(bytes)
            pcm.forEach { putShort(it) }
        }.array()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun record(context:Context):ByteArray = withContext(Dispatchers.IO) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0)
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 4096))
        val sample = ShortArray(MAX_SAMPLES)
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED)
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            var offset = 0
            val started = SystemClock.elapsedRealtime()
            while(offset < sample.size) {
                currentCoroutineContext().ensureActive()
                check(SystemClock.elapsedRealtime() - started < 12000L)
                val count = recorder.read(sample, offset, minOf(2048, sample.size - offset), AudioRecord.READ_NON_BLOCKING)
                check(count >= 0)
                if(count == 0) delay(20L) else offset += count
            }
            currentCoroutineContext().ensureActive()
            wav(sample)
        } finally {
            runCatching { if(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
            recorder.release()
            sample.fill(0)
        }
    }
}
