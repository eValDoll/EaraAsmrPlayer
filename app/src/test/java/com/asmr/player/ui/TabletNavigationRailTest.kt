package com.asmr.player.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.nav.TabletNavigationRail
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1024dp-h640dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletNavigationRailTest {
    @get:Rule val composeRule = createEaraComposeRule()

    @Test
    fun smallTabletShowsAllDestinationsAndOpensThemDirectly() {
        val routes = mutableListOf<String>()
        var navigationBottom = 0.dp
        composeRule.setContent {
            AsmrPlayerTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    val density = LocalDensity.current
                    navigationBottom = with(density) { StableWindowInsets.navigationBars.getBottom(this).toDp() }
                    TabletNavigationRail(activeRoute = "groups", onNavigate = { routes += it })
                }
            }
        }
        listOf("本地库", "在线搜索", "热门收听", "我的收藏", "我的列表", "我的分组", "ASMR 看板", "设置").forEach {
            composeRule.onNodeWithTag("tabletNavigation:$it").assertIsDisplayed()
        }
        val railBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
        val settingsBounds = composeRule.onNodeWithTag("tabletNavigation:设置").getUnclippedBoundsInRoot()
        assertTrue("导航按钮应靠下排列，底部仅保留安全间距", railBounds.bottom - settingsBounds.bottom <= navigationBottom + 16.dp)
        composeRule.onNodeWithTag("tabletNavigation:我的分组").assertIsSelected()
        composeRule.onNodeWithText("更多").assertDoesNotExist()
        composeRule.onNodeWithText("Eara").assertIsDisplayed()
        val brandBounds = composeRule.onNodeWithTag("tabletBrand").getUnclippedBoundsInRoot()
        val firstNavigationBounds = composeRule.onNodeWithTag("tabletNavigation:本地库").getUnclippedBoundsInRoot()
        assertTrue("品牌标识应位于导航项上方", brandBounds.bottom <= firstNavigationBounds.top)
        composeRule.onNodeWithText("任务管理").assertDoesNotExist()
        val labelLayouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("ASMR 看板").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(labelLayouts)
        }
        val labelLayout = labelLayouts.single()
        assertEquals(1, labelLayout.lineCount)
        assertFalse(
            "size=${labelLayout.size}, paragraph=${labelLayout.multiParagraph.width}x${labelLayout.multiParagraph.height}, " +
                "lines=${labelLayout.lineCount}, exceeded=${labelLayout.multiParagraph.didExceedMaxLines}, " +
                "width=${labelLayout.didOverflowWidth}, height=${labelLayout.didOverflowHeight}",
            labelLayout.hasVisualOverflow,
        )
        listOf("我的列表", "我的分组", "ASMR 看板").forEach {
            composeRule.onNodeWithTag("tabletNavigation:$it").performClick()
        }
        assertEquals(listOf("playlists", "groups", "listening_calendar"), routes)
    }
}
