package com.asmr.player.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import com.asmr.player.ui.player.MiniPlayerDisplayMode
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val BOTTOM_CHROME_UNDERLAY_TAG = "bottomChromeUnderlay"

@RunWith(AndroidJUnit4::class)
class BottomChromeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun blankAreaTap_doesNotPassThroughToUnderlyingContent() {
        var underlayClicks = 0

        composeRule.setContent {
            AsmrPlayerTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 120.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(BOTTOM_CHROME_UNDERLAY_TAG)
                            .clickable { underlayClicks++ }
                    )
                    BottomChrome(
                        activeRoute = Routes.Library,
                        miniPlayerVisible = false,
                        miniPlayerDisplayMode = MiniPlayerDisplayMode.CoverOnly,
                        onMiniPlayerDisplayModeChange = {},
                        onOpenNowPlaying = {},
                        onOpenQueue = {},
                        onNavigate = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeRule.onNodeWithTag(BottomNavBarTag)
            .performTouchInput {
                down(centerLeft.copy(y = 4f))
                up()
            }

        composeRule.runOnIdle {
            assertEquals(0, underlayClicks)
        }
    }

    @Test
    fun navItemTap_stillInvokesNavigation() {
        var lastRoute: String? = null

        composeRule.setContent {
            AsmrPlayerTheme {
                BottomChrome(
                    activeRoute = Routes.Library,
                    miniPlayerVisible = false,
                    miniPlayerDisplayMode = MiniPlayerDisplayMode.CoverOnly,
                    onMiniPlayerDisplayModeChange = {},
                    onOpenNowPlaying = {},
                    onOpenQueue = {},
                    onNavigate = { lastRoute = it },
                    modifier = Modifier.size(width = 360.dp, height = 120.dp)
                )
            }
        }

        composeRule.onNodeWithTag("bottomNavItem:search").performClick()

        composeRule.runOnIdle {
            assertEquals(Routes.Search, lastRoute)
        }
    }
}
