package com.asmr.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.delay

enum class MiniPlayerDisplayMode {
    CoverOnly,
    Expanded
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MiniPlayer(
    displayMode: MiniPlayerDisplayMode,
    onDisplayModeChange: (MiniPlayerDisplayMode) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    largeLayout: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playback by viewModel.playback.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val item = playback.currentMediaItem ?: return
    val metadata = item.mediaMetadata
    val colorScheme = AsmrTheme.colorScheme
    val currentMediaId = item.mediaId
    val barHeight = if (largeLayout) 64.dp else 56.dp
    val coverSize = if (largeLayout) 60.dp else 52.dp
    val coverInset = 2.dp

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

    AnimatedContent(
        targetState = displayMode,
        modifier = modifier,
        transitionSpec = {
            fadeIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) togetherWith fadeOut(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) using SizeTransform(clip = false)
        },
        label = "miniPlayerMode"
    ) { mode ->
        when (mode) {
            MiniPlayerDisplayMode.CoverOnly -> {
                MiniPlayerCoverOnly(
                    artworkModel = metadata.artworkUri,
                    isPlaying = isPlayingEffective,
                    onExpand = { onDisplayModeChange(MiniPlayerDisplayMode.Expanded) },
                    largeLayout = largeLayout
                )
            }

            MiniPlayerDisplayMode.Expanded -> {
                BoxWithConstraints {
                    val widthProgress = ((maxWidth.value - 220f) / 280f).coerceIn(0f, 1f)
                    val controlsSpacing = ((if (largeLayout) 4f else 2f) + (if (largeLayout) 6f else 5f) * widthProgress).dp
                    val controlsButtonSize = ((if (largeLayout) 32f else 28f) + (if (largeLayout) 8f else 6f) * widthProgress).dp
                    val favoriteIconSize = if (largeLayout) 19.dp else 17.dp
                    val playbackIconSize = if (largeLayout) 22.dp else 20.dp
                    val queueIconSize = if (largeLayout) 20.dp else 18.dp

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight),
                        shape = RoundedCornerShape(
                            topStart = coverSize / 2,
                            bottomStart = coverSize / 2,
                            topEnd = if (largeLayout) 26.dp else 22.dp,
                            bottomEnd = if (largeLayout) 26.dp else 22.dp
                        ),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = colorScheme.primarySoft
                                .copy(alpha = if (colorScheme.isDark) 0.10f else 0.16f)
                                .compositeOver(colorScheme.surface)
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = coverInset, top = coverInset)
                                        .size(coverSize)
                                        .clip(CircleShape)
                                        .background(colorScheme.surfaceVariant)
                                        .clickable { onDisplayModeChange(MiniPlayerDisplayMode.CoverOnly) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsmrAsyncImage(
                                        model = metadata.artworkUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        placeholderCornerRadius = 52,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = if (largeLayout) 12.dp else 10.dp, end = 8.dp)
                                        .clickable(onClick = onOpenNowPlaying),
                                    verticalArrangement = Arrangement.Center
                                ) {
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
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(end = 8.dp),
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
                                        modifier = Modifier.size(favoriteIconSize)
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
                                        modifier = Modifier.size(playbackIconSize)
                                    )
                                }
                                    IconButton(
                                        onClick = onOpenQueue,
                                        modifier = Modifier.size(controlsButtonSize)
                                    ) {
                                        Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = colorScheme.onSurface,
                                        modifier = Modifier.size(queueIconSize)
                                    )
                                }
                            }
                            }

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (largeLayout) 3.dp else 2.dp),
                                color = colorScheme.primary,
                                trackColor = colorScheme.primary.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerCoverOnly(
    artworkModel: Any?,
    isPlaying: Boolean,
    onExpand: () -> Unit,
    largeLayout: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val compactMinWidth = if (largeLayout) 76.dp else 64.dp
    val barHeight = if (largeLayout) 64.dp else 56.dp
    val coverSize = if (largeLayout) 60.dp else 52.dp
    Box(
        modifier = modifier
            .widthIn(min = compactMinWidth)
            .height(barHeight),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(coverSize)
                .clip(CircleShape)
                .background(colorScheme.surface)
                .clickable(onClick = onExpand),
            contentAlignment = Alignment.Center
        ) {
            AsmrAsyncImage(
                model = artworkModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholderCornerRadius = if (largeLayout) 60 else 52,
                modifier = Modifier.fillMaxSize()
            )
            MiniPlayerActivityBars(
                isPlaying = isPlaying,
                modifier = Modifier.align(Alignment.BottomCenter),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun MiniPlayerActivityBars(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "miniPlayerBars")
    val heights = listOf(0, 60, 120, 180, 240, 300).map { delayMs ->
        transition.animateFloat(
            initialValue = 0.14f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 520,
                    delayMillis = delayMs,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "miniPlayerBar$delayMs"
        )
    }

    Row(
        modifier = modifier
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEachIndexed { index, height ->
            val maxHeight = 4.2f + (index % 3) * 0.9f + index * 0.15f
            Box(
                modifier = Modifier
                    .size(
                        width = 1.8.dp,
                        height = (if (isPlaying) 3f + height.value * maxHeight else 3f).dp
                    )
                    .clip(RoundedCornerShape(99.dp))
                    .background(tint)
            )
        }
    }
}
