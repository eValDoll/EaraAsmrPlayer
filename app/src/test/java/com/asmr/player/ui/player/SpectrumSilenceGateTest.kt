package com.asmr.player.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumSilenceGateTest {
    @Test
    fun entersSilenceOnlyBelowEnterThreshold() {
        val enter = 0.06f
        val exit = 0.09f
        assertTrue(updateSilenceActive(current = false, maxEnergy = 0.0f, enterMax = enter, exitMax = exit))
        assertTrue(updateSilenceActive(current = false, maxEnergy = 0.059f, enterMax = enter, exitMax = exit))
        assertFalse(updateSilenceActive(current = false, maxEnergy = 0.06f, enterMax = enter, exitMax = exit))
        assertFalse(updateSilenceActive(current = false, maxEnergy = 0.08f, enterMax = enter, exitMax = exit))
    }

    @Test
    fun exitsSilenceOnlyAboveExitThreshold() {
        val enter = 0.06f
        val exit = 0.09f
        assertTrue(updateSilenceActive(current = true, maxEnergy = 0.0f, enterMax = enter, exitMax = exit))
        assertTrue(updateSilenceActive(current = true, maxEnergy = 0.09f, enterMax = enter, exitMax = exit))
        assertFalse(updateSilenceActive(current = true, maxEnergy = 0.091f, enterMax = enter, exitMax = exit))
    }

    @Test
    fun hysteresisPreventsChatterBetweenEnterAndExit() {
        val enter = 0.06f
        val exit = 0.09f
        assertTrue(updateSilenceActive(current = true, maxEnergy = 0.075f, enterMax = enter, exitMax = exit))
        assertFalse(updateSilenceActive(current = false, maxEnergy = 0.075f, enterMax = enter, exitMax = exit))
    }
}

