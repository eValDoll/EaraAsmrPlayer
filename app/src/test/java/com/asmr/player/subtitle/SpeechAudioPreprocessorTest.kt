package com.asmr.player.subtitle

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechAudioPreprocessorTest {
    @Test
    fun `constant dc offset is removed`() {
        val result = SpeechAudioPreprocessor.process(
            samples = FloatArray(16_000) { 0.25f },
            sampleRateHz = 16_000
        )

        assertTrue(result.maxOf { abs(it) } < 0.0001f)
    }

    @Test
    fun `quiet speech-like signal is raised without clipping`() {
        val input = FloatArray(16_000) { index ->
            (0.01 * sin(2.0 * PI * 440.0 * index / 16_000.0)).toFloat()
        }

        val result = SpeechAudioPreprocessor.process(input, 16_000)

        assertTrue(rms(result) > rms(input) * 3.5f)
        assertTrue(result.maxOf { abs(it) } <= 0.95f)
    }

    private fun rms(samples: FloatArray): Float {
        return kotlin.math.sqrt(samples.sumOf { it.toDouble() * it } / samples.size).toFloat()
    }
}
