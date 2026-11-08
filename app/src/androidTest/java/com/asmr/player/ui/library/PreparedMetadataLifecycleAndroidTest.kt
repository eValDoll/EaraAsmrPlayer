package com.asmr.player.ui.library

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.asmr.player.ui.common.HardwarePixelTestHost
import com.asmr.player.ui.common.rememberSaveablePrefetchedLazyListState
import com.asmr.player.ui.library.prepared.LocalPreparedParagraphSession
import com.asmr.player.ui.library.prepared.PreparedAlbumParagraphs
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real metadata premeasurement, retained drawing and reuse; no business app or device data. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
@OptIn(ExperimentalFoundationApi::class)
class PreparedMetadataLifecycleAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun sameKeyTextThemeFontAndRtlChanges_matchAtNextCapturedCommitAndAfterSettling() {
        val fixture = createFixture()
        try {
            fixture.host.captureSettled().recycle()
            runBlocking(Dispatchers.Main) { fixture.scroll.scrollToItem(4) }
            fixture.host.captureSettled().recycle()
            instrumentation.runOnMainSync {
                fixture.revision.value++
                fixture.dark.value = true
                fixture.fontScale.value = 1.5f
                fixture.rtl.value = true
                fixture.italic.value = true
            }
            val firstCommitted = fixture.host.capture()
            val settled = fixture.host.captureSettled()
            try {
                compare(firstCommitted, settled, fixture.output, "first-commit-and-settled")
                assertMatchesOriginalPaint(fixture, firstCommitted, "changed-inputs")
            } finally {
                firstCommitted.recycle()
                settled.recycle()
            }
            instrumentation.runOnMainSync { fixture.usePrepared.value = true }
            runBlocking(Dispatchers.Main) { fixture.scroll.scrollToItem(60) }
            fixture.host.captureSettled().recycle()
            runBlocking(Dispatchers.Main) { fixture.scroll.scrollToItem(64) }
            val reused = fixture.host.captureSettled()
            try {
                assertMatchesOriginalPaint(fixture, reused, "reused-rtl-italic")
            } finally { reused.recycle() }
        } finally { fixture.close() }
    }

    @Test
    fun lazyReuseAfterAnimatedScroll_preservesMeasuredText() {
        val fixture = createFixture()
        try {
            val initial = fixture.host.captureSettled()
            try {
                runBlocking(AndroidUiDispatcher.Main) { fixture.scroll.animateScrollToItem(30) }
                val far = fixture.host.captureSettled()
                try {
                    assertTrue("Far rows must replace their text", !initial.sameAs(far))
                    assertMatchesOriginalPaint(fixture, far, "animated-far-rows")
                } finally { far.recycle() }
                runBlocking(AndroidUiDispatcher.Main) { fixture.scroll.animateScrollToItem(0) }
                val returned = fixture.host.captureSettled()
                try {
                    compare(initial, returned, fixture.output, "animated-reuse-return")
                } finally { returned.recycle() }
            } finally { initial.recycle() }
        } finally { fixture.close() }
    }

    private fun createFixture(): Fixture {
        check(instrumentation.targetContext.applicationContext.javaClass == Application::class.java) {
            "Use PreparedMetadataLifecycleTestRunner; do not initialize the business Application"
        }
        lateinit var fixture: Fixture
        instrumentation.runOnMainSync { fixture = Fixture().also { it.show() } }
        return fixture
    }

    private inner class Fixture {
        val host = HardwarePixelTestHost(instrumentation.targetContext, 768, 640, 2f)
        val usePrepared = mutableStateOf(true)
        val dark = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val italic = mutableStateOf(false)
        val fontScale = mutableStateOf(1f)
        val revision = mutableStateOf(0)
        lateinit var scroll: LazyListState
        val output = File(instrumentation.targetContext.externalCacheDir ?: instrumentation.targetContext.cacheDir,
            "prepared-metadata-lifecycle-${System.nanoTime()}").apply {
            check(mkdirs())
            instrumentation.sendStatus(2, Bundle().apply { putString("preparedMetadataArtifacts", absolutePath) })
        }

        fun show() {
            host.show()
            host.view.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(2f, fontScale.value),
                    LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    MaterialTheme(
                        colorScheme = if (dark.value) darkColorScheme() else lightColorScheme(),
                        typography = Typography(labelSmall = TextStyle(fontFamily = FontFamily.Default,
                            fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = .5.sp,
                            fontStyle = if (italic.value) FontStyle.Italic else FontStyle.Normal)),
                    ) {
                        val state = rememberSaveablePrefetchedLazyListState("prepared-metadata-lifecycle")
                        PreparedAlbumParagraphs(state, true, revision.value,
                            itemAtLazyIndex = { null }) {
                            val currentSession = checkNotNull(LocalPreparedParagraphSession.current)
                            SideEffect { scroll = state }
                            CompositionLocalProvider(LocalPreparedParagraphSession provides
                                if (usePrepared.value) currentSession else null) {
                                // The reference uses a different text node class. Recreate its
                                // modifier chain instead of reusing the candidate's coordinators.
                                key(usePrepared.value) {
                                LazyColumn(state = state, modifier = Modifier.fillMaxSize()
                                    .background(if (dark.value) Color.Black else Color.White)) {
                                    items((0 until 100).toList(), key = { it }) { index ->
                                        Column(Modifier.fillMaxWidth().height(96.dp)) {
                                            val key = "metadata-row-$index"
                                            AlbumItemCvLightweight(
                                                cvText = "声优 $index・${revision.value}、همس العربية 🎧",
                                                preparedItemKey = key, onClick = {}, onCvLongClick = {},
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            AlbumItemTagsLightweight(
                                                tags = listOf("耳かき $index・${revision.value}", "fj Ágấ 🌙"),
                                                preparedItemKey = key, onClick = {}, onTagLongClick = {},
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

        fun close() = instrumentation.runOnMainSync { host.close() }
    }

    private fun assertMatchesOriginalPaint(fixture: Fixture, candidate: Bitmap, name: String) {
        instrumentation.runOnMainSync { fixture.usePrepared.value = false }
        val reference = fixture.host.captureSettled()
        try {
            compare(reference, candidate, fixture.output, name)
            val pixels = IntArray(candidate.width * candidate.height)
            candidate.getPixels(pixels, 0, candidate.width, 0, 0, candidate.width, candidate.height)
            val background = if (fixture.dark.value) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            assertTrue("Metadata must retain visible glyphs", pixels.count { it != background } > 400)
        } finally { reference.recycle() }
    }

    private fun compare(reference: Bitmap, candidate: Bitmap, output: File, name: String) {
        assertEquals(reference.width, candidate.width)
        assertEquals(reference.height, candidate.height)
        val a = IntArray(reference.width * reference.height)
        val b = IntArray(a.size)
        reference.getPixels(a, 0, reference.width, 0, 0, reference.width, reference.height)
        candidate.getPixels(b, 0, candidate.width, 0, 0, candidate.width, candidate.height)
        var maxDelta = 0
        var aboveTwo = 0
        for (index in a.indices) {
            var delta = 0
            for (shift in intArrayOf(0, 8, 16, 24)) delta = max(delta,
                abs((a[index] ushr shift and 255) - (b[index] ushr shift and 255)))
            maxDelta = max(maxDelta, delta)
            if (delta > 2) aboveTwo++
        }
        File(output, "$name.json").writeText(JSONObject().put("maxChannelDelta", maxDelta)
            .put("pixelsAboveTwo", aboveTwo).toString(2), Charsets.UTF_8)
        File(output, "$name-reference.png").outputStream().use { reference.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(output, "$name-candidate.png").outputStream().use { candidate.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertEquals("$name: cropped/stale/missing production text, maxDelta=$maxDelta", 0, aboveTwo)
    }
}
