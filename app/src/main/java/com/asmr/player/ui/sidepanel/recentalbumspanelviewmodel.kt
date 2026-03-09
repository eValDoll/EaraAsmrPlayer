package com.asmr.player.ui.sidepanel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.ui.library.LibraryQueryBuilder
import com.asmr.player.ui.library.LibraryQuerySpec
import com.asmr.player.ui.library.LibrarySort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.net.URLDecoder
import javax.inject.Inject

data class RecentAlbumResumeInfo(
    val mediaId: String,
    val positionMs: Long,
    val trackTitle: String?
)

data class RecentAlbumUiItem(
    val album: AlbumEntity,
    val resume: RecentAlbumResumeInfo?
)

@HiltViewModel
class RecentAlbumsPanelViewModel @Inject constructor(
    private val db: AppDatabase
) : ViewModel() {
    private val query = LibraryQueryBuilder.build(LibraryQuerySpec(sort = LibrarySort.LastPlayedDesc))

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<RecentAlbumUiItem>> = db.albumDao()
        .queryAlbums(query)
        .map { albums -> albums.filter { it.id > 0L }.take(5) }
        .distinctUntilChanged()
        .flatMapLatest { recent ->
            val albumIds = recent.map { it.id }
            if (albumIds.isEmpty()) {
                flowOf(emptyList())
            } else {
                db.trackPlaybackProgressDao()
                    .observeLastPlayedRowsForAlbums(albumIds)
                    .map { rows ->
                        val resumeByAlbumId = LinkedHashMap<Long, RecentAlbumResumeInfo?>()
                        rows.forEach { row ->
                            val albumId = row.albumId ?: return@forEach
                            if (resumeByAlbumId.containsKey(albumId)) return@forEach
                            val title = row.trackTitle.orEmpty().trim()
                                .ifBlank { deriveTitleFromMediaId(row.mediaId) }
                                .trim()
                                .takeIf { it.isNotBlank() }
                            resumeByAlbumId[albumId] = RecentAlbumResumeInfo(
                                mediaId = row.mediaId,
                                positionMs = row.positionMs.coerceAtLeast(0L),
                                trackTitle = title
                            )
                        }
                        recent.map { album ->
                            RecentAlbumUiItem(
                                album = album,
                                resume = resumeByAlbumId[album.id]
                            )
                        }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())
}

private fun deriveTitleFromMediaId(mediaId: String): String {
    val id = mediaId.trim()
    if (id.isBlank()) return ""
    if (id.startsWith("http", ignoreCase = true)) {
        val last = runCatching { Uri.parse(id).lastPathSegment }.getOrNull().orEmpty()
            .ifBlank { id.substringAfterLast('/') }
        val clean = last.substringBefore('?').substringBefore('#')
        val decoded = runCatching { URLDecoder.decode(clean, "UTF-8") }.getOrDefault(clean)
        return decoded.substringBeforeLast('.', decoded).ifBlank { id }
    }
    return runCatching { File(id).nameWithoutExtension }.getOrDefault(id).ifBlank { id }
}
