package com.asmr.player.ui.player

import android.net.Uri
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

    val artworkModel = remember(metadata?.artworkUri) {
        sanitizeBackdropArtworkModel(metadata?.artworkUri)
    }
    val dominantColorResult by rememberComputedDominantColorCenterWeighted(
        model = artworkModel,
        defaultColor = colorScheme.background
    )

    CoverArtworkBackground(
        artworkModel = artworkModel,
        enabled = enabled,
        clarity = clarity,
        overlayBaseColor = colorScheme.background,
        tintBaseColor = dominantColorResult.color ?: colorScheme.primarySoft,
        artworkAlignment = artworkAlignment,
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
