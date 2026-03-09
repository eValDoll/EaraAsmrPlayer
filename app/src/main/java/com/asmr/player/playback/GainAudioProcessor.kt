package com.asmr.player.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class GainAudioProcessor : BaseAudioProcessor() {
    @Volatile
    private var gain: Float = 1f
    private var passthrough = false

    fun setGain(gain: Float) {
        this.gain = gain.coerceIn(0f, 4f)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        passthrough =
            (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT &&
                inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_FLOAT)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val g = gain
        val count = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(count)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        if (passthrough || g == 1f) {
            outputBuffer.put(inputBuffer)
        } else {
            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            val encoding = inputAudioFormat.encoding
            if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
                while (inputBuffer.remaining() >= 4) {
                    val v = inputBuffer.float
                    outputBuffer.putFloat((v * g).coerceIn(-1f, 1f))
                }
            } else {
                while (inputBuffer.remaining() >= 2) {
                    val v = inputBuffer.short
                    val out = (v * g).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    outputBuffer.putShort(out)
                }
            }
        }
        outputBuffer.flip()
    }
}
