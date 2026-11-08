package com.asmr.player.ui.library

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.asmr.player.R
import com.asmr.player.ui.common.HardwarePixelTestHost
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Independent hardware owners; no Activity, AsmrApp, database, services, or network. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class SharedAlbumIconPainterAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val resources = listOf(R.drawable.ic_album_meta_club, R.drawable.ic_album_meta_cv, R.drawable.ic_album_meta_tags)
    private val colors = listOf(Color(0xFFDD3344), Color(0xFF22AA66), Color(0xFF2266DD), Color(0xFFCC9922))

    @Test
    fun sharedSize_preservesTintAlphaAndUntouchedDisplayListsAfterLocalChange() {
        val specs = (0 until 12).map { index ->
            Spec(13f, colors[index / 3], listOf(1f, .65f, .3f, .9f)[index / 3])
        }
        withPair(specs) { pair ->
            val before = compareSettled(pair, "same-size-multiple-tint-alpha")
            try {
                val untouched = (0 until 12).filter { it != 4 }
                val retained = counts(pair, untouched)
                val changed = changeAndCompare(pair, "one-cell-changed") { fixture ->
                    fixture.cells[4].spec.value = Spec(13f, Color(0xFFAA22CC), .42f)
                }
                try {
                    assertFalse("Control must actually change visible content", before.sameAs(changed))
                    assertEquals("Unchanged per-cell layers must retain their recorded draw", retained, counts(pair, untouched))
                } finally {
                    changed.recycle()
                }
                changeAndCompare(pair, "one-cell-restored") { fixture ->
                    fixture.cells[4].spec.value = specs[4]
                }.let { restored ->
                    try { assertTrue("Restored content must match initial pixels", before.sameAs(restored)) }
                    finally { restored.recycle() }
                }
            } finally { before.recycle() }
        }
    }

    @Test
    fun interleavedActualSizesAndFilterFallback_doNotRewriteSharedBitmapReferences() {
        val specs = (0 until 12).map { index ->
            Spec(listOf(13f, 7f, 19f, 13f)[index / 3], colors[index / 3], .85f,
                if (index == 10) BlendMode.SrcOver else BlendMode.SrcIn)
        }
        withPair(specs) { pair ->
            compareSettled(pair, "interleaved-13-7-19-13").recycle()
            val untouched = listOf(0, 1, 2, 9, 11)
            val retained = counts(pair, untouched)
            changeAndCompare(pair, "swap-small-large-and-argb-filter") { fixture ->
                fixture.cells[3].spec.value = specs[3].copy(sizeDp = 19f)
                fixture.cells[6].spec.value = specs[6].copy(sizeDp = 7f)
                fixture.cells[10].spec.value = specs[10].copy(blend = BlendMode.Multiply)
            }.recycle()
            assertEquals("Shared 13dp cells must not rerecord while other cache sizes change", retained, counts(pair, untouched))
            changeAndCompare(pair, "restore-size-and-alpha-mask-filter") { fixture ->
                listOf(3, 6, 10).forEach { fixture.cells[it].spec.value = specs[it] }
            }.recycle()
        }
    }

    @Test
    fun directionChangeAndDensityOwnerRecreation_preserveIndependentResourceLifetimes() {
        val specs = (0 until 12).map { Spec(13f, colors[it / 3], .75f) }
        withPair(specs, density = 2f) { original ->
            compareSettled(original, "ltr-density-two").recycle()
            changeAndCompare(original, "rtl-same-owner") { it.direction.value = LayoutDirection.Rtl }.recycle()
            // Keep the old owners alive while distinct density/resource owners are created.
            withPair(specs, density = 3f, direction = LayoutDirection.Rtl) { recreated ->
                compareSettled(recreated, "rtl-recreated-density-three").recycle()
                compareSettled(original, "old-owner-after-new-owner-draw").recycle()
                changeAndCompare(recreated, "ltr-new-owner") { it.direction.value = LayoutDirection.Ltr }.recycle()
            }
        }
    }

    private data class Spec(val sizeDp: Float, val color: Color, val alpha: Float, val blend: BlendMode = BlendMode.SrcIn)
    private class Cell(spec: Spec) {
        val spec = mutableStateOf(spec)
        val draws = AtomicInteger(0)
    }
    private data class FixturePair(val reference: Fixture, val candidate: Fixture) {
        val fixtures get() = listOf(reference, candidate)
    }

    private inner class Fixture(specs: List<Spec>, density: Float, initialDirection: LayoutDirection, shared: Boolean) {
        val host = HardwarePixelTestHost(instrumentation.targetContext, 720, 900, density)
        val cells = specs.map(::Cell)
        val direction = mutableStateOf(initialDirection)
        val generation = mutableIntStateOf(0)
        val drawnGeneration = AtomicInteger(-1)

        init {
            val configuration = Configuration(host.presentation.context.resources.configuration).apply {
                densityDpi = (density * 160).toInt()
                fontScale = 1f
            }
            host.view.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(density, 1f),
                    LocalConfiguration provides configuration,
                    LocalLayoutDirection provides direction.value,
                ) {
                    Box(Modifier.fillMaxSize().background(Color(0xFFF1F2F4)).drawWithContent {
                        drawContent()
                        drawnGeneration.set(generation.intValue)
                    }) {
                        if (shared) AlbumListIconScope { Grid(shared = true) }
                        else Grid(shared = false)
                    }
                }
            }
        }

        @Composable
        private fun Grid(shared: Boolean) {
            Column(Modifier.padding(16.dp)) {
                repeat(4) { row ->
                    Row {
                        repeat(3) { column -> DrawCell(row * 3 + column, shared) }
                    }
                }
            }
        }

        @Composable
        private fun DrawCell(index: Int, shared: Boolean) {
            val cell = cells[index]
            val spec = cell.spec.value
            val painter = if (shared) rememberAlbumListMetaIconPainter(resources[index % 3])
                else painterResource(resources[index % 3])
            Box(Modifier.size(56.dp).graphicsLayer().drawWithContent {
                cell.draws.incrementAndGet()
                drawContent()
            }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(spec.sizeDp.dp).paint(
                    painter = painter, sizeToIntrinsics = false,
                    alignment = Alignment.Center, contentScale = ContentScale.Fit,
                    alpha = spec.alpha, colorFilter = ColorFilter.tint(spec.color, spec.blend),
                ))
            }
        }
    }

    private fun withPair(specs: List<Spec>, density: Float = 3f, direction: LayoutDirection = LayoutDirection.Ltr,
                         block: (FixturePair) -> Unit) {
        check(instrumentation.targetContext.applicationContext.javaClass == Application::class.java) {
            "Use SharedAlbumIconPainterTestRunner; business application must never start"
        }
        lateinit var pair: FixturePair
        instrumentation.runOnMainSync {
            pair = FixturePair(Fixture(specs, density, direction, false), Fixture(specs, density, direction, true))
            pair.fixtures.forEach { it.host.show() }
        }
        try { block(pair) }
        finally { instrumentation.runOnMainSync { pair.fixtures.forEach { it.host.close() } } }
    }

    private fun counts(pair: FixturePair, indices: List<Int>): List<Int> =
        pair.fixtures.flatMap { fixture -> indices.map { fixture.cells[it].draws.get() } }

    private fun compareSettled(pair: FixturePair, label: String): Bitmap {
        val expected = pair.reference.host.captureSettled()
        val actual = pair.candidate.host.captureSettled()
        try { compare(label, expected, actual) } finally { actual.recycle() }
        return expected
    }

    private fun changeAndCompare(pair: FixturePair, label: String, update: (Fixture) -> Unit): Bitmap {
        val committed = CountDownLatch(2)
        instrumentation.runOnMainSync {
            pair.fixtures.forEach { fixture ->
                fixture.host.view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                update(fixture)
                fixture.generation.intValue++
                fixture.host.view.invalidate()
            }
            Snapshot.sendApplyNotifications()
        }
        check(committed.await(5, TimeUnit.SECONDS)) { "No changed hardware frame for both independent owners" }
        pair.fixtures.forEach {
            assertEquals("Committed root must contain requested generation", it.generation.intValue, it.drawnGeneration.get())
        }
        // No extra invalidation after the changed commit: retain untouched child display lists.
        val expected = copyLatest(pair.reference)
        val actual = copyLatest(pair.candidate)
        try { compare(label, expected, actual) } finally { actual.recycle() }
        return expected
    }

    private fun copyLatest(fixture: Fixture): Bitmap {
        val ready = CountDownLatch(1)
        var status = PixelCopy.ERROR_UNKNOWN
        lateinit var bitmap: Bitmap
        instrumentation.runOnMainSync {
            val view = fixture.host.view
            check(view.isHardwareAccelerated)
            val origin = IntArray(2)
            view.getLocationInWindow(origin)
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(checkNotNull(fixture.host.presentation.window),
                Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height),
                bitmap, { status = it; ready.countDown() }, Handler(Looper.getMainLooper()))
        }
        check(ready.await(5, TimeUnit.SECONDS) && status == PixelCopy.SUCCESS) { "PixelCopy status $status" }
        return bitmap
    }

    private fun compare(label: String, expected: Bitmap, actual: Bitmap) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        if (!expected.sameAs(actual)) {
            val context = instrumentation.targetContext
            val folder = File(context.externalCacheDir ?: context.cacheDir, "shared-icon-pixels-$label").apply { mkdirs() }
            File(folder, "reference.png").outputStream().use { expected.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(folder, "candidate.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
            instrumentation.sendStatus(2, Bundle().apply { putString("sharedAlbumIconArtifacts", folder.absolutePath) })
        }
        assertTrue("Exact independent-owner pixels differ: $label", expected.sameAs(actual))
    }
}
