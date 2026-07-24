package com.asmr.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.asmr.player.data.lyrics.EXTRA_ALBUM_WORK_ID
import com.asmr.player.data.lyrics.EXTRA_LYRICS_RELATIVE_PATH_NO_EXT
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
    val contentKey: String = "",
    val isLoading: Boolean = false,
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

    private suspend fun reloadForItem(item: MediaItem?) {
        val mediaId = item?.mediaId.orEmpty()
        if (mediaId.isBlank()) {
            _uiState.value = LyricsUiState()
            return
        }
        val mediaKey = lyricsContentKeyForItem(item)
        _uiState.value = _uiState.value.copy(isLoading = true)
        val result = lyricsLoader.load(item)
        _uiState.value = LyricsUiState(
            title = result.title,
            contentKey = mediaKey,
            isLoading = false,
            lyrics = result.lyrics
        )
    }

    fun refreshCurrentLyrics() {
        viewModelScope.launch {
            reloadForItem(playback.value.currentMediaItem)
        }
    }

    init {
        viewModelScope.launch {
            playerConnection.lyricsReloadRequests.collect {
                reloadForItem(playback.value.currentMediaItem)
            }
        }
        viewModelScope.launch {
            var lastMediaKey: String? = null
            playerConnection.snapshot.collect { snap ->
                val item = snap.currentMediaItem
                val mediaId = item?.mediaId.orEmpty()
                val mediaKey = lyricsContentKeyForItem(item)
                if (mediaId.isBlank()) {
                    _uiState.value = LyricsUiState()
                    return@collect
                }
                if (lastMediaKey == mediaKey) return@collect
                lastMediaKey = mediaKey
                reloadForItem(item)
            }
        }
    }

    private fun lyricsContentKeyForItem(item: MediaItem?): String {
        val mediaId = item?.mediaId.orEmpty()
        val extras = item?.mediaMetadata?.extras
        return listOf(
            mediaId,
            extras?.getString(EXTRA_LYRICS_RELATIVE_PATH_NO_EXT).orEmpty(),
            extras?.getString("rj_code").orEmpty(),
            extras?.getString(EXTRA_ALBUM_WORK_ID).orEmpty()
        ).joinToString("|")
    }
}
