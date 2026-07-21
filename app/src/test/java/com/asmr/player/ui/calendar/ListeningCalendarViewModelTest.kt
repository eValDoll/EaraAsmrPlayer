package com.asmr.player.ui.calendar

import com.asmr.player.data.local.db.entities.DailyStatEntity
import com.asmr.player.util.ListeningDay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class ListeningCalendarViewModelTest {

    private lateinit var originalTz: TimeZone

    @Before
    fun setUp() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTz)
    }

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

    @Test
    fun buildCurrentSummary_aggregatesSelectedGranularityToCurrentDate() {
        val minute = 60_000L
        val stats = listOf(
            DailyStatEntity("2026-07-01", listeningDurationMs = 10L * minute, trackCount = 1),
            DailyStatEntity("2026-07-19", listeningDurationMs = 20L * minute, trackCount = 2),
            DailyStatEntity("2026-07-20", listeningDurationMs = 30L * minute, trackCount = 3),
            DailyStatEntity("2026-07-21", listeningDurationMs = 40L * minute, trackCount = 4, networkTrafficBytes = 1024L)
        )

        val day = ListeningCalendarViewModel.buildCurrentSummary(
            stats = stats,
            currentDate = "2026-07-21",
            granularity = ListeningComparisonGranularity.Day
        )
        val week = ListeningCalendarViewModel.buildCurrentSummary(
            stats = stats,
            currentDate = "2026-07-21",
            granularity = ListeningComparisonGranularity.Week
        )
        val month = ListeningCalendarViewModel.buildCurrentSummary(
            stats = stats,
            currentDate = "2026-07-21",
            granularity = ListeningComparisonGranularity.Month
        )

        assertEquals(40L * minute, day.totalDurationMs)
        assertEquals(4, day.totalTrackCount)
        assertEquals(70L * minute, week.totalDurationMs)
        assertEquals(7, week.totalTrackCount)
        assertEquals(100L * minute, month.totalDurationMs)
        assertEquals(10, month.totalTrackCount)
        assertEquals(4, month.activeDayCount)
        assertEquals(1024L, month.totalTrafficBytes)
    }

    @Test
    fun buildSummaryComparison_dayUsesSelectedHeatmapDate() {
        val minute = 60_000L
        val comparison = ListeningCalendarViewModel.buildSummaryComparison(
            stats = listOf(
                DailyStatEntity("2026-07-10", listeningDurationMs = 60L * minute, trackCount = 2),
                DailyStatEntity("2026-07-20", listeningDurationMs = 120L * minute, trackCount = 5)
            ),
            currentDate = "2026-07-20",
            selectedDate = "2026-07-10",
            granularity = ListeningComparisonGranularity.Day
        )

        assertEquals("7月10日", comparison.referenceLabel)
        assertEquals(false, comparison.usesDailyAverage)
        assertEquals(120.0 * minute, comparison.durationMs.current, 0.1)
        assertEquals(60.0 * minute, comparison.durationMs.reference, 0.1)
        assertEquals(3.0, comparison.trackCount.difference, 0.1)
    }

    @Test
    fun buildSummaryComparison_weekUsesSelectedWeekOffsetAndElapsedDaysAverage() {
        val minute = 60_000L
        val comparison = ListeningCalendarViewModel.buildSummaryComparison(
            stats = listOf(
                DailyStatEntity("2026-07-06", listeningDurationMs = 30L * minute, trackCount = 1),
                DailyStatEntity("2026-07-07", listeningDurationMs = 30L * minute, trackCount = 1),
                DailyStatEntity("2026-07-08", listeningDurationMs = 30L * minute, trackCount = 1),
                DailyStatEntity("2026-07-20", listeningDurationMs = 60L * minute, trackCount = 2),
                DailyStatEntity("2026-07-21", listeningDurationMs = 60L * minute, trackCount = 2),
                DailyStatEntity("2026-07-22", listeningDurationMs = 120L * minute, trackCount = 2)
            ),
            currentDate = "2026-07-22",
            selectedDate = "2026-07-08",
            granularity = ListeningComparisonGranularity.Week
        )

        assertEquals("2周前", comparison.referenceLabel)
        assertEquals(true, comparison.usesDailyAverage)
        assertEquals(80.0 * minute, comparison.durationMs.current, 0.1)
        assertEquals(30.0 * minute, comparison.durationMs.reference, 0.1)
        assertEquals(1.0, comparison.trackCount.difference, 0.1)
    }

    @Test
    fun buildSummaryComparison_monthUsesSelectedMonthOffsetAndDailyAverage() {
        val currentStats = ListeningDay.datesBetween("2026-07-01", "2026-07-20")
            .map { DailyStatEntity(it, listeningDurationMs = 60_000L, trackCount = 1) }
        val referenceStats = ListeningDay.datesBetween("2026-05-01", "2026-05-20")
            .map { DailyStatEntity(it, listeningDurationMs = 30_000L, trackCount = 2) }

        val comparison = ListeningCalendarViewModel.buildSummaryComparison(
            stats = currentStats + referenceStats,
            currentDate = "2026-07-20",
            selectedDate = "2026-05-05",
            granularity = ListeningComparisonGranularity.Month
        )

        assertEquals("2个月前", comparison.referenceLabel)
        assertEquals(true, comparison.usesDailyAverage)
        assertEquals(60_000.0, comparison.durationMs.current, 0.1)
        assertEquals(30_000.0, comparison.durationMs.reference, 0.1)
        assertEquals(-1.0, comparison.trackCount.difference, 0.1)
    }
}
