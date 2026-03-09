package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.asmr.player.data.local.db.entities.AlbumTagEntity
import com.asmr.player.data.local.db.entities.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT COUNT(*) FROM tags")
    suspend fun countTags(): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>): List<Long>

    @Query("SELECT * FROM tags WHERE nameNormalized = :nameNormalized LIMIT 1")
    suspend fun getTagByNormalized(nameNormalized: String): TagEntity?

    @Query("SELECT * FROM tags WHERE nameNormalized IN (:nameNormalizedList)")
    suspend fun getTagsByNormalized(nameNormalizedList: List<String>): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    suspend fun getTagById(id: Long): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbumTags(refs: List<AlbumTagEntity>)

    @Query("DELETE FROM album_tag WHERE albumId = :albumId")
    suspend fun deleteAlbumTagsByAlbumId(albumId: Long)

    @Query("DELETE FROM album_tag WHERE albumId = :albumId AND source = :source")
    suspend fun deleteAlbumTagsByAlbumIdAndSource(albumId: Long, source: Int)

    @Query("DELETE FROM album_tag WHERE albumId = :albumId AND source != :keepSource")
    suspend fun deleteAlbumTagsByAlbumIdExceptSource(albumId: Long, keepSource: Int)

    @Query("DELETE FROM album_tag WHERE albumId = :albumId AND tagId IN (:tagIds)")
    suspend fun deleteAlbumTags(albumId: Long, tagIds: List<Long>)

    @Query("DELETE FROM album_tag WHERE tagId = :tagId")
    suspend fun deleteAlbumTagsByTagId(tagId: Long)

    @Query("UPDATE OR IGNORE album_tag SET tagId = :toTagId WHERE tagId = :fromTagId")
    suspend fun moveAlbumTagsToAnotherTag(fromTagId: Long, toTagId: Long)

    @Query("SELECT DISTINCT albumId FROM album_tag WHERE tagId = :tagId")
    suspend fun getAlbumIdsForTag(tagId: Long): List<Long>

    @Transaction
    suspend fun replaceAlbumTags(albumId: Long, refs: List<AlbumTagEntity>) {
        deleteAlbumTagsByAlbumId(albumId)
        if (refs.isNotEmpty()) insertAlbumTags(refs)
    }

    @Query(
        """
        SELECT t.id AS id, t.name AS name, t.nameNormalized AS nameNormalized,
               (SELECT COUNT(DISTINCT at.albumId) FROM album_tag at WHERE at.tagId = t.id) AS albumCount,
               (
                   (SELECT COUNT(1) FROM album_tag at2 WHERE at2.tagId = t.id AND at2.source = :userSource) +
                   (SELECT COUNT(1) FROM track_tag tt WHERE tt.tagId = t.id AND tt.source = :userSource)
               ) AS userAlbumCount
        FROM tags t
        ORDER BY albumCount DESC, t.nameNormalized ASC
        """
    )
    fun getTagsWithCounts(userSource: Int): Flow<List<TagWithCount>>

    @Query(
        """
        SELECT at.albumId AS albumId,
               GROUP_CONCAT(t.name, ',') AS tagsCsv
        FROM album_tag at
        JOIN tags t ON t.id = at.tagId
        WHERE at.source = :source
        GROUP BY at.albumId
        """
    )
    fun getAlbumTagsBySource(source: Int): Flow<List<AlbumTagsCsv>>

    @Query(
        """
        SELECT GROUP_CONCAT(t.name, ',')
        FROM album_tag at
        JOIN tags t ON t.id = at.tagId
        WHERE at.albumId = :albumId AND at.source = :source
        """
    )
    suspend fun getAlbumTagsCsvOnce(albumId: Long, source: Int): String?

    @Query(
        """
        SELECT t.id AS id, t.name AS name, t.nameNormalized AS nameNormalized,
               (SELECT COUNT(DISTINCT at.albumId) FROM album_tag at WHERE at.tagId = t.id) AS albumCount,
               (
                   (SELECT COUNT(1) FROM album_tag at2 WHERE at2.tagId = t.id AND at2.source = :userSource) +
                   (SELECT COUNT(1) FROM track_tag tt WHERE tt.tagId = t.id AND tt.source = :userSource)
               ) AS userAlbumCount
        FROM tags t
        WHERE t.nameNormalized LIKE :likeQuery ESCAPE '\'
        ORDER BY albumCount DESC, t.nameNormalized ASC
        """
    )
    suspend fun searchTagsWithCountsOnce(likeQuery: String, userSource: Int): List<TagWithCount>

    @Query("UPDATE tags SET name = :newName, nameNormalized = :newNormalized WHERE id = :tagId")
    suspend fun updateTag(tagId: Long, newName: String, newNormalized: String)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: Long)
}
