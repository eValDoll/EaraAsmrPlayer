package com.asmr.player.ui.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class AlbumItemLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

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
                LocalDensity provides Density(2.75f, 1f)
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
        val statsBounds = composeRule.onNodeWithTag(ALBUM_ITEM_STATS_TAG).getUnclippedBoundsInRoot()
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
                LocalDensity provides Density(2.75f, 1f)
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
        val tagsBounds = composeRule.onNodeWithTag(ALBUM_ITEM_TAGS_TAG).getUnclippedBoundsInRoot()

        assertTrue(
            "Expected tags row clipping boundary to reach the album card right edge",
            abs((tagsBounds.right - cardBounds.right).value) <= 0.5f
        )
    }
}
