package com.asmr.player.playback

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryLoadControlTest {
    @Test
    fun normalStartupAndBufferingKeepTheMedia3Defaults() {
        val control = createControl()

        assertFalse(control.shouldStartPlayback(parameters(999)))
        assertTrue(control.shouldStartPlayback(parameters(1_000)))
        assertFalse(control.shouldStartPlayback(parameters(1_999, rebuffering = true)))
        assertTrue(control.shouldStartPlayback(parameters(2_000, rebuffering = true)))
    }

    @Test
    fun recoveryWaitsForAReserveAcrossPrepareAndRebuffering() {
        val control = createControl()

        for (rebuffering in listOf(false, true)) {
            control.beginRecovery()
            control.onPrepared(PlayerId.UNSET)
            assertFalse(control.shouldStartPlayback(parameters(2_000, rebuffering = rebuffering)))
            assertFalse(control.shouldStartPlayback(parameters(11_999, rebuffering = rebuffering)))
            assertTrue(control.shouldStartPlayback(parameters(12_000, rebuffering = rebuffering)))
        }
        assertTrue(control.shouldStartPlayback(parameters(1_000)))
    }

    @Test
    fun fasterPlaybackRequiresTheSameReserveInPlayoutTime() {
        val control = createControl()
        control.beginRecovery()

        assertFalse(control.shouldStartPlayback(parameters(12_000, speed = 2f)))
        assertTrue(control.shouldStartPlayback(parameters(24_000, speed = 2f)))
    }

    @Test
    fun clearingRecoveryRestoresNormalUserPlayback() {
        val control = createControl()
        control.beginRecovery()
        control.clearRecovery()

        assertTrue(control.shouldStartPlayback(parameters(1_000)))
    }

    @Test
    fun theExistingByteLimitPreventsWaitingForeverOnHighBitrateTracks() {
        val control = createControl()
        control.beginRecovery()
        val allocations = List(DefaultLoadControl.DEFAULT_MIN_BUFFER_SIZE / C.DEFAULT_BUFFER_SEGMENT_SIZE) {
            control.allocator.allocate()
        }
        try {
            assertTrue(control.shouldStartPlayback(parameters(2_000)))
        } finally {
            allocations.forEach(control.allocator::release)
        }
    }

    @Test
    fun liveRecoveryKeepsTheMedia3LiveOffsetLimit() {
        val control = createControl()
        control.beginRecovery()

        assertFalse(control.shouldStartPlayback(parameters(1_999, liveOffsetMs = 4_000)))
        assertTrue(control.shouldStartPlayback(parameters(2_000, liveOffsetMs = 4_000)))
    }

    private fun createControl() = PlaybackRecoveryLoadControl().apply {
        onPrepared(PlayerId.UNSET)
    }

    private fun parameters(
        bufferMs: Long,
        rebuffering: Boolean = false,
        speed: Float = 1f,
        liveOffsetMs: Long? = null,
    ) = LoadControl.Parameters(
        PlayerId.UNSET,
        Timeline.EMPTY,
        MediaPeriodId("audio"),
        0L,
        bufferMs * 1_000L,
        speed,
        true,
        rebuffering,
        liveOffsetMs?.times(1_000L) ?: C.TIME_UNSET,
        C.TIME_UNSET,
    )
}
