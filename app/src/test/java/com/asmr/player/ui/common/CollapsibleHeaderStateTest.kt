package com.asmr.player.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class CollapsibleHeaderStateTest {
    @Test
    fun scrollDelta_clampsWithinExpandedAndCollapsedBounds() {
        val state = CollapsibleHeaderState()
        state.updateHeight(120f)

        state.onScrollDelta(-200f)
        assertEquals(-120f, state.offsetPx)
        assertEquals(1f, state.collapseFraction)

        state.onScrollDelta(50f)
        assertEquals(-70f, state.offsetPx)
        assertEquals(COLLAPSIBLE_HEADER_STATE_PARTIAL, collapsibleHeaderUiState(state.collapseFraction))

        state.onScrollDelta(200f)
        assertEquals(0f, state.offsetPx)
        assertEquals(COLLAPSIBLE_HEADER_STATE_EXPANDED, collapsibleHeaderUiState(state.collapseFraction))
    }
}
