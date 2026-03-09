package com.asmr.player.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheetContent(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val playback by viewModel.playback.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val colorScheme = AsmrTheme.colorScheme
    
    val currentId = playback.currentMediaItem?.mediaId.orEmpty()
    val currentIndex = remember(queue, currentId) { queue.indexOfFirst { it.mediaId == currentId } }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "当前播放队列",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.textSecondary
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            itemsIndexed(queue, key = { idx, it -> "${it.mediaId}#$idx" }) { index, mediaItem ->
                val title = mediaItem.mediaMetadata.title?.toString().orEmpty().ifBlank { mediaItem.mediaId }
                val artist = mediaItem.mediaMetadata.artist?.toString().orEmpty()
                val uriText = mediaItem.localConfiguration?.uri?.toString().orEmpty()
                val sourceLabel = if (uriText.startsWith("http", ignoreCase = true)) "在线" else "本地"
                val selected = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (index in queue.indices) viewModel.playQueueIndex(index)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(width = 22.dp, height = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            PlayingWaveIndicator(
                                isPlaying = playback.isPlaying,
                                color = colorScheme.primary,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                            color = if (selected) colorScheme.primary else colorScheme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (artist.isNotBlank()) "$sourceLabel · $artist" else sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { viewModel.removeFromQueue(index) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "移除",
                            tint = colorScheme.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayingWaveIndicator(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val fractions = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "playingWave")
        listOf(
            transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar0"
            ).value,
            transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 560, delayMillis = 110, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar1"
            ).value,
            transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = 220, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar2"
            ).value
        )
    } else {
        listOf(0.35f, 0.7f, 0.45f)
    }

    Canvas(modifier = modifier) {
        val barCount = 3
        val barWidth = size.width / (barCount * 2f - 1f)
        val gap = barWidth
        val radius = barWidth / 2f

        for (i in 0 until barCount) {
            val height = (size.height * fractions[i].coerceIn(0f, 1f)).coerceAtLeast(1f)
            val left = i * (barWidth + gap)
            val top = size.height - height
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            )
        }
    }
}
