package com.asmr.player.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRequestTest {

    @Test
    fun secondTapDuringPauseFadeRequestsPlayFromOptimisticState() {
        val pauseRequest = nextPlaybackRequest(
            optimisticIsPlaying = null,
            playWhenReady = true
        )
        val playRequest = nextPlaybackRequest(
            optimisticIsPlaying = pauseRequest,
            playWhenReady = true
        )

        assertFalse(pauseRequest)
        assertTrue(playRequest)
    }
}
