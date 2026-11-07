package com.asmr.player

import androidx.compose.ui.unit.dp
import com.asmr.player.ui.common.TabletLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletLayoutTest {
    @Test
    fun tabletWindowMustHaveRoomForBothPanes() {
        assertTrue(TabletLayout(true, 952.dp, 600.dp).twoPane)
        assertTrue(TabletLayout(true, 1294.dp, 800.dp).twoPane)
        assertFalse(TabletLayout(true, 600.dp, 700.dp).twoPane)
        assertFalse(TabletLayout(true, 710.dp, 480.dp).twoPane)
        assertFalse(TabletLayout(true, 960.dp, 360.dp).twoPane)
    }

    @Test
    fun largeTextAndEmbeddedDetailsFallBackToOnePane() {
        assertFalse(TabletLayout(true, 952.dp, 600.dp, fontScale = 1.5f).twoPane)
        assertTrue(TabletLayout(true, 1294.dp, 800.dp, fontScale = 1.5f).twoPane)
        assertFalse(TabletLayout(true, 952.dp, 600.dp, embedded = true).twoPane)
        assertFalse(TabletLayout(false, 952.dp, 600.dp).twoPane)
    }
}
