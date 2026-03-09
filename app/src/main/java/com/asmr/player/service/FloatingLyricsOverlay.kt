package com.asmr.player.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.asmr.player.data.settings.FloatingLyricsSettings

class FloatingLyricsOverlay(
    private val context: Context
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var container: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastX = 0
    private var lastY = 0
    private var downRawX = 0f
    private var downRawY = 0f
    private var currentSettings = FloatingLyricsSettings()

    fun isShown(): Boolean = container != null

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun applySettings(settings: FloatingLyricsSettings) {
        currentSettings = settings
        val view = container ?: return
        val p = params ?: return
        
        // 更新样式
        val layout = view as? LinearLayout ?: return
        val line1 = layout.getChildAt(0) as? TextView ?: return
        
        line1.textSize = settings.size
        line1.setTextColor(settings.color)
        
        val align = when (settings.align) {
            0 -> Gravity.START
            2 -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }
        line1.gravity = align
        
        layout.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            alpha = (settings.opacity * 255).toInt()
            setColor(0xFF000000.toInt())
        }
        
        // 更新位置
        p.y = settings.yOffset
        
        // 更新点击穿透
        if (settings.touchable) {
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        
        runCatching { windowManager.updateViewLayout(view, p) }
    }

    fun show() {
        if (container != null) return
        if (!canDraw()) return

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dp(14)
            val padV = dp(10)
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0xFF000000.toInt())
                alpha = (currentSettings.opacity * 255).toInt()
            }
        }

        val line1 = TextView(context).apply {
            id = View.generateViewId()
            setTextColor(currentSettings.color)
            textSize = currentSettings.size
            setTypeface(typeface, Typeface.BOLD)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            setHorizontallyScrolling(true)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        layout.addView(line1)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
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
            x = 0
            y = currentSettings.yOffset
        }

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
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    p.x = lastX + dx
                    p.y = lastY + dy
                    currentSettings = currentSettings.copy(yOffset = p.y)
                    runCatching { windowManager.updateViewLayout(layout, p) }
                    true
                }
                else -> false
            }
        }

        params = p
        container = layout
        windowManager.addView(layout, p)
        applySettings(currentSettings)
    }

    fun hide() {
        val view = container ?: return
        runCatching { windowManager.removeView(view) }
        container = null
        params = null
    }

    fun updateLine(current: String) {
        val view = container as? LinearLayout ?: return
        val line1 = view.getChildAt(0) as? TextView ?: return
        line1.text = current
    }

    private fun dp(value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density).toInt()
    }
}
