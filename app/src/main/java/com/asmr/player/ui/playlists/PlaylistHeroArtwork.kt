package com.asmr.player.ui.playlists

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.asmr.player.cache.CachePolicy
import com.asmr.player.cache.ImageCacheEntryPoint
import com.asmr.player.ui.common.DiscPlaceholder
import com.asmr.player.ui.common.LocalPageAnimationsActive
import com.asmr.player.ui.common.withPageAnimationClock
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val ArtworkFadeDurationMs = 220
private const val ArtworkSizeStep = 64

private data class ArtworkRequest(val model: String, val size: IntSize)

internal data class PlaylistHeroArtworkFrame(
    val model: String,
    val size: IntSize,
    val bitmap: ImageBitmap?,
    val isPreview: Boolean = false,
) {
    val painter: BitmapPainter? = bitmap?.let { BitmapPainter(it) }
}

private fun PlaylistHeroArtworkFrame.covers(request: ArtworkRequest): Boolean = model == request.model &&
        !isPreview &&
        (request.model.isEmpty() ||
            (size.width >= request.size.width && size.height >= request.size.height))

private fun PlaylistHeroArtworkFrame.usesSameBitmap(other: PlaylistHeroArtworkFrame): Boolean =
    bitmap?.asAndroidBitmap() === other.bitmap?.asAndroidBitmap()

internal class PlaylistHeroArtworkState {
    var settledFrame by mutableStateOf<PlaylistHeroArtworkFrame?>(null)
        internal set
    var incomingFrame by mutableStateOf<PlaylistHeroArtworkFrame?>(null)
        internal set
    val incomingAlpha = Animatable(0f)
}

@Composable
internal fun PlaylistHeroArtwork(
    model: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    previewModel: String = model,
) {
    var requestSize by remember { mutableStateOf(IntSize.Zero) }
    val state = rememberPlaylistHeroArtwork(model, isActive, requestSize, previewModel)
    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    val roundedSize = IntSize(
                        width = ((size.width + ArtworkSizeStep - 1) / ArtworkSizeStep) * ArtworkSizeStep,
                        height = ((size.height + ArtworkSizeStep - 1) / ArtworkSizeStep) * ArtworkSizeStep,
                    )
                    if (requestSize != roundedSize) requestSize = roundedSize
                }
            },
    ) {
        ArtworkLayer(frame = state.settledFrame, modifier = Modifier.fillMaxSize())
        state.incomingFrame?.let { frame ->
            ArtworkLayer(
                frame = frame,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = state.incomingAlpha.value
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            )
        }
    }
}

@Composable
internal fun rememberPlaylistHeroArtwork(
    model: String,
    isActive: Boolean,
    requestSize: IntSize,
    previewModel: String = model,
): PlaylistHeroArtworkState {
    val appContext = LocalContext.current.applicationContext
    val managerProvider = remember(appContext) {
        lazy {
            EntryPointAccessors.fromApplication(appContext, ImageCacheEntryPoint::class.java).imageCacheManager()
        }
    }
    val normalizedModel = remember(model) { model.trim() }
    val imageModel = remember(normalizedModel) {
        if (normalizedModel.startsWith('/')) File(normalizedModel) else normalizedModel
    }
    val normalizedPreviewModel = remember(previewModel) { previewModel.trim() }
    val previewImageModel = remember(normalizedPreviewModel) {
        if (normalizedPreviewModel.startsWith('/')) File(normalizedPreviewModel) else normalizedPreviewModel
    }
    val request = remember(normalizedModel, requestSize) { ArtworkRequest(normalizedModel, requestSize) }
    val latestRequest by rememberUpdatedState(request)
    val animationsActive = rememberUpdatedState(LocalPageAnimationsActive.current)
    val state = remember { PlaylistHeroArtworkState() }
    var readyFrame by remember { mutableStateOf<PlaylistHeroArtworkFrame?>(null) }

    LaunchedEffect(request, isActive) {
        readyFrame = null
        if (!isActive || request.size == IntSize.Zero) return@LaunchedEffect
        val reusableFrame = state.incomingFrame?.takeIf { it.covers(request) }
            ?: state.settledFrame?.takeIf { it.covers(request) }
        if (reusableFrame != null) {
            readyFrame = reusableFrame
            return@LaunchedEffect
        }
        if (request.model.isEmpty()) {
            readyFrame = PlaylistHeroArtworkFrame(request.model, request.size, bitmap = null)
            return@LaunchedEffect
        }
        try {
            val manager = withContext(Dispatchers.IO) { managerProvider.value }
            val existingPreview = state.incomingFrame?.takeIf { it.model == request.model }
                ?: state.settledFrame?.takeIf { it.model == request.model }
            if (existingPreview != null) {
                readyFrame = existingPreview
            } else {
                val cachedPreview = withContext(Dispatchers.IO) {
                    manager.peekAnySize(imageModel)?.let { it to true }
                        ?: normalizedPreviewModel.takeIf { it.isNotEmpty() && it != request.model }
                            ?.let { manager.peekAnySize(previewImageModel)?.let { bitmap -> bitmap to false } }
                }
                currentCoroutineContext().ensureActive()
                if (latestRequest != request) return@LaunchedEffect
                cachedPreview?.let { (bitmap, matchesTargetModel) ->
                    val cachedSize = IntSize(bitmap.width, bitmap.height)
                    val complete = matchesTargetModel && cachedSize.width >= request.size.width &&
                        cachedSize.height >= request.size.height
                    readyFrame = PlaylistHeroArtworkFrame(
                        model = request.model,
                        size = if (complete) request.size else cachedSize,
                        bitmap = bitmap,
                        isPreview = !complete,
                    )
                    if (complete) return@LaunchedEffect
                }
            }
            val bitmap = withTimeoutOrNull(15_000L) {
                manager.loadImage(model = imageModel, size = request.size, cachePolicy = CachePolicy.DEFAULT)
            } ?: return@LaunchedEffect
            currentCoroutineContext().ensureActive()
            if (latestRequest == request) {
                readyFrame = PlaylistHeroArtworkFrame(request.model, request.size, bitmap)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A failed replacement leaves the last usable cover visible.
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        state.incomingFrame?.let { frame ->
            val remainingDurationMs = (ArtworkFadeDurationMs * (1f - state.incomingAlpha.value))
                .roundToInt().coerceAtLeast(1)
            withPageAnimationClock(animationsActive) {
                state.incomingAlpha.animateTo(1f, tween(remainingDurationMs, easing = LinearEasing))
            }
            state.settledFrame = frame
            state.incomingFrame = null
        }
        snapshotFlow { readyFrame }
            .filterNotNull()
            .conflate()
            .collect { frame ->
                if (frame.model != latestRequest.model || state.settledFrame == frame) return@collect
                if ((frame.bitmap == null && state.settledFrame?.bitmap == null) ||
                    state.settledFrame?.usesSameBitmap(frame) == true
                ) {
                    state.settledFrame = frame
                    return@collect
                }
                state.incomingAlpha.snapTo(0f)
                state.incomingFrame = frame
                withPageAnimationClock(animationsActive) {
                    state.incomingAlpha.animateTo(1f, tween(ArtworkFadeDurationMs, easing = LinearEasing))
                }
                state.settledFrame = frame
                state.incomingFrame = null
            }
    }
    return state
}

@Composable
private fun ArtworkLayer(frame: PlaylistHeroArtworkFrame?, modifier: Modifier) {
    val painter = frame?.painter
    if (painter == null) {
        DiscPlaceholder(modifier = modifier, cornerRadius = 0)
    } else {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
        )
    }
}
