package com.asmr.player.service

import android.content.res.Configuration
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.asmr.player.data.settings.FloatingLyricsSettings
import com.asmr.player.util.SubtitleEntry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingLyricsViewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val longCue = SubtitleEntry(0, 1000, "这是一条需要完整阅读的悬浮字幕，自动换行后不应隐藏任何文字。".repeat(30))

    @Test
    fun heightFollowsLineCountAndCapsLongCuesWithoutTruncatingText() = onMain {
        val view = createView()
        view.updateLine("短句")
        measure(view)
        val singleLineHeight = view.height
        assertEquals(view.textView.height + view.paddingTop + view.paddingBottom, singleLineHeight)
        view.updateLine("第一行\n第二行")
        measure(view)
        assertEquals(2, view.textView.lineCount)
        assertTrue(view.height > singleLineHeight)
        assertEquals(view.textView.height + view.paddingTop + view.paddingBottom, view.height)
        view.updateLine(longCue.text, longCue)
        measure(view)
        assertEquals(view.maxMultilineHeight.coerceAtMost(1000), view.height)
        val layout = view.textView.layout
        assertTrue(layout.lineCount > 3)
        assertEquals(longCue.text.length, layout.getLineEnd(layout.lineCount - 1))
        assertEquals(0, layout.getEllipsisCount(layout.lineCount - 1))
        assertTrue(view.scrollView.canScrollVertically(1))
        assertNull(view.textView.ellipsize)
        assertFalse(view.textView.isSelected)
        view.updateLine("短句")
        measure(view)
        assertEquals(singleLineHeight, view.height)
        assertFalse(view.scrollView.canScrollVertically(1))
    }

    @Test
    fun changingWidthRecalculatesWrappedHeightForTheSameCue() = onMain {
        val view = createView()
        view.updateLine("宽度变化时，悬浮歌词需要根据实际换行重新计算高度。".repeat(2))
        measure(view, width = 640)
        val wideHeight = view.height
        measure(view, width = 360)
        assertTrue(view.height > wideHeight)
        measure(view, width = 640)
        assertEquals(wideHeight, view.height)
    }

    @Test
    fun sameCueKeepsReadingPositionAndNextIdenticalCueResetsIt() = onMain {
        val view = createView()
        view.updateLine(longCue.text, longCue)
        measure(view)
        view.scrollView.scrollTo(0, 100)
        view.updateLine(longCue.text, longCue)
        assertEquals(100, view.scrollView.scrollY)
        view.scrollView.fling(2000)
        view.updateLine(longCue.text, longCue.copy(startMs = 1000, endMs = 2000))
        view.scrollView.computeScroll()
        assertEquals(0, view.scrollView.scrollY)
    }

    @Test
    fun disablingMultilineRestoresSingleLineMarqueeAndDragging() = onMain {
        val view = createView()
        view.updateLine(longCue.text, longCue)
        measure(view)
        val multilineHeight = view.height
        view.applySettings(FloatingLyricsSettings(), multiline = false)
        measure(view)
        assertEquals(1, view.textView.lineCount)
        assertEquals(TextUtils.TruncateAt.MARQUEE, view.textView.ellipsize)
        assertTrue(view.textView.isSelected)
        assertTrue(view.height < multilineHeight)
        assertFalse(view.scrollView.canScrollVertically(1))
        var dragged = false
        view.setOnTouchListener { _, _ -> dragged = true; true }
        swipe(view, view.width / 2f, view.height / 2f)
        assertTrue(dragged)
    }

    @Test
    fun compactHeightAndLargeFontKeepAllTextScrollable() = onMain {
        val config = Configuration(instrumentation.targetContext.resources.configuration).apply { fontScale = 1.8f }
        val view = FloatingLyricsView(instrumentation.targetContext.createConfigurationContext(config))
        view.applySettings(FloatingLyricsSettings(size = 32f), multiline = true)
        view.updateLine(longCue.text, longCue)
        measure(view, maxHeight = 144)
        assertTrue(view.height <= 144)
        assertTrue(view.scrollView.canScrollVertically(1))
        assertEquals(longCue.text.length, view.textView.layout.getLineEnd(view.textView.lineCount - 1))
    }

    @Test
    fun overflowingTextScrollsWhilePaddingStillDrags() = onMain {
        val view = createView()
        view.updateLine(longCue.text, longCue)
        measure(view)
        var dragEvents = 0
        view.setOnTouchListener { _, _ -> dragEvents++; true }
        swipe(view, view.width / 2f, view.height - view.paddingBottom - 2f)
        assertTrue(view.scrollView.scrollY > 0)
        assertEquals(0, dragEvents)
        swipe(view, 1f, view.height / 2f)
        assertTrue(dragEvents > 0)
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun actualOverlaySwitchesModeWithoutRecreatingWindowAndStaysOnScreen() {
        val packageName = instrumentation.targetContext.packageName
        val previousMode = Regex("SYSTEM_ALERT_WINDOW: (\\w+)")
            .find(shell("appops get $packageName SYSTEM_ALERT_WINDOW"))?.groupValues?.get(1) ?: "default"
        var overlay: FloatingLyricsOverlay? = null
        lateinit var view: FloatingLyricsView
        var expandedHeight = 0
        try {
            shell("appops set $packageName SYSTEM_ALERT_WINDOW allow")
            onMain {
                val roots = WindowInspector.getGlobalWindowViews().toSet()
                overlay = FloatingLyricsOverlay(instrumentation.targetContext).apply {
                    applySettings(FloatingLyricsSettings(), multilineEnabled = false)
                    show()
                    updateLine(longCue.text, longCue)
                }
                view = WindowInspector.getGlobalWindowViews().filterIsInstance<FloatingLyricsView>()
                    .single { it !in roots }
            }
            instrumentation.waitForIdleSync()
            onMain {
                assertEquals(1, view.textView.lineCount)
                overlay!!.applySettings(FloatingLyricsSettings(yOffset = 2000), multilineEnabled = true)
            }
            instrumentation.waitForIdleSync()
            onMain {
                assertTrue(view.isAttachedToWindow)
                assertTrue(view.textView.lineCount > 3)
                assertEquals(view.maxMultilineHeight, view.height)
                expandedHeight = view.height
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                assertTrue(location[1] >= 0)
                assertTrue(location[1] + view.height <= view.resources.displayMetrics.heightPixels)
                overlay!!.updateLine("短句")
            }
            instrumentation.waitForIdleSync()
            onMain {
                assertTrue(view.height < expandedHeight)
                assertEquals(view.textView.height + view.paddingTop + view.paddingBottom, view.height)
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                assertEquals((view.resources.displayMetrics.heightPixels - view.height).coerceAtMost(2000), location[1])
                overlay!!.applySettings(FloatingLyricsSettings(touchable = false), multilineEnabled = true)
                val params = view.layoutParams as WindowManager.LayoutParams
                assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
                overlay!!.applySettings(FloatingLyricsSettings(), multilineEnabled = false)
            }
            instrumentation.waitForIdleSync()
            onMain {
                assertTrue(view.isAttachedToWindow)
                assertEquals(TextUtils.TruncateAt.MARQUEE, view.textView.ellipsize)
                assertEquals(1, view.textView.lineCount)
            }
        } finally {
            onMain { overlay?.hide() }
            shell("appops set $packageName SYSTEM_ALERT_WINDOW $previousMode")
        }
    }

    private fun createView() = FloatingLyricsView(instrumentation.targetContext).apply {
        applySettings(FloatingLyricsSettings(), multiline = true)
    }

    private fun measure(view: View, maxHeight: Int = 1000, width: Int = 640) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun swipe(view: View, x: Float, y: Float) {
        val start = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
            val event = MotionEvent.obtain(start, start + index * 40L, action, x, y - index * 40f, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun onMain(block: () -> Unit) {
        var result: Result<Unit>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        result!!.getOrThrow()
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
