package com.asmr.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.asmr.player.data.local.db.dao.TagWithCount
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.util.TagNormalizer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagAssignDialog(
    title: String,
    inheritedTags: List<String>,
    userTags: List<String>,
    allTags: List<TagWithCount>,
    onApplyUserTags: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    onOpenTagManager: (() -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    var workingUserTags by remember(userTags) { mutableStateOf(dedupeByNormalized(userTags)) }
    var query by rememberSaveable { mutableStateOf("") }
    var customInput by rememberSaveable { mutableStateOf("") }

    val inheritedNormalized = remember(inheritedTags) {
        inheritedTags.asSequence().map { TagNormalizer.normalize(it) }.filter { it.isNotBlank() }.toSet()
    }
    val userNormalized = remember(workingUserTags) {
        workingUserTags.asSequence().map { TagNormalizer.normalize(it) }.filter { it.isNotBlank() }.toSet()
    }
    val existingNormalized = remember(inheritedNormalized, userNormalized) { inheritedNormalized + userNormalized }

    val suggestions = remember(query, allTags, existingNormalized) {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) {
            allTags
        } else {
            allTags.filter { it.name.lowercase().contains(normalized) || it.nameNormalized.contains(normalized) }
        }
            .filterNot { existingNormalized.contains(it.nameNormalized) }
            .take(80)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { onApplyUserTags(dedupeByNormalized(workingUserTags)) }) { Text("保存") }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = LocalBottomOverlayPadding.current),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (inheritedTags.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "已有标签", color = colorScheme.textTertiary)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                inheritedTags.distinct().forEach { t ->
                                    TagBadge(text = t, selected = false, isUser = false, onClick = null)
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "用户标签", color = colorScheme.textTertiary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (workingUserTags.isEmpty()) {
                                Text(text = "未添加", color = colorScheme.textSecondary)
                            } else {
                                workingUserTags.forEach { t ->
                                    TagBadge(
                                        text = t,
                                        selected = true,
                                        isUser = true,
                                        onClick = {
                                            val n = TagNormalizer.normalize(t)
                                            workingUserTags = workingUserTags.filterNot { TagNormalizer.normalize(it) == n }
                                        }
                                    )
                                }
                            }
                        }
                        if (workingUserTags.isNotEmpty()) {
                            Text(text = "点击用户标签可移除", color = colorScheme.textSecondary)
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                                }
                            }
                        },
                        placeholder = { Text("搜索已有标签") }
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { t ->
                            TagBadge(
                                text = "${t.name} ${t.albumCount}",
                                selected = false,
                                isUser = false,
                                onClick = {
                                    workingUserTags = dedupeByNormalized(workingUserTags + t.name)
                                }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customInput,
                            onValueChange = { customInput = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("添加自定义标签") }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        IconButton(
                            onClick = {
                                val trimmed = customInput.trim()
                                if (trimmed.isBlank()) return@IconButton
                                val n = TagNormalizer.normalize(trimmed)
                                if (n.isBlank()) return@IconButton
                                if (existingNormalized.contains(n)) return@IconButton
                                workingUserTags = dedupeByNormalized(workingUserTags + trimmed)
                                customInput = ""
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = colorScheme.primary)
                        }
                        if (onOpenTagManager != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            TextButton(onClick = { onOpenTagManager() }) { Text("自定义标签管理") }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

private fun dedupeByNormalized(input: List<String>): List<String> {
    val seen = LinkedHashSet<String>()
    val out = ArrayList<String>(input.size)
    input.forEach { raw ->
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return@forEach
        val normalized = TagNormalizer.normalize(trimmed)
        if (normalized.isBlank()) return@forEach
        if (seen.add(normalized)) out.add(trimmed)
    }
    return out
}

@Composable
private fun TagBadge(
    text: String,
    selected: Boolean,
    isUser: Boolean,
    onClick: (() -> Unit)?
) {
    val colorScheme = AsmrTheme.colorScheme
    val tint = if (isUser) colorScheme.primary else colorScheme.accent
    val bg = if (selected) tint.copy(alpha = 0.18f) else colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val border = if (selected) tint.copy(alpha = 0.45f) else tint.copy(alpha = 0.22f)
    val label = if (selected) tint else colorScheme.textPrimary

    androidx.compose.material3.Surface(
        color = bg,
        contentColor = label,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        modifier = Modifier
            .then(if (onClick != null) Modifier.padding(0.dp) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
