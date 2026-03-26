package com.asmr.player.benchmark

import androidx.media3.common.MediaItem
import androidx.room.withTransaction
import com.asmr.player.data.local.datastore.SearchCacheStore
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumGroupEntity
import com.asmr.player.data.local.db.entities.AlbumGroupItemEntity
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.repository.PlaylistRepository
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.playback.PlayerConnection
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

        clearPlayerQueue()

        if (scenario == BenchmarkScenario.SearchNetwork) {
            return BenchmarkSeedSummary()
        }

        val summary = seedLocalData()
        if (scenario == BenchmarkScenario.Queue) {
            prepareQueue(summary)
        }
        return summary
    }

    private suspend fun seedLocalData(): BenchmarkSeedSummary = withContext(Dispatchers.IO) {
        database.clearAllTables()
        database.withTransaction {
            val albumDao = database.albumDao()
            val trackDao = database.trackDao()
            val playlistDao = database.playlistDao()
            val playlistItemDao = database.playlistItemDao()
            val groupDao = database.albumGroupDao()
            val groupItemDao = database.albumGroupItemDao()

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
