package com.asmr.player.data.repository

import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumTagEntity
import com.asmr.player.data.local.db.entities.TagEntity
import com.asmr.player.data.local.db.entities.TagSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AlbumTagRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AlbumTagRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AlbumTagRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun normalizationPreservesFirstDisplayNameAndDeduplicatesEquivalentTags() {
        assertEquals(
            listOf("ＡＳＭＲ" to "asmr", "耳 かき" to "耳かき", "中文" to "中文"),
            parseAlbumTags(" ＡＳＭＲ, asmr,耳 かき,耳_かき, ,中文 ")
        )
        assertEquals("asmr 耳かき 中文", buildTagsToken(" ＡＳＭＲ, asmr,耳 かき,耳_かき, ,中文 "))
    }

    @Test
    fun refreshingAutomaticTagsPreservesUserTagsAndReplacesOldAutomaticTags() = runBlocking {
        val albumId = database.albumDao().insertAlbum(AlbumEntity(title = "Album", path = "/album"))
        val dao = database.tagDao()
        dao.insertTags(listOf(TagEntity(name = "收藏", nameNormalized = "收藏")))
        val userTagId = checkNotNull(dao.getTagByNormalized("收藏")).id
        dao.insertAlbumTags(listOf(AlbumTagEntity(albumId, userTagId, TagSource.USER)))

        repository.upsertAlbumTagsFromCsv(albumId, "Old", TagSource.SCAN)
        repository.upsertAlbumTagsFromCsv(albumId, "新标签,新_标签,收藏", TagSource.AUTO)

        assertEquals("收藏", dao.getAlbumTagsCsvOnce(albumId, TagSource.USER))
        assertEquals("新标签", dao.getAlbumTagsCsvOnce(albumId, TagSource.AUTO))
        assertNull(dao.getAlbumTagsCsvOnce(albumId, TagSource.SCAN))
    }

    @Test
    fun blankMetadataPreservesExistingTags() = runBlocking {
        val albumId = database.albumDao().insertAlbum(AlbumEntity(title = "Album", path = "/album"))
        repository.upsertAlbumTagsFromCsv(albumId, "耳かき", TagSource.AUTO)
        repository.upsertAlbumTagsFromCsv(albumId, " , ", TagSource.SCAN)
        assertEquals("耳かき", database.tagDao().getAlbumTagsCsvOnce(albumId, TagSource.AUTO))
    }

    @Test
    fun failedReplacementRollsBackBothTagsAndAlbumReferences() = runBlocking {
        val albumId = database.albumDao().insertAlbum(AlbumEntity(title = "Album", path = "/album"))
        repository.upsertAlbumTagsFromCsv(albumId, "旧标签", TagSource.AUTO)
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_album_tags BEFORE INSERT ON album_tag BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )

        val failure = runCatching { repository.upsertAlbumTagsFromCsv(albumId, "新标签", TagSource.SCAN) }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals("旧标签", database.tagDao().getAlbumTagsCsvOnce(albumId, TagSource.AUTO))
        assertNull(database.tagDao().getTagByNormalized("新标签"))
    }

    @Test
    fun ftsIndexIncludesNormalizedMetadataAndUserTags() = runBlocking {
        val entity = AlbumEntity(title = "Album", path = "/album", tags = "ＡＳＭＲ,耳かき")
        val albumId = database.albumDao().insertAlbum(entity)
        database.tagDao().insertTags(listOf(TagEntity(name = "中文", nameNormalized = "中文")))
        val tagId = checkNotNull(database.tagDao().getTagByNormalized("中文")).id
        database.tagDao().insertAlbumTags(listOf(AlbumTagEntity(albumId, tagId, TagSource.USER)))

        repository.upsertAlbumFtsIndex(albumId, entity)

        database.openHelper.readableDatabase.query("SELECT tagsToken FROM album_fts WHERE rowid = $albumId").use {
            assertTrue(it.moveToFirst())
            assertEquals("asmr 耳かき 中文", it.getString(0))
        }
    }

    @Test
    fun initializesLegacyMetadataOnceWithoutDuplicatingNormalizedTags() = runBlocking {
        val first = database.albumDao().insertAlbum(AlbumEntity(title = "First", path = "/first", tags = "ＡＳＭＲ,耳かき"))
        val second = database.albumDao().insertAlbum(AlbumEntity(title = "Second", path = "/second", tags = "asmr"))

        repository.ensureTagTablesInitialized()
        repository.ensureTagTablesInitialized()

        assertEquals(2L, database.tagDao().countTags())
        assertEquals(2, database.tagDao().getAlbumTagsOnce(first).size)
        assertEquals(1, database.tagDao().getAlbumTagsOnce(second).size)
        assertTrue(database.tagDao().getAlbumTagsOnce(first).all { it.source == TagSource.AUTO })
    }
}
