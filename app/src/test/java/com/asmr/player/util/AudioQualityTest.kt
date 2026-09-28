package com.asmr.player.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioQualityTest {
    @Test
    fun hqRequiresBothMeasuredSampleRateAndBitrate() {
        assertEquals(AudioQuality.HQ, AudioTechnicalMetadata(sampleRate = 44_100, bitrate = 192_000).quality)
        assertNull(AudioTechnicalMetadata(sampleRate = 44_100, bitrate = 191_999).quality)
        assertNull(AudioTechnicalMetadata(sampleRate = 32_000, bitrate = 320_000).quality)
        assertNull(AudioTechnicalMetadata(sampleRate = 96_000).quality)
        assertNull(AudioTechnicalMetadata(bitrate = 320_000).quality)
    }

    @Test
    fun sqRequiresConfirmedLosslessEncodingAndCdLevelParameters() {
        assertEquals(AudioQuality.SQ, AudioTechnicalMetadata(sampleRate = 44_100, bitsPerSample = 16, mimeType = "audio/flac").quality)
        assertEquals(AudioQuality.SQ, AudioTechnicalMetadata(sampleRate = 96_000, bitsPerSample = 24, mimeType = "audio/raw").quality)
        assertNull(AudioTechnicalMetadata(sampleRate = 22_050, bitsPerSample = 16, mimeType = "audio/flac").quality)
        assertNull(AudioTechnicalMetadata(sampleRate = 44_100, bitsPerSample = 8, mimeType = "audio/raw").quality)
        assertNull(AudioTechnicalMetadata(sampleRate = 44_100, mimeType = "audio/flac").quality)
    }

    @Test
    fun lossyHighBitrateAndDecoderOutputDepthDoNotImplySq() {
        assertEquals(AudioQuality.HQ, AudioTechnicalMetadata(sampleRate = 96_000, bitrate = 1_500_000, bitsPerSample = 24, mimeType = "audio/aac").quality)
        assertNull(AudioTechnicalMetadata(bitsPerSample = 24, mimeType = "audio/flac").quality)
        assertNull(AudioTechnicalMetadata().quality)
    }
}
