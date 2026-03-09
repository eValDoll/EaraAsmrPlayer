package com.asmr.player.ui.player

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import androidx.media3.common.util.UnstableApi
import com.asmr.player.playback.StereoSpectrumBus
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random
import java.util.Arrays

@UnstableApi
class ChannelSpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Channel {
        Left,
        Right
    }

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var alpha: Float,
        var size: Float,
        var life: Float,
        var maxLife: Float,
        val color: Int
    )

    private val sourceBins = FloatArray(StereoSpectrumBus.DefaultBinCount) // 原始频谱 bins（来自 StereoSpectrumBus），长度固定（默认 128）
    private var barCount: Int = 64 // 实际绘制的柱子数量（会对 sourceBins 下采样/重排到这个数量）
    private var targetBins: FloatArray = FloatArray(barCount) // 当前帧处理后的目标能量（下采样/空间平滑/拟合之后、未做时间包络）
    private var envBins: FloatArray = FloatArray(barCount) // 时间包络后的显示能量（attack/release + 量化/死区后），最终用于绘制宽度
    private var envRawBins: FloatArray = FloatArray(barCount)
    private var freqWeight: FloatArray = FloatArray(barCount) // 频段权重：用来突出低频鼓点/人声、并抑制高频噪声
    private var energyBins: FloatArray = FloatArray(barCount) // 重处理时缓存的能量（= targetBins * freqWeight），用于 Min-Max Scaling
    private var baseBins: FloatArray = FloatArray(barCount) // 慢速基线（背景能量）：用于“基线 + 瞬态”分离，避免密集节奏长期顶死
    private var tmp0: FloatArray = FloatArray(barCount) // 空间平滑临时缓冲（5 点加权平均那段）
    private var tmp1: FloatArray = FloatArray(barCount) // SG/3 点平滑的临时缓冲
    private val sgWindow: Int = 9 // Savitzky–Golay 窗口大小（奇数），越大越平滑但越“糊”
    private val sgOrder: Int = 3 // Savitzky–Golay 多项式阶数，越大越能保留细节但越容易抖
    private val sgWeights: FloatArray = computeSavitzkyGolayWeights(window = sgWindow, order = sgOrder) // SG 卷积权重（最小二乘拟合求得）
    
    // Paints
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var baseColor: Int = 0xFFFFFFFF.toInt()
    private val particles = ArrayList<Particle>()
    private val random = Random(System.currentTimeMillis())

    private var channel: Channel = Channel.Left // 当前视图显示哪个声道（Left/Right）
    private var running: Boolean = false // 是否在用 Choreographer 驱动重绘
    private var lastFrameNs: Long = 0L // 上一帧绘制时间（ns），用于估算 dt
    private var frameDtNs: Long = 16_666_667L // 帧间隔（ns），用于把“每秒参数”转换成“每帧参数”
    private var lastProcessMs: Long = 0L // 上次做“重处理”(下采样/平滑/拟合/MinMax) 的时间（ms）
    private val processIntervalMs: Long = 16L // 重处理频率（ms）：16≈60fps；调大更稳但响应更慢
    private val attackTauSeconds: Float = 0.012f // attack 时间常数：上升速度，越小越“弹”（更贴鼓点）
    private val releaseTauSeconds: Float = 0.008f // release 时间常数：回落速度，越小越快（避免顶死）
    private val quantStep: Float = 0.008f // 量化台阶：把能量离散化，抑制细碎抖动；越大越稳但越“阶梯”
    private val deadband: Float = 0.01f // 死区：变化小于该值不更新，进一步抑制抖动
    private val lowEnergyCut: Float = 0.05f // 低能量截断：低于该值直接过滤为 0，去掉底噪导致的“糊一片”
    private val widthMargin: Float = 2.0f // 绘制宽度安全系数：最终宽度 = v * w * widthMargin，避免贴边/溢出
    private val stallDelta: Float = 0.012f
    private val stallDropPerSecond: Float = 0.75f
    private val silenceEnterMax: Float = 0.06f
    private val silenceExitMax: Float = 0.09f
    private var silenceActive: Boolean = false
    private val silenceExitRequiredFrames: Int = 5
    private var silenceExitStreak: Int = 0
    private var debugOverlayEnabled: Boolean = false
    private var debugMaxEnergy: Float = 0f
    private var debugAvgEnergy: Float = 0f

    private val autoRangeEnabled: Boolean = true
    private val autoRangeTauSeconds: Float = 0.16f
    private var autoRangeMin: Float = 0f
    private var autoRangeMax: Float = 1f
    private var autoScratch: FloatArray = FloatArray(barCount)

    private val choreographer = Choreographer.getInstance()
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val last = lastFrameNs
            if (last != 0L) {
                var dt = frameTimeNanos - last
                if (dt < 1_000_000L) dt = 1_000_000L
                if (dt > 50_000_000L) dt = 50_000_000L
                frameDtNs = dt
            }
            lastFrameNs = frameTimeNanos
            invalidate()
            choreographer.postFrameCallback(this)
        }
    }

    init {
        rebuildBandWeight()
        val density = resources.displayMetrics.density
        debugPaint.color = 0xFFFFFFFF.toInt()
        debugPaint.textSize = 12f * density
        debugPaint.alpha = 210
        
        // Setup Glow Paint
        glowPaint.maskFilter = BlurMaskFilter(8f * density, BlurMaskFilter.Blur.NORMAL)
        glowPaint.alpha = 100 // Lower alpha for glow
        
        isLongClickable = true
        setOnLongClickListener {
            debugOverlayEnabled = !debugOverlayEnabled
            invalidate()
            true
        }
        
        // Disable HW acceleration for glow paint if needed (BlurMaskFilter is supported in HW since API 14 but sometimes tricky)
        // actually BlurMaskFilter is supported, but let's keep it standard.
    }

    fun setChannel(channel: Channel) {
        this.channel = channel
    }

    fun setBarColor(argb: Int) {
        baseColor = argb
        updateShader()
        invalidate()
    }

    private fun updateShader() {
        if (width > 0 && height > 0) {
            // Enhanced Gradient: 3-stop gradient for more pop
            // Bottom: Base Color (Solid)
            // Middle: Base Color with 80% alpha
            // Top: Base Color with 10% alpha (faded)
            val r = Color.red(baseColor)
            val g = Color.green(baseColor)
            val b = Color.blue(baseColor)
            
            val colorBottom = Color.argb(255, r, g, b)
            val colorMiddle = Color.argb(200, r, g, b)
            val colorTop = Color.argb(30, r, g, b)
            
            val shader = LinearGradient(
                0f, height.toFloat(), 0f, 0f,
                intArrayOf(colorBottom, colorMiddle, colorTop),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
            
            paint.shader = shader
            glowPaint.shader = shader // Glow shares the same gradient
            
            // Update particle color to match (slightly lighter)
            particlePaint.color = Color.argb(255, min(255, r + 40), min(255, g + 40), min(255, b + 40))
        } else {
            paint.color = baseColor
            paint.shader = null
            glowPaint.shader = null
        }
    }


    fun setDebugOverlayEnabled(enabled: Boolean) {
        debugOverlayEnabled = enabled
        invalidate()
    }

    fun setBarCount(count: Int) {
        val clamped = count.coerceIn(8, StereoSpectrumBus.DefaultBinCount)
        if (clamped == barCount) return
        barCount = clamped
        targetBins = FloatArray(barCount)
        envBins = FloatArray(barCount)
        envRawBins = FloatArray(barCount)
        freqWeight = FloatArray(barCount)
        energyBins = FloatArray(barCount)
        baseBins = FloatArray(barCount)
        tmp0 = FloatArray(barCount)
        tmp1 = FloatArray(barCount)
        autoScratch = FloatArray(barCount)
        rebuildBandWeight()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (parent as? android.view.ViewGroup)?.clipChildren = false
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) return

        if (channel == Channel.Left) {
            StereoSpectrumBus.store.copyLatestLeft(sourceBins)
        } else {
            StereoSpectrumBus.store.copyLatestRight(sourceBins)
        }

        val nowMs = SystemClock.uptimeMillis()
        val doProcess = lastProcessMs == 0L || nowMs - lastProcessMs >= processIntervalMs
        if (doProcess) {
            val prevProcessMs = lastProcessMs
            val processDtMs = if (prevProcessMs == 0L) processIntervalMs else (nowMs - prevProcessMs).coerceAtLeast(1L)
            lastProcessMs = nowMs

            if (!StereoSpectrumBus.playbackActive) {
                silenceActive = true
                silenceExitStreak = 0
                autoRangeMin = 0f
                autoRangeMax = 1f
                for (i in 0 until energyBins.size) energyBins[i] = 0f
            } else {

            val srcCount = sourceBins.size
            val dstCount = targetBins.size
            if (dstCount == srcCount) {
                sourceBins.copyInto(targetBins)
            } else {
                val scale = srcCount.toFloat() / dstCount.toFloat()
                for (i in 0 until dstCount) {
                    val start = (i * scale).toInt()
                    val end = minOf(srcCount - 1, ((i + 1) * scale).toInt() - 1)
                    var sum = 0f
                    var count = 0
                    var k = start
                    while (k <= end) {
                        sum += sourceBins[k]
                        count++
                        k++
                    }
                    val avg = if (count == 0) 0f else (sum / count.toFloat())
                    targetBins[i] = if (avg < 0.02f) 0f else avg
                }
            }

            if (dstCount > 4) {
                tmp0[0] = targetBins[0]
                tmp0[1] = targetBins[1]
                val last = dstCount - 1
                tmp0[last] = targetBins[last]
                tmp0[last - 1] = targetBins[last - 1]
                for (i in 2 until last - 1) {
                    tmp0[i] =
                        (targetBins[i - 2] + 2f * targetBins[i - 1] + 3f * targetBins[i] + 2f * targetBins[i + 1] + targetBins[i + 2]) / 9f
                }
                tmp0.copyInto(targetBins)
            }

            if (dstCount >= sgWindow) {
                applySgSmoothingInPlace(targetBins, tmp1, sgWeights)
            } else if (dstCount > 2) {
                val last = dstCount - 1
                tmp1[0] = targetBins[0]
                tmp1[last] = targetBins[last]
                for (i in 1 until last) {
                    tmp1[i] = (targetBins[i - 1] + 2f * targetBins[i] + targetBins[i + 1]) * 0.25f
                }
                tmp1.copyInto(targetBins)
            }

            var maxV = 0f
            var sumV = 0f
            for (i in 0 until dstCount) {
                val v = (targetBins[i] * freqWeight[i]).coerceIn(0f, 1f)
                energyBins[i] = v
                if (v > maxV) maxV = v
                sumV += v
            }
            silenceActive = if (silenceActive) {
                if (maxV > silenceExitMax) {
                    silenceExitStreak++
                } else {
                    silenceExitStreak = 0
                }
                silenceExitStreak < silenceExitRequiredFrames
            } else {
                silenceExitStreak = 0
                maxV < silenceEnterMax
            }

            if (silenceActive) {
                autoRangeMin = 0f
                autoRangeMax = 1f
                for (i in 0 until dstCount) energyBins[i] = 0f
            } else if (autoRangeEnabled && dstCount > 0) {
                val n = min(dstCount, autoScratch.size)
                for (i in 0 until n) autoScratch[i] = energyBins[i]
                Arrays.sort(autoScratch, 0, n)
                val idx10 = ((n - 1) * 0.10f).toInt().coerceIn(0, n - 1)
                val idx95 = ((n - 1) * 0.95f).toInt().coerceIn(0, n - 1)
                val p10 = autoScratch[idx10]
                val p95 = autoScratch[idx95]

                val targetMin = (p10 - 0.02f).coerceIn(0f, 0.6f)
                val targetMax = max(p95 + 0.03f, targetMin + 0.10f).coerceIn(0.12f, 1f)
                val dtSec = (processDtMs.toFloat() / 1000f).coerceIn(0.001f, 0.2f)
                val a = (1.0 - exp((-dtSec / autoRangeTauSeconds).toDouble())).toFloat().coerceIn(0f, 1f)

                if (autoRangeMax <= autoRangeMin || prevProcessMs == 0L) {
                    autoRangeMin = targetMin
                    autoRangeMax = targetMax
                } else {
                    autoRangeMin += (targetMin - autoRangeMin) * a
                    autoRangeMax += (targetMax - autoRangeMax) * a
                }

                val denom = max(0.08f, autoRangeMax - autoRangeMin)
                for (i in 0 until dstCount) {
                    val scaled = ((energyBins[i] - autoRangeMin) / denom).coerceIn(0f, 1f)
                    energyBins[i] = scaled
                }
            }

            var postMax = 0f
            var postSum = 0f
            for (i in 0 until dstCount) {
                val v = energyBins[i]
                if (v > postMax) postMax = v
                postSum += v
            }
            debugMaxEnergy = postMax
            debugAvgEnergy = if (dstCount == 0) 0f else (postSum / dstCount.toFloat())
            }
        }

        val dstCount = targetBins.size
        val dtSec = (frameDtNs.toDouble() / 1_000_000_000.0).coerceIn(0.0, 0.05)
        val dt = dtSec.toFloat().coerceAtLeast(0.001f)
        
        // Update particles
        updateParticles(dt, width.toFloat(), height.toFloat())
        
        val alphaAttack = (1.0 - exp((-dt / attackTauSeconds).toDouble())).toFloat().coerceIn(0f, 1f)
        val alphaRelease = (1.0 - exp((-dt / releaseTauSeconds).toDouble())).toFloat().coerceIn(0f, 1f)
        val alphaStable = (1.0 - exp((-dt / 0.065f).toDouble())).toFloat().coerceIn(0f, 1f)
        for (i in 0 until dstCount) {
            val center = (dstCount - 1).toFloat() * 0.5f
            val denom = max(1f, center)
            val d = (kotlin.math.abs(i.toFloat() - center) / denom).coerceIn(0f, 1f)
            val srcIndex = (d * (dstCount - 1).toFloat()).roundToInt().coerceIn(0, dstCount - 1)

            var raw = if (srcIndex < energyBins.size) energyBins[srcIndex] else 0f
            if (silenceActive) raw = 0f
            if (raw < lowEnergyCut) raw = 0f

            val base = baseBins[srcIndex] + (raw - baseBins[srcIndex]) * (1f - exp((-dt / 0.35f).toDouble())).toFloat()
            baseBins[srcIndex] = base
            val transient = max(0f, raw - base)
            val emphasis = (base * 0.35f + transient * 1.40f).coerceIn(0f, 1f)

            val target = emphasis
            val curRaw = envRawBins[i]
            val baseAlpha = if (target >= curRaw) alphaAttack else alphaRelease
            val nearTopW = ((target - 0.85f) / 0.15f).coerceIn(0f, 1f)
            val slowAlpha = min(baseAlpha, alphaStable)
            val a = if (abs(target - curRaw) < 0.06f) (baseAlpha * (1f - nearTopW) + slowAlpha * nearTopW) else baseAlpha
            var nextRaw = curRaw + (target - curRaw) * a
            if (target > 0.04f && target < curRaw && abs(target - curRaw) < stallDelta) {
                nextRaw = min(nextRaw, curRaw - stallDropPerSecond * dt)
            }
            nextRaw = min(1f, max(0f, nextRaw))
            envRawBins[i] = nextRaw
            envBins[i] = quantizeWithHysteresis(nextRaw, envBins[i])
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val barCount = envBins.size

        val gap = h * 0.06f / barCount.toFloat()
        val barHeight = (h - gap * (barCount - 1)) / barCount.toFloat()

        val isLeft = channel == Channel.Left
        
        // --- DRAWING ---
        
        // Pass 1: Draw Glow (behind bars)
        for (i in 0 until barCount) {
            val v = envBins[i].coerceIn(0f, 1f)
            if (v < 0.01f) continue
            
            val bw = v * w * widthMargin
            val y = h - (i + 1) * barHeight - i * gap
            var x0 = if (isLeft) w - bw else 0f
            var x1 = if (isLeft) w else bw
            val radius = barHeight * 0.5f
            if (isLeft) x1 += radius else x0 -= radius
            
            canvas.drawRoundRect(x0, y, x1, y + barHeight, radius, radius, glowPaint)
        }

        // Pass 2: Draw Core Bars & Spawn Particles
        for (i in 0 until barCount) {
            val v = envBins[i].coerceIn(0f, 1f)
            val bw = v * w * widthMargin

            val y = h - (i + 1) * barHeight - i * gap
            var x0 = if (isLeft) w - bw else 0f
            var x1 = if (isLeft) w else bw
            
            val radius = barHeight * 0.5f
            if (isLeft) {
                x1 += radius
            } else {
                x0 -= radius
            }
            canvas.drawRoundRect(x0, y, x1, y + barHeight, radius, radius, paint)
            
            // Spawn particles logic
            if (v > 0.5f && !silenceActive) {
                // Higher energy -> higher chance
                val chance = (v - 0.5f) * 0.2f // max 0.1 per frame per bar
                if (random.nextFloat() < chance) {
                     val px = if (isLeft) x0 else x1 // Tip of the bar
                     val py = y + barHeight * 0.5f
                     spawnParticle(px, py, v)
                }
            }
        }
        
        // Pass 3: Draw Particles
        drawParticles(canvas)

        if (debugOverlayEnabled) {
            val lines = arrayOf(
                "max=${"%.3f".format(debugMaxEnergy)} avg=${"%.3f".format(debugAvgEnergy)}",
                "silence=$silenceActive lowCut=${"%.3f".format(lowEnergyCut)}",
                "dtMs=${"%.1f".format(dtSec * 1000.0)}",
                "particles=${particles.size}"
            )
            val x = 8f * resources.displayMetrics.density
            var y = 16f * resources.displayMetrics.density
            val dy = 14f * resources.displayMetrics.density
            for (line in lines) {
                canvas.drawText(line, x, y, debugPaint)
                y += dy
            }
        }
    }
    
    private fun updateParticles(dt: Float, w: Float, h: Float) {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            if (p.life <= 0) {
                iter.remove()
                continue
            }
            
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
            
            // Simple gravity/drag
            p.vx *= 0.95f
            // Drift up
            p.vy -= 10f * dt 
        }
    }
    
    private fun spawnParticle(x: Float, y: Float, intensity: Float) {
        if (particles.size > 200) return // Limit count
        
        val size = (random.nextFloat() * 4f + 2f) * resources.displayMetrics.density
        val life = random.nextFloat() * 0.5f + 0.3f
        
        // Random velocity
        // Shoot outwards away from the bar base
        val isLeft = channel == Channel.Left
        val vxDir = if (isLeft) -1f else 1f
        val vx = (random.nextFloat() * 100f + 20f) * vxDir * intensity
        val vy = (random.nextFloat() - 0.5f) * 100f
        
        particles.add(Particle(
            x = x,
            y = y,
            vx = vx,
            vy = vy,
            alpha = 1f,
            size = size,
            life = life,
            maxLife = life,
            color = particlePaint.color
        ))
    }
    
    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            particlePaint.alpha = (p.alpha * 255).toInt()
            canvas.drawCircle(p.x, p.y, p.size, particlePaint)
        }
    }

    private fun rebuildBandWeight() {
        val n = freqWeight.size
        if (n <= 1) {
            if (n == 1) freqWeight[0] = 1f
            return
        }
        val denom = (n - 1).toFloat()
        for (i in 0 until n) {
            val t = i.toFloat() / denom
            val kick = 0.55f * exp((-0.5f * ((t - 0.08f) / 0.06f) * ((t - 0.08f) / 0.06f)).toDouble()).toFloat()
            val vocal = 0.35f * exp((-0.5f * ((t - 0.33f) / 0.12f) * ((t - 0.33f) / 0.12f)).toDouble()).toFloat()
            val rolloff = (1f - 0.70f * t * t * t).coerceIn(0.25f, 1f)
            val weight = (0.95f + kick + vocal) * rolloff
            freqWeight[i] = weight.coerceIn(0.35f, 1.75f)
        }
    }

    private fun applySgSmoothingInPlace(src: FloatArray, tmp: FloatArray, weights: FloatArray) {
        val n = src.size
        val m = weights.size / 2
        for (i in 0 until n) {
            var acc = 0f
            for (j in -m..m) {
                val idx = (i + j).coerceIn(0, n - 1)
                acc += src[idx] * weights[j + m]
            }
            tmp[i] = acc
        }
        tmp.copyInto(src)
    }

    private fun computeSavitzkyGolayWeights(window: Int, order: Int): FloatArray {
        val w = if (window % 2 == 1) window else window + 1
        val o = order.coerceIn(1, w - 1)
        val m = w / 2
        val cols = o + 1

        val a = Array(w) { DoubleArray(cols) }
        for (i in 0 until w) {
            val x = (i - m).toDouble()
            var p = 1.0
            for (j in 0 until cols) {
                a[i][j] = p
                p *= x
            }
        }

        val ata = Array(cols) { DoubleArray(cols) }
        for (r in 0 until cols) {
            for (c in 0 until cols) {
                var sum = 0.0
                for (i in 0 until w) sum += a[i][r] * a[i][c]
                ata[r][c] = sum
            }
        }

        val inv = invertSquareMatrix(ata)
        val weights = FloatArray(w)
        for (i in 0 until w) {
            var sum = 0.0
            for (j in 0 until cols) {
                sum += inv[0][j] * a[i][j]
            }
            weights[i] = sum.toFloat()
        }
        return weights
    }

    private fun invertSquareMatrix(src: Array<DoubleArray>): Array<DoubleArray> {
        val n = src.size
        val a = Array(n) { r -> src[r].clone() }
        val inv = Array(n) { r -> DoubleArray(n) { c -> if (r == c) 1.0 else 0.0 } }

        for (i in 0 until n) {
            var pivotRow = i
            var pivotAbs = kotlin.math.abs(a[i][i])
            for (r in i + 1 until n) {
                val v = kotlin.math.abs(a[r][i])
                if (v > pivotAbs) {
                    pivotAbs = v
                    pivotRow = r
                }
            }
            if (pivotRow != i) {
                val tmp = a[i]
                a[i] = a[pivotRow]
                a[pivotRow] = tmp
                val tmpInv = inv[i]
                inv[i] = inv[pivotRow]
                inv[pivotRow] = tmpInv
            }

            val pivot = a[i][i]
            val invPivot = 1.0 / pivot
            for (c in 0 until n) {
                a[i][c] *= invPivot
                inv[i][c] *= invPivot
            }

            for (r in 0 until n) {
                if (r == i) continue
                val factor = a[r][i]
                if (factor == 0.0) continue
                for (c in 0 until n) {
                    a[r][c] -= factor * a[i][c]
                    inv[r][c] -= factor * inv[i][c]
                }
            }
        }
        return inv
    }

    private fun start() {
        if (running) return
        running = true
        lastFrameNs = 0L
        choreographer.postFrameCallback(frameCallback)
    }

    private fun stop() {
        if (!running) return
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    private fun quantizeWithHysteresis(raw: Float, currentQuantized: Float): Float {
        if (quantStep <= 0f) return raw.coerceIn(0f, 1f)
        val cur = currentQuantized.coerceIn(0f, 1f)
        val level = (cur / quantStep).roundToInt()
        val w = ((raw - 0.85f) / 0.15f).coerceIn(0f, 1f)
        val db = (deadband * (1f - 0.6f * w)).coerceIn(deadband * 0.35f, deadband)
        val up = ((level + 0.5f) * quantStep + db).coerceIn(0f, 1f)
        val down = ((level - 0.5f) * quantStep - db).coerceIn(0f, 1f)
        val nextLevel = when {
            raw > up -> level + 1
            raw < down -> level - 1
            else -> level
        }
        return (nextLevel * quantStep).coerceIn(0f, 1f)
    }
}
