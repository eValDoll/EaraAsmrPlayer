package com.asmr.player.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomChromeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun computeVisibleNavItems_keepsActiveRouteVisible() {
        val layout = computeVisibleNavItems(
            allItems = bottomChromeNavItems(),
            activeRoute = "downloads",
            availableWidth = 260.dp
        )

        assertTrue(layout.showsOverflow)
        assertTrue(layout.visibleItems.any { it.route == "downloads" })
        assertTrue(layout.overflowItems.isNotEmpty())
    }

    @Test
    fun resolvePrimaryRoute_mapsSecondaryRoutesToTheirOwner() {
        assertEquals("playlists", resolvePrimaryRoute("playlist/{playlistId}/{playlistName}", "library"))
        assertEquals("groups", resolvePrimaryRoute("group/{groupId}/{groupName}", "library"))
        assertEquals("library", resolvePrimaryRoute("library_filter", "search"))
        assertEquals("search", resolvePrimaryRoute("album_detail_rj/{rj}", "search"))
        assertEquals(
            "playlist_system/favorites",
            resolvePrimaryRoute("playlist_system/{type}", "library", playlistSystemType = "favorites")
        )
    }

    @Test
    fun bottomNavigationPill_collapsedOnlyShowsActiveItem() {
        composeRule.setContent {
            AsmrPlayerTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    BottomNavigationPill(
                        navItems = bottomChromeNavItems(),
                        activeRoute = "downloads",
                        expanded = false,
                        availableWidth = 320.dp,
                        onNavigate = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag(BottomNavBarTag).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "collapsed")
        )
        composeRule.onAllNodesWithTag("bottomNavItem:downloads").assertCountEquals(1)
        composeRule.onAllNodesWithTag("bottomNavItem:settings").assertCountEquals(0)
    }

    @Test
    fun bottomNavigationPill_overflowMenuNavigatesToHiddenItem() {
        var selectedRoute = ""

        composeRule.setContent {
            AsmrPlayerTheme {
                var overflowExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.width(260.dp)) {
                    BottomNavigationPill(
                        navItems = bottomChromeNavItems(),
                        activeRoute = "downloads",
                        expanded = true,
                        availableWidth = 260.dp,
                        overflowExpanded = overflowExpanded,
                        onOverflowExpandedChange = { overflowExpanded = it },
                        onNavigate = { selectedRoute = it }
                    )
                }
            }
        }

        composeRule.onNodeWithTag(BottomNavBarTag).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "expanded")
        )
        composeRule.onAllNodesWithTag("bottomNavItem:downloads").assertCountEquals(1)
        composeRule.onNodeWithTag(BottomNavOverflowTag).performClick()
        composeRule.onNodeWithTag("overflowIcon:settings").performClick()

        assertEquals("settings", selectedRoute)
    }
}
