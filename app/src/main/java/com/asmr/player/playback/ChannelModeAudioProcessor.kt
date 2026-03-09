package com.asmr.player.playback

import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class ChannelModeAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var mode: Int = 0

    private var passthrough = false

    fun setMode(mode: Int) {
        this.mode = mode.coerceIn(0, 3)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        passthrough = inputAudioFormat.channelCount != 2 ||
            (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT &&
                inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_FLOAT)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val count = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(count)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val m = mode
        if (passthrough || m == 0) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val encoding = inputAudioFormat.encoding
        if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            while (inputBuffer.remaining() >= 8) {
                val left = inputBuffer.float
                val right = inputBuffer.float
                when (m) {
                    1 -> {
                        outputBuffer.putFloat(right.coerceIn(-1f, 1f))
                        outputBuffer.putFloat(left.coerceIn(-1f, 1f))
                    }
                    2 -> {
                        val v = left.coerceIn(-1f, 1f)
                        outputBuffer.putFloat(v)
                        outputBuffer.putFloat(v)
                    }
                    3 -> {
                        val v = right.coerceIn(-1f, 1f)
                        outputBuffer.putFloat(v)
                        outputBuffer.putFloat(v)
                    }
                    else -> {
                        outputBuffer.putFloat(left.coerceIn(-1f, 1f))
                        outputBuffer.putFloat(right.coerceIn(-1f, 1f))
                    }
                }
            }
            if (inputBuffer.hasRemaining()) {
                outputBuffer.put(inputBuffer)
            }
        } else {
            while (inputBuffer.remaining() >= 4) {
                val left = inputBuffer.short
                val right = inputBuffer.short
                when (m) {
                    1 -> {
                        outputBuffer.putShort(right)
                        outputBuffer.putShort(left)
                    }
                    2 -> {
                        outputBuffer.putShort(left)
                        outputBuffer.putShort(left)
                    }
                    3 -> {
                        outputBuffer.putShort(right)
                        outputBuffer.putShort(right)
                    }
                    else -> {
                        outputBuffer.putShort(left)
                        outputBuffer.putShort(right)
                    }
                }
            }
            if (inputBuffer.hasRemaining()) {
                outputBuffer.put(inputBuffer)
            }
        }
        outputBuffer.flip()
    }
}

