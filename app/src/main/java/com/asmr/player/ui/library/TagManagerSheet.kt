package com.asmr.player.ui.library

import com.asmr.player.i18n.appString
import com.asmr.player.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.data.local.db.dao.TagWithCount
import com.asmr.player.ui.common.FlatActionDialog
import com.asmr.player.ui.common.FlatDialogAction
import com.asmr.player.ui.common.FlatDialogActionTone
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.theme.AsmrTheme

@Composable
fun TagManagerSheet(
    tags: List<TagWithCount>,
    onRename: (tagId: Long, newName: String) -> Unit,
    onDelete: (tagId: Long) -> Unit,
    onClose: () -> Unit
) {
    var selected by remember { mutableStateOf<TagWithCount?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val colorScheme = AsmrTheme.colorScheme

    val visibleTags = remember(tags, filter) {
        val q = filter.trim().lowercase()
        tags
            .asSequence()
            .filter { it.userAlbumCount > 0L || it.albumCount == 0L }
            .filter { q.isBlank() || it.name.lowercase().contains(q) }
            .toList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
            }
            Text(
                text = appString(R.string.ui_tag_management),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.textPrimary
            )
            TextButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.ui_done))
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            TagManagerSearchField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = appString(R.string.ui_search_tags)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            flingBehavior = rememberCalmScrollableFlingBehavior()
        ) {
            items(visibleTags, key = { it.id }) { tag ->
                ListItem(
                    headlineContent = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.textPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (tag.albumCount == 0L) {
                                    TagManagerMetricBadge(label = appString(R.string.ui_not_used))
                                } else {
                                    TagManagerMetricBadge(
                                        label = appString(R.string.ui_user_annotation),
                                        value = tag.userAlbumCount.toString(),
                                        highlighted = true
                                    )
                                    TagManagerMetricBadge(
                                        label = appString(R.string.ui_total),
                                        value = tag.albumCount.toString()
                                    )
                                }
                            }
                        }
                    },
                    supportingContent = {
                        Text(
                            text = appString(R.string.ui_click_to_rename_or_delete),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.textTertiary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = tag
                            renameText = tag.name
                            showRenameDialog = true
                        }
                )
            }
            item { Spacer(modifier = Modifier.padding(bottom = 16.dp)) }
        }
    }

    if (showRenameDialog) {
        val tag = selected
        if (tag != null) {
            FlatActionDialog(
                onDismissRequest = { showRenameDialog = false },
                message = appString(R.string.ui_modify_the_label_name_or_delete_this_user),
                actions = listOf(
                    FlatDialogAction(
                        text = appString(R.string.ui_delete),
                        tone = FlatDialogActionTone.Danger,
                        onClick = {
                            showRenameDialog = false
                            showDeleteDialog = true
                        }
                    ),
                    FlatDialogAction(appString(R.string.ui_cancel), onClick = { showRenameDialog = false }),
                    FlatDialogAction(
                        text = appString(R.string.ui_save),
                        tone = FlatDialogActionTone.Primary,
                        enabled = renameText.trim().isNotBlank(),
                        onClick = {
                            onRename(tag.id, renameText.trim())
                            showRenameDialog = false
                        }
                    )
                )
            ) {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }

    if (showDeleteDialog) {
        val tag = selected
        if (tag != null) {
            FlatActionDialog(
                onDismissRequest = { showDeleteDialog = false },
                message = appString(R.string.ui_the_label_will_be_removed_from_all_user),
                actions = listOf(
                    FlatDialogAction(appString(R.string.ui_cancel), onClick = { showDeleteDialog = false }),
                    FlatDialogAction(
                        text = appString(R.string.ui_delete),
                        tone = FlatDialogActionTone.Danger,
                        onClick = {
                            onDelete(tag.id)
                            showDeleteDialog = false
                        }
                    )
                )
            )
        }
    }
}

@Composable
private fun TagManagerSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val colorScheme = AsmrTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = colorScheme.textPrimary),
        cursorBrush = SolidColor(colorScheme.primary),
        decorationBox = { innerTextField ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = shape,
                color = colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.54f else 0.86f),
                border = BorderStroke(
                    width = 1.dp,
                    color = colorScheme.onSurfaceVariant.copy(alpha = if (colorScheme.isDark) 0.24f else 0.16f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.textTertiary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = colorScheme.textTertiary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            }
        }
    )
}

@Composable
private fun TagManagerMetricBadge(
    label: String,
    value: String? = null,
    highlighted: Boolean = false
) {
    val colorScheme = AsmrTheme.colorScheme
    val containerColor = if (highlighted) {
        colorScheme.primary.copy(alpha = 0.12f)
    } else {
        colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val borderColor = if (highlighted) {
        colorScheme.primary.copy(alpha = 0.18f)
    } else {
        colorScheme.textTertiary.copy(alpha = 0.18f)
    }
    val contentColor = if (highlighted) colorScheme.primary else colorScheme.textSecondary
    val valueContainerColor = if (highlighted) {
        colorScheme.primary.copy(alpha = 0.14f)
    } else {
        colorScheme.textTertiary.copy(alpha = 0.12f)
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.widthIn(max = 72.dp),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (value != null) {
                Surface(
                    color = valueContainerColor,
                    contentColor = contentColor,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = value,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor
                    )
                }
            }
        }
    }
}
