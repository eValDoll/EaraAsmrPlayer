package com.asmr.player.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import com.asmr.player.cache.CachePolicy
import com.asmr.player.cache.ImageCacheEntryPoint
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

@Composable
fun AsmrAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    placeholderCornerRadius: Int = 6,
    placeholder: @Composable (Modifier) -> Unit = { m ->
        DiscPlaceholder(modifier = m, cornerRadius = placeholderCornerRadius)
    },
    empty: @Composable (Modifier) -> Unit = placeholder,
    loading: @Composable (Modifier) -> Unit = { m ->
        AsmrImageLoadingPlaceholder(modifier = m, cornerRadius = placeholderCornerRadius)
    },
    retainPainterDuringReload: Boolean = false,
    reloadKey: Any? = null,
    loadWhenSizeStableForMillis: Long = 0L,
    fadeIn: Boolean = true,
    fadeInState: State<Boolean>? = null,
    fadeInMillis: Int = 500,
    peekAnySizeForInitial: Boolean = false,
    requestSize: IntSize? = null,
    // 按原尺寸加载（size=null）：缓存 key 与显示尺寸无关，让列表与详情大图共用同一缓存条目，
    // 详情页进入即内存命中、不再二次网络请求、不再低分辨率占位闪烁。显示时由 ContentScale 缩放。
    loadAtOriginalSize: Boolean = false,
    onBitmapPainterState: ((BitmapPainter?, State<Float>) -> Unit)? = null,
) {
    val normalizedModel = remember(model) { normalizeImageModel(model) }
    if (normalizedModel == null) {
        empty(modifier)
        return
    }

    val ctx = LocalContext.current.applicationContext
    val manager = remember(ctx) {
        EntryPointAccessors.fromApplication(ctx, ImageCacheEntryPoint::class.java).imageCacheManager()
    }
    val measuredSize: MutableState<IntSize?> = remember { mutableStateOf(null) }
    // 跨尺寸即时占位：若该图片已被列表等处加载过，先用任意尺寸的缓存位图立即显示，
    // 同时仍按精确尺寸加载原图并在完成后无缝替换，避免详情大图等待网络重新请求。
    val seededPainter = remember(normalizedModel, reloadKey) {
        if (peekAnySizeForInitial) manager.peekAnySize(normalizedModel)?.let { BitmapPainter(it) } else null
    }
    val painter: MutableState<Painter?> = remember(normalizedModel, reloadKey) { mutableStateOf(seededPainter) }
    val seededPlaceholder = remember(normalizedModel, reloadKey) { mutableStateOf(seededPainter != null) }
    val state: MutableState<AsmrAsyncImageState> =
        remember(normalizedModel, reloadKey) {
            mutableStateOf(if (seededPainter != null) AsmrAsyncImageState.Success else AsmrAsyncImageState.Loading)
        }
    val loadedSize: MutableState<IntSize?> = remember(normalizedModel, reloadKey) { mutableStateOf(null) }
    val crossfade = remember(normalizedModel, reloadKey) { Animatable(if (seededPainter != null) 1f else 0f) }
    val crossfadeRunning = remember(normalizedModel, reloadKey) { mutableStateOf(false) }
    val resolvedFadeIn = fadeInState?.value ?: fadeIn
    val latestFadeIn by rememberUpdatedState(resolvedFadeIn)
    val containerModifier = if (requestSize == null) {
        modifier.onSizeChanged { sz ->
            if (sz.width > 0 && sz.height > 0) measuredSize.value = IntSize(sz.width, sz.height)
        }
    } else {
        modifier
    }
    val contentModifier = Modifier.fillMaxSize()

    val resolvedSize = requestSize ?: measuredSize.value
    val loadSizeKey: Any? = if (loadAtOriginalSize) Unit else resolvedSize
    LaunchedEffect(normalizedModel, loadSizeKey, reloadKey) {
        val initialSize = requestSize ?: measuredSize.value
        if (!loadAtOriginalSize && initialSize == null) return@LaunchedEffect
        if (!loadAtOriginalSize && loadWhenSizeStableForMillis > 0L) {
            delay(loadWhenSizeStableForMillis)
        }
        val sz = requestSize ?: measuredSize.value ?: initialSize ?: IntSize.Zero
        suspend fun finishWithExistingPainter() {
            state.value = AsmrAsyncImageState.Success
            crossfadeRunning.value = false
            crossfade.snapTo(1f)
        }
        // 原尺寸加载：load key 与显示尺寸无关，尺寸变化（如 hero 折叠）不应触发重载，
        // 已加载的位图由 ContentScale 重新裁切即可。
        if (loadAtOriginalSize && painter.value != null && loadedSize.value != null) {
            finishWithExistingPainter()
            return@LaunchedEffect
        }
        if (retainPainterDuringReload && loadedSize.value == sz && painter.value != null) {
            finishWithExistingPainter()
            return@LaunchedEffect
        }
        try {
            crossfadeRunning.value = false
            val hasExistingPainter = painter.value != null
            val shouldRetainPainter = (retainPainterDuringReload || loadAtOriginalSize || seededPlaceholder.value) && hasExistingPainter
            val imageRequestSize = if (loadAtOriginalSize) null else sz
            if (!shouldRetainPainter) {
                state.value = AsmrAsyncImageState.Loading
                painter.value = null
                crossfade.snapTo(0f)
            } else {
                state.value = AsmrAsyncImageState.Success
                crossfade.snapTo(1f)
            }
            val img = withTimeoutOrNull(15_000) {
                manager.loadImage(
                    model = normalizedModel,
                    size = imageRequestSize,
                    cachePolicy = CachePolicy.DEFAULT
                )
            } ?: throw IllegalStateException("Image load timeout")
            painter.value = BitmapPainter(img)
            loadedSize.value = sz
            seededPlaceholder.value = false
            state.value = AsmrAsyncImageState.Success
            if (latestFadeIn && !shouldRetainPainter) {
                crossfadeRunning.value = true
                try {
                    crossfade.animateTo(1f, tween(durationMillis = fadeInMillis))
                } finally {
                    crossfadeRunning.value = false
                }
            } else {
                crossfade.snapTo(1f)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (painter.value == null) {
                state.value = AsmrAsyncImageState.Error
                painter.value = null
                loadedSize.value = null
            }
        }
    }

    val p = painter.value
    val bitmapPainterAlpha = remember(normalizedModel, reloadKey) {
        derivedStateOf {
            val currentPainter = painter.value
            val seeded = currentPainter != null && seededPlaceholder.value
            if (currentPainter != null && latestFadeIn && !seeded && crossfadeRunning.value) {
                crossfade.value.coerceIn(0f, 1f)
            } else {
                1f
            }
        }
    }
    val latestBitmapPainterObserver by rememberUpdatedState(onBitmapPainterState)
    LaunchedEffect(p, bitmapPainterAlpha) {
        latestBitmapPainterObserver?.invoke(p as? BitmapPainter, bitmapPainterAlpha)
    }
    LaunchedEffect(resolvedFadeIn, p) {
        if (!resolvedFadeIn && p != null) {
            crossfadeRunning.value = false
            crossfade.snapTo(1f)
        }
    }
    val currentState = state.value
    val hasSeededPainter = p != null && seededPlaceholder.value
    Box(modifier = containerModifier) {
        when {
            currentState == AsmrAsyncImageState.Error -> {
                placeholder(contentModifier)
            }
            else -> {
                if (!hasSeededPainter && (currentState == AsmrAsyncImageState.Loading || crossfadeRunning.value)) {
                    val loadingModifier = if (currentState == AsmrAsyncImageState.Loading) {
                        contentModifier
                    } else {
                        contentModifier.graphicsLayer {
                            this.alpha = (1f - crossfade.value).coerceIn(0f, 1f)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                    }
                    loading(loadingModifier)
                }
                if (p != null) {
                    val imageModifier = if (resolvedFadeIn && !hasSeededPainter && crossfadeRunning.value) {
                        contentModifier.graphicsLayer {
                            this.alpha = crossfade.value.coerceIn(0f, 1f)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                    } else {
                        contentModifier
                    }
                    Image(
                        painter = p,
                        contentDescription = contentDescription,
                        modifier = imageModifier,
                        contentScale = contentScale,
                        alignment = alignment,
                        alpha = alpha,
                        colorFilter = colorFilter
                    )
                }
            }
        }
    }
}

internal val NoImageLoadingIndicator: @Composable (Modifier) -> Unit = {}

private enum class AsmrAsyncImageState {
    Loading,
    Success,
    Error,
}

private fun normalizeImageModel(model: Any?): Any? {
    return when (model) {
        is String -> {
            val s = model.trim()
            if (s.isEmpty()) return null
            val lower = s.lowercase()
            when {
                lower.startsWith("http://") ||
                    lower.startsWith("https://") ||
                    lower.startsWith("content://") ||
                    lower.startsWith("file://") -> s
                s.startsWith("/") -> File(s)
                else -> s
            }
        }
        else -> model
    }
}
