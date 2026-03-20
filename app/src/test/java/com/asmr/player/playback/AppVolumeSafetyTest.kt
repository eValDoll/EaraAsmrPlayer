package com.asmr.player.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVolumeSafetyTest {

    @Test
    fun tapJumpAboveFortyPercentRequiresWarning() {
        assertTrue(
            AppVolumeSafety.shouldWarnBeforeLoudVolume(
                targetPercent = 42,
                source = AppVolumeChangeSource.TapJump,
                hasAcknowledgedWarningThisLaunch = false
            )
        )
    }

    @Test
    fun tapJumpAtFortyPercentDoesNotRequireWarning() {
        assertFalse(
            AppVolumeSafety.shouldWarnBeforeLoudVolume(
                targetPercent = 40,
                source = AppVolumeChangeSource.TapJump,
                hasAcknowledgedWarningThisLaunch = false
            )
        )
    }

    @Test
    fun dragNeverRequiresWarning() {
        assertFalse(
            AppVolumeSafety.shouldWarnBeforeLoudVolume(
                targetPercent = 80,
                source = AppVolumeChangeSource.Drag,
                hasAcknowledgedWarningThisLaunch = false
            )
        )
    }

    @Test
    fun acknowledgedWarningSuppressesFurtherWarningsUntilRestart() {
        assertFalse(
            AppVolumeSafety.shouldWarnBeforeLoudVolume(
                targetPercent = 80,
                source = AppVolumeChangeSource.TapJump,
                hasAcknowledgedWarningThisLaunch = true
            )
        )
    }
}
