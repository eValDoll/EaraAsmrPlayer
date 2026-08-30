package com.asmr.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerPlayFeedbackTest {

    @Test
    fun emphasisStartsAndEndsAtRestAndPeaksMidway() {
        assertEquals(0f, miniPlayerPlayFeedbackEmphasis(0f), 0.0001f)
        assertTrue(miniPlayerPlayFeedbackEmphasis(0.5f) > 0.99f)
        assertEquals(0f, miniPlayerPlayFeedbackEmphasis(1f), 0.0001f)
    }

    @Test
    fun emphasisClampsProgressOutsideAnimationRange() {
        assertEquals(0f, miniPlayerPlayFeedbackEmphasis(-1f), 0.0001f)
        assertEquals(0f, miniPlayerPlayFeedbackEmphasis(2f), 0.0001f)
    }
}
