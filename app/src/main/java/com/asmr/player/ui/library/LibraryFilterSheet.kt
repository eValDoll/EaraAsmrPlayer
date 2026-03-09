package com.asmr.player.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.asmr.player.data.local.db.dao.TagWithCount
import com.asmr.player.ui.theme.AsmrTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterSheet(
    querySpec: LibraryQuerySpec,
    tags: List<TagWithCount>,
    circles: List<String>,
    cvs: List<String>,
    presets: List<LibraryFilterPreset>,
    onOpenTagManager: () -> Unit,
    onSetSource: (LibrarySourceFilter?) -> Unit,
    onToggleTag: (Long) -> Unit,
    onToggleCircle: (String) -> Unit,
    onToggleCv: (String) -> Unit,
    onClear: () -> Unit,
    onApplyPreset: (LibraryFilterPreset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onClose: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    var tagSearch by rememberSaveable { mutableStateOf("") }
    var showSavePreset by remember { mutableStateOf(false) }
    var presetName by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.FilterList, contentDescription = null, tint = colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "筛选", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onOpenTagManager) {
                Icon(imageVector = Icons.Default.ManageSearch, contentDescription = null)
            }
            IconButton(onClick = onClear) {
                Icon(imageVector = Icons.Default.Restore, contentDescription = null)
            }
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }
            item {
                SectionCard(title = "来源") {
                    SourceChips(current = querySpec.source, onSetSource = onSetSource)
                }
            }

            item {
                SectionCard(
                    title = "标签",
                    subtitle = if (querySpec.includeTagIds.isNotEmpty()) "已选 ${querySpec.includeTagIds.size}" else null
                ) {
                    OutlinedTextField(
                        value = tagSearch,
                        onValueChange = { tagSearch = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 30.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("搜索标签", style = MaterialTheme.typography.bodySmall) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    val normalizedQuery = remember(tagSearch) { tagSearch.trim().lowercase() }
                    val filtered = remember(normalizedQuery, tags) {
                        if (normalizedQuery.isBlank()) {
                            tags
                        } else {
                            tags.filter { it.name.lowercase().contains(normalizedQuery) || it.nameNormalized.contains(normalizedQuery) }
                        }
                    }
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val ordered = remember(filtered, querySpec.includeTagIds) {
                            filtered.sortedWith(
                                compareByDescending<TagWithCount> { querySpec.includeTagIds.contains(it.id) }
                                    .thenByDescending { it.albumCount }
                                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                            )
                        }.take(400)
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .verticalScroll(scrollState)
                                .padding(12.dp)
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ordered.forEach { t ->
                                    val selected = querySpec.includeTagIds.contains(t.id)
                                    TagStamp(
                                        name = t.name,
                                        count = t.albumCount,
                                        isUserTag = t.userAlbumCount > 0L,
                                        selected = selected,
                                        onClick = { onToggleTag(t.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = "社团",
                    subtitle = if (querySpec.circles.isNotEmpty()) "已选 ${querySpec.circles.size}" else null
                ) {
                    ValueChipGrid(
                        values = circles.take(30),
                        selected = querySpec.circles,
                        onToggle = onToggleCircle
                    )
                }
            }

            item {
                SectionCard(
                    title = "CV",
                    subtitle = if (querySpec.cvs.isNotEmpty()) "已选 ${querySpec.cvs.size}" else null
                ) {
                    ValueChipGrid(
                        values = cvs.take(30),
                        selected = querySpec.cvs,
                        onToggle = onToggleCv
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Bookmark, contentDescription = null, tint = colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "预设")
                    }
                    TextButton(onClick = { showSavePreset = true }) { Text("保存当前") }
                }
            }
            items(presets, key = { it.id }) { preset ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onApplyPreset(preset)
                            onClose()
                        }
                        .padding(horizontal = 16.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(buildPresetSummary(preset.spec), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            IconButton(onClick = { onDeletePreset(preset.id) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showSavePreset) {
        AlertDialog(
            onDismissRequest = { showSavePreset = false },
            title = { Text("保存筛选预设") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("请输入预设名称") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = presetName.trim()
                        if (name.isNotBlank()) onSavePreset(name)
                        presetName = ""
                        showSavePreset = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        presetName = ""
                        showSavePreset = false
                    }
                ) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, color = colorScheme.textPrimary, modifier = Modifier.weight(1f))
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, color = colorScheme.textTertiary)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceChips(
    current: LibrarySourceFilter?,
    onSetSource: (LibrarySourceFilter?) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val normalized = if (current == LibrarySourceFilter.Both) null else current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SourceChip(
            label = "不限",
            selected = normalized == null,
            onClick = { onSetSource(null) },
            colorScheme = colorScheme
        )
        SourceChip(
            label = "仅本地",
            selected = normalized == LibrarySourceFilter.LocalOnly,
            onClick = { onSetSource(LibrarySourceFilter.LocalOnly) },
            colorScheme = colorScheme
        )
        SourceChip(
            label = "仅下载",
            selected = normalized == LibrarySourceFilter.DownloadOnly,
            onClick = { onSetSource(LibrarySourceFilter.DownloadOnly) },
            colorScheme = colorScheme
        )
        SourceChip(
            label = "本地+下载",
            selected = normalized == LibrarySourceFilter.LocalAndDownload,
            onClick = { onSetSource(LibrarySourceFilter.LocalAndDownload) },
            colorScheme = colorScheme
        )
    }
}

@Composable
private fun SourceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    colorScheme: com.asmr.player.ui.theme.AsmrColorScheme
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = colorScheme.primary,
            selectedLeadingIconColor = colorScheme.primary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = colorScheme.primary.copy(alpha = 0.25f),
            selectedBorderColor = colorScheme.primary
        )
    )
}

@Composable
private fun TagStamp(
    name: String,
    count: Long,
    isUserTag: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val tint = if (isUserTag) colorScheme.primary else colorScheme.accent
    val bg = if (selected) tint.copy(alpha = 0.18f) else colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val border = if (selected) tint.copy(alpha = 0.45f) else tint.copy(alpha = 0.22f)
    val nameColor = if (selected) tint else colorScheme.textPrimary
    val countColor = if (selected) tint.copy(alpha = 0.8f) else colorScheme.textTertiary
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(width = 1.dp, color = border, shape = shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = nameColor,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = count.toString(), color = countColor, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ValueChipGrid(
    values: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val ordered = remember(values, selected) {
        values.sortedWith(
            compareByDescending<String> { selected.contains(it) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it }
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ordered.forEach { value ->
            val isSelected = selected.contains(value)
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(value) },
                label = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colorScheme.primary.copy(alpha = 0.18f),
                    selectedLabelColor = colorScheme.primary,
                    selectedLeadingIconColor = colorScheme.primary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = colorScheme.primary.copy(alpha = 0.25f),
                    selectedBorderColor = colorScheme.primary
                )
            )
        }
    }
}

private fun buildPresetSummary(spec: LibraryQuerySpec): String {
    val parts = ArrayList<String>(5)
    val source = when (spec.source) {
        LibrarySourceFilter.LocalOnly -> "仅本地"
        LibrarySourceFilter.DownloadOnly -> "仅下载"
        LibrarySourceFilter.LocalAndDownload -> "本地+下载"
        LibrarySourceFilter.Both -> "不限来源"
        null -> "不限来源"
    }
    parts.add(source)
    if (!spec.textQuery.isNullOrBlank()) parts.add("搜索")
    if (spec.includeTagIds.isNotEmpty()) parts.add("标签 ${spec.includeTagIds.size}")
    if (spec.circles.isNotEmpty()) parts.add("社团 ${spec.circles.size}")
    if (spec.cvs.isNotEmpty()) parts.add("CV ${spec.cvs.size}")
    return parts.joinToString(" · ")
}
