package com.asmr.player.service

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.settings.FloatingLyricsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w360dp-h800dp")
class FloatingLyricsOverlayTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun backgroundOpacityOnlyChangesBlackBackgroundInBothModes() {
        val view = FloatingLyricsView(context)
        for (multiline in listOf(false, true)) {
            for ((opacity, alpha) in listOf(0f to 0, 0.5f to 128, 1f to 255)) {
                view.applySettings(FloatingLyricsSettings(backgroundOpacity = opacity), multiline)
                assertEquals(Color.argb(alpha, 0, 0, 0), (view.background as ColorDrawable).color)
                assertEquals(255, Color.alpha(view.textView.currentTextColor))
                assertEquals(1f, view.alpha)
            }
        }
    }

    @Test
    fun diagonalDraggingOnlyMovesVerticalPositionInBothModes() {
        ShadowSettings.setCanDrawOverlays(true)
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        for (multiline in listOf(false, true)) {
            var saved: FloatingLyricsSettings? = null
            val overlay = FloatingLyricsOverlay(context) { saved = it }
            try {
                overlay.applySettings(FloatingLyricsSettings(yOffset = 120), multiline)
                overlay.show()
                val view = (shadowOf(windowManager) as ShadowWindowManagerImpl).views
                    .filterIsInstance<FloatingLyricsView>().single()
                view.updateLine("歌词")
                view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST))
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)
                val params = view.layoutParams as WindowManager.LayoutParams
                touch(view, MotionEvent.ACTION_DOWN, 1f, 1f)
                touch(view, MotionEvent.ACTION_MOVE, 101f, 1f)
                assertEquals(0, params.x)
                assertEquals(120, params.y)
                touch(view, MotionEvent.ACTION_MOVE, 201f, 61f)
                assertEquals(0, params.x)
                assertEquals(180, params.y)
                touch(view, MotionEvent.ACTION_UP, 201f, 61f)
                assertNotNull(saved)
                assertEquals(180, saved!!.yOffset)
                overlay.onConfigurationChanged()
                assertEquals(0, params.x)
                assertEquals(180, params.y)
            } finally {
                overlay.hide()
            }
        }
    }

    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 10L, action, x, y, 0)
        try {
            view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
