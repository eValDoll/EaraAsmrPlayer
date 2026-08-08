package com.asmr.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechRegionTest {
    @Test
    fun `regions from two channels are padded and merged on one timeline`() {
        val result = mergeSpeechRegions(
            regions = listOf(
                SpeechRegion(startSample = 16_000, endSample = 24_000),
                SpeechRegion(startSample = 22_400, endSample = 32_000)
            ),
            totalSamples = 48_000,
            sampleRateHz = 16_000
        )

        assertEquals(
            listOf(SpeechRegion(startSample = 12_000, endSample = 37_600)),
            result
        )
    }

    @Test
    fun `continuous regions stay merged before acoustic splitting`() {
        val result = mergeSpeechRegions(
            regions = listOf(SpeechRegion(startSample = 0, endSample = 40_000)),
            totalSamples = 40_000,
            sampleRateHz = 1_000,
            preRollMs = 0,
            postRollMs = 0,
            mergeGapMs = 0
        )

        assertEquals(listOf(SpeechRegion(0, 40_000)), result)
    }

    @Test
    fun `long region is split at a real quiet point shared by both channels`() {
        val left = FloatArray(12_000) { 1f }
        val right = FloatArray(12_000) { 0.8f }
        for (index in 4_800 until 5_200) {
            left[index] = 0f
            right[index] = 0f
        }

        val result = splitSpeechRegionsAtQuietPoints(
            regions = listOf(SpeechRegion(0, 12_000)),
            channels = listOf(left, right),
            sampleRateHz = 1_000,
            minimumRegionMs = 4_000,
            maximumRegionMs = 7_000,
            analysisWindowMs = 80,
            analysisStepMs = 40
        )

        assertEquals(2, result.size)
        assert(result.first().endSample in 4_800..5_200)
        assertEquals(result.first().endSample, result.last().startSample)
        assertEquals(12_000, result.last().endSample)
    }

    @Test
    fun `acoustic splitting does not leave a tiny trailing slice`() {
        val samples = FloatArray(8_000) { 1f }

        val result = splitSpeechRegionsAtQuietPoints(
            regions = listOf(SpeechRegion(0, 8_000)),
            channels = listOf(samples),
            sampleRateHz = 1_000,
            minimumRegionMs = 4_000,
            maximumRegionMs = 7_000
        )

        assertEquals(listOf(SpeechRegion(0, 4_000), SpeechRegion(4_000, 8_000)), result)
    }
}
