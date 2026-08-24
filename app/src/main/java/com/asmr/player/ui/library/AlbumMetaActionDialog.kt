package com.asmr.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.asmr.player.ui.theme.AsmrTheme

@Composable
internal fun AlbumMetaActionDialog(
    keyword: String,
    onDismissRequest: () -> Unit,
    onSearch: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onCreateGroup: (String) -> Unit,
    onAddBlockedKeyword: (String) -> Unit,
    onCopy: ((String) -> Unit)? = null,
) {
    val normalizedKeyword = remember(keyword) { keyword.trim() }
    if (normalizedKeyword.isBlank()) return

    fun runAction(action: (String) -> Unit) {
        onDismissRequest()
        action(normalizedKeyword)
    }

    val colorScheme = AsmrTheme.colorScheme
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "将「$normalizedKeyword」：",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            onCopy?.let { copy ->
                AlbumMetaActionRow(
                    icon = Icons.Rounded.ContentCopy,
                    text = "复制到粘贴板",
                    onClick = { runAction(copy) },
                )
            }
            AlbumMetaActionRow(
                icon = Icons.Rounded.Search,
                text = "作为关键词进行搜索",
                onClick = { runAction(onSearch) },
            )
            AlbumMetaActionRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                text = "作为名称创建列表",
                onClick = { runAction(onCreatePlaylist) },
            )
            AlbumMetaActionRow(
                icon = Icons.Rounded.CreateNewFolder,
                text = "作为名称创建分组",
                onClick = { runAction(onCreateGroup) },
            )
            AlbumMetaActionRow(
                icon = Icons.Rounded.Block,
                text = "作为屏蔽词屏蔽",
                onClick = { runAction(onAddBlockedKeyword) },
            )
        }
    }
}

@Composable
private fun AlbumMetaActionRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
