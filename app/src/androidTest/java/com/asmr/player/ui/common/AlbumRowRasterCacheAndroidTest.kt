package com.asmr.player.ui.common

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
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
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.ThemeMode
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real AlbumItem reuse and dirty propagation; isolated owners and in-memory covers only. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
@OptIn(ExperimentalFoundationApi::class)
class AlbumRowRasterCacheAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun cachedRows_preserveContentAcrossReuseReplacementAndOwnerRecreation() {
        val covers = prepareImages()
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            val expected = sequence(covers, mode, cached = false)
            try {
                val actual = sequence(covers, mode, cached = true)
                try {
                    assertFalse("Far rows must display their own content", expected.settled[0].sameAs(expected.settled[1]))
                    assertFalse("Same-key replacement must change text and cover", expected.settled[0].sameAs(expected.settled[3]))
                    expected.settled.indices.forEach { index ->
                        assertEquivalent("$mode lifecycle stage $index", expected.settled[index], actual.settled[index])
                    }
                    assertEquals(expected.first.keys, actual.first.keys)
                    expected.first.forEach { (stage, bitmap) ->
                        assertEquivalent("$mode first committed $stage", bitmap, actual.first.getValue(stage))
                    }
                    assertEquivalent("$mode return to first rows", actual.settled[0], actual.settled[2])
                    assertEquivalent("$mode owner recreation", actual.settled[3], actual.settled[4])
                } finally {
                    actual.close()
                }
            } finally {
                expected.close()
            }
        }
    }

    @Test
    fun transformedRows_disableRasterAndRestoreUpdatedContent() {
        val covers = prepareImages()
        val reference = transformSequence(covers, cached = false)
        try {
            val candidate = transformSequence(covers, cached = true)
            try {
                assertTrue("A real scale/translation must change the rendered scene", !reference[0].sameAs(reference[1]))
                reference.indices.forEach { index ->
                    assertEquivalent("Transform stage $index", reference[index], candidate[index])
                }
            } finally {
                candidate.forEach(Bitmap::recycle)
            }
        } finally {
            reference.forEach(Bitmap::recycle)
        }
    }

    @Test
    fun integerHorizontalTranslation_preservesRetainedRowsAndUpdatesAfterReturn() {
        val covers = prepareImages()
        val reference = linkedMapOf<String, Bitmap>()
        try {
            translationSequence(covers, cached = false) { stage, bitmap -> reference[stage] = bitmap }
            val original = reference.getValue("initial")
            assertFalse("The intermediate translation must move the visible rows",
                original.sameAs(reference.getValue("visible settled")))
            assertFalse("Replacing content after returning must update the scene",
                original.sameAs(reference.getValue("updated settled")))
            val compared = mutableListOf<String>()
            translationSequence(covers, cached = true) { stage, bitmap ->
                try {
                    assertEquivalent("Integer translation $stage", reference.getValue(stage), bitmap)
                    if (stage == "returned first" || stage == "returned settled") {
                        assertEquivalent("Integer translation restores the original $stage", original, bitmap)
                    }
                    compared += stage
                } finally {
                    bitmap.recycle()
                }
            }
            assertEquals("Every reference phase must be compared", reference.keys.toList(), compared)
        } finally {
            reference.values.forEach(Bitmap::recycle)
        }
    }

    private fun sequence(covers: List<File>, mode: ThemeMode, cached: Boolean): LifecycleCaptures {
        val captures = LifecycleCaptures()
        val fixture = fixture(covers, mode, cached)
        try {
            captures.settled += captureLoaded(fixture, coverIndex = 0)
            captures.first["distant"] = captureFirstText(fixture, coverIndex = 0) { fixture.scroll.requestScrollToItem(70) }
            assertEquals("The first distant frame must have changed the lazy window", 70, fixture.scroll.firstVisibleItemIndex)
            captures.settled += captureLoaded(fixture, coverIndex = 0)
            assertFirstText("$mode first distant text", fixture, captures.settled[1], captures.first.getValue("distant"))
            captures.first["returned"] = captureFirstText(fixture, coverIndex = 0) { fixture.scroll.requestScrollToItem(0) }
            assertEquals("The first returning frame must have restored the lazy window", 0, fixture.scroll.firstVisibleItemIndex)
            captures.settled += captureLoaded(fixture, coverIndex = 0)
            assertFirstText("$mode first returned text", fixture, captures.settled[2], captures.first.getValue("returned"))
            captures.first["replaced"] = captureFirstText(fixture, coverIndex = 1) { fixture.replaced.value = true }
            captures.settled += captureLoaded(fixture, coverIndex = 1)
            assertFirstText("$mode first replaced text", fixture, captures.settled[3], captures.first.getValue("replaced"))
        } catch (failure: Throwable) {
            captures.close()
            throw failure
        } finally {
            instrumentation.runOnMainSync { fixture.host.close() }
        }
        val recreated = fixture(covers, mode, cached)
        try {
            // Populate the owner's first rows before replacing them; the replacement must also
            // survive the newly created raster-layer lifetime.
            captureLoaded(recreated, coverIndex = 0).recycle()
            captures.first["recreated"] = captureFirstText(recreated, coverIndex = 1) { recreated.replaced.value = true }
            captures.settled += captureLoaded(recreated, coverIndex = 1)
            assertFirstText("$mode first recreated text", recreated, captures.settled[4], captures.first.getValue("recreated"))
        } catch (failure: Throwable) {
            captures.close()
            throw failure
        } finally {
            instrumentation.runOnMainSync { recreated.host.close() }
        }
        return captures
    }

    private fun transformSequence(covers: List<File>, cached: Boolean): List<Bitmap> {
        val fixture = fixture(covers, ThemeMode.Dark, cached)
        val captures = mutableListOf<Bitmap>()
        return try {
            captures += captureLoaded(fixture, coverIndex = 0)
            instrumentation.runOnMainSync { fixture.transformed.value = true }
            captures += captureLoaded(fixture, coverIndex = 0)
            instrumentation.runOnMainSync { fixture.replaced.value = true }
            captures += captureLoaded(fixture, coverIndex = 1)
            instrumentation.runOnMainSync { fixture.transformed.value = false }
            captures += captureLoaded(fixture, coverIndex = 1)
            captures
        } catch (failure: Throwable) {
            captures.forEach(Bitmap::recycle)
            throw failure
        } finally {
            instrumentation.runOnMainSync { fixture.host.close() }
        }
    }

    private fun translationSequence(
        covers: List<File>,
        cached: Boolean,
        capture: (String, Bitmap) -> Unit,
    ) {
        val fixture = fixture(covers, ThemeMode.Dark, cached)
        try {
            val original = captureLoaded(fixture, coverIndex = 0)
            val background = original.getPixel(0, 0)
            // The receiver owns each bitmap. Compare and release candidate frames as they
            // arrive, so first-commit coverage does not retain two full screenshot sequences.
            capture("initial", original)
            for ((stage, translationX) in listOf("visible" to -180, "outside" to 1440, "returned" to 0)) {
                val first = captureChangedFrame(fixture) { fixture.horizontalTranslation.intValue = translationX }
                assertTranslationContent(fixture, first, background, coverIndex = 0, outside = stage == "outside")
                capture("$stage first", first)
                val settled = if (stage == "outside") fixture.host.captureSettled() else captureLoaded(fixture, coverIndex = 0)
                assertTranslationContent(fixture, settled, background, coverIndex = 0, outside = stage == "outside")
                capture("$stage settled", settled)
            }
            val updated = captureChangedFrame(fixture) { fixture.replaced.value = true }
            assertTranslationContent(fixture, updated, background, coverIndex = 1, outside = false)
            capture("updated first", updated)
            capture("updated settled", captureLoaded(fixture, coverIndex = 1))
        } finally {
            instrumentation.runOnMainSync { fixture.host.close() }
        }
    }

    private fun assertTranslationContent(
        fixture: Fixture,
        bitmap: Bitmap,
        background: Int,
        coverIndex: Int,
        outside: Boolean,
    ) {
        try {
            if (outside) {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("The fully translated page must leave no text, cover or shadow behind",
                    pixels.all { it == background })
            } else {
                assertTrue("The translated frame must display its preloaded cover $coverIndex",
                    hasCoverColor(bitmap, CoverColors[coverIndex], fixture.horizontalTranslation.intValue))
            }
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    private fun fixture(covers: List<File>, mode: ThemeMode, cached: Boolean): Fixture {
        lateinit var fixture: Fixture
        instrumentation.runOnMainSync {
            fixture = Fixture(covers, mode, cached)
            fixture.host.show()
        }
        return fixture
    }

    private fun captureLoaded(fixture: Fixture, coverIndex: Int): Bitmap {
        // A pair of identical placeholder frames is not evidence that the image is loaded.
        repeat(10) {
            val bitmap = fixture.host.captureSettled()
            if (hasCoverColor(bitmap, CoverColors[coverIndex], fixture.horizontalTranslation.intValue)) return bitmap
            bitmap.recycle()
        }
        error("The fixture never displayed its preloaded cover $coverIndex")
    }

    private fun hasCoverColor(bitmap: Bitmap, color: Int, translationX: Int = 0): Boolean {
        // Inside the first 124 dp cover, away from its rounded corners, shadow and overlays.
        // This interior remains covered for the fixture's small scale/translation as well.
        val pixels = IntArray(120 * 120)
        bitmap.getPixels(pixels, 0, 120, 282 + translationX, 258, 120, 120)
        return pixels.count { pixel ->
            abs(Color.red(pixel) - Color.red(color)) <= 2 &&
                abs(Color.green(pixel) - Color.green(color)) <= 2 &&
                abs(Color.blue(pixel) - Color.blue(color)) <= 2
        } >= pixels.size * 95 / 100
    }

    private fun captureChangedFrame(fixture: Fixture, change: () -> Unit): Bitmap {
        val committed = CountDownLatch(1)
        var generation = 0
        instrumentation.runOnMainSync {
            generation = fixture.frameGeneration.intValue + 1
            // ViewRootImpl collects commit listeners before draw(); never register from onDraw.
            fixture.host.view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            change()
            fixture.frameGeneration.intValue = generation
            Snapshot.sendApplyNotifications()
            fixture.host.view.invalidate()
        }
        check(committed.await(5, TimeUnit.SECONDS)) { "No committed changed raster fixture frame" }
        assertEquals("The committed draw must contain the requested generation",
            generation, fixture.lastDrawGeneration.get())
        val copied = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        lateinit var bitmap: Bitmap
        instrumentation.runOnMainSync {
            val view = fixture.host.view
            check(view.isHardwareAccelerated)
            val location = IntArray(2)
            view.getLocationInWindow(location)
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(checkNotNull(fixture.host.presentation.window),
                Rect(location[0], location[1], location[0] + view.width, location[1] + view.height),
                bitmap, { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
        }
        check(copied.await(5, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS) {
            "PixelCopy after raster fixture commit failed: $result"
        }
        // PixelCopy returns the latest available buffer, not a pinned historical frame.
        return bitmap
    }

    private fun captureFirstText(fixture: Fixture, coverIndex: Int, change: () -> Unit): Bitmap {
        val bitmap = captureChangedFrame(fixture, change)
        try {
            assertTrue("The first changed frame must contain the preloaded cover $coverIndex",
                hasCoverColor(bitmap, CoverColors[coverIndex]))
            return firstText(fixture, bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun firstText(fixture: Fixture, bitmap: Bitmap): Bitmap {
        // The untransformed fixture is 3 px/dp: 40 dp outer inset, 12 dp item inset,
        // 124 dp cover, 10 dp cover/text gap, 4/8 dp item top/bottom inset. Use the
        // measured lazy-item height, since the metadata can make it taller than the cover.
        lateinit var area: Rect
        instrumentation.runOnMainSync {
            val item = checkNotNull(fixture.scroll.layoutInfo.visibleItemsInfo.firstOrNull())
            val top = (120 + item.offset + 12).coerceAtLeast(120)
            val bottom = (120 + item.offset + item.size - 24).coerceAtMost(bitmap.height - 120)
            check(bottom > top) { "No visible first-row metadata area" }
            area = Rect(558, top, 1164, bottom)
        }
        return Bitmap.createBitmap(bitmap, area.left, area.top, area.width(), area.height())
    }

    private fun assertFirstText(label: String, fixture: Fixture, settled: Bitmap, first: Bitmap) {
        val expected = firstText(fixture, settled)
        try {
            assertEquivalent(label, expected, first)
        } finally {
            expected.recycle()
        }
    }

    private fun prepareImages(): List<File> {
        val app = instrumentation.targetContext.applicationContext
        check(app is AlbumCoverShadowPixelTestApplication) { "Use AlbumRowRasterCacheTestRunner" }
        val output = File(app.cacheDir, "row-raster-lifecycle-${System.nanoTime()}").apply { check(mkdirs()) }
        val memory = MemoryCache(8 * 1024 * 1024)
        val version = "isolated-row-raster-lifecycle"
        app.fixtureImages = ImageCacheManager(
            app, CacheConfig(cacheVersion = version), memory,
            DiskCache(File(output, "images"), 1_048_576L, 0L),
            ImageLoaderFacade(app, OkHttpClient(), Dispatchers.IO), CacheStats(), Dispatchers.IO,
        )
        return CoverColors.mapIndexed { index, color ->
            val file = File(output, "cover-$index.png")
            val bitmap = Bitmap.createBitmap(372, 372, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            memory.put(CacheKeyFactory.createKey(app, file, IntSize(372, 372), version), bitmap)
            runBlocking { checkNotNull(app.fixtureImages.loadImageFromCache(file, IntSize(372, 372))) }
            file
        }
    }

    private inner class Fixture(covers: List<File>, mode: ThemeMode, cached: Boolean) {
        val host = HardwarePixelTestHost(instrumentation.targetContext, 1320, 2100, 3f)
        val scroll = LazyListState()
        val replaced = mutableStateOf(false)
        val transformed = mutableStateOf(false)
        val horizontalTranslation = mutableIntStateOf(0)
        val frameGeneration = mutableIntStateOf(0)
        val lastDrawGeneration = AtomicInteger(-1)
        private val allowed = mutableStateOf(cached)

        init {
            val configuration = Configuration(host.presentation.context.resources.configuration).apply {
                screenWidthDp = 360
                fontScale = 1f
            }
            host.view.setContent {
                CompositionLocalProvider(
                    LocalConfiguration provides configuration,
                    LocalDensity provides Density(3f, 1f),
                    LocalPageAnimationsActive provides true,
                    LocalAlbumRowRasterCacheAllowed provides if (cached) allowed else null,
                ) {
                    AsmrPlayerTheme(mode = mode) {
                        val shadows = rememberAlbumCoverShadowCache()
                        CompositionLocalProvider(LocalAlbumCoverShadowCache provides shadows) {
                            AlbumRowRasterCacheScope(transformIsIdentity = { !transformed.value }) {
                                Box(Modifier.fillMaxSize().background(AsmrTheme.colorScheme.background)
                                    .drawWithContent {
                                        drawContent()
                                        lastDrawGeneration.set(frameGeneration.intValue)
                                    }.padding(40.dp)) {
                                    LazyColumn(
                                        state = scroll,
                                        modifier = Modifier.width(360.dp).graphicsLayer {
                                            // Integer parent translation keeps the candidate's
                                            // allowed State true; scale/parallax still use Auto.
                                            translationX = horizontalTranslation.intValue.toFloat()
                                            scaleY = if (transformed.value) 1.018f else 1f
                                            translationY = if (transformed.value) 12.25f else 0f
                                        },
                                    ) {
                                        items(100, key = { it }, contentType = { "album" }) { id ->
                                            val updated = replaced.value
                                            AlbumItem(
                                                album = Album(
                                                    id = id.toLong() + 1,
                                                    title = if (updated) "更新后的标题 $id・文字与封面同时改变" else "专辑 $id・耳边细语 ASMR",
                                                    path = "/isolated-row-$id",
                                                    circle = "Eara Circle $id",
                                                    cv = if (updated) "新声优 / Updated voice" else "CV 测试 / Voice $id",
                                                    tags = if (updated) listOf("更新", "新标签") else listOf("双耳", "耳かき", "binaural"),
                                                    rjCode = "RJ${100000 + id}",
                                                    coverPath = covers[if (updated) 1 else 0].absolutePath,
                                                    ratingValue = 4.9,
                                                    ratingCount = 123,
                                                    priceJpy = 1980,
                                                    releaseDate = "2026-10-29",
                                                ),
                                                onClick = {},
                                                onLongClick = {},
                                                onCvLongClick = {},
                                                onTagLongClick = {},
                                                animateOnlineDetails = false,
                                                coverFadeIn = false,
                                            )
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

    private fun assertEquivalent(label: String, expected: Bitmap, actual: Bitmap) {
        check(expected.width == actual.width && expected.height == actual.height)
        val reference = IntArray(expected.width * expected.height)
        val candidate = IntArray(reference.size)
        expected.getPixels(reference, 0, expected.width, 0, 0, expected.width, expected.height)
        actual.getPixels(candidate, 0, actual.width, 0, 0, actual.width, actual.height)
        var maximum = 0
        var overOne = 0
        reference.indices.forEach { index ->
            var pixelDifference = 0
            for (channel in 0..3) {
                val shift = channel * 8
                pixelDifference = maxOf(pixelDifference,
                    abs((reference[index] ushr shift and 255) - (candidate[index] ushr shift and 255)))
            }
            maximum = maxOf(maximum, pixelDifference)
            if (pixelDifference > 1) overOne++
        }
        // One extra 8-bit compositing step can round by one, with isolated AA edges rounding twice.
        // Missing glyphs, stale covers or clipped shadows exceed this bound by a wide margin.
        val equivalent = maximum <= 2 && overOne <= reference.size / 1000
        var evidence: File? = null
        if (!equivalent) {
            evidence = File(instrumentation.targetContext.externalCacheDir
                ?: instrumentation.targetContext.cacheDir, "row-raster-failure-${System.nanoTime()}")
            check(evidence.mkdirs())
            File(evidence, "expected.png").outputStream().use { expected.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(evidence, "actual.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(evidence, "stage.txt").writeText(label, Charsets.UTF_8)
        }
        assertTrue("$label: maximum channel difference=$maximum, pixels over one=$overOne; evidence=$evidence",
            equivalent)
    }

    private class LifecycleCaptures {
        val settled = mutableListOf<Bitmap>()
        val first = linkedMapOf<String, Bitmap>()
        fun close() { settled.forEach(Bitmap::recycle); first.values.forEach(Bitmap::recycle) }
    }

    private companion object {
        val CoverColors = listOf(Color.rgb(45, 129, 187), Color.rgb(205, 93, 43))
    }
}
