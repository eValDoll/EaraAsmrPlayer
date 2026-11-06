package com.asmr.player.ui.calendar

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
