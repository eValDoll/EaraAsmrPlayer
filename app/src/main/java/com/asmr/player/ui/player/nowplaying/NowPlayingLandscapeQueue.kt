package com.asmr.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.theme.AsmrTheme

@Composable
internal fun TabletLandscapeQueuePanel(
    viewModel: PlayerViewModel,
    currentMediaId: String,
    isPlaying: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val colorScheme = AsmrTheme.colorScheme
    val listState = rememberLazyListState()
    val currentIndex = remember(queue, currentMediaId) {
        queue.indexOfFirst { it.mediaId == currentMediaId }
    }

    LaunchedEffect(expanded, currentIndex, queue.size) {
        if (expanded && currentIndex in queue.indices) {
            listState.scrollToItem(currentIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 380.dp)
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = colorScheme.onSurface.copy(alpha = 0.14f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                tint = colorScheme.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "播放队列",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = colorScheme.textPrimary
            )
            if (queue.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${queue.size} 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.textTertiary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "收起播放列表" else "展开播放列表",
                tint = colorScheme.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(durationMillis = 180, delayMillis = 40)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(durationMillis = 140))
        ) {
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "播放队列为空",
                        modifier = Modifier.padding(horizontal = 32.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.textTertiary
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 136.dp),
                    flingBehavior = rememberCalmScrollableFlingBehavior(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    itemsIndexed(
                        items = queue,
                        key = { index, mediaItem -> "${mediaItem.mediaId}#$index" },
                        contentType = { _, _ -> "landscapeQueueItem" }
                    ) { index, mediaItem ->
                        val selected = index == currentIndex
                        val title = mediaItem.mediaMetadata.title
                            ?.toString()
                            .orEmpty()
                            .ifBlank { mediaItem.mediaId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clickable(enabled = index != currentIndex) {
                                    viewModel.playQueueIndex(index)
                                }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(
                                        if (selected) colorScheme.primaryStrong else Color.Transparent
                                    )
                            )
                            Text(
                                text = (index + 1).toString().padStart(2, '0'),
                                modifier = Modifier.width(30.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) colorScheme.primaryStrong else colorScheme.textTertiary
                            )
                            Text(
                                text = title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (selected) colorScheme.primaryStrong else colorScheme.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (selected && isPlaying) {
                                Text(
                                    text = "播放中",
                                    modifier = Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.primaryStrong
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = colorScheme.onSurface.copy(alpha = 0.14f)
        )
    }
}
