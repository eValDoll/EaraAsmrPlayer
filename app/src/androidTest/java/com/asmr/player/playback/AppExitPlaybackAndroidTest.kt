package com.asmr.player.playback

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.asmr.player.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AppExitPlaybackAndroidTest {
    @Test
    fun doubleBackAndTaskRemovalStopSessionWithoutClearingRestoredQueue() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val stateStore = PlaybackStateStore(context)
        val audioFile = createSilentWav(context)
        val mediaId = audioFile.absolutePath
        val initialPositionMs = 2_000L
        PlaybackConnectionLifecycle.markAppOpened()
        kotlinx.coroutines.runBlocking {
            stateStore.save(
                PersistedPlaybackStateV2(
                    queue = listOf(
                        PersistedPlaybackQueueItem(
                            mediaId = mediaId,
                            uri = Uri.fromFile(audioFile).toString(),
                            mimeType = "audio/wav",
                            title = "退出恢复测试音频",
                            artist = "Eara Test",
                            albumTitle = "退出恢复测试",
                            artworkUri = null,
                            albumId = null,
                            trackId = null,
                            rjCode = null
                        )
                    ),
                    currentIndex = 0,
                    positionMs = initialPositionMs,
                    playWhenReady = false,
                    repeatMode = 0,
                    shuffleEnabled = false,
                    speed = 1f,
                    pitch = 1f,
                    savedAtEpochMs = System.currentTimeMillis()
                )
            )
        }

        var firstController: MediaController? = null
        var restoredController: MediaController? = null
        var finalController: MediaController? = null
        var reopenedScenario: ActivityScenario<MainActivity>? = null
        var finalScenario: ActivityScenario<MainActivity>? = null
        val initialScenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val controller = connectController(context)
            firstController = controller
            waitUntil("初次启动未恢复测试队列") {
                onMainThread { controller.mediaItemCount == 1 }
            }
            onMainThread {
                controller.seekTo(initialPositionMs)
                controller.play()
            }
            waitUntil("测试音频未开始播放") {
                onMainThread { controller.isPlaying }
            }
            waitUntil("播放通知未出现") {
                activeAppNotifications(context) > 0
            }

            initialScenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
                activity.onBackPressedDispatcher.onBackPressed()
            }
            waitUntil("双击返回后媒体会话仍未断开") {
                onMainThread { !controller.isConnected }
            }
            waitUntil("双击返回后播放通知仍存在") {
                activeAppNotifications(context) == 0
            }

            val savedAfterExit = kotlinx.coroutines.runBlocking { stateStore.load() }
            requireNotNull(savedAfterExit)
            assertEquals(listOf(mediaId), savedAfterExit.queue.map { it.mediaId })
            assertTrue(savedAfterExit.positionMs >= initialPositionMs)
            assertFalse(savedAfterExit.playWhenReady)

            reopenedScenario = ActivityScenario.launch(MainActivity::class.java)
            val reopenedController = connectController(context)
            restoredController = reopenedController
            waitUntil("重新打开后未恢复播放队列") {
                onMainThread {
                    reopenedController.mediaItemCount == 1 &&
                        reopenedController.currentMediaItem?.mediaId == mediaId
                }
            }
            val restoredPosition = onMainThread { reopenedController.currentPosition }
            assertTrue(restoredPosition >= initialPositionMs)
            assertFalse(onMainThread { reopenedController.playWhenReady })

            onMainThread { reopenedController.play() }
            waitUntil("划掉任务前测试音频未开始播放") {
                onMainThread { reopenedController.isPlaying }
            }
            waitUntil("划掉任务前播放通知未出现") {
                activeAppNotifications(context) > 0
            }
            onMainThread {
                context.getSystemService(ActivityManager::class.java)
                    .appTasks
                    .first()
                    .finishAndRemoveTask()
            }
            waitUntil("划掉最近任务后媒体会话仍未断开") {
                onMainThread { !reopenedController.isConnected }
            }
            waitUntil("划掉最近任务后播放通知仍存在") {
                activeAppNotifications(context) == 0
            }
            val savedAfterTaskRemoval = kotlinx.coroutines.runBlocking { stateStore.load() }
            requireNotNull(savedAfterTaskRemoval)
            assertEquals(listOf(mediaId), savedAfterTaskRemoval.queue.map { it.mediaId })
            assertFalse(savedAfterTaskRemoval.playWhenReady)

            finalScenario = ActivityScenario.launch(MainActivity::class.java)
            val controllerAfterTaskRemoval = connectController(context)
            finalController = controllerAfterTaskRemoval
            waitUntil("划掉最近任务并重开后未恢复播放队列") {
                onMainThread {
                    controllerAfterTaskRemoval.mediaItemCount == 1 &&
                        controllerAfterTaskRemoval.currentMediaItem?.mediaId == mediaId
                }
            }
            assertTrue(onMainThread { controllerAfterTaskRemoval.currentPosition } >= initialPositionMs)
            assertFalse(onMainThread { controllerAfterTaskRemoval.playWhenReady })
        } finally {
            finalController?.let { controller -> onMainThread { controller.release() } }
            restoredController?.let { controller -> onMainThread { controller.release() } }
            firstController?.let { controller ->
                if (onMainThread { controller.isConnected }) {
                    onMainThread { controller.release() }
                }
            }
            finalScenario?.close()
            reopenedScenario?.close()
            initialScenario.close()
            kotlinx.coroutines.runBlocking { stateStore.clear() }
            audioFile.delete()
            PlaybackConnectionLifecycle.markAppOpened()
        }
    }

    private fun connectController(context: Context): MediaController {
        val disconnected = AtomicBoolean(false)
        val token = SessionToken(context, ComponentName(context, com.asmr.player.service.PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setApplicationLooper(Looper.getMainLooper())
            .setListener(
                object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        disconnected.set(true)
                    }
                }
            )
            .buildAsync()
        return future.get(15, TimeUnit.SECONDS).also { controller ->
            check(!disconnected.get() && onMainThread { controller.isConnected })
        }
    }

    private fun activeAppNotifications(context: Context): Int {
        return context.getSystemService(NotificationManager::class.java)
            .activeNotifications
            .count { it.packageName == context.packageName }
    }

    private fun <T> onMainThread(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result.set(runCatching(block))
        }
        return result.get().getOrThrow()
    }

    private fun waitUntil(message: String, timeoutMs: Long = 15_000L, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (runCatching(condition).getOrDefault(false)) return
            Thread.sleep(100L)
        }
        assertTrue(message, runCatching(condition).getOrDefault(false))
    }

    private fun createSilentWav(context: Context): File {
        val sampleRate = 8_000
        val durationSeconds = 20
        val channelCount = 1
        val bitsPerSample = 16
        val dataSize = sampleRate * durationSeconds * channelCount * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(channelCount.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channelCount * bitsPerSample / 8)
            putShort((channelCount * bitsPerSample / 8).toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }
        return File(context.filesDir, "app-exit-playback-test.wav").also { file ->
            FileOutputStream(file).use { output ->
                output.write(header.array())
                val silence = ByteArray(8_192)
                var remaining = dataSize
                while (remaining > 0) {
                    val count = minOf(remaining, silence.size)
                    output.write(silence, 0, count)
                    remaining -= count
                }
            }
        }
    }
}
