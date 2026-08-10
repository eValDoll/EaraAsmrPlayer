package com.asmr.player.ui.playlists

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.theme.AsmrTheme

@Composable
fun PlaylistPickerScreen(
    windowSizeClass: WindowSizeClass,
    items: List<MediaItem>,
    onBack: () -> Unit,
    embeddedInDialog: Boolean = false,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val userPlaylists = remember(playlists) { playlists.filter { it.category == "user" } }
    val colorScheme = AsmrTheme.colorScheme
    val listState = rememberLazyListState()
    val screenActive = remember { mutableStateOf(true) }
    var createName by rememberSaveable { mutableStateOf("") }
    var addingPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
    val canCreate = remember(createName) { createName.trim().isNotBlank() }
    val isAdding = addingPlaylistId != null

    val pickerItems = remember(items) { items }
    BackHandler(enabled = isAdding) {}

    DisposableEffect(Unit) {
        onDispose { screenActive.value = false }
    }

    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = if (isCompact) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "选择列表",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.textPrimary
                )
                if (pickerItems.size > 1) {
                    Text(
                        text = "将添加 ${pickerItems.size} 项",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.textSecondary
                    )
                }

                if (embeddedInDialog) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { if (!isAdding) onBack() },
                            enabled = !isAdding,
                            colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
                        ) {
                            Text("关闭")
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it },
                        modifier = Modifier.weight(1f),
                        enabled = !isAdding,
                        singleLine = true,
                        placeholder = { Text("新建列表名称") }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    TextButton(
                        onClick = {
                            val trimmed = createName.trim()
                            if (trimmed.isBlank()) return@TextButton
                            viewModel.createPlaylist(trimmed)
                            createName = ""
                        },
                        enabled = canCreate && !isAdding,
                        colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
                    ) {
                        Text("创建")
                    }
                }
            }

            if (userPlaylists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    Text("暂无可选列表", color = colorScheme.textSecondary)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().thinScrollbar(listState),
                    flingBehavior = rememberCalmScrollableFlingBehavior(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(userPlaylists, key = { it.id }) { playlist ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colorScheme.surface.copy(alpha = 0.5f))
                                .clickable(enabled = !isAdding) {
                                    addingPlaylistId = playlist.id
                                    viewModel.addItemsToPlaylistInBackground(
                                        playlistId = playlist.id,
                                        items = pickerItems,
                                        onComplete = {
                                            if (screenActive.value) {
                                                addingPlaylistId = null
                                                onBack()
                                            }
                                        },
                                        onFailure = {
                                            if (screenActive.value) {
                                                addingPlaylistId = null
                                            }
                                        }
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.padding(bottom = 12.dp)) }
                }
            }
        }
    }
}
