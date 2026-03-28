package com.asmr.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingMotionSpecTest {

    @Test
    fun portraitSlotsMatchExpectedStoryboard() {
        assertEquals(
            listOf(
                NowPlayingMotionSlot.HEADER,
                NowPlayingMotionSlot.COVER,
                NowPlayingMotionSlot.LYRICS,
                NowPlayingMotionSlot.PROGRESS,
                NowPlayingMotionSlot.ACTION_ROW,
                NowPlayingMotionSlot.CONTROLS,
                NowPlayingMotionSlot.VOLUME
            ),
            NowPlayingMotionSpec.orderedSlots(NowPlayingMotionLayout.PORTRAIT)
        )
    }

    @Test
    fun phoneLandscapeSlotsMatchExpectedStoryboard() {
        assertEquals(
            listOf(
                NowPlayingMotionSlot.HEADER,
                NowPlayingMotionSlot.COVER,
                NowPlayingMotionSlot.PROGRESS,
                NowPlayingMotionSlot.LYRICS,
                NowPlayingMotionSlot.CONTROLS
            ),
            NowPlayingMotionSpec.orderedSlots(NowPlayingMotionLayout.PHONE_LANDSCAPE)
        )
    }

    @Test
    fun splitLandscapeSlotsMatchExpectedStoryboard() {
        assertEquals(
            listOf(
                NowPlayingMotionSlot.HEADER,
                NowPlayingMotionSlot.COVER,
                NowPlayingMotionSlot.PROGRESS,
                NowPlayingMotionSlot.INFO_PANEL,
                NowPlayingMotionSlot.CONTROLS
            ),
            NowPlayingMotionSpec.orderedSlots(NowPlayingMotionLayout.SPLIT_LANDSCAPE)
        )
    }

    @Test
    fun portraitDelaysKeepCoverLeadingAndHeaderTrailingOnExit() {
        val layout = NowPlayingMotionLayout.PORTRAIT

        assertEquals(0, NowPlayingMotionSpec.enterDelayMs(layout, NowPlayingMotionSlot.HEADER))
        assertEquals(0, NowPlayingMotionSpec.enterDelayMs(layout, NowPlayingMotionSlot.COVER))
        assertEquals(NowPlayingMotionSpec.EnterStepDelayMs, NowPlayingMotionSpec.enterDelayMs(layout, NowPlayingMotionSlot.LYRICS))
        assertEquals(NowPlayingMotionSpec.EnterStepDelayMs * 2, NowPlayingMotionSpec.enterDelayMs(layout, NowPlayingMotionSlot.PROGRESS))
        assertEquals(NowPlayingMotionSpec.EnterStepDelayMs * 3, NowPlayingMotionSpec.enterDelayMs(layout, NowPlayingMotionSlot.ACTION_ROW))
        assertEquals(NowPlayingMotionSpec.EnterStepDelayMs * 4, NowPlayingMotionSpec.enterDelayMs(layout, NowPlayingMotionSlot.CONTROLS))
        assertEquals(NowPlayingMotionSpec.EnterStepDelayMs * 5, NowPlayingMotionSpec.enterDelayMs(layout, NowPlayingMotionSlot.VOLUME))

        assertEquals(0, NowPlayingMotionSpec.exitDelayMs(layout, NowPlayingMotionSlot.VOLUME))
        assertEquals(NowPlayingMotionSpec.ExitStepDelayMs, NowPlayingMotionSpec.exitDelayMs(layout, NowPlayingMotionSlot.CONTROLS))
        assertEquals(NowPlayingMotionSpec.ExitStepDelayMs * 2, NowPlayingMotionSpec.exitDelayMs(layout, NowPlayingMotionSlot.ACTION_ROW))
        assertEquals(NowPlayingMotionSpec.ExitStepDelayMs * 3, NowPlayingMotionSpec.exitDelayMs(layout, NowPlayingMotionSlot.PROGRESS))
        assertEquals(NowPlayingMotionSpec.ExitStepDelayMs * 4, NowPlayingMotionSpec.exitDelayMs(layout, NowPlayingMotionSlot.LYRICS))
        assertEquals(NowPlayingMotionSpec.ExitStepDelayMs * 5, NowPlayingMotionSpec.exitDelayMs(layout, NowPlayingMotionSlot.COVER))
        assertEquals(NowPlayingMotionSpec.ExitStepDelayMs * 6, NowPlayingMotionSpec.exitDelayMs(layout, NowPlayingMotionSlot.HEADER))
        assertEquals(
            NowPlayingMotionSpec.ExitStepDelayMs * 6 + NowPlayingMotionSpec.ExitDurationMs,
            NowPlayingMotionSpec.totalExitDurationMs(layout)
        )
    }

    @Test
    fun landscapeDelaysKeepRowsStaggeredAndHeaderLastOnExit() {
        val phoneLayout = NowPlayingMotionLayout.PHONE_LANDSCAPE
        assertEquals(0, NowPlayingMotionSpec.enterDelayMs(phoneLayout, NowPlayingMotionSlot.HEADER))
        assertEquals(0, NowPlayingMotionSpec.enterDelayMs(phoneLayout, NowPlayingMotionSlot.COVER))
        assertEquals(NowPlayingMotionSpec.EnterStepDelayMs, NowPlayingMotionSpec.enterDelayMs(phoneLayout, NowPlayingMotionSlot.PROGRESS))
        assertEquals(NowPlayingMotionSpec.EnterStepDelayMs * 2, NowPlayingMotionSpec.enterDelayMs(phoneLayout, NowPlayingMotionSlot.LYRICS))
        assertEquals(NowPlayingMotionSpec.EnterStepDelayMs * 3, NowPlayingMotionSpec.enterDelayMs(phoneLayout, NowPlayingMotionSlot.CONTROLS))
        assertEquals(0, NowPlayingMotionSpec.exitDelayMs(phoneLayout, NowPlayingMotionSlot.CONTROLS))
        assertEquals(NowPlayingMotionSpec.ExitStepDelayMs, NowPlayingMotionSpec.exitDelayMs(phoneLayout, NowPlayingMotionSlot.LYRICS))
        assertEquals(NowPlayingMotionSpec.ExitStepDelayMs * 2, NowPlayingMotionSpec.exitDelayMs(phoneLayout, NowPlayingMotionSlot.PROGRESS))
        assertEquals(NowPlayingMotionSpec.ExitStepDelayMs * 3, NowPlayingMotionSpec.exitDelayMs(phoneLayout, NowPlayingMotionSlot.COVER))
        assertEquals(NowPlayingMotionSpec.ExitStepDelayMs * 4, NowPlayingMotionSpec.exitDelayMs(phoneLayout, NowPlayingMotionSlot.HEADER))

        val splitLayout = NowPlayingMotionLayout.SPLIT_LANDSCAPE
        assertEquals(0, NowPlayingMotionSpec.enterDelayMs(splitLayout, NowPlayingMotionSlot.HEADER))
        assertEquals(0, NowPlayingMotionSpec.enterDelayMs(splitLayout, NowPlayingMotionSlot.COVER))
        assertEquals(NowPlayingMotionSpec.EnterStepDelayMs, NowPlayingMotionSpec.enterDelayMs(splitLayout, NowPlayingMotionSlot.PROGRESS))
        assertEquals(NowPlayingMotionSpec.EnterStepDelayMs * 2, NowPlayingMotionSpec.enterDelayMs(splitLayout, NowPlayingMotionSlot.INFO_PANEL))
        assertEquals(NowPlayingMotionSpec.EnterStepDelayMs * 3, NowPlayingMotionSpec.enterDelayMs(splitLayout, NowPlayingMotionSlot.CONTROLS))
        assertEquals(0, NowPlayingMotionSpec.exitDelayMs(splitLayout, NowPlayingMotionSlot.CONTROLS))
        assertEquals(NowPlayingMotionSpec.ExitStepDelayMs, NowPlayingMotionSpec.exitDelayMs(splitLayout, NowPlayingMotionSlot.INFO_PANEL))
        assertEquals(NowPlayingMotionSpec.ExitStepDelayMs * 2, NowPlayingMotionSpec.exitDelayMs(splitLayout, NowPlayingMotionSlot.PROGRESS))
        assertEquals(NowPlayingMotionSpec.ExitStepDelayMs * 3, NowPlayingMotionSpec.exitDelayMs(splitLayout, NowPlayingMotionSlot.COVER))
        assertEquals(NowPlayingMotionSpec.ExitStepDelayMs * 4, NowPlayingMotionSpec.exitDelayMs(splitLayout, NowPlayingMotionSlot.HEADER))
    }
}
