package com.asmr.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

class VolumeThresholdAudioProcessorTest {

    @Test
    fun peakSafetyCapsBoostToPreventClipping() {
        val processor = VolumeThresholdAudioProcessor().apply {
            setEnabled(true)
            setMode(VolumeThresholdAudioProcessor.MODE_THRESHOLD)
            setThresholds(minDb = -12f, maxDb = 0f)
        }
        processor.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()

        val frames = 48_000
        val inSamples = FloatArray(frames * 2) { 0.1f }
        inSamples[0] = 0.9f
        inSamples[1] = 0.9f

        val out = processFloat(processor, inSamples)
        var peak = 0f
        for (v in out) peak = max(peak, abs(v))
        assertTrue("output clipped: peak=$peak", peak <= 1.0f)
        assertTrue("peak safety not applied: peak=$peak", peak <= 0.96f)
    }

    @Test
    fun doesNotBoostBelowNoiseFloor() {
        val processor = VolumeThresholdAudioProcessor().apply {
            setEnabled(true)
            setMode(VolumeThresholdAudioProcessor.MODE_THRESHOLD)
            setThresholds(minDb = -24f, maxDb = -6f)
        }
        processor.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()

        val frames = 2000
        val inSamples = FloatArray(frames * 2) { 1e-5f }
        val out = processFloat(processor, inSamples)

        assertEquals(inSamples.size, out.size)
        for (i in out.indices) {
            assertTrue(abs(out[i] - inSamples[i]) < 1e-6f)
        }
    }

    @Test
    fun loudnessModeMovesTowardTargetLevel() {
        val processor = VolumeThresholdAudioProcessor().apply { setEnabled(true) }
        processor.setMode(VolumeThresholdAudioProcessor.MODE_LOUDNESS)
        processor.setLoudnessTargetDb(-18f)
        processor.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()

        val framesPerBuffer = 4_800
        val buffers = 40
        val inSamples = FloatArray(framesPerBuffer * 2) { 0.05f }
        var out = FloatArray(0)
        repeat(buffers) {
            out = processFloat(processor, inSamples)
        }
        val rms = computeRms(out)
        val db = if (rms <= 1e-9f) -120f else (20f * kotlin.math.log10(rms.toDouble()).toFloat())
        assertTrue("loudness not close to target: db=$db", db > -22f && db < -14f)
    }

    @Test
    fun resetForNewItemClearsCarryoverGain() {
        val processor = VolumeThresholdAudioProcessor().apply { setEnabled(true) }
        processor.setMode(VolumeThresholdAudioProcessor.MODE_LOUDNESS)
        processor.setLoudnessTargetDb(-18f)
        processor.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()

        val framesPerBuffer = 4_800
        val buffers = 30
        val inSamples = FloatArray(framesPerBuffer * 2) { 0.05f }
        var out = FloatArray(0)
        repeat(buffers) {
            out = processFloat(processor, inSamples)
        }
        val rmsBefore = computeRms(out)

        processor.resetForNewItem()
        val outAfter = processFloat(processor, inSamples)
        val rmsAfter = computeRms(outAfter)

        assertTrue("reset did not reduce gain enough: before=$rmsBefore after=$rmsAfter", rmsAfter < rmsBefore * 0.85f)
    }

    private fun processFloat(processor: VolumeThresholdAudioProcessor, samples: FloatArray): FloatArray {
        val input = ByteBuffer.allocateDirect(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in samples) input.putFloat(v)
        input.flip()

        processor.queueInput(input)
        val outBuf = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(outBuf.remaining() / 4)
        var i = 0
        while (outBuf.remaining() >= 4) {
            out[i++] = outBuf.float
        }
        return out
    }

    private fun computeRms(samples: FloatArray): Float {
        var sumSq = 0.0
        var n = 0
        for (v in samples) {
            val d = v.toDouble()
            sumSq += d * d
            n++
        }
        return if (n <= 0) 0f else kotlin.math.sqrt(sumSq / n.toDouble()).toFloat()
    }
}
