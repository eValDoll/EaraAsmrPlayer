package com.asmr.player.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.asmr.player.MainActivity
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.repository.TrackSliceRepository
import com.asmr.player.domain.model.Slice
import com.asmr.player.service.PlaybackService
import com.asmr.player.ui.player.PlayerViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class BackgroundSlicePlaybackAndroidTest {
    @Test
    fun slicesKeepLoopingSkippingAndEndingPreviewInBackgroundAndWithScreenOff() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val powerManager = context.getSystemService(PowerManager::class.java)
        val stateStore = PlaybackStateStore(context)
        val savedState = runBlocking { stateStore.load() }
        val repository = TrackSliceRepository(AppDatabaseProvider.get(context).trackSliceDao())
        val audioFile = createSilentWav(context)
        val firstId = "background-slice-test:first"
        val secondId = "background-slice-test:second"
        val slices = listOf(Slice(1L, 1_000L, 3_000L), Slice(2L, 8_000L, 10_000L))
        val items = listOf(firstId, secondId).map { id ->
            MediaItem.Builder().setMediaId(id).setUri(Uri.fromFile(audioFile))
                .setMimeType("audio/wav").build()
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var viewModel: PlayerViewModel? = null
        var sliceModeEnabled = false
        try {
            scenario.onActivity { viewModel = ViewModelProvider(it)[PlayerViewModel::class.java] }
            val model = requireNotNull(viewModel)
            waitUntil("播放器未完成队列恢复") {
                onMainThread {
                    model.playerOrNull() != null && model.playback.value.startupRestoreResolved
                }
            }
            val player = onMainThread { requireNotNull(model.playerOrNull()) }
            runBlocking {
                for (id in listOf(firstId, secondId)) {
                    repository.clearTrack(id)
                    for (slice in slices) repository.appendSlice(id, slice.startMs, slice.endMs)
                }
            }
            onMainThread {
                model.playMediaItems(items, 0)
                // 每次轮询最多推进 125ms，避免越过现有引擎的 200ms 结束判定窗口。
                player.playbackParameters = PlaybackParameters(0.5f)
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.shuffleModeEnabled = false
                model.toggleSliceMode()
                sliceModeEnabled = true
            }
            waitUntil("测试音频未开始播放") { onMainThread { player.isPlaying } }
            val seeks = mutableListOf<Long>()
            val listener = object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) seeks.add(newPosition.positionMs)
                }
            }
            onMainThread { player.addListener(listener) }

            // Home 保留播放服务；后续断言直接读取控制器，不能靠重新打开页面触发补跳。
            context.startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            SystemClock.sleep(1_000L)
            for (screenOff in listOf(false, true)) {
                if (screenOff) {
                    shell("input keyevent KEYCODE_SLEEP")
                    waitUntil("设备未熄屏") { !powerManager.isInteractive }
                    SystemClock.sleep(1_000L)
                }
                onMainThread {
                    player.repeatMode = Player.REPEAT_MODE_ONE
                    player.seekTo(0, slices.first().startMs)
                    seeks.clear()
                }
                waitUntil("后台切片未持续循环，screenOff=$screenOff", 25_000L) {
                    onMainThread {
                        seeks.count { it == slices[1].startMs } >= 2 &&
                            seeks.count { it == slices[0].startMs } >= 2
                    }
                }
                assertTrue(onMainThread { player.isPlaying })
                assertEquals(0, onMainThread { player.currentMediaItemIndex })

                onMainThread { player.repeatMode = Player.REPEAT_MODE_ALL }
                waitUntil("后台末切片未跳到下一曲，screenOff=$screenOff", 12_000L) {
                    onMainThread { player.currentMediaItem?.mediaId == secondId }
                }
                // 下一曲须重新加载其切片，不能依赖暂停的展示轮询更新音轨。
                onMainThread {
                    player.repeatMode = Player.REPEAT_MODE_ONE
                    seeks.clear()
                }
                waitUntil("后台换曲后切片未继续生效，screenOff=$screenOff", 12_000L) {
                    onMainThread { seeks.contains(slices[1].startMs) }
                }
                if (screenOff) assertFalse(powerManager.isInteractive)
            }

            onMainThread {
                model.toggleSliceMode()
                sliceModeEnabled = false
                model.playSlicePreview(Slice(3L, 12_000L, 14_000L))
            }
            waitUntil("熄屏预览未开始播放") { onMainThread { player.isPlaying } }
            waitUntil("熄屏预览到点后未暂停", 8_000L) {
                onMainThread { !player.playWhenReady }
            }
            assertTrue(onMainThread { player.currentPosition in 13_800L..14_500L })
            assertFalse(powerManager.isInteractive)

            // 预览完成后应清除预览状态，普通播放不应再次被暂停或拉回切片。
            onMainThread {
                player.seekTo(15_000L)
                player.play()
            }
            waitUntil("预览结束后普通播放仍受切片干预", 5_000L) {
                onMainThread { player.isPlaying && player.currentPosition > 16_000L }
            }
            onMainThread { player.removeListener(listener) }
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            onMainThread {
                viewModel?.let { model ->
                    model.setUserScrubbing(false)
                    if (sliceModeEnabled) model.toggleSliceMode()
                    model.playerOrNull()?.pause()
                    if (model.playerOrNull() != null) PlaybackService.requestShutdownForAppExit(context)
                }
            }
            waitUntil("测试结束后播放服务未停止") {
                onMainThread { viewModel?.playerOrNull() == null }
            }
            scenario.close()
            runBlocking {
                repository.clearTrack(firstId)
                repository.clearTrack(secondId)
                if (savedState != null) stateStore.save(savedState) else stateStore.clear()
            }
            audioFile.delete()
            PlaybackConnectionLifecycle.markAppOpened()
        }
    }

    private fun <T> onMainThread(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result.set(runCatching(block))
        }
        return result.get().getOrThrow()
    }

    private fun waitUntil(message: String, timeoutMs: Long = 15_000L, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100L)
        }
        assertTrue(message, condition())
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
    }

    private fun createSilentWav(context: Context): File {
        val sampleRate = 8_000
        val dataSize = sampleRate * 30 * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }
        return File(context.cacheDir, "background-slice-test.wav").also { file ->
            file.outputStream().use {
                it.write(header.array())
                it.write(ByteArray(dataSize))
            }
        }
    }
}
