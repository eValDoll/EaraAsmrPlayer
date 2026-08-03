package com.asmr.player.cache

import org.junit.Assert.assertEquals
import org.junit.Test

class AppCacheLimitsTest {
    @Test
    fun clampSizeMb_enforcesConfiguredRange() {
        assertEquals(AppCacheLimits.MinSizeMb, AppCacheLimits.clampSizeMb(1))
        assertEquals(150, AppCacheLimits.clampSizeMb(150))
        assertEquals(AppCacheLimits.MaxSizeMb, AppCacheLimits.clampSizeMb(2_000))
    }

    @Test
    fun cacheBudgets_addUpToConfiguredTotal() {
        val configuredSizeMb = 150

        val allocatedBytes = AppCacheLimits.imageMaxSizeBytes(configuredSizeMb) +
            AppCacheLimits.playbackMaxSizeBytes(configuredSizeMb) +
            AppCacheLimits.previewMaxSizeBytes(configuredSizeMb)

        assertEquals(AppCacheLimits.totalSizeBytes(configuredSizeMb), allocatedBytes)
    }
}
