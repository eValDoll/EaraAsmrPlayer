package com.asmr.player.ui.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.repository.PlaylistRepository
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SystemPlaylistScreen(
    windowSizeClass: WindowSizeClass,
    type: String,
    onPlayAll: (List<PlaylistItemEntity>, PlaylistItemEntity) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val targetName = PlaylistRepository.PLAYLIST_FAVORITES
    val playlist = playlists.firstOrNull { it.name == targetName }

    if (playlist == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    PlaylistDetailScreen(
        windowSizeClass = windowSizeClass,
        playlistId = playlist.id,
        title = playlist.name,
        onPlayAll = onPlayAll
    )
}
