package com.asmr.player.subtitle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleGenerationPolicyTest {
    @Test
    fun supportsFileName_acceptsOnlyMp3AndWavIgnoringCase() {
        assertTrue(SubtitleGenerationPolicy.supportsFileName("voice.MP3"))
        assertTrue(SubtitleGenerationPolicy.supportsFileName("voice.wav"))
        assertFalse(SubtitleGenerationPolicy.supportsFileName("voice.flac"))
        assertFalse(SubtitleGenerationPolicy.supportsFileName("voice.mp4"))
    }
}
