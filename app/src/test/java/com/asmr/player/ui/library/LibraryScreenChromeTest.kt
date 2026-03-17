package com.asmr.player.ui.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.ui.common.CollapsibleHeaderState
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenChromeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun libraryChrome_collapsesAndExpandsWhileKeepingControlsMounted() {
        composeRule.mainClock.autoAdvance = false
        lateinit var chromeState: CollapsibleHeaderState

        composeRule.setContent {
            chromeState = remember { CollapsibleHeaderState() }

            AsmrPlayerTheme {
                LibraryChrome(
                    modifier = Modifier,
                    searchText = "voice",
                    onSearchTextChange = {},
                    onClearSearch = {},
                    sortMenuExpanded = false,
                    onSortMenuExpandedChange = {},
                    onSortLastPlayed = {},
                    onSortAdded = {},
                    onSortTitle = {},
                    onOpenFilterScreen = {},
                    rightPanelToggle = null,
                    dynamicContainerColor = MaterialTheme.colorScheme.surface,
                    materialColorScheme = MaterialTheme.colorScheme,
                    chromeOffsetPx = chromeState.offsetPx,
                    collapseFraction = chromeState.collapseFraction,
                    onMeasured = { chromeState.updateHeight(it.height.toFloat()) }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LIBRARY_CHROME_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "expanded")
        )

        composeRule.runOnIdle { chromeState.onScrollDelta(-1000f) }
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LIBRARY_CHROME_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "collapsed")
        )
        composeRule.onNodeWithTag(LIBRARY_SEARCH_INPUT_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, LIBRARY_SEARCH_INPUT_TAG)
        )
        composeRule.onNodeWithTag(LIBRARY_SORT_BUTTON_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, LIBRARY_SORT_BUTTON_TAG)
        )
        composeRule.onNodeWithTag(LIBRARY_FILTER_BUTTON_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, LIBRARY_FILTER_BUTTON_TAG)
        )

        composeRule.runOnIdle { chromeState.expand() }
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LIBRARY_CHROME_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "expanded")
        )
    }
}
