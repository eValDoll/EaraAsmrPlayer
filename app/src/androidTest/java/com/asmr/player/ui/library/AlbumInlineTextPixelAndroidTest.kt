package com.asmr.player.ui.library

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.asmr.player.ui.common.HardwarePixelTestHost
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
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Same-frame native rendering, including the actual production text node and platform ripple. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class AlbumInlineTextPixelAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun inlineText_matchesMaterialTextAtNativePixelsAcrossEnvironmentsAndUpdates() {
        val output = outputDirectory("environments")
        val results = JSONArray()
        for ((density, fontScale) in listOf(1f to 1f, 3f to 1f, 1.25f to 1.5f)) {
            for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                    val name = "${mode.name}-$density-font$fontScale-${direction.name}"
                    val fixture = fixture(density, fontScale, mode, direction)
                    try {
                        instrumentation.runOnMainSync {
                            fixture.implementation.value = Implementation.Reference
                            fixture.transform.value = Transform(.75f, -.25f, 1.025f, 1.0125f)
                        }
                        compare(fixture, output, "$name-reference-position-calibration", results,
                            referencePositionCalibration = true)
                        for ((index, transform) in listOf(
                            Transform(), Transform(.25f, .75f), Transform(.5f, .25f),
                            Transform(.75f, -.25f, 1.025f, 1.0125f),
                        ).withIndex()) {
                            instrumentation.runOnMainSync { fixture.transform.value = transform }
                            compare(fixture, output, "$name-transform$index", results, samePosition = true)
                        }
                        instrumentation.runOnMainSync {
                            fixture.updated.value = true
                            fixture.prominent.value = true
                        }
                        compare(fixture, output, "$name-text-color-font-update", results, samePosition = true)
                        runBlocking(Dispatchers.Main) { fixture.scroll.forEach { it.scrollTo(it.maxValue / 2) } }
                        compare(fixture, output, "$name-scrolled-middle", results, samePosition = true)
                        runBlocking(Dispatchers.Main) { fixture.scroll.forEach { it.scrollTo(it.maxValue) } }
                        compare(fixture, output, "$name-scrolled-end", results, samePosition = true)
                        runBlocking(Dispatchers.Main) { fixture.scroll.forEach { it.scrollTo(0) } }
                        instrumentation.runOnMainSync {
                            fixture.updated.value = false
                            fixture.prominent.value = false
                            fixture.transform.value = Transform()
                        }
                        compare(fixture, output, "$name-restored", results, samePosition = true)
                    } finally {
                        instrumentation.runOnMainSync { fixture.host.close() }
                    }
                }
            }
        }
        finish(output, results)
    }

    @Test
    fun inlineText_platformRippleMatchesDuringPressReleaseCancelAndRedraw() {
        val output = outputDirectory("ripple")
        val results = JSONArray()
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            val fixture = fixture(3f, 1f, mode, LayoutDirection.Ltr)
            try {
                val before = compare(fixture, output, "${mode.name}-idle", results, retainReference = true)!!
                var changedDuringPress = false
                val press = PressInteraction.Press(Offset(24f, 20f))
                val startedAt = SystemClock.uptimeMillis()
                emitOnFrame(fixture, press) {
                    fixture.checkRippleClock = true
                }
                repeat(12) { index ->
                    val reference = compare(fixture, output, "${mode.name}-press$index", results,
                        settled = false, retainReference = true, elapsedMs = SystemClock.uptimeMillis() - startedAt)!!
                    changedDuringPress = changedDuringPress || !before.sameAs(reference)
                    reference.recycle()
                }
                compare(fixture, output, "${mode.name}-pressed-settled", results)
                assertTrue("The fixture must draw an actual platform ripple", changedDuringPress)
                emitOnFrame(fixture, PressInteraction.Release(press)) {
                    fixture.updated.value = true
                }
                repeat(8) { index -> compare(fixture, output, "${mode.name}-release$index", results, settled = false) }
                compare(fixture, output, "${mode.name}-release-settled", results)
                val cancelPress = PressInteraction.Press(Offset(8f, 8f))
                emitOnFrame(fixture, cancelPress)
                repeat(4) { index -> compare(fixture, output, "${mode.name}-cancel-press$index", results, settled = false) }
                emitOnFrame(fixture, PressInteraction.Cancel(cancelPress)) {
                    fixture.updated.value = false
                }
                repeat(8) { index -> compare(fixture, output, "${mode.name}-cancel$index", results, settled = false) }
                val restored = compare(fixture, output, "${mode.name}-restored", results, retainReference = true)!!
                try {
                    assertTrue("Press/release/cancel must restore the original pixels", before.sameAs(restored))
                } finally {
                    before.recycle()
                    restored.recycle()
                }
            } finally {
                instrumentation.runOnMainSync { fixture.host.close() }
            }
        }
        finish(output, results)
    }

    @Test
    fun inlineText_referenceOnlyRippleControlAtTwoPositions() {
        val output = outputDirectory("ripple-reference-control")
        val results = JSONArray()
        val fixture = fixture(3f, 1f, ThemeMode.Light, LayoutDirection.Ltr)
        var before: Bitmap? = null
        try {
            instrumentation.runOnMainSync { fixture.implementation.value = Implementation.Reference }
            val idle = compare(fixture, output, "Light-reference-control-idle", results,
                retainReference = true, referenceOnlyDynamicControl = true)!!
            before = idle
            var changedDuringPress = false
            val press = PressInteraction.Press(Offset(24f, 20f))
            val startedAt = SystemClock.uptimeMillis()
            emitOnFrame(fixture, press) { fixture.checkRippleClock = true }
            repeat(12) { index ->
                val reference = compare(fixture, output, "Light-reference-control-press$index", results,
                    settled = false, retainReference = true,
                    elapsedMs = SystemClock.uptimeMillis() - startedAt,
                    referenceOnlyDynamicControl = true)!!
                try {
                    changedDuringPress = changedDuringPress || !idle.sameAs(reference)
                } finally {
                    reference.recycle()
                }
            }
            assertTrue("Reference control must draw an actual platform ripple", changedDuringPress)
            finish(output, results)
        } finally {
            before?.recycle()
            instrumentation.runOnMainSync { fixture.host.close() }
        }
    }

    private fun outputDirectory(suffix: String): File {
        val app = instrumentation.targetContext.applicationContext
        check(app.javaClass == Application::class.java) { "Use AlbumInlineTextPixelTestRunner" }
        return File(app.externalCacheDir ?: app.cacheDir, "album-inline-text-$suffix-${System.nanoTime()}").apply {
            check(mkdirs())
            instrumentation.sendStatus(2, Bundle().apply { putString("albumInlineTextArtifacts", absolutePath) })
        }
    }

    private fun fixture(density: Float, fontScale: Float, mode: ThemeMode, direction: LayoutDirection): Fixture {
        lateinit var fixture: Fixture
        instrumentation.runOnMainSync {
            fixture = Fixture(instrumentation.targetContext, density, fontScale, mode, direction)
            fixture.host.show()
        }
        return fixture
    }

    private fun emitOnFrame(fixture: Fixture, interaction: Interaction, update: () -> Unit = {}) {
        val delivered = CountDownLatch(1)
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
                try {
                    update()
                    fixture.interactionFrameTimeNanos = frameTimeNanos
                    fixture.interactionAnimationTimeMs = AnimationUtils.currentAnimationTimeMillis()
                    check(fixture.interactions.tryEmit(interaction))
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    delivered.countDown()
                }
            }
        }
        check(delivered.await(5, TimeUnit.SECONDS)) { "No frame for the shared native interaction" }
        failure?.let { throw it }
    }

    private fun compare(
        fixture: Fixture,
        output: File,
        name: String,
        results: JSONArray,
        settled: Boolean = true,
        retainReference: Boolean = false,
        elapsedMs: Long? = null,
        samePosition: Boolean = false,
        referencePositionCalibration: Boolean = false,
        referenceOnlyDynamicControl: Boolean = false,
    ): Bitmap? {
        if (referenceOnlyDynamicControl) {
            check(!samePosition && !referencePositionCalibration)
            instrumentation.runOnMainSync {
                check(fixture.implementation.value == Implementation.Reference)
            }
        }
        lateinit var reference: Bitmap
        lateinit var candidate: Bitmap
        var referenceGeometry: List<Map<Int, Geometry>>? = null
        if (samePosition) {
            check(settled && !fixture.checkRippleClock)
            instrumentation.runOnMainSync { fixture.implementation.value = Implementation.Reference }
            reference = fixture.host.captureSettled()
            instrumentation.runOnMainSync {
                referenceGeometry = fixture.geometry.map { it.toMap() }
                fixture.implementation.value = Implementation.Candidate
            }
            candidate = fixture.host.captureSettled()
        } else {
            val full = if (settled) fixture.host.captureSettled() else fixture.host.capture()
            assertEquals("Virtual display must not scale the fixture", fixture.host.width, full.width)
            assertEquals("Both complete panels must fit the virtual display", fixture.panelHeight * 2, full.height)
            reference = Bitmap.createBitmap(full, 0, 0, full.width, fixture.panelHeight)
            candidate = Bitmap.createBitmap(full, 0, fixture.panelHeight, full.width, fixture.panelHeight)
            full.recycle()
        }
        var retained = false
        try {
            var rippleClocks = emptyList<Long>()
            instrumentation.runOnMainSync {
                assertEquals("Both implementations must expose all real text layouts", setOf(0, 1, 2), fixture.geometry[0].keys)
                assertEquals("Sizes and baselines must remain identical", fixture.geometry[0], fixture.geometry[1])
                referenceGeometry?.let {
                    assertEquals("Sequential native sizes and baselines must remain identical", it, fixture.geometry)
                }
                assertEquals("Horizontal scroll geometry must remain identical", fixture.scroll[0].maxValue, fixture.scroll[1].maxValue)
                assertTrue("The fixture must exercise horizontal clipping and fading", fixture.scroll[0].maxValue > 0)
                if (fixture.checkRippleClock) rippleClocks = fixture.host.view.rippleStateChangeTimes()
            }
            val before = IntArray(reference.width * reference.height)
            val after = IntArray(before.size)
            reference.getPixels(before, 0, reference.width, 0, 0, reference.width, reference.height)
            candidate.getPixels(after, 0, candidate.width, 0, 0, candidate.width, candidate.height)
            var maximum = 0
            var changed = 0
            for (i in before.indices) {
                if (before[i] != after[i]) changed++
                for (shift in 0..24 step 8) {
                    maximum = max(maximum, abs(((before[i] ushr shift) and 255) - ((after[i] ushr shift) and 255)))
                }
            }
            val result = JSONObject().put("fixture", name).put("maxChannelDifference", maximum)
                .put("pixelsDifferent", changed).put("requiredMaxChannelDifference", if (referencePositionCalibration) JSONObject.NULL else 0)
                .put("referenceOnlyPositionCalibration", referencePositionCalibration)
                .put("referenceOnlyDynamicControl", referenceOnlyDynamicControl)
                .put("samePhysicalPosition", samePosition)
                .put("sameHardwareFrame", !samePosition).put("sharedInteractionSource", true)
                .put("geometry", fixture.geometry[0].toString())
            if (fixture.checkRippleClock) result.put("nativeRippleStateChangeTimesMs", JSONArray(rippleClocks))
                .put("interactionFrameTimeNanos", fixture.interactionFrameTimeNanos)
                .put("interactionAnimationTimeMs", fixture.interactionAnimationTimeMs)
            if (elapsedMs != null) result.put("elapsedSincePressMs", elapsedMs)
            results.put(result)
            File(output, "pixels.json").writeText(JSONObject().put("results", results).toString(2), Charsets.UTF_8)
            instrumentation.sendStatus(2, Bundle().apply { putString("albumInlineTextPixels", result.toString()) })
            if (maximum != 0 || name.endsWith("restored") || name.endsWith("idle")) {
                for ((suffix, bitmap) in listOf("reference" to reference, "candidate" to candidate)) {
                    File(output, "$name-$suffix.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
            }
            if (fixture.checkRippleClock) {
                assertEquals("Both native ripple hosts must receive the shared interaction", 2, rippleClocks.size)
                assertEquals("Fixture clock mismatch; this is not a text pixel regression", 1, rippleClocks.distinct().size)
            }
            if (!referencePositionCalibration) assertEquals("Native pixels differ for $name; see $output", 0, maximum)
            retained = retainReference
            return if (retainReference) reference else null
        } finally {
            candidate.recycle()
            if (!retained) reference.recycle()
        }
    }

    private fun finish(output: File, results: JSONArray) {
        File(output, "pixels.json").writeText(JSONObject().put("results", results).put("passed", true).toString(2), Charsets.UTF_8)
    }

    private data class Transform(val x: Float = 0f, val y: Float = 0f, val scaleX: Float = 1f, val scaleY: Float = 1f)
    private data class Geometry(val size: IntSize, val firstBaseline: Int, val lastBaseline: Int)
    private enum class Implementation { Pair, Reference, Candidate }

    private class Fixture(context: Context, density: Float, fontScale: Float, mode: ThemeMode, direction: LayoutDirection) {
        val panelHeight = (220 * density).roundToInt()
        val host = HardwarePixelTestHost(context, (424 * density).roundToInt(), panelHeight * 2, density)
        val transform = mutableStateOf(Transform())
        val updated = mutableStateOf(false)
        val prominent = mutableStateOf(false)
        val scroll = List(2) { ScrollState(0) }
        val interactions = FrameInteractionSource()
        val geometry = List(2) { mutableMapOf<Int, Geometry>() }
        var checkRippleClock = false
        var interactionFrameTimeNanos = 0L
        var interactionAnimationTimeMs = 0L
        val implementation = mutableStateOf(Implementation.Pair)

        init {
            val configuration = Configuration(host.presentation.context.resources.configuration).apply {
                screenWidthDp = 424
                this.fontScale = fontScale
            }
            host.view.setContent {
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale), LocalConfiguration provides configuration,
                    LocalLayoutDirection provides direction) {
                    AsmrPlayerTheme(mode = mode) {
                        Layout(content = { Panel(0); Panel(1) }) { measurables, constraints ->
                            val panels = measurables.map { it.measure(Constraints.fixed(constraints.maxWidth, panelHeight)) }
                            layout(constraints.maxWidth, panelHeight * 2) {
                                panels.forEachIndexed { index, panel -> panel.place(0, index * panelHeight) }
                            }
                        }
                    }
                }
            }
        }

        @Composable
        private fun Panel(index: Int) {
            Column(Modifier.fillMaxSize().background(AsmrTheme.colorScheme.background).padding(24.dp)
                .graphicsLayer {
                    val value = transform.value
                    translationX = value.x
                    translationY = value.y
                    scaleX = value.scaleX
                    scaleY = value.scaleY
                }, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Inline(index, 0, if (updated.value) "#雨音 🎧 café ffi 𠮷" else "#耳かき 🎧 office 眠り")
                Inline(index, 1, if (updated.value) "声优更新・長い声優名 ABC العربية 🌙🌿" else "CV・白昼の夢と長い声優名 ffi العربية 🎧", Modifier.width(180.dp))
                Row(Modifier.width(300.dp).fixtureHorizontalFade(scroll[index]).horizontalScroll(scroll[index])) {
                    Inline(index, 2, if (updated.value) "#更新されたタグ・音楽 🌙 العربية 0123456789 abcdefghijklmnopqrstuvwxyz" else "#双耳・近距离耳语・binaural whisper・حكاية ليلية 🎧 0123456789 abcdefghijklmnopqrstuvwxyz")
                }
            }
        }

        @OptIn(ExperimentalFoundationApi::class)
        @Composable
        private fun Inline(index: Int, id: Int, text: String, modifier: Modifier = Modifier) {
            val common = modifier.onGloballyPositioned {
                geometry[index][id] = Geometry(it.size, it[FirstBaseline], it[LastBaseline])
            }.clip(RoundedCornerShape(4.dp)).combinedClickable(
                interactionSource = if (id == 0) interactions else null,
                indication = LocalIndication.current,
                onClick = {}, onLongClick = {},
            ).padding(horizontal = 2.dp, vertical = 2.dp)
            val style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (prominent.value) 13.sp else 12.sp,
                fontWeight = if (prominent.value) FontWeight.SemiBold else FontWeight.Medium,
            )
            val color = if (updated.value) AsmrTheme.colorScheme.textPrimary else AsmrTheme.colorScheme.textSecondary
            val candidate = when (implementation.value) {
                Implementation.Pair -> index == 1
                Implementation.Reference -> false
                Implementation.Candidate -> true
            }
            if (!candidate) {
                Text(text, modifier = common, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                // No onTextLayout: it would select AlbumInlineText's Material Text fallback.
                AlbumInlineText(text, modifier = common, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Deliver each platform ripple collector inline while Choreographer holds the animation clock. */
@OptIn(InternalCoroutinesApi::class)
private class FrameInteractionSource : MutableInteractionSource {
    private val delegate = MutableInteractionSource()
    override val interactions: Flow<Interaction> = object : Flow<Interaction> {
        override suspend fun collect(collector: FlowCollector<Interaction>) {
            // The normal AndroidUiDispatcher may resume two collectors in different wall-clock
            // milliseconds. Only the test input delivery changes; both native ripple nodes remain real.
            withContext(Dispatchers.Main.immediate) { delegate.interactions.collect(collector) }
        }
    }

    override suspend fun emit(interaction: Interaction) = delegate.emit(interaction)
    override fun tryEmit(interaction: Interaction): Boolean = delegate.tryEmit(interaction)
}

/** Inspect the pinned Compose ripple host, not hidden Android framework fields. */
private fun View.rippleStateChangeTimes(): List<Long> {
    val times = mutableListOf<Long>()
    fun visit(view: View) {
        if (view.javaClass.name == "androidx.compose.material.ripple.RippleHostView") {
            val field = view.javaClass.getDeclaredField("lastRippleStateChangeTimeMillis").apply { isAccessible = true }
            (field.get(view) as? Long)?.let(times::add)
        }
        if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
    }
    visit(this)
    return times
}

/** Same mask/order as AlbumMetaLightweight.horizontalScrollEdgeFade; the production helper is private. */
private fun Modifier.fixtureHorizontalFade(scroll: ScrollState): Modifier = graphicsLayer {
    compositingStrategy = if (scroll.maxValue > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
}.drawWithCache {
    val width = 18.dp.toPx().coerceAtMost(size.width / 3f)
    val left = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = width)
    val right = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - width, endX = size.width)
    onDrawWithContent {
        drawContent()
        if (scroll.value > 0) drawRect(left, size = Size(width, size.height), blendMode = BlendMode.DstIn)
        if (scroll.value < scroll.maxValue) drawRect(right, topLeft = Offset(size.width - width, 0f),
            size = Size(width, size.height), blendMode = BlendMode.DstIn)
    }
}
