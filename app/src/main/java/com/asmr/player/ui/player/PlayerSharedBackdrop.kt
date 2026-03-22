package com.asmr.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.ui.common.rememberComputedDominantColorCenterWeighted
import com.asmr.player.ui.theme.AsmrTheme

@Composable
internal fun PlayerSharedBackdrop(
    playback: PlaybackSnapshot,
    enabled: Boolean,
    clarity: Float,
    artworkAlignment: Alignment
) {
    if (!enabled) return

    val colorScheme = AsmrTheme.colorScheme
    val item = playback.currentMediaItem
    val metadata = item?.mediaMetadata
    val uriText = item?.localConfiguration?.uri?.toString().orEmpty()
    val mimeType = item?.localConfiguration?.mimeType.orEmpty()
    val ext = uriText.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
    val isVideo = metadata?.extras?.getBoolean("is_video") == true ||
        mimeType.startsWith("video/") ||
        ext in setOf("mp4", "m4v", "webm", "mkv", "mov")

    if (isVideo) return

    val dominantColorResult by rememberComputedDominantColorCenterWeighted(
        model = metadata?.artworkUri,
        defaultColor = colorScheme.background
    )
    val backgroundArtwork = remember(metadata?.artworkUri) {
        metadata?.artworkUri?.takeUnless { uri ->
            val uriTextValue = uri.toString()
            uri.scheme.equals("android.resource", ignoreCase = true) ||
                uriTextValue.contains("ic_placeholder", ignoreCase = true)
        }
    }

    CoverArtworkBackground(
        artworkModel = backgroundArtwork,
        enabled = enabled,
        clarity = clarity,
        overlayBaseColor = colorScheme.background,
        tintBaseColor = dominantColorResult.color ?: colorScheme.primary,
        artworkAlignment = artworkAlignment,
        isDark = colorScheme.isDark
    )
}
