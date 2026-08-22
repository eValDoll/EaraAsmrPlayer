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
}
