package com.asmr.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.asmr.player.data.repository.AudioMetadataCache
import com.asmr.player.util.AudioTechnicalMetadata
import kotlinx.coroutines.delay

internal enum class AudioSource { Local, Online }

internal fun audioSource(path: String): AudioSource? {
    val value = path.trim()
    return when {
        value.isBlank() -> null
        value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true) -> AudioSource.Online
        else -> AudioSource.Local
    }
}

@Composable
internal fun rememberAudioMetadata(path: String, loadMetadata: Boolean = true): AudioTechnicalMetadata? {
    val context = LocalContext.current.applicationContext
    val entry = remember(path) { AudioMetadataCache.entry(path) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val metadata = remember(entry) { mutableStateOf(entry.metadata.value) }
    LaunchedEffect(entry, loadMetadata, lifecycleOwner) {
        if (loadMetadata) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                entry.metadata.collect { metadata.value = it }
            }
        }
    }
    LaunchedEffect(entry, loadMetadata, lifecycleOwner) {
        if (loadMetadata && audioSource(path) == AudioSource.Local) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(200)
                AudioMetadataCache.loadLocal(context, path, entry)
            }
        }
    }
    return metadata.value
}
