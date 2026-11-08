package com.asmr.player.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PrefetchFrameBudgetTest {
    private var now = 1_000_000_000L
    private val budget = PrefetchFrameBudget { now }

    @Test
    fun requestsShareTheRemainingBudgetOfOneFrame() {
        budget.beginFrame(now, 60f)
        assertEquals(16_666_666L, budget.availableTimeNanos())

        now += 6_000_000L
        assertEquals(10_666_666L, budget.availableTimeNanos())
        now += 5_000_000L
        assertEquals(5_666_666L, budget.availableTimeNanos())
    }

    @Test
    fun switchingFrom60To120And144Hz_replacesThePreviousFrameBudget() {
        budget.beginFrame(now, 60f)
        now += 16_666_666L
        budget.beginFrame(now, 120f)
        assertEquals(8_333_333L, budget.availableTimeNanos())

        now += 8_333_333L
        budget.beginFrame(now, 144f)
        assertEquals(6_944_444L, budget.availableTimeNanos())
    }

    @Test
    fun missedFrame_cannotReuseOrExtendItsExpiredDeadline() {
        budget.beginFrame(now, 120f)
        now += 9_000_000L
        assertEquals(0L, budget.availableTimeNanos())
        now += 7_000_000L
        assertEquals(0L, budget.availableTimeNanos())

        budget.beginFrame(now - 3_000_000L, 120f)
        assertEquals(5_333_333L, budget.availableTimeNanos())
    }

    @Test
    fun noFrameAndSuspendedFrame_haveNoPrefetchBudget() {
        assertEquals(0L, budget.availableTimeNanos())
        budget.beginFrame(now, 120f)
        budget.clear()
        assertEquals(0L, budget.availableTimeNanos())
    }

    @Test
    fun unavailableOrInvalidRefreshRate_fallsBackTo60Hz() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, 29f, 500f).forEach { refreshRate ->
            budget.beginFrame(now, refreshRate)
            assertEquals(16_666_666L, budget.availableTimeNanos())
        }
    }
}
