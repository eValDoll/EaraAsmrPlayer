package com.asmr.player.ui.common

import com.asmr.player.ui.drawer.SiteStatus
import com.asmr.player.ui.drawer.SiteStatusType
import org.junit.Assert.assertEquals
import org.junit.Test

class SiteStatusComponentsTest {
    @Test
    fun latencyIndicator_showsNumericLatencyAndMatchingSignalBars() {
        assertEquals(
            SiteLatencyDisplay(label = "180 ms", activeBars = 3),
            siteLatencyDisplay(SiteStatus(SiteStatusType.Ok, latencyMs = 180L))
        )
        assertEquals(
            SiteLatencyDisplay(label = "520 ms", activeBars = 2),
            siteLatencyDisplay(SiteStatus(SiteStatusType.Ok, latencyMs = 520L))
        )
        assertEquals(
            SiteLatencyDisplay(label = "1200 ms", activeBars = 1),
            siteLatencyDisplay(SiteStatus(SiteStatusType.Ok, latencyMs = 1_200L))
        )
    }
}
