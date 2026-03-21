package com.asmr.player.ui.common

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

internal fun volumeRouteIcon(routeKind: AudioOutputRouteKind, isMuted: Boolean): ImageVector {
    return if (routeKind == AudioOutputRouteKind.Headphones) {
        Icons.Default.Headset
    } else if (isMuted) {
        Icons.Default.VolumeOff
    } else {
        Icons.Default.VolumeUp
    }
}
