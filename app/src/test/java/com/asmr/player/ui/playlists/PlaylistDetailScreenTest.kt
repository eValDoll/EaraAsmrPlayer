package com.asmr.player.ui.playlists

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.asmr.player.ui.createEaraComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.local.db.entities.PlaylistItemWithSubtitles
import com.asmr.player.data.local.db.entities.PlaylistPlaybackEntity
import com.asmr.player.data.repository.PlaylistRepository
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.playback.withSourcePlaylist
import com.asmr.player.ui.common.EARA_EMPTY_STATE_TAG
import com.asmr.player.ui.common.reorderable.ItemPosition
import com.asmr.player.ui.testWindowSizeClass
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistDetailScreenTest {
    @get:Rule
    val composeRule = createEaraComposeRule()

    @Test
    fun longPressItem_keepsReorderInlineInsteadOfOpeningDialog() {
        composeRule.setContent {
            AsmrPlayerTheme {
                PlaylistDetailContent(
                    windowSizeClass = testWindowSizeClass(),
                    title = "我的收藏",
                    items = sampleItems(),
                    onPlayAll = { _: List<PlaylistItemEntity>, _: PlaylistItemEntity -> },
                    onResumeAll = { _, _, _ -> },
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
                    onResumeAll = { _, _, _ -> },
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
                    onResumeAll = { _, _, _ -> },
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
        composeRule.onNodeWithTag(PLAYLIST_TRACK_HERO_TAG).assertDoesNotExist()
    }

    @Test
    fun heroContinuesLastPlayedItemInCurrentListOrder() {
        val history = PlaylistPlaybackEntity(
            playlistId = 1L,
            mediaId = "b",
            positionMs = 4_250L,
            durationMs = 12_000L,
            isCompleted = false,
            updatedAt = 10L
        )
        var requestedIds = emptyList<String>()
        var requestedStart = ""
        var requestedPosition = -1L
        composeRule.setContent {
            AsmrPlayerTheme {
                PlaylistDetailContent(
                    windowSizeClass = testWindowSizeClass(),
                    title = "晚安时刻",
                    items = sampleItems(),
                    playbackHistory = MutableStateFlow(history),
                    onPlayAll = { _, _ -> },
                    onResumeAll = { items, start, position ->
                        requestedIds = items.map { it.mediaId }
                        requestedStart = start.mediaId
                        requestedPosition = position
                    },
                    onRemoveItem = {},
                    onMoveItemToTop = {},
                    onMoveItemToBottom = {},
                    onSaveManualOrder = {}
                )
            }
        }
        composeRule.onNodeWithTag(PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG).performClick()
        assertEquals(listOf("a", "b", "c"), requestedIds)
        assertEquals("b", requestedStart)
        assertEquals(4_250L, requestedPosition)
    }

    @Test
    fun heroFallsBackWhenRecordIsMissingRemovedOrFromAnotherList() {
        val items = sampleItems()
        val history = PlaylistPlaybackEntity(1L, "b", 4_250L, 12_000L, false, 10L)
        assertEquals("b", resolvePlaylistHeroItem(items, history)?.mediaId)
        assertEquals("a", resolvePlaylistHeroItem(items, null)?.mediaId)
        assertEquals("a", resolvePlaylistHeroItem(items, history.copy(mediaId = "removed"))?.mediaId)
        assertEquals("a", resolvePlaylistHeroItem(items, history.copy(playlistId = 2L))?.mediaId)
        assertNull(resolvePlaylistHeroItem(emptyList(), history))
    }

    @Test
    fun livePlaybackSelectsTheStartedTrackBeforeItsDatabaseRecordArrives() {
        val history = MutableStateFlow<PlaylistPlaybackEntity?>(
            PlaylistPlaybackEntity(1L, "a", 1_000L, 12_000L, false, 10L)
        )
        val live = MutableStateFlow(PlaybackSnapshot())
        var requestedStart = ""
        var requestedPosition = -1L
        composeRule.setContent {
            AsmrPlayerTheme {
                PlaylistDetailContent(
                    windowSizeClass = testWindowSizeClass(),
                    title = "晚安时刻",
                    items = sampleItems(),
                    playbackHistory = history,
                    playback = live,
                    onPlayAll = { _, _ -> },
                    onResumeAll = { _, start, position ->
                        requestedStart = start.mediaId
                        requestedPosition = position
                    },
                    onRemoveItem = {},
                    onMoveItemToTop = {},
                    onMoveItemToBottom = {},
                    onSaveManualOrder = {},
                )
            }
        }
        composeRule.runOnIdle { live.value = playingSnapshot("b", positionMs = 2_500L) }
        composeRule.onNodeWithTag(PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG).performClick()
        assertEquals("b", requestedStart)
        assertEquals(2_500L, requestedPosition)

        composeRule.runOnIdle {
            history.value = requireNotNull(history.value).copy(positionMs = 1_500L, updatedAt = 11L)
            live.value = playingSnapshot("b", positionMs = 3_500L).copy(isPlaying = false, playWhenReady = false)
        }
        composeRule.onNodeWithTag(PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG).performClick()
        assertEquals("b", requestedStart)
        assertEquals(3_500L, requestedPosition)
    }

    @Test
    fun bufferingAndFailedRequestsDoNotBecomeObservedHistoryAndOtherListsStayIndependent() {
        val state = PlaylistHeroPlaybackState(1L)
        val history = PlaylistPlaybackEntity(1L, "a", 1_000L, 12_000L, false, 10L)
        val buffering = playingSnapshot("b").copy(isPlaying = false, playbackState = Player.STATE_BUFFERING)
        state.update(buffering)
        assertNull(state.pendingMediaId)
        assertNull(state.observedPlayback("b", history))
        assertEquals("b", resolveRequestedPlaylistHeroMediaId(buffering, 1L))
        state.update(buffering.copy(playbackState = Player.STATE_IDLE))
        assertNull(state.pendingMediaId)
        assertNull(resolveRequestedPlaylistHeroMediaId(buffering.copy(playbackState = Player.STATE_IDLE), 1L))
        state.update(playingSnapshot("b", playlistId = 2L))
        assertNull(state.pendingMediaId)
        assertNull(state.observedPlayback("b"))
        assertNull(resolveRequestedPlaylistHeroMediaId(playingSnapshot("b", playlistId = 2L), 1L))
    }

    @Test
    fun heroShowsRequestedTrackDuringBufferingThenFallsBackOnFailureAndRetainsActualPlaybackAfterPause() {
        val history = MutableStateFlow<PlaylistPlaybackEntity?>(
            PlaylistPlaybackEntity(1L, "a", 1_000L, 12_000L, false, 10L)
        )
        val live = MutableStateFlow(playingSnapshot("a", positionMs = 1_000L))
        var requestedStart = ""
        var requestedPosition = -1L
        composeRule.setContent {
            AsmrPlayerTheme {
                PlaylistDetailContent(
                    windowSizeClass = testWindowSizeClass(),
                    title = "晚安时刻",
                    items = sampleItems(),
                    playbackHistory = history,
                    playback = live,
                    onPlayAll = { _, _ -> },
                    onResumeAll = { _, start, position ->
                        requestedStart = start.mediaId
                        requestedPosition = position
                    },
                    onRemoveItem = {},
                    onMoveItemToTop = {},
                    onMoveItemToBottom = {},
                    onSaveManualOrder = {},
                )
            }
        }
        val bufferingB = playingSnapshot("b").copy(isPlaying = false, playbackState = Player.STATE_BUFFERING)
        composeRule.runOnIdle { live.value = bufferingB }
        assertHeroTitle("Track B")
        composeRule.onNodeWithTag(PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG).performClick()
        assertEquals("b", requestedStart)
        composeRule.mainClock.advanceTimeBy(4_000L)
        assertHeroTitle("Track B")
        assertEquals("a", history.value?.mediaId)

        composeRule.runOnIdle { live.value = bufferingB.copy(playbackState = Player.STATE_IDLE) }
        assertHeroTitle("Track A")
        composeRule.onNodeWithTag(PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG).performClick()
        assertEquals("a", requestedStart)
        assertEquals(1_000L, requestedPosition)

        composeRule.runOnIdle { live.value = bufferingB }
        assertHeroTitle("Track B")
        composeRule.runOnIdle { live.value = playingSnapshot("b", positionMs = 2_500L) }
        assertHeroTitle("Track B")
        composeRule.runOnIdle {
            live.value = playingSnapshot("b", positionMs = 3_500L).copy(isPlaying = false, playWhenReady = false)
            history.value = requireNotNull(history.value).copy(updatedAt = 11L)
        }
        assertHeroTitle("Track B")
        composeRule.onNodeWithTag(PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG).performClick()
        assertEquals("b", requestedStart)
        assertEquals(3_500L, requestedPosition)
    }

    @Test
    fun failedReplayOfTheSameTrackContinuesFromItsLastActualPosition() {
        val history = MutableStateFlow<PlaylistPlaybackEntity?>(
            PlaylistPlaybackEntity(1L, "b", 50_000L, 120_000L, false, 10L)
        )
        val actual = playingSnapshot("b", positionMs = 50_000L).copy(durationMs = 120_000L)
        val live = MutableStateFlow(actual)
        var requestedPosition = -1L
        composeRule.setContent {
            AsmrPlayerTheme {
                PlaylistDetailContent(
                    windowSizeClass = testWindowSizeClass(),
                    title = "晚安时刻",
                    items = sampleItems(),
                    playbackHistory = history,
                    playback = live,
                    onPlayAll = { _, _ -> },
                    onResumeAll = { _, _, position -> requestedPosition = position },
                    onRemoveItem = {},
                    onMoveItemToTop = {},
                    onMoveItemToBottom = {},
                    onSaveManualOrder = {},
                )
            }
        }
        val buffering = actual.copy(isPlaying = false, playbackState = Player.STATE_BUFFERING, positionMs = 0L)
        composeRule.runOnIdle { live.value = buffering }
        assertHeroTitle("Track B")
        composeRule.runOnIdle { live.value = buffering.copy(playbackState = Player.STATE_IDLE, durationMs = 0L) }
        assertHeroTitle("Track B")
        composeRule.onNodeWithTag(PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG).performClick()
        assertEquals(50_000L, requestedPosition)
    }

    @Test
    fun unplayedBufferingBeforeSwitchingSourceDoesNotOverwriteTheObservedPosition() {
        var now = 20L
        val state = PlaylistHeroPlaybackState(1L) { now }
        val history = PlaylistPlaybackEntity(1L, "b", 50_000L, 120_000L, false, 20L)
        val actual = playingSnapshot("b", positionMs = 50_000L).copy(durationMs = 120_000L)
        state.update(actual)
        state.confirm(history)
        now = 30L
        state.update(actual.copy(isPlaying = false, playbackState = Player.STATE_BUFFERING, positionMs = 0L))
        now = 40L
        val otherSource = playingSnapshot("c", playlistId = 2L)
        state.update(otherSource)

        assertEquals(50_000L, state.observedPlayback("b", history)?.positionMs)
        assertEquals(20L, state.observedPlayback("b", history)?.updatedAt)
        assertNull(resolvePlaylistHeroLivePosition(otherSource, 1L, "b"))
    }

    @Test
    fun readyButSuppressedPlaybackDoesNotConfirmAResetPosition() {
        var now = 20L
        val state = PlaylistHeroPlaybackState(1L) { now }
        val history = PlaylistPlaybackEntity(1L, "b", 50_000L, 120_000L, false, 20L)
        val actual = playingSnapshot("b", positionMs = 50_000L).copy(durationMs = 120_000L)
        state.update(actual)
        state.confirm(history)
        now = 30L
        val suppressed = actual.copy(isPlaying = false, playWhenReady = true, positionMs = 0L)
        state.update(suppressed)
        assertEquals(50_000L, state.observedPlayback("b", history)?.positionMs)
        state.update(suppressed.copy(playWhenReady = false))
        assertEquals(50_000L, state.observedPlayback("b", history)?.positionMs)
        assertEquals(20L, state.observedPlayback("b", history)?.updatedAt)
    }

    @Test
    fun startedTrackSurvivesStaleHistoryAndLeavingItsPlaybackUntilTheDatabaseCatchesUp() {
        val state = PlaylistHeroPlaybackState(1L) { 11L }
        val oldHistory = PlaylistPlaybackEntity(1L, "a", 1_000L, 12_000L, false, 10L)
        state.update(playingSnapshot("b", positionMs = 2_000L))
        state.confirm(oldHistory)
        state.update(playingSnapshot("b", positionMs = 3_000L).copy(isPlaying = false, playWhenReady = false))
        state.update(playingSnapshot("c", playlistId = 2L))

        assertEquals("b", resolvePlaylistHeroItem(sampleItems(), oldHistory, state.pendingMediaId)?.mediaId)
        assertEquals(3_000L, state.observedPlayback("b")?.positionMs)
        assertEquals("a", resolvePlaylistHeroItem(sampleItems().filterNot { it.mediaId == "b" }, oldHistory, state.pendingMediaId)?.mediaId)

        state.confirm(oldHistory.copy(mediaId = "b", updatedAt = 12L))
        assertNull(state.pendingMediaId)
        state.update(playingSnapshot("b", positionMs = 4_000L))
        assertNull(state.pendingMediaId)
        val newestHistory = oldHistory.copy(mediaId = "c", updatedAt = Long.MAX_VALUE)
        state.activate(newestHistory, playingSnapshot("c").copy(isPlaying = false, playWhenReady = false))
        assertEquals("c", resolvePlaylistHeroItem(sampleItems(), newestHistory, state.pendingMediaId)?.mediaId)
    }

    @Test
    fun returningPausedBeforeTheDatabaseCatchesUpKeepsTheStartedTrack() {
        val state = PlaylistHeroPlaybackState(1L) { 20L }
        val history = PlaylistPlaybackEntity(1L, "a", 1_000L, 12_000L, false, 10L)
        state.update(playingSnapshot("b", positionMs = 2_000L))
        val paused = playingSnapshot("b", positionMs = 3_000L).copy(isPlaying = false, playWhenReady = false)
        state.update(paused)

        state.activate(history.copy(updatedAt = 15L), paused)
        state.update(paused)

        assertEquals("b", state.pendingMediaId)
        assertEquals("b", resolvePlaylistHeroItem(sampleItems(), history, state.pendingMediaId)?.mediaId)
        assertEquals(3_000L, state.observedPlayback("b", history)?.positionMs)
    }

    @Test
    fun oldHistoryForTheSameTrackDoesNotConfirmItsNewPlaybackBeforeADelayedDifferentTrack() {
        var now = 20L
        val state = PlaylistHeroPlaybackState(1L) { now }
        val oldB = PlaylistPlaybackEntity(1L, "b", 1_000L, 12_000L, false, 10L)
        state.activate(oldB, playingSnapshot("a", positionMs = 2_000L))
        now = 30L
        state.update(playingSnapshot("b", positionMs = 3_000L))

        state.confirm(oldB)

        assertEquals("b", state.pendingMediaId)
        now = 40L
        val pausedB = playingSnapshot("b", positionMs = 4_000L).copy(isPlaying = false, playWhenReady = false)
        state.update(pausedB)
        val delayedA = oldB.copy(mediaId = "a", positionMs = 2_000L, updatedAt = 20L)
        state.confirm(delayedA)
        assertEquals("b", resolvePlaylistHeroItem(sampleItems(), delayedA, state.pendingMediaId)?.mediaId)
        assertEquals(4_000L, state.observedPlayback("b", delayedA)?.positionMs)

        state.activate(delayedA, pausedB)
        assertEquals("b", state.pendingMediaId)
        val confirmedB = oldB.copy(positionMs = 4_000L, updatedAt = 40L)
        state.confirm(confirmedB)
        assertNull(state.pendingMediaId)
        assertEquals("b", resolvePlaylistHeroItem(sampleItems(), confirmedB, state.pendingMediaId)?.mediaId)
    }

    @Test
    fun newerPlaybackRecordedWhileOffscreenReplacesAnUnconfirmedOldSelection() {
        val state = PlaylistHeroPlaybackState(1L) { 20L }
        state.update(playingSnapshot("b", positionMs = 2_000L))
        val newerHistory = PlaylistPlaybackEntity(1L, "c", 5_000L, 12_000L, false, 40L)

        state.activate(newerHistory, playingSnapshot("c", positionMs = 5_000L).copy(isPlaying = false, playWhenReady = false))

        assertNull(state.pendingMediaId)
        assertNull(state.observedPlayback("b", newerHistory))
        assertEquals("c", resolvePlaylistHeroItem(sampleItems(), newerHistory, state.pendingMediaId)?.mediaId)
    }

    @Test
    fun newerPersistedProgressIsNotCoveredByThePreviousObservationOrByFallback() {
        val state = PlaylistHeroPlaybackState(1L) { 20L }
        state.update(playingSnapshot("b", positionMs = 2_000L))
        state.confirm(PlaylistPlaybackEntity(1L, "b", 2_000L, 12_000L, false, 20L))
        val newerHistory = PlaylistPlaybackEntity(1L, "b", 8_000L, 12_000L, false, 40L)

        state.activate(newerHistory, playingSnapshot("c", playlistId = 2L).copy(isPlaying = false, playWhenReady = false))

        assertNull(state.observedPlayback("b", newerHistory))
        assertNull(state.observedPosition("b", newerHistory))
        assertNull(state.observedPlayback("b", null))
        assertEquals("b", resolvePlaylistHeroItem(sampleItems(), newerHistory, state.pendingMediaId)?.mediaId)
    }

    @Test
    fun actualPlaybackOnReturnWinsEvenWhenItHasTheSameIdAsAnEarlierConfirmedTrack() {
        var now = 20L
        val state = PlaylistHeroPlaybackState(1L) { now }
        state.update(playingSnapshot("b", positionMs = 2_000L))
        state.confirm(PlaylistPlaybackEntity(1L, "b", 2_000L, 12_000L, false, 20L))
        assertNull(state.pendingMediaId)
        val offscreenHistory = PlaylistPlaybackEntity(1L, "c", 5_000L, 12_000L, false, 40L)
        now = 60L

        state.activate(offscreenHistory, playingSnapshot("b", positionMs = 3_000L))

        assertEquals("b", state.pendingMediaId)
        assertEquals(3_000L, state.observedPlayback("b", offscreenHistory)?.positionMs)
        state.update(playingSnapshot("b", positionMs = 4_000L).copy(isPlaying = false, playWhenReady = false))
        state.confirm(offscreenHistory)
        assertEquals("b", resolvePlaylistHeroItem(sampleItems(), offscreenHistory, state.pendingMediaId)?.mediaId)
    }

    @Test
    fun reactivatedHeroKeepsTheActuallyPlayingTrackWhileTheNewRecordIsStillPending() {
        val active = mutableStateOf(true)
        val history = MutableStateFlow<PlaylistPlaybackEntity?>(
            PlaylistPlaybackEntity(1L, "b", 2_000L, 12_000L, false, 20L)
        )
        val live = MutableStateFlow(playingSnapshot("b", positionMs = 2_000L))
        var requestedStart = ""
        composeRule.setContent {
            AsmrPlayerTheme {
                PlaylistDetailContent(
                    windowSizeClass = testWindowSizeClass(),
                    isDataActive = active.value,
                    title = "晚安时刻",
                    items = sampleItems(),
                    playbackHistory = history,
                    playback = live,
                    onPlayAll = { _, _ -> },
                    onResumeAll = { _, start, _ -> requestedStart = start.mediaId },
                    onRemoveItem = {},
                    onMoveItemToTop = {},
                    onMoveItemToBottom = {},
                    onSaveManualOrder = {},
                )
            }
        }
        composeRule.runOnIdle { active.value = false }
        composeRule.runOnIdle {
            history.value = PlaylistPlaybackEntity(1L, "c", 5_000L, 12_000L, false, 40L)
            live.value = playingSnapshot("b", positionMs = 3_000L)
        }
        composeRule.runOnIdle { active.value = true }
        composeRule.onNodeWithTag(PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG).performClick()
        assertEquals("b", requestedStart)
        composeRule.runOnIdle {
            live.value = playingSnapshot("b", positionMs = 4_000L).copy(isPlaying = false, playWhenReady = false)
        }
        composeRule.onNodeWithTag(PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG).performClick()
        assertEquals("b", requestedStart)
    }

    @Test
    fun reorderUsesMediaKeysWithHeroAndOtherLeadingItems() {
        val items = mutableStateListOf<PlaylistItemWithSubtitles>().apply { addAll(sampleItems()) }
        items.movePlaylistItems(ItemPosition(6, "c"), ItemPosition(4, "a"))
        assertEquals(listOf("c", "a", "b"), items.map { it.mediaId })
        items.movePlaylistItems(ItemPosition(0, "__playlist_detail_hero__"), ItemPosition(1, "a"))
        assertEquals(listOf("c", "a", "b"), items.map { it.mediaId })
    }

    private fun sampleItems(): List<PlaylistItemWithSubtitles> {
        return listOf(
            playlistItem(mediaId = "a", title = "Track A"),
            playlistItem(mediaId = "b", title = "Track B"),
            playlistItem(mediaId = "c", title = "Track C")
        )
    }

    private fun assertHeroTitle(title: String) {
        composeRule.onNode(
            hasText(title) and hasAnyAncestor(hasTestTag(PLAYLIST_TRACK_HERO_TAG)),
            useUnmergedTree = true,
        ).assertExists()
    }

    private fun playingSnapshot(mediaId: String, playlistId: Long = 1L, positionMs: Long = 0L) = PlaybackSnapshot(
        isConnected = true,
        isPlaying = true,
        playWhenReady = true,
        playbackState = Player.STATE_READY,
        currentMediaItem = MediaItem.Builder().setMediaId(mediaId).build().withSourcePlaylist(playlistId),
        positionMs = positionMs,
        durationMs = 12_000L,
    )

    private fun playlistItem(mediaId: String, title: String): PlaylistItemWithSubtitles {
        return PlaylistItemWithSubtitles(
            playlistId = 1L,
            mediaId = mediaId,
            title = title,
            artist = "Artist",
            albumTitle = "Album",
            albumCv = "CV Alice",
            uri = "file:///$mediaId.mp3",
            duration = 12.0,
            artworkUri = "",
            playbackArtworkUri = "",
            albumId = 1L,
            trackId = 2L,
            rjCode = "RJ123456",
            albumWorkId = "WORK123",
            trackGroup = "Disc 1",
            lyricsRelativePathNoExt = "Disc 1/$mediaId",
            mimeType = "audio/mpeg",
            isVideo = false,
            itemOrder = 0,
            hasSubtitles = false
        )
    }
}
