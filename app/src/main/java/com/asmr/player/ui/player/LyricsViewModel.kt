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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
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
    val playback = playerConnection.snapshot

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LyricsUiState> = combine(
        playback.map { it.currentMediaItem }.distinctUntilChangedBy(::lyricsContentKeyForItem),
        playerConnection.lyricsReloadRequests.onStart { emit(Unit) }
    ) { item, _ -> item }.flatMapLatest { item ->
        val mediaKey = lyricsContentKeyForItem(item)
        lyricsLoader.observe(item).map { result ->
            LyricsUiState(title = result.title, contentKey = mediaKey, lyrics = result.lyrics)
        }.onStart {
            emit(LyricsUiState(contentKey = mediaKey, isLoading = item != null))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsUiState())

    fun refreshCurrentLyrics() {
        playerConnection.requestLyricsReload()
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
