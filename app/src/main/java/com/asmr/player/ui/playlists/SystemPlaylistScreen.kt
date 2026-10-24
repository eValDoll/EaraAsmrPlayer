package com.asmr.player.ui.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.repository.PlaylistRepository
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.collectAsStateWhileActive
import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SystemPlaylistScreen(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    isDataActive: Boolean = isActive,
    onPlayAll: (List<PlaylistItemEntity>, PlaylistItemEntity) -> Unit,
    onResumeAll: (List<PlaylistItemEntity>, PlaylistItemEntity, Long) -> Unit,
    playback: StateFlow<PlaybackSnapshot>,
    scrollToTopSignal: Long = 0L,
    viewModel: PlaylistsViewModel = hiltViewModel(),
    playlistDetailViewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWhileActive(isDataActive)
    val targetName = PlaylistRepository.PLAYLIST_FAVORITES
    val playlist = playlists.firstOrNull { it.name == targetName }

    if (playlist == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
        }
        return
    }

    PlaylistDetailScreen(
        windowSizeClass = windowSizeClass,
        isActive = isActive,
        isDataActive = isDataActive,
        playlistId = playlist.id,
        title = playlist.name,
        onPlayAll = onPlayAll,
        onResumeAll = onResumeAll,
        playback = playback,
        scrollToTopSignal = scrollToTopSignal,
        viewModel = playlistDetailViewModel,
    )
}
