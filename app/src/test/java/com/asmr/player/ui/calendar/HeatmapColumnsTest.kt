package com.asmr.player.ui.calendar

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.asmr.player.util.ListeningDay
import java.util.TimeZone

class HeatmapColumnsTest {

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

    private fun day(date: String) = HeatmapDay(date = date, durationMs = 0L, level = 0)

    @Test
    fun emptyInputReturnsEmpty() {
        assertEquals(emptyList<List<HeatmapDay?>>(), buildHeatmapColumns(emptyList()))
    }

    @Test
    fun eachColumnHasSevenCells() {
        // 2026-07-19 is a Sunday -> no leading blanks
        val days = (19..25).map { day("2026-07-%02d".format(it)) }
        val columns = buildHeatmapColumns(days)
        assertEquals(1, columns.size)
        assertEquals(7, columns[0].size)
        assertEquals("2026-07-19", columns[0][0]?.date)
        assertEquals("2026-07-25", columns[0][6]?.date)
    }

    @Test
    fun leadingBlanksMatchFirstDayOfWeek() {
        // 2026-07-20 is a Monday -> index 1 -> exactly 1 leading blank
        val days = listOf(day("2026-07-20"))
        val columns = buildHeatmapColumns(days)
        assertEquals(1, columns.size)
        assertNull(columns[0][0])
        assertEquals("2026-07-20", columns[0][1]?.date)
    }

    @Test
    fun spillsIntoMultipleColumns() {
        // Sunday start, 8 days -> 2 columns, second column has 1 real + 6 blanks
        val days = (19..26).map { day("2026-07-%02d".format(it)) }
        val columns = buildHeatmapColumns(days)
        assertEquals(2, columns.size)
        assertEquals("2026-07-26", columns[1][0]?.date)
        assertNull(columns[1][1])
        assertNull(columns[1][6])
    }

    @Test
    fun monthLabelsSpanMonthlyBoundaries() {
        val days = ListeningDay.datesBetween("2026-01-01", "2026-02-10").map(::day)
        val columns = buildHeatmapColumns(days)
        val labels = buildHeatmapMonthLabels(columns)
        assertEquals(listOf("1月", "2月"), labels.map { it.text })
        assertTrue(labels.all { it.span > 0 })
        assertEquals(columns.size, labels.sumOf { it.span })
    }
}
