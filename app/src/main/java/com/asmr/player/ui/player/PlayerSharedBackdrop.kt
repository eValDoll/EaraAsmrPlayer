package com.asmr.player.ui.player

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.media3.common.MediaItem
import com.asmr.player.data.settings.PlayerBackgroundMode
import com.asmr.player.ui.theme.AsmrTheme

@Composable
internal fun PlayerSharedBackdrop(
    mediaItem: MediaItem?,
    backgroundMode: PlayerBackgroundMode,
    coverEnabled: Boolean,
    clarity: Float,
    artworkAlignment: Alignment,
    isPlaying: Boolean = true,
    isActive: Boolean = true,
    dynamicEnabled: Boolean = true
) {
    val appColors = AsmrTheme.colorScheme
    val colorScheme = remember(appColors, backgroundMode, coverEnabled) {
        playerSurfaceColors(appColors, backgroundMode, coverEnabled)
    }
    val metadata = mediaItem?.mediaMetadata
    val uriText = mediaItem?.localConfiguration?.uri?.toString().orEmpty()
    val mimeType = mediaItem?.localConfiguration?.mimeType.orEmpty()
    val ext = uriText.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
    val isVideo = metadata?.extras?.getBoolean("is_video") == true ||
        mimeType.startsWith("video/") ||
        ext in setOf("mp4", "m4v", "webm", "mkv", "mov")

    val artworkModel = remember(metadata?.artworkUri) {
        sanitizeBackdropArtworkModel(metadata?.artworkUri)
    }
    val tintBaseColor = if (backgroundMode == PlayerBackgroundMode.Solid && coverEnabled) {
        val playerThemeColors = rememberPlayerThemeColors(
            mediaItem = mediaItem,
            colorScheme = colorScheme,
            coverBackgroundEnabled = coverEnabled,
            transitionDurationMs = 0,
            cachedTransitionDurationMs = 0
        )
        if (isVideo) playerThemeColors.videoBackdropColor else playerThemeColors.backdropTintColor
    } else colorScheme.background
    CoverArtworkBackground(
        artworkModel = artworkModel,
        backgroundMode = backgroundMode,
        coverEnabled = coverEnabled,
        clarity = clarity,
        artworkAlignment = artworkAlignment,
        isPlaying = isPlaying,
        isActive = isActive,
        dynamicEnabled = dynamicEnabled,
        overlayBaseColor = colorScheme.background,
        tintBaseColor = tintBaseColor,
        isDark = colorScheme.isDark
    )
}

internal fun sanitizeBackdropArtworkModel(artworkUri: Uri?): Uri? {
    return artworkUri?.takeUnless { uri ->
        val uriTextValue = uri.toString()
        uri.scheme.equals("android.resource", ignoreCase = true) ||
            uriTextValue.contains("ic_placeholder", ignoreCase = true)
    }
}
