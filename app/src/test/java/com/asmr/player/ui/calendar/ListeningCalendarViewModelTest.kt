package com.asmr.player.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningCalendarViewModelTest {

    @Test
    fun levelFor_zeroDurationIsLevelZero() {
        assertEquals(0, ListeningCalendarViewModel.levelFor(0L, 1000L))
    }

    @Test
    fun levelFor_anyDurationWithNoPeakIsLevelOne() {
        assertEquals(1, ListeningCalendarViewModel.levelFor(500L, 0L))
    }

    @Test
    fun levelFor_bucketsByRatioOfPeak() {
        val peak = 100L
        assertEquals(1, ListeningCalendarViewModel.levelFor(20L, peak)) // 0.20
        assertEquals(1, ListeningCalendarViewModel.levelFor(25L, peak)) // 0.25 boundary -> level 1
        assertEquals(2, ListeningCalendarViewModel.levelFor(40L, peak)) // 0.40
        assertEquals(2, ListeningCalendarViewModel.levelFor(50L, peak)) // 0.50 boundary -> level 2
        assertEquals(3, ListeningCalendarViewModel.levelFor(60L, peak)) // 0.60
        assertEquals(3, ListeningCalendarViewModel.levelFor(75L, peak)) // 0.75 boundary -> level 3
        assertEquals(4, ListeningCalendarViewModel.levelFor(90L, peak)) // 0.90
        assertEquals(4, ListeningCalendarViewModel.levelFor(100L, peak)) // peak -> level 4
    }

    @Test
    fun dateRangeForYear_usesCurrentDayForCurrentYear() {
        assertEquals(
            "2026-01-01" to "2026-07-20",
            ListeningCalendarViewModel.dateRangeForYear(2026, "2026-07-20")
        )
    }

    @Test
    fun dateRangeForYear_usesFullYearForPastYears() {
        assertEquals(
            "2025-01-01" to "2025-12-31",
            ListeningCalendarViewModel.dateRangeForYear(2025, "2026-07-20")
        )
    }

    @Test
    fun availableYearsForDates_includesCurrentYearAndHistoricalYears() {
        assertEquals(
            listOf(2026, 2025, 2024),
            ListeningCalendarViewModel.availableYearsForDates(
                listOf("2024-04-01", "2026-01-01"),
                currentYear = 2026
            )
        )
    }
}
