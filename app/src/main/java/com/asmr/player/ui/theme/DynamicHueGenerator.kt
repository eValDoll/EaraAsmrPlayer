package com.asmr.player.ui.theme

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.asmr.player.cache.CachePolicy
import com.asmr.player.cache.ImageCacheEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal fun dynamicHueCacheKeyForArtwork(
    artworkModel: Any?,
    centerRegionRatio: Float = 0.62f
): String? {
    val baseKey = artworkModel?.toString().orEmpty().trim()
    if (baseKey.isBlank()) return null
    val regionKey = (centerRegionRatio * 100).toInt().coerceIn(10, 100)
    return "hue:cw:$regionKey:$baseKey"
}

internal fun dynamicHueCacheKeyForVideo(
    videoUri: Uri?,
    centerRegionRatio: Float = 0.62f
): String? {
    val baseKey = videoUri?.toString().orEmpty().trim()
    if (baseKey.isBlank()) return null
    val regionKey = (centerRegionRatio * 100).toInt().coerceIn(10, 100)
    return "hue:vf:cw:$regionKey:$baseKey"
}

internal fun peekDynamicHueSeedColor(cacheKey: String?): Color? {
    val key = cacheKey?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    return DynamicHueCache.get(key)
}

@Composable
fun PrewarmDynamicHuePalette(
    artworkModel: Any?,
    fallbackHue: HuePalette,
    imageSizePx: Int = 256,
    centerRegionRatio: Float = 0.62f
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val manager = remember(app) {
        EntryPointAccessors.fromApplication(app, ImageCacheEntryPoint::class.java).imageCacheManager()
    }
    val rawBaseKey = artworkModel?.toString().orEmpty()
    val lastNonBlankBaseKeyState = rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    if (rawBaseKey.isNotBlank() && rawBaseKey != lastNonBlankBaseKeyState.value) {
        lastNonBlankBaseKeyState.value = rawBaseKey
    }
    val baseKey = if (rawBaseKey.isNotBlank()) rawBaseKey else lastNonBlankBaseKeyState.value
    val regionKey = (centerRegionRatio * 100).toInt().coerceIn(10, 100)
    val seedKey = "hue:cw:$regionKey:$baseKey"

    LaunchedEffect(seedKey, baseKey) {
        if (baseKey.isBlank()) return@LaunchedEffect
        if (DynamicHueCache.get(seedKey) != null) return@LaunchedEffect

        DynamicHueCache.getOrCompute(seedKey) {
            withContext(Dispatchers.Default) {
                val model = artworkModel ?: return@withContext null
                val image = runCatching {
                    manager.loadImage(
                        model = model,
                        size = IntSize(imageSizePx, imageSizePx),
                        cachePolicy = CachePolicy.DEFAULT
                    )
                }.getOrNull() ?: return@withContext null
                val bitmap = image.asAndroidBitmap()
                if (bitmap.width < 10 || bitmap.height < 10) return@withContext null

                monetSeedColorFromBitmap(
                    bitmap = bitmap,
                    fallbackColor = fallbackHue.primary,
                    centerRegionRatio = centerRegionRatio
                )
            }
        }
    }
}

@Composable
fun PrewarmDynamicHuePaletteFromVideoFrame(
    videoUri: Uri?,
    fallbackHue: HuePalette,
    imageSizePx: Int = 256,
    centerRegionRatio: Float = 0.62f,
    timeoutMs: Long = 2_500L
) {
    val context = LocalContext.current
    val rawBaseKey = videoUri?.toString().orEmpty()
    val lastNonBlankBaseKeyState = rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    if (rawBaseKey.isNotBlank() && rawBaseKey != lastNonBlankBaseKeyState.value) {
        lastNonBlankBaseKeyState.value = rawBaseKey
    }
    val baseKey = if (rawBaseKey.isNotBlank()) rawBaseKey else lastNonBlankBaseKeyState.value
    val regionKey = (centerRegionRatio * 100).toInt().coerceIn(10, 100)
    val seedKey = "hue:vf:cw:$regionKey:$baseKey"

    LaunchedEffect(seedKey, baseKey, videoUri) {
        if (baseKey.isBlank() || videoUri == null) return@LaunchedEffect
        if (DynamicHueCache.get(seedKey) != null) return@LaunchedEffect

        DynamicHueCache.getOrCompute(seedKey) {
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(timeoutMs) {
                    val bitmap = extractMeaningfulVideoFrameBitmap(context, videoUri, imageSizePx) ?: return@withTimeoutOrNull null
                    monetSeedColorFromBitmap(
                        bitmap = bitmap,
                        fallbackColor = fallbackHue.primary,
                        centerRegionRatio = centerRegionRatio
                    )
                }
            }
        }
    }
}

@Composable
fun rememberDynamicHuePalette(
    artworkModel: Any?,
    mode: ThemeMode,
    neutral: NeutralPalette,
    fallbackHue: HuePalette,
    imageSizePx: Int = 256,
    centerRegionRatio: Float = 0.62f,
    transitionDurationMs: Int = 520,
    cachedTransitionDurationMs: Int = 260
): State<HuePalette> {
    val context = LocalContext.current
    val app = context.applicationContext
    val manager = remember(app) {
        EntryPointAccessors.fromApplication(app, ImageCacheEntryPoint::class.java).imageCacheManager()
    }
    val rawBaseKey = artworkModel?.toString().orEmpty()
    val lastNonBlankBaseKeyState = rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    if (rawBaseKey.isNotBlank() && rawBaseKey != lastNonBlankBaseKeyState.value) {
        lastNonBlankBaseKeyState.value = rawBaseKey
    }
    val baseKey = if (rawBaseKey.isNotBlank()) rawBaseKey else lastNonBlankBaseKeyState.value
    val regionKey = (centerRegionRatio * 100).toInt().coerceIn(10, 100)
    val seedKey = "hue:cw:$regionKey:$baseKey"
    
    // Keep the animatable un-keyed so the previous track's color is retained while the
    // new seed is computed on a background thread; we then animate from old to new color.
    val animatable = remember {
        Animatable(DynamicHueCache.get(seedKey) ?: fallbackHue.primary, ColorVectorConverter)
    }


    LaunchedEffect(seedKey) {
        if (baseKey.isBlank()) {
            return@LaunchedEffect
        }
        
        // If we have it in cache, snap immediately to avoid animation if we are just scrolling/recomposing?
        DynamicHueCache.get(seedKey)?.let {
            if (cachedTransitionDurationMs <= 0) animatable.snapTo(it)
            else animatable.animateTo(it, animationSpec = tween(cachedTransitionDurationMs))
            return@LaunchedEffect
        }
        
        var constrainedPrimary: Color? = null
        var attempt = 0
        // Retry logic for color extraction failure (e.g. Coil loading issue)
        while (constrainedPrimary == null && attempt < 3) {
            if (attempt > 0) kotlinx.coroutines.delay(300)
            
            constrainedPrimary = DynamicHueCache.getOrCompute(seedKey) {
                withContext(Dispatchers.Default) {
                    val m = artworkModel ?: return@withContext null
                    val img = runCatching {
                        manager.loadImage(
                            model = m,
                            size = IntSize(imageSizePx, imageSizePx),
                            cachePolicy = CachePolicy.DEFAULT
                        )
                    }.getOrNull() ?: return@withContext null
                    val bitmap = img.asAndroidBitmap()
                        
                    if (bitmap.width < 10 || bitmap.height < 10) return@withContext null

                    monetSeedColorFromBitmap(
                        bitmap = bitmap,
                        fallbackColor = fallbackHue.primary,
                        centerRegionRatio = centerRegionRatio
                    )
                }
            }
            attempt++
        }

        if (constrainedPrimary == null) {
            // If failed after retries, stick to fallback
            if (cachedTransitionDurationMs <= 0) animatable.snapTo(fallbackHue.primary)
            else animatable.animateTo(
                fallbackHue.primary,
                animationSpec = tween(durationMillis = cachedTransitionDurationMs, easing = FastOutSlowInEasing)
            )
            return@LaunchedEffect
        }

        if (transitionDurationMs <= 0) animatable.snapTo(constrainedPrimary)
        else animatable.animateTo(
            constrainedPrimary,
            animationSpec = tween(durationMillis = transitionDurationMs, easing = FastOutSlowInEasing)
        )
    }

    return remember(mode, neutral, fallbackHue, animatable) {
        derivedStateOf {
            deriveHuePalette(animatable.value, mode, neutral, fallbackHue.onPrimary)
        }
    }
}

@Composable
fun rememberDynamicHuePaletteFromVideoFrame(
    videoUri: Uri?,
    mode: ThemeMode,
    neutral: NeutralPalette,
    fallbackHue: HuePalette,
    imageSizePx: Int = 256,
    centerRegionRatio: Float = 0.62f,
    timeoutMs: Long = 2_500L,
    transitionDurationMs: Int = 520,
    cachedTransitionDurationMs: Int = 260
): State<HuePalette> {
    val context = LocalContext.current
    val rawBaseKey = videoUri?.toString().orEmpty()
    val lastNonBlankBaseKeyState = rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    if (rawBaseKey.isNotBlank() && rawBaseKey != lastNonBlankBaseKeyState.value) {
        lastNonBlankBaseKeyState.value = rawBaseKey
    }
    val baseKey = if (rawBaseKey.isNotBlank()) rawBaseKey else lastNonBlankBaseKeyState.value
    val regionKey = (centerRegionRatio * 100).toInt().coerceIn(10, 100)
    val seedKey = "hue:vf:cw:$regionKey:$baseKey"

    // Keep the animatable un-keyed so the previous track's color is retained while the
    // new seed is computed on a background thread; we then animate from old to new color.
    val animatable = remember {
        Animatable(DynamicHueCache.get(seedKey) ?: fallbackHue.primary, ColorVectorConverter)
    }

    LaunchedEffect(seedKey) {
        if (baseKey.isBlank() || videoUri == null) return@LaunchedEffect

        DynamicHueCache.get(seedKey)?.let {
            if (cachedTransitionDurationMs <= 0) animatable.snapTo(it)
            else animatable.animateTo(it, animationSpec = tween(cachedTransitionDurationMs))
            return@LaunchedEffect
        }

        val constrainedPrimary = DynamicHueCache.getOrCompute(seedKey) {
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(timeoutMs) {
                    val bitmap = extractMeaningfulVideoFrameBitmap(context, videoUri, imageSizePx) ?: return@withTimeoutOrNull null
                    monetSeedColorFromBitmap(
                        bitmap = bitmap,
                        fallbackColor = fallbackHue.primary,
                        centerRegionRatio = centerRegionRatio
                    )
                }
            }
        }

        if (constrainedPrimary == null) {
            if (cachedTransitionDurationMs <= 0) animatable.snapTo(fallbackHue.primary)
            else animatable.animateTo(
                fallbackHue.primary,
                animationSpec = tween(durationMillis = cachedTransitionDurationMs, easing = FastOutSlowInEasing)
            )
            return@LaunchedEffect
        }

        if (transitionDurationMs <= 0) animatable.snapTo(constrainedPrimary)
        else animatable.animateTo(
            constrainedPrimary,
            animationSpec = tween(durationMillis = transitionDurationMs, easing = FastOutSlowInEasing)
        )
    }

    return remember(mode, neutral, fallbackHue, animatable) {
        derivedStateOf {
            deriveHuePalette(animatable.value, mode, neutral, fallbackHue.onPrimary)
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

private object DynamicHueCache {
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
