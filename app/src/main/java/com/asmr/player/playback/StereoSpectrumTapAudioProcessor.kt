package com.asmr.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

@UnstableApi
class StereoSpectrumTapAudioProcessor(
    private val pcmBuffer: StereoPcmRingBuffer,
    private val onAudioFormat: (sampleRate: Int) -> Unit
) : BaseAudioProcessor() {

    private val inv32768 = 1f / 32768f
    private var tapEnabled = false

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        tapEnabled = inputAudioFormat.channelCount == 2 && inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        if (tapEnabled) {
            onAudioFormat(inputAudioFormat.sampleRate)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        if (tapEnabled) {
            val start = inputBuffer.position()
            val end = inputBuffer.limit()

            var i = start
            while (i + 3 < end) {
                val left = readShortLE(inputBuffer, i)
                val right = readShortLE(inputBuffer, i + 2)
                pcmBuffer.writeNormalized(left * inv32768, right * inv32768)
                i += 4
            }
        }

        val count = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(count)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    override fun onFlush() {
        pcmBuffer.resetWriteCursor()
    }

    override fun onReset() {
        pcmBuffer.reset()
    }

    private fun readShortLE(buffer: ByteBuffer, index: Int): Float {
        val b0 = buffer.get(index).toInt() and 0xFF
        val b1 = buffer.get(index + 1).toInt()
        val v = (b0 or (b1 shl 8)).toShort().toInt()
        return v.toFloat()
    }
}
