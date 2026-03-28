package com.asmr.player.ui.groups

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.media3.common.MediaItem
import com.asmr.player.data.local.db.dao.AlbumGroupTrackRow
import com.asmr.player.ui.testWindowSizeClass
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Rule
import org.junit.Test

class AlbumGroupDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressTrack_keepsReorderInlineInsteadOfOpeningDialog() {
        composeRule.setContent {
            AsmrPlayerTheme {
                AlbumGroupDetailContent(
                    windowSizeClass = testWindowSizeClass(),
                    title = "我的分组",
                    tracks = sampleTracks(),
                    onPlayMediaItems = { _: List<MediaItem>, _: Int -> },
                    onRemoveTrack = {},
                    onRemoveAlbum = {},
                    onMoveTrackToTop = { _, _ -> },
                    onMoveTrackToBottom = { _, _ -> },
                    onSaveAlbumTrackOrder = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("$GROUP_DETAIL_SECTION_HEADER_TAG_PREFIX:101")
            .performClick()
        composeRule.onNodeWithTag("$GROUP_DETAIL_TRACK_TAG_PREFIX:/albums/a/1.mp3")
            .performTouchInput {
                down(center)
                advanceEventTime(800)
                up()
            }

        composeRule.onAllNodesWithTag(GROUP_DETAIL_REORDER_DIALOG_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag("$GROUP_DETAIL_TRACK_TAG_PREFIX:/albums/a/1.mp3").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.TestTag,
                "$GROUP_DETAIL_TRACK_TAG_PREFIX:/albums/a/1.mp3"
            )
        )
        composeRule.onNodeWithTag("$GROUP_DETAIL_TRACK_TAG_PREFIX:/albums/a/2.mp3").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.TestTag,
                "$GROUP_DETAIL_TRACK_TAG_PREFIX:/albums/a/2.mp3"
            )
        )
        composeRule.onAllNodesWithTag("$GROUP_DETAIL_TRACK_TAG_PREFIX:/albums/b/1.mp3")
            .assertCountEquals(0)
    }

    @Test
    fun trackMenu_containsMoveActions() {
        composeRule.setContent {
            AsmrPlayerTheme {
                AlbumGroupDetailContent(
                    windowSizeClass = testWindowSizeClass(),
                    title = "我的分组",
                    tracks = sampleTracks(),
                    onPlayMediaItems = { _: List<MediaItem>, _: Int -> },
                    onRemoveTrack = {},
                    onRemoveAlbum = {},
                    onMoveTrackToTop = { _, _ -> },
                    onMoveTrackToBottom = { _, _ -> },
                    onSaveAlbumTrackOrder = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("$GROUP_DETAIL_SECTION_HEADER_TAG_PREFIX:101")
            .performClick()
        composeRule.onNodeWithTag("$GROUP_DETAIL_TRACK_MENU_BUTTON_TAG_PREFIX:/albums/a/1.mp3")
            .performClick()

        composeRule.onNodeWithTag(GROUP_DETAIL_MOVE_TOP_MENU_ITEM_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, GROUP_DETAIL_MOVE_TOP_MENU_ITEM_TAG)
        )
        composeRule.onNodeWithTag(GROUP_DETAIL_MOVE_BOTTOM_MENU_ITEM_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, GROUP_DETAIL_MOVE_BOTTOM_MENU_ITEM_TAG)
        )
    }

    private fun sampleTracks(): List<AlbumGroupTrackRow> {
        return listOf(
            track(albumId = 101L, mediaId = "/albums/a/1.mp3", title = "A1", albumTitle = "Album A"),
            track(albumId = 101L, mediaId = "/albums/a/2.mp3", title = "A2", albumTitle = "Album A"),
            track(albumId = 202L, mediaId = "/albums/b/1.mp3", title = "B1", albumTitle = "Album B")
        )
    }

    private fun track(albumId: Long, mediaId: String, title: String, albumTitle: String): AlbumGroupTrackRow {
        return AlbumGroupTrackRow(
            groupId = 1L,
            mediaId = mediaId,
            itemOrder = 0,
            createdAt = 0L,
            trackId = mediaId.hashCode().toLong(),
            albumId = albumId,
            trackTitle = title,
            trackDuration = 12.0,
            trackPath = mediaId,
            trackGroup = "",
            albumTitle = albumTitle,
            albumRjCode = "",
            albumWorkId = "",
            albumCoverThumbPath = "",
            albumCoverPath = "",
            albumCoverUrl = ""
        )
    }
}
