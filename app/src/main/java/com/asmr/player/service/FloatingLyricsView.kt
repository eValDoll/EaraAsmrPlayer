package com.asmr.player.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import com.asmr.player.data.settings.FloatingLyricsSettings
import com.asmr.player.util.SubtitleEntry
import kotlin.math.roundToInt

internal class FloatingLyricsView(context: Context) : FrameLayout(context) {
    internal val textView = TextView(context).apply {
        setTypeface(typeface, Typeface.BOLD)
        paintFlags = paintFlags or Paint.SUBPIXEL_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
    }
    private var multilineEnabled = false
    private var currentText: String? = null
    private var currentCue: SubtitleEntry? = null
    private val scrollbarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val scrollView = object : ScrollView(context) {
        private fun canReadMore() = multilineEnabled &&
            (canScrollVertically(-1) || canScrollVertically(1))

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
            canReadMore() && super.onInterceptTouchEvent(event)

        override fun onTouchEvent(event: MotionEvent): Boolean =
            canReadMore() && super.onTouchEvent(event)
    }.apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(textView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        setOnScrollChangeListener { _, _, _, _, _ -> this@FloatingLyricsView.invalidate() }
    }

    init {
        background = null
        setPadding(dp(14), dp(10), dp(14), dp(10))
        addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    internal val maxMultilineHeight: Int
        get() = (resources.displayMetrics.heightPixels * 0.45f).roundToInt().coerceAtLeast(1)

    fun applySettings(settings: FloatingLyricsSettings, multiline: Boolean) {
        val modeChanged = multilineEnabled != multiline
        multilineEnabled = multiline
        textView.apply {
            textSize = settings.size
            val hsv = FloatArray(3)
            Color.colorToHSV(settings.color, hsv)
            val lightTheme = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK !=
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            hsv[1] = if (lightTheme) hsv[1].coerceIn(0.48f, 0.88f) else hsv[1].coerceIn(0.42f, 0.90f)
            hsv[2] = if (lightTheme) hsv[2].coerceIn(0.98f, 1f) else hsv[2].coerceIn(0.94f, 1f)
            setTextColor(Color.HSVToColor(255, hsv))
            val luminance = (0.2126f * Color.red(settings.color) +
                0.7152f * Color.green(settings.color) + 0.0722f * Color.blue(settings.color)) / 255f
            setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(),
                Color.argb(if (luminance > 0.72f) 176 else 208, 0, 0, 0))
            gravity = when (settings.align) {
                0 -> Gravity.START
                2 -> Gravity.END
                else -> Gravity.CENTER_HORIZONTAL
            }
            isSingleLine = !multiline
            maxLines = if (multiline) Int.MAX_VALUE else 1
            setHorizontallyScrolling(!multiline)
            ellipsize = if (multiline) null else TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = !multiline
        }
        scrollbarPaint.color = textView.currentTextColor
        scrollbarPaint.alpha = 140
        scrollView.setPadding(0, 0, if (multiline) dp(6) else 0, 0)
        if (modeChanged) resetScroll()
        requestLayout()
        invalidate()
    }

    fun updateLine(text: String, cue: SubtitleEntry? = null) {
        if (currentText == text && currentCue == cue) return
        currentText = text
        currentCue = cue
        textView.text = text
        resetScroll()
    }

    private fun resetScroll() {
        scrollView.scrollTo(0, 0)
        // 终止上一句的惯性滚动，避免它把新句带离句首。
        scrollView.fling(0)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightSpec = if (multilineEnabled && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            val limit = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                maxMultilineHeight
            } else {
                maxMultilineHeight.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
            }
            // 只限制上限，实际高度由换行后的文字和内边距决定。
            MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST)
        } else {
            heightMeasureSpec
        }
        super.onMeasure(widthMeasureSpec, heightSpec)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val viewport = scrollView.height
        val content = textView.height
        if (!multilineEnabled || viewport <= 0 || content <= viewport) return
        val thumb = (viewport.toFloat() * viewport / content).coerceAtLeast(dp(16).toFloat())
            .coerceAtMost(viewport.toFloat())
        val top = paddingTop + (viewport - thumb) * scrollView.scrollY / (content - viewport)
        val right = (width - paddingRight).toFloat()
        canvas.drawRoundRect(right - dp(2), top, right, top + thumb, dp(1).toFloat(), dp(1).toFloat(), scrollbarPaint)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
