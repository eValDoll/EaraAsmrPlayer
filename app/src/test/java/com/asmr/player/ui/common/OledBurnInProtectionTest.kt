package com.asmr.player.ui.common

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OledBurnInProtectionTest {
    @Test
    fun pixelShiftPattern_coversFiveByFiveAreaWithinConfiguredLimit() {
        val expectedPositions = buildSet {
            for (x in -OledBurnInMaxShiftPixels..OledBurnInMaxShiftPixels) {
                for (y in -OledBurnInMaxShiftPixels..OledBurnInMaxShiftPixels) {
                    add(x to y)
                }
            }
        }
        val actualPositions = OledBurnInPixelShiftPattern
            .map { it.x to it.y }
            .toSet()

        assertEquals(expectedPositions, actualPositions)
        assertEquals(0 to 0, OledBurnInPixelShiftPattern.first().let { it.x to it.y })
    }

    @Test
    fun pixelShiftPattern_movesAtMostOnePixelPerStepIncludingWrapAround() {
        val loop = OledBurnInPixelShiftPattern + OledBurnInPixelShiftPattern.first()

        loop.zipWithNext().forEach { (current, next) ->
            assertTrue(abs(next.x - current.x) <= 1)
            assertTrue(abs(next.y - current.y) <= 1)
        }
    }
}
