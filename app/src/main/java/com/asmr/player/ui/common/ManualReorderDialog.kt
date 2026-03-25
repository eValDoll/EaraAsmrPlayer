package com.asmr.player.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.asmr.player.ui.common.reorderable.ItemPosition
import com.asmr.player.ui.common.reorderable.ReorderableItem
import com.asmr.player.ui.common.reorderable.detectReorderAfterLongPress
import com.asmr.player.ui.common.reorderable.rememberReorderableLazyListState
import com.asmr.player.ui.common.reorderable.reorderable
import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.delay

internal data class ManualReorderListItem(
    val key: String,
    val title: String,
    val subtitle: String = "",
    val artworkModel: Any? = null,
    val supportingText: String = ""
)

private const val MANUAL_REORDER_TOP_SENTINEL_KEY = "__manual_reorder_top_sentinel__"

@Composable
internal fun ManualReorderDialog(
    title: String,
    items: List<ManualReorderListItem>,
    initialKey: String,
    dialogTag: String,
    rowTagPrefix: String,
    onDismiss: () -> Unit,
    onOrderCommitted: (List<String>) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val listState = rememberLazyListState()
    val localItems = remember { mutableStateListOf<ManualReorderListItem>() }
    var highlightKey by remember(initialKey) { mutableStateOf<String?>(initialKey) }

    val reorderState = rememberReorderableLazyListState(
        listState = listState,
        maxScrollPerFrame = 28.dp,
        scrollTriggerPadding = 112.dp,
        onMove = { from, to ->
            localItems.move(from, to)
        },
        canDragOver = { draggedOver, _ ->
            localItems.any { item -> item.key == draggedOver.key }
        },
        onDragEnd = { _, _ ->
            onOrderCommitted(localItems.map { it.key })
        }
    )

    LaunchedEffect(items) {
        if (reorderState.draggingItemIndex == null) {
            localItems.clear()
            localItems.addAll(items)
        }
    }

    LaunchedEffect(initialKey, items) {
        val targetIndex = items.indexOfFirst { it.key == initialKey }
        if (targetIndex >= 0) {
            listState.scrollToItem((targetIndex - 1).coerceAtLeast(0))
            delay(1200)
        }
        highlightKey = null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag(dialogTag),
            color = colorScheme.background,
            contentColor = colorScheme.onBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colorScheme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "长按后拖动，放手立即保存",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.textSecondary
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .reorderable(reorderState)
                        .detectReorderAfterLongPress(reorderState)
                        .thinScrollbar(listState),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = MANUAL_REORDER_TOP_SENTINEL_KEY) {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                    items(localItems, key = { item -> item.key }) { item ->
                        ReorderableItem(
                            reorderableState = reorderState,
                            key = item.key
                        ) { isDragging ->
                            ManualReorderRow(
                                item = item,
                                highlighted = highlightKey == item.key,
                                isDragging = isDragging,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("$rowTagPrefix:${item.key}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ManualReorderRow(
    item: ManualReorderListItem,
    highlighted: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val backgroundColor by animateColorAsState(
        targetValue = when {
            highlighted -> colorScheme.primary.copy(alpha = 0.14f)
            else -> colorScheme.surface.copy(alpha = 0.78f)
        },
        label = "manualReorderRowColor"
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 18.dp else 0.dp,
        label = "manualReorderRowElevation"
    )

    Row(
        modifier = modifier
            .align(Alignment.TopStart)
            .shadow(elevation, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsmrAsyncImage(
            model = item.artworkModel?.toString().orEmpty(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholderCornerRadius = 10,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colorScheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (item.supportingText.isNotBlank()) {
                Text(
                    text = item.supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Text(
            text = "拖动",
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.textTertiary,
            modifier = Modifier.padding(start = 12.dp)
        )
    }

}

private fun SnapshotStateList<ManualReorderListItem>.move(from: ItemPosition, to: ItemPosition) {
    if (isEmpty()) return
    val fromIndex = from.index - 1
    val toIndex = to.index - 1
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return
    add(toIndex, removeAt(fromIndex))
}
