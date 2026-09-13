package com.asmr.player.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import com.asmr.player.data.settings.FloatingLyricsSettings
import com.asmr.player.util.SubtitleEntry

class FloatingLyricsOverlay(
    private val context: Context,
    private val onSettingsChanged: (FloatingLyricsSettings) -> Unit = {}
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var container: FloatingLyricsView? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastX = 0
    private var lastY = 0
    private var downRawX = 0f
    private var downRawY = 0f
    private var currentSettings = FloatingLyricsSettings()
    private var multilineEnabled = false

    fun isShown(): Boolean = container != null

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun applySettings(settings: FloatingLyricsSettings, multilineEnabled: Boolean) {
        currentSettings = settings
        this.multilineEnabled = multilineEnabled
        val view = container ?: return
        val p = params ?: return
        view.applySettings(settings, multilineEnabled)
        p.x = settings.xOffset
        p.y = settings.yOffset
        constrainMultilinePosition(view, p)
        p.flags = if (settings.touchable) {
            p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        runCatching { windowManager.updateViewLayout(view, p) }
    }

    fun onConfigurationChanged() {
        applySettings(currentSettings, multilineEnabled)
    }

    fun show() {
        if (container != null || !canDraw()) return
        val layout = FloatingLyricsView(context).apply {
            applySettings(currentSettings, multilineEnabled)
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val initialFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            if (currentSettings.touchable) initialFlags else initialFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentSettings.xOffset
            y = currentSettings.yOffset
        }
        constrainMultilinePosition(layout, p)
        layout.setOnTouchListener { _, event ->
            if (!currentSettings.touchable) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = p.x
                    lastY = p.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = lastX + (event.rawX - downRawX).toInt()
                    p.y = lastY + (event.rawY - downRawY).toInt()
                    constrainMultilinePosition(layout, p)
                    currentSettings = currentSettings.copy(xOffset = p.x, yOffset = p.y)
                    runCatching { windowManager.updateViewLayout(layout, p) }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    currentSettings = currentSettings.copy(xOffset = p.x, yOffset = p.y)
                    onSettingsChanged(currentSettings)
                    false
                }
                else -> false
            }
        }
        params = p
        container = layout
        windowManager.addView(layout, p)
    }

    private fun constrainMultilinePosition(view: FloatingLyricsView, p: WindowManager.LayoutParams) {
        if (!multilineEnabled) return
        // 多行字幕占满可用宽度，限制位置以免整行或底部被拖出屏幕。
        p.x = 0
        p.y = p.y.coerceIn(0, (context.resources.displayMetrics.heightPixels - view.multilineHeight).coerceAtLeast(0))
    }

    fun hide() {
        val view = container ?: return
        runCatching { windowManager.removeView(view) }
        container = null
        params = null
    }

    fun updateLine(current: String, cue: SubtitleEntry? = null) {
        container?.updateLine(current, cue)
    }
}
