package com.asmr.player.subtitle

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingLinearResamplerTest {
    @Test
    fun `48 kHz input is downsampled to 16 kHz`() {
        val resampler = StreamingLinearResampler(
            inputSampleRate = 48_000,
            outputSampleRate = 16_000,
            outputChunkSize = 16_000
        )

        val completed = resampler.consume(FloatArray(48_000) { it / 48_000f })

        assertEquals(1, completed.size)
        assertEquals(16_000, completed.single().size)
        assertNull(resampler.finish())
    }

    @Test
    fun `completed chunks and final remainder preserve samples at 16 kHz`() {
        val resampler = StreamingLinearResampler(
            inputSampleRate = 16_000,
            outputSampleRate = 16_000,
            outputChunkSize = 4
        )
        val samples = floatArrayOf(-1f, -0.5f, 0f, 0.5f, 0.75f, 1f)

        val completed = resampler.consume(samples)

        assertEquals(1, completed.size)
        assertArrayEquals(samples.copyOfRange(0, 4), completed.single(), 0.0001f)
        assertArrayEquals(samples.copyOfRange(4, 6), resampler.finish(), 0.0001f)
    }

    @Test
    fun `long chunk is split at the quietest eligible window`() {
        val resampler = StreamingLinearResampler(
            inputSampleRate = 16_000,
            outputSampleRate = 16_000,
            outputChunkSize = 10,
            minimumOutputChunkSize = 6,
            silenceWindowSize = 2
        )
        val samples = floatArrayOf(1f, 1f, 1f, 1f, 1f, 0f, 0f, 1f, 1f, 1f)

        val completed = resampler.consume(samples)

        assertEquals(1, completed.size)
        assertEquals(6, completed.single().size)
        assertArrayEquals(samples.copyOfRange(6, 10), resampler.finish(), 0.0001f)
    }
}
