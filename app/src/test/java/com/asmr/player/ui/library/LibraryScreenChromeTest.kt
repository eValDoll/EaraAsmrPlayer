package com.asmr.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.ui.common.CollapsibleHeaderState
import com.asmr.player.ui.common.interruptScrollableFlingOnPointerDown
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
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
                    currentSort = LibrarySort.AddedDesc,
                    sortMenuExpanded = false,
                    onSortMenuExpandedChange = {},
                    onSortLastPlayed = {},
                    onSortAdded = {},
                    onSortTitle = {},
                    onOpenFilterScreen = {},
                    rightPanelToggle = null,
                    materialColorScheme = MaterialTheme.colorScheme,
                    chromeState = chromeState,
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

    @Test
    fun collapsedLibraryChrome_doesNotBlockUnderlyingItemTap() {
        composeRule.mainClock.autoAdvance = false
        lateinit var chromeState: CollapsibleHeaderState
        var itemClicks = 0

        composeRule.setContent {
            chromeState = remember { CollapsibleHeaderState() }

            AsmrPlayerTheme {
                Box(modifier = Modifier.interruptScrollableFlingOnPointerDown {}) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("library_top_item")
                            .clickable { itemClicks += 1 }
                    )
                    LibraryChrome(
                        searchText = "voice",
                        onSearchTextChange = {},
                        onClearSearch = {},
                        currentSort = LibrarySort.AddedDesc,
                        sortMenuExpanded = false,
                        onSortMenuExpandedChange = {},
                        onSortLastPlayed = {},
                        onSortAdded = {},
                        onSortTitle = {},
                        onOpenFilterScreen = {},
                        rightPanelToggle = null,
                        materialColorScheme = MaterialTheme.colorScheme,
                        chromeState = chromeState,
                        onMeasured = { chromeState.updateHeight(it.height.toFloat()) }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle { chromeState.collapse() }
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("library_top_item").performTouchInput {
            down(center)
            up()
        }

        composeRule.runOnIdle { assertEquals(1, itemClicks) }
    }
}
