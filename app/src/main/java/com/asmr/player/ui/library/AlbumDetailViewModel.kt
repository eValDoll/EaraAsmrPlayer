package com.asmr.player.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.asmr.player.BuildConfig
import com.asmr.player.cache.AppCacheManager
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.dao.AlbumDao
import com.asmr.player.data.local.db.dao.TagWithCount
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.entities.AlbumTagEntity
import com.asmr.player.data.local.db.entities.RemoteSubtitleSourceEntity
import com.asmr.player.data.local.db.entities.OnlineSavedResourceEntity
import com.asmr.player.data.local.db.entities.TagEntity
import com.asmr.player.data.local.db.entities.TagSource
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.db.entities.TrackTagEntity
import com.asmr.player.data.local.db.entities.titleForDisplay
import com.asmr.player.data.lyrics.LyricsLoader
import com.asmr.player.data.lyrics.deriveLyricsRelativePathNoExt
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS
import com.asmr.player.data.remote.api.AsmrOneAvailabilityApi
import com.asmr.player.data.remote.api.AsmrOneEndpoint
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.auth.buildDlsiteCookieHeader
import com.asmr.player.data.remote.crawler.AsmrOneCrawler
import com.asmr.player.data.remote.crawler.AsmrOneTracksResult
import com.asmr.player.data.remote.crawler.asmrOneWorkMatchesRj
import com.asmr.player.data.remote.dlsite.DLSITE_PLAY_PREVIEW_CACHE_VERSION
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncCandidate
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncResolveResult
import com.asmr.player.data.remote.dlsite.DlsiteLanguageEdition
import com.asmr.player.data.remote.dlsite.DlsitePlayLoadStatus
import com.asmr.player.data.remote.dlsite.DlsitePlayTreeResult
import com.asmr.player.data.remote.dlsite.DlsitePlayWorkClient
import com.asmr.player.data.remote.dlsite.DlsiteProductInfoClient
import com.asmr.player.data.remote.dlsite.descrambleDlsitePlayBitmap
import com.asmr.player.data.remote.dlsite.parseDlsitePlayImageSeed
import com.asmr.player.data.remote.dlsite.resolveCloudSyncWorkId
import com.asmr.player.data.remote.dlsite.resolveDlsiteCloudSync
import com.asmr.player.data.remote.dlsite.resolveSelectedDlsiteCloudSync
import com.asmr.player.data.remote.download.DownloadManager
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.listentogether.ListenTogetherRepository
import com.asmr.player.ui.common.queryTrackFileSize
import com.asmr.player.ui.nav.AlbumCoverHint
import com.asmr.player.ui.nav.AlbumCoverHintStore
import com.asmr.player.ui.nav.albumFromCoverHint
import com.asmr.player.util.DlsiteWorkNo
import com.asmr.player.util.MessageManager
import com.asmr.player.util.OnlineLyricsStore
import com.asmr.player.util.RemoteSubtitleSource
import com.asmr.player.util.SubtitleMatchSupport
import com.asmr.player.util.SyncCoordinator
import com.asmr.player.util.TagNormalizer
import com.asmr.player.util.TrackKeyNormalizer
import com.asmr.player.util.isOnlineTrackPath
import com.asmr.player.work.AlbumCoverThumbWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val LISTEN_TOGETHER_RJ_SUMMARY_POLL_INTERVAL_MS = 60_000L

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val database: AppDatabase,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val asmrOneCrawler: AsmrOneCrawler,
    private val asmrOneAvailabilityApi: AsmrOneAvailabilityApi,
    private val dlsiteScraper: DLSiteScraper,
    private val dlsiteProductInfoClient: DlsiteProductInfoClient,
    private val dlsitePlayWorkClient: DlsitePlayWorkClient,
    private val downloadManager: DownloadManager,
    private val lyricsLoader: LyricsLoader,
    private val syncCoordinator: SyncCoordinator,
    private val listenTogetherRepository: ListenTogetherRepository,
    private val appCacheManager: AppCacheManager,
    @Named("image") private val imageOkHttpClient: OkHttpClient,
    val messageManager: MessageManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private data class AlbumAudioAggregate(
        val trackCount: Int,
        val totalDuration: Double,
        val totalSizeBytes: Long,
    )

    private suspend fun computeAlbumAudioAggregate(
        tracks: List<TrackEntity>,
    ): AlbumAudioAggregate {
        val totalSizeBytes = withContext(Dispatchers.IO) {
            tracks.sumOf { track -> queryTrackFileSize(context, track.path) ?: 0L }
        }
        return AlbumAudioAggregate(
            trackCount = tracks.size,
            totalDuration = tracks.sumOf { it.duration },
            totalSizeBytes = totalSizeBytes,
        )
    }

    private suspend fun refreshAlbumAudioAggregate(albumId: Long) {
        if (albumId <= 0L) return
        val entity = albumDao.getAlbumById(albumId) ?: return
        val tracks = trackDao.getTracksForAlbumOnce(albumId)
        val aggregate = computeAlbumAudioAggregate(tracks)
        albumDao.updateAlbum(
            entity.copy(
                audioTrackCount = aggregate.trackCount,
                audioTotalDuration = aggregate.totalDuration,
                audioTotalSizeBytes = aggregate.totalSizeBytes,
            )
        )
    }

    private val _uiState = MutableStateFlow<AlbumDetailUiState>(
        createRouteInitialUiState(savedStateHandle)
    )
    val uiState = _uiState.asStateFlow()
    private val _cloudSyncSelectionDialogState = MutableStateFlow<CloudSyncSelectionDialogState?>(null)
    internal val cloudSyncSelectionDialogState: StateFlow<CloudSyncSelectionDialogState?> = _cloudSyncSelectionDialogState.asStateFlow()
    private var pendingCloudSyncSelection: CompletableDeferred<String?>? = null

    val availableTags: StateFlow<List<TagWithCount>> = database.tagDao()
        .getTagsWithCounts(TagSource.USER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var dlsiteLoadToken: Int = 0
    private var dlsiteTrialLoadToken: Int = 0
    private var asmrOneLoadToken: Int = 0
    private var lastAlbumKey: String? = null
    private var albumLoadJob: Job? = null
    private var dlsiteLoadJob: Job? = null
    private var dlsiteRecommendationEnrichJob: Job? = null
    private var dlsiteTrialLoadJob: Job? = null
    private var asmrOneLoadJob: Job? = null
    private var dlsitePlayLoadJob: Job? = null
    private var localTracksObserveJob: Job? = null
    private val asmrOneAttemptedRj = linkedSetOf<String>()
    private val dlsitePlayAttemptedRj = linkedSetOf<String>()
    private val asmrOneCollectedCache = linkedMapOf<String, Pair<Long, Boolean?>>()
    private val asmrOneResolvedCache = linkedMapOf<String, Pair<Long, Pair<String, Int?>?>>()
    private val asmrOneResolutionInFlight = mutableMapOf<String, Deferred<Pair<String, Int?>?>>()
    private val asmrOneTracksCache = linkedMapOf<String, Pair<Long, AsmrOneTracksResult>>()

    private val treeExpandedByKey = linkedMapOf<String, List<String>>()
    private val treeInitializedKeys = linkedSetOf<String>()
    private val listScrollByKey = linkedMapOf<String, Pair<Int, Int>>()
    private val treeCurrentPathByKey = linkedMapOf<String, String>()
    private val remoteFileSizeCache = linkedMapOf<String, Long?>()
    private var listenTogetherRjSummaryJob: Job? = null
    private val listenTogetherRjSummaryInFlight = AtomicBoolean(false)
    private val listenTogetherRjSummaryPollingEnabled = MutableStateFlow(false)
    private val preferredTreePathPrefs by lazy {
        context.getSharedPreferences("album_detail_tree_prefs", Context.MODE_PRIVATE)
    }

    init {
        viewModelScope.launch {
            combine(
                uiState.map {
                    (it as? AlbumDetailUiState.Success)?.model?.listenTogetherSummaryRj().orEmpty()
                },
                listenTogetherRjSummaryPollingEnabled,
            ) { rj, enabled ->
                rj.takeIf { enabled }.orEmpty()
            }
                .distinctUntilChanged()
                .collect { rj ->
                    listenTogetherRjSummaryJob?.cancel()
                    listenTogetherRjSummaryJob = if (rj.isBlank()) {
                        null
                    } else {
                        viewModelScope.launch {
                            while (true) {
                                refreshListenTogetherRjSummary(rj)
                                delay(LISTEN_TOGETHER_RJ_SUMMARY_POLL_INTERVAL_MS)
                            }
                        }
                    }
                }
        }
    }

    fun setListenTogetherRjSummaryPollingEnabled(enabled: Boolean) {
        listenTogetherRjSummaryPollingEnabled.value = enabled
        if (!enabled) {
            listenTogetherRjSummaryJob?.cancel()
            listenTogetherRjSummaryJob = null
        }
    }

    private suspend fun refreshListenTogetherRjSummary(rjCode: String) {
        val normalizedRj = rjCode.trim().uppercase()
        if (normalizedRj.isBlank()) return
        if (!listenTogetherRjSummaryInFlight.compareAndSet(false, true)) return
        try {
            val summary = runCatching {
                listenTogetherRepository.getRjSummary(normalizedRj)
            }.getOrNull() ?: return
            val listenerCount = summary.listenerCount.coerceAtLeast(0)
            val current = _uiState.value as? AlbumDetailUiState.Success ?: return
            if (!current.model.listenTogetherSummaryRj().equals(normalizedRj, ignoreCase = true)) return
            if (current.model.listenTogetherRjListenerCount == listenerCount) return
            _uiState.value = AlbumDetailUiState.Success(
                model = current.model.copy(listenTogetherRjListenerCount = listenerCount)
            )
        } finally {
            listenTogetherRjSummaryInFlight.set(false)
        }
    }

    private suspend fun isAsmrOneCollected(rj: String, timeoutMs: Long = 1_200L): Boolean? {
        val key = rj.trim().uppercase()
        if (key.isBlank()) return null
        val now = SystemClock.elapsedRealtime()
        val cached = asmrOneCollectedCache[key]
        if (cached != null && (now - cached.first) <= 60_000L) return cached.second
        val result = resolveAsmrOneWork(key, timeoutMs = timeoutMs) != null
        asmrOneCollectedCache[key] = now to result
        if (asmrOneCollectedCache.size > 300) {
            val firstKey = asmrOneCollectedCache.entries.firstOrNull()?.key
            if (firstKey != null) asmrOneCollectedCache.remove(firstKey)
        }
        return result
    }

    private fun asmrOneTracksCacheKey(workId: String): String {
        return workId.trim()
    }

    private suspend fun resolveAsmrOneWork(
        workNo: String,
        timeoutMs: Long = 12_000L
    ): Pair<String, Int?>? {
        val key = workNo.trim().uppercase()
        if (key.isBlank()) return null
        if (asmrOneCrawler.selectedEndpoint() == AsmrOneEndpoint.BACKUP) return null
        val now = SystemClock.elapsedRealtime()
        val cached = asmrOneResolvedCache[key]
        if (cached != null) {
            val ttlMs = if (cached.second == null) 5_000L else 10 * 60_000L
            if ((now - cached.first) <= ttlMs) return cached.second
        }

        val request = asmrOneResolutionInFlight[key] ?: viewModelScope.async {
            val result = runCatching { asmrOneCrawler.searchWithTrace(key) }.getOrNull()
            val found = result
                ?.response
                ?.works
                ?.firstOrNull { work -> asmrOneWorkMatchesRj(work, key) }
            val workId = found?.id?.toString()?.trim().orEmpty()
            val resolved = workId
                .takeIf { it.isNotBlank() }
                ?.let { it to result?.trace?.site }
            asmrOneResolvedCache[key] = SystemClock.elapsedRealtime() to resolved
            if (asmrOneResolvedCache.size > 500) {
                val firstKey = asmrOneResolvedCache.entries.firstOrNull()?.key
                if (firstKey != null) asmrOneResolvedCache.remove(firstKey)
            }
            resolved
        }.also { deferred ->
            asmrOneResolutionInFlight[key] = deferred
            deferred.invokeOnCompletion {
                asmrOneResolutionInFlight.remove(key, deferred)
            }
        }
        return withTimeoutOrNull(timeoutMs) { request.await() }
    }

    private suspend fun getAsmrOneTracksCached(workId: String): AsmrOneTracksResult {
        val normalizedId = workId.trim()
        if (normalizedId.isBlank()) return AsmrOneTracksResult(emptyList(), null)
        val cacheKey = asmrOneTracksCacheKey(normalizedId)
        val now = SystemClock.elapsedRealtime()
        val cached = asmrOneTracksCache[cacheKey]
        if (cached != null && (now - cached.first) <= 10 * 60_000L) return cached.second
        val result = runCatching {
            asmrOneCrawler.getTracksWithTrace(normalizedId)
        }.getOrDefault(AsmrOneTracksResult(emptyList(), null))
        if (result.tree.isNotEmpty()) {
            asmrOneTracksCache[cacheKey] = now to result
            if (asmrOneTracksCache.size > 200) {
                val firstKey = asmrOneTracksCache.entries.firstOrNull()?.key
                if (firstKey != null) asmrOneTracksCache.remove(firstKey)
            }
        }
        return result
    }

    private suspend fun fetchBackupAsmrOneTracksByRj(rj: String): Pair<String, List<AsmrOneTrackNodeResponse>>? {
        val normalizedRj = rj.trim().uppercase()
        if (!RJ_CODE_REGEX.matches(normalizedRj)) return null
        val result = runCatching { asmrOneAvailabilityApi.getTrackTreeByRj(normalizedRj) }.getOrNull()
            ?: return null
        val tree = result.trackTree.orEmpty()
        if (tree.isEmpty()) return null
        val workId = result.workId.takeIf { it > 0 }?.toString().orEmpty()
        return workId to tree
    }

    fun getTreeExpanded(stateKey: String): List<String> {
        return treeExpandedByKey[stateKey].orEmpty()
    }

    fun isTreeInitialized(stateKey: String): Boolean {
        return treeInitializedKeys.contains(stateKey)
    }

    fun persistTreeState(stateKey: String, expanded: List<String>) {
        treeExpandedByKey[stateKey] = expanded.distinct()
        treeInitializedKeys.add(stateKey)
    }

    fun clearTreeState(stateKey: String) {
        treeExpandedByKey.remove(stateKey)
        treeInitializedKeys.remove(stateKey)
        treeCurrentPathByKey.remove(stateKey)
    }

    fun getTreeCurrentPath(stateKey: String): String {
        return treeCurrentPathByKey[stateKey].orEmpty()
    }

    fun persistTreeCurrentPath(stateKey: String, currentPath: String) {
        if (stateKey.isBlank()) return
        treeCurrentPathByKey[stateKey] = currentPath.trim().trim('/')
    }

    fun getPreferredTreeCurrentPath(stateKey: String): String {
        if (stateKey.isBlank()) return ""
        return preferredTreePathPrefs.getString("preferred_path:$stateKey", "").orEmpty().trim().trim('/')
    }

    fun persistPreferredTreeCurrentPath(stateKey: String, currentPath: String) {
        if (stateKey.isBlank()) return
        val normalized = currentPath.trim().trim('/')
        preferredTreePathPrefs.edit().putString("preferred_path:$stateKey", normalized).apply()
    }

    fun clearPreferredTreeCurrentPath(stateKey: String) {
        if (stateKey.isBlank()) return
        preferredTreePathPrefs.edit().remove("preferred_path:$stateKey").apply()
    }

    suspend fun loadOnlineTextPreview(url: String): String? {
        val u = url.trim()
        if (u.isBlank()) return null
        return lyricsLoader.fetchTextForPreview(u)
    }

    fun cancelActiveLoads() {
        setListenTogetherRjSummaryPollingEnabled(false)
        // 该方法只在详情页退出/清理时调用，界面已经不可见；不要为 loading 标志
        // 再发布一份新 UiState，与弹栈处置争用同一帧。
        cancelPendingOnlineJobs(resetLoadingState = false)
        albumLoadJob?.cancel()
        albumLoadJob = null
        localTracksObserveJob?.cancel()
        localTracksObserveJob = null
        lastAlbumKey = null
    }

    fun cancelOnlineLoadsForExit() {
        cancelPendingOnlineJobs(resetLoadingState = false)
    }

    private fun cancelPendingOnlineJobs(resetLoadingState: Boolean) {
        dlsiteLoadToken++
        dlsiteTrialLoadToken++
        asmrOneLoadToken++

        dlsiteLoadJob?.cancel()
        dlsiteLoadJob = null
        dlsiteRecommendationEnrichJob?.cancel()
        dlsiteRecommendationEnrichJob = null
        dlsiteTrialLoadJob?.cancel()
        dlsiteTrialLoadJob = null
        asmrOneLoadJob?.cancel()
        asmrOneLoadJob = null
        asmrOneResolutionInFlight.values.forEach { it.cancel() }
        asmrOneResolutionInFlight.clear()
        dlsitePlayLoadJob?.cancel()
        dlsitePlayLoadJob = null

        asmrOneAttemptedRj.clear()
        dlsitePlayAttemptedRj.clear()

        if (!resetLoadingState) return
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        _uiState.value = AlbumDetailUiState.Success(
            model = current.model.copy(
                isLoadingDlsite = false,
                isLoadingDlsiteTrial = false,
                isLoadingAsmrOne = false,
                isLoadingDlsitePlay = false
            )
        )
    }

    suspend fun prepareDlsitePlayImagePreview(
        url: String,
        optimizedName: String?,
        crypt: Boolean,
        width: Int?,
        height: Int?
    ): String? = withContext(Dispatchers.IO) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return@withContext null
        if (!crypt) return@withContext normalizedUrl
        val imageWidth = width ?: return@withContext null
        val imageHeight = height ?: return@withContext null
        if (imageWidth <= 0 || imageHeight <= 0) return@withContext null
        val name = optimizedName?.trim().orEmpty().ifBlank {
            normalizedUrl.substringBefore('?').substringAfterLast('/')
        }
        val seed = parseDlsitePlayImageSeed(name) ?: return@withContext null

        val previewDir = File(context.cacheDir, AppCacheManager.DLSITE_PREVIEW_CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
        val previewKey = listOf(
            DLSITE_PLAY_PREVIEW_CACHE_VERSION.toString(),
            normalizedUrl,
            name,
            imageWidth.toString(),
            imageHeight.toString(),
            seed.toString()
        ).joinToString("|")
        val previewFile = File(previewDir, "${previewKey.hashCode()}_descrambled.png")
        if (previewFile.exists() && previewFile.length() > 0L) return@withContext previewFile.absolutePath

        val requestBuilder = Request.Builder()
            .url(normalizedUrl)
            .header("Accept", "image/*,*/*;q=0.8")
            .header("Referer", "https://play.dlsite.com/")
            .header("User-Agent", NetworkHeaders.USER_AGENT)
            .header("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
            .get()
        val cookie = buildDlsiteCookieHeader(DlsiteAuthStore(context).getPlayCookie())
        if (cookie.isNotBlank()) requestBuilder.header("Cookie", cookie)

        val bytes = runCatching {
            imageOkHttpClient.newCall(requestBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.bytes()
            }
        }.getOrNull() ?: return@withContext null
        val scrambled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        val descrambled = descrambleDlsitePlayBitmap(scrambled, seed, imageWidth, imageHeight)
        FileOutputStream(previewFile).use { out ->
            descrambled.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (descrambled !== scrambled && !descrambled.isRecycled) descrambled.recycle()
        if (!scrambled.isRecycled) scrambled.recycle()
        appCacheManager.onPreviewCacheChanged(previewFile)
        previewFile.absolutePath
    }

    fun getListScrollPosition(stateKey: String): Pair<Int, Int> {
        return listScrollByKey[stateKey] ?: (0 to 0)
    }

    fun persistListScrollPosition(stateKey: String, index: Int, offset: Int) {
        if (stateKey.isBlank()) return
        listScrollByKey[stateKey] = (index.coerceAtLeast(0) to offset.coerceAtLeast(0))
    }

    suspend fun loadRemoteFileSize(url: String): Long? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null
        remoteFileSizeCache[trimmed]?.let { return it }

        val resolved = withContext(Dispatchers.IO) {
            requestRemoteFileSize(trimmed, imageOkHttpClient)
        }
        remoteFileSizeCache[trimmed] = resolved
        while (remoteFileSizeCache.size > 512) {
            val firstKey = remoteFileSizeCache.entries.firstOrNull()?.key ?: break
            remoteFileSizeCache.remove(firstKey)
        }
        return resolved
    }

    fun confirmCloudSyncSelection(workno: String) {
        val deferred = pendingCloudSyncSelection ?: return
        if (deferred.isActive) {
            deferred.complete(workno.trim().uppercase().ifBlank { null })
        }
        if (pendingCloudSyncSelection === deferred) {
            pendingCloudSyncSelection = null
            _cloudSyncSelectionDialogState.value = null
        }
    }

    fun cancelCloudSyncSelection() {
        val deferred = pendingCloudSyncSelection ?: return
        if (deferred.isActive) {
            deferred.complete(null)
        }
        if (pendingCloudSyncSelection === deferred) {
            pendingCloudSyncSelection = null
            _cloudSyncSelectionDialogState.value = null
        }
    }

    private suspend fun awaitCloudSyncSelection(
        albumTitle: String,
        candidates: List<DlsiteCloudSyncCandidate>
    ): String? {
        cancelCloudSyncSelection()
        val normalizedCandidates = candidates
            .filter { it.workno.isNotBlank() }
            .distinctBy { it.workno.trim().uppercase() }
        if (normalizedCandidates.isEmpty()) return null
        val deferred = CompletableDeferred<String?>()
        pendingCloudSyncSelection = deferred
        _cloudSyncSelectionDialogState.value = CloudSyncSelectionDialogState(
            albumTitle = albumTitle,
            candidates = normalizedCandidates
        )
        return try {
            deferred.await()
        } finally {
            if (pendingCloudSyncSelection === deferred) {
                pendingCloudSyncSelection = null
                _cloudSyncSelectionDialogState.value = null
            }
        }
    }

    private suspend fun resolveManualCloudSync(
        entity: AlbumEntity,
        baseWorkno: String
    ): DlsiteCloudSyncResolveResult {
        return resolveDlsiteCloudSync(
            keyword = entity.title.trim(),
            baseWorkno = baseWorkno,
            search = { searchKeyword, locale ->
                dlsiteScraper.search(searchKeyword, page = 1, order = "trend", locale = locale).items
            },
            fetchLanguageEditions = { productId ->
                dlsiteProductInfoClient.fetchLanguageEditions(productId)
            },
            fetchDetails = { workno, locale ->
                dlsiteScraper.getDetails(workno, locale = locale)
            }
        )
    }

    private suspend fun resolveSelectedManualCloudSync(workno: String): DlsiteCloudSyncResolveResult {
        return resolveSelectedDlsiteCloudSync(
            workno = workno,
            fetchLanguageEditions = { productId ->
                dlsiteProductInfoClient.fetchLanguageEditions(productId)
            },
            fetchDetails = { selectedWorkno, locale ->
                dlsiteScraper.getDetails(selectedWorkno, locale = locale)
            }
        )
    }

    private suspend fun applyManualCloudSyncSuccess(
        entity: AlbumEntity,
        updatedWorkId: String,
        result: DlsiteCloudSyncResolveResult.Success
    ): String {
        val resolvedWorkno = result.workno
        val details = result.details
        val oldTitle = entity.title.trim()
        val newTitle = details.title.trim()
        val finalTitle = when {
            oldTitle.isNotBlank() && newTitle.isBlank() -> oldTitle
            oldTitle.isNotBlank() && newTitle.isNotBlank() && oldTitle.contains(newTitle) && oldTitle.length > newTitle.length -> oldTitle
            else -> newTitle.ifBlank { oldTitle }
        }
        val updated = entity.copy(
            title = finalTitle,
            circle = details.circle.ifBlank { entity.circle },
            cv = details.cv.ifBlank { entity.cv },
            tags = if (details.tags.isNotEmpty()) details.tags.joinToString(",") else entity.tags,
            coverUrl = details.coverUrl.ifBlank { entity.coverUrl },
            description = details.description.ifBlank { entity.description },
            workId = resolveCloudSyncWorkId(updatedWorkId, resolvedWorkno),
            rjCode = resolvedWorkno
        )
        withContext(Dispatchers.IO) {
            albumDao.updateAlbum(updated)
            upsertAlbumFtsIndex(updated.id, updated)
            upsertAlbumTagsFromCsv(updated.id, updated.tags, TagSource.AUTO)
            if (updated.coverPath.trim().isBlank() && updated.coverThumbPath.trim().isBlank()) {
                runCatching {
                    ensureAlbumCoverSaved(updated.id, updated.coverPath, updated.coverUrl)
                }
            }
        }
        return resolvedWorkno
    }

    suspend fun loadAlbumAndAwait(albumId: Long?, rjCode: String?, force: Boolean = false) {
        loadAlbum(albumId, rjCode, force)
        albumLoadJob?.join()
    }

    fun loadAlbum(albumId: Long?, rjCode: String?, force: Boolean = false) {
        val normalizedRj = rjCode?.trim().orEmpty().uppercase()
        val key = if (normalizedRj.isNotBlank()) "rj:$normalizedRj" else "id:${albumId ?: 0L}"
        val current = _uiState.value as? AlbumDetailUiState.Success
        if (!force && current != null && lastAlbumKey == key) return
        cancelPendingOnlineJobs(resetLoadingState = false)
        albumLoadJob?.cancel()
        lastAlbumKey = key
        val initialHint = AlbumCoverHintStore.peekHint(albumId, normalizedRj)
        val initialRj = normalizedRj.ifBlank { initialHint?.rjCode.orEmpty() }
        val initialHintAlbum = albumFromInitialHint(initialRj, initialHint)
        if (force || current == null) {
            _uiState.value = AlbumDetailUiState.Success(
                model = createInitialAlbumDetailModel(
                    rj = initialRj,
                    displayAlbum = initialHintAlbum,
                    dlsiteInfo = initialHintAlbum.takeIf { shouldPreserveHeaderAlbumMetadata(initialHint) },
                    preserveHeaderAlbumMetadata = shouldPreserveHeaderAlbumMetadata(initialHint)
                )
            )
        }
        albumLoadJob = viewModelScope.launch {
            try {
                val localAlbum = if (albumId != null && albumId > 0) {
                    loadLocalAlbumById(albumId)
                } else if (!rjCode.isNullOrBlank()) {
                    loadLocalAlbumByRj(rjCode)
                } else {
                    null
                }

                val rj = rjCode?.trim().orEmpty().ifBlank {
                    localAlbum?.rjCode?.trim().orEmpty().ifBlank { localAlbum?.workId?.trim().orEmpty() }
                }.uppercase()

                val hint = AlbumCoverHintStore.peekHint(albumId, rj) ?: initialHint
                val hintAlbum = albumFromInitialHint(rj, hint)
                val preserveHeaderAlbumMetadata = shouldPreserveHeaderAlbumMetadata(hint)
                val dlsiteInfo = hintAlbum.takeIf { preserveHeaderAlbumMetadata }
                // 种入列表点击时记录的封面与元信息：让 hero 与列表卡片使用相同图片 model，
                // 在网络解析完成前即可命中跨尺寸内存缓存，避免重复请求封面。
                val displayAlbum = if (hint != null) hintAlbum else localAlbum ?: hintAlbum
                val initialLoadedModel = createInitialAlbumDetailModel(
                    rj = rj,
                    displayAlbum = displayAlbum,
                    localAlbum = localAlbum,
                    dlsiteInfo = dlsiteInfo,
                    preserveHeaderAlbumMetadata = preserveHeaderAlbumMetadata
                )
                val currentModel = (_uiState.value as? AlbumDetailUiState.Success)?.model
                val loadedModel = initialLoadedModel.withPreservedListenTogetherListenerCount(currentModel)
                if (loadedModel != currentModel) {
                    _uiState.value = AlbumDetailUiState.Success(
                        model = loadedModel
                    )
                }
                localTracksObserveJob?.cancel()
                localTracksObserveJob = null
                val localId = localAlbum?.id ?: 0L
                if (localId > 0L) {
                    localTracksObserveJob = viewModelScope.launch {
                        trackDao.getTracksForAlbum(localId)
                            .map { entities -> entities.map { it.toDomain() } }
                            .flowOn(Dispatchers.Default)
                            .distinctUntilChanged()
                            .collect { tracks ->
                                val cur = _uiState.value as? AlbumDetailUiState.Success ?: return@collect
                                val curLocal = cur.model.localAlbum ?: return@collect
                                if (curLocal.id != localId) return@collect

                                val updatedLocal = curLocal.copy(tracks = tracks)
                                val updatedDisplay = if (cur.model.displayAlbum.id == localId) {
                                    cur.model.displayAlbum.copy(tracks = tracks)
                                } else {
                                    cur.model.displayAlbum
                                }
                                _uiState.value = AlbumDetailUiState.Success(
                                    model = cur.model.copy(
                                        localAlbum = updatedLocal,
                                        displayAlbum = updatedDisplay
                                    )
                                )
                            }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = AlbumDetailUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    fun setUserTagsForTrack(trackId: Long, tags: List<String>) {
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
                trackTagDao.deleteTrackTagsByTrackIdAndSource(trackId, TagSource.USER)
                if (pairs.isNotEmpty()) {
                    val tagEntities = pairs.map { (name, normalized) ->
                        TagEntity(name = name, nameNormalized = normalized)
                    }
                    tagDao.insertTags(tagEntities)
                    val persisted = tagDao.getTagsByNormalized(pairs.map { it.second })
                    val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })
                    val refs = pairs.mapNotNull { (_, normalized) ->
                        val tagId = idByNormalized[normalized] ?: return@mapNotNull null
                        TrackTagEntity(trackId = trackId, tagId = tagId, source = TagSource.USER)
                    }
                    if (refs.isNotEmpty()) trackTagDao.insertTrackTags(refs)
                }
            }
        }
    }

    fun manualSetRjAndSync(input: String) {
        val normalized = Regex("""RJ\d+""", RegexOption.IGNORE_CASE).find(input.trim())?.value?.uppercase().orEmpty()
        if (normalized.isBlank()) {
            messageManager.showError("请输入有效的作品编号")
            return
        }
        val current = _uiState.value as? AlbumDetailUiState.Success
        val local = current?.model?.localAlbum
        if (local == null || local.id <= 0L) {
            messageManager.showError("仅支持本地库专辑手动绑定作品编号")
            return
        }

        viewModelScope.launch {
            val token = syncCoordinator.tryBegin() ?: run {
                messageManager.showInfo("同步任务进行中，请等待完成或取消后再同步")
                return@launch
            }
            _uiState.value = AlbumDetailUiState.Loading
            try {
                val entity = withContext(Dispatchers.IO) { albumDao.getAlbumById(local.id) } ?: run {
                    messageManager.showError("专辑不存在")
                    loadAlbum(local.id, normalized, force = true)
                    return@launch
                }

                val updatedWorkId = resolveCloudSyncWorkId(entity.workId, normalized)
                withContext(Dispatchers.IO) {
                    albumDao.updateAlbum(entity.copy(workId = updatedWorkId, rjCode = normalized))
                }

                when (
                    val result = withContext(Dispatchers.IO) {
                        resolveManualCloudSync(entity, normalized)
                    }
                ) {
                    is DlsiteCloudSyncResolveResult.Success -> {
                        val resolvedWorkno = withContext(Dispatchers.IO) {
                            applyManualCloudSyncSuccess(entity, updatedWorkId, result)
                        }
                        messageManager.showSuccess("已绑定 $resolvedWorkno 并完成云同步")
                        loadAlbum(local.id, resolvedWorkno, force = true)
                    }

                    is DlsiteCloudSyncResolveResult.Ambiguous -> {
                        val selectedWorkno = awaitCloudSyncSelection(
                            albumTitle = local.title,
                            candidates = result.candidates
                        )
                        if (selectedWorkno == null) {
                            loadAlbum(local.id, normalized, force = true)
                            return@launch
                        }
                        when (val selectedResult = withContext(Dispatchers.IO) {
                            resolveSelectedManualCloudSync(selectedWorkno)
                        }) {
                            is DlsiteCloudSyncResolveResult.Success -> {
                                val resolvedWorkno = withContext(Dispatchers.IO) {
                                    applyManualCloudSyncSuccess(entity, updatedWorkId, selectedResult)
                                }
                                messageManager.showSuccess("已绑定 $resolvedWorkno 并完成云同步")
                                loadAlbum(local.id, resolvedWorkno, force = true)
                                return@launch
                            }

                            is DlsiteCloudSyncResolveResult.Ambiguous -> {
                                messageManager.showError("同步失败：搜索结果不唯一")
                                loadAlbum(local.id, normalized, force = true)
                                return@launch
                            }

                            DlsiteCloudSyncResolveResult.NotFound -> {
                                messageManager.showError("同步失败：未找到专辑信息")
                                loadAlbum(local.id, normalized, force = true)
                                return@launch
                            }
                        }
                    }

                    DlsiteCloudSyncResolveResult.NotFound -> {
                        messageManager.showError("同步失败：未找到专辑信息")
                        loadAlbum(local.id, normalized, force = true)
                    }
                }
            } catch (e: Exception) {
                messageManager.showError("同步失败，请稍后重试")
                loadAlbum(local.id, normalized, force = true)
            } finally {
                syncCoordinator.end(token)
            }
        }
    }

    fun setLocalCoverPath(pathOrUri: String) {
        val value = pathOrUri.trim()
        if (value.isBlank()) return
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val local = current.model.localAlbum ?: return
        if (local.id <= 0L) return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val entity = albumDao.getAlbumById(local.id) ?: return@withContext
                    albumDao.updateAlbum(entity.copy(coverPath = value, coverThumbPath = ""))
                    runCatching { database.localTreeCacheDao().deleteByAlbum(entity.id) }
                }
                updateCurrentCoverState(local.id, value, "")
                enqueueAlbumCoverThumbWork(local.id)
                messageManager.showSuccess("已设置封面")
            } catch (e: Exception) {
                messageManager.showError("设置封面失败，请检查后重试")
            }
        }
    }

    private fun updateCurrentCoverState(albumId: Long, coverPath: String, coverThumbPath: String) {
        val cur = _uiState.value as? AlbumDetailUiState.Success ?: return
        _uiState.value = AlbumDetailUiState.Success(
            model = cur.model.withUpdatedLocalCover(
                albumId = albumId,
                coverPath = coverPath,
                coverThumbPath = coverThumbPath
            )
        )
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

    private fun buildTagsToken(tagsCsv: String): String {
        return tagsCsv.split(",")
            .map { TagNormalizer.normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
    }

    private fun parseAlbumTags(tagsCsv: String): List<Pair<String, String>> {
        return tagsCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it to TagNormalizer.normalize(it) }
            .filter { it.second.isNotBlank() }
            .distinctBy { it.second }
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

    private suspend fun resolveInitialDlsiteLoadTarget(
        model: AlbumDetailModel
    ): ResolvedDlsiteLoadTarget {
        val clean = model.baseRjCode.trim().uppercase()
        if (clean.isBlank()) {
            return ResolvedDlsiteLoadTarget(
                editions = model.dlsiteEditions,
                selectedLang = model.dlsiteSelectedLang,
                workno = model.dlsiteWorkno.trim().uppercase()
            )
        }
        val editions = runCatching { dlsiteProductInfoClient.fetchLanguageEditions(clean) }
            .getOrDefault(emptyList())
        return resolveInitialDlsiteLoadTarget(
            entryRjCode = clean,
            editions = editions
        )
    }

    private fun dlsiteLocaleForLang(lang: String): String {
        return when (lang.trim().uppercase()) {
            "CHI_HANS" -> "zh_CN"
            "CHI_HANT" -> "zh_TW"
            else -> "ja_JP"
        }
    }

    private suspend fun enrichRecommendationsWithAsmrOne(recommendations: DlsiteRecommendations): DlsiteRecommendations {
        val candidates = (recommendations.circleWorks + recommendations.sameVoiceWorks + recommendations.alsoBoughtWorks)
            .asSequence()
            .map { it.rjCode.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_RECOMMENDATION_ASMR_ONE_ENRICH)
            .toList()
        if (candidates.isEmpty()) return recommendations

        val semaphore = Semaphore(RECOMMENDATION_ASMR_ONE_ENRICH_CONCURRENCY)
        val detailsByRj = coroutineScope {
            candidates.map { rj ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val asmr = runCatching { asmrOneCrawler.getDetails(rj) }.getOrNull()
                        rj to asmr
                    }
                }
            }.mapNotNull { deferred ->
                val (rj, details) = runCatching { deferred.await() }.getOrNull() ?: return@mapNotNull null
                details?.let { rj to it }
            }.toMap()
        }
        if (detailsByRj.isEmpty()) return recommendations

        fun enrich(list: List<DlsiteRecommendedWork>): List<DlsiteRecommendedWork> {
            return list.map { work ->
                val asmr = detailsByRj[work.rjCode.trim().uppercase()] ?: return@map work
                work.copy(
                    title = asmr.title.ifBlank { work.title },
                    coverUrl = asmr.mainCoverUrl.ifBlank { work.coverUrl }
                )
            }
        }
        return DlsiteRecommendations(
            circleWorks = enrich(recommendations.circleWorks),
            sameVoiceWorks = enrich(recommendations.sameVoiceWorks),
            alsoBoughtWorks = enrich(recommendations.alsoBoughtWorks)
        )
    }

    fun ensureDlsiteLoaded() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        if (current.model.hasLoadedInitialDlsiteContent) return
        if (current.model.isLoadingDlsite) return
        
        dlsiteLoadJob?.cancel()
        dlsiteRecommendationEnrichJob?.cancel()
        // 如果还没有拉取过多语言列表，先拉取一次
        val token = ++dlsiteLoadToken
        dlsiteLoadJob = viewModelScope.launch {
            val latestBefore = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
            _uiState.value = AlbumDetailUiState.Success(
                model = latestBefore.copy(
                    isLoadingDlsite = true,
                    isLoadingDlsiteTrial = false
                )
            )
            try {
                var loadModel = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                if (!loadModel.hasResolvedInitialDlsiteTarget) {
                    val resolvedTarget = resolveInitialDlsiteLoadTarget(loadModel)
                    val latestResolved = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                    if (token != dlsiteLoadToken) return@launch
                    val targetWorkno = resolvedTarget.workno.trim().uppercase()
                    val targetChanged = targetWorkno.isNotBlank() &&
                        !targetWorkno.equals(latestResolved.rjCode.trim().uppercase(), ignoreCase = true)
                    val keepAsmrOneContentDuringTargetSwitch = targetChanged &&
                        latestResolved.asmrOneTree.isNotEmpty()
                    if (targetChanged) {
                        asmrOneLoadToken++
                        asmrOneAttemptedRj.clear()
                    }
                    loadModel = latestResolved.copy(
                        rjCode = resolvedTarget.workno,
                        displayAlbum = mergeDetailHeaderAlbum(
                            currentDisplayAlbum = latestResolved.displayAlbum,
                            localAlbum = latestResolved.localAlbum,
                            fetchedDlsiteInfo = latestResolved.dlsiteInfo,
                            rjCode = resolvedTarget.workno,
                            asmrOneWorkId = if (targetChanged) null else latestResolved.asmrOneWorkId,
                            preserveHeaderAlbumMetadata = latestResolved.preserveHeaderAlbumMetadata
                        ),
                        dlsiteWorkno = resolvedTarget.workno,
                        dlsiteEditions = resolvedTarget.editions,
                        dlsiteSelectedLang = resolvedTarget.selectedLang,
                        hasResolvedInitialDlsiteTarget = true,
                        hasResolvedAsmrOneContent = if (targetChanged) false else latestResolved.hasResolvedAsmrOneContent,
                        asmrOneWorkId = if (targetChanged && !keepAsmrOneContentDuringTargetSwitch) {
                            null
                        } else {
                            latestResolved.asmrOneWorkId
                        },
                        asmrOneSite = if (targetChanged && !keepAsmrOneContentDuringTargetSwitch) {
                            null
                        } else {
                            latestResolved.asmrOneSite
                        },
                        asmrOneTree = if (targetChanged && !keepAsmrOneContentDuringTargetSwitch) {
                            emptyList()
                        } else {
                            latestResolved.asmrOneTree
                        },
                        isLoadingDlsite = true,
                        isLoadingAsmrOne = if (targetChanged) false else latestResolved.isLoadingAsmrOne,
                        isLoadingDlsiteTrial = false
                    )
                    _uiState.value = AlbumDetailUiState.Success(model = loadModel)
                }

                if (loadModel.dlsiteInfo != null) {
                    val workno = loadModel.dlsiteWorkno.trim().uppercase().ifBlank { loadModel.rjCode.trim().uppercase() }
                    if (workno.isBlank()) {
                        _uiState.value = AlbumDetailUiState.Success(
                            model = loadModel.copy(
                                hasLoadedInitialDlsiteContent = true,
                                isLoadingDlsite = false
                            )
                        )
                        return@launch
                    }
                }

                val workno = loadModel.dlsiteWorkno.trim().uppercase().ifBlank { loadModel.rjCode.trim().uppercase() }
                if (workno.isBlank()) {
                    _uiState.value = AlbumDetailUiState.Success(
                        model = loadModel.copy(
                            hasLoadedInitialDlsiteContent = true,
                            isLoadingDlsite = false
                        )
                    )
                    return@launch
                }
                val locale = dlsiteLocaleForLang(loadModel.dlsiteSelectedLang)
                val (dlsiteInitialDetail, dlsiteRecommendationsFromV2) = coroutineScope {
                    val initialDeferred = async {
                        runCatching { dlsiteScraper.getInitialWorkDetail(workno, locale = locale) }.getOrNull()
                    }
                    val recDeferred = async {
                        runCatching { dlsiteScraper.getRecommendationsDetailV2(workno, locale = locale) }
                            .getOrDefault(DlsiteRecommendations())
                    }
                    initialDeferred.await() to recDeferred.await()
                }
                val dlsiteWorkInfo = dlsiteInitialDetail?.workInfo
                val dlsiteTrialTracks = dlsiteInitialDetail?.trialTracks.orEmpty()

                val dlsiteInfo = dlsiteWorkInfo?.album
                val dlsiteGalleryUrls = dlsiteWorkInfo?.galleryUrls.orEmpty()
                
                fun mergePreferNonBlank(
                    primary: List<DlsiteRecommendedWork>,
                    secondary: List<DlsiteRecommendedWork>
                ): List<DlsiteRecommendedWork> {
                    if (primary.isEmpty()) return secondary
                    if (secondary.isEmpty()) return primary
                    val secondaryById = secondary.associateBy { it.rjCode.trim().uppercase() }
                    val merged = primary.map { p ->
                        val s = secondaryById[p.rjCode.trim().uppercase()]
                        if (s == null) {
                            p
                        } else {
                            p.copy(
                                title = p.title.ifBlank { s.title },
                                coverUrl = p.coverUrl.ifBlank { s.coverUrl },
                                ribbon = p.ribbon ?: s.ribbon
                            )
                        }
                    }
                    val existing = merged.mapTo(hashSetOf()) { it.rjCode.trim().uppercase() }
                    val appended = secondary.filter { it.rjCode.trim().uppercase() !in existing }
                    return (merged + appended).distinctBy { it.rjCode.trim().uppercase() }
                }

                val fallbackRecs = dlsiteWorkInfo?.recommendations ?: DlsiteRecommendations()
                val circleWorks = mergePreferNonBlank(dlsiteRecommendationsFromV2.circleWorks, fallbackRecs.circleWorks)
                val sameVoiceWorks = mergePreferNonBlank(dlsiteRecommendationsFromV2.sameVoiceWorks, fallbackRecs.sameVoiceWorks)
                val alsoBoughtWorks = mergePreferNonBlank(dlsiteRecommendationsFromV2.alsoBoughtWorks, fallbackRecs.alsoBoughtWorks)
                
                val dlsiteRecommendationsRaw = DlsiteRecommendations(
                    circleWorks = circleWorks,
                    sameVoiceWorks = sameVoiceWorks,
                    alsoBoughtWorks = alsoBoughtWorks
                )
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                if (token != dlsiteLoadToken) return@launch
                val displayAlbum = mergeDetailHeaderAlbum(
                    currentDisplayAlbum = updated.displayAlbum,
                    localAlbum = updated.localAlbum,
                    fetchedDlsiteInfo = dlsiteInfo,
                    rjCode = updated.rjCode,
                    asmrOneWorkId = updated.asmrOneWorkId,
                    preserveHeaderAlbumMetadata = updated.preserveHeaderAlbumMetadata
                )
                _uiState.value = AlbumDetailUiState.Success(
                    model = updated.copy(
                        displayAlbum = displayAlbum,
                        dlsiteInfo = if (updated.preserveHeaderAlbumMetadata) updated.dlsiteInfo else dlsiteInfo,
                        dlsiteGalleryUrls = dlsiteGalleryUrls,
                        dlsiteTrialTracks = dlsiteTrialTracks,
                        dlsiteRecommendations = dlsiteRecommendationsRaw,
                        hasLoadedInitialDlsiteContent = true,
                        isLoadingDlsite = false
                    )
                )

                dlsiteRecommendationEnrichJob?.cancel()
                dlsiteRecommendationEnrichJob = viewModelScope.launch enrichLaunch@{
                    val current2 = _uiState.value as? AlbumDetailUiState.Success ?: return@enrichLaunch
                    if (token != dlsiteLoadToken) return@enrichLaunch
                    val recs = current2.model.dlsiteRecommendations
                    if (recs.alsoBoughtWorks.isEmpty() && recs.circleWorks.isEmpty() && recs.sameVoiceWorks.isEmpty()) return@enrichLaunch
                    val enriched = runCatching { enrichRecommendationsWithAsmrOne(recs) }
                        .getOrNull() ?: return@enrichLaunch
                    val updated2 = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@enrichLaunch
                    if (token != dlsiteLoadToken) return@enrichLaunch
                    _uiState.value = AlbumDetailUiState.Success(model = updated2.copy(dlsiteRecommendations = enriched))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                _uiState.value = AlbumDetailUiState.Success(model = updated.copy(isLoadingDlsite = false))
            }
        }
    }

    fun selectDlsiteLanguage(lang: String) {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val normalized = lang.trim().uppercase()
        if (normalized.isBlank()) return

        val editions = current.model.dlsiteEditions
        val target = editions.firstOrNull { it.lang == normalized }
            ?: if (normalized == "JPN") DlsiteLanguageEdition(
                workno = current.model.baseRjCode.trim().uppercase(),
                lang = "JPN",
                label = "日本語",
                displayOrder = 1
            ) else null
        val workno = target?.workno?.trim()?.uppercase().orEmpty().ifBlank { current.model.baseRjCode.trim().uppercase() }
        if (workno.isBlank()) return
        if (normalized == current.model.dlsiteSelectedLang.trim().uppercase() && workno == current.model.dlsiteWorkno.trim().uppercase()) return

        cancelPendingOnlineJobs(resetLoadingState = false)
        dlsiteLoadToken++
        asmrOneLoadToken++
        asmrOneAttemptedRj.clear()
        dlsitePlayAttemptedRj.clear()
        _uiState.value = AlbumDetailUiState.Success(
            model = current.model.copy(
                dlsiteSelectedLang = target?.lang ?: normalized,
                dlsiteWorkno = workno,
                dlsitePlayWorkno = "",
                rjCode = workno,
                displayAlbum = mergeDetailHeaderAlbum(
                    currentDisplayAlbum = current.model.displayAlbum,
                    localAlbum = current.model.localAlbum,
                    fetchedDlsiteInfo = null,
                    rjCode = workno,
                    asmrOneWorkId = null,
                    preserveHeaderAlbumMetadata = current.model.preserveHeaderAlbumMetadata
                ),
                dlsiteInfo = null,
                dlsiteGalleryUrls = emptyList(),
                dlsiteTrialTracks = emptyList(),
                dlsiteRecommendations = DlsiteRecommendations(),
                hasResolvedInitialDlsiteTarget = true,
                hasLoadedInitialDlsiteContent = false,
                hasResolvedAsmrOneContent = false,
                hasResolvedDlsitePlayContent = false,
                isDlsiteLanguageUserSelected = true,
                asmrOneWorkId = null,
                asmrOneSite = null,
                asmrOneTree = emptyList(),
                dlsitePlayTree = emptyList(),
                isLoadingDlsite = false,
                isLoadingDlsiteTrial = false,
                isLoadingAsmrOne = false,
                isLoadingDlsitePlay = false
            )
        )
        clearTreeState("tree:asmrOne:$workno")
        clearTreeState("tree:dlsitePlay:$workno")
        clearTreeState("localTree:rj:$workno")
        viewModelScope.launch {
            val local = runCatching { loadLocalAlbumByRj(workno) }.getOrNull() ?: current.model.localAlbum
            val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
            if (!updated.rjCode.equals(workno, ignoreCase = true)) return@launch
            val displayAlbum = mergeDetailHeaderAlbum(
                currentDisplayAlbum = updated.displayAlbum,
                localAlbum = local,
                fetchedDlsiteInfo = updated.dlsiteInfo,
                rjCode = updated.rjCode,
                asmrOneWorkId = updated.asmrOneWorkId,
                preserveHeaderAlbumMetadata = updated.preserveHeaderAlbumMetadata
            )
            _uiState.value = AlbumDetailUiState.Success(
                model = updated.copy(
                    localAlbum = local,
                    displayAlbum = displayAlbum
                )
            )
        }
        ensureDlsiteLoaded()
        ensureAsmrOneLoaded()
    }

    fun refreshAsmrOneSection() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val keyRj = current.model.rjCode.trim().uppercase()
        if (keyRj.isBlank() || current.model.isLoadingAsmrOne) return

        asmrOneAttemptedRj.remove(keyRj)
        asmrOneCollectedCache.remove(keyRj)
        asmrOneResolvedCache.remove(keyRj)
        asmrOneResolutionInFlight.remove(keyRj)?.cancel()

        val oldWorkId = current.model.asmrOneWorkId?.trim().orEmpty()
        if (oldWorkId.isNotBlank()) {
            val cacheKey = asmrOneTracksCacheKey(oldWorkId)
            asmrOneTracksCache.remove(cacheKey)
        }

        _uiState.value = AlbumDetailUiState.Success(
            model = current.model.copy(
                asmrOneWorkId = null,
                asmrOneSite = null,
                asmrOneTree = emptyList(),
                hasResolvedAsmrOneContent = false,
                isLoadingAsmrOne = false
            )
        )
        ensureAsmrOneLoaded()
    }

    private fun finishAsmrOneLoad(keyRj: String, resolved: Boolean, showFailureMessage: Boolean = false) {
        val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return
        val updatedKey = updated.rjCode.trim().uppercase()
        if (updatedKey.equals(keyRj, ignoreCase = true)) {
            if (showFailureMessage && updated.asmrOneTree.isEmpty()) {
                messageManager.showError("在线资源加载失败，请稍后重试")
            }
            _uiState.value = AlbumDetailUiState.Success(
                model = updated.copy(
                    isLoadingAsmrOne = false,
                    hasResolvedAsmrOneContent = if (resolved) true else updated.hasResolvedAsmrOneContent
                )
            )
        }
    }

    fun refreshDlsiteTrialSection() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val workno = current.model.dlsiteWorkno.trim().uppercase().ifBlank { current.model.rjCode.trim().uppercase() }
        if (workno.isBlank() || current.model.isLoadingDlsite || current.model.isLoadingDlsiteTrial) return

        val token = ++dlsiteTrialLoadToken
        val locale = dlsiteLocaleForLang(current.model.dlsiteSelectedLang)
        dlsiteTrialLoadJob?.cancel()
        dlsiteTrialLoadJob = viewModelScope.launch {
            val latestBefore = _uiState.value as? AlbumDetailUiState.Success ?: return@launch
            val latestWorkno = latestBefore.model.dlsiteWorkno.trim().uppercase().ifBlank { latestBefore.model.rjCode.trim().uppercase() }
            if (!latestWorkno.equals(workno, ignoreCase = true)) return@launch
            if (latestBefore.model.isLoadingDlsite || latestBefore.model.isLoadingDlsiteTrial) return@launch
            _uiState.value = AlbumDetailUiState.Success(model = latestBefore.model.copy(isLoadingDlsiteTrial = true))
            try {
                val tracks = runCatching { dlsiteScraper.getTracks(workno, locale = locale) }.getOrDefault(emptyList())
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                val updatedWorkno = updated.dlsiteWorkno.trim().uppercase().ifBlank { updated.rjCode.trim().uppercase() }
                if (token != dlsiteTrialLoadToken || !updatedWorkno.equals(workno, ignoreCase = true)) return@launch
                _uiState.value = AlbumDetailUiState.Success(
                    model = updated.copy(
                        dlsiteTrialTracks = tracks,
                        isLoadingDlsiteTrial = false
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                val updatedWorkno = updated.dlsiteWorkno.trim().uppercase().ifBlank { updated.rjCode.trim().uppercase() }
                if (token != dlsiteTrialLoadToken || !updatedWorkno.equals(workno, ignoreCase = true)) return@launch
                messageManager.showError("试听刷新失败，请稍后重试")
                _uiState.value = AlbumDetailUiState.Success(model = updated.copy(isLoadingDlsiteTrial = false))
            }
        }
    }

    fun ensureAsmrOneLoaded() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val keyRj = current.model.rjCode.trim().uppercase()
        if (current.model.asmrOneTree.isNotEmpty() && current.model.hasResolvedAsmrOneContent) {
            return
        }
        if (
            keyRj.isBlank() ||
            current.model.hasResolvedAsmrOneContent ||
            current.model.isLoadingAsmrOne ||
            asmrOneAttemptedRj.contains(keyRj)
        ) return
        asmrOneAttemptedRj.add(keyRj)
        val token = ++asmrOneLoadToken
        asmrOneLoadJob?.cancel()
        asmrOneLoadJob = viewModelScope.launch {
            val latestBefore = _uiState.value as? AlbumDetailUiState.Success ?: return@launch
            val latestKey = latestBefore.model.rjCode.trim().uppercase()
            if (!latestKey.equals(keyRj, ignoreCase = true)) {
                asmrOneAttemptedRj.remove(keyRj)
                finishAsmrOneLoad(keyRj, resolved = false)
                return@launch
            }
            _uiState.value = AlbumDetailUiState.Success(
                model = latestBefore.model.copy(
                    isLoadingAsmrOne = true,
                    hasResolvedAsmrOneContent = false
                )
            )
            try {
                val latest = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                val selectedLang = latest.dlsiteSelectedLang.trim().uppercase().ifBlank { "JPN" }
                val latestBase = latest.baseRjCode.trim().uppercase()
                val jpnWorkno = latest.dlsiteEditions.firstOrNull { it.lang.equals("JPN", ignoreCase = true) }
                    ?.workno
                    ?.trim()
                    ?.uppercase()
                    .orEmpty()
                val originalRj = jpnWorkno.ifBlank { latestBase.ifBlank { keyRj } }
                val preferInitialRj = !latest.isDlsiteLanguageUserSelected &&
                    RJ_CODE_REGEX.matches(latestBase)
                val directoryRjs = asmrOneTrackRjCandidates(
                    baseRj = latestBase,
                    currentRj = keyRj,
                    dlsiteWorkno = latest.dlsiteWorkno,
                    originalRj = originalRj,
                    selectedLang = selectedLang,
                    preferInitialRj = preferInitialRj
                )
                fun finishWithResolvedAsmrOneTree(
                    workId: String?,
                    site: Int?,
                    tree: List<AsmrOneTrackNodeResponse>
                ): Boolean {
                    val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return true
                    if (token != asmrOneLoadToken) {
                        asmrOneAttemptedRj.remove(keyRj)
                        finishAsmrOneLoad(keyRj, resolved = false)
                        return true
                    }
                    val resolvedWorkId = workId?.trim().orEmpty().ifBlank { updated.asmrOneWorkId.orEmpty() }
                    val displayAlbum = mergeDetailHeaderAlbum(
                        currentDisplayAlbum = updated.displayAlbum,
                        localAlbum = updated.localAlbum,
                        fetchedDlsiteInfo = updated.dlsiteInfo,
                        rjCode = updated.rjCode,
                        asmrOneWorkId = resolvedWorkId.takeIf { it.isNotBlank() } ?: updated.asmrOneWorkId,
                        preserveHeaderAlbumMetadata = updated.preserveHeaderAlbumMetadata
                    )
                    _uiState.value = AlbumDetailUiState.Success(
                        model = updated.copy(
                            displayAlbum = displayAlbum,
                            asmrOneWorkId = resolvedWorkId.takeIf { it.isNotBlank() } ?: updated.asmrOneWorkId,
                            asmrOneSite = site,
                            asmrOneTree = tree,
                            hasResolvedAsmrOneContent = true,
                            isLoadingAsmrOne = false
                        )
                    )
                    return true
                }

                suspend fun loadBackupEndpointAndFinishIfFound(): Boolean {
                    val backupResult = fetchAsmrOneTracksFromBackup(
                        candidateRjs = directoryRjs,
                        fetchBackup = { fetchBackupAsmrOneTracksByRj(it) }
                    )
                    if (backupResult.second.isEmpty()) return false
                    return finishWithResolvedAsmrOneTree(
                        workId = backupResult.first,
                        site = AsmrOneEndpoint.BACKUP,
                        tree = backupResult.second
                    )
                }

                if (asmrOneCrawler.selectedEndpoint() == AsmrOneEndpoint.BACKUP) {
                    if (!loadBackupEndpointAndFinishIfFound()) {
                        asmrOneAttemptedRj.remove(keyRj)
                        finishAsmrOneLoad(keyRj, resolved = true)
                    }
                    return@launch
                }

                var preferredInitialResolution: Pair<String, Int?>? = null
                if (preferInitialRj) {
                    val preferredInitial = resolveAsmrOneWork(latestBase)
                    if (preferredInitial != null) {
                        preferredInitialResolution = preferredInitial
                        val preferredWorkId = preferredInitial.first
                        val preferredResult = getAsmrOneTracksCached(preferredWorkId)
                        if (preferredResult.tree.isNotEmpty()) {
                            finishWithResolvedAsmrOneTree(
                                workId = preferredWorkId,
                                site = preferredResult.site,
                                tree = preferredResult.tree
                            )
                            return@launch
                        }
                    }
                }

                var resolvedOriginal = preferredInitialResolution.takeIf {
                    latestBase.equals(originalRj, ignoreCase = true) ||
                        latestBase.equals(keyRj, ignoreCase = true)
                }
                if (resolvedOriginal == null) {
                    val directRjs = listOf(originalRj, keyRj)
                        .map { it.trim().uppercase() }
                        .filter { RJ_CODE_REGEX.matches(it) }
                        .distinct()
                    for (candidateRj in directRjs) {
                        val resolved = resolveAsmrOneWork(candidateRj) ?: continue
                        resolvedOriginal = resolved
                        break
                    }
                }
                if (resolvedOriginal == null) {
                    asmrOneAttemptedRj.remove(keyRj)
                    if (token != asmrOneLoadToken) {
                        finishAsmrOneLoad(keyRj, resolved = false)
                    } else {
                        finishAsmrOneLoad(keyRj, resolved = true)
                    }
                    return@launch
                }
                val originalWorkId = resolvedOriginal.first

                val originalDetails = runCatching {
                    asmrOneCrawler.getDetails(originalWorkId)
                }.getOrNull()

                val workId = resolveAsmrOneTrackWorkId(
                    resolvedWorkId = originalWorkId,
                    resolvedDetails = originalDetails,
                    selectedLang = selectedLang,
                    selectedRjs = directoryRjs
                )
                val trackResult = workId
                    ?.let { getAsmrOneTracksCached(it) }
                    ?: AsmrOneTracksResult(emptyList(), null)
                if (token != asmrOneLoadToken) {
                    asmrOneAttemptedRj.remove(keyRj)
                    finishAsmrOneLoad(keyRj, resolved = false)
                    return@launch
                }
                if (workId.isNullOrBlank()) {
                    asmrOneAttemptedRj.remove(keyRj)
                    finishAsmrOneLoad(keyRj, resolved = true)
                    return@launch
                }
                finishWithResolvedAsmrOneTree(
                    workId = workId,
                    site = trackResult.site,
                    tree = trackResult.tree
                )
            } catch (e: CancellationException) {
                asmrOneAttemptedRj.remove(keyRj)
                finishAsmrOneLoad(keyRj, resolved = false)
            } catch (e: Exception) {
                asmrOneAttemptedRj.remove(keyRj)
                finishAsmrOneLoad(keyRj, resolved = true, showFailureMessage = true)
            }
        }
    }

    fun ensureDlsitePlayLoaded() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val baseRj = current.model.baseRjCode.trim().uppercase()
        val candidates0 = DlsiteWorkNo.normalizeCandidates(
            listOf(
                current.model.dlsitePlayWorkno,
                current.model.dlsiteWorkno,
                current.model.rjCode,
                baseRj
            )
        )
        val playCookieFingerprint = DlsiteAuthStore(context).getPlayCookie().trim().hashCode()
        val attemptKey = candidates0.joinToString("|") + "#" + playCookieFingerprint

        if (
            candidates0.isEmpty() ||
            current.model.dlsitePlayTree.isNotEmpty() ||
            current.model.isLoadingDlsitePlay ||
            dlsitePlayAttemptedRj.contains(attemptKey)
        ) return
        dlsitePlayAttemptedRj.add(attemptKey)
        dlsitePlayLoadJob?.cancel()
        dlsitePlayLoadJob = viewModelScope.launch {
            _uiState.value = AlbumDetailUiState.Success(
                model = current.model.copy(
                    isLoadingDlsitePlay = true,
                    hasResolvedDlsitePlayContent = false
                )
            )
            try {
                val editions = runCatching {
                    if (current.model.dlsiteEditions.size > 1) {
                        current.model.dlsiteEditions
                    } else if (baseRj.isNotBlank()) {
                        dlsiteProductInfoClient.fetchLanguageEditions(baseRj)
                    } else {
                        emptyList()
                    }
                }.getOrDefault(emptyList())
                val editionWorknos = editions
                    .asSequence()
                    .map { it.workno.trim().uppercase() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sortedBy { if (it.startsWith("RJ", ignoreCase = true)) 0 else 1 }
                    .toList()
                val candidates = (candidates0 + editionWorknos).distinct()

                var pickedWorkno: String? = null
                var pickedResult: DlsitePlayTreeResult? = null
                var lastError: Exception? = null
                var sawNotAvailable = false
                for (workno in candidates) {
                    val res = try {
                        withTimeout(ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS) {
                            dlsitePlayWorkClient.fetchPlayableTree(workno)
                        }
                    } catch (e: TimeoutCancellationException) {
                        lastError = e
                        continue
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                        continue
                    }
                    if (res.status == DlsitePlayLoadStatus.Success && res.tree.isNotEmpty()) {
                        pickedWorkno = workno
                        pickedResult = res
                        break
                    }
                    if (res.status == DlsitePlayLoadStatus.NotAvailable) {
                        sawNotAvailable = true
                    }
                }

                val result = pickedResult ?: DlsitePlayTreeResult(
                    tree = emptyList(),
                    subtitlesByUrl = emptyMap(),
                    status = if (sawNotAvailable) DlsitePlayLoadStatus.NotAvailable else DlsitePlayLoadStatus.Success
                )
                result.subtitlesByUrl.forEach { (url, subs) ->
                    if (subs.isNotEmpty()) OnlineLyricsStore.set(url, subs)
                }
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                if (pickedResult == null && lastError != null && !sawNotAvailable) {
                    dlsitePlayAttemptedRj.remove(attemptKey)
                    messageManager.showError("DLsite Play 加载失败，请稍后重试")
                }
                _uiState.value = AlbumDetailUiState.Success(
                    model = updated.copy(
                        dlsitePlayTree = result.tree,
                        dlsitePlayWorkno = pickedWorkno?.trim().orEmpty(),
                        hasResolvedDlsitePlayContent = true,
                        isLoadingDlsitePlay = false
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dlsitePlayAttemptedRj.remove(attemptKey)
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                _uiState.value = AlbumDetailUiState.Success(
                    model = updated.copy(
                        hasResolvedDlsitePlayContent = true,
                        isLoadingDlsitePlay = false
                    )
                )
            }
        }
    }

    private fun albumFromInitialHint(rj: String, hint: AlbumCoverHint?): Album {
        return albumFromCoverHint(rj, hint)
    }

    private fun createRouteInitialUiState(savedStateHandle: SavedStateHandle): AlbumDetailUiState {
        val albumId = savedStateHandle.get<Long>("albumId")?.takeIf { it > 0L }
        val routeRj = savedStateHandle.get<String>("rjCode")
            ?.trim()
            .orEmpty()
            .ifBlank { savedStateHandle.get<String>("rj")?.trim().orEmpty() }
            .uppercase()
        val hint = AlbumCoverHintStore.peekHint(albumId, routeRj)
        val initialRj = routeRj.ifBlank { hint?.rjCode.orEmpty() }
        val hintAlbum = albumFromInitialHint(initialRj, hint)
        val preserveHeaderMetadata = shouldPreserveHeaderAlbumMetadata(hint)
        return AlbumDetailUiState.Success(
            model = createInitialAlbumDetailModel(
                rj = initialRj,
                displayAlbum = hintAlbum,
                dlsiteInfo = hintAlbum.takeIf { preserveHeaderMetadata },
                preserveHeaderAlbumMetadata = preserveHeaderMetadata
            )
        )
    }

    private fun createInitialAlbumDetailModel(
        rj: String,
        displayAlbum: Album,
        localAlbum: Album? = null,
        dlsiteInfo: Album? = null,
        preserveHeaderAlbumMetadata: Boolean = false
    ): AlbumDetailModel {
        return AlbumDetailModel(
            baseRjCode = rj,
            rjCode = rj,
            listenTogetherRjListenerCount = null,
            displayAlbum = displayAlbum,
            localAlbum = localAlbum,
            dlsiteInfo = dlsiteInfo,
            dlsiteGalleryUrls = emptyList(),
            dlsiteTrialTracks = emptyList(),
            dlsiteRecommendations = DlsiteRecommendations(),
            dlsiteWorkno = rj,
            dlsitePlayWorkno = "",
            dlsiteEditions = defaultDlsiteEditions(rj),
            dlsiteSelectedLang = "JPN",
            hasResolvedInitialDlsiteTarget = false,
            hasLoadedInitialDlsiteContent = false,
            hasResolvedAsmrOneContent = false,
            hasResolvedDlsitePlayContent = false,
            preserveHeaderAlbumMetadata = preserveHeaderAlbumMetadata,
            isDlsiteLanguageUserSelected = false,
            asmrOneWorkId = null,
            asmrOneSite = null,
            asmrOneTree = emptyList(),
            dlsitePlayTree = emptyList(),
            isLoadingDlsite = false,
            isLoadingDlsiteTrial = false,
            isLoadingAsmrOne = false,
            isLoadingDlsitePlay = false
        )
    }

    private suspend fun loadLocalAlbumById(albumId: Long): Album? {
        val entity = albumDao.getAlbumById(albumId) ?: return null
        return entityToDomain(entity)
    }

    private suspend fun loadLocalAlbumByRj(rjCode: String): Album? {
        val normalized = rjCode.trim().uppercase()
        val entity = albumDao.getAlbumByWorkIdOnce(normalized)
            ?: albumDao.getAlbumByWorkIdOnce(rjCode.trim())
            ?: return null
        return entityToDomain(entity)
    }

    private suspend fun entityToDomain(entity: AlbumEntity): Album {
        val tracks = loadLocalTracks(entity)
        return Album(
            id = entity.id,
            title = entity.titleForDisplay,
            displayTitle = entity.displayTitle,
            path = entity.path,
            localPath = entity.localPath,
            downloadPath = entity.downloadPath,
            circle = entity.circle,
            cv = entity.cv,
            tags = entity.tags.split(",").filter { it.isNotBlank() },
            coverUrl = entity.coverUrl,
            coverPath = entity.coverPath,
            coverThumbPath = entity.coverThumbPath,
            workId = entity.workId,
            rjCode = entity.rjCode.ifBlank { entity.workId },
            description = entity.description,
            tracks = tracks
        )
    }

    fun downloadAlbum() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val model = current.model
        val album = model.displayAlbum

        val existingLocalKeys = LinkedHashSet<String>()
        val existingLocalKeysNoGroup = LinkedHashSet<String>()
        model.localAlbum?.tracks
            ?.filter { !it.path.trim().startsWith("http", ignoreCase = true) }
            ?.forEach { t ->
                existingLocalKeys.add(TrackKeyNormalizer.buildKey(t.title, t.group, null))
                existingLocalKeysNoGroup.add(TrackKeyNormalizer.buildKey(t.title, "", null))
            }
        
        messageManager.showInfo("正在加入下载队列")
        
        val folderName = safeFolderName(album.rjCode.ifBlank { album.workId }.ifBlank { album.title })
        val baseDir = File(context.getExternalFilesDir(null), "albums")
        val targetDir = File(baseDir, folderName)
        if (!targetDir.exists()) targetDir.mkdirs()

        val coverUrl = album.coverUrl.trim()
        if (coverUrl.startsWith("http", ignoreCase = true)) {
            val ext = coverUrl.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..5 } ?: "jpg"
            val coverFileName = "cover.$ext"
            downloadManager.enqueueDownload(
                url = coverUrl,
                fileName = coverFileName,
                targetDir = targetDir.absolutePath,
                taskRootDir = targetDir.absolutePath,
                relativePath = coverFileName,
                taskSubtitle = album.title,
                tags = listOf("album:$folderName"),
                albumTitle = album.title,
                albumCircle = album.circle,
                albumCv = album.cv,
                albumTagsCsv = album.tags.joinToString(","),
                albumCoverUrl = album.coverUrl,
                albumWorkId = album.workId,
                albumRjCode = album.rjCode
            )
        }

        album.tracks.forEachIndexed { index, track ->
            val url = track.path.trim()
            if (!url.startsWith("http", ignoreCase = true)) return@forEachIndexed
            val key = TrackKeyNormalizer.buildKey(track.title, track.group, null)
            val keyNoGroup = TrackKeyNormalizer.buildKey(track.title, "", null)
            if (existingLocalKeys.contains(key) || existingLocalKeysNoGroup.contains(keyNoGroup)) {
                return@forEachIndexed
            }
            val ext = url.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..6 } ?: "mp3"
            val fileName = "${(index + 1).toString().padStart(2, '0')}_${safeFileName(track.title)}.$ext"
            downloadManager.enqueueDownload(
                url = url,
                fileName = fileName,
                targetDir = targetDir.absolutePath,
                taskRootDir = targetDir.absolutePath,
                relativePath = fileName,
                tags = listOf("album:$folderName"),
                albumTitle = album.title,
                albumCircle = album.circle,
                albumCv = album.cv,
                albumTagsCsv = album.tags.joinToString(","),
                albumCoverUrl = album.coverUrl,
                albumWorkId = album.workId,
                albumRjCode = album.rjCode
            )
        }
    }

    fun downloadAsmrOneSelected(selectedLeafPaths: Set<String>) {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        enqueueRemoteTreeSelectionDownload(
            album = current.model.displayAlbum,
            tree = current.model.asmrOneTree,
            selectedLeafPaths = selectedLeafPaths,
            relativeBaseDir = ""
        )
    }

    fun downloadDlsitePlaySelected(selectedLeafPaths: Set<String>) {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        enqueueRemoteTreeSelectionDownload(
            album = current.model.displayAlbum,
            tree = current.model.dlsitePlayTree,
            selectedLeafPaths = selectedLeafPaths,
            relativeBaseDir = ""
        )
    }

    fun downloadDlsitePlayLosslessArchive() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val model = current.model
        if (model.dlsitePlayTree.isEmpty()) return

        val album = model.displayAlbum
        val workno = normalizeRj(
            model.dlsitePlayWorkno
                .ifBlank { model.dlsiteWorkno }
                .ifBlank { model.rjCode }
                .ifBlank { album.rjCode.ifBlank { album.workId } }
        )
        if (workno.isBlank()) {
            messageManager.showError("无法确定 DLsite Play 作品编号")
            return
        }

        val folderName = safeFolderName(album.rjCode.ifBlank { album.workId }.ifBlank { workno }.ifBlank { album.title })
        val baseDir = File(context.getExternalFilesDir(null), "albums")
        val targetDir = File(baseDir, folderName)
        if (!targetDir.exists()) targetDir.mkdirs()

        enqueueRemoteDownloadCover(
            album = album,
            targetRootDir = targetDir,
            taskKey = "album:$folderName",
            taskSubtitle = album.title
        )

        downloadManager.enqueueDownload(
            url = "https://play.dlsite.com/api/v3/download?workno=$workno",
            fileName = "dlsite_lossless_archive.zip",
            targetDir = targetDir.absolutePath,
            taskRootDir = targetDir.absolutePath,
            relativePath = "dlsite_lossless_archive.zip",
            taskSubtitle = album.title,
            tags = listOf("album:$folderName"),
            albumTitle = album.title,
            albumCircle = album.circle,
            albumCv = album.cv,
            albumTagsCsv = album.tags.joinToString(","),
            albumCoverUrl = album.coverUrl,
            albumWorkId = album.workId,
            albumRjCode = album.rjCode
        )

        messageManager.showInfo("正在加入无损下载队列")
    }

    fun downloadDlsiteTrialSelected(selectedLeafPaths: Set<String>) {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        enqueueRemoteTreeSelectionDownload(
            album = current.model.displayAlbum,
            tree = buildDlsiteTrialDownloadTree(current.model.dlsiteTrialTracks),
            selectedLeafPaths = selectedLeafPaths,
            relativeBaseDir = DlsiteTrialDownloadDirectoryName
        )
    }

    fun downloadSavedOnlineTrack(track: Track, relativePath: String) {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val url = track.path.trim()
        if (!isOnlineTrackPath(url)) return

        val normalizedRelativePath = relativePath
            .replace('\\', '/')
            .trim()
            .trimStart('/')
            .ifBlank { safeFileName(track.title) }
        if (treeFileTypeForNode(normalizedRelativePath, url) != TreeFileType.Audio) return

        enqueueRemoteLeafDownloads(
            album = current.model.displayAlbum,
            selected = listOf(
                AsmrOneLeafDownload(
                    url = url,
                    relativePath = normalizedRelativePath,
                    duration = track.duration
                )
            ),
            relativeBaseDir = "",
            includeCover = false
        )
    }

    fun saveOnlineSelectedToLibrary(selectedLeafPaths: Set<String>) {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val model = current.model
        val displayAlbum = model.displayAlbum
        val tree = if (model.dlsitePlayTree.isNotEmpty()) model.dlsitePlayTree else model.asmrOneTree
        if (tree.isEmpty()) return

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val leaves = flattenOnlineSaveLeaves(tree)
                val selected = if (selectedLeafPaths.isEmpty()) leaves else leaves.filter { selectedLeafPaths.contains(it.relativePath) }
                if (selected.isEmpty()) {
                    return@withContext SaveOnlineToLibraryResult(
                        selectedCount = 0,
                        insertedCount = 0,
                        resourceSavedCount = 0
                    )
                }

                val rj = normalizeRj(displayAlbum.rjCode.ifBlank { displayAlbum.workId }.ifBlank { model.rjCode })
                val workKey = rj.ifBlank { displayAlbum.workId.trim().ifBlank { displayAlbum.title.trim() } }
                val onlinePath = "web://rj/${workKey.uppercase()}"
                val albumDir = onlineSaveAlbumDir(displayAlbum, workKey).apply {
                    mkdirs()
                    ensureNoMediaMarkers(this)
                }
                val playableSelected = selected.filter { isPlayableTreeFileType(it.fileType) }
                val resourceSelected = selected.filter { !isPlayableTreeFileType(it.fileType) }

                fun canonicalUrl(url: String): String {
                    return url.trim().substringBefore('#').substringBefore('?')
                }

                var insertedCount = 0
                var resourceSavedCount = 0
                var albumIdResult = 0L
                database.withTransaction {
                    val existing = if (workKey.isNotBlank()) {
                        runCatching { albumDao.getAlbumByWorkIdOnce(workKey) }.getOrNull()
                    } else {
                        null
                    }

                    val tagsCsv = displayAlbum.tags.joinToString(",")
                    val entity = AlbumEntity(
                        id = existing?.id ?: 0L,
                        title = existing?.title?.takeIf { it.isNotBlank() } ?: displayAlbum.title,
                        path = existing?.path?.takeIf { it.isNotBlank() } ?: onlinePath,
                        localPath = existing?.localPath?.takeIf { it.isNotBlank() } ?: albumDir.absolutePath,
                        downloadPath = existing?.downloadPath,
                        circle = existing?.circle?.takeIf { it.isNotBlank() } ?: displayAlbum.circle,
                        cv = existing?.cv?.takeIf { it.isNotBlank() } ?: displayAlbum.cv,
                        tags = existing?.tags?.takeIf { it.isNotBlank() } ?: tagsCsv,
                        coverUrl = existing?.coverUrl?.takeIf { it.isNotBlank() } ?: displayAlbum.coverUrl,
                        coverPath = existing?.coverPath.orEmpty(),
                        coverThumbPath = existing?.coverThumbPath.orEmpty(),
                        workId = existing?.workId?.takeIf { it.isNotBlank() } ?: displayAlbum.workId.trim().ifBlank { workKey },
                        rjCode = existing?.rjCode?.takeIf { it.isNotBlank() } ?: displayAlbum.rjCode.trim().ifBlank { rj },
                        description = existing?.description?.takeIf { it.isNotBlank() } ?: displayAlbum.description.trim()
                    )
                    val insertedId = runCatching { albumDao.insertAlbum(entity) }.getOrDefault(0L)
                    val albumId = if (insertedId > 0L) insertedId else (existing?.id ?: 0L)
                    if (albumId <= 0L) return@withTransaction
                    albumIdResult = albumId
                    runCatching { database.localTreeCacheDao().deleteByAlbum(albumId) }

                    val fts = AlbumFtsEntity(
                        albumId = albumId,
                        title = entity.title,
                        circle = entity.circle,
                        cv = entity.cv,
                        rjCode = entity.rjCode,
                        workId = entity.workId,
                        tagsToken = entity.tags.replace(',', ' ').trim()
                    )
                    runCatching { database.albumFtsDao().upsert(listOf(fts)) }

                    val existingTracks = runCatching { trackDao.getTracksForAlbumOnce(albumId) }.getOrDefault(emptyList())
                    val existingUrlKeys = existingTracks
                        .asSequence()
                        .map { canonicalUrl(it.path) }
                        .filter { it.isNotBlank() }
                        .toSet()

                    val seenUrlKeys = linkedSetOf<String>()
                    val newLeaves = playableSelected.filter { leaf ->
                        val urlKey = canonicalUrl(leaf.url)
                        val duplicate = urlKey.isBlank() ||
                            existingUrlKeys.contains(urlKey) ||
                            seenUrlKeys.contains(urlKey)
                        if (!duplicate) {
                            seenUrlKeys.add(urlKey)
                            true
                        } else {
                            false
                        }
                    }
                    val newTracks = newLeaves.map { leaf ->
                        TrackEntity(
                            albumId = albumId,
                            title = leaf.title,
                            path = leaf.url.trim(),
                            duration = leaf.duration,
                            group = leaf.group
                        )
                    }
                    if (newTracks.isNotEmpty()) {
                        val insertedTrackIds = runCatching { trackDao.insertTracks(newTracks) }.getOrDefault(emptyList())
                        insertedCount = insertedTrackIds.count { it > 0L }
                        val sources = insertedTrackIds.zip(newLeaves).flatMap { (trackId, leaf) ->
                            leaf.subtitleSources.mapNotNull { src ->
                                val url = src.url.trim()
                                if (url.isBlank()) return@mapNotNull null
                                RemoteSubtitleSourceEntity(
                                    trackId = trackId,
                                    url = url,
                                    language = src.language,
                                    ext = src.ext
                                )
                            }
                        }
                        if (sources.isNotEmpty()) {
                            runCatching { database.remoteSubtitleSourceDao().insertAll(sources) }
                        }
                    }

                    val resourceDao = database.onlineSavedResourceDao()
                    val existingResourcesByPath = resourceDao.getForAlbumOnce(albumId)
                        .associateBy { it.relativePath }
                    val changedResources = resourceSelected.mapNotNull { leaf ->
                        val relativePath = leaf.relativePath.replace('\\', '/').trim().trimStart('/')
                        val url = leaf.url.trim()
                        if (relativePath.isBlank() || !url.startsWith("http", ignoreCase = true)) {
                            return@mapNotNull null
                        }
                        val existingResource = existingResourcesByPath[relativePath]
                        val resource = OnlineSavedResourceEntity(
                            id = existingResource?.id ?: 0L,
                            albumId = albumId,
                            relativePath = relativePath,
                            url = url,
                            fileType = leaf.fileType.name
                        )
                        resource.takeUnless { it == existingResource }
                    }.distinctBy { it.relativePath }
                    if (changedResources.isNotEmpty()) {
                        resourceDao.insertAll(changedResources)
                        resourceSavedCount = changedResources.size
                    }
                }

                if (albumIdResult > 0L) {
                    refreshAlbumAudioAggregate(albumIdResult)
                }

                SaveOnlineToLibraryResult(
                    selectedCount = selected.size,
                    insertedCount = insertedCount,
                    resourceSavedCount = resourceSavedCount
                )
            }

            val selectedCount = result.selectedCount
            val changedCount = result.insertedCount + result.resourceSavedCount
            val skippedCount = selectedCount - changedCount

            if (selectedCount <= 0) {
                messageManager.showInfo("没有可保存文件")
            } else if (changedCount <= 0) {
                messageManager.showInfo("本地已存在，未重复保存")
            } else if (skippedCount > 0) {
                messageManager.showSuccess("已保存到本地库（${changedCount}项），跳过已存在（${skippedCount}项）")
            } else {
                messageManager.showSuccess("已保存到本地库（${changedCount}项）")
            }
        }
    }

    private data class SaveOnlineToLibraryResult(
        val selectedCount: Int,
        val insertedCount: Int,
        val resourceSavedCount: Int
    )

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
            if (BuildConfig.DEBUG) Log.d("AlbumDetailViewModel", msg)
        }
        fun fail(reason: String): Boolean {
            debugLog("ensureAlbumCoverSaved fail albumId=$albumId reason=$reason coverPath=${coverPath.take(160)} coverUrl=${coverUrl.take(160)}")
            return false
        }

        val existingPathRaw = coverPath.trim().takeIf { it.isNotBlank() && it != "null" }
        if (existingPathRaw != null && !existingPathRaw.startsWith("content://", ignoreCase = true)) {
            val f = if (existingPathRaw.startsWith("file://", ignoreCase = true)) {
                runCatching { File(android.net.Uri.parse(existingPathRaw).path.orEmpty()) }.getOrNull()
            } else {
                File(existingPathRaw)
            }
            if (f != null && f.exists() && f.length() > 0L) return true
        }

        val url = coverUrl.trim().takeIf { it.isNotBlank() && it != "null" }?.let { u ->
            if (u.startsWith("//")) "https:$u" else u
        }.orEmpty()
        val canUseNetwork = url.isNotBlank() && !isLikelyPlaceholderCover(url)

        val localCandidate = existingPathRaw
            ?: url.takeIf { it.startsWith("content://", ignoreCase = true) || it.startsWith("file://", ignoreCase = true) }
        val sourceKey = (if (canUseNetwork) url else localCandidate).orEmpty()
        if (sourceKey.isBlank()) return fail("empty_source")
        val sourceHash = sourceKey.hashCode().toString()
        val coverDir = File(context.filesDir, "album_covers").apply { if (!exists()) mkdirs() }
        val thumbDir = File(context.filesDir, "album_thumbs").apply { if (!exists()) mkdirs() }
        val coverFile = File(coverDir, "a_${albumId}_$sourceHash.jpg")
        val thumbFile = File(thumbDir, "a_${albumId}_${sourceHash}_v2.jpg")

        if (coverFile.exists() && coverFile.length() > 0L && thumbFile.exists() && thumbFile.length() > 0L) {
            val entity = try {
                albumDao.getAlbumById(albumId)
            } catch (_: Exception) {
                null
            }
            if (entity != null && (entity.coverPath != coverFile.absolutePath || entity.coverThumbPath != thumbFile.absolutePath)) {
                try {
                    albumDao.updateAlbum(entity.copy(coverPath = coverFile.absolutePath, coverThumbPath = thumbFile.absolutePath))
                } catch (_: Exception) {
                }
            }
            return true
        }

        val bitmap = if (canUseNetwork) {
            val tmpFile = File(coverDir, "a_${albumId}_$sourceHash.tmp")
            try {
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
                while (maxDim / sample > 1280) sample *= 2 // 减小最大尺寸从 2048 到 1280
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565 // 使用 RGB_565 减少一半内存占用
                }
                BitmapFactory.decodeFile(tmpFile.absolutePath, opts) ?: return fail("decode_failed_sample_$sample")
            } finally {
                runCatching { if (tmpFile.exists()) tmpFile.delete() }
            }
        } else {
            val p = localCandidate ?: return fail("empty_local_source")
            if (p.startsWith("file://", ignoreCase = true)) {
                val filePath = runCatching { android.net.Uri.parse(p).path.orEmpty() }.getOrNull().orEmpty()
                if (filePath.isBlank()) return fail("file_uri_no_path")
                val f = File(filePath)
                if (!f.exists() || f.length() <= 0L) return fail("file_not_found")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, bounds)
                val w = bounds.outWidth
                val h = bounds.outHeight
                if (w <= 0 || h <= 0) return fail("file_decode_bounds_invalid")
                val maxDim = maxOf(w, h)
                var sample = 1
                while (maxDim / sample > 1280) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeFile(f.absolutePath, opts) ?: return fail("file_decode_failed_sample_$sample")
            } else {
                if (!p.startsWith("content://", ignoreCase = true)) return fail("unsupported_local_scheme")
                val uri = runCatching { android.net.Uri.parse(p) }.getOrNull() ?: return fail("content_uri_parse_failed")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, bounds)
                } ?: return fail("content_open_failed")
                val w = bounds.outWidth
                val h = bounds.outHeight
                if (w <= 0 || h <= 0) return fail("content_decode_bounds_invalid")
                val maxDim = maxOf(w, h)
                var sample = 1
                while (maxDim / sample > 1280) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                } ?: return fail("content_decode_failed_sample_$sample")
            }
        }

        FileOutputStream(coverFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        val thumb = centerCropSquare(bitmap, 640)
        FileOutputStream(thumbFile).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        return try {
            val entity = albumDao.getAlbumById(albumId) ?: return true
            albumDao.updateAlbum(entity.copy(coverPath = coverFile.absolutePath, coverThumbPath = thumbFile.absolutePath))
            debugLog("ensureAlbumCoverSaved ok albumId=$albumId cover=${coverFile.length()} thumb=${thumbFile.length()}")
            true
        } catch (e: Exception) {
            fail("db_update_${e.javaClass.simpleName}")
        }
    }

    private data class OnlineSaveLeaf(
        val relativePath: String,
        val title: String,
        val url: String,
        val duration: Double,
        val group: String,
        val fileType: TreeFileType,
        val subtitleSources: List<RemoteSubtitleSource>
    )

    private fun flattenOnlineSaveLeaves(tree: List<AsmrOneTrackNodeResponse>): List<OnlineSaveLeaf> {
        val out = mutableListOf<OnlineSaveLeaf>()
        fun sanitize(name: String): String = name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val subtitleExts = setOf("lrc", "srt", "vtt")

        data class LeafFile(
            val rawTitle: String,
            val safeTitle: String,
            val url: String,
            val duration: Double?,
            val fileType: TreeFileType
        ) {
            val ext: String = run {
                val ext0 = rawTitle.substringAfterLast('.', "").lowercase()
                if (ext0.isNotBlank()) ext0 else url.substringBefore('?').substringAfterLast('.', "").lowercase()
            }
        }

        val subtitleCandidates = mutableListOf<Pair<com.asmr.player.util.SubtitleMatchCandidate, LeafFile>>()

        fun collectSubtitleCandidates(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
            nodes.forEach { node ->
                val children = node.children.orEmpty()
                val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
                val url = node.mediaDownloadUrl ?: node.streamUrl
                val safeTitle = sanitize(rawTitle)
                val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
                if (children.isNotEmpty() || url.isNullOrBlank()) {
                    if (children.isNotEmpty()) collectSubtitleCandidates(children, path)
                    return@forEach
                }
                val fileType = treeFileTypeForNode(rawTitle, url, node.type)
                val leaf = LeafFile(
                    rawTitle = rawTitle,
                    safeTitle = safeTitle,
                    url = url,
                    duration = node.duration,
                    fileType = fileType
                )
                if (subtitleExts.contains(leaf.ext)) {
                    val candidate = SubtitleMatchSupport.inferCandidate(path, leaf.url)
                    if (candidate != null) subtitleCandidates += candidate to leaf
                }
            }
        }

        fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
            val leafFiles = nodes.mapNotNull { node ->
                val children = node.children.orEmpty()
                val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
                val url = node.mediaDownloadUrl ?: node.streamUrl
                if (children.isNotEmpty() || url.isNullOrBlank()) return@mapNotNull null
                val safeTitle = sanitize(rawTitle)
                val fileType = treeFileTypeForNode(rawTitle, url, node.type)
                if (!isLibraryResourceSavableTreeFileType(fileType)) return@mapNotNull null
                LeafFile(
                    rawTitle = rawTitle,
                    safeTitle = safeTitle,
                    url = url,
                    duration = node.duration,
                    fileType = fileType
                )
            }

            leafFiles.forEach { leaf ->
                val path = if (parentPath.isBlank()) leaf.safeTitle else "$parentPath/${leaf.safeTitle}"
                val relDir = path.substringBeforeLast('/', "")
                val group = relDir
                val subsRaw = if (leaf.fileType == TreeFileType.Audio) {
                    val matched = SubtitleMatchSupport.matchBest(path.substringBeforeLast('.'), subtitleCandidates.map { it.first })
                    if (matched != null) {
                        subtitleCandidates.firstOrNull { it.first.sourceRef == matched.sourceRef }?.second?.let { subtitleLeaf ->
                            listOf(RemoteSubtitleSource(url = subtitleLeaf.url, language = matched.language, ext = subtitleLeaf.ext))
                        }.orEmpty()
                    } else {
                        emptyList()
                    }
                } else emptyList()
                val subs = if (leaf.fileType == TreeFileType.Audio && subsRaw.isNotEmpty()) {
                    subsRaw
                } else if (leaf.fileType == TreeFileType.Audio) {
                    OnlineLyricsStore.get(leaf.url)
                } else {
                    emptyList()
                }
                out.add(
                    OnlineSaveLeaf(
                        relativePath = path,
                        title = leaf.safeTitle.substringBeforeLast('.'),
                        url = leaf.url,
                        duration = leaf.duration ?: 0.0,
                        group = group,
                        fileType = leaf.fileType,
                        subtitleSources = subs
                    )
                )
            }

            nodes.forEach { node ->
                val children = node.children.orEmpty()
                if (children.isEmpty()) return@forEach
                val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
                val safeTitle = sanitize(rawTitle)
                val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
                walk(children, path)
            }
        }
        collectSubtitleCandidates(tree, "")
        walk(tree, "")
        return out.map { leaf ->
            if (leaf.fileType != TreeFileType.Audio) {
                return@map leaf
            }
            val matched = SubtitleMatchSupport.matchBest(leaf.relativePath.substringBeforeLast('.'), subtitleCandidates.map { it.first })
            val subtitles = if (matched != null) {
                subtitleCandidates.firstOrNull { it.first.sourceRef == matched.sourceRef }?.second?.let { subtitleLeaf ->
                    listOf(RemoteSubtitleSource(url = subtitleLeaf.url, language = matched.language, ext = subtitleLeaf.ext))
                }.orEmpty()
            } else {
                leaf.subtitleSources
            }
            leaf.copy(subtitleSources = subtitles)
        }
    }

    private fun onlineSaveAlbumDir(album: Album, workKey: String): File {
        val baseDir = File(context.getExternalFilesDir(null), "albums")
        val folderName = safeFolderName(
            album.rjCode.ifBlank { album.workId }.ifBlank { workKey }.ifBlank { album.title }
        )
        return File(baseDir, folderName)
    }

    private fun ensureNoMediaMarkers(dir: File) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            val albumsRoot = File(context.getExternalFilesDir(null), "albums")
            if (!albumsRoot.exists()) albumsRoot.mkdirs()
            val rootMarker = File(albumsRoot, ".nomedia")
            if (!rootMarker.exists()) rootMarker.createNewFile()
            val marker = File(dir, ".nomedia")
            if (!marker.exists()) marker.createNewFile()
        }
    }

    private fun normalizeRj(raw: String): String {
        return Regex("""RJ\d+""", RegexOption.IGNORE_CASE).find(raw)?.value?.uppercase().orEmpty()
    }

    private suspend fun loadLocalTracks(albumEntity: AlbumEntity): List<Track> {
        val fromDb = runCatching { trackDao.getTracksForAlbumOnce(albumEntity.id) }.getOrDefault(emptyList())
        if (fromDb.isNotEmpty()) {
            return fromDb.map { it.toDomain() }
        }
        return emptyList()
    }

    private fun scanDocumentTreeTracks(albumId: Long, albumPath: String): List<Track> {
        val uri = runCatching { android.net.Uri.parse(albumPath) }.getOrNull() ?: return emptyList()
        val resolver = context.contentResolver
        val treeId = runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: ""
        val documentId = runCatching { android.provider.DocumentsContract.getDocumentId(uri) }.getOrNull() ?: treeId
        val treeUri = if (treeId.isNotBlank()) android.provider.DocumentsContract.buildTreeDocumentUri(uri.authority, treeId) else uri
        
        val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val result = mutableListOf<Track>()
        
        fun walk(parentDocId: String, parentRelativePath: String) {
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            
            runCatching {
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                    
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIndex)
                        val name = cursor.getString(nameIndex).orEmpty()
                        val mime = cursor.getString(mimeIndex).orEmpty()
                        
                        if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                            val relPath = if (parentRelativePath.isEmpty()) name else "$parentRelativePath/$name"
                            walk(id, relPath)
                        } else if (audioExtensions.contains(name.substringAfterLast('.', "").lowercase())) {
                            val trackUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                            val group = if (parentRelativePath.contains("/")) parentRelativePath.substringAfterLast('/') else parentRelativePath
                            result.add(
                                Track(
                                    albumId = albumId,
                                    title = name.substringBeforeLast('.'),
                                    path = trackUri.toString(),
                                    duration = 0.0,
                                    group = group,
                                    lyricsRelativePathNoExt = if (parentRelativePath.isEmpty()) {
                                        name.substringBeforeLast('.')
                                    } else {
                                        "$parentRelativePath/${name.substringBeforeLast('.')}"
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
        
        if (documentId.isNotBlank()) {
            walk(documentId, "")
        }
        return result.sortedBy { it.path }
    }

    private fun scanLocalTracks(albumId: Long, albumPath: String): List<Track> {
        val root = File(albumPath)
        if (!root.exists() || !root.isDirectory) return emptyList()
        val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val files = root.walkTopDown()
            .filter { it.isFile && audioExtensions.contains(it.extension.lowercase()) }
            .toList()
            .sortedBy { it.absolutePath }
        return files.map { file ->
            val parent = file.parentFile
            val group = if (parent != null && parent != root) parent.name else ""
            Track(
                albumId = albumId,
                title = file.nameWithoutExtension,
                path = file.absolutePath,
                duration = 0.0,
                group = group,
                lyricsRelativePathNoExt = deriveLyricsRelativePathNoExt(file.absolutePath, listOf(root.absolutePath))
            )
        }
    }

    private fun TrackEntity.toDomain(): Track {
        return Track(
            id = id,
            albumId = albumId,
            title = titleForDisplay,
            path = path,
            duration = duration,
            group = group,
            lyricsRelativePathNoExt = ""
        )
    }

    private fun safeFolderName(input: String): String {
        return input.trim().ifEmpty { "album" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    private fun safeFileName(input: String): String {
        return input.trim().ifEmpty { "track" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    private fun enqueueRemoteTreeSelectionDownload(
        album: Album,
        tree: List<AsmrOneTrackNodeResponse>,
        selectedLeafPaths: Set<String>,
        relativeBaseDir: String
    ) {
        if (tree.isEmpty()) return

        val leaves = flattenAsmrOneLeafDownloads(tree)
        val subExts = setOf("lrc", "srt", "vtt")

        val initialSelected = if (selectedLeafPaths.isEmpty()) {
            leaves.filter { leaf ->
                isDownloadableTreeFileType(treeFileTypeForName(leaf.relativePath))
            }
        } else {
            leaves.filter { selectedLeafPaths.contains(it.relativePath) }
        }

        if (initialSelected.isEmpty()) return

        val selected = linkedSetOf<AsmrOneLeafDownload>()
        initialSelected.forEach { media ->
            selected.add(media)
            val mediaRelPath = media.relativePath
            val mediaBase = mediaRelPath.substringBeforeLast('.')
            val mediaDir = mediaRelPath.substringBeforeLast('/', "")

            leaves.forEach subtitleLoop@{ potentialSub ->
                val subRelPath = potentialSub.relativePath
                val subExt = subRelPath.substringAfterLast('.').lowercase()
                if (!subExts.contains(subExt)) return@subtitleLoop

                val subDir = subRelPath.substringBeforeLast('/', "")
                if (subDir != mediaDir) return@subtitleLoop

                val subBase = subRelPath.substringBeforeLast('.')
                val isMatch = subBase.equals(mediaBase, ignoreCase = true) ||
                    subBase.startsWith("$mediaBase.", ignoreCase = true)
                if (isMatch) selected.add(potentialSub)
            }
        }

        enqueueRemoteLeafDownloads(
            album = album,
            selected = selected,
            relativeBaseDir = relativeBaseDir,
            includeCover = true
        )
    }

    private fun enqueueRemoteLeafDownloads(
        album: Album,
        selected: Collection<AsmrOneLeafDownload>,
        relativeBaseDir: String,
        includeCover: Boolean
    ) {
        if (selected.isEmpty()) return

        val rjOrWorkId = album.rjCode.ifBlank { album.workId }
        val folderName = safeFolderName(rjOrWorkId.ifBlank { album.title })
        val baseDir = File(context.getExternalFilesDir(null), "albums")
        val albumDir = File(baseDir, folderName)
        val normalizedBaseDir = relativeBaseDir.trim().trim('/', '\\')
        val targetRootDir = if (normalizedBaseDir.isBlank()) albumDir else File(albumDir, normalizedBaseDir)
        if (!targetRootDir.exists()) targetRootDir.mkdirs()

        val taskKey = buildRemoteDownloadTaskKey(folderName, normalizedBaseDir)
        val taskSubtitle = album.title

        if (includeCover) {
            enqueueRemoteDownloadCover(
                album = album,
                targetRootDir = targetRootDir,
                taskKey = taskKey,
                taskSubtitle = taskSubtitle
            )
        }

        var skipped = 0
        var enqueued = 0
        selected.forEach { item ->
            val relPath = item.relativePath.replace('\\', '/')
            val rawName = relPath.substringAfterLast('/', relPath)

            val url = item.url.trim()
            if (!url.startsWith("http", ignoreCase = true)) return@forEach

            val baseName = safeFileName(rawName)
            val extFromName = baseName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            val extFromUrl = url.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..6 }
            val fileName = if (extFromName != null) {
                baseName
            } else {
                val defaultExt = when (treeFileTypeForName(relPath)) {
                    TreeFileType.Video -> "mp4"
                    TreeFileType.Image -> "jpg"
                    TreeFileType.Pdf -> "pdf"
                    TreeFileType.Archive -> "zip"
                    TreeFileType.Document -> "doc"
                    TreeFileType.Spreadsheet -> "csv"
                    TreeFileType.Presentation -> "ppt"
                    TreeFileType.Code -> "txt"
                    TreeFileType.Ebook -> "epub"
                    TreeFileType.Font -> "ttf"
                    TreeFileType.AppPackage -> "apk"
                    else -> "mp3"
                }
                val ext = extFromUrl ?: defaultExt
                "$baseName.$ext"
            }

            val relDir = relPath.substringBeforeLast('/', "")
            val dir = if (relDir.isBlank()) targetRootDir else File(targetRootDir, relDir)
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, fileName)
            if (outFile.exists() && outFile.isFile) {
                skipped += 1
                return@forEach
            }

            val relativeFilePath = buildString {
                if (relDir.isNotBlank()) {
                    append(relDir)
                    append('/')
                }
                append(fileName)
            }

            downloadManager.enqueueDownload(
                url = url,
                fileName = fileName,
                targetDir = dir.absolutePath,
                taskRootDir = targetRootDir.absolutePath,
                relativePath = relativeFilePath,
                taskSubtitle = taskSubtitle,
                tags = listOf(taskKey),
                albumTitle = album.title,
                albumCircle = album.circle,
                albumCv = album.cv,
                albumTagsCsv = album.tags.joinToString(","),
                albumCoverUrl = album.coverUrl,
                albumWorkId = album.workId,
                albumRjCode = album.rjCode
            )
            enqueued += 1
        }

        if (skipped > 0 && enqueued == 0) {
            messageManager.showInfo("本地已存在，已跳过下载（${skipped}项）")
        } else if (skipped > 0) {
            messageManager.showInfo("已加入下载队列（${enqueued}项），跳过已存在（${skipped}项）")
        } else if (enqueued > 0) {
            messageManager.showInfo("正在加入下载队列（${enqueued}项）")
        }
    }

    private fun buildRemoteDownloadTaskKey(folderName: String, relativeBaseDir: String): String {
        return if (relativeBaseDir.isBlank()) {
            "album:$folderName"
        } else {
            "album:$folderName/$relativeBaseDir"
        }
    }

    private fun enqueueRemoteDownloadCover(
        album: Album,
        targetRootDir: File,
        taskKey: String,
        taskSubtitle: String
    ) {
        val coverUrl = album.coverUrl.trim()
        if (!coverUrl.startsWith("http", ignoreCase = true)) return

        val ext = coverUrl.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..5 } ?: "jpg"
        val coverFileName = "cover.$ext"
        downloadManager.enqueueDownload(
            url = coverUrl,
            fileName = coverFileName,
            targetDir = targetRootDir.absolutePath,
            taskRootDir = targetRootDir.absolutePath,
            relativePath = coverFileName,
            taskSubtitle = taskSubtitle,
            tags = listOf(taskKey),
            albumTitle = album.title,
            albumCircle = album.circle,
            albumCv = album.cv,
            albumTagsCsv = album.tags.joinToString(","),
            albumCoverUrl = album.coverUrl,
            albumWorkId = album.workId,
            albumRjCode = album.rjCode
        )
    }

    override fun onCleared() {
        cancelActiveLoads()
        cancelCloudSyncSelection()
        super.onCleared()
    }

    private companion object {
        val RJ_CODE_REGEX = Regex("""RJ\d{6,}""")
        const val MAX_RECOMMENDATION_ASMR_ONE_ENRICH = 12
        const val RECOMMENDATION_ASMR_ONE_ENRICH_CONCURRENCY = 4
    }
}
