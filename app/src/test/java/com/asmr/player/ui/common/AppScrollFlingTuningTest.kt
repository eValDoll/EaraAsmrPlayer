package com.asmr.player.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AppScrollFlingTuningTest {
    @Test
    fun shouldStartCalmFling_ignoresTinyLiftVelocity() {
        assertEquals(
            false,
            shouldStartCalmFling(
                velocity = 180f,
                startVelocityPxPerSecond = 260f
            )
        )
    }

    @Test
    fun shouldStartCalmFling_allowsIntentionalSwipeVelocity() {
        assertEquals(
            true,
            shouldStartCalmFling(
                velocity = -700f,
                startVelocityPxPerSecond = 260f
            )
        )
    }

    @Test
    fun calmVerticalFlingVelocity_keepsGentleFlingUnchanged() {
        assertEquals(
            700f,
            calmVerticalFlingVelocity(
                velocity = 700f,
                dampingStartPxPerSecond = 850f,
                maxVelocityPxPerSecond = 3200f,
                velocityScale = 0.6f
            ),
            0.001f
        )
    }

    @Test
    fun calmVerticalFlingVelocity_dampsVelocityAboveStartThreshold() {
        assertEquals(
            1540f,
            calmVerticalFlingVelocity(
                velocity = 2000f,
                dampingStartPxPerSecond = 850f,
                maxVelocityPxPerSecond = 3200f,
                velocityScale = 0.6f
            ),
            0.001f
        )
    }

    @Test
    fun calmVerticalFlingVelocity_capsFastFlingAndKeepsDirection() {
        assertEquals(
            -3200f,
            calmVerticalFlingVelocity(
                velocity = -9000f,
                dampingStartPxPerSecond = 850f,
                maxVelocityPxPerSecond = 3200f,
                velocityScale = 0.6f
            ),
            0.001f
        )
    }

    @Test
    fun decayedCalmFlingVelocity_reducesVelocityWithoutFlippingDirection() {
        val decayed = decayedCalmFlingVelocity(
            velocity = -2400f,
            frameSeconds = 1f / 60f,
            decayRatePerSecond = 2.7f
        )

        assertEquals(true, decayed < 0f)
        assertEquals(true, decayed > -2400f)
    }

    @Test
    fun calmFlingDecayRateForVelocity_keepsMidSpeedGlideLongerThanFastAndFinalSlowdown() {
        val fastRate = calmFlingDecayRateForVelocity(
            velocity = 2400f,
            fastVelocityPxPerSecond = 1800f,
            lowMidVelocityPxPerSecond = 360f
        )
        val midRate = calmFlingDecayRateForVelocity(
            velocity = 1000f,
            fastVelocityPxPerSecond = 1800f,
            lowMidVelocityPxPerSecond = 360f
        )
        val lowRate = calmFlingDecayRateForVelocity(
            velocity = 220f,
            fastVelocityPxPerSecond = 1800f,
            lowMidVelocityPxPerSecond = 360f
        )

        assertEquals(true, fastRate > lowRate)
        assertEquals(true, lowRate > midRate)
    }
}
