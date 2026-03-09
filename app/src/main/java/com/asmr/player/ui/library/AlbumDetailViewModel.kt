package com.asmr.player.ui.library

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.dao.AlbumDao
import com.asmr.player.data.local.db.dao.TagWithCount
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.entities.RemoteSubtitleSourceEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.db.entities.TagEntity
import com.asmr.player.data.local.db.entities.TagSource
import com.asmr.player.data.local.db.entities.TrackTagEntity
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.crawler.AsmrOneCrawler
import com.asmr.player.data.remote.dlsite.DlsitePlayWorkClient
import com.asmr.player.data.remote.dlsite.DlsitePlayTreeResult
import com.asmr.player.data.remote.dlsite.DlsiteLanguageEdition
import com.asmr.player.data.remote.dlsite.DlsiteProductInfoClient
import com.asmr.player.data.remote.download.DownloadManager
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
import com.asmr.player.data.lyrics.LyricsLoader
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.util.OnlineLyricsStore
import com.asmr.player.util.RemoteSubtitleSource
import com.asmr.player.util.SyncCoordinator
import com.asmr.player.util.TrackKeyNormalizer
import com.asmr.player.util.DlsiteWorkNo
import com.asmr.player.data.remote.crawler.asmrOneWorkMatchesRj
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

import com.asmr.player.util.MessageManager
import com.asmr.player.util.TagNormalizer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Named
import com.asmr.player.BuildConfig
import com.asmr.player.work.AlbumCoverThumbWorker

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val database: AppDatabase,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val asmrOneCrawler: AsmrOneCrawler,
    private val dlsiteScraper: DLSiteScraper,
    private val dlsiteProductInfoClient: DlsiteProductInfoClient,
    private val dlsitePlayWorkClient: DlsitePlayWorkClient,
    private val downloadManager: DownloadManager,
    private val lyricsLoader: LyricsLoader,
    private val syncCoordinator: SyncCoordinator,
    @Named("image") private val imageOkHttpClient: OkHttpClient,
    val messageManager: MessageManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlbumDetailUiState>(AlbumDetailUiState.Loading)
    val uiState = _uiState.asStateFlow()

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
    private var localTracksObserveJob: Job? = null
    private val asmrOneAttemptedRj = linkedSetOf<String>()
    private val asmrOneCollectedCache = linkedMapOf<String, Pair<Long, Boolean?>>()
    private val asmrOneResolvedCache = linkedMapOf<String, Pair<Long, Pair<String, Int?>>>()
    private val asmrOneTracksCache = linkedMapOf<String, Pair<Long, List<AsmrOneTrackNodeResponse>>>()
    private val autoFallbackToJpnOnceByKey = linkedSetOf<String>()

    private val treeExpandedByKey = linkedMapOf<String, List<String>>()
    private val treeInitializedKeys = linkedSetOf<String>()
    private val listScrollByKey = linkedMapOf<String, Pair<Int, Int>>()

    private suspend fun isAsmrOneCollected(rj: String, timeoutMs: Long = 1_200L): Boolean? {
        val key = rj.trim().uppercase()
        if (key.isBlank()) return null
        val now = SystemClock.elapsedRealtime()
        val cached = asmrOneCollectedCache[key]
        if (cached != null && (now - cached.first) <= 60_000L) return cached.second
        val result = runCatching { asmrOneCrawler.hasWorkFast(key, timeoutMs = timeoutMs) }.getOrNull()
        asmrOneCollectedCache[key] = now to result
        if (asmrOneCollectedCache.size > 300) {
            val firstKey = asmrOneCollectedCache.entries.firstOrNull()?.key
            if (firstKey != null) asmrOneCollectedCache.remove(firstKey)
        }
        return result
    }

    private fun asmrOneTracksCacheKey(site: Int?, workId: String): String {
        return "${site ?: 0}|${workId.trim()}"
    }

    private suspend fun resolveAsmrOneWork(
        workNo: String,
        timeoutMs: Long = 1_800L
    ): Pair<String, Int?>? {
        val key = workNo.trim().uppercase()
        if (key.isBlank()) return null
        val now = SystemClock.elapsedRealtime()
        val cached = asmrOneResolvedCache[key]
        if (cached != null && (now - cached.first) <= 10 * 60_000L) return cached.second

        val result = runCatching { withTimeoutOrNull(timeoutMs) { asmrOneCrawler.searchWithTrace(key) } }.getOrNull()
        val found = result
            ?.response
            ?.works
            ?.firstOrNull { w -> asmrOneWorkMatchesRj(w, key) }
        val workId = found?.id?.toString()?.trim().orEmpty()
        if (workId.isBlank()) return null
        val site = result?.trace?.takeIf { it.fallbackUsed }?.fallbackSite
        val resolved = workId to site
        asmrOneResolvedCache[key] = now to resolved
        if (asmrOneResolvedCache.size > 500) {
            val firstKey = asmrOneResolvedCache.entries.firstOrNull()?.key
            if (firstKey != null) asmrOneResolvedCache.remove(firstKey)
        }
        return resolved
    }

    private suspend fun getAsmrOneTracksCached(site: Int?, workId: String): List<AsmrOneTrackNodeResponse> {
        val normalizedId = workId.trim()
        if (normalizedId.isBlank()) return emptyList()
        val cacheKey = asmrOneTracksCacheKey(site, normalizedId)
        val now = SystemClock.elapsedRealtime()
        val cached = asmrOneTracksCache[cacheKey]
        if (cached != null && (now - cached.first) <= 10 * 60_000L) return cached.second
        val tree = runCatching {
            if (site != null) asmrOneCrawler.getTracksFromSite(site, normalizedId) else asmrOneCrawler.getTracks(normalizedId)
        }.getOrDefault(emptyList())
        asmrOneTracksCache[cacheKey] = now to tree
        if (asmrOneTracksCache.size > 200) {
            val firstKey = asmrOneTracksCache.entries.firstOrNull()?.key
            if (firstKey != null) asmrOneTracksCache.remove(firstKey)
        }
        return tree
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
    }

    suspend fun loadOnlineTextPreview(url: String): String? {
        val u = url.trim()
        if (u.isBlank()) return null
        return lyricsLoader.fetchTextForPreview(u)
    }

    fun getListScrollPosition(stateKey: String): Pair<Int, Int> {
        return listScrollByKey[stateKey] ?: (0 to 0)
    }

    fun persistListScrollPosition(stateKey: String, index: Int, offset: Int) {
        if (stateKey.isBlank()) return
        listScrollByKey[stateKey] = (index.coerceAtLeast(0) to offset.coerceAtLeast(0))
    }

    fun loadAlbum(albumId: Long?, rjCode: String?, force: Boolean = false) {
        val normalizedRj = rjCode?.trim().orEmpty().uppercase()
        val key = if (normalizedRj.isNotBlank()) "rj:$normalizedRj" else "id:${albumId ?: 0L}"
        val current = _uiState.value as? AlbumDetailUiState.Success
        if (!force && current != null && lastAlbumKey == key) return
        lastAlbumKey = key
        viewModelScope.launch {
            _uiState.value = AlbumDetailUiState.Loading
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

                val displayAlbum = localAlbum ?: Album(
                    title = rj.ifBlank { "专辑" },
                    path = "",
                    workId = localAlbum?.workId.orEmpty(),
                    rjCode = rj
                )
                _uiState.value = AlbumDetailUiState.Success(
                    model = AlbumDetailModel(
                        baseRjCode = rj,
                        rjCode = rj,
                        displayAlbum = displayAlbum,
                        localAlbum = localAlbum,
                        dlsiteInfo = null,
                        dlsiteGalleryUrls = emptyList(),
                        dlsiteTrialTracks = emptyList(),
                        dlsiteRecommendations = DlsiteRecommendations(),
                        dlsiteWorkno = rj,
                        dlsitePlayWorkno = "",
                        dlsiteEditions = defaultDlsiteEditions(rj),
                        dlsiteSelectedLang = "JPN",
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
            messageManager.showError("请输入有效 RJ 号（如 RJ123456）")
            return
        }
        val current = _uiState.value as? AlbumDetailUiState.Success
        val local = current?.model?.localAlbum
        if (local == null || local.id <= 0L) {
            messageManager.showError("仅支持本地库专辑手动绑定 RJ")
            return
        }

        viewModelScope.launch {
            val token = syncCoordinator.tryBegin("绑定RJ并云同步") ?: run {
                val current = syncCoordinator.state.value?.label
                if (current.isNullOrBlank()) {
                    messageManager.showInfo("正在执行同步任务，请稍后再试")
                } else {
                    messageManager.showInfo("正在执行：$current，请等待完成或取消后再同步")
                }
                return@launch
            }
            _uiState.value = AlbumDetailUiState.Loading
            try {
                val entity = withContext(Dispatchers.IO) { albumDao.getAlbumById(local.id) } ?: run {
                    messageManager.showError("专辑不存在")
                    loadAlbum(local.id, normalized, force = true)
                    return@launch
                }

                val trimmedWorkId = entity.workId.trim()
                val updatedWorkId = when {
                    trimmedWorkId.isBlank() -> normalized
                    Regex("""RJ\d+""", RegexOption.IGNORE_CASE).matches(trimmedWorkId) -> normalized
                    else -> entity.workId
                }
                withContext(Dispatchers.IO) {
                    albumDao.updateAlbum(entity.copy(workId = updatedWorkId, rjCode = normalized))
                }

                val lang = current.model.dlsiteSelectedLang.trim().uppercase().ifBlank { "JPN" }
                val locale = dlsiteLocaleForLang(lang)
                val details = withContext(Dispatchers.IO) { dlsiteScraper.getDetails(normalized, locale = locale) }
                if (details == null) {
                    messageManager.showError("同步失败：未找到专辑信息")
                    loadAlbum(local.id, normalized, force = true)
                    return@launch
                }

                val oldTitle = entity.title.trim()
                val newTitle = details.title.trim()
                val finalTitle = when {
                    oldTitle.isNotBlank() && newTitle.isBlank() -> oldTitle
                    oldTitle.isNotBlank() && newTitle.isNotBlank() && oldTitle.contains(newTitle) && oldTitle.length > newTitle.length -> oldTitle
                    else -> newTitle.ifBlank { oldTitle }
                }

                withContext(Dispatchers.IO) {
                    albumDao.updateAlbum(
                        entity.copy(
                            title = finalTitle,
                            circle = details.circle.ifBlank { entity.circle },
                            cv = details.cv.ifBlank { entity.cv },
                            tags = if (details.tags.isNotEmpty()) details.tags.joinToString(",") else entity.tags,
                            coverUrl = details.coverUrl.ifBlank { entity.coverUrl },
                            description = details.description.ifBlank { entity.description },
                            workId = updatedWorkId,
                            rjCode = normalized
                        )
                    )
                }
                messageManager.showSuccess("已绑定 $normalized 并完成云同步")
                loadAlbum(local.id, normalized, force = true)
            } catch (e: Exception) {
                messageManager.showError("同步异常：${e.message}")
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
                }
                enqueueAlbumCoverThumbWork(local.id)
                messageManager.showSuccess("已设置封面")
                val rj = current.model.rjCode.ifBlank { local.rjCode.ifBlank { local.workId } }
                loadAlbum(local.id, rj, force = true)
            } catch (e: Exception) {
                messageManager.showError("设置封面失败：${e.message}")
            }
        }
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

    private fun defaultDlsiteEditions(rj: String): List<DlsiteLanguageEdition> {
        val clean = rj.trim().uppercase()
        if (clean.isBlank()) return emptyList()
        return listOf(DlsiteLanguageEdition(workno = clean, lang = "JPN", label = "日本語", displayOrder = 1))
    }

    private fun fetchDlsiteEditions(baseRj: String) {
        val clean = baseRj.trim().uppercase()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val editions = runCatching { dlsiteProductInfoClient.fetchLanguageEditions(clean) }.getOrDefault(emptyList())
            val merged = buildList {
                val hasJpn = editions.any { it.lang == "JPN" }
                if (!hasJpn) addAll(defaultDlsiteEditions(clean))
                addAll(editions)
            }.distinctBy { it.lang }.sortedWith(compareBy({ it.displayOrder }, { it.lang }))

            val current = _uiState.value as? AlbumDetailUiState.Success ?: return@launch
            if (!current.model.baseRjCode.equals(clean, ignoreCase = true)) return@launch

            val isFirstLangFetch = current.model.dlsiteEditions.size <= 1
            val currentSelectedLang = current.model.dlsiteSelectedLang.trim().uppercase().ifBlank { "JPN" }
            if (isFirstLangFetch && currentSelectedLang == "JPN") {
                _uiState.value = AlbumDetailUiState.Success(model = current.model.copy(dlsiteEditions = merged))
                val hasHansEdition = merged.any { it.lang.equals("CHI_HANS", ignoreCase = true) }
                val hasHantEdition = merged.any { it.lang.equals("CHI_HANT", ignoreCase = true) }
                val jpnWorkno = merged.firstOrNull { it.lang.equals("JPN", ignoreCase = true) }
                    ?.workno
                    ?.trim()
                    ?.uppercase()
                    .orEmpty()
                    .ifBlank { clean }

                val resolved = resolveAsmrOneWork(jpnWorkno, timeoutMs = 1_200L) ?: resolveAsmrOneWork(clean, timeoutMs = 1_200L)
                val picked = if (resolved != null) {
                    val (workId, site) = resolved
                    val details = withTimeoutOrNull(1_500L) {
                        if (site != null) asmrOneCrawler.getDetailsFromSite(site, workId) else asmrOneCrawler.getDetails(workId)
                    }
                    val other = details?.other_language_editions_in_db.orEmpty()
                    val hasHans = other.any { it.lang.orEmpty().contains("简体") || it.lang.orEmpty().contains("簡体") }
                    val hasHant = other.any { it.lang.orEmpty().contains("繁体") || it.lang.orEmpty().contains("繁體") }
                    when {
                        hasHansEdition && hasHans -> "CHI_HANS"
                        hasHantEdition && hasHant -> "CHI_HANT"
                        else -> "JPN"
                    }
                } else {
                    when {
                        hasHansEdition -> "CHI_HANS"
                        hasHantEdition -> "CHI_HANT"
                        else -> "JPN"
                    }
                }
                if (picked != "JPN") {
                    selectDlsiteLanguage(picked)
                }
                return@launch
            }

            val selectedByWorkno = merged.firstOrNull { it.workno.trim().equals(clean, ignoreCase = true) }
            val selectedLang = current.model.dlsiteSelectedLang.trim().uppercase().ifBlank { "JPN" }
            val selected = selectedByWorkno
                ?: merged.firstOrNull { it.lang == selectedLang }
                ?: merged.firstOrNull { it.lang == "JPN" }
                ?: merged.firstOrNull()
            val selectedWorkno = selected?.workno?.trim()?.uppercase().orEmpty().ifBlank { clean }
            _uiState.value = AlbumDetailUiState.Success(
                model = current.model.copy(
                    dlsiteEditions = merged,
                    dlsiteSelectedLang = selected?.lang ?: "JPN",
                    dlsiteWorkno = selectedWorkno
                )
            )
        }
    }

    private fun dlsiteLocaleForLang(lang: String): String {
        return when (lang.trim().uppercase()) {
            "CHI_HANS" -> "zh_CN"
            "CHI_HANT" -> "zh_TW"
            else -> "ja_JP"
        }
    }

    private suspend fun enrichRecommendationsWithAsmrOne(recommendations: DlsiteRecommendations): DlsiteRecommendations {
        suspend fun enrich(list: List<DlsiteRecommendedWork>): List<DlsiteRecommendedWork> {
            return list.map { w ->
                val rj = w.rjCode.trim().uppercase()
                val asmr = runCatching { asmrOneCrawler.getDetails(rj) }.getOrNull()
                if (asmr == null) return@map w
                w.copy(
                    title = asmr.title.ifBlank { w.title },
                    coverUrl = asmr.mainCoverUrl.ifBlank { w.coverUrl }
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
        val workno = current.model.dlsiteWorkno.trim().uppercase().ifBlank { current.model.rjCode.trim().uppercase() }
        if (workno.isBlank() || current.model.isLoadingDlsite) return
        
        // 如果还没有拉取过多语言列表，先拉取一次
        if (current.model.dlsiteEditions.size <= 1) {
            fetchDlsiteEditions(current.model.baseRjCode)
        }

        if (current.model.dlsiteInfo != null) return
        
        val token = ++dlsiteLoadToken
        val locale = dlsiteLocaleForLang(current.model.dlsiteSelectedLang)
        viewModelScope.launch {
            _uiState.value = AlbumDetailUiState.Success(model = current.model.copy(isLoadingDlsite = true, isLoadingDlsiteTrial = false))
            try {
                val (dlsiteWorkInfo, dlsiteRecommendationsFromV2, dlsiteTrialTracks) = coroutineScope {
                    val infoDeferred = async { runCatching { dlsiteScraper.getWorkInfo(workno, locale = locale) }.getOrNull() }
                    val recDeferred = async {
                        runCatching { dlsiteScraper.getRecommendationsDetailV2(workno, locale = locale) }
                            .getOrDefault(DlsiteRecommendations())
                    }
                    val tracksDeferred = async {
                        runCatching { dlsiteScraper.getTracks(workno, locale = locale) }.getOrDefault(emptyList())
                    }
                    Triple(infoDeferred.await(), recDeferred.await(), tracksDeferred.await())
                }

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
                val displayAlbum = buildDisplayAlbum(updated.rjCode, updated.localAlbum, dlsiteInfo, updated.asmrOneWorkId)
                _uiState.value = AlbumDetailUiState.Success(
                    model = updated.copy(
                        displayAlbum = displayAlbum,
                        dlsiteInfo = dlsiteInfo,
                        dlsiteGalleryUrls = dlsiteGalleryUrls,
                        dlsiteTrialTracks = dlsiteTrialTracks,
                        dlsiteRecommendations = dlsiteRecommendationsRaw,
                        isLoadingDlsite = false
                    )
                )

                viewModelScope.launch {
                    val current2 = _uiState.value as? AlbumDetailUiState.Success ?: return@launch
                    if (token != dlsiteLoadToken) return@launch
                    val recs = current2.model.dlsiteRecommendations
                    if (recs.alsoBoughtWorks.isEmpty() && recs.circleWorks.isEmpty() && recs.sameVoiceWorks.isEmpty()) return@launch
                    val enriched = runCatching { enrichRecommendationsWithAsmrOne(recs) }
                        .getOrNull() ?: return@launch
                    val updated2 = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                    if (token != dlsiteLoadToken) return@launch
                    _uiState.value = AlbumDetailUiState.Success(model = updated2.copy(dlsiteRecommendations = enriched))
                }
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

        dlsiteLoadToken++
        asmrOneLoadToken++
        asmrOneAttemptedRj.clear()
        _uiState.value = AlbumDetailUiState.Success(
            model = current.model.copy(
                dlsiteSelectedLang = target?.lang ?: normalized,
                dlsiteWorkno = workno,
                dlsitePlayWorkno = "",
                rjCode = workno,
                dlsiteInfo = null,
                dlsiteGalleryUrls = emptyList(),
                dlsiteTrialTracks = emptyList(),
                dlsiteRecommendations = DlsiteRecommendations(),
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
            val local = runCatching { loadLocalAlbumByRj(workno) }.getOrNull()
            val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
            if (!updated.rjCode.equals(workno, ignoreCase = true)) return@launch
            val displayAlbum = buildDisplayAlbum(updated.rjCode, local, updated.dlsiteInfo, updated.asmrOneWorkId)
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

        val oldWorkId = current.model.asmrOneWorkId?.trim().orEmpty()
        if (oldWorkId.isNotBlank()) {
            val cacheKey = asmrOneTracksCacheKey(current.model.asmrOneSite, oldWorkId)
            asmrOneTracksCache.remove(cacheKey)
        }

        _uiState.value = AlbumDetailUiState.Success(
            model = current.model.copy(
                asmrOneWorkId = null,
                asmrOneSite = null,
                asmrOneTree = emptyList(),
                isLoadingAsmrOne = false
            )
        )
        ensureAsmrOneLoaded()
    }

    fun refreshDlsiteTrialSection() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val workno = current.model.dlsiteWorkno.trim().uppercase().ifBlank { current.model.rjCode.trim().uppercase() }
        if (workno.isBlank() || current.model.isLoadingDlsite || current.model.isLoadingDlsiteTrial) return

        val token = ++dlsiteTrialLoadToken
        val locale = dlsiteLocaleForLang(current.model.dlsiteSelectedLang)
        viewModelScope.launch {
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
            } catch (e: Exception) {
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                val updatedWorkno = updated.dlsiteWorkno.trim().uppercase().ifBlank { updated.rjCode.trim().uppercase() }
                if (token != dlsiteTrialLoadToken || !updatedWorkno.equals(workno, ignoreCase = true)) return@launch
                val errMsg = e.message?.trim().orEmpty().ifBlank { "未知错误" }
                messageManager.showError("试听刷新失败：$errMsg")
                _uiState.value = AlbumDetailUiState.Success(model = updated.copy(isLoadingDlsiteTrial = false))
            }
        }
    }

    fun ensureAsmrOneLoaded() {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val keyRj = current.model.rjCode.trim().uppercase()
        if (
            keyRj.isBlank() ||
            current.model.asmrOneTree.isNotEmpty() ||
            current.model.isLoadingAsmrOne ||
            asmrOneAttemptedRj.contains(keyRj)
        ) return
        asmrOneAttemptedRj.add(keyRj)
        val token = ++asmrOneLoadToken
        viewModelScope.launch {
            val latestBefore = _uiState.value as? AlbumDetailUiState.Success ?: return@launch
            val latestKey = latestBefore.model.rjCode.trim().uppercase()
            if (!latestKey.equals(keyRj, ignoreCase = true)) {
                asmrOneAttemptedRj.remove(keyRj)
                return@launch
            }
            _uiState.value = AlbumDetailUiState.Success(model = latestBefore.model.copy(isLoadingAsmrOne = true))
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

                val collected = isAsmrOneCollected(originalRj, timeoutMs = 1_200L)
                if (collected == false) {
                    val baseKey = originalRj
                    val preferred = listOf("CHI_HANS", "CHI_HANT", "JPN")
                    val candidates = preferred.mapNotNull { lang ->
                        val workno = latest.dlsiteEditions.firstOrNull { it.lang.equals(lang, ignoreCase = true) }
                            ?.workno
                            ?.trim()
                            ?.uppercase()
                            .orEmpty()
                            .ifBlank {
                                if (lang == "JPN") baseKey else ""
                            }
                        if (workno.isBlank() || workno.equals(keyRj, ignoreCase = true)) return@mapNotNull null
                        lang to workno
                    }
                    val results = coroutineScope {
                        candidates.map { (lang, workno) ->
                            async {
                                lang to isAsmrOneCollected(workno, timeoutMs = 1_200L)
                            }
                        }.mapNotNull { runCatching { it.await() }.getOrNull() }.toMap()
                    }
                    val fallbackLang = preferred.firstOrNull { lang -> results[lang] == true }
                    val fallbackKey = "base:$baseKey|cur:$keyRj|to:${fallbackLang.orEmpty()}"
                    if (!fallbackLang.isNullOrBlank() && !autoFallbackToJpnOnceByKey.contains(fallbackKey)) {
                        autoFallbackToJpnOnceByKey.add(fallbackKey)
                        val updated0 = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                        val updatedKey0 = updated0.rjCode.trim().uppercase()
                        if (updatedKey0.equals(keyRj, ignoreCase = true) && updated0.isLoadingAsmrOne) {
                            _uiState.value = AlbumDetailUiState.Success(model = updated0.copy(isLoadingAsmrOne = false))
                        }
                        selectDlsiteLanguage(fallbackLang)
                        return@launch
                    }
                    val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                    val updatedKey = updated.rjCode.trim().uppercase()
                    if (updatedKey.equals(keyRj, ignoreCase = true) && updated.isLoadingAsmrOne) {
                        _uiState.value = AlbumDetailUiState.Success(model = updated.copy(isLoadingAsmrOne = false))
                    }
                    return@launch
                }

                val resolvedOriginal = resolveAsmrOneWork(originalRj)
                    ?: resolveAsmrOneWork(keyRj)
                    ?: run {
                        val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                        if (token != asmrOneLoadToken) return@launch
                        asmrOneAttemptedRj.remove(keyRj)
                        if (updated.rjCode.trim().uppercase().equals(keyRj, ignoreCase = true) && updated.isLoadingAsmrOne) {
                            _uiState.value = AlbumDetailUiState.Success(model = updated.copy(isLoadingAsmrOne = false))
                        }
                        return@launch
                    }
                val (originalWorkId, site) = resolvedOriginal

                val originalDetails = runCatching {
                    if (site != null) asmrOneCrawler.getDetailsFromSite(site, originalWorkId) else asmrOneCrawler.getDetails(originalWorkId)
                }.getOrNull()

                val otherId = if (selectedLang == "JPN") {
                    null
                } else {
                    val other = originalDetails?.other_language_editions_in_db.orEmpty()
                    other.firstOrNull { it.source_id.orEmpty().trim().equals(keyRj, ignoreCase = true) }?.id
                        ?: when (selectedLang) {
                            "CHI_HANS" -> other.firstOrNull { it.lang.orEmpty().contains("简体") }?.id
                            "CHI_HANT" -> other.firstOrNull { it.lang.orEmpty().contains("繁體") }?.id
                            "KO_KR" -> other.firstOrNull { it.lang.orEmpty().contains("韩") || it.lang.orEmpty().contains("韓") }?.id
                            else -> null
                        }
                }

                val finalWorkId = otherId?.toString()?.trim().orEmpty().ifBlank { originalWorkId }
                val tree = getAsmrOneTracksCached(site, finalWorkId).ifEmpty {
                    if (finalWorkId != originalWorkId) getAsmrOneTracksCached(site, originalWorkId) else emptyList()
                }
                val workId = finalWorkId
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                if (token != asmrOneLoadToken) {
                    asmrOneAttemptedRj.remove(keyRj)
                    val updatedKey = updated.rjCode.trim().uppercase()
                    if (updatedKey.equals(keyRj, ignoreCase = true) && updated.isLoadingAsmrOne) {
                        _uiState.value = AlbumDetailUiState.Success(model = updated.copy(isLoadingAsmrOne = false))
                    }
                    return@launch
                }
                if (workId.isBlank()) {
                    asmrOneAttemptedRj.remove(keyRj)
                    val updatedKey = updated.rjCode.trim().uppercase()
                    if (updatedKey.equals(keyRj, ignoreCase = true) && updated.isLoadingAsmrOne) {
                        _uiState.value = AlbumDetailUiState.Success(model = updated.copy(isLoadingAsmrOne = false))
                    }
                    return@launch
                }
                val displayAlbum = buildDisplayAlbum(updated.rjCode, updated.localAlbum, updated.dlsiteInfo, workId)
                _uiState.value = AlbumDetailUiState.Success(
                    model = updated.copy(
                        displayAlbum = displayAlbum,
                        asmrOneWorkId = workId,
                        asmrOneSite = site,
                        asmrOneTree = tree,
                        isLoadingAsmrOne = false
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                asmrOneAttemptedRj.remove(keyRj)
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                val updatedKey = updated.rjCode.trim().uppercase()
                if (updatedKey.equals(keyRj, ignoreCase = true) && updated.isLoadingAsmrOne) {
                    _uiState.value = AlbumDetailUiState.Success(model = updated.copy(isLoadingAsmrOne = false))
                }
            } catch (e: Exception) {
                asmrOneAttemptedRj.remove(keyRj)
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                val updatedKey = updated.rjCode.trim().uppercase()
                if (updatedKey.equals(keyRj, ignoreCase = true)) {
                    if (updated.asmrOneTree.isEmpty()) {
                        val errMsg = e.message?.trim().orEmpty().ifBlank { "未知错误" }
                        messageManager.showError("在线资源加载失败：$errMsg")
                    }
                    if (updated.isLoadingAsmrOne) {
                        _uiState.value = AlbumDetailUiState.Success(model = updated.copy(isLoadingAsmrOne = false))
                    }
                } else if (updated.isLoadingAsmrOne) {
                    _uiState.value = AlbumDetailUiState.Success(model = updated.copy(isLoadingAsmrOne = false))
                }
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

        if (candidates0.isEmpty() || current.model.dlsitePlayTree.isNotEmpty() || current.model.isLoadingDlsitePlay) return
        viewModelScope.launch {
            _uiState.value = AlbumDetailUiState.Success(model = current.model.copy(isLoadingDlsitePlay = true))
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
                for (workno in candidates) {
                    val res = try {
                        dlsitePlayWorkClient.fetchPlayableTree(workno)
                    } catch (e: Exception) {
                        lastError = e
                        continue
                    }
                    if (res.tree.isNotEmpty()) {
                        pickedWorkno = workno
                        pickedResult = res
                        break
                    }
                }

                val result = pickedResult ?: DlsitePlayTreeResult(emptyList(), emptyMap())
                result.subtitlesByUrl.forEach { (url, subs) ->
                    if (subs.isNotEmpty()) OnlineLyricsStore.set(url, subs)
                }
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                if (pickedResult == null && lastError != null) {
                    val errMsg = lastError?.message?.trim().orEmpty().ifBlank { "未知错误" }
                    messageManager.showError("DLsite Play 加载失败：$errMsg")
                }
                _uiState.value = AlbumDetailUiState.Success(
                    model = updated.copy(
                        dlsitePlayTree = result.tree,
                        dlsitePlayWorkno = pickedWorkno?.trim().orEmpty(),
                        isLoadingDlsitePlay = false
                    )
                )
            } catch (e: Exception) {
                val updated = (_uiState.value as? AlbumDetailUiState.Success)?.model ?: return@launch
                _uiState.value = AlbumDetailUiState.Success(model = updated.copy(isLoadingDlsitePlay = false))
            }
        }
    }

    private suspend fun fetchAsmrOneWithFallback(
        primaryRj: String,
        fallbackRj: String
    ): Triple<String?, Int?, List<AsmrOneTrackNodeResponse>> {
        suspend fun pickExact(workNo: String): Triple<String?, Int?, List<AsmrOneTrackNodeResponse>> {
            val normalized = workNo.trim().uppercase()
            if (normalized.isBlank()) return Triple(null, null, emptyList())
            val resolved = resolveAsmrOneWork(normalized) ?: return Triple(null, null, emptyList())
            val (workId, site) = resolved
            val tree = getAsmrOneTracksCached(site, workId)
            return Triple(workId, site, tree)
        }

        val primary = pickExact(primaryRj)
        if (primary.first != null) return primary

        val normalizedFallback = fallbackRj.trim()
        val normalizedPrimary = primaryRj.trim()
        if (normalizedFallback.isNotBlank() && !normalizedFallback.equals(normalizedPrimary, ignoreCase = true)) {
            val fallback = pickExact(normalizedFallback)
            if (fallback.first != null) return fallback
        }

        return Triple(null, null, emptyList())
    }

    private fun buildDisplayAlbum(
        rjCode: String,
        localAlbum: Album?,
        dlsiteInfo: Album?,
        asmrOneWorkId: String?
    ): Album {
        val base = dlsiteInfo ?: localAlbum ?: Album(title = rjCode.ifBlank { "专辑" }, path = "")
        return base.copy(
            workId = asmrOneWorkId?.takeIf { it.isNotBlank() } ?: base.workId,
            rjCode = rjCode.ifBlank { base.rjCode.ifBlank { base.workId } }
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
            title = entity.title,
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
        
        messageManager.showInfo("正在加入下载队列：${album.title}")
        
        val folderName = safeFolderName(album.rjCode.ifBlank { album.workId }.ifBlank { album.title })
        val baseDir = File(context.getExternalFilesDir(null), "albums")
        val targetDir = File(baseDir, folderName)
        if (!targetDir.exists()) targetDir.mkdirs()

        val coverUrl = album.coverUrl.trim()
        val coverFileName: String
        if (coverUrl.startsWith("http", ignoreCase = true)) {
            val ext = coverUrl.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..5 } ?: "jpg"
            coverFileName = "cover.$ext"
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
        } else {
            coverFileName = "cover.jpg"
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
        val model = current.model
        val album = model.displayAlbum
        val tree = model.asmrOneTree
        if (tree.isEmpty()) return

        val leaves = flattenAsmrOneLeafDownloads(tree)
        val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val subExts = setOf("lrc", "srt", "vtt")

        val initialSelected = if (selectedLeafPaths.isEmpty()) {
            leaves.filter { leaf ->
                val ext = leaf.relativePath.substringAfterLast('.').lowercase()
                audioExts.contains(ext)
            }
        } else {
            leaves.filter { selectedLeafPaths.contains(it.relativePath) }
        }

        if (initialSelected.isEmpty()) return

        // 自动关联字幕逻辑
        val selected = mutableSetOf<AsmrOneLeafDownload>()
        initialSelected.forEach { audio ->
            selected.add(audio)
            val audioRelPath = audio.relativePath
            val audioBase = audioRelPath.substringBeforeLast('.')
            val audioDir = if (audioRelPath.contains('/')) audioRelPath.substringBeforeLast('/') else ""

            leaves.forEach { potentialSub ->
                val subRelPath = potentialSub.relativePath
                val subExt = subRelPath.substringAfterLast('.').lowercase()
                if (!subExts.contains(subExt)) return@forEach

                val subDir = if (subRelPath.contains('/')) subRelPath.substringBeforeLast('/') else ""
                if (subDir != audioDir) return@forEach

                val subBase = subRelPath.substringBeforeLast('.')
                val isMatch = subBase.equals(audioBase, ignoreCase = true) ||
                    subBase.startsWith("$audioBase.", ignoreCase = true)

                if (isMatch) {
                    selected.add(potentialSub)
                }
            }
        }

        messageManager.showInfo("正在加入下载队列（${selected.size}项）：${album.title}")

        val rjOrWorkId = album.rjCode.ifBlank { album.workId }
        val folderName = safeFolderName(rjOrWorkId.ifBlank { album.title })
        val baseDir = File(context.getExternalFilesDir(null), "albums")
        val targetDir = File(baseDir, folderName)
        if (!targetDir.exists()) targetDir.mkdirs()

        val coverUrl = album.coverUrl.trim()
        val coverFileName: String
        if (coverUrl.startsWith("http", ignoreCase = true)) {
            val ext = coverUrl.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..5 } ?: "jpg"
            coverFileName = "cover.$ext"
            downloadManager.enqueueDownload(
                url = coverUrl,
                fileName = coverFileName,
                targetDir = targetDir.absolutePath,
                taskRootDir = targetDir.absolutePath,
                relativePath = coverFileName,
                tags = listOf("album:$folderName"),
                albumTitle = album.title,
                albumCircle = album.circle,
                albumCv = album.cv,
                albumTagsCsv = album.tags.joinToString(","),
                albumCoverUrl = album.coverUrl,
                albumWorkId = album.workId,
                albumRjCode = album.rjCode
            )
        } else {
            coverFileName = "cover.jpg"
        }

        val existingLocalKeys = LinkedHashSet<String>()
        val existingLocalKeysNoGroup = LinkedHashSet<String>()
        model.localAlbum?.tracks
            ?.filter { !it.path.trim().startsWith("http", ignoreCase = true) }
            ?.forEach { t ->
                existingLocalKeys.add(TrackKeyNormalizer.buildKey(t.title, t.group, null))
                existingLocalKeysNoGroup.add(TrackKeyNormalizer.buildKey(t.title, "", null))
            }

        var skipped = 0
        var enqueued = 0
        selected.forEach { item ->
            val relPath = item.relativePath
            val rawName = relPath.substringAfterLast('/', relPath)
            val relGroup = relPath.substringBeforeLast('/', "").substringAfterLast('/', "")
            val titleFromName = rawName.substringBeforeLast('.', rawName)
            val key = TrackKeyNormalizer.buildKey(titleFromName, relGroup, null)
            val keyNoGroup = TrackKeyNormalizer.buildKey(titleFromName, "", null)
            if (existingLocalKeys.contains(key) || existingLocalKeysNoGroup.contains(keyNoGroup)) {
                skipped += 1
                return@forEach
            }
            val baseName = safeFileName(rawName)
            val extFromName = baseName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            val extFromUrl = item.url.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..6 }
            val fileName = if (extFromName != null) {
                baseName
            } else {
                val ext = extFromUrl ?: "mp3"
                "$baseName.$ext"
            }
            val relDir = relPath.substringBeforeLast('/', "")
            val dir = if (relDir.isBlank()) targetDir else File(targetDir, relDir)
            val url = item.url.trim()
            if (!url.startsWith("http", ignoreCase = true)) return@forEach
            val outFile = File(dir, fileName)
            if (outFile.exists() && outFile.isFile) {
                skipped += 1
                return@forEach
            }
            val relativeFilePath = if (relDir.isBlank()) fileName else "$relDir/$fileName"
            downloadManager.enqueueDownload(
                url = url,
                fileName = fileName,
                targetDir = dir.absolutePath,
                taskRootDir = targetDir.absolutePath,
                relativePath = relativeFilePath,
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
            enqueued += 1
        }
        if (skipped > 0 && enqueued == 0) {
            messageManager.showInfo("本地已存在，已跳过下载（${skipped}项）：${album.title}")
        } else if (skipped > 0) {
            messageManager.showInfo("已加入下载队列（${enqueued}项），跳过已存在（${skipped}项）：${album.title}")
        }
    }

    fun downloadDlsitePlaySelected(selectedLeafPaths: Set<String>) {
        val current = _uiState.value as? AlbumDetailUiState.Success ?: return
        val model = current.model
        val album = model.displayAlbum
        val tree = model.dlsitePlayTree
        if (tree.isEmpty()) return

        val leaves = flattenAsmrOneLeafDownloads(tree)
        val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val subExts = setOf("lrc", "srt", "vtt")

        val initialSelected = if (selectedLeafPaths.isEmpty()) {
            leaves.filter { leaf ->
                val ext = leaf.relativePath.substringAfterLast('.').lowercase()
                audioExts.contains(ext)
            }
        } else {
            leaves.filter { selectedLeafPaths.contains(it.relativePath) }
        }

        if (initialSelected.isEmpty()) return

        val selected = mutableSetOf<AsmrOneLeafDownload>()
        initialSelected.forEach { audio ->
            selected.add(audio)
            val audioRelPath = audio.relativePath
            val audioBase = audioRelPath.substringBeforeLast('.')
            val audioDir = if (audioRelPath.contains('/')) audioRelPath.substringBeforeLast('/') else ""

            leaves.forEach { potentialSub ->
                val subRelPath = potentialSub.relativePath
                val subExt = subRelPath.substringAfterLast('.').lowercase()
                if (!subExts.contains(subExt)) return@forEach

                val subDir = if (subRelPath.contains('/')) subRelPath.substringBeforeLast('/') else ""
                if (subDir != audioDir) return@forEach

                val subBase = subRelPath.substringBeforeLast('.')
                val isMatch = subBase.equals(audioBase, ignoreCase = true) ||
                    subBase.startsWith("$audioBase.", ignoreCase = true)

                if (isMatch) {
                    selected.add(potentialSub)
                }
            }
        }

        messageManager.showInfo("正在加入下载队列（${selected.size}项）：${album.title}")

        val rjOrWorkId = album.rjCode.ifBlank { album.workId }
        val folderName = safeFolderName(rjOrWorkId.ifBlank { album.title })
        val baseDir = File(context.getExternalFilesDir(null), "albums")
        val targetDir = File(baseDir, folderName)
        if (!targetDir.exists()) targetDir.mkdirs()

        val coverUrl = album.coverUrl.trim()
        val coverFileName: String
        if (coverUrl.startsWith("http", ignoreCase = true)) {
            val ext = coverUrl.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..5 } ?: "jpg"
            coverFileName = "cover.$ext"
            downloadManager.enqueueDownload(
                url = coverUrl,
                fileName = coverFileName,
                targetDir = targetDir.absolutePath,
                taskRootDir = targetDir.absolutePath,
                relativePath = coverFileName,
                tags = listOf("album:$folderName"),
                albumTitle = album.title,
                albumCircle = album.circle,
                albumCv = album.cv,
                albumTagsCsv = album.tags.joinToString(","),
                albumCoverUrl = album.coverUrl,
                albumWorkId = album.workId,
                albumRjCode = album.rjCode
            )
        } else {
            coverFileName = "cover.jpg"
        }

        val existingLocalKeys = LinkedHashSet<String>()
        val existingLocalKeysNoGroup = LinkedHashSet<String>()
        model.localAlbum?.tracks
            ?.filter { !it.path.trim().startsWith("http", ignoreCase = true) }
            ?.forEach { t ->
                existingLocalKeys.add(TrackKeyNormalizer.buildKey(t.title, t.group, null))
                existingLocalKeysNoGroup.add(TrackKeyNormalizer.buildKey(t.title, "", null))
            }

        var skipped = 0
        var enqueued = 0
        selected.forEach { item ->
            val relPath = item.relativePath
            val rawName = relPath.substringAfterLast('/', relPath)
            val relGroup = relPath.substringBeforeLast('/', "").substringAfterLast('/', "")
            val titleFromName = rawName.substringBeforeLast('.', rawName)
            val key = TrackKeyNormalizer.buildKey(titleFromName, relGroup, null)
            val keyNoGroup = TrackKeyNormalizer.buildKey(titleFromName, "", null)
            if (existingLocalKeys.contains(key) || existingLocalKeysNoGroup.contains(keyNoGroup)) {
                skipped += 1
                return@forEach
            }
            val baseName = safeFileName(rawName)
            val extFromName = baseName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            val extFromUrl = item.url.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..6 }
            val fileName = if (extFromName != null) {
                baseName
            } else {
                val ext = extFromUrl ?: "mp3"
                "$baseName.$ext"
            }
            val relDir = relPath.substringBeforeLast('/', "")
            val dir = if (relDir.isBlank()) targetDir else File(targetDir, relDir)
            val url = item.url.trim()
            if (!url.startsWith("http", ignoreCase = true)) return@forEach
            val outFile = File(dir, fileName)
            if (outFile.exists() && outFile.isFile) {
                skipped += 1
                return@forEach
            }
            val relativeFilePath = if (relDir.isBlank()) fileName else "$relDir/$fileName"
            downloadManager.enqueueDownload(
                url = url,
                fileName = fileName,
                targetDir = dir.absolutePath,
                taskRootDir = targetDir.absolutePath,
                relativePath = relativeFilePath,
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
            enqueued += 1
        }
        if (skipped > 0 && enqueued == 0) {
            messageManager.showInfo("本地已存在，已跳过下载（${skipped}项）：${album.title}")
        } else if (skipped > 0) {
            messageManager.showInfo("已加入下载队列（${enqueued}项），跳过已存在（${skipped}项）：${album.title}")
        }
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
                        albumId = 0L,
                        coverUrl = "",
                        coverPath = ""
                    )
                }

                val rj = normalizeRj(displayAlbum.rjCode.ifBlank { displayAlbum.workId }.ifBlank { model.rjCode })
                val workKey = rj.ifBlank { displayAlbum.workId.trim().ifBlank { displayAlbum.title.trim() } }
                val onlinePath = "web://rj/${workKey.uppercase()}"

                fun canonicalUrl(url: String): String {
                    return url.trim().substringBefore('#').substringBefore('?')
                }

                var insertedCount = 0
                var albumIdResult = 0L
                var coverUrlResult = ""
                var coverPathResult = ""
                database.withTransaction {
                    val existing = if (workKey.isNotBlank()) {
                        runCatching { albumDao.getAlbumByWorkIdOnce(workKey) }.getOrNull()
                    } else null

                    val tagsCsv = displayAlbum.tags.joinToString(",")
                    val entity = AlbumEntity(
                        id = existing?.id ?: 0L,
                        title = existing?.title?.takeIf { it.isNotBlank() } ?: displayAlbum.title,
                        path = existing?.path?.takeIf { it.isNotBlank() } ?: onlinePath,
                        localPath = existing?.localPath,
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
                    coverUrlResult = entity.coverUrl.trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }.orEmpty()
                    coverPathResult = entity.coverPath.trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }.orEmpty()

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
                    val newLeaves = selected.filter { leaf ->
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
                }

                SaveOnlineToLibraryResult(
                    selectedCount = selected.size,
                    insertedCount = insertedCount,
                    albumId = albumIdResult,
                    coverUrl = coverUrlResult,
                    coverPath = coverPathResult
                )
            }

            val selectedCount = result.selectedCount
            val insertedCount = result.insertedCount

            if (result.albumId > 0L) {
                try {
                    ensureAlbumCoverSaved(result.albumId, result.coverPath, result.coverUrl)
                } catch (_: Exception) {
                }
            }
            if (selectedCount <= 0) {
                messageManager.showInfo("没有可保存的音频/视频文件")
            } else if (insertedCount <= 0) {
                messageManager.showInfo("本地已存在，未重复保存")
            } else if (insertedCount < selectedCount) {
                val skipped = selectedCount - insertedCount
                messageManager.showSuccess("已保存到本地库（${insertedCount}项），跳过已存在（${skipped}项）")
            } else {
                messageManager.showSuccess("已保存到本地库（${insertedCount}项）")
            }
        }
    }

    private data class SaveOnlineToLibraryResult(
        val selectedCount: Int,
        val insertedCount: Int,
        val albumId: Long,
        val coverUrl: String,
        val coverPath: String
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
                while (maxDim / sample > 2048) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
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
                while (maxDim / sample > 2048) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
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
                while (maxDim / sample > 2048) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
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

    private data class OnlineSaveLeaf(
        val relativePath: String,
        val title: String,
        val url: String,
        val duration: Double,
        val group: String,
        val subtitleSources: List<RemoteSubtitleSource>
    )

    private fun flattenOnlineSaveLeaves(tree: List<AsmrOneTrackNodeResponse>): List<OnlineSaveLeaf> {
        val out = mutableListOf<OnlineSaveLeaf>()
        fun sanitize(name: String): String = name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val videoExts = setOf("mp4", "mkv", "webm")
        val subtitleExts = setOf("lrc", "srt", "vtt")

        data class LeafFile(
            val rawTitle: String,
            val safeTitle: String,
            val url: String,
            val duration: Double?
        ) {
            val ext: String = run {
                val ext0 = rawTitle.substringAfterLast('.', "").lowercase()
                if (ext0.isNotBlank()) ext0 else url.substringBefore('?').substringAfterLast('.', "").lowercase()
            }
            val baseName: String = safeTitle.substringBeforeLast('.')
        }

        fun buildSubtitlesForAudio(audio: LeafFile, siblings: List<LeafFile>): List<RemoteSubtitleSource> {
            val base = audio.baseName
            if (base.isBlank()) return emptyList()
            return siblings.filter { subtitleExts.contains(it.ext) }.mapNotNull { sib ->
                val sibBase = sib.baseName
                val lang = when {
                    sibBase == base -> "default"
                    sibBase.startsWith("$base.") -> {
                        val suffix = sibBase.substring(base.length + 1)
                        val parts = suffix.split('.').filter { it.isNotBlank() }
                        val valid = parts.filter { !audioExts.contains(it.lowercase()) }
                        when {
                            valid.isEmpty() -> "zh"
                            valid.size == 1 -> valid[0]
                            else -> valid.last()
                        }
                    }
                    else -> return@mapNotNull null
                }
                RemoteSubtitleSource(url = sib.url, language = lang, ext = sib.ext)
            }.sortedWith(
                compareBy<RemoteSubtitleSource> { s ->
                    val prefer = listOf("default", "zh", "cn", "chs", "ja", "jp", "jpn")
                    val idx = prefer.indexOf(s.language.lowercase())
                    if (idx >= 0) idx else Int.MAX_VALUE
                }.thenBy { it.url }
            )
        }

        fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
            val leafFiles = nodes.mapNotNull { node ->
                val children = node.children.orEmpty()
                val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
                val url = node.mediaDownloadUrl ?: node.streamUrl
                if (children.isNotEmpty() || url.isNullOrBlank()) return@mapNotNull null
                val safeTitle = sanitize(rawTitle)
                LeafFile(rawTitle = rawTitle, safeTitle = safeTitle, url = url, duration = node.duration)
            }

            leafFiles.filter { audioExts.contains(it.ext) || videoExts.contains(it.ext) }.forEach { leaf ->
                val path = if (parentPath.isBlank()) leaf.safeTitle else "$parentPath/${leaf.safeTitle}"
                val relDir = path.substringBeforeLast('/', "")
                val group = relDir
                val subsRaw = if (audioExts.contains(leaf.ext)) buildSubtitlesForAudio(leaf, leafFiles) else emptyList()
                val subs = if (subsRaw.isNotEmpty()) subsRaw else OnlineLyricsStore.get(leaf.url)
                out.add(
                    OnlineSaveLeaf(
                        relativePath = path,
                        title = leaf.safeTitle.substringBeforeLast('.'),
                        url = leaf.url,
                        duration = leaf.duration ?: 0.0,
                        group = group,
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
        walk(tree, "")
        return out
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
                                    group = group
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
                group = group
            )
        }
    }

    private fun TrackEntity.toDomain(): Track {
        return Track(
            id = id,
            albumId = albumId,
            title = title,
            path = path,
            duration = duration,
            group = group
        )
    }

    private fun safeFolderName(input: String): String {
        return input.trim().ifEmpty { "album" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    private fun safeFileName(input: String): String {
        return input.trim().ifEmpty { "track" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }
}

sealed class AlbumDetailUiState {
    object Loading : AlbumDetailUiState()
    data class Success(val model: AlbumDetailModel) : AlbumDetailUiState()
    data class Error(val message: String) : AlbumDetailUiState()
}

data class AlbumDetailModel(
    val baseRjCode: String,
    val rjCode: String,
    val displayAlbum: Album,
    val localAlbum: Album?,
    val dlsiteInfo: Album?,
    val dlsiteGalleryUrls: List<String>,
    val dlsiteTrialTracks: List<Track>,
    val dlsiteRecommendations: DlsiteRecommendations,
    val dlsiteWorkno: String,
    val dlsitePlayWorkno: String,
    val dlsiteEditions: List<DlsiteLanguageEdition>,
    val dlsiteSelectedLang: String,
    val asmrOneWorkId: String?,
    val asmrOneSite: Int?,
    val asmrOneTree: List<AsmrOneTrackNodeResponse>,
    val dlsitePlayTree: List<AsmrOneTrackNodeResponse>,
    val isLoadingDlsite: Boolean,
    val isLoadingDlsiteTrial: Boolean,
    val isLoadingAsmrOne: Boolean,
    val isLoadingDlsitePlay: Boolean
)

private data class AsmrOneLeafDownload(
    val url: String,
    val relativePath: String,
    val duration: Double?
)

private fun flattenAsmrOneTracks(tree: List<AsmrOneTrackNodeResponse>): List<Track> {
    val leaves = flattenAsmrOneLeafDownloads(tree)
    return leaves.map {
        Track(
            albumId = 0,
            title = it.relativePath.substringAfterLast('/'),
            path = it.url,
            duration = it.duration ?: 0.0
        )
    }
}

private fun flattenAsmrOneLeafDownloads(tree: List<AsmrOneTrackNodeResponse>): List<AsmrOneLeafDownload> {
    val out = mutableListOf<AsmrOneLeafDownload>()
    fun sanitizeSegment(name: String): String {
        return name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }
    fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
        nodes.forEach { node ->
            val title = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitizeSegment(title)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            val children = node.children.orEmpty()
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (!url.isNullOrBlank() && children.isEmpty()) {
                out.add(AsmrOneLeafDownload(url = url, relativePath = path, duration = node.duration))
            } else if (children.isNotEmpty()) {
                walk(children, path)
            }
        }
    }
    walk(tree, "")
    return out
}
