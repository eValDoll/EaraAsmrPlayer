package com.asmr.player.ui.playlists

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.local.db.entities.PlaylistItemWithSubtitles
import com.asmr.player.data.repository.PlaylistRepository
import com.asmr.player.ui.common.EARA_EMPTY_STATE_TAG
import com.asmr.player.ui.testWindowSizeClass
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Rule
import org.junit.Test

class PlaylistDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressItem_keepsReorderInlineInsteadOfOpeningDialog() {
        composeRule.setContent {
            AsmrPlayerTheme {
                PlaylistDetailContent(
                    windowSizeClass = testWindowSizeClass(),
                    title = "我的收藏",
                    items = sampleItems(),
                    onPlayAll = { _: List<PlaylistItemEntity>, _: PlaylistItemEntity -> },
                    onRemoveItem = {},
                    onMoveItemToTop = {},
                    onMoveItemToBottom = {},
                    onSaveManualOrder = {}
                )
            }
        }

        composeRule.onNodeWithTag("$PLAYLIST_DETAIL_ITEM_TAG_PREFIX:b")
            .performTouchInput {
                down(center)
                advanceEventTime(800)
                up()
            }

        composeRule.onAllNodesWithTag(PLAYLIST_DETAIL_REORDER_DIALOG_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag("$PLAYLIST_DETAIL_ITEM_TAG_PREFIX:b").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.TestTag,
                "$PLAYLIST_DETAIL_ITEM_TAG_PREFIX:b"
            )
        )
    }

    @Test
    fun itemMenu_containsMoveActions() {
        composeRule.setContent {
            AsmrPlayerTheme {
                PlaylistDetailContent(
                    windowSizeClass = testWindowSizeClass(),
                    title = "我的收藏",
                    items = sampleItems(),
                    onPlayAll = { _: List<PlaylistItemEntity>, _: PlaylistItemEntity -> },
                    onRemoveItem = {},
                    onMoveItemToTop = {},
                    onMoveItemToBottom = {},
                    onSaveManualOrder = {}
                )
            }
        }

        composeRule.onNodeWithTag("$PLAYLIST_DETAIL_ITEM_MENU_BUTTON_TAG_PREFIX:a")
            .performClick()

        composeRule.onNodeWithTag(PLAYLIST_DETAIL_MOVE_TOP_MENU_ITEM_TAG).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.TestTag,
                PLAYLIST_DETAIL_MOVE_TOP_MENU_ITEM_TAG
            )
        )
        composeRule.onNodeWithTag(PLAYLIST_DETAIL_MOVE_BOTTOM_MENU_ITEM_TAG).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.TestTag,
                PLAYLIST_DETAIL_MOVE_BOTTOM_MENU_ITEM_TAG
            )
        )
    }

    @Test
    fun emptyFavorites_showsBrandedEmptyState() {
        composeRule.setContent {
            AsmrPlayerTheme {
                PlaylistDetailContent(
                    windowSizeClass = testWindowSizeClass(),
                    title = PlaylistRepository.PLAYLIST_FAVORITES,
                    items = emptyList(),
                    onPlayAll = { _: List<PlaylistItemEntity>, _: PlaylistItemEntity -> },
                    onRemoveItem = {},
                    onMoveItemToTop = {},
                    onMoveItemToBottom = {},
                    onSaveManualOrder = {}
                )
            }
        }

        composeRule.onNodeWithTag(EARA_EMPTY_STATE_TAG).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.TestTag,
                EARA_EMPTY_STATE_TAG
            )
        )
    }

    private fun sampleItems(): List<PlaylistItemWithSubtitles> {
        return listOf(
            playlistItem(mediaId = "a", title = "Track A"),
            playlistItem(mediaId = "b", title = "Track B"),
            playlistItem(mediaId = "c", title = "Track C")
        )
    }

    private fun playlistItem(mediaId: String, title: String): PlaylistItemWithSubtitles {
        return PlaylistItemWithSubtitles(
            playlistId = 1L,
            mediaId = mediaId,
            title = title,
            artist = "Artist",
            uri = "file:///$mediaId.mp3",
            artworkUri = "",
            playbackArtworkUri = "",
            itemOrder = 0,
            hasSubtitles = false
        )
    }
}
