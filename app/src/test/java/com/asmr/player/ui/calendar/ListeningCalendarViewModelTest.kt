package com.asmr.player.ui.calendar

import com.asmr.player.data.local.db.entities.ListeningSessionEntity
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
        val year = ListeningCalendarViewModel.buildCurrentSummary(
            stats = stats,
            currentDate = "2026-07-21",
            granularity = ListeningComparisonGranularity.Year
        )

        assertEquals(40L * minute, day.totalDurationMs)
        assertEquals(4, day.totalTrackCount)
        assertEquals(70L * minute, week.totalDurationMs)
        assertEquals(7, week.totalTrackCount)
        assertEquals(100L * minute, month.totalDurationMs)
        assertEquals(10, month.totalTrackCount)
        assertEquals(4, month.activeDayCount)
        assertEquals(1024L, month.totalTrafficBytes)
        assertEquals(100L * minute, year.totalDurationMs)
        assertEquals(10, year.totalTrackCount)
        assertEquals(4, year.activeDayCount)
        assertEquals(1024L, year.totalTrafficBytes)
    }

    @Test
    fun mergeAdjacentListeningSessions_combinesOnlyNeighboringSameWorks() {
        val minute = 60_000L
        val firstA = listeningSession(
            id = 1L,
            rjCode = "RJ123456",
            title = "作品 A",
            startAtMs = 3_000L,
            durationMs = 3L * minute,
            trafficBytes = 300L,
            trackCount = 1
        )
        val secondA = listeningSession(
            id = 2L,
            rjCode = "rj123456",
            title = "作品 A",
            startAtMs = 2_000L,
            durationMs = 2L * minute,
            trafficBytes = 200L,
            trackCount = 2
        )
        val workB = listeningSession(
            id = 3L,
            rjCode = "RJ654321",
            title = "作品 B",
            startAtMs = 1_000L,
            durationMs = minute
        )
        val laterA = listeningSession(
            id = 4L,
            rjCode = "RJ123456",
            title = "作品 A",
            startAtMs = 500L,
            durationMs = minute
        )

        val merged = ListeningCalendarViewModel.mergeAdjacentListeningSessions(
            listOf(firstA, secondA, workB, laterA)
        )

        assertEquals(3, merged.size)
        assertEquals(1L, merged[0].id)
        assertEquals(5L * minute, merged[0].durationMs)
        assertEquals(500L, merged[0].trafficBytes)
        assertEquals(3, merged[0].trackCount)
        assertEquals(workB.id, merged[1].id)
        assertEquals(laterA.id, merged[2].id)
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
        assertEquals(120.0 * minute, comparison.durationMs.current, 0.1)
        assertEquals(60.0 * minute, comparison.durationMs.reference, 0.1)
        assertEquals(3.0, comparison.trackCount.difference, 0.1)
    }

    @Test
    fun buildSummaryComparison_weekUsesSelectedWeekOffsetAndTotals() {
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
        assertEquals(240.0 * minute, comparison.durationMs.current, 0.1)
        assertEquals(90.0 * minute, comparison.durationMs.reference, 0.1)
        assertEquals(3.0, comparison.trackCount.difference, 0.1)
        assertEquals(0.0, comparison.activeDayCount.difference, 0.1)
    }

    @Test
    fun buildSummaryComparison_monthUsesSelectedMonthOffsetAndTotals() {
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
        assertEquals(20.0 * 60_000.0, comparison.durationMs.current, 0.1)
        assertEquals(20.0 * 30_000.0, comparison.durationMs.reference, 0.1)
        assertEquals(-20.0, comparison.trackCount.difference, 0.1)
        assertEquals(0.0, comparison.activeDayCount.difference, 0.1)
    }

    @Test
    fun buildSummaryComparison_yearUsesSelectedYearOffsetAndTotals() {
        val currentStats = ListeningDay.datesBetween("2026-01-01", "2026-07-20")
            .map { DailyStatEntity(it, listeningDurationMs = 60_000L, trackCount = 1) }
        val referenceStats = ListeningDay.datesBetween("2024-01-01", "2024-07-20")
            .map { DailyStatEntity(it, listeningDurationMs = 30_000L, trackCount = 2) }

        val comparison = ListeningCalendarViewModel.buildSummaryComparison(
            stats = currentStats + referenceStats,
            currentDate = "2026-07-20",
            selectedDate = "2024-03-01",
            granularity = ListeningComparisonGranularity.Year
        )

        assertEquals("2年前", comparison.referenceLabel)
        assertEquals(201.0 * 60_000.0, comparison.durationMs.current, 0.1)
        assertEquals(202.0 * 30_000.0, comparison.durationMs.reference, 0.1)
        assertEquals(-203.0, comparison.trackCount.difference, 0.1)
        assertEquals(-1.0, comparison.activeDayCount.difference, 0.1)
    }
}

private fun listeningSession(
    id: Long,
    rjCode: String = "",
    title: String = "",
    albumId: Long = -1L,
    circle: String = "",
    cv: String = "",
    startAtMs: Long,
    durationMs: Long,
    trafficBytes: Long = 0L,
    trackCount: Int = 0
): ListeningSessionEntity =
    ListeningSessionEntity(
        id = id,
        albumId = albumId,
        rjCode = rjCode,
        title = title,
        circle = circle,
        cv = cv,
        listeningDate = "2026-07-22",
        startAtMs = startAtMs,
        lastActiveAtMs = startAtMs + durationMs,
        durationMs = durationMs,
        trafficBytes = trafficBytes,
        trackCount = trackCount
    )
