package com.asmr.player.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ListeningDayTest {

    private lateinit var originalTz: TimeZone

    private fun epoch(iso: String): Long {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        return fmt.parse(iso)!!.time
    }

    @Before
    fun setUp() {
        originalTz = TimeZone.getDefault()
        // 固定为一个非 UTC 时区，验证"按设备时区"的口径。
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTz)
    }

    @Test
    fun dateOf_beforeFiveAm_belongsToPreviousDay() {
        // 07-20 03:00 属于 07-19（凌晨收听归前一天）
        assertEquals("2026-07-19", ListeningDay.dateOf(epoch("2026-07-20 03:00")))
    }

    @Test
    fun dateOf_justBeforeFiveAm_belongsToPreviousDay() {
        // 07-20 04:59 仍属于 07-19
        assertEquals("2026-07-19", ListeningDay.dateOf(epoch("2026-07-20 04:59")))
    }

    @Test
    fun dateOf_atFiveAm_belongsToSameDay() {
        // 07-20 05:00 归属于 07-20
        assertEquals("2026-07-20", ListeningDay.dateOf(epoch("2026-07-20 05:00")))
    }

    @Test
    fun dateOf_afternoon_belongsToSameDay() {
        assertEquals("2026-07-20", ListeningDay.dateOf(epoch("2026-07-20 14:30")))
    }

    @Test
    fun dateOf_lateNightBeforeMidnight_belongsToSameDay() {
        // 07-20 23:30 仍是 07-20
        assertEquals("2026-07-20", ListeningDay.dateOf(epoch("2026-07-20 23:30")))
    }

    @Test
    fun startOfDate_isFiveAmLocal() {
        val start = ListeningDay.startOfDate("2026-07-20")
        assertEquals(epoch("2026-07-20 05:00"), start)
    }

    @Test
    fun datesBetween_isInclusiveAndAscending() {
        val dates = ListeningDay.datesBetween("2026-07-18", "2026-07-21")
        assertEquals(listOf("2026-07-18", "2026-07-19", "2026-07-20", "2026-07-21"), dates)
    }

    @Test
    fun datesBetween_singleDay() {
        assertEquals(listOf("2026-07-20"), ListeningDay.datesBetween("2026-07-20", "2026-07-20"))
    }

    @Test
    fun datesBetween_invalidRangeReturnsEmpty() {
        assertEquals(emptyList<String>(), ListeningDay.datesBetween("2026-07-21", "2026-07-20"))
    }

    @Test
    fun datesBetween_crossesMonthBoundary() {
        val dates = ListeningDay.datesBetween("2026-01-30", "2026-02-02")
        assertEquals(
            listOf("2026-01-30", "2026-01-31", "2026-02-01", "2026-02-02"),
            dates
        )
    }

    @Test
    fun dayOfWeekIndex_knownDate() {
        // 2026-07-20 is a Monday -> index 1 (Sunday = 0)
        assertEquals(1, ListeningDay.dayOfWeekIndex("2026-07-20"))
        // 2026-07-19 is a Sunday -> index 0
        assertEquals(0, ListeningDay.dayOfWeekIndex("2026-07-19"))
    }

    @Test
    fun dateDaysAgo_countsBackListeningDays() {
        val now = epoch("2026-07-20 14:00")
        assertEquals("2026-07-13", ListeningDay.dateDaysAgo(7, now))
    }

    @Test
    fun dateDaysAgo_fromEarlyMorningUsesListeningDay() {
        // 07-20 03:00 -> today is 07-19, 7 days ago = 07-12
        val now = epoch("2026-07-20 03:00")
        assertEquals("2026-07-12", ListeningDay.dateDaysAgo(7, now))
    }
}
