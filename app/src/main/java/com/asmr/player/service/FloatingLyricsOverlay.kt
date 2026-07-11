package com.asmr.player.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Color as AndroidColor
import android.graphics.Paint
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
import kotlin.math.roundToInt

class FloatingLyricsOverlay(
    private val context: Context,
    private val onSettingsChanged: (FloatingLyricsSettings) -> Unit = {}
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
        line1.setTextColor(adjustTextColorForReadability(settings.color))
        line1.setShadowLayer(
            dp(4).toFloat(),
            0f,
            dp(1).toFloat(),
            AndroidColor.argb(
                if (isLightColor(settings.color)) 176 else 208,
                0,
                0,
                0
            )
        )
        
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
        
        // 始终应用 settings 中的位置，确保设置页滑条和已持久化拖拽位置都能立即生效。
        p.x = settings.xOffset
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
            setTextColor(adjustTextColorForReadability(currentSettings.color))
            textSize = currentSettings.size
            setTypeface(typeface, Typeface.BOLD)
            paintFlags = paintFlags or Paint.SUBPIXEL_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
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

    private fun adjustTextColorForReadability(color: Int): Int {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(color, hsv)
        hsv[1] = if (isLightThemeApprox()) {
            hsv[1].coerceIn(0.48f, 0.88f)
        } else {
            hsv[1].coerceIn(0.42f, 0.90f)
        }
        hsv[2] = if (isLightThemeApprox()) {
            hsv[2].coerceIn(0.98f, 1.0f)
        } else {
            hsv[2].coerceIn(0.94f, 1.0f)
        }
        return AndroidColor.HSVToColor(255, hsv)
    }

    private fun isLightColor(color: Int): Boolean {
        val luminance = (
            0.2126f * AndroidColor.red(color) +
                0.7152f * AndroidColor.green(color) +
                0.0722f * AndroidColor.blue(color)
            ) / 255f
        return luminance > 0.72f
    }

    private fun isLightThemeApprox(): Boolean {
        val nightMask = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightMask != android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
