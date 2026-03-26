package com.asmr.player.benchmark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.data.repository.AlbumGroupRepository
import com.asmr.player.data.repository.PlaylistRepository
import com.asmr.player.ui.groups.AlbumGroupDetailContent
import com.asmr.player.ui.groups.AlbumGroupPickerScreen
import com.asmr.player.ui.groups.AlbumGroupsScreen
import com.asmr.player.ui.library.LibraryScreen
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.player.QueueSheetContent
import com.asmr.player.ui.playlists.PlaylistDetailContent
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.playlists.PlaylistsScreen
import com.asmr.player.ui.search.SearchScreen
import com.asmr.player.ui.theme.AsmrPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BenchmarkHarnessActivity : ComponentActivity() {
    @Inject
    lateinit var benchmarkDataSeeder: BenchmarkDataSeeder

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    @Inject
    lateinit var albumGroupRepository: AlbumGroupRepository

    private var uiState by mutableStateOf<BenchmarkHarnessUiState>(BenchmarkHarnessUiState.Loading)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = BenchmarkScenario.fromIntent(intent)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this@BenchmarkHarnessActivity)
            AsmrPlayerTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    BenchmarkHarnessRoot(
                        scenario = scenario,
                        windowSizeClass = windowSizeClass,
                        uiState = uiState,
                        playlistRepository = playlistRepository,
                        albumGroupRepository = albumGroupRepository
                    )
                }
            }
        }

        lifecycleScope.launch {
            uiState = runCatching {
                BenchmarkHarnessUiState.Ready(benchmarkDataSeeder.prepareScenario(scenario))
            }.getOrElse { throwable ->
                BenchmarkHarnessUiState.Error(
                    throwable.stackTraceToString().lineSequence().firstOrNull().orEmpty()
                )
            }
        }
    }
}

@Composable
private fun BenchmarkHarnessRoot(
    scenario: BenchmarkScenario,
    windowSizeClass: WindowSizeClass,
    uiState: BenchmarkHarnessUiState,
    playlistRepository: PlaylistRepository,
    albumGroupRepository: AlbumGroupRepository
) {
    when (uiState) {
        BenchmarkHarnessUiState.Loading -> {
            BenchmarkStatusScreen("benchmark-loading:${scenario.value}")
        }

        is BenchmarkHarnessUiState.Error -> {
            BenchmarkStatusScreen("benchmark-error:${uiState.message}")
        }

        is BenchmarkHarnessUiState.Ready -> {
            Box(modifier = Modifier.fillMaxSize()) {
                BenchmarkScenarioScreen(
                    scenario = scenario,
                    windowSizeClass = windowSizeClass,
                    seedSummary = uiState.seedSummary,
                    playlistRepository = playlistRepository,
                    albumGroupRepository = albumGroupRepository
                )
                Text(
                    text = "benchmark-ready:${scenario.value}",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.24f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BenchmarkScenarioScreen(
    scenario: BenchmarkScenario,
    windowSizeClass: WindowSizeClass,
    seedSummary: BenchmarkSeedSummary,
    playlistRepository: PlaylistRepository,
    albumGroupRepository: AlbumGroupRepository
) {
    when (scenario) {
        BenchmarkScenario.LibraryAlbums,
        BenchmarkScenario.LibraryTracks -> {
            LibraryScreen(
                windowSizeClass = windowSizeClass,
                onAlbumClick = {},
                onPlayTracks = { _, _, _ -> },
                onOpenPlaylistPicker = { _, _, _, _, _, _, _, _ -> },
                onOpenGroupPicker = {},
                onOpenFilterScreen = {}
            )
        }

        BenchmarkScenario.SearchNetwork -> {
            SearchScreen(
                windowSizeClass = windowSizeClass,
                onAlbumClick = {}
            )
        }

        BenchmarkScenario.FavoritesDetail -> {
            val items by playlistRepository
                .observePlaylistItemsWithSubtitles(seedSummary.favoritesPlaylistId)
                .collectAsState(initial = emptyList())
            PlaylistDetailContent(
                windowSizeClass = windowSizeClass,
                title = PlaylistRepository.PLAYLIST_FAVORITES,
                items = items,
                onPlayAll = { _, _ -> },
                onRemoveItem = {},
                onMoveItemToTop = {},
                onMoveItemToBottom = {},
                onSaveManualOrder = {}
            )
        }

        BenchmarkScenario.PlaylistsList -> {
            PlaylistsScreen(
                windowSizeClass = windowSizeClass,
                onPlaylistClick = {}
            )
        }

        BenchmarkScenario.PlaylistDetail -> {
            val items by playlistRepository
                .observePlaylistItemsWithSubtitles(seedSummary.detailPlaylistId)
                .collectAsState(initial = emptyList())
            PlaylistDetailContent(
                windowSizeClass = windowSizeClass,
                title = seedSummary.detailPlaylistName,
                items = items,
                onPlayAll = { _, _ -> },
                onRemoveItem = {},
                onMoveItemToTop = {},
                onMoveItemToBottom = {},
                onSaveManualOrder = {}
            )
        }

        BenchmarkScenario.PlaylistPicker -> {
            PlaylistPickerScreen(
                windowSizeClass = windowSizeClass,
                mediaId = seedSummary.sampleMediaId,
                uri = seedSummary.sampleUri,
                title = seedSummary.sampleTitle,
                artist = seedSummary.sampleArtist,
                artworkUri = seedSummary.sampleArtworkUri,
                albumId = seedSummary.sampleAlbumId,
                trackId = seedSummary.sampleTrackId,
                rjCode = seedSummary.sampleRjCode,
                embeddedInDialog = true,
                onBack = {}
            )
        }

        BenchmarkScenario.GroupsList -> {
            AlbumGroupsScreen(
                windowSizeClass = windowSizeClass,
                onGroupClick = {}
            )
        }

        BenchmarkScenario.GroupDetail -> {
            val tracks by albumGroupRepository
                .observeGroupTracks(seedSummary.detailGroupId)
                .collectAsState(initial = emptyList())
            AlbumGroupDetailContent(
                windowSizeClass = windowSizeClass,
                title = seedSummary.detailGroupName,
                tracks = tracks,
                onPlayMediaItems = { _, _ -> },
                onRemoveTrack = {},
                onRemoveAlbum = {},
                onMoveTrackToTop = { _, _ -> },
                onMoveTrackToBottom = { _, _ -> },
                onSaveAlbumTrackOrder = { _, _ -> }
            )
        }

        BenchmarkScenario.GroupPicker -> {
            AlbumGroupPickerScreen(
                windowSizeClass = windowSizeClass,
                albumId = seedSummary.sampleAlbumId,
                onBack = {}
            )
        }

        BenchmarkScenario.Queue -> {
            val playerViewModel: PlayerViewModel = hiltViewModel()
            QueueSheetContent(
                viewModel = playerViewModel,
                onDismiss = {}
            )
        }
    }
}

@Composable
private fun BenchmarkStatusScreen(
    label: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private sealed interface BenchmarkHarnessUiState {
    data object Loading : BenchmarkHarnessUiState
    data class Ready(val seedSummary: BenchmarkSeedSummary) : BenchmarkHarnessUiState
    data class Error(val message: String) : BenchmarkHarnessUiState
}
