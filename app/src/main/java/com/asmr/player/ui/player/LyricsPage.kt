package com.asmr.player.ui.player

import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.rememberDominantColorCenterWeighted

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings

@Composable
fun LyricsPage(
    onBack: () -> Unit,
    onSeekTo: (Long) -> Unit,
    playerViewModel: PlayerViewModel,
    coverBackgroundEnabled: Boolean,
    coverBackgroundClarity: Float,
    coverPreviewMode: CoverPreviewMode,
    lyricsPageSettings: LyricsPageSettings,
    viewModel: LyricsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playback by playerViewModel.playback.collectAsState()
    val position = playback.positionMs
    val colorScheme = AsmrTheme.colorScheme
    val artwork = playback.currentMediaItem?.mediaMetadata?.artworkUri
    val dominantColor by rememberDominantColorCenterWeighted(
        model = artwork,
        defaultColor = colorScheme.background
    )
    val lyricColors = rememberLyricReadableColors(
        accentColor = dominantColor,
        coverBackgroundEnabled = coverBackgroundEnabled,
        coverBackgroundClarity = coverBackgroundClarity
    )
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useDragPreview = coverBackgroundEnabled && coverPreviewMode == CoverPreviewMode.Drag
    val useMotionPreview = coverBackgroundEnabled && coverPreviewMode == CoverPreviewMode.Motion
    val coverMotionState = rememberCoverMotionState(
        enabled = useMotionPreview,
        resetKey = playback.currentMediaItem?.mediaId
    )
    val coverDragPreviewState = rememberCoverDragPreviewState(
        enabled = useDragPreview,
        resetKey = playback.currentMediaItem?.mediaId
    )
    val artworkAlignment = when {
        useDragPreview -> coverDragPreviewState.toAlignment()
        useMotionPreview -> coverMotionState.toAlignment()
        else -> Alignment.Center
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .coverDragPreviewGesture(
                enabled = useDragPreview,
                state = coverDragPreviewState,
                minPointers = 2
            )
    ) {
        CoverArtworkBackground(
            artworkModel = artwork,
            enabled = coverBackgroundEnabled,
            clarity = coverBackgroundClarity,
            overlayBaseColor = colorScheme.background,
            tintBaseColor = dominantColor,
            artworkAlignment = artworkAlignment,
            isDark = colorScheme.isDark
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isLandscape) 4.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.KeyboardArrowDown, 
                        contentDescription = null,
                        modifier = Modifier.size(if (isLandscape) 24.dp else 28.dp)
                    )
                }
                val headerShadow = if (colorScheme.isDark) {
                    Shadow(color = Color.Black.copy(alpha = 0.5f), offset = Offset(0f, 2f), blurRadius = 4f)
                } else {
                    Shadow(color = Color.Black.copy(alpha = 0.15f), offset = Offset(0f, 1f), blurRadius = 2f)
                }

                Text(
                    text = uiState.title.ifBlank { "歌词" },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = if (isLandscape) 14.sp else 16.sp,
                        shadow = headerShadow
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                AppleLyricsView(
                    lyrics = uiState.lyrics,
                    currentPosition = position,
                    onSeekTo = onSeekTo,
                    colors = lyricColors,
                    modifier = Modifier.fillMaxSize(),
                    isLandscape = isLandscape,
                    settings = lyricsPageSettings
                )
            }
        }
    }
}

// LyricsList has been replaced by AppleLyricsView
