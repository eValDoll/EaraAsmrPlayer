package com.asmr.player.ui.common

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.asmr.player.cache.CacheConfig
import com.asmr.player.cache.CacheKeyFactory
import com.asmr.player.cache.CacheStats
import com.asmr.player.cache.DiskCache
import com.asmr.player.cache.ImageCacheManager
import com.asmr.player.cache.ImageLoaderFacade
import com.asmr.player.cache.MemoryCache
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.library.AlbumItem
import com.asmr.player.ui.library.AlbumCoverDepthShadow
import com.asmr.player.ui.library.albumListCoverSize
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.ThemeMode
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose/fonts/cover/shadow. No Activity, business database, preferences, or network fixture. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class AlbumCoverShadowPixelAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Test
    fun settledAlbumItems_sharedShadowsMatchOriginalAndRestoreExactly() {
        val app = instrumentation.targetContext.applicationContext
        check(app is AlbumCoverShadowPixelTestApplication) { "Use AlbumCoverShadowPixelTestRunner" }
        val output = File(app.externalCacheDir ?: app.cacheDir, "album-cover-shadow-pixels-${System.nanoTime()}")
        check(output.mkdirs())
        val memory = MemoryCache(32 * 1024 * 1024)
        val version = "isolated-album-row-pixels"
        app.fixtureImages = ImageCacheManager(
            app, CacheConfig(cacheVersion = version), memory,
            DiskCache(File(output, "fixture-cache"), 1_048_576L, 0L),
            ImageLoaderFacade(app, OkHttpClient(), Dispatchers.IO), CacheStats(), Dispatchers.IO,
        )
        val results = JSONArray()
        var failed = false
        for ((density, fontScale) in listOf(1f to 1f, 3f to 1f, 1f to 2f, 3f to 2f)) {
            for (widthDp in listOf(360, 544)) {
                for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                    val name = "${mode.name}-$density-$widthDp" + if (fontScale == 1f) "" else "-font$fontScale"
                    val cover = File(output, "$name-cover.png")
                    val coverPx = (albumListCoverSize(widthDp).value * density).roundToInt()
                    val bitmap = Bitmap.createBitmap(coverPx, coverPx, Bitmap.Config.ARGB_8888)
                    val pixels = IntArray(coverPx * coverPx) { i ->
                        val x = i % coverPx
                        val y = i / coverPx
                        Color.rgb(24 + x * 200 / coverPx, 32 + y * 180 / coverPx, 220 - x * 160 / coverPx)
                    }
                    bitmap.setPixels(pixels, 0, coverPx, 0, 0, coverPx, coverPx)
                    cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    memory.put(CacheKeyFactory.createKey(app, cover, IntSize(coverPx, coverPx), version), bitmap)
                    runBlocking { checkNotNull(app.fixtureImages.loadImageFromCache(cover, IntSize(coverPx, coverPx))) }
                    lateinit var fixture: Fixture
                    instrumentation.runOnMainSync { fixture = Fixture(app, density, widthDp, mode, cover, fontScale) }
                    try {
                        instrumentation.runOnMainSync { fixture.show() }
                        val positions = if (fontScale == 1f && widthDp == 360) {
                            listOf(0f to 0f, .25f to 57.25f, -.25f to 113.75f)
                        } else listOf(0f to 0f)
                        for ((positionIndex, position) in positions.withIndex()) {
                            instrumentation.runOnMainSync { fixture.translation.value = position }
                            val original = settledCapture(fixture)
                            val originalBounds = checkNotNull(fixture.rowBounds)
                            instrumentation.runOnMainSync { fixture.assertBothRowsFullyVisible() }
                            instrumentation.runOnMainSync { fixture.shared.value = true }
                            val shared = settledCapture(fixture)
                            assertEquals("Shared shadows must preserve real layout", originalBounds, fixture.rowBounds)
                            instrumentation.runOnMainSync {
                                val cache = checkNotNull(fixture.cache) { "The fixture must exercise the shared cache" }
                                assertEquals("Identical rows must share one source", 1, cache.liveSourceCount)
                                assertEquals("Both rows must hold a live source lease", 2, cache.liveReferenceCount)
                            }
                            instrumentation.runOnMainSync { fixture.shared.value = false }
                            val restored = settledCapture(fixture)
                            instrumentation.runOnMainSync {
                                assertEquals("Fallback must release all shared sources", 0, checkNotNull(fixture.cache).liveSourceCount)
                            }
                            try {
                                val sharedDifference = difference(original, shared)
                                val fallbackDifference = difference(original, restored)
                                val caseName = "$name-position$positionIndex"
                                val result = JSONObject().put("fixture", caseName)
                                    .put("fontScale", fontScale).put("translationPx", JSONArray(listOf(position.first, position.second)))
                                    .put("simultaneousShadowRows", 2)
                                    .put("rowBounds", JSONArray(fixture.rowBoundsById.values.map { it.toString() }))
                                    .put("hardwareAccelerated", fixture.view.isHardwareAccelerated)
                                    .put("shared", sharedDifference).put("restoredOriginal", fallbackDifference)
                                    .put("requiredSharedMaxChannelDifference", 0)
                                    .put("requiredRestoredMaxChannelDifference", 0)
                                results.put(result)
                                failed = failed || sharedDifference.getInt("maxChannelDifference") != 0 ||
                                    fallbackDifference.getInt("maxChannelDifference") != 0
                                listOf("original" to original, "shared" to shared, "restored" to restored).forEach { (suffix, image) ->
                                    File(output, "$caseName-$suffix.png").outputStream().use {
                                        image.compress(Bitmap.CompressFormat.PNG, 100, it)
                                    }
                                }
                                instrumentation.sendStatus(2, Bundle().apply {
                                    putString("albumCoverShadowPixels", result.toString())
                                })
                            } finally {
                                original.recycle()
                                shared.recycle()
                                restored.recycle()
                            }
                        }
                    } finally {
                        instrumentation.runOnMainSync { fixture.close() }
                    }
                }
            }
        }
        val report = JSONObject().put("fixtureOnlyApplication", app.javaClass.name)
            .put("results", results).put("passed", !failed)
        File(output, "pixels.json").writeText(report.toString(2), Charsets.UTF_8)
        instrumentation.sendStatus(2, Bundle().apply { putString("albumCoverShadowArtifacts", output.absolutePath) })
        assertTrue("Actual Compose pixel mismatch; see $output/pixels.json", !failed)
    }

    @Test
    fun actualCoverFadeAndRowReplacement_preservePixelsAndSharedSourceLifetime() {
        val app = instrumentation.targetContext.applicationContext
        check(app is AlbumCoverShadowPixelTestApplication) { "Use AlbumCoverShadowPixelTestRunner" }
        val output = File(app.externalCacheDir ?: app.cacheDir, "album-cover-shadow-lifecycle-${System.nanoTime()}")
        check(output.mkdirs())
        app.fixtureImages = ImageCacheManager(
            app, CacheConfig(cacheVersion = "isolated-shadow-lifecycle"), MemoryCache(8 * 1024 * 1024),
            DiskCache(File(output, "fixture-cache"), 1_048_576L, 0L),
            ImageLoaderFacade(app, OkHttpClient(), Dispatchers.IO), CacheStats(), Dispatchers.IO,
        )
        val cover = File(output, "cover.png")
        Bitmap.createBitmap(372, 372, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(73, 126, 184))
            cover.outputStream().use { check(compress(Bitmap.CompressFormat.PNG, 100, it)) }
            recycle()
        }
        val cases = JSONArray()
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            lateinit var fixture: Fixture
            instrumentation.runOnMainSync { fixture = Fixture(app, 3f, 360, mode, cover, 1f, animateCovers = true); fixture.show() }
            try {
                awaitAlpha(fixture) { it.all { alpha -> alpha == 0f } }
                compareSharedPhase(fixture, output, "${mode.name}-fade-zero", expectedReferences = 0, cases = cases)

                instrumentation.runOnMainSync { fixture.animationsActive.value = true }
                awaitAlpha(fixture) { it.all { alpha -> alpha in 0.25f..0.75f } }
                instrumentation.runOnMainSync { fixture.animationsActive.value = false }
                compareSharedPhase(fixture, output, "${mode.name}-fade-middle", expectedReferences = 0, cases = cases)

                instrumentation.runOnMainSync { fixture.animationsActive.value = true }
                awaitAlpha(fixture) { it.all { alpha -> alpha == 1f } }
                compareSharedPhase(fixture, output, "${mode.name}-fade-settled", expectedReferences = 2, cases = cases)
                var source: Any? = null
                instrumentation.runOnMainSync {
                    source = onlySource(checkNotNull(fixture.cache))
                    fixture.rows.value = listOf(2)
                }
                settledCapture(fixture).recycle()
                instrumentation.runOnMainSync {
                    assertEquals(1, checkNotNull(fixture.cache).liveReferenceCount)
                    assertSame("Removing the first row must retain the second row's source", source, onlySource(checkNotNull(fixture.cache)))
                    assertEquals(listOf(2), fixture.rows.value)
                }
                compareSharedPhase(fixture, output, "${mode.name}-first-row-removed", expectedReferences = 1, cases = cases)
                instrumentation.runOnMainSync {
                    source = onlySource(checkNotNull(fixture.cache))
                    fixture.rows.value = listOf(2, 3)
                }
                awaitAlpha(fixture) { it.all { alpha -> alpha == 1f } }
                settledCapture(fixture).recycle()
                instrumentation.runOnMainSync {
                    assertEquals(2, checkNotNull(fixture.cache).liveReferenceCount)
                    assertSame("A new row must reuse the remaining row's source", source, onlySource(checkNotNull(fixture.cache)))
                }
                compareSharedPhase(fixture, output, "${mode.name}-new-row-inserted", expectedReferences = 2, cases = cases)
            } finally {
                instrumentation.runOnMainSync {
                    fixture.close()
                    assertEquals("Disposing the owner must release every source", 0, checkNotNull(fixture.cache).liveSourceCount)
                }
                File(output, "pixels.json").writeText(JSONObject().put("cases", cases).toString(2), Charsets.UTF_8)
                instrumentation.sendStatus(2, Bundle().apply { putString("albumCoverShadowArtifacts", output.absolutePath) })
            }
        }
    }

    private fun awaitAlpha(fixture: Fixture, predicate: (List<Float>) -> Boolean) {
        repeat(240) {
            var ready = false
            val frame = CountDownLatch(1)
            instrumentation.runOnMainSync {
                val ids = fixture.rows.value
                ready = ids.all { it in fixture.alphaStates } && predicate(ids.map { checkNotNull(fixture.alphaStates[it]).value })
                if (!ready) fixture.view.postOnAnimation { frame.countDown() }
            }
            if (ready) return
            check(frame.await(1, TimeUnit.SECONDS)) { "No animation frame while waiting for cover progress" }
        }
        error("Real cover fade did not reach the expected progress")
    }

    private fun onlySource(cache: AlbumCoverShadowCache): Any {
        val field = AlbumCoverShadowCache::class.java.getDeclaredField("sources").apply { isAccessible = true }
        return (field.get(cache) as Map<*, *>).values.single()!!
    }

    private fun compareSharedPhase(fixture: Fixture, output: File, name: String, expectedReferences: Int, cases: JSONArray) {
        val frames = linkedMapOf<String, Bitmap>()
        val result = JSONObject().put("case", name).put("expectedReferences", expectedReferences)
        try {
            instrumentation.runOnMainSync { fixture.shared.value = false }
            frames["original"] = settledCapture(fixture)
            instrumentation.runOnMainSync { fixture.shared.value = true }
            frames["shared"] = settledCapture(fixture)
            instrumentation.runOnMainSync {
                val cache = checkNotNull(fixture.cache)
                assertEquals(if (expectedReferences == 0) 0 else 1, cache.liveSourceCount)
                assertEquals(expectedReferences, cache.liveReferenceCount)
                result.put("alpha", JSONArray(fixture.rows.value.map { checkNotNull(fixture.alphaStates[it]).value }))
            }
            val comparison = difference(checkNotNull(frames["original"]), checkNotNull(frames["shared"]))
            result.put("comparison", comparison).put("requiredMaxChannelDifference", 0)
            assertEquals("Shared shadow mismatch at $name", 0, comparison.getInt("maxChannelDifference"))
        } finally {
            cases.put(result)
            try {
                for ((suffix, bitmap) in frames) File(output, "$name-$suffix.png").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { frames.values.forEach(Bitmap::recycle) }
        }
    }

    private fun settledCapture(fixture: Fixture): Bitmap {
        var previous: Bitmap? = null
        repeat(30) {
            val committed = CountDownLatch(1)
            instrumentation.runOnMainSync {
                fixture.view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                fixture.view.invalidate()
            }
            check(committed.await(5, TimeUnit.SECONDS)) { "No hardware frame commit" }
            val ready = CountDownLatch(1)
            var result = PixelCopy.ERROR_UNKNOWN
            lateinit var target: Bitmap
            instrumentation.runOnMainSync {
                check(fixture.view.isHardwareAccelerated) { "Software rendering cannot verify shared shadows" }
                val location = IntArray(2)
                fixture.view.getLocationInWindow(location)
                target = Bitmap.createBitmap(fixture.view.width, fixture.view.height, Bitmap.Config.ARGB_8888)
                PixelCopy.request(checkNotNull(fixture.presentation.window),
                    Rect(location[0], location[1], location[0] + target.width, location[1] + target.height),
                    target, { result = it; ready.countDown() }, mainHandler)
            }
            check(ready.await(5, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS) { "PixelCopy failed: $result" }
            val stable = previous?.sameAs(target) == true
            previous?.recycle()
            if (stable) return target
            previous = target
        }
        previous?.recycle()
        error("AlbumItem pixels did not settle")
    }

    private fun difference(reference: Bitmap, candidate: Bitmap): JSONObject {
        check(reference.width == candidate.width && reference.height == candidate.height)
        val before = IntArray(reference.width * reference.height)
        val after = IntArray(before.size)
        reference.getPixels(before, 0, reference.width, 0, 0, reference.width, reference.height)
        candidate.getPixels(after, 0, candidate.width, 0, 0, candidate.width, candidate.height)
        val histogram = LongArray(256)
        for (i in before.indices) {
            var maximum = 0
            var referenceChannels = before[i]
            var candidateChannels = after[i]
            repeat(4) {
                maximum = max(maximum, abs((referenceChannels and 255) - (candidateChannels and 255)))
                referenceChannels = referenceChannels ushr 8
                candidateChannels = candidateChannels ushr 8
            }
            histogram[maximum]++
        }
        return JSONObject().put("maxChannelDifference", histogram.indexOfLast { it > 0 })
            .put("pixelsDifferent", before.size - histogram[0]).put("pixelsOver1", histogram.drop(2).sum())
            .put("maxChannelDifferenceHistogram", JSONArray(histogram.toList()))
    }

    private class Fixture(
        context: Context,
        density: Float,
        widthDp: Int,
        mode: ThemeMode,
        private val cover: File,
        fontScale: Float,
        private val animateCovers: Boolean = false,
    ) {
        val rows = mutableStateOf(listOf(1, 2))
        val animationsActive = mutableStateOf(!animateCovers)
        val alphaStates = mutableMapOf<Int, State<Float>>()
        private val coverSize = albumListCoverSize(widthDp)
        val shared = mutableStateOf(false)
        val translation = mutableStateOf(0f to 0f)
        var cache: AlbumCoverShadowCache? = null
        var rowBounds: androidx.compose.ui.geometry.Rect? = null
            private set
        val rowBoundsById = mutableMapOf<Int, androidx.compose.ui.geometry.Rect>()
        private val width = ((widthDp + 80) * density).roundToInt()
        private val height = ((if (fontScale > 1f) 960 else 760) * density).roundToInt()
        private val host = HardwarePixelTestHost(context, width, height, density)
        val presentation get() = host.presentation
        val view get() = host.view

        init {
            val configuration = Configuration(presentation.context.resources.configuration).apply {
                screenWidthDp = widthDp
                this.fontScale = fontScale
            }
            val album = Album(title = "耳边细语・AlbumItem 硬件绘制对照", path = "/isolated-pixel-fixture",
                circle = "Eara Pixel Circle", cv = "CV 测试 / Example Voice", tags = listOf("双耳", "耳かき", "binaural"),
                rjCode = "RJ999999", coverPath = cover.absolutePath, ratingValue = 4.9, ratingCount = 3210,
                dlCount = 123456, priceJpy = 1980, releaseDate = "2026-10-25")
            view.setContent {
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale), LocalConfiguration provides configuration,
                    LocalPageAnimationsActive provides animationsActive.value) {
                    val cache = rememberAlbumCoverShadowCache()
                    SideEffect { this@Fixture.cache = cache }
                    CompositionLocalProvider(LocalAlbumCoverShadowCache provides cache.takeIf { shared.value }) {
                        AsmrPlayerTheme(mode = mode) {
                            val scrollState = rememberScrollState()
                            Box(Modifier.fillMaxSize().background(AsmrTheme.colorScheme.background).padding(40.dp)) {
                                Column(Modifier.graphicsLayer {
                                    translationX = translation.value.first
                                    translationY = translation.value.second
                                }.then(
                                    if (animateCovers) Modifier else Modifier
                                        .verticalScroll(scrollState)
                                        .padding(vertical = 50.dp)
                                ), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    rows.value.forEachIndexed { index, id ->
                                        key(id) {
                                            if (animateCovers) {
                                                FadingCover(id)
                                            } else {
                                                AlbumItem(album, onClick = {}, onLongClick = {}, animateOnlineDetails = false,
                                                    coverFadeIn = false, modifier = Modifier.width(widthDp.dp).onGloballyPositioned {
                                                        val bounds = it.unclippedBoundsInWindow()
                                                        rowBoundsById[id] = bounds
                                                        if (index == 0) rowBounds = bounds
                                                    })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun assertBothRowsFullyVisible() {
            assertEquals(setOf(1, 2), rowBoundsById.keys)
            rowBoundsById.forEach { (id, bounds) ->
                assertTrue("Row $id must have a positive size: $bounds", bounds.width > 0f && bounds.height > 0f)
                assertTrue("Row $id must remain fully visible: $bounds inside ${view.width}x${view.height}",
                    bounds.left >= 0f && bounds.top >= 0f && bounds.right <= view.width && bounds.bottom <= view.height)
            }
        }

        @Composable
        private fun FadingCover(id: Int) {
            val alpha = remember(id) { mutableStateOf<State<Float>?>(null) }
            val shape = remember { RoundedCornerShape(12.dp) }
            val sizePx = with(LocalDensity.current) { coverSize.roundToPx() }
            DisposableEffect(id) { onDispose { alphaStates.remove(id) } }
            Box(Modifier.size(coverSize)) {
                AlbumCoverDepthShadow(
                    progress = { alpha.value?.value ?: 0f }, isDark = AsmrTheme.colorScheme.isDark,
                    shape = shape, blurRadius = 10.dp, modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().clip(shape)) {
                    AsmrAsyncImage(
                        model = cover, contentDescription = null, fadeIn = true, fadeInMillis = 800,
                        peekAnySizeForInitial = false, requestSize = IntSize(sizePx, sizePx),
                        loading = NoImageLoadingIndicator, modifier = Modifier.fillMaxSize(),
                        onBitmapPainterState = { painter, progress ->
                            alpha.value = progress.takeIf { painter != null }
                            if (painter == null) alphaStates.remove(id) else alphaStates[id] = progress
                        },
                    )
                }
            }
        }

        private fun LayoutCoordinates.unclippedBoundsInWindow(): androidx.compose.ui.geometry.Rect {
            val root = findRootCoordinates()
            val bounds = root.localBoundingBoxOf(this, clipBounds = false)
            if (bounds.width == 0f || bounds.height == 0f) return androidx.compose.ui.geometry.Rect.Zero
            val corners = arrayOf(bounds.topLeft, bounds.topRight, bounds.bottomRight, bounds.bottomLeft)
                .map(root::localToWindow)
            return androidx.compose.ui.geometry.Rect(
                corners.minOf { it.x }, corners.minOf { it.y },
                corners.maxOf { it.x }, corners.maxOf { it.y },
            )
        }

        fun show() = host.show()

        fun close() = host.close()
    }
}
