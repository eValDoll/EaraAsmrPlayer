package com.asmr.player.subtitle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleGenerationPolicyTest {
    @Test
    fun supportsFileName_acceptsLibraryAudioFormatsIgnoringCase() {
        listOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "opus").forEach { extension ->
            assertTrue(extension, SubtitleGenerationPolicy.supportsFileName("音声.$extension"))
            assertTrue(extension, SubtitleGenerationPolicy.supportsFileName("音声.${extension.uppercase()}"))
        }
    }

    @Test
    fun supportsFileName_rejectsVideosSubtitlesAndUnknownFormats() {
        assertFalse(SubtitleGenerationPolicy.supportsFileName("voice.mp4"))
        assertFalse(SubtitleGenerationPolicy.supportsFileName("voice.srt"))
        assertFalse(SubtitleGenerationPolicy.supportsFileName("voice.ape"))
        assertFalse(SubtitleGenerationPolicy.supportsFileName("voice.acc"))
        assertFalse(SubtitleGenerationPolicy.supportsFileName("voice"))
    }
}
