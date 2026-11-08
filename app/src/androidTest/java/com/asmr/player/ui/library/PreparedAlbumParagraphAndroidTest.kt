package com.asmr.player.ui.library

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.common.HardwarePixelTestHost
import com.asmr.player.ui.library.prepared.LocalPreparedParagraphSession
import com.asmr.player.ui.library.prepared.PreparedAlbumParagraphItem
import com.asmr.player.ui.library.prepared.PreparedParagraphLabel
import com.asmr.player.ui.library.prepared.PreparedParagraphOccurrence
import com.asmr.player.ui.library.prepared.PreparedAlbumParagraphs
import com.asmr.player.ui.library.prepared.PreparedLayoutStyle
import com.asmr.player.ui.library.prepared.PreparedParagraphRequest
import com.asmr.player.ui.library.prepared.PreparedParagraphSession
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Native text lifecycle checks; no activity, business application, database or service. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalFoundationApi::class)
@SdkSuppress(minSdkVersion = 33)
class PreparedAlbumParagraphAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun preparedParagraph_isAdoptedAndMatchesMaterialTextSizeBaselinesAndPixels() {
        val fixture = createFixture()
        try {
            val reference = fixture.host.captureSettled()
            val geometry = fixture.geometry.getValue(0)
            try {
                fixture.prepareSingle()
                val before = fixture.session.stats().adopted
                instrumentation.runOnMainSync { fixture.prepared.value = true }
                val candidate = fixture.host.captureSettled()
                try {
                    assertTrue("Prepared cache must be adopted; fallback cannot pass this test",
                        fixture.session.stats().adopted > before)
                    assertTrue("Prepared text must contain visible glyphs", hasInk(candidate, Color.White))
                    assertEquals("Prepared text size and baselines", geometry, fixture.geometry.getValue(0))
                    assertTrue("Prepared text must match Material Text at the same position", reference.sameAs(candidate))
                    assertEquals("Prepared layout must remain reusable without synchronous relayout", 0L,
                        fixture.session.stats().staleAdoptions)
                } finally {
                    candidate.recycle()
                }
            } finally {
                reference.recycle()
            }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun pagePreparedParagraphs_surviveLazyReuseReturnAndSameKeyTextReplacement() {
        val fixture = createFixture(list = true)
        var original: Bitmap? = null
        try {
            original = fixture.host.captureSettled()
            assertTrue("Initial rows must contain text", hasInk(original, Color.White))
            awaitPrepared(fixture.session)
            val adoptedBefore = fixture.session.stats().adopted
            runBlocking(Dispatchers.Main) { fixture.scroll.scrollToItem(10) }
            fixture.host.captureSettled().recycle()
            assertTrue("The production ahead-window provider must supply an adopted paragraph",
                fixture.session.stats().adopted > adoptedBefore)
            assertEquals("Adopting the page-prepared layout must avoid synchronous relayout", 0L,
                fixture.session.stats().staleAdoptions)

            runBlocking(Dispatchers.Main) { fixture.scroll.scrollToItem(80) }
            val distant = fixture.host.captureSettled()
            try {
                assertTrue("Recycled distant rows must not lose text", hasInk(distant, Color.White))
                assertFalse("The distant text must differ from the first rows", original.sameAs(distant))
            } finally {
                distant.recycle()
            }
            runBlocking(Dispatchers.Main) { fixture.scroll.scrollToItem(0) }
            val restored = fixture.host.captureSettled()
            try {
                assertTrue("Returning rows must preserve all original glyphs", original.sameAs(restored))
            } finally {
                restored.recycle()
            }

            instrumentation.runOnMainSync { fixture.revision.value++ }
            val replaced = fixture.host.captureSettled()
            try {
                assertFalse("Replacing text under the same lazy key must update its glyphs", original.sameAs(replaced))
                assertMatchesReference(fixture, replaced)
            } finally {
                replaced.recycle()
            }
            assertEquals("Background preparation must not fail", 0L, fixture.session.stats().failures)
        } finally {
            original?.recycle()
            fixture.close()
        }
    }

    @Test
    fun changedThemeFontScaleDirectionAndText_renderSynchronouslyWithoutWarmup() {
        val fixture = createFixture()
        try {
            fixture.host.captureSettled().recycle()
            fixture.prepareSingle()
            instrumentation.runOnMainSync { fixture.prepared.value = true }
            fixture.host.captureSettled().recycle()
            assertTrue("Environment regression must start from an adopted paragraph", fixture.session.stats().adopted > 0)
            val adoptedBefore = fixture.session.stats().adopted
            instrumentation.runOnMainSync {
                fixture.environment.value = Environment(dark = true, fontScale = 1.4f, direction = LayoutDirection.Rtl)
                fixture.singleText.value = "更新声优・雨音 العربية 🌙 café"
            }
            val changed = fixture.host.captureSettled()
            try {
                assertTrue("Unprepared changed text must render immediately", hasInk(changed, Color.Black))
                assertEquals("An old prepared key must not be adopted for changed inputs", adoptedBefore,
                    fixture.session.stats().adopted)
                assertMatchesReference(fixture, changed)
            } finally {
                changed.recycle()
            }
        } finally {
            fixture.close()
        }
    }

    private fun createFixture(list: Boolean = false): Fixture {
        check(instrumentation.targetContext.applicationContext.javaClass == Application::class.java) {
            "Use PreparedAlbumParagraphTestRunner; AsmrApp must not be initialized"
        }
        lateinit var fixture: Fixture
        instrumentation.runOnMainSync {
            fixture = Fixture(list)
            fixture.host.show()
        }
        return fixture
    }

    private fun assertMatchesReference(fixture: Fixture, candidate: Bitmap) {
        val geometry = fixture.geometry.toMap()
        instrumentation.runOnMainSync { fixture.prepared.value = false }
        val reference = fixture.host.captureSettled()
        try {
            assertTrue("Updated text must match current Material Text pixels", candidate.sameAs(reference))
            assertEquals("Updated text size and baselines must match", geometry, fixture.geometry.toMap())
        } finally {
            reference.recycle()
        }
    }

    private fun awaitPrepared(session: PreparedParagraphSession) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val stats = session.stats()
            assertEquals("Background paragraph preparation failed", 0L, stats.failures)
            if (stats.ready > 0 && stats.pending == 0 && !stats.running) return
            SystemClock.sleep(10)
        }
        error("No completed prepared paragraph within the bounded wait: ${session.stats()}")
    }

    private fun hasInk(bitmap: Bitmap, background: Color): Boolean {
        val backgroundArgb = if (background == Color.White) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { it != backgroundArgb } > 20
    }

    private data class Environment(
        val dark: Boolean = false,
        val fontScale: Float = 1f,
        val direction: LayoutDirection = LayoutDirection.Ltr,
    )

    private data class Geometry(val size: IntSize, val firstBaseline: Int, val lastBaseline: Int)

    private inner class Fixture(private val list: Boolean) {
        val host = HardwarePixelTestHost(instrumentation.targetContext, 480, 320, 1f)
        val prepared = mutableStateOf(list)
        val environment = mutableStateOf(Environment())
        val singleText = mutableStateOf("声优・耳语 🎧 office العربية")
        val revision = mutableStateOf(0)
        val scroll = LazyListState()
        val geometry = ConcurrentHashMap<Int, Geometry>()
        private val ownedSession = if (list) null else PreparedParagraphSession.create(instrumentation.targetContext)
        lateinit var session: PreparedParagraphSession
        private lateinit var singleStyle: TextStyle

        init {
            host.view.setContent {
                val env = environment.value
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, env.fontScale),
                    LocalLayoutDirection provides env.direction,
                ) {
                    MaterialTheme(colorScheme = if (env.dark) darkColorScheme() else lightColorScheme()) {
                        Box(Modifier.fillMaxSize().background(if (env.dark) Color.Black else Color.White)) {
                            if (list) {
                                val albums = List(100) { index ->
                                    Album(id = index.toLong(), title = "专辑 $index", path = "",
                                        tags = listOf(if (index == 0 && revision.value > 0) "更新雨声" else "标签 $index"))
                                }
                                PreparedAlbumParagraphs(
                                    listState = scroll,
                                    active = true,
                                    dataKey = revision.value,
                                    itemAtLazyIndex = { index -> albums.getOrNull(index)?.let { PreparedAlbumParagraphItem(it.id, it) } },
                                ) {
                                    val current = checkNotNull(LocalPreparedParagraphSession.current)
                                    SideEffect { session = current }
                                    LazyColumn(Modifier.width(260.dp).height(240.dp), state = scroll) {
                                        items(albums, key = { it.id }, contentType = { "prepared-text" }) { album ->
                                            Column {
                                                Row(Modifier.width(260.dp).horizontalScroll(rememberScrollState())) {
                                                    Item(album.id.toInt(), "#${album.tags.single()}",
                                                        MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium))
                                                }
                                                Spacer(Modifier.height(60.dp))
                                            }
                                        }
                                    }
                                }
                            } else {
                                val current = checkNotNull(ownedSession)
                                CompositionLocalProvider(
                                    LocalPreparedParagraphSession provides current,
                                    LocalFontFamilyResolver provides current.fontFamilyResolver,
                                ) {
                                    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    SideEffect { session = current; singleStyle = style }
                                    Item(0, singleText.value, style, Modifier.width(260.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        fun prepareSingle() {
            val env = environment.value
            session.setAheadWindow(listOf(PreparedParagraphRequest(
                occurrence = PreparedParagraphOccurrence(0L, PreparedParagraphLabel.Cv, 0),
                text = singleText.value,
                layoutStyle = PreparedLayoutStyle(singleStyle),
                fontFamilyResolver = session.fontFamilyResolver,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                maxLines = 1,
                minLines = 1,
                density = 1f,
                fontScale = env.fontScale,
                layoutDirection = env.direction,
                constraints = Constraints(minWidth = 260, maxWidth = 260, maxHeight = 320),
            )))
            awaitPrepared(session)
        }

        @Composable
        private fun Item(id: Int, text: String, style: TextStyle, modifier: Modifier = Modifier) {
            val measured = modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                geometry[id] = Geometry(IntSize(placeable.width, placeable.height), placeable[FirstBaseline], placeable[LastBaseline])
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            val color = if (environment.value.dark) Color.White else Color.Black
            if (prepared.value) {
                AlbumInlineText(text, modifier = measured, style = style, color = color,
                    preparedOccurrence = PreparedParagraphOccurrence(id.toLong(),
                        if (list) PreparedParagraphLabel.Tag else PreparedParagraphLabel.Cv, 0),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(text, modifier = measured, style = style, color = color,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        fun close() {
            instrumentation.runOnMainSync {
                host.close()
                ownedSession?.close()
            }
        }
    }
}
