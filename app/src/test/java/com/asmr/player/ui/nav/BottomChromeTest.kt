package com.asmr.player.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.asmr.player.ui.player.MiniPlayerDisplayMode
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

private const val BOTTOM_CHROME_UNDERLAY_TAG = "bottomChromeUnderlay"
private const val BOTTOM_CHROME_ROOT_TAG = "bottomChromeRoot"

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

    @Test
    fun compactWidthWithMiniPlayer_scalesChromeWithoutChangingVisibleNavCount_336dp() {
        compactWidthWithMiniPlayer_scalesChromeWithoutChangingVisibleNavCount(336.dp)
    }

    @Test
    fun compactWidthWithMiniPlayer_scalesChromeWithoutChangingVisibleNavCount_360dp() {
        compactWidthWithMiniPlayer_scalesChromeWithoutChangingVisibleNavCount(360.dp)
    }

    @Test
    fun compactWidthWithMiniPlayer_keepsNavHeightStableBetweenModes() {
        val displayMode = mutableStateOf(MiniPlayerDisplayMode.CoverOnly)

        composeRule.setContent {
            AsmrPlayerTheme {
                BottomChrome(
                    activeRoute = Routes.Library,
                    miniPlayerVisible = true,
                    miniPlayerDisplayMode = displayMode.value,
                    onMiniPlayerDisplayModeChange = {},
                    onOpenNowPlaying = {},
                    onOpenQueue = {},
                    onNavigate = {},
                    modifier = Modifier.size(width = 336.dp, height = 120.dp),
                    miniPlayerContent = { miniModifier ->
                        Box(
                            modifier = miniModifier.height(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Mini")
                        }
                    }
                )
            }
        }

        val coverOnlyBounds = composeRule.onNodeWithTag(BottomNavBarTag).getUnclippedBoundsInRoot()
        val coverOnlyHeight = coverOnlyBounds.bottom - coverOnlyBounds.top

        composeRule.runOnIdle {
            displayMode.value = MiniPlayerDisplayMode.Expanded
        }
        composeRule.waitForIdle()

        val expandedBounds = composeRule.onNodeWithTag(BottomNavBarTag).getUnclippedBoundsInRoot()
        val expandedHeight = expandedBounds.bottom - expandedBounds.top

        assertTrue(
            "Expected compact nav bar height to stay stable while mini player expands",
            abs((coverOnlyHeight - expandedHeight).value) <= 0.5f
        )
    }

    @Test
    fun compactWidthWithMiniPlayer_doesNotJumpActiveItemWhenExpanding() {
        val displayMode = mutableStateOf(MiniPlayerDisplayMode.CoverOnly)
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.setContent {
                AsmrPlayerTheme {
                    BottomChrome(
                        activeRoute = "settings",
                        miniPlayerVisible = true,
                        miniPlayerDisplayMode = displayMode.value,
                        onMiniPlayerDisplayModeChange = {},
                        onOpenNowPlaying = {},
                        onOpenQueue = {},
                        onNavigate = {},
                        modifier = Modifier.size(width = 336.dp, height = 120.dp),
                        miniPlayerContent = { miniModifier ->
                            Box(
                                modifier = miniModifier.height(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Mini")
                            }
                        }
                    )
                }
            }

            composeRule.waitForIdle()
            val beforeLeft = composeRule.onNodeWithTag("bottomNavItem:settings")
                .getUnclippedBoundsInRoot()
                .left

            composeRule.runOnIdle {
                displayMode.value = MiniPlayerDisplayMode.Expanded
            }
            composeRule.waitForIdle()

            val afterLeft = composeRule.onNodeWithTag("bottomNavItem:settings")
                .getUnclippedBoundsInRoot()
                .left

            assertTrue(
                "Expected active nav item not to jump horizontally on the first expand frame",
                abs((afterLeft - beforeLeft).value) <= 0.5f
            )
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun compactWidthWithMiniPlayer_activeItemFollowsCollapseWithoutHorizontalBacktrack() {
        val displayMode = mutableStateOf(MiniPlayerDisplayMode.CoverOnly)
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.setContent {
                AsmrPlayerTheme {
                    BottomChrome(
                        activeRoute = "settings",
                        miniPlayerVisible = true,
                        miniPlayerDisplayMode = displayMode.value,
                        onMiniPlayerDisplayModeChange = {},
                        onOpenNowPlaying = {},
                        onOpenQueue = {},
                        onNavigate = {},
                        modifier = Modifier.size(width = 336.dp, height = 120.dp),
                        miniPlayerContent = { miniModifier ->
                            Box(
                                modifier = miniModifier.height(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Mini")
                            }
                        }
                    )
                }
            }

            composeRule.waitForIdle()
            val collapseCenters = mutableListOf(activeItemCenterX("settings"))
            composeRule.runOnIdle {
                displayMode.value = MiniPlayerDisplayMode.Expanded
            }
            repeat(30) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
                collapseCenters += activeItemCenterX("settings")
            }

            assertNoHorizontalBacktrack(
                label = "collapsing nav active item",
                positions = collapseCenters
            )
            val totalCollapseMove = abs(collapseCenters.last() - collapseCenters.first())
            val earlyCollapseMove = abs(collapseCenters[4] - collapseCenters.first())
            assertTrue(
                "Expected the active item to start moving with the collapse animation instead of snapping late",
                earlyCollapseMove >= totalCollapseMove * 0.08f
            )

            val expandCenters = mutableListOf(activeItemCenterX("settings"))
            composeRule.runOnIdle {
                displayMode.value = MiniPlayerDisplayMode.CoverOnly
            }
            repeat(30) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
                expandCenters += activeItemCenterX("settings")
            }

            assertNoHorizontalBacktrack(
                label = "expanding nav active item",
                positions = expandCenters
            )
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun navBar_withoutMiniPlayer_staysCentered() {
        composeRule.setContent {
            AsmrPlayerTheme {
                Box(
                    modifier = Modifier
                        .size(width = 360.dp, height = 120.dp)
                        .testTag(BOTTOM_CHROME_ROOT_TAG),
                    contentAlignment = Alignment.BottomCenter
                ) {
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

        val rootBounds = composeRule.onNodeWithTag(BOTTOM_CHROME_ROOT_TAG).getUnclippedBoundsInRoot()
        val navBounds = composeRule.onNodeWithTag(BottomNavBarTag).getUnclippedBoundsInRoot()
        val rootCenterX = (rootBounds.left + rootBounds.right) / 2f
        val navCenterX = (navBounds.left + navBounds.right) / 2f

        assertTrue(
            "Expected nav bar center to stay near the screen center when mini player is hidden",
            kotlin.math.abs((navCenterX - rootCenterX).value) <= 1f
        )
    }

    @Test
    fun navBar_andMiniPlayer_areCenteredAsAGroup() {
        composeRule.setContent {
            AsmrPlayerTheme {
                Box(
                    modifier = Modifier
                        .size(width = 360.dp, height = 120.dp)
                        .testTag(BOTTOM_CHROME_ROOT_TAG),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    BottomChrome(
                        activeRoute = Routes.Library,
                        miniPlayerVisible = true,
                        miniPlayerDisplayMode = MiniPlayerDisplayMode.CoverOnly,
                        onMiniPlayerDisplayModeChange = {},
                        onOpenNowPlaying = {},
                        onOpenQueue = {},
                        onNavigate = {},
                        modifier = Modifier.fillMaxSize(),
                        miniPlayerContent = { miniModifier ->
                            Box(
                                modifier = miniModifier
                                    .height(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Mini")
                            }
                        }
                    )
                }
            }
        }

        val rootBounds = composeRule.onNodeWithTag(BOTTOM_CHROME_ROOT_TAG).getUnclippedBoundsInRoot()
        val navBounds = composeRule.onNodeWithTag(BottomNavBarTag).getUnclippedBoundsInRoot()
        val miniBounds = composeRule.onNodeWithTag(BottomChromeMiniPlayerTag).getUnclippedBoundsInRoot()
        val rootCenterX = (rootBounds.left + rootBounds.right) / 2f
        val groupCenterX = (navBounds.left + miniBounds.right) / 2f

        assertTrue(
            "Expected bottom chrome group to stay centered when mini player is visible",
            kotlin.math.abs((groupCenterX - rootCenterX).value) <= 1f
        )
    }

    @Test
    fun inactiveNavItemContainer_isTransparent() {
        val activeContainer = Color(0xFFF2D4C3)

        val inactive = resolveBottomNavItemContainerColor(
            activeContainer = activeContainer,
            selectedProgress = 0f
        )
        val active = resolveBottomNavItemContainerColor(
            activeContainer = activeContainer,
            selectedProgress = 1f
        )

        assertEquals(Color.Transparent, inactive)
        assertEquals(activeContainer, active)
    }

    @Test
    fun partialNavItemContainer_preservesScaledAlpha() {
        val activeContainer = Color(0xCCF2D4C3)

        val partial = resolveBottomNavItemContainerColor(
            activeContainer = activeContainer,
            selectedProgress = 0.5f
        )

        assertEquals(activeContainer.red, partial.red, 0.0001f)
        assertEquals(activeContainer.green, partial.green, 0.0001f)
        assertEquals(activeContainer.blue, partial.blue, 0.0001f)
        assertTrue(abs(partial.alpha - (activeContainer.alpha * 0.5f)) <= 0.0001f)
    }

    private fun compactWidthWithMiniPlayer_scalesChromeWithoutChangingVisibleNavCount(width: Dp) {
        composeRule.setContent {
            AsmrPlayerTheme {
                BottomChrome(
                    activeRoute = Routes.Library,
                    miniPlayerVisible = true,
                    miniPlayerDisplayMode = MiniPlayerDisplayMode.CoverOnly,
                    onMiniPlayerDisplayModeChange = {},
                    onOpenNowPlaying = {},
                    onOpenQueue = {},
                    onNavigate = {},
                    modifier = Modifier.size(width = width, height = 120.dp),
                    miniPlayerContent = { miniModifier ->
                        Box(
                            modifier = miniModifier
                                .height(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Mini")
                        }
                    }
                )
            }
        }

        val navBounds = composeRule.onNodeWithTag(BottomNavBarTag).getUnclippedBoundsInRoot()
        val overflowBounds = composeRule.onNodeWithTag(BottomNavOverflowTag).getUnclippedBoundsInRoot()
        val miniBounds = composeRule.onNodeWithTag(BottomChromeMiniPlayerTag).getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag("bottomNavItem:playlist_system/favorites").getUnclippedBoundsInRoot()

        assertTrue(
            "Expected compact overflow toggle to stay inside the nav bar",
            overflowBounds.right.value <= navBounds.right.value + 0.5f
        )
        assertTrue(
            "Expected compact nav bar and mini player not to overlap",
            navBounds.right.value <= miniBounds.left.value + 0.5f
        )

        composeRule.onNodeWithTag(BottomNavOverflowTag).performClick()
        composeRule.onNodeWithTag("bottomNavItem:playlists").getUnclippedBoundsInRoot()
    }

    private fun activeItemCenterX(route: String): Float {
        val bounds = composeRule.onNodeWithTag("bottomNavItem:$route")
            .getUnclippedBoundsInRoot()
        return ((bounds.left + bounds.right) / 2f).value
    }

    private fun assertNoHorizontalBacktrack(label: String, positions: List<Float>) {
        val meaningfulDeltas = positions
            .zipWithNext { previous, next -> next - previous }
            .filter { abs(it) > 0.25f }
        if (meaningfulDeltas.isEmpty()) return

        val direction = meaningfulDeltas.first()
        assertTrue(
            "Expected $label to move in one horizontal direction without backtracking: $positions",
            meaningfulDeltas.all { it * direction >= -0.01f }
        )
    }
}
