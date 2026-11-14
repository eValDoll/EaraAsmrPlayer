package com.asmr.player.playback

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = com.asmr.player.LocalizedTestApplication::class, sdk = [24, 34])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayModeTest {
    @Test
    fun existingSettingsKeepTheirMeaningAndUnknownValuesUseListLoop() {
        assertEquals(PlayMode.ListLoop, PlayMode.fromStorageValue(null))
        assertEquals(PlayMode.ListLoop, PlayMode.fromStorageValue(-1))
        assertEquals(PlayMode.ListLoop, PlayMode.fromStorageValue(99))
        assertEquals(PlayMode.ListLoop, PlayMode.fromStorageValue(0))
        assertEquals(PlayMode.SingleLoop, PlayMode.fromStorageValue(1))
        assertEquals(PlayMode.Shuffle, PlayMode.fromStorageValue(2))
        assertEquals(PlayMode.PauseAfterTrack, PlayMode.fromStorageValue(3))
    }

    @Test
    fun cycleIncludesPauseAfterTrackAndReturnsToListLoop() {
        var mode = PlayMode.ListLoop
        val visited = List(4) { mode.also { mode = mode.next() } }
        assertEquals(
            listOf(PlayMode.ListLoop, PlayMode.SingleLoop, PlayMode.Shuffle, PlayMode.PauseAfterTrack),
            visited
        )
        assertEquals(PlayMode.ListLoop, mode)
    }

    @Test
    fun applyingAndRestoringModesUpdatesTheRealPlaybackEngine() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        try {
            player.configureTrackEndPause()
            for (mode in PlayMode.entries + PlayMode.ListLoop) {
                mode.applyTo(player)
                assertEquals(mode, PlayMode.fromPlayer(player.repeatMode, player.shuffleModeEnabled))
                assertEquals(mode, PlayMode.fromStorageValue(mode.storageValue))
                assertEquals(mode == PlayMode.PauseAfterTrack, player.pauseAtEndOfMediaItems)
            }

            // 队列恢复直接还原 repeatMode，也必须启用或取消边界暂停。
            player.repeatMode = Player.REPEAT_MODE_OFF
            assertTrue(player.pauseAtEndOfMediaItems)
            player.repeatMode = Player.REPEAT_MODE_ONE
            assertFalse(player.pauseAtEndOfMediaItems)
            player.repeatMode = Player.REPEAT_MODE_ALL
            assertFalse(player.pauseAtEndOfMediaItems)
        } finally {
            player.release()
        }
    }
}
