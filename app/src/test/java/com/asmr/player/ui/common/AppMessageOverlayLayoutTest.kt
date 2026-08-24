package com.asmr.player.ui.common

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMessageOverlayLayoutTest {
    @Test
    fun overlayAlignment_centersMessageBelowHeader() {
        val containerSize = IntSize(width = 1080, height = 2400)
        val messageSize = IntSize(width = 320, height = 120)

        val offset = AppMessageOverlayAlignment.align(
            size = messageSize,
            space = containerSize,
            layoutDirection = LayoutDirection.Ltr
        )

        assertEquals((containerSize.width - messageSize.width) / 2, offset.x)
        assertEquals(0, offset.y)
        assertEquals(56.dp, AppMessageOverlayTopPadding)
        assertTrue(AppMessageOverlayTopPadding > EaraMainTopBarHeight)
    }
}
