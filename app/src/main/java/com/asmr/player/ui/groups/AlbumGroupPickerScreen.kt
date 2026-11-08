package com.asmr.player.ui.groups

import com.asmr.player.i18n.appString
import com.asmr.player.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmr.player.ui.common.CollectionPickerContent
import com.asmr.player.ui.common.CollectionPickerRow

@Composable
fun AlbumGroupPickerScreen(
    windowSizeClass: WindowSizeClass,
    albumId: Long,
    onBack: () -> Unit,
    embeddedInDialog: Boolean = false,
    viewModel: AlbumGroupsViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val screenActive = remember { mutableStateOf(true) }
    var createName by rememberSaveable { mutableStateOf("") }
    var addingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val isAdding = addingId != null

    BackHandler(enabled = isAdding) {}
    DisposableEffect(Unit) {
        onDispose { screenActive.value = false }
    }

    CollectionPickerContent(
        windowSizeClass = windowSizeClass,
        title = appString(R.string.ui_select_group),
        createPlaceholder = appString(R.string.ui_new_group_name),
        createName = createName,
        onCreateNameChange = { createName = it },
        onCreate = {
            val trimmed = createName.trim()
            if (trimmed.isNotBlank()) {
                viewModel.createGroup(trimmed)
                createName = ""
            }
        },
        onBack = onBack,
        embeddedInDialog = embeddedInDialog,
        isAdding = isAdding,
        isEmpty = groups.isEmpty(),
        emptyText = appString(R.string.ui_there_are_currently_no_optional_groups),
    ) {
        items(groups, key = { it.id }) { group ->
            CollectionPickerRow(
                name = group.name,
                artworkUri = group.firstArtworkUri,
                summary = appString(R.string.ui_total_albums_songs, group.albumCount, group.itemCount),
                enabled = !isAdding,
                isAdding = addingId == group.id,
                onClick = {
                    addingId = group.id
                    viewModel.addAlbumToGroupInBackground(
                        groupId = group.id,
                        albumId = albumId,
                        onComplete = {
                            if (screenActive.value) {
                                addingId = null
                                onBack()
                            }
                        },
                        onFailure = {
                            if (screenActive.value) {
                                addingId = null
                            }
                        }
                    )
                }
            )
        }
    }
}
