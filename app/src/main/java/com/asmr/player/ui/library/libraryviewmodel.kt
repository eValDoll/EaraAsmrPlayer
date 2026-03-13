package com.asmr.player.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import androidx.room.withTransaction
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.dao.AlbumDao
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.local.db.dao.LibraryTrackRow
import com.asmr.player.data.local.db.dao.LibraryTrackAlbumHeaderRow
import com.asmr.player.data.local.db.dao.TagWithCount
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumTagEntity
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TagEntity
import com.asmr.player.data.local.db.entities.TagSource
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.db.entities.TrackTagEntity
import com.asmr.player.data.remote.api.AsmrOneApi
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.data.remote.dlsite.DlsiteProductInfoClient
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.util.GlobalSyncState
import com.asmr.player.util.ScanRootsStore
import com.asmr.player.util.SubtitleParser
import com.asmr.player.util.MessageManager
import com.asmr.player.util.SyncCoordinator
import com.asmr.player.util.TagNormalizer
import com.asmr.player.util.TrackKeyNormalizer
import com.asmr.player.util.isOnlineTrackPath
import com.asmr.player.util.isVirtualAlbumPath
import com.asmr.player.util.EmbeddedMediaExtractor
import com.asmr.player.work.AlbumCoverThumbWorker
import com.asmr.player.work.TrackDurationWorker
import com.google.gson.Gson
import com.asmr.player.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class LibraryUiState {
    object Loading : LibraryUiState()
    data class BulkInProgress(val progress: BulkProgress) : LibraryUiState()
    data class Success(val syncingAlbums: Map<Long, SyncStatus> = emptyMap()) : LibraryUiState()
}

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

enum class BulkPhase {
    ScanningLocal,
    SyncingCloud
}

data class BulkProgress(
    val phase: BulkPhase,
    val current: Int,
    val total: Int,
    val currentAlbumTitle: String = "",
    val currentFile: String = "",
    val startedAtElapsedMs: Long = SystemClock.elapsedRealtime()
) {
    val fraction: Float = if (total <= 0) 0f else (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}


@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val database: AppDatabase,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val dlsiteScraper: DLSiteScraper,
    private val dlsiteProductInfoClient: DlsiteProductInfoClient,
    private val asmrOneApi: AsmrOneApi,
    private val settingsRepository: SettingsRepository,
    private val syncCoordinator: SyncCoordinator,
    @Named("image") private val imageOkHttpClient: OkHttpClient,
    private val messageManager: MessageManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val scanRootsStore = ScanRootsStore(context)
    private val presetStore = LibraryPresetStore(context)
    private val _scanRoots = MutableStateFlow<Set<String>>(emptySet())
    val scanRoots: StateFlow<List<String>> = _scanRoots
        .map { it.toList().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _syncStatus = MutableStateFlow<Map<Long, SyncStatus>>(emptyMap())
    private val _bulkProgress = MutableStateFlow<BulkProgress?>(null)
    val bulkProgress: StateFlow<BulkProgress?> = _bulkProgress.asStateFlow()
    val globalSyncState: StateFlow<GlobalSyncState?> = syncCoordinator.state
    val isGlobalSyncRunning: StateFlow<Boolean> = globalSyncState
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), globalSyncState.value != null)
    private val scanMutex = Mutex()
    private val bulkStartMutex = Mutex()
    private val dlsiteLocaleCache = ConcurrentHashMap<String, String>()
    private var bulkJob: Job? = null
    private val albumJobs = ConcurrentHashMap<Long, Job>()
    private var lastFileUpdateElapsedMs: Long = 0L
    private val _querySpec = MutableStateFlow(LibraryQuerySpec())
    val querySpec: StateFlow<LibraryQuerySpec> = _querySpec.asStateFlow()
    private val _expandedTrackAlbumIds = MutableStateFlow<Set<Long>>(emptySet())

    val availableTags: StateFlow<List<TagWithCount>> = database.tagDao()
        .getTagsWithCounts(TagSource.USER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableCircles: StateFlow<List<String>> = albumDao.getDistinctCircles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableCvs: StateFlow<List<String>> = albumDao.getDistinctCvs()
        .map { rows ->
            val result = ArrayList<String>(rows.size)
            val seen = HashSet<String>(rows.size)
            rows.forEach { raw ->
                raw.split(',', '，')
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { token ->
                        val normalized = token
                            .replace(" ", "")
                            .replace("　", "")
                            .lowercase()
                        if (normalized.isNotBlank() && seen.add(normalized)) {
                            result.add(token)
                        }
                    }
            }
            result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filterPresets: StateFlow<List<LibraryFilterPreset>> = presetStore.presets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userTagsByAlbumId: StateFlow<Map<Long, List<String>>> = database.tagDao()
        .getAlbumTagsBySource(TagSource.USER)
        .map { rows ->
            rows.associate { row ->
                val tags = row.tagsCsv
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                row.albumId to tags
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val userTagsByTrackId: StateFlow<Map<Long, List<String>>> = database.trackTagDao()
        .getTrackTagsBySource(TagSource.USER)
        .map { rows ->
            rows.associate { row ->
                val tags = row.tagsCsv
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                row.trackId to tags
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        _scanRoots.value = runCatching { scanRootsStore.getRoots() }.getOrDefault(emptySet())
        viewModelScope.launch(Dispatchers.IO) {
            ensureTagTablesInitialized()
        }
        viewModelScope.launch {
            val shouldAutoScan = withContext(Dispatchers.IO) {
                val hasAnyAlbum = runCatching { albumDao.getAllAlbumsOnce().isNotEmpty() }.getOrDefault(false)
                if (hasAnyAlbum) return@withContext false
                val hasRoots = runCatching { scanRootsStore.getRoots().isNotEmpty() }.getOrDefault(false)
                val hasDownloaded = runCatching {
                    val baseDir = File(context.getExternalFilesDir(null), "albums")
                    baseDir.exists() &&
                        baseDir.isDirectory &&
                        (baseDir.listFiles()?.any { it.isDirectory && File(it, ".download_complete").exists() } == true)
                }.getOrDefault(false)
                hasRoots || hasDownloaded
            }
            if (!shouldAutoScan) return@launch
            delay(450)
            scanAllRoots()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val syncingAlbums: StateFlow<Map<Long, SyncStatus>> = _syncStatus.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = _bulkProgress
        .combine(syncingAlbums) { bulk, syncing ->
            if (bulk != null) LibraryUiState.BulkInProgress(bulk) else LibraryUiState.Success(syncing)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Success())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedAlbums = _querySpec
        .map { it }
        .distinctUntilChanged()
        .flatMapLatest { spec ->
            Pager(
                config = PagingConfig(pageSize = 40, prefetchDistance = 10, enablePlaceholders = false),
                pagingSourceFactory = { albumDao.queryAlbumsPaged(LibraryQueryBuilder.build(spec)) }
            ).flow
        }
        .combine(userTagsByAlbumId) { paging, userTags ->
            paging.map { entity -> entity.toAlbum(userTags[entity.id].orEmpty()) }
        }
        .cachedIn(viewModelScope)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedTrackAlbumHeaders = _querySpec
        .map { it }
        .distinctUntilChanged()
        .flatMapLatest { spec ->
            Pager(
                config = PagingConfig(pageSize = 40, prefetchDistance = 10, enablePlaceholders = false),
                pagingSourceFactory = { trackDao.queryLibraryTrackAlbumHeadersPaged(LibraryTrackQueryBuilder.buildAlbumHeaders(spec)) }
            ).flow
        }
        .cachedIn(viewModelScope)

    private fun AlbumEntity.toAlbum(userTagsForAlbum: List<String>): Album {
        val baseTags = tags
            .split(",")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        val allTags = (baseTags + userTagsForAlbum).distinct()
        return Album(
            id = id,
            title = title,
            path = path,
            localPath = localPath,
            downloadPath = downloadPath,
            circle = circle,
            cv = cv,
            tags = allTags,
            coverUrl = coverUrl,
            coverPath = coverPath,
            coverThumbPath = coverThumbPath,
            workId = workId,
            rjCode = rjCode.ifBlank { workId },
            description = description
        )
    }

    suspend fun loadInheritedTagsForAlbum(albumId: Long): List<String> {
        if (albumId <= 0L) return emptyList()
        val userTags = userTagsByAlbumId.value[albumId].orEmpty()
        val entity = albumDao.getAlbumById(albumId) ?: return userTags
        val baseTags = entity.tags
            .split(",")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        return (baseTags + userTags).distinct()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val expandedTrackAlbumTracks: StateFlow<Map<Long, List<LibraryTrackRow>>> = _expandedTrackAlbumIds
        .combine(_querySpec) { ids, spec -> ids to spec }
        .distinctUntilChanged()
        .flatMapLatest { (ids, spec) ->
            val normalized = ids.asSequence().filter { it > 0L }.distinct().toList()
            if (normalized.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val flows = normalized.map { albumId ->
                    trackDao.queryLibraryTracks(LibraryTrackQueryBuilder.buildForAlbum(spec, albumId))
                        .map { rows -> albumId to rows }
                }
                combine(flows) { pairs -> pairs.toMap() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setExpandedTrackAlbums(albumIds: Set<Long>) {
        _expandedTrackAlbumIds.value = albumIds.asSequence().filter { it > 0L }.toSet()
    }

    val libraryViewMode: StateFlow<Int?> = settingsRepository.libraryViewMode
        .map<Int, Int?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSearchQuery(query: String) {
        val trimmed = query.trim()
        val newText = trimmed.ifBlank { null }
        _querySpec.update { current ->
            if (current.textQuery == newText) current else current.copy(textQuery = newText)
        }
    }

    fun setSort(sort: LibrarySort) {
        _querySpec.update { current ->
            if (current.sort == sort) current else current.copy(sort = sort)
        }
    }

    fun setSourceFilter(filter: LibrarySourceFilter?) {
        _querySpec.update { current ->
            if (current.source == filter) current else current.copy(source = filter)
        }
    }

    fun toggleTag(tagId: Long) {
        _querySpec.update { current ->
            val updated = current.includeTagIds.toMutableSet()
            if (!updated.add(tagId)) updated.remove(tagId)
            if (updated == current.includeTagIds) current else current.copy(includeTagIds = updated)
        }
    }

    fun toggleCircle(circle: String) {
        val normalized = circle.trim()
        if (normalized.isBlank()) return
        _querySpec.update { current ->
            val updated = current.circles.toMutableSet()
            if (!updated.add(normalized)) updated.remove(normalized)
            if (updated == current.circles) current else current.copy(circles = updated)
        }
    }

    fun toggleCv(cv: String) {
        val normalized = cv.trim()
        if (normalized.isBlank()) return
        _querySpec.update { current ->
            val updated = current.cvs.toMutableSet()
            if (!updated.add(normalized)) updated.remove(normalized)
            if (updated == current.cvs) current else current.copy(cvs = updated)
        }
    }

    fun clearFilters() {
        _querySpec.update { current ->
            current.copy(
                includeTagIds = emptySet(),
                excludeTagIds = emptySet(),
                circles = emptySet(),
                cvs = emptySet(),
                source = null
            )
        }
    }

    fun applyPreset(preset: LibraryFilterPreset) {
        _querySpec.value = preset.spec
    }

    fun savePreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            presetStore.savePreset(trimmed, _querySpec.value)
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            presetStore.deletePreset(id)
        }
    }

    fun setUserTagsForAlbum(albumId: Long, tagsCsv: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = albumDao.getAlbumById(albumId) ?: return@launch
            val tags = parseAlbumTags(tagsCsv)
            val tagDao = database.tagDao()
            database.withTransaction {
                tagDao.deleteAlbumTagsByAlbumIdAndSource(albumId, TagSource.USER)
                if (tags.isNotEmpty()) {
                    val tagEntities = tags.map { (name, normalized) ->
                        TagEntity(name = name, nameNormalized = normalized)
                    }
                    tagDao.insertTags(tagEntities)
                    val persisted = tagDao.getTagsByNormalized(tags.map { it.second })
                    val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })
                    val refs = tags.mapNotNull { (_, normalized) ->
                        val tagId = idByNormalized[normalized] ?: return@mapNotNull null
                        AlbumTagEntity(albumId = albumId, tagId = tagId, source = TagSource.USER)
                    }
                    if (refs.isNotEmpty()) tagDao.insertAlbumTags(refs)
                }
            }
            upsertAlbumFtsIndex(albumId, entity)
        }
    }

    fun setUserTagsForTrack(trackId: Long, tagsCsv: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val tags = parseAlbumTags(tagsCsv)
            val tagDao = database.tagDao()
            val trackTagDao = database.trackTagDao()
            database.withTransaction {
                trackTagDao.deleteTrackTagsByTrackIdAndSource(trackId, TagSource.USER)
                if (tags.isNotEmpty()) {
                    val tagEntities = tags.map { (name, normalized) ->
                        TagEntity(name = name, nameNormalized = normalized)
                    }
                    tagDao.insertTags(tagEntities)
                    val persisted = tagDao.getTagsByNormalized(tags.map { it.second })
                    val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })
                    val refs = tags.mapNotNull { (_, normalized) ->
                        val tagId = idByNormalized[normalized] ?: return@mapNotNull null
                        TrackTagEntity(trackId = trackId, tagId = tagId, source = TagSource.USER)
                    }
                    if (refs.isNotEmpty()) trackTagDao.insertTrackTags(refs)
                }
            }
        }
    }

    fun renameUserTag(tagId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val tagDao = database.tagDao()
            val albumIds = tagDao.getAlbumIdsForTag(tagId).toMutableSet()
            database.withTransaction {
                val existing = tagDao.getTagById(tagId) ?: return@withTransaction
                val newNormalized = TagNormalizer.normalize(trimmed)
                if (newNormalized.isBlank()) return@withTransaction

                val conflict = tagDao.getTagByNormalized(newNormalized)
                if (conflict != null && conflict.id != existing.id) {
                    albumIds.addAll(tagDao.getAlbumIdsForTag(conflict.id))
                    tagDao.moveAlbumTagsToAnotherTag(existing.id, conflict.id)
                    database.trackTagDao().moveTrackTagsToAnotherTag(existing.id, conflict.id)
                    tagDao.deleteAlbumTagsByTagId(existing.id)
                    database.trackTagDao().deleteTrackTagsByTagId(existing.id)
                    tagDao.deleteTag(existing.id)
                } else {
                    tagDao.updateTag(existing.id, trimmed, newNormalized)
                }
            }
            albumIds.forEach { albumId ->
                val entity = albumDao.getAlbumById(albumId) ?: return@forEach
                upsertAlbumFtsIndex(albumId, entity)
            }
        }
    }

    fun deleteUserTag(tagId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val tagDao = database.tagDao()
            val albumIds = tagDao.getAlbumIdsForTag(tagId)
            database.withTransaction {
                tagDao.deleteAlbumTagsByTagId(tagId)
                database.trackTagDao().deleteTrackTagsByTagId(tagId)
                tagDao.deleteTag(tagId)
            }
            albumIds.forEach { albumId ->
                val entity = albumDao.getAlbumById(albumId) ?: return@forEach
                upsertAlbumFtsIndex(albumId, entity)
            }
        }
    }

    fun setLibraryViewMode(mode: Int) {
        viewModelScope.launch {
            settingsRepository.setLibraryViewMode(mode)
        }
    }

    fun cancelBulkTask() {
        val job = bulkJob
        job?.cancel()
        bulkJob = null
        _bulkProgress.value = null
    }

    private suspend fun cancelBulkTaskAndJoinLocked(timeoutMs: Long = 1_500L) {
        val job = bulkJob ?: run {
            _bulkProgress.value = null
            return
        }
        job.cancel()
        withTimeoutOrNull(timeoutMs) { job.join() }
        if (bulkJob == job) {
            bulkJob = null
        }
        _bulkProgress.value = null
    }

    private fun isBulkTaskRunning(): Boolean {
        return bulkJob?.isActive == true
    }

    private fun showSyncBusy(nextAction: String) {
        val current = syncCoordinator.state.value?.label
        if (current.isNullOrBlank()) {
            messageManager.showInfo("正在执行同步任务，请稍后再试")
        } else {
            messageManager.showInfo("正在执行：$current，请等待完成或取消后再$nextAction")
        }
    }

    private fun tryRegisterAlbumJob(albumId: Long, taskName: String): Boolean {
        if (albumId <= 0L) return false
        if (isBulkTaskRunning()) {
            messageManager.showInfo("正在执行批量任务，请先取消后再$taskName")
            return false
        }
        val existing = albumJobs[albumId]
        if (existing?.isActive == true) {
            messageManager.showInfo("该专辑正在执行${taskName}")
            return false
        }
        return true
    }

    fun cancelAlbumTask(albumId: Long) {
        val job = albumJobs.remove(albumId)
        if (job == null) {
            messageManager.showInfo("没有可取消的任务")
            return
        }
        job.cancel()
        _syncStatus.value -= albumId
        messageManager.showInfo("已取消任务")
    }

    private fun startBulkProgress(phase: BulkPhase, total: Int, current: Int = 0, currentAlbumTitle: String = "") {
        _bulkProgress.value = BulkProgress(
            phase = phase,
            current = current.coerceAtLeast(0),
            total = total.coerceAtLeast(0),
            currentAlbumTitle = currentAlbumTitle,
            currentFile = ""
        )
        lastFileUpdateElapsedMs = 0L
    }

    private fun updateBulkAlbumProgress(current: Int, currentAlbumTitle: String) {
        val p = _bulkProgress.value ?: return
        _bulkProgress.value = p.copy(
            current = current.coerceAtLeast(0),
            currentAlbumTitle = currentAlbumTitle
        )
    }

    private fun maybeUpdateBulkCurrentFile(currentFile: String) {
        val p = _bulkProgress.value ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastFileUpdateElapsedMs < 120L) return
        lastFileUpdateElapsedMs = now
        _bulkProgress.value = p.copy(currentFile = currentFile)
    }

    private fun finishBulkProgress() {
        _bulkProgress.value = null
        lastFileUpdateElapsedMs = 0L
    }

    private fun isImageName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").trim().lowercase()
        return ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    }

    private fun pickCoverFileFromAlbumDir(albumDir: File): File? {
        val top = albumDir.listFiles()?.toList().orEmpty()
        val named = top.firstOrNull { it.isFile && it.name.startsWith("cover.", ignoreCase = true) && isImageName(it.name) }
        if (named != null) return named

        val topImages = top.filter { it.isFile && isImageName(it.name) }
        val topLargest = topImages.maxByOrNull { it.length() }
        if (topLargest != null) return topLargest

        var best: File? = null
        var bestSize = 0L
        albumDir.walkTopDown().forEach { f ->
            if (!f.isFile) return@forEach
            if (!isImageName(f.name)) return@forEach
            val size = runCatching { f.length() }.getOrDefault(0L)
            if (size > bestSize) {
                bestSize = size
                best = f
            }
        }
        return best
    }

    private fun pickCoverNode(nodes: List<DocNode>, treeUri: Uri, albumDocumentId: String): String {
        val named = nodes.firstOrNull { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR && it.displayName.startsWith("cover.", ignoreCase = true) && isImageName(it.displayName) }
        if (named != null) {
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, named.documentId).toString()
        }

        val images = nodes.filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR && isImageName(it.displayName) }
        val largest = images.maxByOrNull { it.sizeBytes }
        if (largest != null) {
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, largest.documentId).toString()
        }

        val deep = walkTree(treeUri, albumDocumentId)
            .filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR && isImageName(it.displayName) }
            .maxByOrNull { it.sizeBytes }
        return deep?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.documentId).toString() }.orEmpty()
    }

    private enum class CacheTreeFileType {
        Audio,
        Video,
        Image,
        Subtitle,
        Text,
        Pdf,
        Other
    }

    private data class CacheLeafEntry(
        val relativePath: String,
        val absolutePath: String,
        val fileType: CacheTreeFileType
    )

    private fun cacheFileTypeForName(fileName: String): CacheTreeFileType {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp3", "wav", "flac", "m4a", "ogg", "aac", "opus" -> CacheTreeFileType.Audio
            "mp4", "mkv", "webm" -> CacheTreeFileType.Video
            "jpg", "jpeg", "png", "webp", "gif" -> CacheTreeFileType.Image
            "lrc", "srt", "vtt" -> CacheTreeFileType.Subtitle
            "txt", "md", "nfo" -> CacheTreeFileType.Text
            "pdf" -> CacheTreeFileType.Pdf
            else -> CacheTreeFileType.Other
        }
    }

    private fun computePathsCacheKey(paths: List<String>): String {
        return paths.map { it.trim() }.filter { it.isNotBlank() }.sorted().joinToString("|")
    }

    private fun computePathsStamp(paths: List<String>): Long {
        val items = paths.map { it.trim() }.filter { it.isNotBlank() }.sorted()
        var acc = 1469598103934665603L
        items.forEach { p ->
            val v = if (p.startsWith("content://")) queryDocumentLastModified(p) else runCatching { File(p).lastModified() }.getOrDefault(0L)
            acc = (acc xor v) * 1099511628211L
        }
        return acc
    }

    private fun queryDocumentLastModified(uriString: String): Long {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return 0L
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null,
                null,
                null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idx < 0) return@use 0L
                if (!cursor.moveToFirst()) return@use 0L
                cursor.getLong(idx)
            } ?: 0L
        }.getOrDefault(0L)
    }

    private suspend fun upsertLocalTreeCache(albumId: Long, albumPaths: List<String>, leaves: List<CacheLeafEntry>) {
        if (albumId <= 0L) return
        val normalizedPaths = albumPaths.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalizedPaths.isEmpty()) return
        val payload = Gson().toJson(leaves)
        val key = computePathsCacheKey(normalizedPaths)
        val stamp = computePathsStamp(normalizedPaths)
        database.localTreeCacheDao().upsert(
            LocalTreeCacheEntity(
                albumId = albumId,
                cacheKey = key,
                stamp = stamp,
                payloadJson = payload,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun addScanRoot(uriString: String): Boolean {
        val existingRoots = scanRootsStore.getRoots()
        
        // 检查重复
        if (existingRoots.contains(uriString)) {
            messageManager.showInfo("扫描目录已存在")
            return false
        }
        
        // 检查嵌套：新目录是否是现有目录的子目录
        val newUri = runCatching { Uri.parse(uriString) }.getOrNull()
        if (newUri != null) {
            for (existingRoot in existingRoots) {
                val existingUri = runCatching { Uri.parse(existingRoot) }.getOrNull() ?: continue
                
                // 检查新目录是否是现有目录的子目录
                if (isSubdirectory(newUri, existingUri)) {
                    messageManager.showInfo("该目录已被包含在现有扫描目录中")
                    return false
                }
                
                // 检查现有目录是否是新目录的子目录
                if (isSubdirectory(existingUri, newUri)) {
                    messageManager.showInfo("该目录包含了现有的扫描目录，请先移除子目录")
                    return false
                }
            }
        }
        
        val added = scanRootsStore.addRoot(uriString)
        _scanRoots.value = runCatching { scanRootsStore.getRoots() }.getOrDefault(emptySet())
        if (added) {
            messageManager.showSuccess("已添加扫描目录")
        }
        return added
    }
    
    private fun isSubdirectory(child: Uri, parent: Uri): Boolean {
        // 比较 URI 的路径部分
        val childPath = child.toString()
        val parentPath = parent.toString()
        
        // 如果是相同的 URI scheme 和 authority
        if (child.scheme != parent.scheme || child.authority != parent.authority) {
            return false
        }
        
        // 获取文档树 ID
        val childTreeId = runCatching { 
            DocumentsContract.getTreeDocumentId(child) 
        }.getOrNull() ?: return false
        
        val parentTreeId = runCatching { 
            DocumentsContract.getTreeDocumentId(parent) 
        }.getOrNull() ?: return false
        
        // 检查子目录关系
        return childTreeId.startsWith(parentTreeId) && childTreeId != parentTreeId
    }

    fun removeScanRoot(uriString: String) {
        scanRootsStore.removeRoot(uriString)
        _scanRoots.value = runCatching { scanRootsStore.getRoots() }.getOrDefault(emptySet())
        messageManager.showInfo("已移除扫描目录")
    }

    fun removeScanRootAndDeleteAlbums(uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            scanRootsStore.removeRoot(uriString)
            _scanRoots.value = runCatching { scanRootsStore.getRoots() }.getOrDefault(emptySet())

            val allAlbums = albumDao.getAllAlbumsOnce()
            val affected = allAlbums.filter { entity ->
                entity.path.startsWith(uriString) ||
                    (entity.localPath?.startsWith(uriString) == true) ||
                    entity.coverPath.startsWith(uriString)
            }

            affected.forEach { entity ->
                val downloadPath = entity.downloadPath
                val keepByDownload = !downloadPath.isNullOrBlank() &&
                    !downloadPath.startsWith("content://") &&
                    runCatching { File(downloadPath).exists() }.getOrDefault(false)

                if (!keepByDownload) {
                    val tracks = trackDao.getTracksForAlbumOnce(entity.id)
                    val hasOnline = isVirtualAlbumPath(entity.path) || tracks.any { isOnlineTrackPath(it.path) }
                    if (!hasOnline) {
                        trackDao.deleteSubtitlesForAlbum(entity.id)
                        trackDao.deleteTracksForAlbum(entity.id)
                        albumDao.deleteAlbum(entity)
                    } else {
                        tracks.filter { it.path.startsWith(uriString) }.forEach { track ->
                            trackDao.deleteSubtitlesForTrack(track.id)
                            trackDao.deleteTrackById(track.id)
                        }
                        val updatedPath = if (entity.path.startsWith(uriString)) (buildOnlineAlbumPath(entity) ?: entity.path) else entity.path
                        val updated = entity.copy(
                            path = updatedPath,
                            localPath = entity.localPath?.takeIf { !it.startsWith(uriString) },
                            coverPath = if (entity.coverPath.startsWith(uriString)) "" else entity.coverPath
                        )
                        albumDao.updateAlbum(updated)
                        upsertAlbumFtsIndex(updated.id, updated)
                    }
                } else {
                    val tracks = trackDao.getTracksForAlbumOnce(entity.id)
                    tracks.filter { it.path.startsWith(uriString) }.forEach { track ->
                        trackDao.deleteSubtitlesForTrack(track.id)
                        trackDao.deleteTrackById(track.id)
                    }

                    val updated = entity.copy(
                        path = if (entity.path.startsWith(uriString)) downloadPath!! else entity.path,
                        localPath = entity.localPath?.takeIf { !it.startsWith(uriString) },
                        coverPath = if (entity.coverPath.startsWith(uriString)) "" else entity.coverPath
                    )
                    albumDao.updateAlbum(updated)
                    upsertAlbumFtsIndex(updated.id, updated)
                }
            }
        }
    }

    fun scanAllRoots() {
        viewModelScope.launch {
            val token = syncCoordinator.tryBegin("刷新本地") ?: run {
                showSyncBusy("刷新本地")
                return@launch
            }
            try {
                bulkStartMutex.withLock {
                    bulkJob = currentCoroutineContext()[Job]
                    try {
                        withContext(Dispatchers.IO) {
                            scanMutex.withLock {
                                currentCoroutineContext().ensureActive()
                                val roots = scanRootsStore.getRoots().toList()
                                val downloadedDirs = runCatching {
                                    val baseDir = File(context.getExternalFilesDir(null), "albums")
                                baseDir.listFiles()?.filter { it.isDirectory && File(it, ".download_complete").exists() }.orEmpty()
                                }.getOrDefault(emptyList())

                                var totalAlbums = downloadedDirs.size
                                roots.forEach { root ->
                                    currentCoroutineContext().ensureActive()
                                    val uri = runCatching { Uri.parse(root) }.getOrNull()
                                    val treeDocId = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
                                    if (uri != null && !treeDocId.isNullOrBlank()) {
                                        totalAlbums += queryChildren(uri, treeDocId).count { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
                                    }
                                }
                                startBulkProgress(phase = BulkPhase.ScanningLocal, total = totalAlbums)

                                var current = 0
                                roots.forEach { root ->
                                    currentCoroutineContext().ensureActive()
                                    scanFromDocumentTree(root) { title ->
                                        current += 1
                                        updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                                    }
                                }
                                scanFromDownloadedDir { title ->
                                    current += 1
                                    updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                                }
                                pruneOrphanedAlbumsByFilesystem()
                            }
                        }
                        messageManager.showSuccess("扫描完成")
                    } catch (e: CancellationException) {
                        messageManager.showInfo("已取消扫描")
                    } catch (e: Exception) {
                        Log.e("LibraryViewModel", "scanAllRoots failed", e)
                        messageManager.showError("扫描失败：${e.message}")
                    } finally {
                        finishBulkProgress()
                        if (bulkJob == currentCoroutineContext()[Job]) {
                            bulkJob = null
                        }
                    }
                }
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun scanSingleRoot(uriString: String) {
        if (uriString.isBlank()) return
        viewModelScope.launch {
            val token = syncCoordinator.tryBegin("刷新目录") ?: run {
                showSyncBusy("刷新目录")
                return@launch
            }
            try {
                bulkStartMutex.withLock {
                    bulkJob = currentCoroutineContext()[Job]
                    try {
                        withContext(Dispatchers.IO) {
                            scanMutex.withLock {
                                currentCoroutineContext().ensureActive()
                                val uri = runCatching { Uri.parse(uriString) }.getOrNull()
                                val treeDocId = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
                                val totalAlbums = if (uri != null && !treeDocId.isNullOrBlank()) {
                                    queryChildren(uri, treeDocId).count { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
                                } else {
                                    0
                                }
                                startBulkProgress(phase = BulkPhase.ScanningLocal, total = totalAlbums)
                                var current = 0
                                scanFromDocumentTree(uriString) { title ->
                                    current += 1
                                    updateBulkAlbumProgress(current = current, currentAlbumTitle = title)
                                }
                            }
                        }
                        messageManager.showSuccess("目录刷新完成")
                    } catch (e: CancellationException) {
                        messageManager.showInfo("已取消刷新")
                    } catch (e: Exception) {
                        Log.e("LibraryViewModel", "scanSingleRoot failed", e)
                        messageManager.showError("刷新失败：${e.message}")
                    } finally {
                        finishBulkProgress()
                        if (bulkJob == currentCoroutineContext()[Job]) {
                            bulkJob = null
                        }
                    }
                }
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun syncMetadata() {
        viewModelScope.launch {
            val token = syncCoordinator.tryBegin("云同步（全量）") ?: run {
                showSyncBusy("云同步")
                return@launch
            }
            try {
                bulkStartMutex.withLock {
                    bulkJob = currentCoroutineContext()[Job]
                    try {
                        val albums = withContext(Dispatchers.IO) { albumDao.getAllAlbumsOnce() }
                        startBulkProgress(phase = BulkPhase.SyncingCloud, total = albums.size)
                        var current = 0
                        albums.forEach { entity ->
                            currentCoroutineContext().ensureActive()
                            current += 1
                            updateBulkAlbumProgress(current = current, currentAlbumTitle = entity.title)
                            withContext(Dispatchers.IO) {
                                syncAlbumMetadataInternal(entity, silent = true)
                            }
                        }
                        messageManager.showSuccess("全量同步完成")
                    } catch (e: CancellationException) {
                        messageManager.showInfo("已取消云同步")
                    } catch (e: Exception) {
                        Log.e("LibraryViewModel", "syncMetadata failed", e)
                        messageManager.showError("云同步失败：${e.message}")
                    } finally {
                        finishBulkProgress()
                        if (bulkJob == currentCoroutineContext()[Job]) {
                            bulkJob = null
                        }
                    }
                }
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun syncMetadataForRoot(uriString: String) {
        if (uriString.isBlank()) return
        viewModelScope.launch {
            val token = syncCoordinator.tryBegin("云同步（目录）") ?: run {
                showSyncBusy("云同步")
                return@launch
            }
            try {
                bulkStartMutex.withLock {
                    bulkJob = currentCoroutineContext()[Job]
                    try {
                        val albums = withContext(Dispatchers.IO) {
                            albumDao.getAllAlbumsOnce()
                                .filter { entity ->
                                    entity.path.startsWith(uriString) || (entity.localPath?.startsWith(uriString) == true)
                                }
                        }
                        startBulkProgress(phase = BulkPhase.SyncingCloud, total = albums.size)
                        var current = 0
                        albums.forEach { entity ->
                            currentCoroutineContext().ensureActive()
                            current += 1
                            updateBulkAlbumProgress(current = current, currentAlbumTitle = entity.title)
                            withContext(Dispatchers.IO) {
                                syncAlbumMetadataInternal(entity, silent = true)
                            }
                        }
                        messageManager.showSuccess("云同步完成")
                    } catch (e: CancellationException) {
                        messageManager.showInfo("已取消云同步")
                    } catch (e: Exception) {
                        Log.e("LibraryViewModel", "syncMetadataForRoot failed", e)
                        messageManager.showError("云同步失败：${e.message}")
                    } finally {
                        finishBulkProgress()
                        if (bulkJob == currentCoroutineContext()[Job]) {
                            bulkJob = null
                        }
                    }
                }
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun syncAlbumMetadata(album: Album) {
        if (!tryRegisterAlbumJob(album.id, "云同步")) return
        val job = viewModelScope.launch {
            val ownerJob = currentCoroutineContext()[Job]
            val token = syncCoordinator.tryBegin("云同步：${album.title}") ?: run {
                showSyncBusy("云同步")
                albumJobs.remove(album.id, ownerJob)
                return@launch
            }
            try {
                val entity = withContext(Dispatchers.IO) { albumDao.getAlbumById(album.id) } ?: return@launch
                withContext(Dispatchers.IO) { syncAlbumMetadataInternal(entity) }
            } catch (e: CancellationException) {
                messageManager.showInfo("已取消云同步：${album.title}")
            } finally {
                albumJobs.remove(album.id, ownerJob)
                syncCoordinator.end(token)
            }
        }
        albumJobs[album.id] = job
    }

    private suspend fun syncAlbumMetadataInternal(entity: AlbumEntity, silent: Boolean = false) {
        val keyword = entity.title.trim()
        var workno = entity.rjCode.ifBlank { entity.workId }.trim().uppercase()
        if (workno.isBlank() && keyword.isNotBlank()) {
            val results = runCatching { dlsiteScraper.search(keyword, page = 1, order = "trend") }.getOrDefault(emptyList())
            if (results.size == 1) {
                workno = results[0].rjCode.trim().uppercase()
            } else {
                if (!silent && results.isNotEmpty()) messageManager.showError("同步失败：搜索结果不唯一")
                if (!silent && results.isEmpty()) messageManager.showError("同步失败：未找到专辑信息")
                return
            }
        }
        if (workno.isBlank()) return

        _syncStatus.value += (entity.id to SyncStatus.Syncing)
        try {
            val locale = resolveDlsiteLocaleForWork(workno)
            val firstTry = runCatching { dlsiteScraper.getDetails(workno, locale = locale) }
            var details = firstTry.getOrNull()
            firstTry.exceptionOrNull()?.let { e ->
                Log.e("LibraryViewModel", "getDetails failed: $workno ($locale)", e)
                if (!silent) messageManager.showError("同步异常：${e.message}")
            }
            if (details == null && keyword.isNotBlank()) {
                val results = runCatching { dlsiteScraper.search(keyword, page = 1, order = "trend") }.getOrDefault(emptyList())
                if (results.size == 1) {
                    val fallbackWorkno = results[0].rjCode.trim().uppercase()
                    if (fallbackWorkno.isNotBlank() && fallbackWorkno != workno) {
                        val fallbackLocale = resolveDlsiteLocaleForWork(fallbackWorkno)
                        val fallbackTry = runCatching { dlsiteScraper.getDetails(fallbackWorkno, locale = fallbackLocale) }
                        details = fallbackTry.getOrNull()
                        fallbackTry.exceptionOrNull()?.let { e ->
                            Log.e("LibraryViewModel", "getDetails fallback failed: $fallbackWorkno ($fallbackLocale)", e)
                            if (!silent) messageManager.showError("同步异常：${e.message}")
                        }
                        if (details != null) workno = fallbackWorkno
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (details != null) {
                val updated = entity.copy(
                    title = details.title.ifBlank { entity.title },
                    circle = details.circle.ifBlank { entity.circle },
                    cv = details.cv.ifBlank { entity.cv },
                    tags = if (details.tags.isNotEmpty()) details.tags.joinToString(",") else entity.tags,
                    coverUrl = details.coverUrl.ifBlank { entity.coverUrl },
                    description = details.description.ifBlank { entity.description },
                    rjCode = workno
                )
                albumDao.updateAlbum(updated)
                upsertAlbumFtsIndex(updated.id, updated)
                upsertAlbumTagsFromCsv(updated.id, updated.tags, TagSource.AUTO)
                if (updated.coverPath.trim().isBlank() && updated.coverThumbPath.trim().isBlank()) {
                    runCatching {
                        ensureAlbumCoverSaved(updated.id, updated.coverPath, updated.coverUrl)
                    }
                }
                if (!silent) messageManager.showSuccess("元数据同步成功：${details.title}")
            } else if (!silent) {
                messageManager.showError("同步失败：未找到专辑信息")
            }
            _syncStatus.value -= entity.id
        } catch (e: CancellationException) {
            _syncStatus.value -= entity.id
            throw e
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "syncAlbumMetadataInternal failed: ${entity.id}", e)
            _syncStatus.value += (entity.id to SyncStatus.Error(e.message ?: "同步失败"))
            if (!silent) messageManager.showError("同步异常：${e.message}")
            if (!silent) delay(3000)
            _syncStatus.value -= entity.id
        }
    }

    private fun isLikelyPlaceholderCover(url: String): Boolean {
        val s = url.trim().lowercase()
        if (!s.startsWith("http")) return true
        return s.contains("noimage") ||
            s.contains("no_image") ||
            s.contains("no-image") ||
            s.contains("placeholder") ||
            s.endsWith("/0.jpg") ||
            s.endsWith("/0.png")
    }

    private suspend fun ensureAlbumCoverSaved(
        albumId: Long,
        coverPath: String,
        coverUrl: String
    ): Boolean {
        fun debugLog(msg: String) {
            if (BuildConfig.DEBUG) Log.d("LibraryViewModel", msg)
        }
        fun fail(reason: String): Boolean {
            debugLog("ensureAlbumCoverSaved fail albumId=$albumId reason=$reason coverPath=${coverPath.take(160)} coverUrl=${coverUrl.take(160)}")
            return false
        }

        val existingPathRaw = coverPath.trim().takeIf { it.isNotBlank() && it != "null" }
        if (existingPathRaw != null && !existingPathRaw.startsWith("content://", ignoreCase = true)) {
            val f = if (existingPathRaw.startsWith("file://", ignoreCase = true)) {
                runCatching { Uri.parse(existingPathRaw).path.orEmpty() }.getOrNull()?.let { File(it) }
            } else {
                File(existingPathRaw)
            }
            if (f != null && f.exists() && f.length() > 0L) return true
        }

        val url = coverUrl.trim().takeIf { it.isNotBlank() && it != "null" }?.let { u ->
            if (u.startsWith("//")) "https:$u" else u
        }.orEmpty()
        val canUseNetwork = url.isNotBlank() && !isLikelyPlaceholderCover(url)
        if (!canUseNetwork) return fail("no_network_cover")

        val sourceHash = url.hashCode().toString()
        val coverDir = File(context.filesDir, "album_covers").apply { if (!exists()) mkdirs() }
        val thumbDir = File(context.filesDir, "album_thumbs").apply { if (!exists()) mkdirs() }
        val coverFile = File(coverDir, "a_${albumId}_$sourceHash.jpg")
        val thumbFile = File(thumbDir, "a_${albumId}_${sourceHash}_v2.jpg")

        if (coverFile.exists() && coverFile.length() > 0L && thumbFile.exists() && thumbFile.length() > 0L) {
            val entity = runCatching { albumDao.getAlbumById(albumId) }.getOrNull()
            if (entity != null && (entity.coverPath != coverFile.absolutePath || entity.coverThumbPath != thumbFile.absolutePath)) {
                runCatching { albumDao.updateAlbum(entity.copy(coverPath = coverFile.absolutePath, coverThumbPath = thumbFile.absolutePath)) }
            }
            return true
        }

        val tmpFile = File(coverDir, "a_${albumId}_$sourceHash.tmp")
        val bitmap = try {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "image/*")
                .get()
                .build()
            imageOkHttpClient.newCall(req).execute().use { resp ->
                val contentType = resp.header("Content-Type").orEmpty()
                debugLog("ensureAlbumCoverSaved http albumId=$albumId code=${resp.code} type=$contentType url=${url.take(160)}")
                if (!resp.isSuccessful) return fail("http_${resp.code}")
                if (contentType.isNotBlank() && !contentType.startsWith("image/", ignoreCase = true)) return fail("not_image_$contentType")
                val body = resp.body ?: return fail("empty_body")
                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                        }
                        out.flush()
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmpFile.absolutePath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return fail("decode_bounds_invalid")
            val maxDim = maxOf(w, h)
            var sample = 1
            while (maxDim / sample > 2048) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(tmpFile.absolutePath, opts) ?: return fail("decode_failed_sample_$sample")
        } finally {
            runCatching { if (tmpFile.exists()) tmpFile.delete() }
        }

        FileOutputStream(coverFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        val thumb = centerCropSquare(bitmap, 640)
        FileOutputStream(thumbFile).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        return runCatching {
            val entity = albumDao.getAlbumById(albumId) ?: return true
            albumDao.updateAlbum(entity.copy(coverPath = coverFile.absolutePath, coverThumbPath = thumbFile.absolutePath))
            debugLog("ensureAlbumCoverSaved ok albumId=$albumId cover=${coverFile.length()} thumb=${thumbFile.length()}")
            true
        }.getOrElse { e ->
            fail("db_update_${e.javaClass.simpleName}")
        }
    }

    private fun centerCropSquare(src: Bitmap, size: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val side = minOf(w, h)
        val left = (w - side) / 2
        val top = (h - side) / 2
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            src,
            Rect(left, top, left + side, top + side),
            Rect(0, 0, size, size),
            paint
        )
        return out
    }

    private suspend fun resolveDlsiteLocaleForWork(workno: String): String {
        val normalized = workno.trim().uppercase()
        if (normalized.isBlank()) return "ja_JP"
        dlsiteLocaleCache[normalized]?.let { return it }

        val preferred = preferredLocaleForSystem()
        val editions = runCatching { dlsiteProductInfoClient.fetchLanguageEditions(normalized) }.getOrDefault(emptyList())
        val out = when {
            preferred == "zh_CN" && editions.any { it.lang == "CHI_HANS" } -> "zh_CN"
            preferred == "zh_TW" && editions.any { it.lang == "CHI_HANT" } -> "zh_TW"
            else -> "ja_JP"
        }
        dlsiteLocaleCache[normalized] = out
        return out
    }

    private fun preferredLocaleForSystem(): String {
        val locale = Locale.getDefault()
        val language = locale.language.lowercase()
        val country = locale.country.uppercase()
        if (language != "zh") return "ja_JP"
        return when (country) {
            "TW", "HK", "MO" -> "zh_TW"
            else -> "zh_CN"
        }
    }

    fun rescanAlbum(album: Album) {
        val localPaths = album.getAllLocalPaths()
        if (localPaths.isEmpty()) return
        if (!tryRegisterAlbumJob(album.id, "本地同步")) return
        val job = viewModelScope.launch {
            val ownerJob = currentCoroutineContext()[Job]
            val token = syncCoordinator.tryBegin("本地同步：${album.title}") ?: run {
                showSyncBusy("本地同步")
                albumJobs.remove(album.id, ownerJob)
                return@launch
            }
            _syncStatus.value += (album.id to SyncStatus.Syncing)
            try {
                var removed = false
                withContext(Dispatchers.IO) {
                    currentCoroutineContext().ensureActive()
                    runCatching { database.localTreeCacheDao().deleteByAlbum(album.id) }

                    var scannedAny = false
                    localPaths.forEach { path ->
                        currentCoroutineContext().ensureActive()
                        val p = path.trim()
                        if (p.isBlank()) return@forEach

                        if (p.startsWith("content://")) {
                            val uri = runCatching { Uri.parse(p) }.getOrNull()
                            val docId = uri?.let { runCatching { DocumentsContract.getDocumentId(it) }.getOrNull() }
                            val treeDocId = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
                            val treeUri = if (uri != null && !treeDocId.isNullOrBlank()) {
                                DocumentsContract.buildTreeDocumentUri(uri.authority, treeDocId)
                            } else null
                            val exists = treeUri != null && !docId.isNullOrBlank() && documentExists(treeUri, docId)
                            if (exists) {
                                scanSingleAlbumFromDocumentUri(album.id, p)
                                scannedAny = true
                            }
                        } else {
                            val root = File(p)
                            if (root.exists() && root.isDirectory) {
                                scanTracksAndSubtitlesFromFileAlbum(album.id, root)
                                scannedAny = true
                            }
                        }
                    }

                    if (!scannedAny) {
                        val entity = albumDao.getAlbumById(album.id) ?: return@withContext
                        val tracks = trackDao.getTracksForAlbumOnce(entity.id)
                        val hasOnline = isVirtualAlbumPath(entity.path) || tracks.any { isOnlineTrackPath(it.path) }
                        if (!hasOnline) {
                            trackDao.deleteSubtitlesForAlbum(entity.id)
                            trackDao.deleteTracksForAlbum(entity.id)
                            albumDao.deleteAlbum(entity)
                            removed = true
                        } else {
                            val prefixes = localPaths.map { it.trim() }.filter { it.isNotBlank() }
                            val toRemove = tracks.filter { t ->
                                !isOnlineTrackPath(t.path) && prefixes.any { pfx -> t.path.startsWith(pfx) }
                            }.map { it.id }
                            if (toRemove.isNotEmpty()) {
                                trackDao.deleteSubtitlesForTracks(toRemove)
                                trackDao.deleteTracksByIds(toRemove)
                            }

                            val updatedPath = if (prefixes.any { pfx -> entity.path.startsWith(pfx) }) {
                                buildOnlineAlbumPath(entity) ?: entity.path
                            } else {
                                entity.path
                            }
                            val updated = entity.copy(
                                path = updatedPath,
                                localPath = entity.localPath?.takeIf { lp -> prefixes.none { pfx -> lp.startsWith(pfx) } },
                                downloadPath = entity.downloadPath?.takeIf { dp -> prefixes.none { pfx -> dp.startsWith(pfx) } },
                                coverPath = entity.coverPath.takeIf { cp -> prefixes.none { pfx -> cp.startsWith(pfx) } }.orEmpty()
                            )
                            albumDao.updateAlbum(updated)
                            upsertAlbumFtsIndex(updated.id, updated)
                        }
                    }
                }
                _syncStatus.value -= album.id
                if (removed) {
                    messageManager.showInfo("目录不存在，已从本地库移除")
                } else {
                    messageManager.showSuccess("重扫完成")
                }
            } catch (e: CancellationException) {
                _syncStatus.value -= album.id
                messageManager.showInfo("已取消重扫")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "rescanAlbum failed: ${album.id}", e)
                _syncStatus.value += (album.id to SyncStatus.Error(e.message ?: "重扫失败"))
                messageManager.showError("重扫失败：${e.message}")
                delay(3000)
                _syncStatus.value -= album.id
            } finally {
                albumJobs.remove(album.id, ownerJob)
                syncCoordinator.end(token)
            }
        }
        albumJobs[album.id] = job
    }

    fun deleteAlbum(album: Album) {
        viewModelScope.launch(Dispatchers.IO) {
            if (album.id <= 0L) return@launch
            if (isBulkTaskRunning()) {
                messageManager.showInfo("正在执行批量任务，请先取消后再删除")
                return@launch
            }

            albumJobs.remove(album.id)?.cancel()
            _syncStatus.value -= album.id

            try {
                val entity = albumDao.getAlbumById(album.id) ?: return@launch
                val downloadRoot = entity.downloadPath.orEmpty()

                database.withTransaction {
                    trackDao.deleteSubtitlesForAlbum(album.id)
                    trackDao.deleteTracksForAlbum(album.id)
                    albumDao.deleteAlbum(entity)
                    database.tagDao().deleteAlbumTagsByAlbumId(album.id)
                    database.albumFtsDao().deleteByAlbumId(album.id)
                }

                if (downloadRoot.isNotBlank()) {
                    val downloadDao = database.downloadDao()
                    val task = runCatching { downloadDao.getTaskByRootDir(downloadRoot) }.getOrNull()
                    if (task != null) {
                        runCatching { WorkManager.getInstance(context).cancelAllWorkByTag(task.taskKey) }
                        runCatching { downloadDao.deleteItemsForTask(task.id) }
                        runCatching { downloadDao.deleteTaskById(task.id) }
                    }
                    deletePathSafely(downloadRoot)
                }

                messageManager.showSuccess("已删除专辑")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "deleteAlbum failed: ${album.id}", e)
                messageManager.showError("删除失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun removeTrackFromAlbum(trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val track = trackDao.getTrackByIdOnce(trackId) ?: return@launch
            val album = albumDao.getAlbumById(track.albumId)
            val path = track.path.trim()

            val deletedFile = if (path.startsWith("http", ignoreCase = true) || path.startsWith("content://", ignoreCase = true)) {
                false
            } else {
                val base = context.getExternalFilesDir(null)
                val allowedRoots = listOfNotNull(
                    album?.downloadPath?.takeIf { it.isNotBlank() }?.let { File(it) },
                    base
                )
                    .mapNotNull { runCatching { it.canonicalFile }.getOrNull() ?: it.absoluteFile }
                val target = runCatching { File(path).canonicalFile }.getOrNull() ?: File(path).absoluteFile
                val isInAllowed = allowedRoots.any { root -> target.path.startsWith(root.path) }
                if (isInAllowed) deletePathSafely(target.absolutePath) else false
            }

            database.withTransaction {
                runCatching { database.remoteSubtitleSourceDao().deleteByTrackId(trackId) }
                runCatching { trackDao.deleteSubtitlesForTrack(trackId) }
                runCatching { database.trackTagDao().deleteTrackTagsByTrackId(trackId) }
                runCatching { trackDao.deleteTrackById(trackId) }
                runCatching { database.localTreeCacheDao().deleteByAlbum(track.albumId) }
            }

            if (deletedFile) {
                messageManager.showSuccess("已删除文件并移除")
            } else {
                messageManager.showSuccess("已从专辑移除")
            }
        }
    }

    private fun deletePathSafely(path: String): Boolean {
        if (path.isBlank()) return false
        val externalBase = context.getExternalFilesDir(null)
        val allowedRoots = listOfNotNull(
            externalBase,
            context.filesDir,
            context.cacheDir
        )
            .mapNotNull { runCatching { it.canonicalFile }.getOrNull() ?: it.absoluteFile }

        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: File(path).absoluteFile
        val isAllowed = allowedRoots.any { root -> target.path.startsWith(root.path) }
        if (!isAllowed) return false
        if (!target.exists()) return false

        return if (target.isDirectory) {
            runCatching { target.deleteRecursively() }.getOrDefault(false)
        } else {
            runCatching { target.delete() }.getOrDefault(false)
        }
    }

    private fun extractRjCode(input: String): String {
        val regex = Regex("""RJ\d+""", RegexOption.IGNORE_CASE)
        return regex.find(input)?.value?.uppercase() ?: ""
    }

    private fun buildOnlineAlbumPath(entity: AlbumEntity): String? {
        val rj = extractRjCode(entity.rjCode.ifBlank { entity.workId }.ifBlank { entity.title })
        val workKey = rj.ifBlank { entity.workId.trim() }.ifBlank { entity.title.trim() }.trim()
        if (workKey.isBlank()) return null
        return "web://rj/${workKey.uppercase()}"
    }

    private fun buildTagsToken(tagsCsv: String): String {
        return tagsCsv.split(",")
            .map { TagNormalizer.normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
    }

    private suspend fun upsertAlbumFtsIndex(albumId: Long, entity: AlbumEntity) {
        val userTagsCsv = database.tagDao().getAlbumTagsCsvOnce(albumId, TagSource.USER).orEmpty()
        val combinedTagsCsv = buildString {
            append(entity.tags)
            if (userTagsCsv.isNotBlank()) {
                if (isNotEmpty() && last() != ',') append(',')
                append(userTagsCsv)
            }
        }
        val tagsToken = buildTagsToken(combinedTagsCsv)
        database.albumFtsDao().upsert(
            listOf(
                AlbumFtsEntity(
                    albumId = albumId,
                    title = entity.title,
                    circle = entity.circle,
                    cv = entity.cv,
                    rjCode = entity.rjCode,
                    workId = entity.workId,
                    tagsToken = tagsToken
                )
            )
        )
    }

    private fun parseAlbumTags(tagsCsv: String): List<Pair<String, String>> {
        return tagsCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it to TagNormalizer.normalize(it) }
            .filter { it.second.isNotBlank() }
            .distinctBy { it.second }
    }

    private suspend fun upsertAlbumTagsFromCsv(albumId: Long, tagsCsv: String, source: Int) {
        val tags = parseAlbumTags(tagsCsv)
        if (tags.isEmpty()) return

        val tagEntities = tags.map { (name, normalized) ->
            TagEntity(name = name, nameNormalized = normalized)
        }
        val tagDao = database.tagDao()
        tagDao.insertTags(tagEntities)

        val normalizedList = tags.map { it.second }
        val persisted = tagDao.getTagsByNormalized(normalizedList)
        val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })

        tagDao.deleteAlbumTagsByAlbumIdExceptSource(albumId, TagSource.USER)
        val refs = normalizedList.mapNotNull { normalized ->
            val tagId = idByNormalized[normalized] ?: return@mapNotNull null
            AlbumTagEntity(albumId = albumId, tagId = tagId, source = source)
        }
        if (refs.isNotEmpty()) tagDao.insertAlbumTags(refs)
    }

    private suspend fun ensureTagTablesInitialized() {
        val tagDao = database.tagDao()
        val tagCount = runCatching { tagDao.countTags() }.getOrDefault(0L)
        if (tagCount > 0L) return

        val albums = runCatching { albumDao.getAllAlbumsOnce() }.getOrDefault(emptyList())
        val allPairs = albums.flatMap { parseAlbumTags(it.tags) }
        if (allPairs.isEmpty()) return

        val firstNameByNormalized = LinkedHashMap<String, String>()
        allPairs.forEach { (name, normalized) ->
            if (!firstNameByNormalized.containsKey(normalized)) firstNameByNormalized[normalized] = name
        }

        val tagEntities = firstNameByNormalized.map { (normalized, name) ->
            TagEntity(name = name, nameNormalized = normalized)
        }
        tagDao.insertTags(tagEntities)
        val persisted = tagDao.getTagsByNormalized(firstNameByNormalized.keys.toList())
        val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })

        database.withTransaction {
            albums.forEach { album ->
                val pairs = parseAlbumTags(album.tags)
                if (pairs.isEmpty()) return@forEach
                val refs = pairs.mapNotNull { (_, normalized) ->
                    val tagId = idByNormalized[normalized] ?: return@mapNotNull null
                    AlbumTagEntity(albumId = album.id, tagId = tagId, source = TagSource.AUTO)
                }
                if (refs.isNotEmpty()) {
                    tagDao.deleteAlbumTagsByAlbumIdExceptSource(album.id, TagSource.USER)
                    tagDao.insertAlbumTags(refs)
                }
            }
        }
    }

    private suspend fun scanFromDownloadedDir(onAlbumScanned: ((String) -> Unit)? = null) {
        val baseDir = File(context.getExternalFilesDir(null), "albums")
        if (!baseDir.exists() || !baseDir.isDirectory) return
        val foundDownloadPaths = LinkedHashSet<String>()
        baseDir.listFiles()
            ?.filter { it.isDirectory && File(it, ".download_complete").exists() }
            ?.forEach { albumDir ->
            currentCoroutineContext().ensureActive()
            foundDownloadPaths.add(albumDir.absolutePath)
            val coverFile = pickCoverFileFromAlbumDir(albumDir)
            val title = albumDir.name
            val rj = extractRjCode(title)

            onAlbumScanned?.invoke(title)
            val existing = resolveAndMergeAlbumForRj(
                rj = rj,
                fallbackPath = albumDir.absolutePath,
                fallbackTitle = title,
                localPath = null,
                downloadPath = albumDir.absolutePath
            )
            val entity = AlbumEntity(
                id = existing?.id ?: 0L,
                title = existing?.title?.takeIf { it.isNotBlank() && it != title } ?: title,
                path = existing?.path?.takeIf { it.isNotBlank() } ?: albumDir.absolutePath,
                localPath = existing?.localPath,
                downloadPath = albumDir.absolutePath,
                circle = existing?.circle ?: "",
                cv = existing?.cv ?: "",
                tags = existing?.tags ?: "",
                coverUrl = existing?.coverUrl ?: "",
                coverPath = coverFile?.absolutePath ?: (existing?.coverPath ?: ""),
                coverThumbPath = existing?.coverThumbPath ?: "",
                workId = existing?.workId?.takeIf { it.isNotBlank() } ?: rj,
                rjCode = existing?.rjCode?.takeIf { it.isNotBlank() } ?: rj,
                description = existing?.description ?: ""
            )
            val albumId = albumDao.insertAlbum(entity)
            upsertAlbumFtsIndex(albumId, entity.copy(id = albumId))
            upsertAlbumTagsFromCsv(albumId, entity.tags, TagSource.SCAN)
            if (entity.coverPath.isBlank()) {
                val audio = albumDir.walkTopDown()
                    .firstOrNull { it.isFile && setOf("mp3","flac","wav","m4a","ogg","aac","opus").contains(it.extension.lowercase()) }
                if (audio != null) {
                    val bmp = EmbeddedMediaExtractor.extractArtwork(context, audio.absolutePath)
                    if (bmp != null) {
                        val saved = EmbeddedMediaExtractor.saveArtworkToCache(context, albumId, bmp)
                        if (!saved.isNullOrBlank()) {
                            val updated = entity.copy(coverPath = saved)
                            albumDao.updateAlbum(updated)
                        }
                    }
                }
            }
            enqueueAlbumCoverThumbWork(albumId)
            scanTracksAndSubtitlesFromFileAlbum(albumId, albumDir)
        }
        pruneMissingDownloadedAlbums(baseDir = baseDir, foundDownloadPaths = foundDownloadPaths)
    }

    private suspend fun pruneMissingDownloadedAlbums(
        baseDir: File,
        foundDownloadPaths: Set<String>
    ) {
        val basePrefix = baseDir.absolutePath.trimEnd('\\', '/') + File.separator
        val albums = albumDao.getAllAlbumsOnce()
        val missing = albums.filter { entity ->
            val dl = entity.downloadPath?.trim().orEmpty()
            dl.isNotBlank() &&
                dl.startsWith(basePrefix) &&
                !foundDownloadPaths.contains(dl) &&
                !File(dl).exists()
        }
        if (missing.isEmpty()) return

        database.withTransaction {
            missing.forEach { entity ->
                if (entity.localPath.isNullOrBlank() && !entity.path.startsWith("content://")) {
                    trackDao.deleteSubtitlesForAlbum(entity.id)
                    trackDao.deleteTracksForAlbum(entity.id)
                    albumDao.deleteAlbum(entity)
                } else {
                    val dl = entity.downloadPath?.trim().orEmpty()
                    val tracks = trackDao.getTracksForAlbumOnce(entity.id)
                    tracks.filter { it.path.startsWith(dl) }.forEach { track ->
                        trackDao.deleteSubtitlesForTrack(track.id)
                        trackDao.deleteTrackById(track.id)
                    }

                    val updated = entity.copy(
                        path = if (entity.path.startsWith(dl)) (entity.localPath ?: entity.path) else entity.path,
                        downloadPath = null,
                        coverPath = if (entity.coverPath.startsWith(dl)) "" else entity.coverPath
                    )
                    albumDao.updateAlbum(updated)
                    upsertAlbumFtsIndex(updated.id, updated)
                }
            }
        }
    }

    private fun enqueueTrackDurationWork(albumId: Long) {
        if (albumId <= 0L) return
        val request = OneTimeWorkRequestBuilder<TrackDurationWorker>()
            .setInputData(workDataOf(TrackDurationWorker.KEY_ALBUM_ID to albumId))
            .addTag("track_duration")
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("track_duration_album_$albumId", ExistingWorkPolicy.REPLACE, request)
    }

    private fun enqueueAlbumCoverThumbWork(albumId: Long) {
        if (albumId <= 0L) return
        val request = OneTimeWorkRequestBuilder<AlbumCoverThumbWorker>()
            .setInputData(workDataOf(AlbumCoverThumbWorker.KEY_ALBUM_ID to albumId))
            .addTag("album_cover_thumb")
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("album_cover_thumb_$albumId", ExistingWorkPolicy.REPLACE, request)
    }

    private suspend fun scanTracksAndSubtitlesFromFileAlbum(albumId: Long, albumDir: File) {
        val prefix = albumDir.absolutePath.trimEnd('\\', '/') + File.separator
        val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")

        val audioFiles = mutableListOf<File>()
        val cacheLeaves = mutableListOf<CacheLeafEntry>()
        albumDir.walkTopDown().forEach { f ->
            currentCoroutineContext().ensureActive()
            if (!f.isFile) return@forEach
            val ext = f.extension.lowercase()
            if (audioExtensions.contains(ext)) {
                audioFiles.add(f)
            }

            val type = cacheFileTypeForName(f.name)
            if (type != CacheTreeFileType.Other && type != CacheTreeFileType.Subtitle) {
                val rawRel = runCatching { f.relativeTo(albumDir).path }.getOrElse { f.name }
                val rel = rawRel.replace('\\', '/').trim().trimStart('/')
                if (rel.isNotBlank()) {
                    cacheLeaves.add(CacheLeafEntry(relativePath = rel, absolutePath = f.absolutePath, fileType = type))
                }
            }
        }
        audioFiles.sortBy { it.absolutePath }

        val allExistingTracks = trackDao.getTracksForAlbumOnce(albumId)
        val existingLocalKeys = LinkedHashSet<String>()
        val existingLocalKeysNoGroup = LinkedHashSet<String>()
        allExistingTracks
            .filter { it.path.isNotBlank() && !it.path.startsWith(prefix) && !it.path.trim().startsWith("http", ignoreCase = true) }
            .forEach { t ->
                existingLocalKeys.add(TrackKeyNormalizer.buildKey(t.title, t.group, null))
                existingLocalKeysNoGroup.add(TrackKeyNormalizer.buildKey(t.title, "", null))
            }

        val existingTracks = allExistingTracks
            .filter { it.path.startsWith(prefix) }
            .associateBy { it.path }
        val seenPaths = linkedSetOf<String>()
        val tracksToUpsert = ArrayList<TrackEntity>(audioFiles.size)

        audioFiles.forEach { audio ->
            currentCoroutineContext().ensureActive()
            maybeUpdateBulkCurrentFile(audio.name)
            val trackTitle = audio.nameWithoutExtension
            val relPath = audio.relativeTo(albumDir).path.replace('\\', '/')
            val group =
                if (relPath.contains("/")) relPath.substringBeforeLast('/').substringAfterLast('/', relPath.substringBeforeLast('/')) else ""
            val audioPath = audio.absolutePath
            val key = TrackKeyNormalizer.buildKey(trackTitle, group, null)
            val keyNoGroup = TrackKeyNormalizer.buildKey(trackTitle, "", null)
            if (existingLocalKeys.contains(key) || existingLocalKeysNoGroup.contains(keyNoGroup)) {
                return@forEach
            }
            seenPaths.add(audioPath)

            tracksToUpsert.add(
                TrackEntity(
                    id = existingTracks[audioPath]?.id ?: 0L,
                    albumId = albumId,
                    title = trackTitle,
                    path = audioPath,
                    duration = 0.0,
                    group = group
                )
            )
        }

        val removedIds = existingTracks.values
            .asSequence()
            .filter { !seenPaths.contains(it.path) }
            .map { it.id }
            .toList()

        database.withTransaction {
            if (tracksToUpsert.isNotEmpty()) {
                trackDao.insertTracks(tracksToUpsert)
            }

            if (removedIds.isNotEmpty()) {
                trackDao.deleteSubtitlesForTracks(removedIds)
                trackDao.deleteTracksByIds(removedIds)
            }
        }

        upsertLocalTreeCache(
            albumId = albumId,
            albumPaths = listOf(albumDir.absolutePath),
            leaves = cacheLeaves.distinctBy { it.relativePath }
        )
        enqueueTrackDurationWork(albumId)
    }

    private suspend fun scanFromDocumentTree(uriString: String, onAlbumScanned: ((String) -> Unit)? = null) {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return
        val children = queryChildren(uri, treeDocId).filter { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
        val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val subtitleExtensions = setOf("lrc", "srt", "vtt")
        val foundAlbumPaths = LinkedHashSet<String>()

        children.forEach { albumDir ->
            currentCoroutineContext().ensureActive()
            val albumUri = DocumentsContract.buildDocumentUriUsingTree(uri, albumDir.documentId)
            val albumPath = albumUri.toString()
            foundAlbumPaths.add(albumPath)
            val title = albumDir.displayName.ifBlank { "album" }
            val rj = extractRjCode(title)
            val albumChildren = queryChildren(uri, albumDir.documentId)
            val coverPath = pickCoverNode(albumChildren, uri, albumDir.documentId)

            onAlbumScanned?.invoke(title)
            val existing = resolveAndMergeAlbumForRj(
                rj = rj,
                fallbackPath = albumPath,
                fallbackTitle = title,
                localPath = albumPath,
                downloadPath = null
            )

            val all = walkTree(uri, albumDir.documentId).filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR }
            val audioFiles = all.filter { audioExtensions.contains(it.displayName.substringAfterLast('.', "").lowercase()) }
            val subtitleNodes = all.filter { subtitleExtensions.contains(it.displayName.substringAfterLast('.', "").lowercase()) }
            val subtitleNodeByRelativePath = subtitleNodes.associateBy { it.relativePath }
            val subtitleIndex = buildSubtitleCandidateIndex(
                subtitles = subtitleNodes.map { it.relativePath },
                isSubtitle = { name -> subtitleExtensions.contains(name.substringAfterLast('.', "").lowercase()) }
            )

            data class TrackSpec(
                val title: String,
                val path: String,
                val group: String
            )

            val trackSpecs = ArrayList<TrackSpec>(audioFiles.size)
            val audioRelativeBaseByPath = LinkedHashMap<String, String>(audioFiles.size)
            audioFiles.sortedBy { it.documentId }.forEach { audio ->
                currentCoroutineContext().ensureActive()
                maybeUpdateBulkCurrentFile(audio.displayName)
                val audioUri = DocumentsContract.buildDocumentUriUsingTree(uri, audio.documentId)
                val trackTitle = audio.displayName.substringBeforeLast('.').ifBlank { "track" }
                val group = if (audio.relativePath.contains("/")) audio.relativePath.substringBeforeLast('/').substringAfterLast('/') else ""
                val relativeBase = audio.relativePath.substringBeforeLast('.')
                trackSpecs.add(
                    TrackSpec(
                        title = trackTitle,
                        path = audioUri.toString(),
                        group = group
                    )
                )
                audioRelativeBaseByPath[audioUri.toString()] = relativeBase
            }

            val subtitlesByAudioPath: Map<String, List<com.asmr.player.util.SubtitleEntry>> = trackSpecs.associate { spec ->
                val key = audioRelativeBaseByPath[spec.path].orEmpty()
                val matched = if (key.isBlank()) emptyList() else subtitleIndex.match(key)
                val parsed = matched.mapNotNull { cand ->
                    val node = subtitleNodeByRelativePath[cand.fileName] ?: return@mapNotNull null
                    val entries = readSubtitleFromUri(uri, node.documentId, node.displayName)
                    ParsedSubtitleCandidate(fileName = cand.fileName, language = cand.language, entries = entries)
                }
                spec.path to mergeSubtitleCandidates(parsed)
            }

            var insertedAlbumId = 0L
            database.withTransaction {
                val entity = AlbumEntity(
                    id = existing?.id ?: 0L,
                    title = existing?.title?.takeIf { it.isNotBlank() && it != title } ?: title,
                    path = existing?.path?.takeIf { it.isNotBlank() } ?: albumPath,
                    localPath = albumPath,
                    downloadPath = existing?.downloadPath,
                    circle = existing?.circle ?: "",
                    cv = existing?.cv ?: "",
                    tags = existing?.tags ?: "",
                    coverUrl = existing?.coverUrl ?: "",
                    coverPath = coverPath.ifBlank { existing?.coverPath.orEmpty() },
                    coverThumbPath = existing?.coverThumbPath.orEmpty(),
                    workId = existing?.workId?.takeIf { it.isNotBlank() } ?: rj,
                    rjCode = existing?.rjCode?.takeIf { it.isNotBlank() } ?: rj,
                    description = existing?.description ?: ""
                )
                insertedAlbumId = albumDao.insertAlbum(entity)
                upsertAlbumFtsIndex(insertedAlbumId, entity.copy(id = insertedAlbumId))
                upsertAlbumTagsFromCsv(insertedAlbumId, entity.tags, TagSource.SCAN)

                val allExistingTracks = trackDao.getTracksForAlbumOnce(insertedAlbumId)
                val toDelete = allExistingTracks.filter { it.path.startsWith(albumPath) }.map { it.id }
                if (toDelete.isNotEmpty()) {
                    trackDao.deleteSubtitlesForTracks(toDelete)
                    trackDao.deleteTracksByIds(toDelete)
                }

                val existingLocalKeys = LinkedHashSet<String>()
                val existingLocalKeysNoGroup = LinkedHashSet<String>()
                allExistingTracks
                    .filter { it.path.isNotBlank() && !it.path.startsWith(albumPath) && !it.path.trim().startsWith("http", ignoreCase = true) }
                    .forEach { t ->
                        existingLocalKeys.add(TrackKeyNormalizer.buildKey(t.title, t.group, null))
                        existingLocalKeysNoGroup.add(TrackKeyNormalizer.buildKey(t.title, "", null))
                    }

                val filteredTrackSpecs = trackSpecs.filter { spec ->
                    val key = TrackKeyNormalizer.buildKey(spec.title, spec.group, null)
                    val keyNoGroup = TrackKeyNormalizer.buildKey(spec.title, "", null)
                    !(existingLocalKeys.contains(key) || existingLocalKeysNoGroup.contains(keyNoGroup))
                }

                val tracksToInsert = filteredTrackSpecs.map { spec ->
                    TrackEntity(
                        albumId = insertedAlbumId,
                        title = spec.title,
                        path = spec.path,
                        duration = 0.0,
                        group = spec.group
                    )
                }

                if (tracksToInsert.isNotEmpty()) {
                    val insertedTrackIds = trackDao.insertTracks(tracksToInsert)
                    val subtitlesToInsert = ArrayList<SubtitleEntity>()
                    insertedTrackIds.zip(filteredTrackSpecs).forEach { (trackId, spec) ->
                        val entries = subtitlesByAudioPath[spec.path].orEmpty()
                        entries.forEach { e ->
                            subtitlesToInsert.add(
                                SubtitleEntity(
                                    trackId = trackId,
                                    startMs = e.startMs,
                                    endMs = e.endMs,
                                    text = e.text
                                )
                            )
                        }
                    }
                    if (subtitlesToInsert.isNotEmpty()) {
                        trackDao.insertSubtitles(subtitlesToInsert)
                    }
                }

                val allAfterInsert = trackDao.getTracksForAlbumOnce(insertedAlbumId)
                val localAfterInsert = allAfterInsert.filter { !it.path.trim().startsWith("http", ignoreCase = true) }
                val localKeyToId = LinkedHashMap<String, Long>()
                localAfterInsert.forEach { t ->
                    localKeyToId.putIfAbsent(TrackKeyNormalizer.buildKey(t.title, t.group, null), t.id)
                    localKeyToId.putIfAbsent(TrackKeyNormalizer.buildKey(t.title, "", null), t.id)
                }
                val onlineTracks = allAfterInsert.filter { it.path.trim().startsWith("http", ignoreCase = true) }
                onlineTracks.forEach { online ->
                    val key = TrackKeyNormalizer.buildKey(online.title, online.group, null)
                    val keyNoGroup = TrackKeyNormalizer.buildKey(online.title, "", null)
                    val targetId = localKeyToId[key] ?: localKeyToId[keyNoGroup]
                    if (targetId != null) {
                        val sourceSubs = trackDao.getSubtitlesForTrack(online.id)
                        if (sourceSubs.isNotEmpty()) {
                            val targetHasSubs = trackDao.getSubtitlesForTrack(targetId).isNotEmpty()
                            if (!targetHasSubs) {
                                trackDao.insertSubtitles(
                                    sourceSubs.map { s ->
                                        SubtitleEntity(
                                            trackId = targetId,
                                            startMs = s.startMs,
                                            endMs = s.endMs,
                                            text = s.text
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                val leaves = all.asSequence()
                    .mapNotNull { node ->
                        val t = cacheFileTypeForName(node.displayName)
                        if (t == CacheTreeFileType.Other) return@mapNotNull null
                        val abs = DocumentsContract.buildDocumentUriUsingTree(uri, node.documentId).toString()
                        CacheLeafEntry(relativePath = node.relativePath, absolutePath = abs, fileType = t)
                    }
                    .distinctBy { it.relativePath }
                    .toList()
                val paths = listOfNotNull(entity.path, entity.localPath, entity.downloadPath).map { it.trim() }.filter { it.isNotBlank() }.distinct()
                upsertLocalTreeCache(albumId = insertedAlbumId, albumPaths = paths, leaves = leaves)
            }
            runCatching {
                val persisted = albumDao.getAlbumById(insertedAlbumId)
                val needCover = persisted?.coverPath?.trim().orEmpty().isBlank()
                if (needCover) {
                    val firstAudio = trackSpecs.firstOrNull()?.path
                    if (!firstAudio.isNullOrBlank()) {
                        val bmp = EmbeddedMediaExtractor.extractArtwork(context, firstAudio)
                        if (bmp != null) {
                            val saved = EmbeddedMediaExtractor.saveArtworkToCache(context, insertedAlbumId, bmp)
                            if (!saved.isNullOrBlank()) {
                                val updated = persisted!!.copy(coverPath = saved)
                                albumDao.updateAlbum(updated)
                            }
                        }
                    }
                }
            }
            enqueueAlbumCoverThumbWork(insertedAlbumId)
            enqueueTrackDurationWork(insertedAlbumId)
        }
        pruneMissingDocumentAlbums(rootUriString = uriString, foundAlbumPaths = foundAlbumPaths)
    }

    private suspend fun pruneMissingDocumentAlbums(
        rootUriString: String,
        foundAlbumPaths: Set<String>
    ) {
        val albums = albumDao.getAllAlbumsOnce()
        val missing = albums.filter { entity ->
            val local = entity.localPath?.trim().orEmpty()
            local.isNotBlank() &&
                local.startsWith(rootUriString) &&
                !foundAlbumPaths.contains(local)
        }
        if (missing.isEmpty()) return

        database.withTransaction {
            missing.forEach { entity ->
                if (entity.downloadPath.isNullOrBlank()) {
                    val tracks = trackDao.getTracksForAlbumOnce(entity.id)
                    val hasOnline = isVirtualAlbumPath(entity.path) || tracks.any { isOnlineTrackPath(it.path) }
                    if (!hasOnline) {
                        trackDao.deleteSubtitlesForAlbum(entity.id)
                        trackDao.deleteTracksForAlbum(entity.id)
                        albumDao.deleteAlbum(entity)
                    } else {
                        val root = rootUriString.trim()
                        tracks.filter { it.path.startsWith(root) }.forEach { track ->
                            trackDao.deleteSubtitlesForTrack(track.id)
                            trackDao.deleteTrackById(track.id)
                        }
                        val updatedPath = if (entity.path.startsWith(root)) (buildOnlineAlbumPath(entity) ?: entity.path) else entity.path
                        val updated = entity.copy(
                            path = updatedPath,
                            localPath = entity.localPath?.takeIf { !it.startsWith(root) },
                            coverPath = if (entity.coverPath.startsWith(root)) "" else entity.coverPath
                        )
                        albumDao.updateAlbum(updated)
                        upsertAlbumFtsIndex(updated.id, updated)
                    }
                } else {
                    val root = rootUriString.trim()
                    val tracks = trackDao.getTracksForAlbumOnce(entity.id)
                    tracks.filter { it.path.startsWith(root) }.forEach { track ->
                        trackDao.deleteSubtitlesForTrack(track.id)
                        trackDao.deleteTrackById(track.id)
                    }

                    val updated = entity.copy(
                        path = if (entity.path.startsWith(root)) entity.downloadPath else entity.path,
                        localPath = entity.localPath?.takeIf { !it.startsWith(root) },
                        coverPath = if (entity.coverPath.startsWith(root)) "" else entity.coverPath
                    )
                    albumDao.updateAlbum(updated)
                    upsertAlbumFtsIndex(updated.id, updated)
                }
            }
        }
    }

    private fun existsLocalUri(uriString: String): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return false
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return false
        val treeUri = DocumentsContract.buildTreeDocumentUri(uri.authority, treeDocId)
        return documentExists(treeUri, docId)
    }

    private suspend fun pruneOrphanedAlbumsByFilesystem() {
        val albums = albumDao.getAllAlbumsOnce()
        if (albums.isEmpty()) return

        fun fileExists(path: String): Boolean = runCatching { File(path).exists() }.getOrDefault(false)
        fun uriOrFileExists(pathOrUri: String): Boolean {
            val v = pathOrUri.trim()
            if (v.isBlank()) return false
            if (isVirtualAlbumPath(v) || isOnlineTrackPath(v)) return true
            return if (v.startsWith("content://")) existsLocalUri(v) else fileExists(v)
        }

        database.withTransaction {
            albums.forEach { entity ->
                val local = entity.localPath?.trim().orEmpty()
                val download = entity.downloadPath?.trim().orEmpty()
                val main = entity.path.trim()

                val localMissing = local.isNotBlank() && !uriOrFileExists(local)
                val downloadMissing = download.isNotBlank() && !fileExists(download)
                val mainMissing = main.isNotBlank() && !uriOrFileExists(main)

                var cachedTracks: List<TrackEntity>? = null
                var cachedHasOnlineTracks: Boolean? = null

                var anyValid = when {
                    local.isNotBlank() -> !localMissing
                    download.isNotBlank() -> !downloadMissing
                    else -> !mainMissing
                } || (!localMissing && local.isNotBlank()) || (!downloadMissing && download.isNotBlank())

                if (!anyValid) {
                    val hasOnlineTracks = cachedHasOnlineTracks ?: run {
                        val ts = cachedTracks ?: trackDao.getTracksForAlbumOnce(entity.id).also { cachedTracks = it }
                        ts.any { isOnlineTrackPath(it.path) }.also { cachedHasOnlineTracks = it }
                    }
                    if (!hasOnlineTracks) {
                        trackDao.deleteSubtitlesForAlbum(entity.id)
                        trackDao.deleteTracksForAlbum(entity.id)
                        albumDao.deleteAlbum(entity)
                        return@forEach
                    }
                    anyValid = true
                }

                var updated = entity

                if (localMissing) {
                    val ts = cachedTracks ?: trackDao.getTracksForAlbumOnce(entity.id).also { cachedTracks = it }
                    ts.filter { it.path.startsWith(local) }.forEach { track ->
                        trackDao.deleteSubtitlesForTrack(track.id)
                        trackDao.deleteTrackById(track.id)
                    }
                    updated = updated.copy(
                        path = if (updated.path.startsWith(local)) (download.ifBlank { updated.path }) else updated.path,
                        localPath = null,
                        coverPath = if (updated.coverPath.startsWith(local)) "" else updated.coverPath
                    )
                }

                if (downloadMissing) {
                    val ts = cachedTracks ?: trackDao.getTracksForAlbumOnce(entity.id).also { cachedTracks = it }
                    ts.filter { it.path.startsWith(download) }.forEach { track ->
                        trackDao.deleteSubtitlesForTrack(track.id)
                        trackDao.deleteTrackById(track.id)
                    }
                    updated = updated.copy(
                        path = if (updated.path.startsWith(download)) (local.ifBlank { updated.path }) else updated.path,
                        downloadPath = null,
                        coverPath = if (updated.coverPath.startsWith(download)) "" else updated.coverPath
                    )
                }

                val hasOnlineTracks = cachedHasOnlineTracks ?: run {
                    val ts = cachedTracks ?: trackDao.getTracksForAlbumOnce(entity.id).also { cachedTracks = it }
                    ts.any { isOnlineTrackPath(it.path) }.also { cachedHasOnlineTracks = it }
                }
                if (updated.path.isNotBlank() && !uriOrFileExists(updated.path) && hasOnlineTracks) {
                    val onlinePath = buildOnlineAlbumPath(updated)
                    if (!onlinePath.isNullOrBlank()) {
                        updated = updated.copy(path = onlinePath)
                    }
                }

                if (updated.localPath.isNullOrBlank() && updated.downloadPath.isNullOrBlank() && updated.path.isNotBlank()) {
                    val stillMissing = !uriOrFileExists(updated.path)
                    if (stillMissing) {
                        if (!hasOnlineTracks) {
                            trackDao.deleteSubtitlesForAlbum(entity.id)
                            trackDao.deleteTracksForAlbum(entity.id)
                            albumDao.deleteAlbum(entity)
                            return@forEach
                        }
                        val onlinePath = buildOnlineAlbumPath(updated)
                        if (!onlinePath.isNullOrBlank()) {
                            updated = updated.copy(path = onlinePath)
                        }
                    }
                }

                if (updated != entity) {
                    albumDao.updateAlbum(updated)
                    upsertAlbumFtsIndex(updated.id, updated)
                }
            }
        }
    }

    private suspend fun scanSingleAlbumFromDocumentUri(albumId: Long, albumUriString: String) {
        val albumUri = runCatching { Uri.parse(albumUriString) }.getOrNull() ?: return
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(albumUri) }.getOrNull() ?: return
        val treeUri = DocumentsContract.buildTreeDocumentUri(albumUri.authority, treeDocId)
        val albumDocId = runCatching { DocumentsContract.getDocumentId(albumUri) }.getOrNull() ?: return

        val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val subtitleExtensions = setOf("lrc", "srt", "vtt")

        val albumChildren = queryChildren(treeUri, albumDocId)
        val coverPath = pickCoverNode(albumChildren, treeUri, albumDocId)

        val all = walkTree(treeUri, albumDocId).filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR }
        val audioFiles = all.filter { audioExtensions.contains(it.displayName.substringAfterLast('.', "").lowercase()) }.sortedBy { it.documentId }
        val subtitleNodes = all.filter { subtitleExtensions.contains(it.displayName.substringAfterLast('.', "").lowercase()) }
        val subtitleNodeByRelativePath = subtitleNodes.associateBy { it.relativePath }
        val subtitleIndex = buildSubtitleCandidateIndex(
            subtitles = subtitleNodes.map { it.relativePath },
            isSubtitle = { name -> subtitleExtensions.contains(name.substringAfterLast('.', "").lowercase()) }
        )

        data class TrackSpec(
            val title: String,
            val path: String,
            val group: String
        )

        val trackSpecs = ArrayList<TrackSpec>(audioFiles.size)
        val audioRelativeBaseByPath = LinkedHashMap<String, String>(audioFiles.size)
        audioFiles.forEach { audio ->
            maybeUpdateBulkCurrentFile(audio.displayName)
            val audioUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, audio.documentId)
            val trackTitle = audio.displayName.substringBeforeLast('.').ifBlank { "track" }
            val group = if (audio.relativePath.contains("/")) audio.relativePath.substringBeforeLast('/').substringAfterLast('/') else ""
            val relativeBase = audio.relativePath.substringBeforeLast('.')
            trackSpecs.add(
                TrackSpec(
                    title = trackTitle,
                    path = audioUri.toString(),
                    group = group
                )
            )
            audioRelativeBaseByPath[audioUri.toString()] = relativeBase
        }

        val subtitlesByAudioPath: Map<String, List<com.asmr.player.util.SubtitleEntry>> = trackSpecs.associate { spec ->
            val key = audioRelativeBaseByPath[spec.path].orEmpty()
            val matched = if (key.isBlank()) emptyList() else subtitleIndex.match(key)
            val parsed = matched.mapNotNull { cand ->
                val node = subtitleNodeByRelativePath[cand.fileName] ?: return@mapNotNull null
                val entries = readSubtitleFromUri(treeUri, node.documentId, node.displayName)
                ParsedSubtitleCandidate(fileName = cand.fileName, language = cand.language, entries = entries)
            }
            spec.path to mergeSubtitleCandidates(parsed)
        }

        val cacheLeaves = all.mapNotNull { node ->
            val type = cacheFileTypeForName(node.displayName)
            if (type == CacheTreeFileType.Other || type == CacheTreeFileType.Subtitle) return@mapNotNull null
            val rawRel = node.relativePath.replace('\\', '/').trim().trimStart('/')
            if (rawRel.isBlank()) return@mapNotNull null
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, node.documentId).toString()
            CacheLeafEntry(relativePath = rawRel, absolutePath = docUri, fileType = type)
        }
        val treePrefix = treeUri.toString().trimEnd('/') + "/document/"
        var persistedPaths: List<String> = emptyList()

        database.withTransaction {
            val entity = albumDao.getAlbumById(albumId) ?: return@withTransaction
            if (coverPath.isNotBlank()) {
                albumDao.updateAlbum(entity.copy(coverPath = coverPath))
            }

            persistedPaths = listOfNotNull(entity.path, entity.localPath, entity.downloadPath)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val allExistingTracks = trackDao.getTracksForAlbumOnce(albumId)
            val existingLocalKeys = LinkedHashSet<String>()
            val existingLocalKeysNoGroup = LinkedHashSet<String>()
            allExistingTracks
                .filter { it.path.isNotBlank() && !it.path.startsWith(treePrefix) && !it.path.trim().startsWith("http", ignoreCase = true) }
                .forEach { t ->
                    existingLocalKeys.add(TrackKeyNormalizer.buildKey(t.title, t.group, null))
                    existingLocalKeysNoGroup.add(TrackKeyNormalizer.buildKey(t.title, "", null))
                }

            val toDelete = allExistingTracks.filter { it.path.startsWith(treePrefix) }.map { it.id }
            if (toDelete.isNotEmpty()) {
                trackDao.deleteSubtitlesForTracks(toDelete)
                trackDao.deleteTracksByIds(toDelete)
            }

            val filteredTrackSpecs = trackSpecs.filter { spec ->
                val key = TrackKeyNormalizer.buildKey(spec.title, spec.group, null)
                val keyNoGroup = TrackKeyNormalizer.buildKey(spec.title, "", null)
                !(existingLocalKeys.contains(key) || existingLocalKeysNoGroup.contains(keyNoGroup))
            }

            val tracksToInsert = filteredTrackSpecs.map { spec ->
                TrackEntity(
                    albumId = albumId,
                    title = spec.title,
                    path = spec.path,
                    duration = 0.0,
                    group = spec.group
                )
            }
            if (tracksToInsert.isNotEmpty()) {
                val insertedTrackIds = trackDao.insertTracks(tracksToInsert)
                val subtitlesToInsert = ArrayList<SubtitleEntity>()
                insertedTrackIds.zip(filteredTrackSpecs).forEach { (trackId, spec) ->
                    val entries = subtitlesByAudioPath[spec.path].orEmpty()
                    entries.forEach { e ->
                        subtitlesToInsert.add(
                            SubtitleEntity(
                                trackId = trackId,
                                startMs = e.startMs,
                                endMs = e.endMs,
                                text = e.text
                            )
                        )
                    }
                }
                if (subtitlesToInsert.isNotEmpty()) {
                    trackDao.insertSubtitles(subtitlesToInsert)
                }
            }
        }
        if (persistedPaths.isNotEmpty() && cacheLeaves.isNotEmpty()) {
            upsertLocalTreeCache(
                albumId = albumId,
                albumPaths = persistedPaths,
                leaves = cacheLeaves.distinctBy { it.relativePath }
            )
        }
        enqueueAlbumCoverThumbWork(albumId)
        enqueueTrackDurationWork(albumId)
    }

    private suspend fun resolveAndMergeAlbumForRj(
        rj: String,
        fallbackPath: String,
        fallbackTitle: String,
        localPath: String?,
        downloadPath: String?
    ): AlbumEntity? {
        if (rj.isNotBlank()) {
            val matches = albumDao.getAlbumsByWorkIdOnce(rj)
            if (matches.isNotEmpty()) {
                val canonical = matches.firstOrNull { !it.localPath.isNullOrBlank() } ?: matches.first()
                matches.filter { it.id != canonical.id }.forEach { other ->
                    trackDao.moveTracksToAlbum(other.id, canonical.id)
                    albumDao.deleteAlbum(other)
                }
                val merged = canonical.copy(
                    title = canonical.title.ifBlank { fallbackTitle },
                    path = canonical.path.ifBlank { fallbackPath },
                    localPath = canonical.localPath ?: localPath,
                    downloadPath = canonical.downloadPath ?: downloadPath,
                    workId = canonical.workId.ifBlank { rj },
                    rjCode = canonical.rjCode.ifBlank { rj }
                )
                albumDao.updateAlbum(merged)
                upsertAlbumFtsIndex(merged.id, merged)
                return merged
            }
        }
        val byPath = albumDao.getAlbumByPathOnce(fallbackPath)
        if (byPath != null) return byPath
        if (fallbackTitle.isNotBlank() && fallbackTitle != "album") {
            return albumDao.getAllAlbumsOnce().firstOrNull { it.title == fallbackTitle }
        }
        return null
    }

    private data class DocNode(
        val documentId: String,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long = 0L,
        val relativePath: String = ""
    )

    private fun queryChildren(treeUri: Uri, parentDocumentId: String, parentRelativePath: String = ""): List<DocNode> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val result = mutableListOf<DocNode>()
        val resolver = context.contentResolver
        runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex).orEmpty()
                    val mime = cursor.getString(mimeIndex).orEmpty()
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                    val relPath = if (parentRelativePath.isEmpty()) name else "$parentRelativePath/$name"
                    result.add(DocNode(documentId = id, displayName = name, mimeType = mime, sizeBytes = size, relativePath = relPath))
                }
            }
        }
        return result
    }

    private fun documentExists(treeUri: Uri, documentId: String): Boolean {
        val resolver = context.contentResolver
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        return runCatching {
            resolver.query(docUri, projection, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        }.getOrDefault(false)
    }

    private fun walkTree(treeUri: Uri, rootDocumentId: String): List<DocNode> {
        val result = mutableListOf<DocNode>()
        val queue = ArrayDeque<DocNode>()
        queryChildren(treeUri, rootDocumentId).forEach { queue.add(it) }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            result.add(node)
            if (node.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                queryChildren(treeUri, node.documentId, node.relativePath).forEach { queue.add(it) }
            }
        }
        return result
    }

    private fun readSubtitleFromUri(treeUri: Uri, documentId: String, displayName: String): List<com.asmr.player.util.SubtitleEntry> {
        val resolver = context.contentResolver
        val subUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val ext = displayName.substringAfterLast('.', "lrc").lowercase()
        val tempFile = File(context.cacheDir, "sub_${System.currentTimeMillis()}.$ext")
        return try {
            runCatching {
                resolver.openInputStream(subUri)?.use { input ->
                    tempFile.outputStream().use { out -> input.copyTo(out) }
                }
            }
            SubtitleParser.parse(tempFile.absolutePath)
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private data class SubtitleCandidate(val fileName: String, val language: String)
    private class SubtitleCandidateIndex(private val byBase: Map<String, List<SubtitleCandidate>>) {
        fun match(baseName: String): List<SubtitleCandidate> = byBase[baseName.trim().lowercase()].orEmpty()
    }
    private fun buildSubtitleCandidateIndex(subtitles: List<String>, isSubtitle: (String) -> Boolean): SubtitleCandidateIndex {
        val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val out = linkedMapOf<String, MutableList<SubtitleCandidate>>()
        subtitles.forEach { fileName ->
            val name = fileName.trim()
            if (name.isBlank() || !isSubtitle(name)) return@forEach

            val extRemovedBase = name.substringBeforeLast('.').trim()
            if (extRemovedBase.isBlank()) return@forEach

            val parts = extRemovedBase.split('.').map { it.trim() }.filter { it.isNotBlank() }
            val lastPart = parts.lastOrNull().orEmpty().lowercase()
            val langPart = lastPart.takeIf { p ->
                p.length in 2..4 && p.all { it in 'a'..'z' } && !audioExts.contains(p)
            }
            val lang = langPart ?: "default"

            val keys = linkedSetOf<String>()
            fun addKey(key: String) {
                val k = key.trim()
                if (k.isNotBlank()) keys.add(k)
            }
            addKey(extRemovedBase)

            if (langPart != null && extRemovedBase.contains('.')) {
                val withoutLang = extRemovedBase.substringBeforeLast('.').trim()
                if (withoutLang.isNotBlank()) {
                    addKey(withoutLang)
                    val audioPart = withoutLang.substringAfterLast('.', "").lowercase()
                    if (audioExts.contains(audioPart) && withoutLang.contains('.')) {
                        addKey(withoutLang.substringBeforeLast('.').trim())
                    }
                }
            }

            val audioPart = extRemovedBase.substringAfterLast('.', "").lowercase()
            if (audioExts.contains(audioPart) && extRemovedBase.contains('.')) {
                addKey(extRemovedBase.substringBeforeLast('.').trim())
            }

            val candidate = SubtitleCandidate(fileName = name, language = lang)
            keys.forEach { k ->
                out.getOrPut(k.lowercase()) { mutableListOf() }.add(candidate)
            }
        }
        return SubtitleCandidateIndex(out)
    }

    private data class ParsedSubtitleCandidate(
        val fileName: String,
        val language: String,
        val entries: List<com.asmr.player.util.SubtitleEntry>
    )

    private fun mergeSubtitleCandidates(parsed: List<ParsedSubtitleCandidate>): List<com.asmr.player.util.SubtitleEntry> {
        val nonEmpty = parsed.filter { it.entries.isNotEmpty() }
        if (nonEmpty.isEmpty()) return emptyList()
        if (nonEmpty.size == 1) return nonEmpty[0].entries

        val langPriority = listOf("default", "zh", "cn", "chs", "ja", "jp", "jpn", "en")
        fun langScore(lang: String): Int {
            val l = lang.trim().lowercase()
            val idx = langPriority.indexOf(l)
            return if (idx >= 0) idx else Int.MAX_VALUE
        }

        val sorted = nonEmpty.sortedWith(
            compareBy<ParsedSubtitleCandidate> { langScore(it.language) }
                .thenBy { it.fileName.lowercase() }
        )
        return sorted.first().entries
    }
}
