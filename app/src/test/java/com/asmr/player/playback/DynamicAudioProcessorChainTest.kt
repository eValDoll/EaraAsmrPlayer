package com.asmr.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class DynamicAudioProcessorChainTest {

    @Test
    fun inactiveProcessorsAreSkippedAndPcmIsPreserved() {
        val first = RecordingProcessor(active = false, delta = 10)
        val second = RecordingProcessor(active = false, delta = 20)
        val chain = configuredChain(first, second)
        val input = byteArrayOf(1, 2, 3, 4)
        val inputBuffer = input.toDirectBuffer()

        chain.queueInput(inputBuffer)
        val output = chain.output

        assertEquals(0, first.inputCount)
        assertEquals(0, second.inputCount)
        assertEquals(inputBuffer.limit(), inputBuffer.position())
        inputBuffer.put(0, 9)
        assertEquals(9, output.get(0).toInt())
        assertArrayEquals(byteArrayOf(9, 2, 3, 4), output.toByteArray())
    }

    @Test
    fun activeProcessorsRunOnceInDeclaredOrder() {
        val first = RecordingProcessor(active = true, delta = 1)
        val skipped = RecordingProcessor(active = false, delta = 40)
        val second = RecordingProcessor(active = true, delta = 2)
        val chain = configuredChain(first, skipped, second)

        chain.queueInput(byteArrayOf(3, 7).toDirectBuffer())

        assertEquals(1, first.inputCount)
        assertEquals(0, skipped.inputCount)
        assertEquals(1, second.inputCount)
        assertArrayEquals(byteArrayOf(6, 10), chain.output.toByteArray())
    }

    private fun configuredChain(vararg processors: RuntimeAudioProcessor): DynamicAudioProcessorChain {
        return DynamicAudioProcessorChain(processors).apply {
            configure(AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
            flush()
        }
    }

    private class RecordingProcessor(
        private val active: Boolean,
        private val delta: Int,
    ) : BaseAudioProcessor(), RuntimeAudioProcessor {
        var inputCount = 0
            private set

        override fun isRuntimeActive(): Boolean = active

        override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat = inputAudioFormat

        override fun queueInput(inputBuffer: ByteBuffer) {
            inputCount += 1
            val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
            while (inputBuffer.hasRemaining()) {
                outputBuffer.put((inputBuffer.get().toInt() + delta).toByte())
            }
            outputBuffer.flip()
        }
    }
}

private fun ByteArray.toDirectBuffer(): ByteBuffer =
    ByteBuffer.allocateDirect(size).apply {
        put(this@toDirectBuffer)
        flip()
    }

private fun ByteBuffer.toByteArray(): ByteArray =
    ByteArray(remaining()).also(::get)
