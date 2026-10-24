package com.asmr.player.ui.playlists

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.asmr.player.data.local.db.entities.PlaylistItemWithSubtitles
import com.asmr.player.data.local.db.entities.PlaylistPlaybackEntity
import com.asmr.player.ui.createEaraComposeRule
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w500dp-h900dp-mdpi")
class PlaylistTrackHeroTest {
    @get:Rule
    val compose = createEaraComposeRule()

    @Test
    fun playButton_followsHistoryPlayingAndCompletedStateAndKeepsClickAction() {
        val playback = mutableStateOf<PlaylistPlaybackEntity?>(null)
        val playing = mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            AsmrPlayerTheme(mode = ThemeMode.Light) {
                PlaylistTrackHero(
                    item = sampleItem(),
                    playback = playback.value,
                    isPlaying = playing.value,
                    onPlay = { clicks++ },
                )
            }
        }

        val button = compose.onNodeWithTag(PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG)
        button.assertContentDescriptionEquals("播放").performClick()
        compose.runOnIdle { playback.value = samplePlayback() }
        button.assertContentDescriptionEquals("播放").performClick()
        compose.runOnIdle { playing.value = true }
        button.assertContentDescriptionEquals("暂停").performClick()
        compose.runOnIdle {
            playing.value = false
            playback.value = samplePlayback().copy(isCompleted = true, positionMs = 20_000L)
        }
        button.assertContentDescriptionEquals("播放").performClick()
        button.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        listOf("播放", "继续播放", "暂停").forEach {
            compose.onAllNodesWithText(it).assertCountEquals(0)
        }
        compose.runOnIdle { assertEquals(4, clicks) }
    }

    @Test
    fun noHistoryAndUnknownDuration_doNotInventProgress() {
        val item = mutableStateOf(sampleItem())
        val playback = mutableStateOf<PlaylistPlaybackEntity?>(null)
        compose.setContent {
            AsmrPlayerTheme(mode = ThemeMode.Dark) {
                PlaylistTrackHero(
                    item = item.value,
                    playback = playback.value,
                    isPlaying = false,
                    onPlay = {},
                )
            }
        }

        compose.onAllNodes(progressMatcher()).assertCountEquals(0)
        compose.onNodeWithTag(PLAYLIST_TRACK_HERO_DURATION_TAG).assertTextEquals("00:40").assertIsDisplayed()
        compose.onNodeWithTag(PLAYLIST_TRACK_HERO_ELAPSED_TAG).assertTextEquals("")
        compose.runOnIdle {
            item.value = item.value.copy(duration = 0.0)
            playback.value = samplePlayback().copy(durationMs = 0L)
        }
        compose.onAllNodes(progressMatcher()).assertCountEquals(0)
        compose.onNodeWithTag(PLAYLIST_TRACK_HERO_ELAPSED_TAG).assertTextEquals("00:05").assertIsDisplayed()
        compose.onNodeWithTag(PLAYLIST_TRACK_HERO_DURATION_TAG).assertTextEquals("")
        compose.onAllNodesWithText("/", substring = true).assertCountEquals(0)
        compose.runOnIdle { playback.value = null }
        compose.onNodeWithTag(PLAYLIST_TRACK_HERO_ELAPSED_TAG).assertTextEquals("")
        compose.onNodeWithTag(PLAYLIST_TRACK_HERO_DURATION_TAG).assertTextEquals("")
        compose.onAllNodesWithText("00:00", substring = true).assertCountEquals(0)
        compose.onAllNodes(progressMatcher()).assertCountEquals(0)
    }

    @Test
    fun savedAndLivePositions_updateReadOnlyProgressAndTime() {
        val position = mutableStateOf(5_000L)
        compose.setContent {
            AsmrPlayerTheme {
                PlaylistTrackHero(
                    item = sampleItem(),
                    playback = samplePlayback(),
                    isPlaying = true,
                    onPlay = {},
                    positionMs = { position.value },
                    durationMs = 20_000L,
                )
            }
        }

        compose.onNodeWithTag(PLAYLIST_TRACK_HERO_ELAPSED_TAG).assertTextEquals("00:05").assertIsDisplayed()
        compose.onNodeWithTag(PLAYLIST_TRACK_HERO_DURATION_TAG).assertTextEquals("00:20").assertIsDisplayed()
        compose.onNode(progressMatcher()).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.25f, 0f..1f),
            )
        )
        compose.runOnIdle { position.value = 12_000L }
        compose.onNodeWithTag(PLAYLIST_TRACK_HERO_ELAPSED_TAG).assertTextEquals("00:12").assertIsDisplayed()
        compose.onNodeWithTag(PLAYLIST_TRACK_HERO_DURATION_TAG).assertTextEquals("00:20")
        compose.onNode(progressMatcher()).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.6f, 0f..1f),
            )
        )
        compose.onNode(progressMatcher()).assert(
            SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress)
        )
    }

    @Test
    fun titleHistoryAndProgressChanges_keepIconBoundsAndHeroHeightStable() {
        val item = mutableStateOf(sampleItem())
        val playback = mutableStateOf<PlaylistPlaybackEntity?>(null)
        val playing = mutableStateOf(false)
        compose.setContent {
            AsmrPlayerTheme(mode = ThemeMode.Light) {
                Box(modifier = Modifier.width(360.dp)) {
                    PlaylistTrackHero(
                        item = item.value,
                        playback = playback.value,
                        isPlaying = playing.value,
                        onPlay = {},
                    )
                }
            }
        }

        val button = compose.onNodeWithTag(PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG)
        val baselineButton = button.getUnclippedBoundsInRoot()
        val baselineHero = compose.onNodeWithTag(PLAYLIST_TRACK_HERO_TAG).getUnclippedBoundsInRoot()
        assertEquals(48.dp, baselineButton.right - baselineButton.left)
        assertEquals(48.dp, baselineButton.bottom - baselineButton.top)

        fun assertStableBounds() {
            assertEquals("标题和播放状态变化不能移动图标", baselineButton, button.getUnclippedBoundsInRoot())
            val hero = compose.onNodeWithTag(PLAYLIST_TRACK_HERO_TAG).getUnclippedBoundsInRoot()
            assertEquals("标题和进度有无不能改变 Hero 高度", baselineHero.bottom - baselineHero.top, hero.bottom - hero.top)
            button.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        }

        compose.runOnIdle { playback.value = samplePlayback() }
        assertStableBounds()
        compose.runOnIdle { playing.value = true }
        button.assertContentDescriptionEquals("暂停")
        assertStableBounds()
        compose.runOnIdle {
            item.value = item.value.copy(title = "很长的音轨标题，在安静的夜晚陪伴你直到入睡之前，继续温柔地低语和掏耳朵")
            playing.value = false
            playback.value = samplePlayback().copy(isCompleted = true, positionMs = 20_000L)
        }
        button.assertContentDescriptionEquals("播放")
        assertStableBounds()
        compose.runOnIdle {
            item.value = item.value.copy(duration = 0.0, artist = "", albumCv = "")
            playback.value = samplePlayback().copy(durationMs = 0L)
        }
        compose.onAllNodes(progressMatcher()).assertCountEquals(0)
        assertStableBounds()
        compose.runOnIdle { playback.value = null }
        assertStableBounds()
        compose.runOnIdle { item.value = sampleItem() }
        assertStableBounds()
    }

    @Test
    fun lightPortraitAndDarkShortScreenLargeText_keepContentWithinHeroWithoutOverlap() {
        val largeShortScreen = mutableStateOf(false)
        val title = "很长的音轨标题，在安静的夜晚陪伴你直到入睡之前，继续温柔地低语和掏耳朵"
        compose.setContent {
            val shortScreen = largeShortScreen.value
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = if (shortScreen) 280 else 360
                screenHeightDp = if (shortScreen) 400 else 800
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(1f, if (shortScreen) 1.8f else 1f),
            ) {
                AsmrPlayerTheme(mode = if (shortScreen) ThemeMode.Dark else ThemeMode.Light) {
                    Box(modifier = Modifier.width(configuration.screenWidthDp.dp)) {
                        PlaylistTrackHero(
                            item = sampleItem().copy(title = title),
                            playback = samplePlayback(),
                            isPlaying = false,
                            onPlay = {},
                        )
                    }
                }
            }
        }

        assertContentFits(title)
        val portraitBounds = compose.onNodeWithTag(PLAYLIST_TRACK_HERO_TAG).getUnclippedBoundsInRoot()
        val portraitHeight = portraitBounds.bottom - portraitBounds.top
        assertTrue("竖屏 Hero 应保留大图空间", portraitHeight >= 292.dp)
        compose.runOnIdle { largeShortScreen.value = true }
        assertContentFits(title)
        compose.onNodeWithTag(PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG).performClick()
    }

    private fun assertContentFits(title: String) {
        val hero = compose.onNodeWithTag(PLAYLIST_TRACK_HERO_TAG).getUnclippedBoundsInRoot()
        val titleBounds = compose.onNodeWithText(title).assertIsDisplayed().getUnclippedBoundsInRoot()
        val artist = compose.onNodeWithText("声优甲").assertIsDisplayed().getUnclippedBoundsInRoot()
        val button = compose.onNodeWithTag(PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG)
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val timeline = compose.onNodeWithTag(PLAYLIST_TRACK_HERO_TIMELINE_TAG).getUnclippedBoundsInRoot()
        val elapsed = compose.onNodeWithTag(PLAYLIST_TRACK_HERO_ELAPSED_TAG)
            .assertTextEquals("00:05").assertIsDisplayed().getUnclippedBoundsInRoot()
        val duration = compose.onNodeWithTag(PLAYLIST_TRACK_HERO_DURATION_TAG)
            .assertTextEquals("00:20").assertIsDisplayed().getUnclippedBoundsInRoot()
        val progress = compose.onNode(progressMatcher()).getUnclippedBoundsInRoot()

        assertTrue("双行标题不能覆盖声优", titleBounds.bottom <= artist.top)
        assertTrue("声优不能覆盖播放操作", artist.bottom <= button.top)
        assertTrue("进度和时间组必须在播放按钮左侧", timeline.right <= button.left)
        assertTrue("声优不能覆盖时间组", artist.bottom <= timeline.top)
        assertTrue("播放按钮必须位于 Hero 内", button.right <= hero.right && button.bottom <= hero.bottom)
        assertTrue("时间必须位于 Hero 内", elapsed.left >= hero.left && duration.right <= hero.right)
        assertTrue("进度必须位于时间上方", progress.bottom <= elapsed.top && progress.bottom <= duration.top)
        assertEquals("时间紧贴进度下方", 4.dp, elapsed.top - progress.bottom)
        assertEquals("已播放时间靠左", progress.left, elapsed.left)
        assertEquals("总时长靠右", progress.right, duration.right)
        assertEquals("两端时间须对齐", elapsed.top, duration.top)
        assertTrue("两端时间不能重叠", elapsed.right <= duration.left)
        assertEquals("播放图标触控宽度固定", 48.dp, button.right - button.left)
        assertEquals("播放图标触控高度固定", 48.dp, button.bottom - button.top)
        compose.onNode(progressMatcher()).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress))
    }

    private fun progressMatcher() = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun samplePlayback() = PlaylistPlaybackEntity(
        playlistId = 1L,
        mediaId = "track",
        positionMs = 5_000L,
        durationMs = 20_000L,
    )

    private fun sampleItem() = PlaylistItemWithSubtitles(
        playlistId = 1L,
        mediaId = "track",
        title = "晚安音轨",
        albumCv = "声优甲",
        uri = "file:///track.mp3",
        duration = 40.0,
        hasSubtitles = false,
    )
}
