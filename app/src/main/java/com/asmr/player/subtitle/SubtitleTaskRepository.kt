package com.asmr.player.subtitle

import android.content.Context
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.dao.SubtitleTaskWithItems
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.SubtitleFallbackCaptionEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskItemEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskSnapshotEntity
import com.asmr.player.data.local.db.entities.SubtitleTitleOwnerEntity
import com.asmr.player.data.local.db.entities.SubtitleTitleOwnerKind
import com.asmr.player.data.local.db.entities.SubtitleTranslationSourceEntity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class SubtitleTaskRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabaseProvider.get(appContext)
    private val dao = database.subtitleTaskDao()
    private val enqueueMutex = Mutex()
    private val startupMutex = Mutex()
    @Volatile private var startupReconciled = false

    val tasks: Flow<List<SubtitleTaskUi>> = dao.observeTasks().map { rows ->
        rows.map(SubtitleTaskWithItems::toUi).filter { it.items.isNotEmpty() }
    }

    suspend fun enqueueGeneration(targets: List<SubtitleGenerationTarget>): SubtitleTaskHandle {
        reconcileOnAppLaunch()
        return enqueueMutex.withLock {
            val normalized = targets.asSequence()
                .filter { it.trackId > 0L }
                .distinctBy(SubtitleGenerationTarget::trackId)
                .toList()
            require(normalized.isNotEmpty()) { "没有可转录的音频" }
            val capability = SubtitleDeviceCapability.evaluate(appContext)
            check(capability.supported) { capability.message }
            check(SubtitleModelRepository.get(appContext).isModelAvailable()) {
                SubtitleModelRepository.MODEL_REQUIRED_MESSAGE
            }
            check(DeepSeekApiKeyStore.get(appContext).isConfigured()) {
                "请先在设置中配置 DeepSeek API Key"
            }
            enqueue(
                targets = normalized.map { it.trackId to it.title },
                origin = SubtitleTaskOrigin.GENERATED,
                mode = SubtitleTaskMode.GENERATED
            )
        }
    }

    suspend fun enqueueTranslation(target: SubtitleTranslationTarget): SubtitleTaskHandle {
        reconcileOnAppLaunch()
        return enqueueMutex.withLock {
            require(target.trackId > 0L) { "字幕所属音轨无效" }
            check(DeepSeekApiKeyStore.get(appContext).isConfigured()) {
                "请先在设置中配置 DeepSeek API Key"
            }
            val subtitles = database.trackDao().getSubtitlesForTrack(target.trackId)
            require(subtitles.any { it.text.isNotBlank() }) { "当前音轨没有可翻译的本地字幕" }
            enqueue(
                targets = listOf(target.trackId to target.title),
                origin = SubtitleTaskOrigin.MANUAL_TRANSLATION,
                mode = SubtitleTaskMode.MANUAL
            )
        }
    }

    private suspend fun enqueue(
        targets: List<Pair<Long, String>>,
        origin: String,
        mode: String
    ): SubtitleTaskHandle = withContext(Dispatchers.IO) {
        val existing = targets.mapNotNull { (trackId, _) -> dao.getItemForTrack(trackId) }
        val existingTrackIds = existing.mapTo(mutableSetOf(), SubtitleTaskItemEntity::trackId)
        val targetsToInsert = targets.filterNot { (trackId, _) -> trackId in existingTrackIds }
        if (targetsToInsert.isEmpty()) {
            val first = existing.minBy(SubtitleTaskItemEntity::queueSequence)
            return@withContext SubtitleTaskHandle(
                taskId = first.taskId,
                itemIds = existing.map(SubtitleTaskItemEntity::id),
                reusedExisting = true
            )
        }
        val tracks = database.trackDao().getTracksByIdsOnce(targetsToInsert.map { it.first })
            .associateBy { it.id }
        require(tracks.isNotEmpty()) { "找不到待处理的本地音频" }
        val albums = tracks.values.map { it.albumId }.distinct()
            .mapNotNull { database.albumDao().getAlbumById(it) }
        val taskId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val rjCodes = albums.map { it.rjCode.trim().ifBlank { it.workId.trim() } }
            .filter(String::isNotBlank)
            .distinct()
        val taskTitle = albums.singleOrNull()?.title?.trim().orEmpty().ifBlank {
            if (targetsToInsert.size == 1) targetsToInsert.single().second.ifBlank { "本地音频" } else "${targetsToInsert.size} 项音频"
        }
        val insertedIds = mutableListOf<String>()
        database.withTransaction {
            var sequence = dao.maxQueueSequence()
            val items = targetsToInsert.mapNotNull { (trackId, requestedTitle) ->
                val track = tracks[trackId] ?: return@mapNotNull null
                val itemId = UUID.randomUUID().toString()
                val original = database.trackDao().getSubtitlesForTrack(trackId).sortedWith(SUBTITLE_ORDER)
                val originalHash = subtitleHash(original)
                sequence += 1L
                insertedIds += itemId
                SubtitleTaskItemEntity(
                    id = itemId,
                    taskId = taskId,
                    trackId = trackId,
                    trackTitle = requestedTitle.trim().ifBlank { track.title.trim() }.ifBlank { "本地音频" },
                    trackPath = track.path,
                    mode = mode,
                    queueSequence = sequence,
                    state = if (mode == SubtitleTaskMode.GENERATED) {
                        SubtitleItemState.QUEUED_TRANSCRIPTION
                    } else {
                        SubtitleItemState.QUEUED_TRANSLATION
                    },
                    suspendedFromState = "",
                    transcriptionChunkCursor = 0,
                    transcriptionProgress = if (mode == SubtitleTaskMode.MANUAL) 100 else 0,
                    transcriptionModelId = "",
                    transcribedMs = 0L,
                    totalDurationMs = (track.duration * 1_000.0).toLong().coerceAtLeast(0L),
                    translationCursor = 0,
                    translationTotal = if (mode == SubtitleTaskMode.MANUAL) original.count { it.text.isNotBlank() } else 0,
                    translationBatchIndex = 0,
                    translationBatchTotal = if (mode == SubtitleTaskMode.MANUAL) {
                        fullTranslationRequestCount(original.count { it.text.isNotBlank() })
                    } else {
                        0
                    },
                    attempt = 0,
                    nextAttemptAt = 0L,
                    errorMessage = "",
                    originalHash = originalHash,
                    lastPublishedHash = originalHash,
                    createdAt = now,
                    updatedAt = now
                )
            }
            require(items.isNotEmpty()) { "找不到待处理的本地音频" }
            dao.insertTask(
                SubtitleTaskEntity(
                    id = taskId,
                    origin = origin,
                    title = taskTitle,
                    rjCode = rjCodes.singleOrNull() ?: if (rjCodes.isEmpty()) "未知RJ" else "批量任务",
                    state = SubtitleTaskState.ACTIVE,
                    warning = "",
                    createdAt = now,
                    updatedAt = now
                )
            )
            dao.insertItems(items)
            database.subtitleTitleOwnerDao().upsertAll(
                albums.map { album ->
                    SubtitleTitleOwnerEntity(
                        taskId = taskId,
                        kind = SubtitleTitleOwnerKind.ALBUM,
                        targetId = album.id,
                        displayTitle = "",
                        createdAt = now
                    )
                } + items.map { item ->
                    SubtitleTitleOwnerEntity(
                        taskId = taskId,
                        kind = SubtitleTitleOwnerKind.TRACK,
                        targetId = item.trackId,
                        displayTitle = "",
                        createdAt = now
                    )
                }
            )
            items.forEach { item ->
                val original = database.trackDao().getSubtitlesForTrack(item.trackId).sortedWith(SUBTITLE_ORDER)
                dao.insertSnapshots(original.mapIndexed { index, subtitle ->
                    SubtitleTaskSnapshotEntity(item.id, index, subtitle.startMs, subtitle.endMs, subtitle.text)
                })
                if (mode == SubtitleTaskMode.MANUAL) {
                    val sources = original.mapIndexedNotNull { index, subtitle ->
                        subtitle.text.trim().takeIf(String::isNotEmpty)?.let { text ->
                            SubtitleTranslationSourceEntity(
                                itemId = item.id,
                                sourceIndex = index,
                                startMs = subtitle.startMs,
                                endMs = subtitle.endMs,
                                text = text
                            )
                        }
                    }
                    dao.insertSources(sources)
                    dao.insertFallbackCaptions(sources.mapIndexed { captionIndex, source ->
                        SubtitleFallbackCaptionEntity(
                            itemId = item.id,
                            captionIndex = captionIndex,
                            firstSourceIndex = source.sourceIndex,
                            lastSourceIndex = source.sourceIndex,
                            startMs = source.startMs,
                            endMs = source.endMs,
                            text = source.text
                        )
                    })
                }
            }
        }
        SubtitleTaskService.wake(appContext)
        SubtitleTaskHandle(taskId, existing.map(SubtitleTaskItemEntity::id) + insertedIds, reusedExisting = existing.isNotEmpty())
    }

    suspend fun pauseItem(itemId: String) = mutateItem(itemId) { item ->
        if (item.state in TERMINAL_OR_SUSPENDED_STATES) return@mutateItem item
        item.copy(
            suspendedFromState = item.suspendedFromState.ifBlank { item.state },
            state = SubtitleItemState.PAUSE_REQUESTED,
            errorMessage = "",
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun pauseTask(taskId: String) {
        reconcileOnAppLaunch()
        dao.getItemsForTask(taskId).forEach { pauseItem(it.id) }
        refreshTaskState(taskId)
        SubtitleTaskService.wake(appContext)
    }

    suspend fun resumeItem(itemId: String) = mutateItem(itemId) { item ->
        check(item.state in setOf(SubtitleItemState.PAUSED, SubtitleItemState.INTERRUPTED, SubtitleItemState.FAILED)) {
            "当前字幕任务不能继续"
        }
        val hasSources = dao.getSources(item.id).isNotEmpty()
        item.copy(
            state = SubtitleItemState.resumeState(item.suspendedFromState, hasSources),
            suspendedFromState = "",
            translationBatchIndex = item.translationBatchIndex.coerceAtMost(1),
            translationBatchTotal = fullTranslationRequestCount(item.translationTotal),
            attempt = 0,
            nextAttemptAt = 0L,
            errorMessage = "",
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun resumeTask(taskId: String) {
        reconcileOnAppLaunch()
        dao.getItemsForTask(taskId)
            .filter { it.state in setOf(SubtitleItemState.PAUSED, SubtitleItemState.INTERRUPTED, SubtitleItemState.FAILED) }
            .forEach { resumeItem(it.id) }
        refreshTaskState(taskId)
        SubtitleTaskService.wake(appContext)
    }

    suspend fun retryItem(itemId: String) = resumeItem(itemId)

    suspend fun cancelItem(itemId: String) = mutateItem(itemId) { item ->
        if (item.state in setOf(SubtitleItemState.SUCCEEDED, SubtitleItemState.CANCELED)) return@mutateItem item
        item.copy(
            suspendedFromState = item.state,
            state = SubtitleItemState.CANCEL_REQUESTED,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun cancelTask(taskId: String) {
        reconcileOnAppLaunch()
        dao.getItemsForTask(taskId).forEach { cancelItem(it.id) }
        refreshTaskState(taskId)
        SubtitleTaskService.wake(appContext)
    }

    internal suspend fun finishCancellation(itemId: String): String? = withContext(Dispatchers.IO) {
        var warning: String? = null
        var taskId: String? = null
        database.withTransaction {
            val item = dao.getItem(itemId) ?: return@withTransaction
            if (item.state != SubtitleItemState.CANCEL_REQUESTED) return@withTransaction
            taskId = item.taskId
            val current = database.trackDao().getSubtitlesForTrack(item.trackId).sortedWith(SUBTITLE_ORDER)
            if (taskStillControlsSubtitles(subtitleHash(current), item.lastPublishedHash)) {
                val snapshot = dao.getSnapshots(item.id)
                database.trackDao().deleteSubtitlesForTrack(item.trackId)
                database.trackDao().insertSubtitles(snapshot.map { caption ->
                    SubtitleEntity(
                        trackId = item.trackId,
                        startMs = caption.startMs,
                        endMs = caption.endMs,
                        text = caption.text
                    )
                })
            } else {
                warning = "${item.trackTitle} 的字幕已被修改，已保留当前版本，未恢复任务快照"
            }
            dao.deleteItem(item.id)
            if (dao.countItems(item.taskId) == 0) {
                revertDisplayTitlesForTask(item.taskId)
                dao.deleteTask(item.taskId)
            }
        }
        taskId?.let { refreshTaskState(it) }
        warning
    }

    private suspend fun revertDisplayTitlesForTask(taskId: String) {
        val ownerDao = database.subtitleTitleOwnerDao()
        val albumDao = database.albumDao()
        val trackDao = database.trackDao()
        ownerDao.getByTask(taskId).forEach { owner ->
            val translated = owner.displayTitle
            if (translated.isBlank()) return@forEach
            when (owner.kind) {
                SubtitleTitleOwnerKind.ALBUM -> {
                    val album = albumDao.getAlbumById(owner.targetId) ?: return@forEach
                    if (album.displayTitle == translated) {
                        albumDao.updateAlbumDisplayTitle(owner.targetId, "")
                    }
                }
                SubtitleTitleOwnerKind.TRACK -> {
                    val track = trackDao.getTrackByIdOnce(owner.targetId) ?: return@forEach
                    if (track.displayTitle == translated) {
                        trackDao.updateTrackDisplayTitle(owner.targetId, "")
                    }
                }
            }
        }
    }

    internal suspend fun finishSucceeded(itemId: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val item = dao.getItem(itemId) ?: return@withTransaction
            val taskId = item.taskId
            if (dao.countItems(taskId) == 1) {
                val titleTranslationPending = database.subtitleTitleOwnerDao()
                    .getByTask(taskId)
                    .any { owner -> owner.displayTitle.isBlank() }
                if (titleTranslationPending) return@withTransaction
            }
            dao.deleteItem(itemId)
            if (dao.countItems(taskId) == 0) dao.deleteTask(taskId)
        }
    }

    internal suspend fun finishTitleTranslation(taskId: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val pending = database.subtitleTitleOwnerDao()
                .getByTask(taskId)
                .any { owner -> owner.displayTitle.isBlank() }
            if (pending) return@withTransaction
            val items = dao.getItemsForTask(taskId)
            if (items.isEmpty() || items.any { item -> item.state != SubtitleItemState.SUCCEEDED }) {
                return@withTransaction
            }
            items.forEach { item -> dao.deleteItem(item.id) }
            dao.deleteTask(taskId)
        }
    }

    internal suspend fun reconcileOnAppLaunch() {
        if (startupReconciled) return
        startupMutex.withLock {
            if (startupReconciled) return
            withContext(Dispatchers.IO) {
                val workManager = WorkManager.getInstance(appContext)
                runCatching { workManager.cancelUniqueWork(LEGACY_GENERATION_WORK).result.get() }
                runCatching { workManager.cancelUniqueWork(LEGACY_TRANSLATION_WORK).result.get() }
                runCatching { workManager.cancelAllWorkByTag(LEGACY_GENERATION_WORK).result.get() }
                runCatching { workManager.cancelAllWorkByTag(LEGACY_TRANSLATION_WORK).result.get() }
                val requestDir = File(appContext.noBackupFilesDir, LEGACY_REQUEST_DIRECTORY)
                requestDir.listFiles()?.forEach { file -> if (file.isFile) file.delete() }
                requestDir.delete()
                val now = System.currentTimeMillis()
                dao.getAllItems()
                    .filter { it.state == SubtitleItemState.CANCEL_REQUESTED }
                    .forEach { finishCancellation(it.id) }
                dao.settlePauseRequestsAfterInterruption(now)
                dao.interruptAllIncompleteItems(now, "应用上次运行异常中断，请手动继续")
                dao.markInterruptedTasks(now)
                dao.getAllItems().map(SubtitleTaskItemEntity::taskId).distinct().forEach { refreshTaskState(it) }
            }
            startupReconciled = true
        }
    }

    internal suspend fun refreshTaskState(taskId: String) {
        val task = dao.getTask(taskId) ?: return
        val items = dao.getItemsForTask(taskId)
        if (items.isEmpty()) return
        val state = when {
            items.any { it.state == SubtitleItemState.CANCEL_REQUESTED } -> SubtitleTaskState.CANCEL_REQUESTED
            items.any { it.state == SubtitleItemState.PAUSE_REQUESTED } -> SubtitleTaskState.PAUSE_REQUESTED
            items.all { it.state == SubtitleItemState.PAUSED } -> SubtitleTaskState.PAUSED
            items.any { it.state == SubtitleItemState.INTERRUPTED } -> SubtitleTaskState.INTERRUPTED
            items.any { it.state == SubtitleItemState.FAILED } && items.none { it.state in RUNNING_STATES } -> SubtitleTaskState.FAILED
            else -> SubtitleTaskState.ACTIVE
        }
        dao.updateTask(task.copy(state = state, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun mutateItem(
        itemId: String,
        transform: suspend (SubtitleTaskItemEntity) -> SubtitleTaskItemEntity
    ) = withContext(Dispatchers.IO) {
        reconcileOnAppLaunch()
        val item = dao.getItem(itemId) ?: return@withContext
        val updated = transform(item)
        if (updated != item) dao.updateItem(updated)
        refreshTaskState(item.taskId)
        SubtitleTaskService.wake(appContext)
    }

    companion object {
        private val RUNNING_STATES = setOf(
            SubtitleItemState.QUEUED_TRANSCRIPTION,
            SubtitleItemState.TRANSCRIBING,
            SubtitleItemState.QUEUED_TRANSLATION,
            SubtitleItemState.WAITING_SLOT,
            SubtitleItemState.WAITING_NETWORK,
            SubtitleItemState.RETRY_WAIT,
            SubtitleItemState.TRANSLATING
        )
        private val TERMINAL_OR_SUSPENDED_STATES = setOf(
            SubtitleItemState.SUCCEEDED,
            SubtitleItemState.CANCELED,
            SubtitleItemState.PAUSED,
            SubtitleItemState.INTERRUPTED,
            SubtitleItemState.FAILED,
            SubtitleItemState.PAUSE_REQUESTED,
            SubtitleItemState.CANCEL_REQUESTED
        )
        private const val LEGACY_GENERATION_WORK = "on_device_subtitle_generation"
        private const val LEGACY_TRANSLATION_WORK = "subtitle_file_translation"
        private const val LEGACY_REQUEST_DIRECTORY = "subtitle-generation-requests"

        @Volatile
        private var instance: SubtitleTaskRepository? = null

        fun get(context: Context): SubtitleTaskRepository = instance ?: synchronized(this) {
            instance ?: SubtitleTaskRepository(context).also { instance = it }
        }
    }
}

private fun SubtitleTaskWithItems.toUi(): SubtitleTaskUi = SubtitleTaskUi(
    id = task.id,
    title = task.title,
    rjCode = task.rjCode,
    state = task.state,
    warning = task.warning,
    createdAt = task.createdAt,
    items = items.sortedBy { it.queueSequence }.map { item ->
        SubtitleTaskItemUi(
            id = item.id,
            taskId = item.taskId,
            trackId = item.trackId,
            title = item.trackTitle,
            mode = item.mode,
            state = item.state,
            transcriptionProgress = item.transcriptionProgress,
            transcribedMs = item.transcribedMs,
            totalDurationMs = item.totalDurationMs,
            translationCursor = item.translationCursor,
            translationTotal = item.translationTotal,
            translationBatchIndex = item.translationBatchIndex,
            translationBatchTotal = item.translationBatchTotal,
            attempt = item.attempt,
            nextAttemptAt = item.nextAttemptAt,
            errorMessage = item.errorMessage,
            createdAt = item.createdAt
        )
    }
)
