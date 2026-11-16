package com.asmr.player.playback

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PauseAfterTrackAndroidTest {
    @Test
    fun trackBoundariesPauseInBackgroundAndWithScreenOffWithoutChangingTheQueue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val power = context.getSystemService(PowerManager::class.java)
        val player = onMain {
            ExoPlayer.Builder(context).build().also {
                it.setWakeMode(C.WAKE_MODE_LOCAL)
                it.configureTrackEndPause()
                it.volume = 0f
            }
        }
        val wasInteractive = power.isInteractive
        try {
            onMain {
                player.setMediaSources(List(3) {
                    SilenceMediaSource.Factory().setDurationUs(800_000L).createMediaSource()
                })
                PlayMode.PauseAfterTrack.applyTo(player)
                player.prepare()
                player.play()
            }
            waitUntil { onMain { !player.playWhenReady && player.currentPosition >= 750L } }
            assertEquals(0, onMain { player.currentMediaItemIndex })
            assertEquals(3, onMain { player.mediaItemCount })
            SystemClock.sleep(1_000L)
            assertEquals(0, onMain { player.currentMediaItemIndex })

            // 暂停后仍可手动选择下一首；最后一首也不能绕回队首。
            shell("input keyevent KEYCODE_SLEEP")
            waitUntil { !power.isInteractive }
            for (index in 1..2) {
                onMain {
                    player.seekToDefaultPosition(index)
                    player.play()
                }
                waitUntil { onMain { !player.playWhenReady && player.currentPosition >= 750L } }
                assertEquals(index, onMain { player.currentMediaItemIndex })
                assertFalse(power.isInteractive)
            }

            // 播放中退出该模式后，应正常自动续播下一首。
            onMain {
                player.seekToDefaultPosition(0)
                player.play()
                PlayMode.ListLoop.applyTo(player)
            }
            waitUntil { onMain { player.currentMediaItemIndex == 1 && player.isPlaying } }
            assertFalse(onMain { player.pauseAtEndOfMediaItems })
            assertTrue(onMain { player.playWhenReady })
        } finally {
            onMain { player.release() }
            if (wasInteractive) shell("input keyevent KEYCODE_WAKEUP")
        }
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result.set(runCatching(block))
        }
        return result.get().getOrThrow()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50L)
        }
        assertTrue("播放边界状态未在限时内到达", condition())
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }
}
