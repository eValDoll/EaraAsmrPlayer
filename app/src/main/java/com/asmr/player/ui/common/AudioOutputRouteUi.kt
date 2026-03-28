package com.asmr.player.ui.common

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.asmr.player.service.AudioOutputRouteKind
import com.asmr.player.service.resolveCurrentAudioOutputRouteKind

@Composable
internal fun rememberCurrentAudioOutputRouteKind(): AudioOutputRouteKind {
    val context = LocalContext.current
    val audioManager = remember(context.applicationContext) {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var routeKind by remember(audioManager) {
        mutableStateOf(resolveCurrentAudioOutputRouteKind(audioManager))
    }

    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>) {
                routeKind = resolveCurrentAudioOutputRouteKind(audioManager)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>) {
                routeKind = resolveCurrentAudioOutputRouteKind(audioManager)
            }
        }
        routeKind = resolveCurrentAudioOutputRouteKind(audioManager)
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose {
            runCatching { audioManager.unregisterAudioDeviceCallback(callback) }
        }
    }

    return routeKind
}

internal fun volumeRouteIcon(routeKind: AudioOutputRouteKind): ImageVector {
    return if (routeKind == AudioOutputRouteKind.Headphones) {
        Icons.Default.Headset
    } else {
        Icons.Default.VolumeUp
    }
}

@Composable
internal fun AudioOutputRouteIcon(
    routeKind: AudioOutputRouteKind,
    isMuted: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = volumeRouteIcon(routeKind),
            contentDescription = contentDescription,
            tint = tint
        )
        if (isMuted) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = size.minDimension * 0.12f
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.24f, size.height * 0.82f),
                    end = Offset(size.width * 0.80f, size.height * 0.20f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
