package com.asmr.player.ui.common

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.cache.CacheConfig
import com.asmr.player.cache.CacheKeyFactory
import com.asmr.player.cache.CacheStats
import com.asmr.player.cache.DiskCache
import com.asmr.player.cache.ImageCacheEntryPoint
import com.asmr.player.cache.ImageCacheManager
import com.asmr.player.cache.ImageLoaderFacade
import com.asmr.player.cache.MemoryCache
import com.asmr.player.ui.createEaraComposeRule
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBitmapFactory

class AsmrAsyncImageTestApplication : Application(), GeneratedComponentManager<Any> {
    lateinit var manager: ImageCacheManager

    private val component = object : ImageCacheEntryPoint, GeneratedComponent {
        override fun imageCacheManager(): ImageCacheManager = manager
    }

    override fun generatedComponent(): Any = component
}

@RunWith(RobolectricTestRunner::class)
@Config(application = AsmrAsyncImageTestApplication::class, sdk = [28])
class AsmrAsyncImageTest {
    @get:Rule val compose = createEaraComposeRule()
    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var app: AsmrAsyncImageTestApplication
    private lateinit var memory: MemoryCache
    private lateinit var stats: CacheStats
    private lateinit var http: OkHttpClient
    private val failure = FailingImageInterceptor()
    private val metadataRelease = CountDownLatch(1)
    private val requestedSize = mutableStateOf(RequestSize)
    private val contentPresent = mutableStateOf(true)
    private val requestJob = SupervisorJob()
    private val requestScope = CoroutineScope(requestJob + Dispatchers.IO)
    private var currentPainter: BitmapPainter? = null
    private var painterUpdates = 0

    @Before
    fun setUp() {
        // Legacy shadow 默认会把任意坏数据解码成 100×100 位图，掩盖真实的解码失败路径。
        ShadowBitmapFactory.setAllowInvalidImageData(false)
        app = ApplicationProvider.getApplicationContext()
        memory = MemoryCache(1_048_576)
        stats = CacheStats()
        http = OkHttpClient.Builder().addInterceptor(failure).build()
        app.manager = ImageCacheManager(
            appContext = app,
            config = CacheConfig(cacheVersion = CacheVersion, diskMaxSizeBytes = 0L),
            memoryCache = memory,
            diskCache = DiskCache(temporaryFolder.newFolder("images"), maxSizeBytes = 0L, ttlMs = 0L),
            loaderFacade = ImageLoaderFacade(app, http, Dispatchers.IO),
            stats = stats,
            decodeDispatcher = Dispatchers.IO,
        )
    }

    @After
    fun tearDown() {
        metadataRelease.countDown()
        failure.release.countDown()
        try {
            compose.runOnIdle { contentPresent.value = false }
            compose.waitForIdle()
        } finally {
            requestScope.cancel()
            try {
                compose.waitUntil(2_000L) {
                    shadowOf(Looper.getMainLooper()).idleFor(16L, TimeUnit.MILLISECONDS)
                    requestJob.isCompleted
                }
            } finally {
                http.dispatcher.cancelAll()
                http.dispatcher.executorService.shutdown()
                if (!http.dispatcher.executorService.awaitTermination(2L, TimeUnit.SECONDS)) {
                    http.dispatcher.executorService.shutdownNow()
                }
                http.connectionPool.evictAll()
            }
        }
    }

    @Test
    fun deletedLocalSeed_isDiscardedAndFailedLoadShowsTheErrorPlaceholder() {
        checkInvalidLocalSeed(delete = true, retain = false, original = false)
    }

    @Test
    fun corruptLocalReplacement_cannotBeKeptByRetainOrOriginalSizeLoading() {
        checkInvalidLocalSeed(delete = false, retain = true, original = true)
    }

    @Test
    fun validRemoteCrossSizeSeed_survivesEvictionAndFailedLoad() {
        seedMemory(RemoteCover, PreviewSize)
        showImage(RemoteCover)
        await { failure.started.count == 0L && currentPainter != null }
        val previewPainter = compose.runOnIdle {
            assertNotNull(currentPainter)
            memory.clear()
            currentPainter
        }

        finishFailedRequest()

        compose.onNodeWithTag(ErrorTag).assertDoesNotExist()
        compose.runOnIdle { assertSame(previewPainter, currentPainter) }
    }

    @Test
    fun loadedPainter_isRetainedWhenAResizeRequestFails() {
        seedMemory(RemoteCover, RequestSize)
        val previousHits = stats.snapshot().memoryHits
        showImage(RemoteCover, retain = true)
        await { currentPainter != null && painterUpdates >= 2 && stats.snapshot().memoryHits > previousHits }
        val loadedPainter = compose.runOnIdle { currentPainter }

        compose.runOnIdle { requestedSize.value = IntSize(32, 32) }
        await { failure.started.count == 0L }
        finishFailedRequest()

        compose.onNodeWithTag(ErrorTag).assertDoesNotExist()
        compose.runOnIdle { assertSame(loadedPainter, currentPainter) }
    }

    @Test
    fun originalSizePainter_doesNotReloadWhenTheDisplaySizeChanges() {
        seedMemory(RemoteCover, null)
        val previousHits = stats.snapshot().memoryHits
        showImage(RemoteCover, original = true)
        await { currentPainter != null && painterUpdates >= 2 && stats.snapshot().memoryHits > previousHits }
        val loadedPainter = compose.runOnIdle { currentPainter }

        compose.runOnIdle { requestedSize.value = IntSize(32, 32) }
        compose.waitForIdle()

        compose.runOnIdle { assertSame(loadedPainter, currentPainter) }
        assertEquals(0L, stats.snapshot().networkFetches)
        assertEquals(1L, failure.started.count)
    }

    private fun checkInvalidLocalSeed(delete: Boolean, retain: Boolean, original: Boolean) {
        val file = temporaryFolder.newFile("cover.png")
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            file.outputStream().use { assertTrue(compress(Bitmap.CompressFormat.PNG, 100, it)) }
            recycle()
        }
        seedMemory(file, PreviewSize)
        val oldModifiedAt = file.lastModified()
        if (delete) {
            assertTrue(file.delete())
        } else {
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(file.setLastModified(oldModifiedAt + 1_000L))
            assertNull("损坏替换文件必须无法解码，不能由 legacy shadow 伪造位图", BitmapFactory.decodeFile(file.absolutePath))
        }
        val metadataStarted = CountDownLatch(1)
        val checkedFile = object : File(file.absolutePath) {
            override fun length(): Long {
                assertNotSame("文件元数据不能在组合或主线程读取", Looper.getMainLooper(), Looper.myLooper())
                metadataStarted.countDown()
                check(metadataRelease.await(5L, TimeUnit.SECONDS))
                return super.length()
            }

            override fun lastModified(): Long {
                assertNotSame(Looper.getMainLooper(), Looper.myLooper())
                return super.lastModified()
            }
        }
        showImage(checkedFile, retain = retain, original = original)
        await { metadataStarted.count == 0L && currentPainter != null }
        compose.runOnIdle { assertNotNull(currentPainter) }

        metadataRelease.countDown()
        await { compose.onAllNodesWithTag(ErrorTag).fetchSemanticsNodes().size == 1 }

        compose.runOnIdle { assertNull(currentPainter) }
    }

    private fun showImage(model: Any, retain: Boolean = false, original: Boolean = false) {
        compose.setContent {
            if (contentPresent.value) {
                AsmrAsyncImage(
                    model = model,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    requestSize = requestedSize.value,
                    peekAnySizeForInitial = true,
                    retainPainterDuringReload = retain,
                    loadAtOriginalSize = original,
                    fadeIn = false,
                    loading = { Box(it.testTag(LoadingTag)) },
                    placeholder = { Box(it.testTag(ErrorTag)) },
                    onBitmapPainterState = { painter, _ ->
                        currentPainter = painter
                        painterUpdates++
                    },
                )
            }
        }
    }

    private fun seedMemory(model: Any, size: IntSize?) {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        memory.put(CacheKeyFactory.createKey(app, model, size, CacheVersion), bitmap)
        runBlocking { app.manager.loadImageFromCache(model, size) }
    }

    private fun finishFailedRequest() {
        val failed = CompletableFuture<Boolean>()
        val size = requestedSize.value
        requestScope.launch {
            try {
                app.manager.loadImage(RemoteCover, size)
                failed.complete(false)
            } catch (error: CancellationException) {
                failed.cancel(false)
                throw error
            } catch (_: Throwable) {
                failed.complete(true)
            }
        }
        failure.release.countDown()
        await { failed.isDone }
        assertTrue(failed.get(0L, TimeUnit.MILLISECONDS))
        compose.waitForIdle()
    }

    private fun await(condition: () -> Boolean) {
        try {
            compose.waitUntil(5_000L) {
                shadowOf(Looper.getMainLooper()).idleFor(16L, TimeUnit.MILLISECONDS)
                compose.runOnIdle { Snapshot.sendApplyNotifications() }
                condition()
            }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError(
                "图片状态等待超时: painter=$currentPainter, updates=$painterUpdates, cache=${stats.snapshot()}, " +
                    "errorNodes=${runCatching { compose.onAllNodesWithTag(ErrorTag).fetchSemanticsNodes().size }.getOrNull()}, " +
                    "loadingNodes=${runCatching { compose.onAllNodesWithTag(LoadingTag).fetchSemanticsNodes().size }.getOrNull()}",
                error,
            )
        }
    }

    private class FailingImageInterceptor : Interceptor {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun intercept(chain: Interceptor.Chain): Response {
            started.countDown()
            check(release.await(5L, TimeUnit.SECONDS))
            throw IOException("Controlled image failure")
        }
    }

    private companion object {
        const val CacheVersion = "async-image-preview-test"
        const val RemoteCover = "https://example.com/cover.png"
        const val ErrorTag = "image_error"
        const val LoadingTag = "image_loading"
        val PreviewSize = IntSize(8, 8)
        val RequestSize = IntSize(16, 16)
    }
}
