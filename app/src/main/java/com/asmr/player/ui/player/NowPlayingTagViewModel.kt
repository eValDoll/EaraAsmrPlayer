package com.asmr.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.dao.TagWithCount
import com.asmr.player.data.local.db.entities.TagEntity
import com.asmr.player.data.local.db.entities.TagSource
import com.asmr.player.data.local.db.entities.TrackTagEntity
import com.asmr.player.util.TagNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingTagViewModel @Inject constructor(
    private val database: AppDatabase
) : ViewModel() {
    data class DialogState(
        val trackId: Long,
        val title: String,
        val inheritedTags: List<String>,
        val userTags: List<String>
    )

    val availableTags: StateFlow<List<TagWithCount>> = database.tagDao()
        .getTagsWithCounts(TagSource.USER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _dialogState = MutableStateFlow<DialogState?>(null)
    val dialogState: StateFlow<DialogState?> = _dialogState.asStateFlow()

    fun openForMediaId(mediaId: String, fallbackTitle: String) {
        if (mediaId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val track = database.trackDao().getTrackByPathOnce(mediaId) ?: return@launch
            val album = database.albumDao().getAlbumById(track.albumId)
            val albumTagsCsv = album?.tags.orEmpty()
            val userAlbumTagsCsv = database.tagDao().getAlbumTagsCsvOnce(track.albumId, TagSource.USER).orEmpty()
            val inherited = parseTagsCsv(listOf(albumTagsCsv, userAlbumTagsCsv).filter { it.isNotBlank() }.joinToString(","))
            val userCsv = database.trackTagDao().getTrackTagsCsvOnce(track.id, TagSource.USER).orEmpty()
            val user = parseTagsCsv(userCsv)
            val title = track.title.ifBlank { fallbackTitle.ifBlank { "音轨" } }
            _dialogState.value = DialogState(trackId = track.id, title = title, inheritedTags = inherited, userTags = user)
        }
    }

    fun dismiss() {
        _dialogState.value = null
    }

    fun applyUserTags(tags: List<String>) {
        val current = _dialogState.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val pairs = tags
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { it to TagNormalizer.normalize(it) }
                .filter { it.second.isNotBlank() }
                .distinctBy { it.second }
                .toList()

            val tagDao = database.tagDao()
            val trackTagDao = database.trackTagDao()
            database.withTransaction {
                trackTagDao.deleteTrackTagsByTrackIdAndSource(current.trackId, TagSource.USER)
                if (pairs.isNotEmpty()) {
                    val tagEntities = pairs.map { (name, normalized) ->
                        TagEntity(name = name, nameNormalized = normalized)
                    }
                    tagDao.insertTags(tagEntities)
                    val persisted = tagDao.getTagsByNormalized(pairs.map { it.second })
                    val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })
                    val refs = pairs.mapNotNull { (_, normalized) ->
                        val tagId = idByNormalized[normalized] ?: return@mapNotNull null
                        TrackTagEntity(trackId = current.trackId, tagId = tagId, source = TagSource.USER)
                    }
                    if (refs.isNotEmpty()) trackTagDao.insertTrackTags(refs)
                }
            }
            _dialogState.value = null
        }
    }

    private fun parseTagsCsv(csv: String): List<String> {
        val parts = csv.split(',', '，')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (parts.isEmpty()) return emptyList()
        val out = ArrayList<String>(parts.size)
        val seen = HashSet<String>(parts.size)
        parts.forEach { raw ->
            val normalized = TagNormalizer.normalize(raw)
            if (normalized.isBlank()) return@forEach
            if (!seen.add(normalized)) return@forEach
            out.add(raw)
        }
        return out
    }
}

