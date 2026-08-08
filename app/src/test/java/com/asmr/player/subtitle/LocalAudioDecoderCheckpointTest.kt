package com.asmr.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalAudioDecoderCheckpointTest {
    @Test
    fun resume_discardsDecodedFramesBeforePersistedCheckpoint() {
        assertEquals(
            960,
            framesToDiscardBeforeCheckpoint(
                bufferStartUs = 29_980_000L,
                requestedStartUs = 30_000_000L,
                sampleRate = 48_000,
                frameCount = 2_048
            )
        )
        assertEquals(
            0,
            framesToDiscardBeforeCheckpoint(
                bufferStartUs = 30_001_000L,
                requestedStartUs = 30_000_000L,
                sampleRate = 48_000,
                frameCount = 2_048
            )
        )
        assertEquals(
            2_048,
            framesToDiscardBeforeCheckpoint(
                bufferStartUs = 29_000_000L,
                requestedStartUs = 30_000_000L,
                sampleRate = 48_000,
                frameCount = 2_048
            )
        )
    }
}
