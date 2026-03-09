package com.asmr.player.playback

import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@UnstableApi
class StereoFftAnalyzer(
    private val pcmBuffer: StereoPcmRingBuffer,
    private val spectrumStore: StereoSpectrumStore,
    private val scope: CoroutineScope,
    private val fftSize: Int = 1024,
    private val binCount: Int = 128
) {
    private val window = FloatArray(fftSize)
    private val fft = ComplexFft(fftSize)

    private val leftIn = FloatArray(fftSize)
    private val rightIn = FloatArray(fftSize)

    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)

    private val binStart = IntArray(binCount)
    private val binEnd = IntArray(binCount)

    private val smoothLeft = FloatArray(binCount)
    private val smoothRight = FloatArray(binCount)
    private val tmpBins = FloatArray(binCount)
    private val tmpSpatial = FloatArray(binCount)
    private val tilt = FloatArray(binCount)

    private var peakLeft: Float = 0.25f
    private var peakRight: Float = 0.25f

    private val headroom: Float = 0.82f
    private val contrastGamma: Float = 2.0f

    @Volatile
    private var sampleRate: Int = 44100

    @Volatile
    private var visualDelayMs: Int = 120

    private var job: Job? = null

    init {
        val denom = (fftSize - 1).coerceAtLeast(1)
        for (i in 0 until fftSize) {
            window[i] = (0.5 - 0.5 * cos(2.0 * PI * i.toDouble() / denom)).toFloat()
        }
        if (binCount > 1) {
            val last = (binCount - 1).toFloat()
            for (i in 0 until binCount) {
                val t = i.toFloat() / last
                tilt[i] = 1f - 0.62f * t * t
            }
        } else {
            tilt[0] = 1f
        }
        rebuildBins()
    }

    fun setSampleRate(sampleRate: Int) {
        if (sampleRate <= 0) return
        this.sampleRate = sampleRate
        rebuildBins()
    }

    fun setVisualDelayMs(delayMs: Int) {
        visualDelayMs = delayMs.coerceIn(0, 400)
    }

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.Default) {
            loop()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun loop() {
        var lastSeq = 0L
        var idleFrames = 0
        while (scope.isActive) {
            val sr = sampleRate
            val framesDelay = ((visualDelayMs.toLong() * sr.toLong()) / 1000L).toInt()
            val slotsDelay = ((framesDelay + pcmBuffer.frameSize / 2) / pcmBuffer.frameSize).coerceIn(0, pcmBuffer.slotCount - 1)
            val seq = pcmBuffer.copyDelayedTo(leftIn, rightIn, slotsDelay)
            if (seq == 0L || seq == lastSeq) {
                idleFrames++
                if (idleFrames >= 4) {
                    publishDecay()
                    idleFrames = 0
                }
                delay(16)
                continue
            }
            idleFrames = 0
            lastSeq = seq

            val writeIndex = spectrumStore.beginWrite()
            computeBins(leftIn, spectrumStore.leftWriteBuffer(writeIndex), smoothLeft, isLeft = true)
            computeBins(rightIn, spectrumStore.rightWriteBuffer(writeIndex), smoothRight, isLeft = false)
            spectrumStore.publish(writeIndex)
        }
    }

    private fun publishDecay() {
        val writeIndex = spectrumStore.beginWrite()
        val outLeft = spectrumStore.leftWriteBuffer(writeIndex)
        val outRight = spectrumStore.rightWriteBuffer(writeIndex)
        for (i in 0 until binCount) {
            val nl = smoothLeft[i] * 0.82f
            val nr = smoothRight[i] * 0.82f
            smoothLeft[i] = if (nl < 0.0005f) 0f else nl
            smoothRight[i] = if (nr < 0.0005f) 0f else nr
            outLeft[i] = smoothLeft[i]
            outRight[i] = smoothRight[i]
        }
        spectrumStore.publish(writeIndex)
    }

    private fun computeBins(
        input: FloatArray,
        outBins: FloatArray,
        smooth: FloatArray,
        isLeft: Boolean
    ) {
        for (i in 0 until fftSize) {
            real[i] = input[i] * window[i]
            imag[i] = 0f
        }
        fft.fft(real, imag)

        val kMax = fftSize / 2
        for (b in 0 until binCount) {
            val start = binStart[b]
            val end = binEnd[b]
            var sum = 0f
            var count = 0
            var k = start
            while (k <= end && k < kMax) {
                val re = real[k]
                val im = imag[k]
                sum += re * re + im * im
                count++
                k++
            }
            val power = if (count == 0) 0f else sum / count.toFloat()
            val target = compress(sqrt(max(0f, power)))
            tmpBins[b] = target
        }

        var maxVal = 0f
        for (b in 0 until binCount) {
            val v = tmpBins[b]
            if (v > maxVal) maxVal = v
        }
        val peak = if (isLeft) {
            val p = max(peakLeft * 0.94f, maxVal)
            peakLeft = p
            p
        } else {
            val p = max(peakRight * 0.94f, maxVal)
            peakRight = p
            p
        }
        val normPeak = max(0.06f, peak)
        for (b in 0 until binCount) {
            val ratio = (tmpBins[b] / normPeak).coerceIn(0f, 1.25f)
            val curved = ratio.pow(contrastGamma)
            val v = curved * tilt[b] * headroom
            tmpBins[b] = if (v < 0.02f) 0f else v.coerceIn(0f, 1f)
        }

        // REMOVED: Redundant smoothing (Attack/Decay) in data layer.
        // We pass raw FFT energy to the View layer to preserve transient punch.
        // The View layer (ChannelSpectrumView) already has its own sophisticated physics model.
        for (b in 0 until binCount) {
             smooth[b] = tmpBins[b]
        }

        if (binCount > 2) {
            tmpSpatial[0] = smooth[0]
            val last = binCount - 1
            tmpSpatial[last] = smooth[last]
            for (i in 1 until last) {
                tmpSpatial[i] = (smooth[i - 1] + 2f * smooth[i] + smooth[i + 1]) * 0.25f
            }
            tmpSpatial.copyInto(outBins)
        } else {
            smooth.copyInto(outBins)
        }
    }

    private fun compress(magnitude: Float): Float {
        val m = max(0f, magnitude)
        val k = 10.0
        return (ln(1.0 + m.toDouble() * k) / ln(1.0 + k)).toFloat()
    }

    private fun rebuildBins() {
        val sr = max(1, sampleRate)
        val fMin = 30.0
        val fMax = (sr / 2).toDouble().coerceAtLeast(fMin + 1.0)
        val ratio = fMax / fMin

        for (b in 0 until binCount) {
            val p0 = b.toDouble() / binCount.toDouble()
            val p1 = (b + 1).toDouble() / binCount.toDouble()
            val hz0 = fMin * ratio.pow(p0)
            val hz1 = fMin * ratio.pow(p1)
            val k0 = ((hz0 / sr.toDouble()) * fftSize.toDouble()).toInt()
            val k1 = ((hz1 / sr.toDouble()) * fftSize.toDouble()).toInt()
            val start = max(1, min(fftSize / 2 - 1, k0))
            val end = max(start, min(fftSize / 2 - 1, k1))
            binStart[b] = start
            binEnd[b] = end
        }
    }

    private class ComplexFft(
        private val n: Int
    ) {
        private val bitRev = IntArray(n)
        private val cosTable = FloatArray(n / 2)
        private val sinTable = FloatArray(n / 2)

        init {
            val levels = 31 - Integer.numberOfLeadingZeros(n)
            for (i in 0 until n) {
                bitRev[i] = Integer.reverse(i) ushr (32 - levels)
            }
            for (i in 0 until n / 2) {
                val angle = (-2.0 * PI * i.toDouble() / n.toDouble())
                cosTable[i] = cos(angle).toFloat()
                sinTable[i] = sin(angle).toFloat()
            }
        }

        fun fft(real: FloatArray, imag: FloatArray) {
            for (i in 0 until n) {
                val j = bitRev[i]
                if (j > i) {
                    val tr = real[i]
                    real[i] = real[j]
                    real[j] = tr
                    val ti = imag[i]
                    imag[i] = imag[j]
                    imag[j] = ti
                }
            }

            var size = 2
            while (size <= n) {
                val half = size / 2
                val step = n / size
                var i = 0
                while (i < n) {
                    var k = 0
                    var j = 0
                    while (j < half) {
                        val l = i + j + half
                        val tpre = real[l] * cosTable[k] - imag[l] * sinTable[k]
                        val tpim = real[l] * sinTable[k] + imag[l] * cosTable[k]
                        val ureal = real[i + j]
                        val uimag = imag[i + j]
                        real[i + j] = ureal + tpre
                        imag[i + j] = uimag + tpim
                        real[l] = ureal - tpre
                        imag[l] = uimag - tpim
                        j++
                        k += step
                    }
                    i += size
                }
                size *= 2
            }
        }
    }
}
