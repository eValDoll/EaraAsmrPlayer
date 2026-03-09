package com.asmr.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.lyrics.LyricsLoader
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LyricsUiState(
    val title: String = "",
    val lyrics: List<SubtitleEntry> = emptyList()
)

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val playerConnection: PlayerConnection,
    private val lyricsLoader: LyricsLoader
) : ViewModel() {
    private val _uiState = MutableStateFlow(LyricsUiState())
    val uiState: StateFlow<LyricsUiState> = _uiState.asStateFlow()

    val playback = playerConnection.snapshot

    init {
        viewModelScope.launch {
            var lastMediaId: String? = null
            playerConnection.snapshot.collect { snap ->
                val mediaId = snap.currentMediaItem?.mediaId.orEmpty()
                if (mediaId.isBlank()) {
                    _uiState.value = _uiState.value.copy(title = "", lyrics = emptyList())
                    return@collect
                }
                if (lastMediaId == mediaId) return@collect
                lastMediaId = mediaId

                val fallbackTitle = snap.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
                val result = lyricsLoader.load(mediaId, fallbackTitle)
                _uiState.value = _uiState.value.copy(title = result.title, lyrics = result.lyrics)
            }
        }
    }
}
