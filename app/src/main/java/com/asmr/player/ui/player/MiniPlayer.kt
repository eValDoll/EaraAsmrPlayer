package com.asmr.player.ui.player
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.ui.common.AsmrAsyncImage

import androidx.compose.ui.text.font.FontWeight
import com.asmr.player.ui.theme.AsmrTheme
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

val MiniPlayerOverlayHeight = 96.dp

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MiniPlayer(
    onClick: () -> Unit,
    onOpenQueue: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playback by viewModel.playback.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val item = playback.currentMediaItem ?: return
    val metadata = item.mediaMetadata
    val colorScheme = AsmrTheme.colorScheme
    val currentMediaId = item.mediaId

    var optimisticIsPlaying by remember { mutableStateOf<Boolean?>(null) }
    var stableMediaId by remember { mutableStateOf(currentMediaId) }
    val isPlayingEffective = optimisticIsPlaying ?: playback.isPlaying

    LaunchedEffect(currentMediaId) {
        if (currentMediaId != stableMediaId) {
            stableMediaId = currentMediaId
            optimisticIsPlaying = null
        }
    }

    LaunchedEffect(optimisticIsPlaying) {
        if (optimisticIsPlaying != null) {
            delay(1_500)
            optimisticIsPlaying = null
        }
    }
    
    val progress = if (playback.durationMs > 0) {
        (playback.positionMs.toDouble() / playback.durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 14.dp)
            .height(72.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val widthProgress = ((maxWidth.value - 360f) / 520f).coerceIn(0f, 1f)
        val controlsEndPadding = (8f + 8f * widthProgress).dp
        val controlsSpacing = (2f + 6f * widthProgress).dp
        val controlsButtonSize = (36f + 8f * widthProgress).dp

        // 主卡片部分
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 48.dp, end = controlsEndPadding), // 增加左侧间距
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ... 标题副标题保持不变 ...
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = metadata.title?.toString().orEmpty().ifBlank { "未播放" },
                            modifier = Modifier.basicMarquee(),
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.textPrimary
                        )
                        Text(
                            text = metadata.artist?.toString().orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.textSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(controlsSpacing)
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleFavorite() },
                            modifier = Modifier.size(controlsButtonSize)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFavorite) Color.Red else colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                optimisticIsPlaying = !(optimisticIsPlaying ?: playback.isPlaying)
                                viewModel.togglePlayPause()
                            },
                            modifier = Modifier.size(controlsButtonSize)
                        ) {
                            Icon(
                                imageVector = if (isPlayingEffective) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenQueue,
                            modifier = Modifier.size(controlsButtonSize)
                        ) {
                            Icon(
                                Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = colorScheme.primary,
                    trackColor = colorScheme.primary.copy(alpha = 0.1f)
                )
            }
        }

        // 圆形超出头像
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp) // 头像更靠左
                .size(64.dp) // 半径增大，从 56dp 增大到 64dp
                .graphicsLayer {
                    shadowElevation = 16f
                    shape = CircleShape
                    clip = false
                    translationY = 4f
                    translationX = 2f
                }
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            AsmrAsyncImage(
                model = metadata.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholderCornerRadius = 64,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
