package com.asmr.player.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
        AsmrShimmerPlaceholder(modifier = m, cornerRadius = placeholderCornerRadius)
    },
    fadeIn: Boolean = true,
    fadeInMillis: Int = 1024,
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
    val painter: MutableState<Painter?> = remember(normalizedModel) { mutableStateOf(null) }
    val state: MutableState<AsmrAsyncImageState> =
        remember(normalizedModel) { mutableStateOf(AsmrAsyncImageState.Loading) }
    val crossfade = remember(normalizedModel) { Animatable(0f) }
    val sizedModifier = modifier.onSizeChanged { sz ->
        if (sz.width > 0 && sz.height > 0) measuredSize.value = IntSize(sz.width, sz.height)
    }

    LaunchedEffect(normalizedModel, measuredSize.value) {
        val sz = measuredSize.value ?: return@LaunchedEffect
        try {
            state.value = AsmrAsyncImageState.Loading
            painter.value = null
            crossfade.snapTo(0f)
            val img = withTimeoutOrNull(15_000) {
                manager.loadImage(model = normalizedModel, size = sz, cachePolicy = CachePolicy.DEFAULT)
            } ?: throw IllegalStateException("Image load timeout")
            painter.value = BitmapPainter(img)
            state.value = AsmrAsyncImageState.Success
            if (fadeIn) {
                crossfade.animateTo(1f, tween(durationMillis = fadeInMillis))
            } else {
                crossfade.snapTo(1f)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            state.value = AsmrAsyncImageState.Error
            painter.value = null
        }
    }

    val p = painter.value
    if (state.value == AsmrAsyncImageState.Error) {
        placeholder(sizedModifier)
        return
    }
    val progress = crossfade.value.coerceIn(0f, 1f)
    Box {
        // Keep a measurable anchor even when loading/empty UI intentionally renders nothing.
        Box(
            modifier = sizedModifier.graphicsLayer {
                this.alpha = 0f
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
        )
        if (state.value == AsmrAsyncImageState.Loading || (fadeIn && progress < 1f)) {
            val loadingAlpha = if (state.value == AsmrAsyncImageState.Loading) 1f else (1f - progress)
            val loadingModifier = if (loadingAlpha >= 0.999f) {
                sizedModifier
            } else {
                sizedModifier.graphicsLayer {
                    this.alpha = loadingAlpha
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
            }
            loading(loadingModifier)
        }
        if (p != null) {
            Image(
                painter = p,
                contentDescription = contentDescription,
                modifier = sizedModifier,
                contentScale = contentScale,
                alignment = alignment,
                alpha = if (fadeIn) alpha * progress else alpha,
                colorFilter = colorFilter
            )
        }
    }
}

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
