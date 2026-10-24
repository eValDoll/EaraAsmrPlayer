package com.asmr.player.ui.playlists

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asmr.player.data.local.db.entities.PlaylistItemWithSubtitles
import com.asmr.player.data.local.db.entities.PlaylistPlaybackEntity
import com.asmr.player.translation.translatedPageText
import com.asmr.player.ui.common.formatStoredCv
import com.asmr.player.ui.common.resolveMainPageBackgroundColor
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.Formatting
import kotlin.math.roundToLong

internal const val PLAYLIST_TRACK_HERO_TAG = "playlistTrackHero"
internal const val PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG = "playlistTrackHeroPlayButton"
internal const val PLAYLIST_TRACK_HERO_TIMELINE_TAG = "playlistTrackHeroTimeline"
internal const val PLAYLIST_TRACK_HERO_ELAPSED_TAG = "playlistTrackHeroElapsed"
internal const val PLAYLIST_TRACK_HERO_DURATION_TAG = "playlistTrackHeroDuration"

@Composable
internal fun PlaylistTrackHero(
    item: PlaylistItemWithSubtitles,
    playback: PlaylistPlaybackEntity?,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    positionMs: () -> Long = { playback?.positionMs ?: 0L },
    durationMs: Long = playback?.durationMs ?: 0L,
    isActive: Boolean = true,
) {
    val colors = AsmrTheme.colorScheme
    val pageBackground = resolveMainPageBackgroundColor(colors)
    val compactHeight = LocalConfiguration.current.screenHeightDp < 500
    val fontScale = LocalDensity.current.fontScale
    val compactTextBackdrop = compactHeight || fontScale > 1.35f
    val artworkGap = when {
        compactHeight -> 36.dp
        fontScale > 1.35f -> 52.dp
        else -> 96.dp
    }
    val minimumHeight = if (compactHeight) 216.dp else 292.dp
    val artwork = remember(item.playbackArtworkUri, item.artworkUri) {
        item.playbackArtworkUri.ifBlank { item.artworkUri }
    }
    val artist = remember(item.artist, item.albumCv) {
        formatStoredCv(item.artist, item.albumCv.takeIf { it.isNotBlank() })
    }
    val knownDurationMs = remember(durationMs, playback?.durationMs, item.duration) {
        durationMs.takeIf { it > 0L }
            ?: playback?.durationMs?.takeIf { it > 0L }
            ?: item.duration.takeIf { it.isFinite() && it > 0.0 }?.let { (it * 1_000).roundToLong() }
            ?: 0L
    }
    val backdropGradient = remember(pageBackground) {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.22f to pageBackground.copy(alpha = 0.08f),
            0.45f to pageBackground.copy(alpha = 0.82f),
            0.68f to pageBackground.copy(alpha = 0.97f),
            1f to pageBackground,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minimumHeight)
            .clipToBounds()
            .background(pageBackground)
            .testTag(PLAYLIST_TRACK_HERO_TAG),
        contentAlignment = Alignment.BottomCenter,
    ) {
        PlaylistHeroArtwork(
            model = artwork,
            isActive = isActive,
            modifier = Modifier.matchParentSize(),
            previewModel = item.artworkUri,
        )
        Box(
            modifier = Modifier.matchParentSize().drawWithCache {
                val gradient = if (compactTextBackdrop) {
                    val textStart = (artworkGap.toPx() / size.height.coerceAtLeast(1f)).coerceIn(0.001f, 0.99f)
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        textStart * 0.5f to pageBackground.copy(alpha = 0.42f),
                        textStart to pageBackground.copy(alpha = 0.94f),
                        1f to pageBackground,
                    )
                } else {
                    backdropGradient
                }
                onDrawBehind { drawRect(gradient) }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = artworkGap, bottom = 18.dp),
        ) {
            Text(
                text = translatedPageText(item.title.ifBlank { "未命名" }, fileName = true),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaylistHeroTimeline(
                    positionMs = positionMs,
                    durationMs = knownDurationMs,
                    hasPlayback = playback != null,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(48.dp).testTag(PLAYLIST_TRACK_HERO_PLAY_BUTTON_TAG),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = colors.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeroTimeline(
    positionMs: () -> Long,
    durationMs: Long,
    hasPlayback: Boolean,
    modifier: Modifier = Modifier,
) {
    val positionSeconds by remember(positionMs) {
        derivedStateOf { positionMs().coerceAtLeast(0L) / 1_000L }
    }
    val elapsed = remember(positionSeconds, hasPlayback) {
        if (hasPlayback) Formatting.formatTrackTime(positionSeconds * 1_000L) else ""
    }
    val duration = remember(durationMs) {
        durationMs.takeIf { it > 0L }?.let(Formatting::formatTrackTime).orEmpty()
    }
    val timeStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    val textColor = AsmrTheme.colorScheme.textSecondary
    Column(modifier = modifier.testTag(PLAYLIST_TRACK_HERO_TIMELINE_TAG)) {
        Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            if (hasPlayback && durationMs > 0L) {
                PlaylistHeroProgress(positionMs = positionMs, durationMs = durationMs, modifier = Modifier.fillMaxWidth())
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                text = elapsed,
                modifier = Modifier.weight(1f).testTag(PLAYLIST_TRACK_HERO_ELAPSED_TAG),
                style = timeStyle,
                color = textColor,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = duration,
                modifier = Modifier.weight(1f).testTag(PLAYLIST_TRACK_HERO_DURATION_TAG),
                style = timeStyle,
                color = textColor,
                textAlign = TextAlign.End,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaylistHeroProgress(
    positionMs: () -> Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val colors = AsmrTheme.colorScheme
    val positionSeconds by remember(positionMs) {
        derivedStateOf { positionMs().coerceAtLeast(0L) / 1_000L }
    }
    Canvas(
        modifier = modifier
            .height(3.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = (positionSeconds * 1_000.0 / durationMs).toFloat().coerceIn(0f, 1f),
                    range = 0f..1f,
                )
            },
    ) {
        val cornerRadius = CornerRadius(size.height / 2f)
        drawRoundRect(color = colors.primary.copy(alpha = 0.14f), cornerRadius = cornerRadius)
        val progress = (positionMs().coerceIn(0L, durationMs).toDouble() / durationMs).toFloat()
        if (progress > 0f) {
            drawRoundRect(
                color = colors.primary,
                size = Size(size.width * progress, size.height),
                cornerRadius = cornerRadius,
            )
        }
    }
}
