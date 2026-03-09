package com.asmr.player.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.asmr.player.ui.theme.AsmrTheme

@Composable
fun AlbumGroupPickerScreen(
    windowSizeClass: WindowSizeClass,
    albumId: Long,
    onBack: () -> Unit,
    viewModel: AlbumGroupsViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsState()
    val colorScheme = AsmrTheme.colorScheme
    var createName by rememberSaveable { mutableStateOf("") }
    val canCreate = remember(createName) { createName.trim().isNotBlank() }

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
                    text = "选择分组",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.textPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("新建分组名称") }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    TextButton(
                        onClick = {
                            val trimmed = createName.trim()
                            if (trimmed.isBlank()) return@TextButton
                            viewModel.createGroup(trimmed)
                            createName = ""
                        },
                        enabled = canCreate,
                        colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
                    ) {
                        Text("创建")
                    }
                }
            }

            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    Text("暂无可选分组", color = colorScheme.textSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(groups, key = { it.id }) { group ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colorScheme.surface.copy(alpha = 0.5f))
                                .clickable {
                                    viewModel.addAlbumToGroup(group.id, albumId)
                                    onBack()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = group.name,
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
