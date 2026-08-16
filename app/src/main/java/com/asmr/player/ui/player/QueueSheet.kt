package com.asmr.player.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private data class QueuePlaybackPresentation(
    val currentMediaId: String = "",
    val isPlaying: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheetContent(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playback by remember(viewModel) {
        viewModel.playback
            .map { snapshot ->
                QueuePlaybackPresentation(
                    currentMediaId = snapshot.currentMediaItem?.mediaId.orEmpty(),
                    isPlaying = snapshot.isPlaying
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = QueuePlaybackPresentation())
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val colorScheme = AsmrTheme.colorScheme
    val listState = rememberLazyListState()

    val currentIndex = remember(queue, playback.currentMediaId) {
        queue.indexOfFirst { it.mediaId == playback.currentMediaId }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "当前播放队列",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.textSecondary
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .thinScrollbar(listState),
            flingBehavior = rememberCalmScrollableFlingBehavior(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            itemsIndexed(queue, key = { idx, it -> "${it.mediaId}#$idx" }) { index, mediaItem ->
                val title = mediaItem.mediaMetadata.title?.toString().orEmpty().ifBlank { mediaItem.mediaId }
                val artist = mediaItem.mediaMetadata.artist?.toString().orEmpty()
                val uriText = mediaItem.localConfiguration?.uri?.toString().orEmpty()
                val sourceLabel = if (uriText.startsWith("http", ignoreCase = true)) "在线" else "本地"
                val selected = index == currentIndex

                Column {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                ),
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
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "移除",
                                tint = colorScheme.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (index < queue.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            thickness = 0.5.dp,
                            color = colorScheme.textSecondary.copy(alpha = 0.18f)
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
    val animatedFractions = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "playingWave")
        arrayOf(
            transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar0"
            ),
            transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 560, delayMillis = 110, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar1"
            ),
            transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = 220, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar2"
            )
        )
    } else {
        null
    }

    Canvas(modifier = modifier) {
        val barCount = 3
        val barWidth = size.width / (barCount * 2f - 1f)
        val gap = barWidth
        val radius = barWidth / 2f

        for (i in 0 until barCount) {
            val fraction = animatedFractions?.get(i)?.value ?: when (i) {
                0 -> 0.35f
                1 -> 0.7f
                else -> 0.45f
            }
            val height = (size.height * fraction.coerceIn(0f, 1f)).coerceAtLeast(1f)
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
