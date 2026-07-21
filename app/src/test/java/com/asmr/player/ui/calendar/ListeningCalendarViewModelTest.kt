package com.asmr.player.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningCalendarViewModelTest {

    @Test
    fun levelFor_zeroDurationIsLevelZero() {
        assertEquals(0, ListeningCalendarViewModel.levelFor(0L))
    }

    @Test
    fun levelFor_bucketsByFixedListeningMinutes() {
        val minute = 60_000L
        assertEquals(1, ListeningCalendarViewModel.levelFor(1L))
        assertEquals(1, ListeningCalendarViewModel.levelFor(9L * minute))
        assertEquals(2, ListeningCalendarViewModel.levelFor(10L * minute))
        assertEquals(2, ListeningCalendarViewModel.levelFor(59L * minute))
        assertEquals(3, ListeningCalendarViewModel.levelFor(60L * minute))
        assertEquals(3, ListeningCalendarViewModel.levelFor(120L * minute))
        assertEquals(4, ListeningCalendarViewModel.levelFor(121L * minute))
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
