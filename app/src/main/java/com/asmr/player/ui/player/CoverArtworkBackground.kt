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
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asmr.player.data.settings.PlayerBackgroundMode

@Composable
fun CoverArtworkBackground(
    artworkModel: Any?,
    backgroundMode: PlayerBackgroundMode,
    coverEnabled: Boolean,
    clarity: Float,
    artworkAlignment: Alignment = Alignment.Center,
    isPlaying: Boolean = true,
    isActive: Boolean = true,
    dynamicEnabled: Boolean = true,
    overlayBaseColor: Color,
    tintBaseColor: Color = overlayBaseColor,
    isDark: Boolean
) {
    if (backgroundMode != PlayerBackgroundMode.Theme || !coverEnabled) {
        Box(Modifier.fillMaxSize().background(overlayBaseColor))
    }
    if (backgroundMode == PlayerBackgroundMode.Solid) return
    if (backgroundMode == PlayerBackgroundMode.Theme) {
        StaticArtworkBackground(
            artworkModel = artworkModel,
            enabled = coverEnabled,
            clarity = clarity,
            overlayBaseColor = overlayBaseColor,
            tintBaseColor = tintBaseColor,
            artworkAlignment = artworkAlignment,
            isDark = isDark
        )
        return
    }

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
    val artwork by rememberPlayerCoverBitmap(artworkModel, isActive && resumed.value)
    artwork?.let { bitmap ->
        FlowingArtworkBackground(
            artwork = bitmap,
            isPlaying = isPlaying,
            isActive = isActive && resumed.value,
            dynamicEnabled = dynamicEnabled
        )
    }
}
