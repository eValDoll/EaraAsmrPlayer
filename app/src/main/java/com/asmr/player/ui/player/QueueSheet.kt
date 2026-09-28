package com.asmr.player.ui.player

import com.asmr.player.ui.common.AudioMetadataLine
import com.asmr.player.ui.common.audioTrailingText
import com.asmr.player.ui.common.rememberTrackFileSizeText
import com.asmr.player.ui.common.audioSource
import com.asmr.player.ui.common.rememberAudioMetadata
import com.asmr.player.ui.common.formatStoredCv
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.playback.EXTRA_ALBUM_CV
import com.asmr.player.util.Formatting
import androidx.media3.common.MediaItem
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheetContent(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMediaId by remember(viewModel) {
        viewModel.playback
            .map { snapshot -> snapshot.currentMediaItem?.mediaId.orEmpty() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = "")
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentDurationMs by viewModel.resolvedDurationMs.collectAsStateWithLifecycle()
    val colorScheme = AsmrTheme.colorScheme
    val listState = rememberLazyListState()

    val currentIndex = remember(queue, currentMediaId) {
        queue.indexOfFirst { it.mediaId == currentMediaId }
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
                .weight(1f, fill = true),
            flingBehavior = rememberCalmScrollableFlingBehavior(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            itemsIndexed(queue, key = { idx, it -> "${it.mediaId}#$idx" }) { index, mediaItem ->
                val title = mediaItem.mediaMetadata.title?.toString().orEmpty().ifBlank { mediaItem.mediaId }
                val details = rememberQueueAudioDetails(mediaItem, !listState.isScrollInProgress)
                val uriText = mediaItem.localConfiguration?.uri?.toString().orEmpty()
                val audioMetadata = rememberAudioMetadata(uriText, !listState.isScrollInProgress)
                val sizeText = rememberTrackFileSizeText(uriText, !listState.isScrollInProgress)
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
                        AsmrAsyncImage(
                            model = mediaItem.mediaMetadata.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholderCornerRadius = 6,
                            peekAnySizeForInitial = true,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (selected) colorScheme.primary else colorScheme.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            AudioMetadataLine(
                                text = details.cv,
                                trailingText = remember(details.durationMs, selected, currentDurationMs, audioMetadata?.durationSeconds, sizeText) {
                                    audioTrailingText(
                                        Formatting.formatTrackSeconds(
                                            (details.durationMs ?: currentDurationMs.takeIf { selected && it > 0 })?.div(1000.0)
                                                ?: audioMetadata?.durationSeconds
                                        ),
                                        sizeText,
                                    )
                                },
                                source = audioSource(uriText),
                                quality = audioMetadata?.quality,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.textSecondary
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

private data class QueueAudioDetails(val cv: String, val durationMs: Long?)

@Composable
private fun rememberQueueAudioDetails(item: MediaItem, loadMetadata: Boolean): QueueAudioDetails {
    val context = LocalContext.current.applicationContext
    val metadata = item.mediaMetadata
    val initial = remember(item) {
        QueueAudioDetails(
            cv = formatStoredCv(metadata.artist?.toString().orEmpty(), metadata.extras?.getString(EXTRA_ALBUM_CV)),
            durationMs = metadata.durationMs?.takeIf { it > 0 },
        )
    }
    val attempted = remember(item) { booleanArrayOf(false) }
    val details by produceState(initialValue = initial, item, loadMetadata) {
        if (!loadMetadata || attempted[0]) return@produceState
        if (metadata.extras?.containsKey(EXTRA_ALBUM_CV) == true && initial.durationMs != null) return@produceState
        delay(200)
        value = withContext(Dispatchers.IO) {
            val db = AppDatabaseProvider.get(context)
            val trackId = metadata.extras?.getLong("track_id") ?: 0L
            val uri = item.localConfiguration?.uri
            val path = if (uri?.scheme == "file") uri.path.orEmpty() else uri?.toString().orEmpty()
            val track = if (trackId > 0) db.trackDao().getTrackByIdOnce(trackId)
                else db.trackDao().getTrackByPathOnce(path.ifBlank { item.mediaId })
            val albumId = track?.albumId ?: metadata.extras?.getLong("album_id") ?: 0L
            val album = if (albumId > 0) db.albumDao().getAlbumById(albumId) else null
            QueueAudioDetails(
                cv = formatStoredCv(metadata.artist?.toString().orEmpty(), album?.cv ?: metadata.extras?.getString(EXTRA_ALBUM_CV)),
                durationMs = initial.durationMs ?: track?.duration?.takeIf { it > 0 }?.let { (it * 1000).toLong() },
            )
        }
        attempted[0] = true
    }
    return details
}
