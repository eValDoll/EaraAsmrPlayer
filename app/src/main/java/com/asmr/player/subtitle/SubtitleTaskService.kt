package com.asmr.player.subtitle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.asmr.player.MainActivity
import com.asmr.player.R
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.SubtitleCommittedCaptionEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.SubtitleFallbackCaptionEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskItemEntity
import com.asmr.player.data.local.db.entities.SubtitleTranscriptionChunkEntity
import com.asmr.player.data.local.db.entities.SubtitleTitleOwnerKind
import com.asmr.player.data.local.db.entities.SubtitleTranslationSourceEntity
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.di.DEEPSEEK_HTTP_CLIENT
import com.asmr.player.util.MessageManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Named
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

internal class SubtitleTaskService : Service() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ServiceEntryPoint {
        @Named(DEEPSEEK_HTTP_CLIENT)
        fun deepSeekOkHttpClient(): OkHttpClient
        fun gson(): Gson
        fun settingsRepository(): SettingsRepository
        fun messageManager(): MessageManager
        fun deepSeekAccountRepository(): DeepSeekAccountRepository
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wakeSignals = Channel<Unit>(Channel.CONFLATED)
    private lateinit var database: AppDatabase
    private lateinit var repository: SubtitleTaskRepository
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var gson: Gson
    private lateinit var deepSeekOkHttpClient: OkHttpClient
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var messageManager: MessageManager
    private lateinit var deepSeekAccountRepository: DeepSeekAccountRepository
    private var transcriptionJob: Job? = null
    private var transcriptionItemId: String? = null
    private val translationJobs = ConcurrentHashMap<String, Job>()
    private val titleTranslationJobs = ConcurrentHashMap<String, Job>()
    private val titleTranslationRetryAt = ConcurrentHashMap<String, Long>()
    private val balanceRefreshLock = Any()
    private var balanceRefreshRequested = false
    private var balanceRefreshJob: Job? = null
    private var transcriptionEngine: SubtitleTranscriptionEngine? = null
    private var stoppingSafely = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = signalWake()
        override fun onLost(network: Network) = signalWake()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = signalWake()
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabaseProvider.get(applicationContext)
        repository = SubtitleTaskRepository.get(applicationContext)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            ServiceEntryPoint::class.java
        )
        gson = entryPoint.gson()
        deepSeekOkHttpClient = entryPoint.deepSeekOkHttpClient()
        settingsRepository = entryPoint.settingsRepository()
        messageManager = entryPoint.messageManager()
        deepSeekAccountRepository = entryPoint.deepSeekAccountRepository()
        createNotificationChannel()
        startAsForeground(buildNotification(emptyList()))
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:SubtitleTasks")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
        serviceScope.launch { schedulerLoop() }
        signalWake()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> serviceScope.launch { pauseAll() }
            ACTION_CANCEL_ALL -> serviceScope.launch { cancelAll() }
            else -> signalWake()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        interruptAndStop("应用已从后台清除，字幕任务已中断")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        wakeLock?.takeIf(PowerManager.WakeLock::isHeld)?.release()
        wakeLock = null
        runCatching { transcriptionEngine?.close() }
        transcriptionEngine = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        interruptAndStop("系统前台服务时限已到，字幕任务已安全中断")
    }

    private suspend fun schedulerLoop() {
        while (serviceScope.isActive) {
            try {
                withTimeoutOrNull(SCHEDULER_TICK_MS) { wakeSignals.receive() }
                reconcileControlRequests()
                scheduleTranscription()
                scheduleTranslations()
                scheduleTitleTranslations()
                releaseTranscriptionEngineWhenIdle()
                updateForegroundNotification()
                stopWhenIdle()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e(TAG, "字幕任务调度循环异常", error)
                delay(SCHEDULER_TICK_MS)
            }
        }
    }

    private suspend fun reconcileControlRequests() {
        val items = database.subtitleTaskDao().getAllItems()
        items.filter { it.state == SubtitleItemState.PAUSE_REQUESTED }.forEach { item ->
            cancelRunningJob(item.id)
            if (!isJobRunning(item.id)) {
                database.subtitleTaskDao().updateItem(
                    item.copy(state = SubtitleItemState.PAUSED, updatedAt = System.currentTimeMillis())
                )
                repository.refreshTaskState(item.taskId)
            }
        }
        items.filter { it.state == SubtitleItemState.CANCEL_REQUESTED }.forEach { item ->
            cancelRunningJob(item.id)
            if (!isJobRunning(item.id)) {
                val warning = repository.finishCancellation(item.id)
                if (!warning.isNullOrBlank()) showWarningNotification(warning)
            }
        }
    }

    private suspend fun scheduleTranscription() {
        if (transcriptionJob?.isActive == true) return
        transcriptionJob = null
        val candidate = database.subtitleTaskDao().getNextTranscription() ?: return
        val selectedId = SubtitleDispatchPolicy.selectTranscriptionItem(
            orderedCandidates = listOf(candidate.id),
            transcriptionActive = false
        ) ?: return
        val item = if (candidate.id == selectedId) candidate else return
        transcriptionItemId = item.id
        transcriptionJob = serviceScope.launch {
            try {
                transcribe(item.id)
            } finally {
                transcriptionJob = null
                transcriptionItemId = null
                signalWake()
            }
        }
    }

    private suspend fun scheduleTranslations() {
        val dao = database.subtitleTaskDao()
        val candidates = dao.getTranslationCandidates(System.currentTimeMillis(), Int.MAX_VALUE)
            .filterNot { translationJobs[it.id]?.isActive == true }
        if (!isNetworkAvailable()) {
            candidates.forEach { item ->
                if (item.state != SubtitleItemState.WAITING_NETWORK) {
                    dao.updateItem(
                        item.copy(
                            state = SubtitleItemState.WAITING_NETWORK,
                            suspendedFromState = item.suspendedFromState.ifBlank { item.state },
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    repository.refreshTaskState(item.taskId)
                }
            }
            return
        }
        val selectedIds = SubtitleDispatchPolicy.selectTranslationItems(
            orderedCandidates = candidates.map(SubtitleTaskItemEntity::id),
            activeItemIds = translationJobs.filterValues(Job::isActive).keys,
            concurrency = DEEPSEEK_TRANSLATION_CONCURRENCY
        )
        val selected = candidates.filter { it.id in selectedIds }
        candidates.filterNot { it.id in selectedIds }.forEach { item ->
            if (item.state != SubtitleItemState.WAITING_SLOT) {
                dao.updateItem(
                    item.copy(
                        state = SubtitleItemState.WAITING_SLOT,
                        suspendedFromState = item.suspendedFromState.ifBlank { item.state },
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        selected.forEach { item ->
            val job = serviceScope.launch(start = CoroutineStart.LAZY) {
                try {
                    translate(item.id)
                } finally {
                    translationJobs.remove(item.id)
                    signalWake()
                }
            }
            translationJobs[item.id] = job
            job.start()
        }
    }

    private suspend fun scheduleTitleTranslations() {
        if (!isNetworkAvailable()) return
        val now = System.currentTimeMillis()
        val ownerDao = database.subtitleTitleOwnerDao()
        ownerDao.getPendingTaskIds().forEach { taskId ->
            if (titleTranslationJobs[taskId]?.isActive == true) return@forEach
            if ((titleTranslationRetryAt[taskId] ?: 0L) > now) return@forEach
            val items = database.subtitleTaskDao().getItemsForTask(taskId)
            if (items.none { it.state !in TITLE_TRANSLATION_BLOCKED_STATES }) return@forEach
            val job = serviceScope.launch(start = CoroutineStart.LAZY) {
                try {
                    translateDisplayNamesForTask(taskId)
                    repository.finishTitleTranslation(taskId)
                    titleTranslationRetryAt.remove(taskId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: SubtitleTranslationException) {
                    titleTranslationRetryAt[taskId] = if (error.retryable) {
                        System.currentTimeMillis() + TITLE_TRANSLATION_RETRY_BACKOFF_MS
                    } else {
                        TITLE_TRANSLATION_DISABLED_RETRY_AT
                    }
                    Log.w(TAG, "作品显示名翻译失败 taskId=$taskId", error)
                } catch (error: Throwable) {
                    titleTranslationRetryAt[taskId] = System.currentTimeMillis() + TITLE_TRANSLATION_RETRY_BACKOFF_MS
                    Log.w(TAG, "作品显示名翻译失败 taskId=$taskId", error)
                } finally {
                    titleTranslationJobs.remove(taskId)
                    signalWake()
                }
            }
            titleTranslationJobs[taskId] = job
            job.start()
        }
    }

    /**
     * 把一个字幕任务涉及的作品标题与音轨标题翻译成中文，写入 displayTitle。
     * 只在任务仍处于翻译阶段（未取消/未删除）时提交结果。
     */
    private suspend fun translateDisplayNamesForTask(taskId: String) {
        val ownerDao = database.subtitleTitleOwnerDao()
        val registeredOwners = ownerDao.getByTask(taskId)
        if (registeredOwners.none { owner -> owner.displayTitle.isBlank() }) return
        val albumOwners = registeredOwners.filter { it.kind == SubtitleTitleOwnerKind.ALBUM }
        val trackOwners = registeredOwners.filter { it.kind == SubtitleTitleOwnerKind.TRACK }
        val allTracks = database.trackDao()
            .getTracksByIdsOnce(trackOwners.map { it.targetId })
            .associateBy { it.id }
        val albumIds = (albumOwners.map { owner -> owner.targetId } + allTracks.values.map { track -> track.albumId })
            .distinct()
        val albums = albumIds.mapNotNull { albumId -> database.albumDao().getAlbumById(albumId) }
        if (albums.isEmpty()) {
            // 任务涉及的作品行已全部失效（例如已被删除），清除未完成的登记，避免无限重试
            ownerDao.deletePendingForTask(taskId)
            return
        }
        val tracksByAlbum = allTracks.values.groupBy { it.albumId }
        val client = requireTranslationClient()
        albums.forEach { album ->
            val albumTracks = tracksByAlbum[album.id].orEmpty()
            if (albumTracks.isEmpty()) return@forEach
            val translated = try {
                client.translateDisplayNames(
                    albumTitle = album.title,
                    circle = album.circle,
                    cv = album.cv,
                    trackTitles = albumTracks.map { it.id to it.title }
                )
            } finally {
                requestBalanceRefresh()
            }
            database.withTransaction {
                database.subtitleTaskDao().getTask(taskId) ?: return@withTransaction
                val items = database.subtitleTaskDao().getItemsForTask(taskId)
                if (items.none { it.state !in TITLE_TRANSLATION_BLOCKED_STATES }) return@withTransaction
                if (translated.albumTitle.isNotBlank()) {
                    database.albumDao().updateAlbumDisplayTitle(album.id, translated.albumTitle)
                    ownerDao.updateDisplayTitle(
                        taskId, SubtitleTitleOwnerKind.ALBUM, album.id, translated.albumTitle
                    )
                }
                albumTracks.forEach trackLoop@{ track ->
                    val title = translated.trackTitles[track.id] ?: return@trackLoop
                    database.trackDao().updateTrackDisplayTitle(track.id, title)
                    ownerDao.updateDisplayTitle(
                        taskId, SubtitleTitleOwnerKind.TRACK, track.id, title
                    )
                }
            }
        }
    }

    private suspend fun transcribe(itemId: String) {
        val dao = database.subtitleTaskDao()
        val initial = dao.getItem(itemId) ?: return
        if (initial.state != SubtitleItemState.QUEUED_TRANSCRIPTION) return
        dao.updateItem(
            initial.copy(
                state = SubtitleItemState.TRANSCRIBING,
                errorMessage = "",
                updatedAt = System.currentTimeMillis()
            )
        )
        repository.refreshTaskState(initial.taskId)
        try {
            val engine = transcriptionEngine ?: SubtitleTranscriptionEngineRegistry
                .defaultFactory(applicationContext)
                .create()
                .also { transcriptionEngine = it }
            val existingChunks = dao.getChunks(itemId)
            val resumeAtMs = existingChunks.lastOrNull()?.endMs ?: 0L
            LocalAudioDecoder(applicationContext, engine.model.inputSampleRateHz).decode(
                path = initial.trackPath,
                startAtMs = resumeAtMs
            ) { chunk ->
                coroutineContext.ensureActive()
                val activeContext = coroutineContext
                val segments = engine.transcribe(
                    channelSamples = chunk.channelSamples,
                    isCancelled = { !activeContext.isActive },
                    onProgress = { }
                )
                coroutineContext.ensureActive()
                val absoluteSegments = segments.map { segment ->
                    GeneratedSubtitle(
                        startMs = chunk.startMs + segment.startMs,
                        endMs = chunk.startMs + segment.endMs,
                        text = segment.text
                    )
                }
                val absoluteTokens = segments.flatMap { segment ->
                    val segmentStart = chunk.startMs + segment.startMs
                    segment.tokens.map { token ->
                        GeneratedSubtitle(
                            startMs = segmentStart + token.startMs,
                            endMs = segmentStart + token.endMs,
                            text = token.text
                        )
                    }
                }
                database.withTransaction {
                    val current = dao.getItem(itemId) ?: throw CancellationException("字幕任务已删除")
                    if (current.state != SubtitleItemState.TRANSCRIBING) throw CancellationException("字幕转录已暂停或取消")
                    val chunkIndex = current.transcriptionChunkCursor
                    val processedMs = chunk.startMs + chunk.durationMs
                    dao.upsertChunk(
                        SubtitleTranscriptionChunkEntity(
                            itemId = itemId,
                            chunkIndex = chunkIndex,
                            startMs = chunk.startMs,
                            endMs = processedMs,
                            segmentsJson = gson.toJson(absoluteSegments),
                            tokensJson = gson.toJson(absoluteTokens),
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    val totalMs = maxOf(current.totalDurationMs, chunk.totalDurationMs, processedMs)
                    dao.updateItem(
                        current.copy(
                            transcriptionChunkCursor = chunkIndex + 1,
                            transcriptionProgress = if (totalMs > 0L) {
                                (processedMs * 100L / totalMs).toInt().coerceIn(0, 99)
                            } else {
                                current.transcriptionProgress
                            },
                            transcribedMs = processedMs,
                            totalDurationMs = totalMs,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            prepareGeneratedTranslation(itemId)
        } catch (cancelled: CancellationException) {
            settleCancelledExecution(itemId)
            throw cancelled
        } catch (error: Throwable) {
            Log.w(TAG, "本地字幕转录失败 itemId=$itemId type=${error.javaClass.name}")
            releaseFailedTranscriptionEngine()
            failItem(itemId, SubtitleFailureMessages.transcription(error))
        }
    }

    private suspend fun prepareGeneratedTranslation(itemId: String) {
        val dao = database.subtitleTaskDao()
        val chunks = dao.getChunks(itemId)
        val generated = chunks.flatMap { chunk -> gson.generatedSubtitles(chunk.segmentsJson) }
        val item = dao.getItem(itemId) ?: return
        val durationMs = maxOf(item.totalDurationMs, chunks.lastOrNull()?.endMs ?: 0L)
        val sourceSegments = SubtitleSegmentNormalizer.normalize(generated, durationMs)
        check(sourceSegments.isNotEmpty()) { "未识别到可生成字幕的日语语音" }
        val fallback = SubtitleSegmentNormalizer.normalize(
            SubtitleSemanticSegmenter.reflow(sourceSegments),
            durationMs
        )
        check(fallback.isNotEmpty()) { "未识别到可生成字幕的日语语音" }
        val layout = buildGeneratedTranslationLayout(itemId, fallback)
        val playerFallback = fallback.map { caption ->
            SubtitleEntity(
                trackId = item.trackId,
                startMs = caption.startMs,
                endMs = caption.endMs,
                text = caption.text,
                japaneseText = caption.text
            )
        }
        database.withTransaction {
            val current = dao.getItem(itemId) ?: return@withTransaction
            check(current.state == SubtitleItemState.TRANSCRIBING) { "字幕转录已暂停或取消" }
            val playerCurrent = database.trackDao().getSubtitlesForTrack(current.trackId).sortedWith(SUBTITLE_ORDER)
            check(subtitleHash(playerCurrent) == current.lastPublishedHash) { "字幕在转录期间已被修改，未覆盖用户版本" }
            dao.deleteSources(itemId)
            dao.deleteFallbackCaptions(itemId)
            dao.insertSources(layout.sources)
            dao.insertFallbackCaptions(layout.fallbackCaptions)
            database.trackDao().deleteSubtitlesForTrack(current.trackId)
            database.trackDao().insertSubtitles(playerFallback)
            val publishedHash = subtitleHash(playerFallback)
            dao.updateItem(
                current.copy(
                    state = SubtitleItemState.QUEUED_TRANSLATION,
                    transcriptionProgress = 100,
                    transcribedMs = durationMs,
                    totalDurationMs = durationMs,
                    translationCursor = 0,
                    translationTotal = layout.sources.size,
                    translationBatchIndex = 0,
                    translationBatchTotal = fullTranslationRequestCount(layout.sources.size),
                    attempt = 0,
                    errorMessage = "",
                    lastPublishedHash = publishedHash,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        repository.refreshTaskState(item.taskId)
    }

    private suspend fun translate(itemId: String) {
        val dao = database.subtitleTaskDao()
        val item = dao.getItem(itemId) ?: return
        if (item.state !in TRANSLATION_QUEUE_STATES) return
        val sources = dao.getSources(itemId)
        if (sources.isEmpty()) {
            failItem(itemId, "字幕翻译源已丢失")
            return
        }
        val confirmedSourceCount = dao.getCommittedCaptions(itemId)
            .sumOf { caption -> caption.lastSourceIndex - caption.firstSourceIndex + 1 }
        dao.updateItem(
            item.copy(
                state = SubtitleItemState.TRANSLATING,
                translationCursor = confirmedSourceCount,
                translationTotal = sources.size,
                translationBatchIndex = 1,
                translationBatchTotal = 1,
                suspendedFromState = "",
                errorMessage = "",
                updatedAt = System.currentTimeMillis()
            )
        )
        repository.refreshTaskState(item.taskId)
        try {
            if (item.mode == SubtitleTaskMode.GENERATED) {
                translateGeneratedFull(itemId, sources)
            } else {
                translateManualFull(itemId, sources)
            }
        } catch (cancelled: CancellationException) {
            settleCancelledExecution(itemId)
            throw cancelled
        } catch (error: SubtitleTranslationException) {
            handleTranslationFailure(itemId, error)
        } catch (error: IOException) {
            handleTranslationFailure(
                itemId,
                SubtitleTranslationException(
                    SubtitleFailureMessages.network(error),
                    retryable = true,
                    cause = error
                )
            )
        } catch (error: Throwable) {
            Log.w(TAG, "字幕翻译运行异常 itemId=$itemId type=${error.javaClass.name}")
            failItem(itemId, SubtitleFailureMessages.translation(error))
        }
    }

    private suspend fun translateGeneratedFull(
        itemId: String,
        sourceEntities: List<SubtitleTranslationSourceEntity>
    ) {
        translateWithProgress(itemId, sourceEntities, allowMerging = true)
    }

    private suspend fun translateManualFull(
        itemId: String,
        sourceEntities: List<SubtitleTranslationSourceEntity>
    ) {
        translateWithProgress(itemId, sourceEntities, allowMerging = false)
    }

    private suspend fun translateWithProgress(
        itemId: String,
        sourceEntities: List<SubtitleTranslationSourceEntity>,
        allowMerging: Boolean
    ) {
        val dao = database.subtitleTaskDao()
        val sources = sourceEntities.map { it.toGeneratedSource() }
        val confirmed = dao.getCommittedCaptions(itemId).map { it.toGeneratedCaption() }
        val client = requireTranslationClient()
        try {
            client.translateSubtitles(
                sources = sources,
                allowMerging = allowMerging,
                confirmedCaptions = confirmed
            ) { captions ->
                commitTranslationProgress(
                    itemId = itemId,
                    captions = captions,
                    sourceEntities = sourceEntities,
                    generated = allowMerging
                )
            }
        } finally {
            requestBalanceRefresh()
        }
        repository.finishSucceeded(itemId)
    }

    private suspend fun commitTranslationProgress(
        itemId: String,
        captions: List<GeneratedSubtitleCaption>,
        sourceEntities: List<SubtitleTranslationSourceEntity>,
        generated: Boolean
    ) {
        val dao = database.subtitleTaskDao()
        database.withTransaction {
            val item = dao.getItem(itemId) ?: return@withTransaction
            check(item.state == SubtitleItemState.TRANSLATING) { "字幕翻译已暂停或取消" }
            assertTaskControlsCurrentSubtitles(item)
            val existing = dao.getCommittedCaptions(itemId)
            val existingSourceCount = existing.sumOf { it.lastSourceIndex - it.firstSourceIndex + 1 }
            val remainingSources = sourceEntities.drop(existingSourceCount).map { it.toGeneratedSource() }
            val validated = validateSubtitleCaptionBatch(
                captions = captions,
                expectedRemainingSources = remainingSources,
                allowMerging = generated
            )
            val inserted = validated.mapIndexed { offset, caption ->
                SubtitleCommittedCaptionEntity(
                    itemId = itemId,
                    captionIndex = existing.size + offset,
                    firstSourceIndex = caption.sourceIndices.first(),
                    lastSourceIndex = caption.sourceIndices.last(),
                    startMs = caption.startMs,
                    endMs = caption.endMs,
                    correctedJapanese = caption.correctedJapanese,
                    chineseText = caption.chineseText
                )
            }
            dao.insertCommittedCaptions(inserted)
            val allCommitted = existing + inserted
            val confirmedSourceCount = allCommitted.sumOf { it.lastSourceIndex - it.firstSourceIndex + 1 }
            val rebuilt = if (generated) {
                allCommitted.map { caption ->
                    SubtitleEntity(
                        trackId = item.trackId,
                        startMs = caption.startMs,
                        endMs = caption.endMs,
                        text = caption.chineseText,
                        japaneseText = caption.correctedJapanese
                    )
                } + rebuildGeneratedFallbackSuffix(
                    trackId = item.trackId,
                    confirmedSourceCount = confirmedSourceCount,
                    sources = sourceEntities,
                    fallback = dao.getFallbackCaptions(itemId)
                )
            } else {
                val translatedByIndex = allCommitted.associateBy(SubtitleCommittedCaptionEntity::firstSourceIndex)
                dao.getSnapshots(itemId).map { snapshot ->
                    val translated = translatedByIndex[snapshot.captionIndex]
                    SubtitleEntity(
                        trackId = item.trackId,
                        startMs = snapshot.startMs,
                        endMs = snapshot.endMs,
                        text = translated?.chineseText ?: snapshot.text,
                        japaneseText = translated?.correctedJapanese ?: snapshot.text
                    )
                }
            }
            publishRebuiltSubtitles(item.trackId, rebuilt)
            val completed = confirmedSourceCount >= sourceEntities.size
            dao.updateItem(
                item.copy(
                    state = if (completed) SubtitleItemState.SUCCEEDED else SubtitleItemState.TRANSLATING,
                    translationCursor = confirmedSourceCount,
                    translationBatchIndex = 1,
                    translationBatchTotal = 1,
                    attempt = 0,
                    nextAttemptAt = 0L,
                    errorMessage = "",
                    lastPublishedHash = subtitleHash(rebuilt),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun assertTaskControlsCurrentSubtitles(item: SubtitleTaskItemEntity) {
        val current = database.trackDao().getSubtitlesForTrack(item.trackId).sortedWith(SUBTITLE_ORDER)
        check(taskStillControlsSubtitles(subtitleHash(current), item.lastPublishedHash)) {
            "字幕在任务期间已被修改，已停止写入"
        }
    }

    private suspend fun publishRebuiltSubtitles(trackId: Long, subtitles: List<SubtitleEntity>) {
        database.trackDao().deleteSubtitlesForTrack(trackId)
        database.trackDao().insertSubtitles(subtitles.sortedWith(SUBTITLE_ORDER))
    }

    private suspend fun handleTranslationFailure(itemId: String, error: SubtitleTranslationException) {
        val dao = database.subtitleTaskDao()
        val item = dao.getItem(itemId) ?: return
        if (item.state != SubtitleItemState.TRANSLATING) return
        val attempt = item.attempt + 1
        if (error.retryable && attempt < MAX_TRANSLATION_ATTEMPTS) {
            val exponential = BASE_RETRY_DELAY_MS * (1L shl (attempt - 1))
            val delayMs = maxOf(exponential, error.retryAfterMs ?: 0L) + Random.nextLong(0L, RETRY_JITTER_MS + 1L)
            dao.updateItem(
                item.copy(
                    state = SubtitleItemState.RETRY_WAIT,
                    suspendedFromState = SubtitleItemState.TRANSLATING,
                    attempt = attempt,
                    nextAttemptAt = System.currentTimeMillis() + delayMs,
                    errorMessage = error.message.orEmpty(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            repository.refreshTaskState(item.taskId)
        } else {
            failItem(itemId, error.message.orEmpty().ifBlank { "字幕翻译重试已耗尽" }, attempt)
        }
    }

    private suspend fun failItem(itemId: String, message: String, attempt: Int? = null) {
        val dao = database.subtitleTaskDao()
        val item = dao.getItem(itemId) ?: return
        if (item.state in setOf(SubtitleItemState.PAUSE_REQUESTED, SubtitleItemState.CANCEL_REQUESTED)) return
        val userMessage = message.trim().ifBlank { "字幕任务失败，请重试。" }
        dao.updateItem(
            item.copy(
                suspendedFromState = item.state,
                state = SubtitleItemState.FAILED,
                attempt = attempt ?: item.attempt,
                errorMessage = userMessage,
                updatedAt = System.currentTimeMillis()
            )
        )
        repository.refreshTaskState(item.taskId)
        if (SubtitleFailureMessages.isUserActionWarning(userMessage)) {
            messageManager.showWarning(userMessage)
        } else {
            messageManager.showError(userMessage)
        }
    }

    private suspend fun settleCancelledExecution(itemId: String) {
        val dao = database.subtitleTaskDao()
        val item = dao.getItem(itemId) ?: return
        when (item.state) {
            SubtitleItemState.PAUSE_REQUESTED -> {
                dao.updateItem(item.copy(state = SubtitleItemState.PAUSED, updatedAt = System.currentTimeMillis()))
                repository.refreshTaskState(item.taskId)
            }
            SubtitleItemState.CANCEL_REQUESTED -> {
                val warning = repository.finishCancellation(itemId)
                if (!warning.isNullOrBlank()) showWarningNotification(warning)
            }
        }
    }

    private fun cancelRunningJob(itemId: String) {
        translationJobs[itemId]?.cancel(CancellationException("字幕任务控制请求"))
        if (transcriptionItemId == itemId) {
            transcriptionJob?.cancel(CancellationException("字幕任务控制请求"))
        }
    }

    private fun isJobRunning(itemId: String): Boolean {
        if (translationJobs[itemId]?.isActive == true) return true
        return transcriptionItemId == itemId && transcriptionJob?.isActive == true
    }

    private suspend fun pauseAll() {
        database.subtitleTaskDao().getAllItems().map(SubtitleTaskItemEntity::taskId).distinct()
            .forEach { repository.pauseTask(it) }
        signalWake()
    }

    private suspend fun cancelAll() {
        database.subtitleTaskDao().getAllItems().map(SubtitleTaskItemEntity::taskId).distinct()
            .forEach { repository.cancelTask(it) }
        signalWake()
    }

    private suspend fun releaseTranscriptionEngineWhenIdle() {
        if (transcriptionJob?.isActive == true) return
        if (database.subtitleTaskDao().getNextTranscription() != null) return
        val engine = transcriptionEngine ?: return
        runCatching { engine.close() }
            .onSuccess { Log.i(TAG, "字幕转录模型内存已释放") }
            .onFailure { error -> Log.w(TAG, "释放字幕转录模型失败", error) }
        transcriptionEngine = null
    }

    private fun releaseFailedTranscriptionEngine() {
        val engine = transcriptionEngine ?: return
        runCatching { engine.close() }
            .onFailure { error -> Log.w(TAG, "转录失败后释放模型失败", error) }
        transcriptionEngine = null
    }

    private suspend fun updateForegroundNotification() {
        val items = database.subtitleTaskDao().getAllItems()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(items))
    }

    private suspend fun stopWhenIdle() {
        if (transcriptionJob?.isActive == true || translationJobs.values.any(Job::isActive)) return
        if (titleTranslationJobs.values.any(Job::isActive)) return
        if (balanceRefreshJob != null) return
        if (database.subtitleTaskDao().countRunnableItems() > 0) return
        val pendingTitleTaskIds = database.subtitleTitleOwnerDao().getPendingTaskIds()
        if (pendingTitleTaskIds.any { titleTranslationRetryAt[it] != TITLE_TRANSLATION_DISABLED_RETRY_AT }) return
        stoppingSafely = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(items: List<SubtitleTaskItemEntity>): Notification {
        val transcribing = items.firstOrNull { it.state == SubtitleItemState.TRANSCRIBING }
        val translating = items.count { it.state == SubtitleItemState.TRANSLATING }
        val waitingNetwork = items.count { it.state == SubtitleItemState.WAITING_NETWORK }
        val waitingSlot = items.count { it.state == SubtitleItemState.WAITING_SLOT }
        val parts = buildList {
            transcribing?.let { add("转录：${it.trackTitle} ${it.transcriptionProgress}%") }
            if (translating > 0) add("翻译中 $translating/$DEEPSEEK_TRANSLATION_CONCURRENCY")
            if (waitingSlot > 0) add("等待槽位 $waitingSlot")
            if (waitingNetwork > 0) add("等待网络 $waitingNetwork")
            if (isEmpty()) add("正在检查字幕队列")
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_playback)
            .setContentTitle("字幕任务")
            .setContentText(parts.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(parts.joinToString(" · ")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent())
            .addAction(0, "打开", openAppPendingIntent())
            .addAction(0, "暂停全部", servicePendingIntent(ACTION_PAUSE_ALL, 1))
            .addAction(0, "取消全部", servicePendingIntent(ACTION_CANCEL_ALL, 2))
            .build()
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, "字幕任务", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun showWarningNotification(message: String) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_playback)
            .setContentTitle("字幕版本冲突")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(WARNING_NOTIFICATION_ID, notification)
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, SubtitleTaskService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun interruptAndStop(message: String) {
        if (stoppingSafely) return
        stoppingSafely = true
        serviceScope.launch(NonCancellable + Dispatchers.IO) {
            val now = System.currentTimeMillis()
            database.subtitleTaskDao().interruptAllIncompleteItems(now, message)
            database.subtitleTaskDao().markInterruptedTasks(now)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun signalWake() {
        wakeSignals.trySend(Unit)
    }

    private suspend fun requireTranslationClient(): SubtitleTranslationClient {
        val apiKey = DeepSeekApiKeyStore.get(applicationContext).read()
        check(apiKey.isNotBlank()) { "请先在设置中配置 DeepSeek API Key" }
        deepSeekAccountRepository.bindApiKey(apiKey)
        return SubtitleTranslationClient(
            okHttpClient = deepSeekOkHttpClient,
            gson = gson,
            apiKey = apiKey,
            settings = settingsRepository.loadDeepSeekTranslationSettings(),
            onTokenUsage = { totalTokens ->
                deepSeekAccountRepository.recordTokenUsage(apiKey, totalTokens)
            }
        )
    }

    private fun requestBalanceRefresh() {
        val jobToStart = synchronized(balanceRefreshLock) {
            balanceRefreshRequested = true
            if (balanceRefreshJob != null) return@synchronized null
            serviceScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                while (true) {
                    val shouldRefresh = synchronized(balanceRefreshLock) {
                        if (balanceRefreshRequested) {
                            balanceRefreshRequested = false
                            true
                        } else {
                            balanceRefreshJob = null
                            false
                        }
                    }
                    if (!shouldRefresh) break
                    val apiKey = DeepSeekApiKeyStore.get(applicationContext).read()
                    if (apiKey.isNotBlank()) deepSeekAccountRepository.refreshBalance(apiKey)
                }
                signalWake()
            }.also { balanceRefreshJob = it }
        }
        jobToStart?.start()
    }

    companion object {
        private val TRANSLATION_QUEUE_STATES = setOf(
            SubtitleItemState.QUEUED_TRANSLATION,
            SubtitleItemState.WAITING_SLOT,
            SubtitleItemState.WAITING_NETWORK,
            SubtitleItemState.RETRY_WAIT
        )
        private val TITLE_TRANSLATION_BLOCKED_STATES = setOf(
            SubtitleItemState.CANCEL_REQUESTED,
            SubtitleItemState.CANCELED
        )
        private const val ACTION_WAKE = "com.asmr.player.subtitle.WAKE"
        private const val ACTION_PAUSE_ALL = "com.asmr.player.subtitle.PAUSE_ALL"
        private const val ACTION_CANCEL_ALL = "com.asmr.player.subtitle.CANCEL_ALL"
        private const val NOTIFICATION_CHANNEL_ID = "subtitle_tasks"
        private const val NOTIFICATION_ID = 7412
        private const val WARNING_NOTIFICATION_ID = 7413
        private const val MAX_TRANSLATION_ATTEMPTS = 4
        private const val BASE_RETRY_DELAY_MS = 1_000L
        private const val RETRY_JITTER_MS = 250L
        private const val SCHEDULER_TICK_MS = 500L
        private const val TITLE_TRANSLATION_RETRY_BACKOFF_MS = 60_000L
        private const val TITLE_TRANSLATION_DISABLED_RETRY_AT = Long.MAX_VALUE
        private const val TAG = "SubtitleTaskService"

        fun wake(context: Context) {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, SubtitleTaskService::class.java).setAction(ACTION_WAKE)
            )
        }
    }
}

internal data class GeneratedTranslationLayout(
    val sources: List<SubtitleTranslationSourceEntity>,
    val fallbackCaptions: List<SubtitleFallbackCaptionEntity>
)

internal fun buildGeneratedTranslationLayout(
    itemId: String,
    captions: List<GeneratedSubtitle>
): GeneratedTranslationLayout {
    require(itemId.isNotBlank())
    require(captions.isNotEmpty())
    val sources = captions.mapIndexed { index, caption ->
        SubtitleTranslationSourceEntity(
            itemId = itemId,
            sourceIndex = index,
            startMs = caption.startMs,
            endMs = caption.endMs,
            text = caption.text
        )
    }
    return GeneratedTranslationLayout(
        sources = sources,
        fallbackCaptions = captions.mapIndexed { index, caption ->
            SubtitleFallbackCaptionEntity(
                itemId = itemId,
                captionIndex = index,
                firstSourceIndex = index,
                lastSourceIndex = index,
                startMs = caption.startMs,
                endMs = caption.endMs,
                text = caption.text
            )
        }
    )
}

internal fun rebuildGeneratedFallbackSuffix(
    trackId: Long,
    confirmedSourceCount: Int,
    sources: List<SubtitleTranslationSourceEntity>,
    fallback: List<SubtitleFallbackCaptionEntity>
): List<SubtitleEntity> = fallback.flatMap { caption ->
    when {
        caption.lastSourceIndex < confirmedSourceCount -> emptyList()
        caption.firstSourceIndex >= confirmedSourceCount -> listOf(
            SubtitleEntity(
                trackId = trackId,
                startMs = caption.startMs,
                endMs = caption.endMs,
                text = caption.text,
                japaneseText = caption.text
            )
        )
        else -> sources.asSequence()
            .drop(confirmedSourceCount)
            .takeWhile { source -> source.sourceIndex <= caption.lastSourceIndex }
            .map { source ->
                SubtitleEntity(
                    trackId = trackId,
                    startMs = source.startMs,
                    endMs = source.endMs,
                    text = source.text,
                    japaneseText = source.text
                )
            }
            .toList()
    }
}

private fun Gson.generatedSubtitles(json: String): List<GeneratedSubtitle> {
    val type = object : TypeToken<List<GeneratedSubtitle>>() {}.type
    return fromJson<List<GeneratedSubtitle>>(json, type).orEmpty()
}

private fun SubtitleTranslationSourceEntity.toGeneratedSource(): GeneratedSubtitleSource = GeneratedSubtitleSource(
    index = sourceIndex,
    startMs = startMs,
    endMs = endMs,
    text = text
)

private fun SubtitleCommittedCaptionEntity.toGeneratedCaption(): GeneratedSubtitleCaption = GeneratedSubtitleCaption(
    sourceIndices = (firstSourceIndex..lastSourceIndex).toList(),
    startMs = startMs,
    endMs = endMs,
    correctedJapanese = correctedJapanese,
    chineseText = chineseText
)
