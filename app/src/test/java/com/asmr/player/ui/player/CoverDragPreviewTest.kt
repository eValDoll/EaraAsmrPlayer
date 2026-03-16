package com.asmr.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverDragPreviewTest {

    @Test
    fun dragStartsCentered() {
        val state = CoverDragPreviewState()

        assertEquals(0f, state.horizontalBias, 0.0001f)
        assertEquals(0f, state.verticalBias, 0.0001f)
    }

    @Test
    fun horizontalDragMapsToHorizontalBias() {
        val state = CoverDragPreviewState()

        state.dragBy(
            deltaX = 50f,
            deltaY = 0f,
            containerWidthPx = 200f,
            containerHeightPx = 400f
        )

        assertEquals(0.5f, state.horizontalBias, 0.0001f)
        assertEquals(0f, state.verticalBias, 0.0001f)
    }

    @Test
    fun verticalDragMapsToVerticalBias() {
        val state = CoverDragPreviewState()

        state.dragBy(
            deltaX = 0f,
            deltaY = 100f,
            containerWidthPx = 400f,
            containerHeightPx = 400f
        )

        assertEquals(0f, state.horizontalBias, 0.0001f)
        assertEquals(0.5f, state.verticalBias, 0.0001f)
    }

    @Test
    fun dragClampStopsAtBounds() {
        val state = CoverDragPreviewState()

        state.dragBy(
            deltaX = 400f,
            deltaY = -400f,
            containerWidthPx = 200f,
            containerHeightPx = 200f
        )

        assertEquals(1f, state.horizontalBias, 0.0001f)
        assertEquals(-1f, state.verticalBias, 0.0001f)
    }

    @Test
    fun resetReturnsToCenter() {
        val state = CoverDragPreviewState()

        state.dragBy(
            deltaX = 60f,
            deltaY = 40f,
            containerWidthPx = 240f,
            containerHeightPx = 160f
        )
        state.reset()

        assertEquals(0f, state.horizontalBias, 0.0001f)
        assertEquals(0f, state.verticalBias, 0.0001f)
    }
}
