package com.asmr.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.data.local.db.dao.TagWithCount
import com.asmr.player.ui.common.FlatTextFieldDialog
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.common.withAddedBottomPadding
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor

@Composable
fun LibraryFilterScreen(
    onClose: () -> Unit,
    viewModel: LibraryViewModel
) {
    val querySpec by viewModel.querySpec.collectAsStateWithLifecycle()
    val tags by viewModel.availableTags.collectAsStateWithLifecycle()
    val circles by viewModel.availableCircles.collectAsStateWithLifecycle()
    val cvs by viewModel.availableCvs.collectAsStateWithLifecycle()
    val presets by viewModel.filterPresets.collectAsStateWithLifecycle()
    val appliedSpec = remember(querySpec) { querySpec.filterOnly() }
    var draftSpec by remember(appliedSpec) { mutableStateOf(appliedSpec) }
    var showTagManager by remember { mutableStateOf(false) }

    LibraryFilterSheet(
        modifier = Modifier.fillMaxSize(),
        appliedSpec = appliedSpec,
        draftSpec = draftSpec,
        tags = tags,
        circles = circles,
        cvs = cvs,
        presets = presets,
        onDraftSpecChange = { draftSpec = it.filterOnly() },
        onOpenTagManager = { showTagManager = true },
        onApply = {
            viewModel.applyFilters(draftSpec)
            onClose()
        },
        onSavePreset = { name -> viewModel.savePreset(name, draftSpec) },
        onDeletePreset = { viewModel.deletePreset(it) },
        onClose = onClose
    )

    if (showTagManager) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showTagManager = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                TagManagerSheet(
                    tags = tags,
                    onRename = { tagId, newName -> viewModel.renameUserTag(tagId, newName) },
                    onDelete = { tagId -> viewModel.deleteUserTag(tagId) },
                    onClose = { showTagManager = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterSheet(
    appliedSpec: LibraryQuerySpec,
    draftSpec: LibraryQuerySpec,
    tags: List<TagWithCount>,
    circles: List<String>,
    cvs: List<String>,
    presets: List<LibraryFilterPreset>,
    onDraftSpecChange: (LibraryQuerySpec) -> Unit,
    onOpenTagManager: () -> Unit,
    onApply: () -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var tagSearch by rememberSaveable { mutableStateOf("") }
    var circleSearch by rememberSaveable { mutableStateOf("") }
    var cvSearch by rememberSaveable { mutableStateOf("") }
    var showSavePreset by remember { mutableStateOf(false) }
    var presetName by rememberSaveable { mutableStateOf("") }
    val normalizedAppliedSpec = remember(appliedSpec) { appliedSpec.filterOnly() }
    val normalizedDraftSpec = remember(draftSpec) { draftSpec.filterOnly() }
    val isDirty = normalizedDraftSpec != normalizedAppliedSpec

    Column(modifier = modifier.fillMaxWidth()) {
        LibraryFilterHeader(
            draftSpec = normalizedDraftSpec,
            isDirty = isDirty,
            onOpenTagManager = onOpenTagManager,
            onClose = onClose
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .thinScrollbar(listState),
            flingBehavior = rememberCalmScrollableFlingBehavior(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                .withAddedBottomPadding(LocalBottomOverlayPadding.current),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FilterSection(
                    title = "来源",
                    subtitle = sourceLabel(normalizedDraftSpec.source)
                ) {
                    SourceSelector(
                        current = normalizedDraftSpec.source,
                        onSetSource = { source ->
                            onDraftSpecChange(normalizedDraftSpec.copy(source = source.takeUnless { it == LibrarySourceFilter.Both }))
                        }
                    )
                }
            }

            item {
                FilterSection(
                    title = "标签",
                    subtitle = selectedCountText(normalizedDraftSpec.includeTagIds.size)
                ) {
                    FilterSearchField(
                        value = tagSearch,
                        onValueChange = { tagSearch = it },
                        placeholder = "搜索标签"
                    )
                    val orderedTags = remember(tags, tagSearch, normalizedDraftSpec.includeTagIds) {
                        tags.filterByTagQuery(tagSearch)
                            .sortedWith(
                                compareByDescending<TagWithCount> { normalizedDraftSpec.includeTagIds.contains(it.id) }
                                    .thenByDescending { it.albumCount }
                                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                            )
                    }
                    if (orderedTags.isEmpty()) {
                        EmptyFilterHint(text = "没有匹配的标签")
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            orderedTags.forEach { tag ->
                                FilterTokenPill(
                                    text = if (tag.name.startsWith("#")) tag.name else "#${tag.name}",
                                    count = tag.albumCount,
                                    selected = normalizedDraftSpec.includeTagIds.contains(tag.id),
                                    tone = FilterTokenTone.Tag,
                                    onClick = {
                                        onDraftSpecChange(normalizedDraftSpec.toggleTag(tag.id))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                FilterSection(
                    title = "社团",
                    subtitle = selectedCountText(normalizedDraftSpec.circles.size)
                ) {
                    FilterSearchField(
                        value = circleSearch,
                        onValueChange = { circleSearch = it },
                        placeholder = "搜索社团"
                    )
                    val orderedCircles = remember(circles, circleSearch, normalizedDraftSpec.circles) {
                        circles.filterByTextQuery(circleSearch)
                            .sortedWith(
                                compareByDescending<String> { normalizedDraftSpec.circles.contains(it) }
                                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it }
                            )
                    }
                    if (orderedCircles.isEmpty()) {
                        EmptyFilterHint(text = "没有匹配的社团")
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            orderedCircles.forEach { circle ->
                                FilterTokenPill(
                                    text = circle,
                                    selected = normalizedDraftSpec.circles.contains(circle),
                                    tone = FilterTokenTone.Circle,
                                    onClick = {
                                        onDraftSpecChange(normalizedDraftSpec.toggleCircle(circle))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                FilterSection(
                    title = "CV",
                    subtitle = selectedCountText(normalizedDraftSpec.cvs.size)
                ) {
                    FilterSearchField(
                        value = cvSearch,
                        onValueChange = { cvSearch = it },
                        placeholder = "搜索 CV"
                    )
                    val orderedCvs = remember(cvs, cvSearch, normalizedDraftSpec.cvs) {
                        cvs.filterByTextQuery(cvSearch)
                            .sortedWith(
                                compareByDescending<String> { normalizedDraftSpec.cvs.contains(it) }
                                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it }
                            )
                    }
                    if (orderedCvs.isEmpty()) {
                        EmptyFilterHint(text = "没有匹配的 CV")
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            orderedCvs.forEach { cv ->
                                FilterTokenPill(
                                    text = cv,
                                    selected = normalizedDraftSpec.cvs.contains(cv),
                                    tone = FilterTokenTone.Cv,
                                    onClick = {
                                        onDraftSpecChange(normalizedDraftSpec.toggleCv(cv))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                PresetSectionHeader(onSavePreset = { showSavePreset = true })
            }
            if (presets.isEmpty()) {
                item {
                    EmptyFilterHint(
                        text = "暂无筛选预设",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                items(presets, key = { it.id }) { preset ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onDraftSpecChange(preset.spec.filterOnly())
                            }
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    buildPresetSummary(preset.spec),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onDeletePreset(preset.id) }) {
                                    Icon(imageVector = Icons.Rounded.Delete, contentDescription = "删除预设")
                                }
                            }
                        )
                    }
                }
            }
        }

        LibraryFilterBottomBar(
            active = normalizedDraftSpec.hasActiveFilters,
            isDirty = isDirty,
            onClose = onClose,
            onApply = onApply
        )
    }

    if (showSavePreset) {
        FlatTextFieldDialog(
            onDismissRequest = { showSavePreset = false },
            message = "请输入筛选预设名称。",
            value = presetName,
            onValueChange = { presetName = it },
            placeholder = "请输入预设名称",
            confirmText = "保存",
            confirmEnabled = presetName.trim().isNotBlank(),
            onConfirm = {
                val name = presetName.trim()
                if (name.isNotBlank()) onSavePreset(name)
                presetName = ""
                showSavePreset = false
            },
        )
    }
}

@Composable
private fun LibraryFilterHeader(
    draftSpec: LibraryQuerySpec,
    isDirty: Boolean,
    onOpenTagManager: () -> Unit,
    onClose: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.16f else 0.10f),
            contentColor = colorScheme.primary,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Rounded.FilterList, contentDescription = null)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "筛选本地库",
                    color = colorScheme.textPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isDirty) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "未应用",
                        color = colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                text = buildFilterSummary(draftSpec),
                color = colorScheme.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onOpenTagManager) {
            Icon(
                imageVector = Icons.Rounded.LocalOffer,
                contentDescription = "管理标签",
                tint = colorScheme.primary
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "关闭筛选",
                tint = colorScheme.textSecondary
            )
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val sectionContainerColor = dynamicPageContainerColor(colorScheme)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = colorScheme.textPrimary,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = colorScheme.textTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 0.dp,
            color = sectionContainerColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceSelector(
    current: LibrarySourceFilter?,
    onSetSource: (LibrarySourceFilter?) -> Unit
) {
    val normalized = current.takeUnless { it == LibrarySourceFilter.Both }
    val options = listOf(
        null to "不限",
        LibrarySourceFilter.LocalOnly to "仅本地",
        LibrarySourceFilter.DownloadOnly to "仅下载",
        LibrarySourceFilter.LocalAndDownload to "本地+下载"
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (source, label) ->
            FilterTokenPill(
                text = label,
                selected = normalized == source,
                tone = FilterTokenTone.Source,
                onClick = { onSetSource(source) }
            )
        }
    }
}

@Composable
private fun FilterSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val colorScheme = AsmrTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = colorScheme.textPrimary),
        cursorBrush = SolidColor(colorScheme.primary),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(shape)
                    .background(
                        color = colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.54f else 0.86f),
                        shape = shape
                    )
                    .border(
                        width = 1.dp,
                        color = colorScheme.onSurfaceVariant.copy(alpha = if (colorScheme.isDark) 0.24f else 0.16f),
                        shape = shape
                    )
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
    )
}

private enum class FilterTokenTone {
    Source,
    Tag,
    Circle,
    Cv
}

@Composable
private fun FilterTokenPill(
    text: String,
    selected: Boolean,
    tone: FilterTokenTone,
    onClick: () -> Unit,
    count: Long? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    val shape = when (tone) {
        FilterTokenTone.Tag -> RoundedCornerShape(7.dp)
        FilterTokenTone.Source,
        FilterTokenTone.Circle,
        FilterTokenTone.Cv -> RoundedCornerShape(999.dp)
    }
    val tint = when (tone) {
        FilterTokenTone.Tag -> colorScheme.textSecondary
        FilterTokenTone.Source,
        FilterTokenTone.Circle,
        FilterTokenTone.Cv -> colorScheme.primary
    }
    val container = when {
        selected -> colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.20f else 0.14f)
        tone == FilterTokenTone.Tag -> colorScheme.surfaceVariant.copy(alpha = if (colorScheme.isDark) 0.74f else 0.92f)
        else -> colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.10f else 0.06f)
    }
    val border = when {
        selected -> colorScheme.primary.copy(alpha = 0.46f)
        tone == FilterTokenTone.Tag -> colorScheme.onSurfaceVariant.copy(alpha = if (colorScheme.isDark) 0.30f else 0.18f)
        else -> colorScheme.primary.copy(alpha = 0.16f)
    }
    val content = if (selected) colorScheme.primary else tint

    Row(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(shape)
            .background(container, shape)
            .border(0.5.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(start = 9.dp, end = if (count != null) 5.dp else 9.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (count != null && count > 0L) {
            Surface(
                color = colorScheme.primary.copy(alpha = if (selected) 0.16f else 0.10f),
                contentColor = content,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    color = content,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun PresetSectionHeader(
    onSavePreset: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Rounded.Bookmark, contentDescription = null, tint = colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "预设",
            color = colorScheme.textPrimary,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSavePreset) {
            Text("保存草稿")
        }
    }
}

@Composable
private fun EmptyFilterHint(
    text: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    Text(
        text = text,
        color = colorScheme.textTertiary,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun LibraryFilterBottomBar(
    active: Boolean,
    isDirty: Boolean,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val containerColor = colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.96f else 0.98f)
        .compositeOver(colorScheme.background)
    Surface(
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = LocalBottomOverlayPadding.current),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onClose,
                modifier = Modifier.weight(1f)
            ) {
                Text("取消")
            }
            FilledTonalButton(
                onClick = onApply,
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (active || isDirty) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                    contentColor = if (active || isDirty) colorScheme.onPrimaryContainer else colorScheme.textSecondary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("应用")
            }
        }
    }
}

private fun LibraryQuerySpec.toggleTag(tagId: Long): LibraryQuerySpec {
    val updated = includeTagIds.toMutableSet()
    if (!updated.add(tagId)) updated.remove(tagId)
    return copy(includeTagIds = updated)
}

private fun LibraryQuerySpec.toggleCircle(circle: String): LibraryQuerySpec {
    val normalized = circle.trim()
    if (normalized.isBlank()) return this
    val updated = circles.toMutableSet()
    if (!updated.add(normalized)) updated.remove(normalized)
    return copy(circles = updated)
}

private fun LibraryQuerySpec.toggleCv(cv: String): LibraryQuerySpec {
    val normalized = cv.trim()
    if (normalized.isBlank()) return this
    val updated = cvs.toMutableSet()
    if (!updated.add(normalized)) updated.remove(normalized)
    return copy(cvs = updated)
}

private fun List<TagWithCount>.filterByTagQuery(query: String): List<TagWithCount> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return this
    return filter { tag ->
        tag.name.lowercase().contains(normalized) || tag.nameNormalized.contains(normalized)
    }
}

private fun List<String>.filterByTextQuery(query: String): List<String> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return this
    return filter { it.lowercase().contains(normalized) }
}

private fun selectedCountText(count: Int): String? {
    return if (count > 0) "已选 $count" else null
}

private fun sourceLabel(source: LibrarySourceFilter?): String {
    return when (source) {
        LibrarySourceFilter.LocalOnly -> "仅本地"
        LibrarySourceFilter.DownloadOnly -> "仅下载"
        LibrarySourceFilter.LocalAndDownload -> "本地+下载"
        LibrarySourceFilter.Both,
        null -> "不限"
    }
}

private fun buildFilterSummary(spec: LibraryQuerySpec): String {
    if (!spec.hasActiveFilters) return "未选择筛选条件"
    val parts = ArrayList<String>(4)
    spec.source?.takeUnless { it == LibrarySourceFilter.Both }?.let { parts.add(sourceLabel(it)) }
    if (spec.includeTagIds.isNotEmpty()) parts.add("标签 ${spec.includeTagIds.size}")
    if (spec.circles.isNotEmpty()) parts.add("社团 ${spec.circles.size}")
    if (spec.cvs.isNotEmpty()) parts.add("CV ${spec.cvs.size}")
    if (spec.excludeTagIds.isNotEmpty()) parts.add("排除标签 ${spec.excludeTagIds.size}")
    return parts.joinToString(" · ")
}

private fun buildPresetSummary(spec: LibraryQuerySpec): String {
    return buildFilterSummary(spec.filterOnly())
}
