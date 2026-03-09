package com.asmr.player.playback

import androidx.media3.common.Player
import com.asmr.player.domain.model.Slice
import org.junit.Assert.assertEquals
import org.junit.Test

class SliceLoopEngineTest {
    private val slices = listOf(
        Slice(id = 1L, startMs = 1_000L, endMs = 2_000L),
        Slice(id = 2L, startMs = 5_000L, endMs = 6_000L)
    )

    @Test
    fun disabled_returnsNone() {
        val engine = SliceLoopEngine()
        val action = engine.decide(
            nowElapsedMs = 1_000L,
            input = SliceLoopInput(
                sliceModeEnabled = false,
                userScrubbing = false,
                mediaId = "t1",
                positionMs = 1_500L,
                durationMs = 10_000L,
                repeatMode = Player.REPEAT_MODE_ALL,
                previewSlice = null,
                slices = slices
            )
        )
        assertEquals(SliceLoopAction.None, action)
    }

    @Test
    fun outsideSlices_seeksToNextSliceStart() {
        val engine = SliceLoopEngine()
        val action = engine.decide(
            nowElapsedMs = 1_000L,
            input = SliceLoopInput(
                sliceModeEnabled = true,
                userScrubbing = false,
                mediaId = "t1",
                positionMs = 2_500L,
                durationMs = 10_000L,
                repeatMode = Player.REPEAT_MODE_ALL,
                previewSlice = null,
                slices = slices
            )
        )
        assertEquals(SliceLoopAction.SeekTo(5_000L), action)
    }

    @Test
    fun insideSlice_beforeEndThreshold_returnsNone() {
        val engine = SliceLoopEngine(endThresholdMs = 200L)
        val action = engine.decide(
            nowElapsedMs = 1_000L,
            input = SliceLoopInput(
                sliceModeEnabled = true,
                userScrubbing = false,
                mediaId = "t1",
                positionMs = 1_700L,
                durationMs = 10_000L,
                repeatMode = Player.REPEAT_MODE_ALL,
                previewSlice = null,
                slices = slices
            )
        )
        assertEquals(SliceLoopAction.None, action)
    }

    @Test
    fun nearSliceEnd_seeksToNextSlice() {
        val engine = SliceLoopEngine(endThresholdMs = 200L)
        val action = engine.decide(
            nowElapsedMs = 1_000L,
            input = SliceLoopInput(
                sliceModeEnabled = true,
                userScrubbing = false,
                mediaId = "t1",
                positionMs = 1_900L,
                durationMs = 10_000L,
                repeatMode = Player.REPEAT_MODE_ALL,
                previewSlice = null,
                slices = slices
            )
        )
        assertEquals(SliceLoopAction.SeekTo(5_000L), action)
    }

    @Test
    fun lastSliceEnd_repeatOne_seeksToFirstSlice() {
        val engine = SliceLoopEngine(endThresholdMs = 200L)
        val action = engine.decide(
            nowElapsedMs = 1_000L,
            input = SliceLoopInput(
                sliceModeEnabled = true,
                userScrubbing = false,
                mediaId = "t1",
                positionMs = 5_900L,
                durationMs = 10_000L,
                repeatMode = Player.REPEAT_MODE_ONE,
                previewSlice = null,
                slices = slices
            )
        )
        assertEquals(SliceLoopAction.SeekTo(1_000L), action)
    }

    @Test
    fun lastSliceEnd_repeatAll_skipsToNext() {
        val engine = SliceLoopEngine(endThresholdMs = 200L)
        val action = engine.decide(
            nowElapsedMs = 1_000L,
            input = SliceLoopInput(
                sliceModeEnabled = true,
                userScrubbing = false,
                mediaId = "t1",
                positionMs = 5_900L,
                durationMs = 10_000L,
                repeatMode = Player.REPEAT_MODE_ALL,
                previewSlice = null,
                slices = slices
            )
        )
        assertEquals(SliceLoopAction.SkipToNext, action)
    }

    @Test
    fun seekToSameTarget_isThrottledWithinCooldown() {
        val engine = SliceLoopEngine(endThresholdMs = 200L, sameTargetCooldownMs = 650L)
        val first = engine.decide(
            nowElapsedMs = 1_000L,
            input = SliceLoopInput(
                sliceModeEnabled = true,
                userScrubbing = false,
                mediaId = "t1",
                positionMs = 1_900L,
                durationMs = 10_000L,
                repeatMode = Player.REPEAT_MODE_ALL,
                previewSlice = null,
                slices = slices
            )
        )
        val second = engine.decide(
            nowElapsedMs = 1_200L,
            input = SliceLoopInput(
                sliceModeEnabled = true,
                userScrubbing = false,
                mediaId = "t1",
                positionMs = 1_950L,
                durationMs = 10_000L,
                repeatMode = Player.REPEAT_MODE_ALL,
                previewSlice = null,
                slices = slices
            )
        )
        assertEquals(SliceLoopAction.SeekTo(5_000L), first)
        assertEquals(SliceLoopAction.None, second)
    }
}
