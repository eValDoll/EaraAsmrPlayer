package com.asmr.player.ui.playlists

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
import androidx.media3.common.MediaItem
import com.asmr.player.ui.common.CollectionPickerContent
import com.asmr.player.ui.common.CollectionPickerRow

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
        title = appString(R.string.ui_select_list),
        createPlaceholder = appString(R.string.ui_new_list_name),
        createName = createName,
        onCreateNameChange = { createName = it },
        onCreate = {
            val trimmed = createName.trim()
            if (trimmed.isNotBlank()) {
                viewModel.createPlaylist(trimmed)
                createName = ""
            }
        },
        onBack = onBack,
        embeddedInDialog = embeddedInDialog,
        isAdding = isAdding,
        isEmpty = userPlaylists.isEmpty(),
        emptyText = appString(R.string.ui_no_optional_list_yet),
        selectionSummary = if (items.size > 1) appString(R.string.ui_items_will_be_added, items.size) else null,
    ) {
        items(userPlaylists, key = { it.id }) { playlist ->
            CollectionPickerRow(
                name = playlist.name,
                artworkUri = playlist.firstArtworkUri,
                summary = appString(R.string.ui_total_first, playlist.itemCount),
                enabled = !isAdding,
                isAdding = addingId == playlist.id,
                onClick = {
                    addingId = playlist.id
                    viewModel.addItemsToPlaylistInBackground(
                        playlistId = playlist.id,
                        items = items,
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
