package com.asmr.player.ui.playlists

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
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

class PlaylistHeroArtworkTestApplication : Application(), GeneratedComponentManager<Any> {
    lateinit var manager: ImageCacheManager

    private val component = object : ImageCacheEntryPoint, GeneratedComponent {
        override fun imageCacheManager(): ImageCacheManager = manager
    }

    override fun generatedComponent(): Any = component
}

@RunWith(RobolectricTestRunner::class)
@Config(application = PlaylistHeroArtworkTestApplication::class, sdk = [28])
class PlaylistHeroArtworkTest {
    @get:Rule val compose = createEaraComposeRule()
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val model = mutableStateOf(FirstCover)
    private val previewModel = mutableStateOf<String?>(null)
    private val active = mutableStateOf(true)
    private val revision = mutableStateOf(0)
    private val composed = mutableStateOf(true)
    private var contentStarted = false
    private var contentPresent = false
    private var composedModel: String? = null
    private var composedActive: Boolean? = null
    private lateinit var app: PlaylistHeroArtworkTestApplication
    private lateinit var memory: MemoryCache
    private lateinit var stats: CacheStats
    private lateinit var interceptor: ControlledArtworkInterceptor
    private lateinit var http: OkHttpClient
    private lateinit var artwork: PlaylistHeroArtworkState

    @Before
    fun prepareImagePipeline() {
        app = ApplicationProvider.getApplicationContext()
        memory = MemoryCache(1_048_576)
        stats = CacheStats()
        interceptor = ControlledArtworkInterceptor()
        http = OkHttpClient.Builder().addInterceptor(interceptor).build()
        app.manager = ImageCacheManager(
            appContext = app,
            config = CacheConfig(cacheVersion = CacheVersion, diskMaxSizeBytes = 0L),
            memoryCache = memory,
            diskCache = DiskCache(temporaryFolder.newFolder("images"), maxSizeBytes = 0L, ttlMs = 0L),
            loaderFacade = ImageLoaderFacade(app, http, Dispatchers.IO),
            stats = stats,
            decodeDispatcher = Dispatchers.IO,
        )
        seedMemory(FirstCover, Color.RED)
        compose.mainClock.autoAdvance = false
    }

    @After
    fun stopPendingImageCalls() {
        try {
            if (contentStarted) {
                compose.runOnIdle { composed.value = false }
                awaitInputCommit { !contentPresent }
                compose.waitForIdle()
            }
        } finally {
            if (::http.isInitialized) {
                http.dispatcher.cancelAll()
                if (::interceptor.isInitialized) interceptor.releaseAll()
                val executor = http.dispatcher.executorService
                executor.shutdown()
                try {
                    if (!executor.awaitTermination(2L, TimeUnit.SECONDS)) {
                        executor.shutdownNow()
                        assertTrue("封面测试 HTTP 线程应停止", executor.awaitTermination(2L, TimeUnit.SECONDS))
                    }
                } finally {
                    http.connectionPool.evictAll()
                }
            }
        }
    }

    @Test
    fun replacementKeepsOldCoverUntilReadyThenBlendsBeforeSettling() {
        val response = interceptor.hold(SecondCover, Color.BLUE)
        showArtworkState()
        finishTransition(FirstCover)

        changeModel(SecondCover)
        await { response.started.count == 0L }
        compose.runOnIdle {
            assertEquals(FirstCover, artwork.settledFrame?.model)
            assertNull(artwork.incomingFrame)
        }

        response.release.countDown()
        await { artwork.incomingFrame?.model == SecondCover }
        assertBlendingOver(FirstCover, SecondCover)
        finishTransition(SecondCover)
        assertEquals(1L, stats.snapshot().decodeCount)
    }

    @Test
    fun cachedThumbnailStartsTransitionWhileFullCoverIsBlockedThenUpgradesWithoutClearingIt() {
        val thumbnail = seedPreview(ThumbnailCover, Color.BLUE)
        val fullCover = interceptor.hold(SecondCover, Color.CYAN)
        showArtworkState()
        finishTransition(FirstCover)

        changeModel(SecondCover, preview = ThumbnailCover)
        await { artwork.incomingFrame?.model == SecondCover && artwork.incomingFrame?.isPreview == true }
        await { fullCover.started.count == 0L }
        assertBlendingOver(FirstCover, SecondCover)
        assertEquals(1L, fullCover.release.count)
        assertEquals(0L, stats.snapshot().decodeCount)
        finishTransition(SecondCover)
        val preview = artwork.settledFrame
        compose.runOnIdle {
            assertTrue(preview?.isPreview == true)
            assertSame(thumbnail, preview?.bitmap?.asAndroidBitmap())
        }

        fullCover.release.countDown()
        await { artwork.incomingFrame?.model == SecondCover && artwork.incomingFrame?.isPreview == false }
        compose.runOnIdle { assertSame(preview, artwork.settledFrame) }
        assertBlendingOver(SecondCover, SecondCover)
        finishTransition(SecondCover)
        compose.runOnIdle {
            assertTrue(artwork.settledFrame?.isPreview == false)
            assertEquals(RequestSize, artwork.settledFrame?.size)
        }
        assertEquals(1L, stats.snapshot().decodeCount)
    }

    @Test
    fun identicalUnderlyingBitmapAcrossPreviewAndFullCacheDoesNotStartAnotherFade() {
        val sharedBitmap = seedPreview(SecondCover, Color.BLUE)
        memory.put(CacheKeyFactory.createKey(app, SecondCover, RequestSize, CacheVersion), sharedBitmap)
        showArtworkState()
        finishTransition(FirstCover)

        changeModel(SecondCover)
        await { artwork.incomingFrame?.model == SecondCover }
        assertBlendingOver(FirstCover, SecondCover)
        finishTransition(SecondCover)
        await { artwork.settledFrame?.isPreview == false }
        val readsAfterFullCover = stats.snapshot().memoryHits
        repeat(3) {
            compose.runOnIdle { revision.value++ }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertNull(artwork.incomingFrame)
                assertSame(sharedBitmap, artwork.settledFrame?.bitmap?.asAndroidBitmap())
            }
        }
        assertEquals(readsAfterFullCover, stats.snapshot().memoryHits)
        assertEquals(0L, stats.snapshot().decodeCount)
        assertEquals(0, interceptor.requestCount.get())
    }

    @Test
    fun previewBlendFreezesAndFullLoadCancelsWhileInactiveThenBothResume() {
        seedPreview(SecondCover, Color.BLUE)
        val fullCover = interceptor.hold(SecondCover, Color.CYAN)
        showArtworkState()
        finishTransition(FirstCover)

        changeModel(SecondCover)
        await { artwork.incomingFrame?.isPreview == true }
        await { fullCover.started.count == 0L }
        assertBlendingOver(FirstCover, SecondCover)
        setActive(false)
        await { fullCover.cancelled.count == 0L }
        val frozenBase = artwork.settledFrame
        val frozenPreview = artwork.incomingFrame
        val frozenAlpha = artwork.incomingAlpha.value
        compose.mainClock.advanceTimeBy(500L)
        compose.runOnIdle {
            assertSame(frozenBase, artwork.settledFrame)
            assertSame(frozenPreview, artwork.incomingFrame)
            assertEquals(frozenAlpha, artwork.incomingAlpha.value, 0f)
            assertTrue(frozenAlpha in 0.05f..0.95f)
        }

        setActive(true)
        await { interceptor.requestCount.get() == 2 }
        compose.mainClock.advanceTimeBy(180L)
        compose.runOnIdle {
            assertEquals(SecondCover, artwork.settledFrame?.model)
            assertTrue(artwork.settledFrame?.isPreview == true)
            assertNull(artwork.incomingFrame)
        }
        assertEquals(1L, fullCover.release.count)
        fullCover.release.countDown()
        await { artwork.incomingFrame?.model == SecondCover && artwork.incomingFrame?.isPreview == false }
        assertBlendingOver(SecondCover, SecondCover)
        finishTransition(SecondCover)
    }

    @Test
    fun memoryHitStillBlendsAndSameModelRecompositionDoesNotReloadOrRestartIt() {
        seedMemory(SecondCover, Color.BLUE)
        showArtworkState()
        finishTransition(FirstCover)
        changeModel(SecondCover)
        await { artwork.incomingFrame?.model == SecondCover }
        assertBlendingOver(FirstCover, SecondCover)
        finishTransition(SecondCover)

        val settled = artwork.settledFrame
        val cacheReads = stats.snapshot().memoryHits
        repeat(3) {
            compose.runOnIdle { revision.value++ }
            compose.mainClock.advanceTimeByFrame()
        }
        compose.runOnIdle {
            assertSame(settled, artwork.settledFrame)
            assertNull(artwork.incomingFrame)
        }
        assertEquals(cacheReads, stats.snapshot().memoryHits)
        assertEquals(0L, stats.snapshot().decodeCount)
        assertEquals(0, interceptor.requestCount.get())
    }

    @Test
    fun rapidReplacementCancelsSlowRequestAndOnlyLatestTargetAppears() {
        val slow = interceptor.hold(SecondCover, Color.BLUE)
        seedMemory(ThirdCover, Color.GREEN)
        showArtworkState()
        finishTransition(FirstCover)
        changeModel(SecondCover)
        await { slow.started.count == 0L }

        changeModel(ThirdCover)
        await { slow.cancelled.count == 0L }
        await { artwork.incomingFrame?.model == ThirdCover }
        assertBlendingOver(FirstCover, ThirdCover)
        slow.release.countDown()
        finishTransition(ThirdCover)
        compose.mainClock.advanceTimeBy(500L)
        compose.runOnIdle {
            assertEquals(ThirdCover, artwork.settledFrame?.model)
            assertNull(artwork.incomingFrame)
        }
        assertEquals(0L, stats.snapshot().decodeCount)
    }

    @Test
    fun inactiveCancelsLoadingAndFreezesBlendedLayersUntilReactivated() {
        val slow = interceptor.hold(SecondCover, Color.BLUE)
        seedMemory(ThirdCover, Color.GREEN)
        showArtworkState()
        finishTransition(FirstCover)
        changeModel(SecondCover)
        await { slow.started.count == 0L }

        setActive(false)
        await { slow.cancelled.count == 0L }
        compose.runOnIdle {
            assertEquals(FirstCover, artwork.settledFrame?.model)
            assertNull(artwork.incomingFrame)
            assertTrue(!artwork.incomingAlpha.isRunning)
        }
        val readsBeforeInactiveChange = stats.snapshot().memoryHits
        changeModel(ThirdCover)
        compose.mainClock.advanceTimeBy(500L)
        assertEquals(readsBeforeInactiveChange, stats.snapshot().memoryHits)

        setActive(true)
        await { artwork.incomingFrame?.model == ThirdCover }
        assertBlendingOver(FirstCover, ThirdCover)
        setActive(false)
        val frozenAlpha = artwork.incomingAlpha.value
        val frozenBase = artwork.settledFrame
        val frozenOverlay = artwork.incomingFrame
        compose.runOnIdle {
            assertEquals(FirstCover, artwork.settledFrame?.model)
            assertEquals(ThirdCover, artwork.incomingFrame?.model)
            assertTrue("离屏时应冻结当前混合帧", frozenAlpha in 0.05f..0.95f)
            assertTrue(!artwork.incomingAlpha.isRunning)
        }
        compose.mainClock.advanceTimeBy(500L)
        compose.runOnIdle {
            assertEquals(frozenAlpha, artwork.incomingAlpha.value, 0f)
            assertSame(frozenBase, artwork.settledFrame)
            assertSame(frozenOverlay, artwork.incomingFrame)
        }
        setActive(true)
        compose.mainClock.advanceTimeBy(32L)
        compose.runOnIdle {
            assertEquals(FirstCover, artwork.settledFrame?.model)
            assertEquals(ThirdCover, artwork.incomingFrame?.model)
            assertTrue(artwork.incomingAlpha.value > frozenAlpha)
            assertTrue(artwork.incomingAlpha.value < 1f)
        }
        finishTransition(ThirdCover)
    }

    private fun showArtworkState() {
        contentStarted = true
        compose.setContent {
            val present = composed.value
            if (present) {
                Box(modifier = Modifier.size(64.dp)) {
                    val currentRevision = revision.value
                    val currentModel = model.value
                    val currentPreviewModel = previewModel.value ?: currentModel
                    val currentActive = active.value
                    val state = rememberPlaylistHeroArtwork(currentModel, currentActive, RequestSize, currentPreviewModel)
                    SideEffect {
                        check(currentRevision >= 0)
                        artwork = state
                        composedModel = currentModel
                        composedActive = currentActive
                    }
                }
            }
            SideEffect { contentPresent = present }
        }
        awaitInputCommit { contentPresent }
    }

    private fun changeModel(value: String, preview: String? = null) {
        compose.runOnIdle {
            model.value = value
            previewModel.value = preview
        }
        awaitInputCommit { composedModel == value }
    }

    private fun setActive(value: Boolean) {
        compose.runOnIdle { active.value = value }
        awaitInputCommit { composedActive == value }
        compose.waitForIdle()
    }

    private fun assertBlendingOver(previous: String, replacement: String) {
        compose.runOnIdle {
            assertEquals(previous, artwork.settledFrame?.model)
            assertEquals(replacement, artwork.incomingFrame?.model)
            assertEquals(0f, artwork.incomingAlpha.value, 0f)
            assertNotNull(artwork.incomingFrame?.painter)
        }
        compose.waitUntil(5_000L) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { artwork.incomingAlpha.value >= 0.4f }
        }
        compose.runOnIdle {
            assertEquals(previous, artwork.settledFrame?.model)
            assertEquals(replacement, artwork.incomingFrame?.model)
            assertTrue("过渡中间帧应混合两张封面", artwork.incomingAlpha.value in 0.05f..0.95f)
        }
    }

    private fun finishTransition(target: String) {
        await { artwork.incomingFrame?.model == target || artwork.settledFrame?.model == target }
        compose.mainClock.advanceTimeBy(300L)
        compose.runOnIdle {
            assertEquals(target, artwork.settledFrame?.model)
            assertNull(artwork.incomingFrame)
        }
    }

    private fun await(condition: () -> Boolean) {
        compose.waitUntil(5_000L) {
            compose.mainClock.advanceTimeBy(0L, ignoreFrameDuration = true)
            shadowOf(Looper.getMainLooper()).idleFor(16L, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(0L, ignoreFrameDuration = true)
            condition()
        }
    }

    private fun awaitInputCommit(condition: () -> Boolean) {
        compose.waitUntil(5_000L) {
            shadowOf(Looper.getMainLooper()).idleFor(16L, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun seedMemory(url: String, color: Int, size: IntSize = RequestSize): Bitmap {
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val key = CacheKeyFactory.createKey(app, url, size, CacheVersion)
        memory.put(key, bitmap)
        return bitmap
    }

    private fun seedPreview(url: String, color: Int): Bitmap {
        val bitmap = seedMemory(url, color, PreviewSize)
        runBlocking { app.manager.loadImage(url, PreviewSize) }
        return bitmap
    }

    private class ControlledArtworkInterceptor : Interceptor {
        val requestCount = AtomicInteger()
        private val responses = ConcurrentHashMap<String, PendingResponse>()

        fun hold(url: String, color: Int): PendingResponse {
            val output = ByteArrayOutputStream()
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
            return PendingResponse(output.toByteArray()).also { responses[url] = it }
        }

        fun releaseAll() = responses.values.forEach { it.release.countDown() }

        override fun intercept(chain: Interceptor.Chain): Response {
            requestCount.incrementAndGet()
            val pending = responses[chain.request().url.toString()] ?: throw IOException("Unexpected cover request")
            pending.started.countDown()
            repeat(1_000) {
                if (chain.call().isCanceled()) {
                    pending.cancelled.countDown()
                    throw IOException("Cover request cancelled")
                }
                if (pending.release.await(10L, TimeUnit.MILLISECONDS)) {
                    return Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(pending.png.toResponseBody("image/png".toMediaType()))
                        .build()
                }
            }
            throw IOException("Controlled cover was not released")
        }
    }

    private class PendingResponse(val png: ByteArray) {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
    }

    companion object {
        private val RequestSize = IntSize(64, 64)
        private val PreviewSize = IntSize(32, 32)
        private const val CacheVersion = "playlist-hero-test"
        private const val FirstCover = "https://hero.test/first.png"
        private const val SecondCover = "https://hero.test/second.png"
        private const val ThirdCover = "https://hero.test/third.png"
        private const val ThumbnailCover = "https://hero.test/thumbnail.png"
    }
}
