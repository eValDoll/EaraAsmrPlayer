package com.asmr.player.ui.common

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.asmr.player.cache.CachePolicy
import com.asmr.player.cache.ImageCacheEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

data class DominantColorResult(
    val color: Color?,
    val fromCache: Boolean
)

@Composable
fun rememberDominantColor(
    model: Any?,
    defaultColor: Color,
    imageSizePx: Int = 256
): State<Color> {
    val context = LocalContext.current
    val app = context.applicationContext
    val manager = remember(app) {
        EntryPointAccessors.fromApplication(app, ImageCacheEntryPoint::class.java).imageCacheManager()
    }
    val key = model?.toString().orEmpty()
    val animatable = remember { Animatable(defaultColor, ColorVectorConverter) }
    val animatedColor = remember { derivedStateOf { animatable.value } }

    LaunchedEffect(key, defaultColor) {
        if (key.isBlank()) {
            animatable.animateTo(defaultColor, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        DominantColorCache.get(key)?.let {
            animatable.animateTo(it, animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        val constrainedColor = DominantColorCache.getOrCompute(key) {
            withContext(Dispatchers.Default) {
                val m = model ?: return@withContext null
                val img = runCatching {
                    manager.loadImage(
                        model = m,
                        size = IntSize(imageSizePx, imageSizePx),
                        cachePolicy = CachePolicy.DEFAULT
                    )
                }.getOrNull() ?: return@withContext null
                val bitmap = img.asAndroidBitmap()
                val colorInt = runCatching {
                    val palette = Palette.from(bitmap).generate()
                    val preferDarkBackground = defaultColor.luminance() < 0.5f
                    pickBestColorInt(palette, defaultColor.toArgb(), preferDarkBackground)
                }.getOrNull() ?: defaultColor.toArgb()

                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(colorInt, hsl)
                val preferDarkBackground = defaultColor.luminance() < 0.5f
                adjustHslForUi(hsl, preferDarkBackground)

                Color(ColorUtils.HSLToColor(hsl))
            }
        } ?: return@LaunchedEffect

        animatable.animateTo(constrainedColor, animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing))
    }

    return animatedColor
}

@Composable
fun rememberDominantColorCenterWeighted(
    model: Any?,
    defaultColor: Color,
    imageSizePx: Int = 256,
    centerRegionRatio: Float = 0.62f
): State<Color> {
    val context = LocalContext.current
    val app = context.applicationContext
    val manager = remember(app) {
        EntryPointAccessors.fromApplication(app, ImageCacheEntryPoint::class.java).imageCacheManager()
    }
    val baseKey = model?.toString().orEmpty()
    val regionKey = (centerRegionRatio * 100).toInt().coerceIn(10, 100)
    val key = "cw:$regionKey:$baseKey"
    val animatable = remember { Animatable(defaultColor, ColorVectorConverter) }
    val animatedColor = remember { derivedStateOf { animatable.value } }

    LaunchedEffect(key, defaultColor, baseKey) {
        if (baseKey.isBlank()) {
            animatable.animateTo(defaultColor, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        DominantColorCache.get(key)?.let {
            animatable.animateTo(it, animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        val constrainedColor = DominantColorCache.getOrCompute(key) {
            withContext(Dispatchers.Default) {
                val m = model ?: return@withContext null
                val img = runCatching {
                    manager.loadImage(
                        model = m,
                        size = IntSize(imageSizePx, imageSizePx),
                        cachePolicy = CachePolicy.DEFAULT
                    )
                }.getOrNull() ?: return@withContext null
                val bitmap = img.asAndroidBitmap()
                val colorInt = runCatching {
                    val palette = Palette.from(bitmap).generate()
                    val hint = computeCenterWeightedHintColorInt(bitmap, centerRegionRatio)
                    val preferDarkBackground = defaultColor.luminance() < 0.5f
                    pickBestColorInt(
                        palette = palette,
                        fallbackColorInt = defaultColor.toArgb(),
                        preferDarkBackground = preferDarkBackground,
                        hintColorInt = hint
                    )
                }.getOrNull() ?: defaultColor.toArgb()

                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(colorInt, hsl)
                val preferDarkBackground = defaultColor.luminance() < 0.5f
                adjustHslForUi(hsl, preferDarkBackground)

                Color(ColorUtils.HSLToColor(hsl))
            }
        } ?: return@LaunchedEffect

        animatable.animateTo(constrainedColor, animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing))
    }

    return animatedColor
}

@Composable
fun rememberComputedBestTextColorOnCoverRegion(
    model: Any?,
    imageSizePx: Int = 256,
    leftRatio: Float,
    topRatio: Float,
    rightRatio: Float,
    bottomRatio: Float,
    minContrastRatio: Double = 4.5
): State<DominantColorResult> {
    val context = LocalContext.current
    val app = context.applicationContext
    val manager = remember(app) {
        EntryPointAccessors.fromApplication(app, ImageCacheEntryPoint::class.java).imageCacheManager()
    }
    val baseKey = model?.toString().orEmpty()
    val lr = (leftRatio * 100f).toInt().coerceIn(0, 100)
    val tr = (topRatio * 100f).toInt().coerceIn(0, 100)
    val rr = (rightRatio * 100f).toInt().coerceIn(0, 100)
    val br = (bottomRatio * 100f).toInt().coerceIn(0, 100)
    val key = "txt:rg:$lr,$tr,$rr,$br:$baseKey"
    val cached = remember(key) { DominantColorCache.get(key) }
    val state = remember(key) { mutableStateOf(DominantColorResult(color = cached, fromCache = cached != null)) }

    LaunchedEffect(key, baseKey) {
        if (baseKey.isBlank()) {
            state.value = DominantColorResult(color = null, fromCache = false)
            return@LaunchedEffect
        }
        if (state.value.color != null) return@LaunchedEffect

        val computed = DominantColorCache.getOrCompute(key) {
            withContext(Dispatchers.Default) {
                val m = model ?: return@withContext null
                val img = runCatching {
                    manager.loadImage(
                        model = m,
                        size = IntSize(imageSizePx, imageSizePx),
                        cachePolicy = CachePolicy.DEFAULT
                    )
                }.getOrNull() ?: return@withContext null
                val bitmap = img.asAndroidBitmap()
                val readableBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                    runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return@withContext null
                } else {
                    bitmap
                }
                val lum = computeRegionMedianLuminance(readableBitmap, leftRatio, topRatio, rightRatio, bottomRatio)
                val pickArgb = pickBlackOrWhiteTextColorArgbFromBackgroundLuminance(
                    backgroundLuminance = lum,
                    minContrastRatio = minContrastRatio
                ) ?: return@withContext null
                Color(pickArgb)
            }
        }

        state.value = DominantColorResult(color = computed, fromCache = false)
    }

    return state
}

@Composable
fun rememberVideoFrameDominantColorCenterWeighted(
    videoUri: Uri?,
    defaultColor: Color,
    imageSizePx: Int = 256,
    centerRegionRatio: Float = 0.62f,
    timeoutMs: Long = 2_500L
): State<Color> {
    val context = LocalContext.current
    val baseKey = videoUri?.toString().orEmpty()
    val regionKey = (centerRegionRatio * 100).toInt().coerceIn(10, 100)
    val key = "vf:cw:$regionKey:$baseKey"
    val animatable = remember { Animatable(defaultColor, ColorVectorConverter) }
    val animatedColor = remember { derivedStateOf { animatable.value } }

    LaunchedEffect(key, defaultColor, baseKey) {
        if (baseKey.isBlank()) {
            animatable.animateTo(defaultColor, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        DominantColorCache.get(key)?.let {
            animatable.animateTo(it, animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }

        val constrainedColor = DominantColorCache.getOrCompute(key) {
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(timeoutMs) {
                    val uri = videoUri ?: return@withTimeoutOrNull null
                    val bitmap = extractMeaningfulVideoFrameBitmap(context, uri, imageSizePx) ?: return@withTimeoutOrNull null
                    val colorInt = runCatching {
                        val palette = Palette.from(bitmap).generate()
                        val hint = computeCenterWeightedHintColorInt(bitmap, centerRegionRatio)
                        val preferDarkBackground = defaultColor.luminance() < 0.5f
                        pickBestColorInt(
                            palette = palette,
                            fallbackColorInt = defaultColor.toArgb(),
                            preferDarkBackground = preferDarkBackground,
                            hintColorInt = hint
                        )
                    }.getOrNull() ?: defaultColor.toArgb()

                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(colorInt, hsl)
                    val preferDarkBackground = defaultColor.luminance() < 0.5f
                    adjustHslForUi(hsl, preferDarkBackground)

                    Color(ColorUtils.HSLToColor(hsl))
                }
            }
        } ?: return@LaunchedEffect

        animatable.animateTo(constrainedColor, animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing))
    }

    return animatedColor
}

@Composable
fun rememberComputedDominantColorCenterWeighted(
    model: Any?,
    defaultColor: Color,
    imageSizePx: Int = 256,
    centerRegionRatio: Float = 0.62f
): State<DominantColorResult> {
    val context = LocalContext.current
    val app = context.applicationContext
    val manager = remember(app) {
        EntryPointAccessors.fromApplication(app, ImageCacheEntryPoint::class.java).imageCacheManager()
    }
    val baseKey = model?.toString().orEmpty()
    val regionKey = (centerRegionRatio * 100).toInt().coerceIn(10, 100)
    val key = "cw:$regionKey:$baseKey"
    val cached = remember(key) { DominantColorCache.get(key) }
    val state = remember(key) { mutableStateOf(DominantColorResult(color = cached, fromCache = cached != null)) }

    LaunchedEffect(key, defaultColor, baseKey) {
        if (baseKey.isBlank()) {
            state.value = DominantColorResult(color = null, fromCache = false)
            return@LaunchedEffect
        }
        if (state.value.color != null) return@LaunchedEffect

        val constrainedColor = DominantColorCache.getOrCompute(key) {
            withContext(Dispatchers.Default) {
                val m = model ?: return@withContext null
                val img = runCatching {
                    manager.loadImage(
                        model = m,
                        size = IntSize(imageSizePx, imageSizePx),
                        cachePolicy = CachePolicy.DEFAULT
                    )
                }.getOrNull() ?: return@withContext null
                val bitmap = img.asAndroidBitmap()
                val colorInt = runCatching {
                    val palette = Palette.from(bitmap).generate()
                    val hint = computeCenterWeightedHintColorInt(bitmap, centerRegionRatio)
                    val preferDarkBackground = defaultColor.luminance() < 0.5f
                    pickBestColorInt(
                        palette = palette,
                        fallbackColorInt = defaultColor.toArgb(),
                        preferDarkBackground = preferDarkBackground,
                        hintColorInt = hint
                    )
                }.getOrNull() ?: defaultColor.toArgb()

                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(colorInt, hsl)
                val preferDarkBackground = defaultColor.luminance() < 0.5f
                adjustHslForUi(hsl, preferDarkBackground)

                Color(ColorUtils.HSLToColor(hsl))
            }
        }

        state.value = DominantColorResult(color = constrainedColor, fromCache = false)
    }

    return state
}

@Composable
fun rememberComputedVideoFrameDominantColorCenterWeighted(
    videoUri: Uri?,
    defaultColor: Color,
    imageSizePx: Int = 256,
    centerRegionRatio: Float = 0.62f,
    timeoutMs: Long = 2_500L
): State<DominantColorResult> {
    val context = LocalContext.current
    val baseKey = videoUri?.toString().orEmpty()
    val regionKey = (centerRegionRatio * 100).toInt().coerceIn(10, 100)
    val key = "vf:cw:$regionKey:$baseKey"
    val cached = remember(key) { DominantColorCache.get(key) }
    val state = remember(key) { mutableStateOf(DominantColorResult(color = cached, fromCache = cached != null)) }

    LaunchedEffect(key, defaultColor, baseKey) {
        if (baseKey.isBlank() || videoUri == null) {
            state.value = DominantColorResult(color = null, fromCache = false)
            return@LaunchedEffect
        }
        if (state.value.color != null) return@LaunchedEffect

        val constrainedColor = DominantColorCache.getOrCompute(key) {
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(timeoutMs) {
                    val uri = videoUri
                    val bitmap = extractMeaningfulVideoFrameBitmap(context, uri, imageSizePx) ?: return@withTimeoutOrNull null
                    val colorInt = runCatching {
                        val palette = Palette.from(bitmap).generate()
                        val hint = computeCenterWeightedHintColorInt(bitmap, centerRegionRatio)
                        val preferDarkBackground = defaultColor.luminance() < 0.5f
                        pickBestColorInt(
                            palette = palette,
                            fallbackColorInt = defaultColor.toArgb(),
                            preferDarkBackground = preferDarkBackground,
                            hintColorInt = hint
                        )
                    }.getOrNull() ?: defaultColor.toArgb()

                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(colorInt, hsl)
                    val preferDarkBackground = defaultColor.luminance() < 0.5f
                    adjustHslForUi(hsl, preferDarkBackground)

                    Color(ColorUtils.HSLToColor(hsl))
                }
            }
        }

        state.value = DominantColorResult(color = constrainedColor, fromCache = false)
    }

    return state
}

@Composable
fun PrewarmDominantColorCenterWeighted(
    model: Any?,
    defaultColor: Color,
    imageSizePx: Int = 256,
    centerRegionRatio: Float = 0.62f
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val manager = remember(app) {
        EntryPointAccessors.fromApplication(app, ImageCacheEntryPoint::class.java).imageCacheManager()
    }
    val baseKey = model?.toString().orEmpty()
    val regionKey = (centerRegionRatio * 100).toInt().coerceIn(10, 100)
    val key = "cw:$regionKey:$baseKey"

    LaunchedEffect(key, defaultColor, baseKey) {
        if (baseKey.isBlank()) return@LaunchedEffect
        if (DominantColorCache.get(key) != null) return@LaunchedEffect

        DominantColorCache.getOrCompute(key) {
            withContext(Dispatchers.Default) {
                val m = model ?: return@withContext null
                val img = runCatching {
                    manager.loadImage(
                        model = m,
                        size = IntSize(imageSizePx, imageSizePx),
                        cachePolicy = CachePolicy.DEFAULT
                    )
                }.getOrNull() ?: return@withContext null
                val bitmap = img.asAndroidBitmap()
                val colorInt = runCatching {
                    val palette = Palette.from(bitmap).generate()
                    val hint = computeCenterWeightedHintColorInt(bitmap, centerRegionRatio)
                    val preferDarkBackground = defaultColor.luminance() < 0.5f
                    pickBestColorInt(
                        palette = palette,
                        fallbackColorInt = defaultColor.toArgb(),
                        preferDarkBackground = preferDarkBackground,
                        hintColorInt = hint
                    )
                }.getOrNull() ?: defaultColor.toArgb()

                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(colorInt, hsl)
                val preferDarkBackground = defaultColor.luminance() < 0.5f
                adjustHslForUi(hsl, preferDarkBackground)

                Color(ColorUtils.HSLToColor(hsl))
            }
        }
    }
}

@Composable
fun PrewarmVideoFrameDominantColorCenterWeighted(
    videoUri: Uri?,
    defaultColor: Color,
    imageSizePx: Int = 256,
    centerRegionRatio: Float = 0.62f,
    timeoutMs: Long = 2_500L
) {
    val context = LocalContext.current
    val baseKey = videoUri?.toString().orEmpty()
    val regionKey = (centerRegionRatio * 100).toInt().coerceIn(10, 100)
    val key = "vf:cw:$regionKey:$baseKey"

    LaunchedEffect(key, defaultColor, baseKey) {
        if (baseKey.isBlank() || videoUri == null) return@LaunchedEffect
        if (DominantColorCache.get(key) != null) return@LaunchedEffect

        DominantColorCache.getOrCompute(key) {
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(timeoutMs) {
                    val bitmap = extractMeaningfulVideoFrameBitmap(context, videoUri, imageSizePx) ?: return@withTimeoutOrNull null
                    val colorInt = runCatching {
                        val palette = Palette.from(bitmap).generate()
                        val hint = computeCenterWeightedHintColorInt(bitmap, centerRegionRatio)
                        val preferDarkBackground = defaultColor.luminance() < 0.5f
                        pickBestColorInt(
                            palette = palette,
                            fallbackColorInt = defaultColor.toArgb(),
                            preferDarkBackground = preferDarkBackground,
                            hintColorInt = hint
                        )
                    }.getOrNull() ?: defaultColor.toArgb()

                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(colorInt, hsl)
                    val preferDarkBackground = defaultColor.luminance() < 0.5f
                    adjustHslForUi(hsl, preferDarkBackground)

                    Color(ColorUtils.HSLToColor(hsl))
                }
            }
        }
    }
}

private fun extractMeaningfulVideoFrameBitmap(context: android.content.Context, uri: Uri, imageSizePx: Int): Bitmap? {
    val retriever = MediaMetadataRetriever()
    try {
        val scheme = uri.scheme.orEmpty().lowercase()
        if (scheme == "http" || scheme == "https") {
            retriever.setDataSource(uri.toString(), emptyMap())
        } else {
            retriever.setDataSource(context, uri)
        }

        val candidatesUs = longArrayOf(
            0L,
            300_000L,
            800_000L,
            1_500_000L,
            2_500_000L,
            4_000_000L
        )

        var first: Bitmap? = null
        for (tUs in candidatesUs) {
            val frame = runCatching {
                retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }.getOrNull() ?: continue

            if (first == null) first = frame
            if (!isLikelyBlankFrame(frame)) return frame.constrainToSize(imageSizePx)
        }

        return first?.constrainToSize(imageSizePx)
    } catch (_: Throwable) {
        return null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun Bitmap.constrainToSize(targetSizePx: Int): Bitmap {
    if (targetSizePx <= 0) return this
    val w = width
    val h = height
    if (w <= 0 || h <= 0) return this
    val maxDim = maxOf(w, h)
    if (maxDim <= targetSizePx) return this
    val scale = targetSizePx.toFloat() / maxDim.toFloat()
    val newW = (w * scale).toInt().coerceAtLeast(1)
    val newH = (h * scale).toInt().coerceAtLeast(1)
    return runCatching { Bitmap.createScaledBitmap(this, newW, newH, true) }.getOrElse { this }
}

private fun isLikelyBlankFrame(bitmap: Bitmap): Boolean {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 8 || h < 8) return true

    val sampleX = 12
    val sampleY = 12
    val stepX = (w / (sampleX + 1)).coerceAtLeast(1)
    val stepY = (h / (sampleY + 1)).coerceAtLeast(1)
    var count = 0
    var sum = 0.0
    var sumSq = 0.0

    var y = stepY
    while (y < h && count < sampleX * sampleY) {
        var x = stepX
        while (x < w && count < sampleX * sampleY) {
            val c = bitmap.getPixel(x, y)
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
            sum += luma
            sumSq += luma * luma
            count++
            x += stepX
        }
        y += stepY
    }

    if (count <= 0) return true
    val mean = sum / count
    val variance = (sumSq / count) - (mean * mean)
    return mean < 18.0 || variance < 12.0
}

private fun computeRegionMedianLuminance(
    bitmap: Bitmap,
    leftRatio: Float,
    topRatio: Float,
    rightRatio: Float,
    bottomRatio: Float
): Double? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 1 || height <= 1) return null

    val l = leftRatio.coerceIn(0f, 1f)
    val t = topRatio.coerceIn(0f, 1f)
    val r = rightRatio.coerceIn(0f, 1f)
    val b = bottomRatio.coerceIn(0f, 1f)
    if (r <= l || b <= t) return null

    val left = (width * l).toInt().coerceIn(0, width - 1)
    val top = (height * t).toInt().coerceIn(0, height - 1)
    val right = (width * r).toInt().coerceIn(left + 1, width)
    val bottom = (height * b).toInt().coerceIn(top + 1, height)
    val regionW = (right - left).coerceAtLeast(1)
    val regionH = (bottom - top).coerceAtLeast(1)

    val pixels = IntArray(regionW * regionH)
    runCatching { bitmap.getPixels(pixels, 0, regionW, left, top, regionW, regionH) }.getOrNull() ?: return null

    val step = if (regionW * regionH <= 22_000) 1 else 2
    val bins = IntArray(256)
    var count = 0
    var y = 0
    while (y < regionH) {
        val row = y * regionW
        var x = 0
        while (x < regionW) {
            val argb = pixels[row + x]
            val a = (argb ushr 24) and 0xFF
            if (a >= 32) {
                val lum = ColorUtils.calculateLuminance(argb).coerceIn(0.0, 1.0)
                val idx = ((lum * 255.0) + 0.5).toInt().coerceIn(0, 255)
                bins[idx]++
                count++
            }
            x += step
        }
        y += step
    }
    if (count <= 0) return null

    val half = (count + 1) / 2
    var acc = 0
    for (i in 0..255) {
        acc += bins[i]
        if (acc >= half) return i / 255.0
    }
    return 0.0
}

private object DominantColorCache {
    private const val MAX_SIZE = 64
    private val lru = LinkedHashMap<String, Color>(MAX_SIZE, 0.75f, true)
    private val inFlight = HashMap<String, Deferred<Color?>>()

    @Synchronized
    fun get(key: String): Color? = lru[key]

    @Synchronized
    fun put(key: String, color: Color) {
        lru[key] = color
        if (lru.size > MAX_SIZE) {
            val it = lru.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
    }

    suspend fun getOrCompute(key: String, compute: suspend () -> Color?): Color? {
        get(key)?.let { return it }

        val created = CompletableDeferred<Color?>()
        val toAwait: Deferred<Color?>
        val doCompute: Boolean
        synchronized(this) {
            lru[key]?.let { return it }
            val existing = inFlight[key]
            if (existing != null) {
                toAwait = existing
                doCompute = false
            } else {
                inFlight[key] = created
                toAwait = created
                doCompute = true
            }
        }

        if (!doCompute) return toAwait.await()

        val computed = runCatching { compute() }.getOrNull()
        synchronized(this) {
            inFlight.remove(key)
            if (computed != null) put(key, computed)
        }
        created.complete(computed)
        return computed
    }
}

private val ColorVectorConverter = TwoWayConverter<Color, AnimationVector4D>(
    convertToVector = { color ->
        AnimationVector4D(color.red, color.green, color.blue, color.alpha)
    },
    convertFromVector = { vector ->
        Color(vector.v1, vector.v2, vector.v3, vector.v4)
    }
)
