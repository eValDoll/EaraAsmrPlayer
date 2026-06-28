package com.asmr.player.ui.library

import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyOnlineSavedAlbumSupportTest {
    @Test
    fun shouldBackfillLegacyOnlineSavedAlbumRoot_acceptsOldWebAlbumWithoutLocalPath() {
        val entity = AlbumEntity(
            id = 1L,
            title = "旧保存作品",
            path = "web://rj/RJ123456",
            workId = "RJ123456",
            rjCode = "RJ123456"
        )

        assertTrue(shouldBackfillLegacyOnlineSavedAlbumRoot(entity, emptyList()))
        assertEquals("RJ123456", legacyOnlineSavedAlbumFolderName(entity))
    }

    @Test
    fun shouldBackfillLegacyOnlineSavedAlbumRoot_acceptsOldAlbumWithOnlineTracks() {
        val entity = AlbumEntity(
            id = 2L,
            title = "No RJ Title",
            path = "web://rj/NO_RJ_TITLE"
        )
        val tracks = listOf(
            TrackEntity(
                albumId = 2L,
                title = "track",
                path = "https://example.com/track.mp3"
            )
        )

        assertTrue(shouldBackfillLegacyOnlineSavedAlbumRoot(entity, tracks))
        assertEquals("No RJ Title", legacyOnlineSavedAlbumFolderName(entity))
    }

    @Test
    fun shouldBackfillLegacyOnlineSavedAlbumRoot_skipsAlbumsThatAlreadyHaveLocalRoots() {
        val entity = AlbumEntity(
            id = 3L,
            title = "RJ123456",
            path = "web://rj/RJ123456",
            localPath = "/albums/RJ123456"
        )

        assertFalse(shouldBackfillLegacyOnlineSavedAlbumRoot(entity, emptyList()))
    }

    @Test
    fun shouldBackfillLegacyOnlineSavedAlbumRoot_skipsPureLocalAlbums() {
        val entity = AlbumEntity(
            id = 4L,
            title = "Local",
            path = "/music/Local"
        )

        assertFalse(shouldBackfillLegacyOnlineSavedAlbumRoot(entity, emptyList()))
    }
}
