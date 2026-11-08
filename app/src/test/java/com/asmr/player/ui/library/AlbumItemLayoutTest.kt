package com.asmr.player.ui.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import com.asmr.player.ui.createEaraComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.common.LocalPageAnimationsActive
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class AlbumItemLayoutTest {
    @get:Rule
    val composeRule = createEaraComposeRule()

    @Test
    @Config(sdk = [33])
    fun lightRow_preservesLayoutAndTouchBoundsAcrossVisibilityChanges() {
        checkRowInteractionBounds(ThemeMode.Light)
    }

    @Test
    @Config(sdk = [33])
    fun darkRow_preservesLayoutAndTouchBoundsAcrossVisibilityChanges() {
        checkRowInteractionBounds(ThemeMode.Dark)
    }

    private fun checkRowInteractionBounds(mode: ThemeMode) {
        val pageActive = mutableStateOf(true)
        var clicks = 0
        var longClicks = 0
        val album = Album(
            id = 1L,
            title = "Album row title",
            path = "/tmp/album",
            cv = "CV A / CV B",
            tags = listOf("binaural", "exclusive"),
            rjCode = "RJ999999",
        )
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalPageAnimationsActive provides pageActive.value,
            ) {
                AsmrPlayerTheme(mode = mode) {
                    Box(Modifier.fillMaxSize().padding(32.dp)) {
                        Box(Modifier.width(240.dp)) {
                            AlbumItem(
                                album = album,
                                onClick = { clicks++ },
                                onLongClick = { longClicks++ },
                                animateOnlineDetails = false,
                            )
                        }
                    }
                }
            }
        }

        val row = composeRule.onNodeWithTag(ALBUM_ITEM_CARD_TAG)
        val initialBounds = row.fetchSemanticsNode().boundsInRoot
        val initialTagBounds = composeRule.onNodeWithText("#binaural", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val initialTagNode = composeRule.onNodeWithText("#binaural", useUnmergedTree = true)
            .fetchSemanticsNode().id
        var expectedClicks = 0
        var expectedLongClicks = 0
        listOf(false, true, false).forEach { active ->
            composeRule.runOnIdle {
                pageActive.value = active
            }
            row.assertHasClickAction()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
            assertEquals(initialBounds, row.fetchSemanticsNode().boundsInRoot)
            val tag = composeRule.onNodeWithText("#binaural", useUnmergedTree = true).fetchSemanticsNode()
            assertEquals(initialTagBounds, tag.boundsInRoot)
            assertEquals(initialTagNode, tag.id)

            row.performTouchInput { click(Offset(20f, center.y)) }
            expectedClicks++
            composeRule.runOnIdle { assertEquals(expectedClicks, clicks) }
            // 卡片外的留白不能扩大实际点击区域。
            composeRule.onRoot().performTouchInput {
                click(Offset(initialBounds.left - 8f, initialBounds.center.y))
            }
            composeRule.runOnIdle { assertEquals(expectedClicks, clicks) }
            row.performTouchInput { longClick(Offset(20f, center.y)) }
            expectedLongClicks++
            composeRule.runOnIdle {
                assertEquals(expectedLongClicks, longClicks)
                assertEquals(expectedClicks, clicks)
            }
        }
    }

    @Test
    fun albumItem_onlineMetadataKeepsItsNodesWhenPageVisibilityChanges() {
        val animationsActive = mutableStateOf(false)
        val album = Album(
            id = 1L,
            title = "Album title",
            path = "/tmp/album",
            circle = "Example Circle",
            cv = "CV A / CV B",
            tags = listOf("binaural", "exclusive"),
            workId = "RJ999999",
            rjCode = "RJ999999",
            ratingValue = 4.9,
            ratingCount = 3210,
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalPageAnimationsActive provides animationsActive.value) {
                AsmrPlayerTheme {
                    Box(modifier = Modifier.width(310.dp)) {
                        AlbumItem(
                            album = album,
                            onClick = {},
                            animateOnlineDetails = true,
                        )
                    }
                }
            }
        }

        fun metadataNodeIds() = listOf("CV A", "#binaural").associateWith { text ->
            composeRule.onNodeWithText(text, useUnmergedTree = true)
                .assertExists()
                .fetchSemanticsNode().id
        }

        val initialNodeIds = metadataNodeIds()
        composeRule.runOnIdle { animationsActive.value = true }
        composeRule.waitForIdle()
        assertEquals(initialNodeIds, metadataNodeIds())

        composeRule.runOnIdle { animationsActive.value = false }
        composeRule.waitForIdle()
        assertEquals(initialNodeIds, metadataNodeIds())
    }

    @Test
    fun albumItem_expandsHeightSoStatsLineIsNotClippedOnNarrowLayouts() {
        val album = Album(
            id = 1L,
            title = "A very long album title for narrow layouts",
            path = "/tmp/album",
            circle = "Example Circle With Longer Name",
            cv = "CV A / CV B / CV C",
            tags = listOf("binaural", "long-tag", "exclusive"),
            workId = "RJ999999",
            rjCode = "RJ999999",
            ratingValue = 4.9,
            ratingCount = 3210,
            dlCount = 98765,
            priceJpy = 123456,
            releaseDate = "2026-05-19"
        )

        composeRule.setContent {
            val base = LocalConfiguration.current
            val compactConfig = Configuration(base).apply {
                screenWidthDp = 310
                smallestScreenWidthDp = 310
            }

            CompositionLocalProvider(
                LocalConfiguration provides compactConfig,
                LocalDensity provides Density(1f, 1f)
            ) {
                AsmrPlayerTheme {
                    Box(modifier = Modifier.width(310.dp)) {
                        AlbumItem(
                            album = album,
                            onClick = {}
                        )
                    }
                }
            }
        }

        val cardBounds = composeRule.onNodeWithTag(ALBUM_ITEM_CARD_TAG).getUnclippedBoundsInRoot()
        val statsBounds = composeRule.onNodeWithTag(ALBUM_ITEM_STATS_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val cardHeight = cardBounds.bottom - cardBounds.top

        assertTrue(
            "Expected stats row to remain inside the album card bounds",
            statsBounds.bottom <= cardBounds.bottom
        )
        assertTrue(
            "Expected album card height to expand beyond the old 140dp minimum when metadata is dense",
            cardHeight > 140.dp
        )
    }

    @Test
    fun albumItem_tagsLineClipsAtCardRightEdge() {
        val album = Album(
            id = 1L,
            title = "Album title",
            path = "/tmp/album",
            circle = "Example Circle",
            cv = "CV A / CV B",
            tags = listOf(
                "very-long-first-tag-name",
                "second-long-tag-name",
                "third-long-tag-name"
            ),
            workId = "RJ999999",
            rjCode = "RJ999999"
        )

        composeRule.setContent {
            val base = LocalConfiguration.current
            val compactConfig = Configuration(base).apply {
                screenWidthDp = 310
                smallestScreenWidthDp = 310
            }

            CompositionLocalProvider(
                LocalConfiguration provides compactConfig,
                LocalDensity provides Density(1f, 1f)
            ) {
                AsmrPlayerTheme {
                    Box(modifier = Modifier.width(310.dp)) {
                        AlbumItem(
                            album = album,
                            onClick = {}
                        )
                    }
                }
            }
        }

        val cardBounds = composeRule.onNodeWithTag(ALBUM_ITEM_CARD_TAG).getUnclippedBoundsInRoot()
        val tagsBounds = composeRule.onNodeWithTag(ALBUM_ITEM_TAGS_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertTrue(
            "Expected tags row clipping boundary to reach the album card right edge: " +
                "card=$cardBounds tags=$tagsBounds",
            abs((tagsBounds.right - cardBounds.right).value) <= 0.5f
        )
    }
}
