package com.asmr.player.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumMetaFlowLayoutTest {
    @Test
    fun lineCount_wrapsByMeasuredWidth() {
        assertEquals(
            3,
            albumMetaFlowLineCount(
                itemWidths = listOf(40, 40, 40, 40, 40),
                maxWidth = 100,
                horizontalSpacing = 4,
            ),
        )
    }

    @Test
    fun collapsedVisibleCount_reservesOverflowChipWithinTwoLines() {
        assertEquals(
            3,
            albumMetaCollapsedVisibleCount(
                itemWidths = listOf(40, 40, 40, 40, 40),
                overflowWidth = 20,
                maxWidth = 100,
                horizontalSpacing = 4,
                maxLines = 2,
            ),
        )
    }

    @Test
    fun collapsedVisibleCount_returnsZeroWhenOnlyOverflowFits() {
        assertEquals(
            0,
            albumMetaCollapsedVisibleCount(
                itemWidths = listOf(90, 90),
                overflowWidth = 40,
                maxWidth = 100,
                horizontalSpacing = 8,
                maxLines = 1,
            ),
        )
    }
}
