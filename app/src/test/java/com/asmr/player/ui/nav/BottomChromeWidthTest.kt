package com.asmr.player.ui.nav

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class BottomChromeWidthTest {
    @Test
    fun bottomChromeWidthLimit_capsWideLayouts() {
        assertEquals(560.dp, bottomChromeWidthLimit(1_200.dp, largeLayout = false))
        assertEquals(680.dp, bottomChromeWidthLimit(1_200.dp, largeLayout = true))
    }

    @Test
    fun bottomChromeWidthLimit_preservesCompactLayouts() {
        assertEquals(360.dp, bottomChromeWidthLimit(360.dp, largeLayout = false))
        assertEquals(600.dp, bottomChromeWidthLimit(600.dp, largeLayout = true))
    }

    @Test
    fun miniPlayerExpandedWidth_matchesExpandedNavWidthWhenSpaceIsPlentiful() {
        assertEquals(
            280.dp,
            bottomChromeMiniPlayerExpandedWidth(
                chromeWidthLimit = 560.dp,
                collapsedNavWidth = 64.dp,
                chromeSpacing = 6.dp,
                minimumWidth = 204.dp,
                expandedNavWidth = 280.dp
            )
        )
    }

    @Test
    fun miniPlayerExpandedWidth_preservesResponsiveWidthBelowNavMaximum() {
        assertEquals(
            250.dp,
            bottomChromeMiniPlayerExpandedWidth(
                chromeWidthLimit = 320.dp,
                collapsedNavWidth = 64.dp,
                chromeSpacing = 6.dp,
                minimumWidth = 204.dp,
                expandedNavWidth = 280.dp
            )
        )
    }
}
