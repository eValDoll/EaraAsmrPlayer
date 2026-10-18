package com.asmr.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asmr.player.ui.common.AsmrAsyncImage
import kotlin.math.pow

@Composable
fun CoverArtworkBackground(
    artworkModel: Any?,
    enabled: Boolean,
    clarity: Float,
    artworkAlignment: Alignment = Alignment.Center,
    isPlaying: Boolean = true,
    isActive: Boolean = true
) {
    if (!enabled) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resumed = remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed.value = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val style = remember(clarity) { coverArtworkBackdropStyle(clarity) }
    val artwork by rememberPlayerCoverBitmap(artworkModel, isActive && resumed.value)
    Box(Modifier.fillMaxSize().background(PlayerBackdropBaseColor))
    artwork?.let { bitmap ->
        if (style.flowingVisible) {
            FlowingArtworkBackground(
                artwork = bitmap,
                isPlaying = isPlaying,
                isActive = isActive && resumed.value
            )
        }
    }
    if (artworkModel != null && style.artworkAlpha > 0f) {
        AsmrAsyncImage(
            model = artworkModel,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alignment = artworkAlignment,
            alpha = style.artworkAlpha,
            placeholder = {},
            empty = {},
            loading = {},
            retainPainterDuringReload = true
        )
    }
}

internal data class BackdropStyle(
    val artworkAlpha: Float,
    val flowingVisible: Boolean
)

internal fun coverArtworkBackdropStyle(clarity: Float): BackdropStyle {
    val alpha = (clarity.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f).pow(2.15f)
    return BackdropStyle(artworkAlpha = alpha, flowingVisible = alpha < 1f)
}
