package com.asmr.player.benchmark

import androidx.media3.common.MediaItem
import androidx.room.withTransaction
import com.asmr.player.data.local.datastore.SearchCacheStore
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumGroupEntity
import com.asmr.player.data.local.db.entities.AlbumGroupItemEntity
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskItemEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.repository.PlaylistRepository
import com.asmr.player.data.remote.download.DOWNLOAD_STATE_QUEUED
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.playback.PlayerConnection
import com.asmr.player.subtitle.SubtitleItemState
import com.asmr.player.subtitle.SubtitleTranscriptionModels
import com.asmr.player.subtitle.SubtitleTaskMode
import com.asmr.player.subtitle.SubtitleTaskOrigin
import com.asmr.player.subtitle.SubtitleTaskState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class BenchmarkSeedSummary(
    val favoritesPlaylistId: Long = -1L,
    val detailPlaylistId: Long = -1L,
    val detailPlaylistName: String = "",
    val detailGroupId: Long = -1L,
    val detailGroupName: String = "",
    val sampleAlbumId: Long = -1L,
    val sampleTrackId: Long = -1L,
    val sampleMediaId: String = "",
    val sampleUri: String = "",
    val sampleTitle: String = "",
    val sampleArtist: String = "",
    val sampleArtworkUri: String = "",
    val sampleRjCode: String = ""
)

@Singleton
class BenchmarkDataSeeder @Inject constructor(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val searchCacheStore: SearchCacheStore,
    private val playerConnection: PlayerConnection
) {
    companion object {
        private const val AlbumCount = 1_200
        private const val UserPlaylistCount = 1_200
        private const val UserGroupCount = 1_200
        private const val DownloadTaskCount = 180
        private const val DownloadItemsPerTask = 4
        private const val SubtitleTaskCount = 180
        private const val QueueConnectTimeoutMs = 15_000L

        private const val FavoritesPlaylistName = PlaylistRepository.PLAYLIST_FAVORITES
        private const val DetailPlaylistName = "Benchmark Playlist Detail"
        private const val DetailGroupName = "Benchmark Group Detail"
    }

    suspend fun prepareScenario(scenario: BenchmarkScenario): BenchmarkSeedSummary {
        searchCacheStore.clear()
        settingsRepository.setSearchViewMode(0)
        settingsRepository.setLibraryViewMode(
            if (scenario == BenchmarkScenario.LibraryTracks) 2 else 0
        )

        if (scenario != BenchmarkScenario.PerformancePlayback) {
            clearPlayerQueue()
        }

        if (scenario == BenchmarkScenario.SearchNetwork) {
            return BenchmarkSeedSummary()
        }

        val summary = seedLocalData(scenario)
        if (scenario == BenchmarkScenario.Queue) {
            prepareQueue(summary)
        } else if (scenario == BenchmarkScenario.PerformancePlayback) {
            resumeExistingPlayback()
        }
        return summary
    }

    private suspend fun resumeExistingPlayback() {
        withTimeout(QueueConnectTimeoutMs) {
            playerConnection.snapshot
                .filter { it.isConnected }
                .first()
        }
        check((playerConnection.getControllerOrNull()?.mediaItemCount ?: 0) > 0) {
            "Performance playback scenario requires an existing playback queue"
        }
        withContext(Dispatchers.Main) {
            playerConnection.play()
        }
        withTimeout(QueueConnectTimeoutMs) {
            playerConnection.snapshot
                .filter { it.isPlaying }
                .first()
        }
    }

    private suspend fun seedLocalData(scenario: BenchmarkScenario): BenchmarkSeedSummary = withContext(Dispatchers.IO) {
        database.clearAllTables()
        database.withTransaction {
            val albumDao = database.albumDao()
            val trackDao = database.trackDao()
            val playlistDao = database.playlistDao()
            val playlistItemDao = database.playlistItemDao()
            val groupDao = database.albumGroupDao()
            val groupItemDao = database.albumGroupItemDao()
            val downloadDao = database.downloadDao()

            val trackSeeds = ArrayList<BenchmarkTrackSeed>(AlbumCount)

            for (index in 0 until AlbumCount) {
                val ordinal = index + 1
                val albumPath = "/benchmark/albums/$ordinal"
                val rjCode = "RJ${ordinal.toString().padStart(8, '0')}"
                val albumId = albumDao.insertAlbum(
                    AlbumEntity(
                        title = "Benchmark Album $ordinal",
                        path = albumPath,
                        localPath = albumPath,
                        circle = "Circle ${index % 32}",
                        cv = "CV ${index % 24}",
                        workId = "WORK${ordinal.toString().padStart(8, '0')}",
                        rjCode = rjCode,
                        description = "Seeded benchmark album $ordinal"
                    )
                )

                val trackPath = "/benchmark/audio/$ordinal.mp3"
                val trackId = trackDao.insertTrack(
                    TrackEntity(
                        albumId = albumId,
                        title = "Benchmark Track $ordinal",
                        path = trackPath,
                        duration = 180.0 + (index % 90),
                        group = ""
                    )
                )

                trackSeeds += BenchmarkTrackSeed(
                    albumId = albumId,
                    albumTitle = "Benchmark Album $ordinal",
                    albumPath = albumPath,
                    circle = "Circle ${index % 32}",
                    cv = "CV ${index % 24}",
                    workId = "WORK${ordinal.toString().padStart(8, '0')}",
                    rjCode = rjCode,
                    trackId = trackId,
                    trackTitle = "Benchmark Track $ordinal",
                    trackPath = trackPath,
                    duration = 180.0 + (index % 90)
                )
            }

            val favoritesPlaylistId = playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = FavoritesPlaylistName,
                    category = PlaylistRepository.CATEGORY_SYSTEM
                )
            )
            val detailPlaylistId = playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = DetailPlaylistName,
                    category = PlaylistRepository.CATEGORY_USER
                )
            )

            for (index in 0 until UserPlaylistCount) {
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = "Benchmark Playlist ${index + 1}",
                        category = PlaylistRepository.CATEGORY_USER,
                        createdAt = System.currentTimeMillis() - index
                    )
                )
            }

            val favoritesItems = trackSeeds.mapIndexed { index, track ->
                track.toPlaylistItem(
                    playlistId = favoritesPlaylistId,
                    itemOrder = index
                )
            }
            val detailPlaylistItems = trackSeeds.mapIndexed { index, track ->
                track.toPlaylistItem(
                    playlistId = detailPlaylistId,
                    itemOrder = index
                )
            }
            playlistItemDao.upsertItems(favoritesItems + detailPlaylistItems)

            val detailGroupId = groupDao.insertGroup(
                AlbumGroupEntity(name = DetailGroupName)
            )

            val genericGroupItems = ArrayList<AlbumGroupItemEntity>(UserGroupCount)
            for (index in 0 until UserGroupCount) {
                val groupId = groupDao.insertGroup(
                    AlbumGroupEntity(
                        name = "Benchmark Group ${index + 1}",
                        createdAt = System.currentTimeMillis() - index
                    )
                )
                val track = trackSeeds[index % trackSeeds.size]
                genericGroupItems += AlbumGroupItemEntity(
                    groupId = groupId,
                    mediaId = track.trackPath,
                    itemOrder = 0
                )
            }
            val detailGroupItems = trackSeeds.mapIndexed { index, track ->
                AlbumGroupItemEntity(
                    groupId = detailGroupId,
                    mediaId = track.trackPath,
                    itemOrder = index
                )
            }
            groupItemDao.upsertItems(genericGroupItems + detailGroupItems)
            seedDownloadTasks(downloadDao)
            if (scenario == BenchmarkScenario.TranslationTasks) {
                seedSubtitleTasks(trackSeeds.take(SubtitleTaskCount))
            }

            val sample = trackSeeds.first()
            BenchmarkSeedSummary(
                favoritesPlaylistId = favoritesPlaylistId,
                detailPlaylistId = detailPlaylistId,
                detailPlaylistName = DetailPlaylistName,
                detailGroupId = detailGroupId,
                detailGroupName = DetailGroupName,
                sampleAlbumId = sample.albumId,
                sampleTrackId = sample.trackId,
                sampleMediaId = sample.trackPath,
                sampleUri = sample.trackPath,
                sampleTitle = sample.trackTitle,
                sampleArtist = sample.artistLabel,
                sampleArtworkUri = "",
                sampleRjCode = sample.rjCode
            )
        }
    }

    private suspend fun seedDownloadTasks(
        downloadDao: com.asmr.player.data.local.db.dao.DownloadDao
    ) {
        val now = System.currentTimeMillis()
        val states = listOf(
            DOWNLOAD_STATE_QUEUED,
            "RUNNING",
            "SUCCEEDED",
            "FAILED",
            "PAUSED"
        )
        for (taskIndex in 0 until DownloadTaskCount) {
            val ordinal = taskIndex + 1
            val rjCode = "RJ9${ordinal.toString().padStart(7, '0')}"
            val rootDir = "/benchmark/downloads/$rjCode"
            val taskId = downloadDao.insertTask(
                DownloadTaskEntity(
                    taskKey = "benchmark_download_$ordinal",
                    title = rjCode,
                    subtitle = "Benchmark Download Album $ordinal",
                    rootDir = rootDir,
                    albumTitle = "Benchmark Download Album $ordinal",
                    albumCircle = "Download Circle ${taskIndex % 16}",
                    albumCv = "Download CV ${taskIndex % 12}",
                    albumTagsCsv = "ASMR,Earpick,Sleep",
                    albumWorkId = rjCode,
                    albumRjCode = rjCode,
                    createdAt = now - taskIndex,
                    updatedAt = now - taskIndex
                )
            )
            if (taskId <= 0L) continue
            for (itemIndex in 0 until DownloadItemsPerTask) {
                val state = states[(taskIndex + itemIndex) % states.size]
                val totalBytes = 42_000_000L + ((taskIndex + itemIndex) % 12) * 3_000_000L
                val downloadedBytes = when (state) {
                    "SUCCEEDED" -> totalBytes
                    "FAILED" -> totalBytes / 3
                    "PAUSED" -> totalBytes / 2
                    "RUNNING" -> totalBytes / 4
                    else -> 0L
                }
                val relativePath = "track_${(itemIndex + 1).toString().padStart(2, '0')}.mp3"
                val workId = benchmarkDownloadWorkId(taskIndex, itemIndex)
                downloadDao.upsertItem(
                    DownloadItemEntity(
                        taskId = taskId,
                        workId = workId,
                        url = "https://benchmark.invalid/$rjCode/$relativePath",
                        relativePath = relativePath,
                        fileName = relativePath,
                        targetDir = rootDir,
                        filePath = "$rootDir/$relativePath",
                        state = state,
                        downloaded = downloadedBytes,
                        total = totalBytes,
                        speed = if (state == "RUNNING") 1_200_000L + itemIndex * 120_000L else 0L,
                        createdAt = now - taskIndex,
                        updatedAt = now - taskIndex
                    )
                )
            }
        }
    }

    private suspend fun seedSubtitleTasks(tracks: List<BenchmarkTrackSeed>) {
        val dao = database.subtitleTaskDao()
        val now = System.currentTimeMillis()
        val states = listOf(
            SubtitleItemState.TRANSCRIBING,
            SubtitleItemState.TRANSLATING,
            SubtitleItemState.WAITING_SLOT,
            SubtitleItemState.PAUSED,
            SubtitleItemState.FAILED
        )
        tracks.forEachIndexed { index, track ->
            val state = states[index % states.size]
            val taskId = "benchmark-subtitle-task-${index + 1}"
            val translationReady = state != SubtitleItemState.TRANSCRIBING
            val taskState = when (state) {
                SubtitleItemState.PAUSED -> SubtitleTaskState.PAUSED
                SubtitleItemState.FAILED -> SubtitleTaskState.FAILED
                else -> SubtitleTaskState.ACTIVE
            }
            dao.insertTask(
                SubtitleTaskEntity(
                    id = taskId,
                    origin = SubtitleTaskOrigin.GENERATED,
                    title = track.albumTitle,
                    rjCode = track.rjCode,
                    state = taskState,
                    warning = "",
                    createdAt = now - index,
                    updatedAt = now - index
                )
            )
            dao.insertItems(
                listOf(
                    SubtitleTaskItemEntity(
                        id = "benchmark-subtitle-item-${index + 1}",
                        taskId = taskId,
                        trackId = track.trackId,
                        trackTitle = track.trackTitle,
                        trackPath = track.trackPath,
                        mode = SubtitleTaskMode.GENERATED,
                        queueSequence = index + 1L,
                        state = state,
                        suspendedFromState = if (state == SubtitleItemState.PAUSED) {
                            SubtitleItemState.TRANSLATING
                        } else {
                            ""
                        },
                        transcriptionChunkCursor = if (translationReady) 6 else 3,
                        transcriptionProgress = if (translationReady) 100 else 42,
                        transcriptionModelId = SubtitleTranscriptionModels.default.id,
                        transcribedMs = if (translationReady) {
                            (track.duration * 1_000.0).toLong()
                        } else {
                            (track.duration * 420.0).toLong()
                        },
                        totalDurationMs = (track.duration * 1_000.0).toLong(),
                        translationCursor = if (translationReady) 48 else 0,
                        translationTotal = if (translationReady) 180 else 0,
                        translationBatchIndex = if (translationReady) 2 else 0,
                        translationBatchTotal = if (translationReady) 3 else 0,
                        attempt = 0,
                        nextAttemptAt = 0L,
                        errorMessage = if (state == SubtitleItemState.FAILED) "Benchmark 翻译失败" else "",
                        originalHash = "benchmark-original-$index",
                        lastPublishedHash = "benchmark-published-$index",
                        createdAt = now - index,
                        updatedAt = now - index
                    )
                )
            )
        }
    }

    private fun benchmarkDownloadWorkId(
        taskIndex: Int,
        itemIndex: Int
    ): String {
        return UUID.nameUUIDFromBytes("benchmark-download-$taskIndex-$itemIndex".toByteArray()).toString()
    }

    private suspend fun prepareQueue(summary: BenchmarkSeedSummary) {
        val playlistItems = withContext(Dispatchers.IO) {
            database.playlistItemDao().getItemsOnce(summary.detailPlaylistId)
        }
        val mediaItems = playlistItems.map { item ->
            MediaItemFactory.fromDetails(
                mediaId = item.mediaId,
                uri = item.uri,
                title = item.title,
                artist = item.artist,
                artworkUri = item.artworkUri
            )
        }
        withTimeout(QueueConnectTimeoutMs) {
            playerConnection.snapshot
                .filter { it.isConnected }
                .first()
        }
        withContext(Dispatchers.Main) {
            playerConnection.setQueue(
                items = mediaItems,
                startIndex = 0,
                playWhenReady = false
            )
        }
    }

    private suspend fun clearPlayerQueue() {
        withContext(Dispatchers.Main) {
            playerConnection.getControllerOrNull()?.clearMediaItems()
        }
    }
}

private data class BenchmarkTrackSeed(
    val albumId: Long,
    val albumTitle: String,
    val albumPath: String,
    val circle: String,
    val cv: String,
    val workId: String,
    val rjCode: String,
    val trackId: Long,
    val trackTitle: String,
    val trackPath: String,
    val duration: Double
) {
    val artistLabel: String
        get() = when {
            circle.isNotBlank() && cv.isNotBlank() -> "$circle / $cv"
            cv.isNotBlank() -> cv
            circle.isNotBlank() -> circle
            else -> rjCode
        }

    fun toPlaylistItem(
        playlistId: Long,
        itemOrder: Int
    ): PlaylistItemEntity {
        return PlaylistItemEntity(
            playlistId = playlistId,
            mediaId = trackPath,
            title = trackTitle,
            artist = artistLabel,
            uri = trackPath,
            artworkUri = "",
            itemOrder = itemOrder
        )
    }

    fun toAlbum(): Album {
        return Album(
            id = albumId,
            title = albumTitle,
            path = albumPath,
            localPath = albumPath,
            circle = circle,
            cv = cv,
            workId = workId,
            rjCode = rjCode
        )
    }

    fun toTrack(): Track {
        return Track(
            id = trackId,
            albumId = albumId,
            title = trackTitle,
            path = trackPath,
            duration = duration
        )
    }
}
