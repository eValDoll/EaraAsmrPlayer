package com.asmr.player.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.asmr.player.util.ListeningDay
import org.junit.Assert.assertFalse
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.ui.common.LocalTabletLayout
import com.asmr.player.ui.common.TabletLayout
import com.asmr.player.ui.createEaraComposeRule
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.AsmrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ListeningSummaryLayoutTest {
    @get:Rule val composeRule = createEaraComposeRule()

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun tabletStatisticsRenderInTwoRowsWithoutIntrinsicMeasurementCrash() {
        render(TabletLayout(true, 1208.dp, 800.dp))
        val bounds = listOf("总时长", "音轨", "活跃天数", "流量").map { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertEquals(bounds[0].top, bounds[1].top, 1f)
        assertEquals(bounds[2].top, bounds[3].top, 1f)
        assertEquals(bounds[0].left, bounds[2].left, 1f)
        assertEquals(bounds[1].left, bounds[3].left, 1f)
        assertTrue(bounds[2].top > bounds[0].bottom)
        assertTrue(bounds[0].right < bounds[1].left)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-port")
    fun phoneStatisticsKeepTwoRows() {
        render(TabletLayout())
        val duration = composeRule.onNodeWithText("总时长").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val tracks = composeRule.onNodeWithText("音轨").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val days = composeRule.onNodeWithText("活跃天数").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(duration.top, tracks.top, 1f)
        assertTrue(days.top > duration.bottom)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletSummaryKeepsLargeTrafficChangesReadableAtEnlargedFont() {
        assertSummaryFits(478.dp, 1.3f)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun narrowSummaryStacksLongChangesWithoutClipping() {
        assertSummaryFits(328.dp, 1.5f)
    }

    private fun assertSummaryFits(width: Dp, fontScale: Float) {
        composeRule.setContent {
            AsmrPlayerTheme {
                CompositionLocalProvider(LocalDensity provides Density(2.5f, fontScale)) {
                    Box(Modifier.width(width)) {
                        SummaryCards(
                            summary = ListeningSummary(totalTrafficBytes = 12345678901234L), artwork = null,
                            comparison = ListeningSummaryComparison(trafficBytes = ListeningMetricDelta(current = 12345678901234.0)),
                            selectedGranularity = ListeningComparisonGranularity.Day,
                            onGranularitySelected = {}, colorScheme = AsmrTheme.colorScheme,
                        )
                    }
                }
            }
        }
        val card = composeRule.onNodeWithTag("listening_summary_流量").fetchSemanticsNode().boundsInRoot
        val node = composeRule.onNodeWithText("+11497.8G").assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertFalse("流量变化值不应截断或溢出", results.single().hasVisualOverflow)
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= card.left && bounds.right <= card.right)
        assertTrue(bounds.top >= card.top && bounds.bottom <= card.bottom)
    }

    @Test
    @Config(qualifiers = "w1280dp-h1600dp-land-mdpi")
    fun heatmapKeepsEveryCellSquareAtFractionalDensity() {
        val days = ListeningDay.datesBetween("2026-01-01", "2026-12-31").map { HeatmapDay(it, 0L, 0) }
        composeRule.setContent {
            AsmrPlayerTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(2.5f, 1.3f),
                    LocalTabletLayout provides TabletLayout(true, 936.dp, 640.dp)
                ) {
                    Box(Modifier.width(478.dp)) {
                        HeatmapSection(days, null, listOf(2026), 2026, AsmrTheme.colorScheme, {}, {})
                    }
                }
            }
        }
        days.forEach { day ->
            val bounds = composeRule.onNodeWithTag("heatmap_day_${day.date}").fetchSemanticsNode().boundsInRoot
            assertEquals("每个日期格子都应保持完整宽度", 33f, bounds.width, 0.01f)
            assertEquals("每个日期格子都应保持正方形", bounds.width, bounds.height, 0.01f)
            assertTrue("日期格子不能超出当前面板", bounds.right <= 1195f)
        }
    }

    private fun render(layout: TabletLayout) {
        composeRule.setContent {
            AsmrPlayerTheme {
                CompositionLocalProvider(LocalTabletLayout provides layout) {
                    SummaryCards(
                        summary = ListeningSummary(), artwork = null,
                        comparison = ListeningSummaryComparison(),
                        selectedGranularity = ListeningComparisonGranularity.Day,
                        onGranularitySelected = {}, colorScheme = AsmrTheme.colorScheme,
                    )
                }
            }
        }
    }
}
