package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmr.player.data.local.db.entities.ManualLyricsSourceEntity

@Dao
interface ManualLyricsSourceDao {
    @Query("SELECT * FROM manual_lyrics_sources WHERE exactTargetKey = :exactTargetKey LIMIT 1")
    suspend fun getByExactTargetKey(exactTargetKey: String): ManualLyricsSourceEntity?

    @Query(
        "SELECT * FROM manual_lyrics_sources WHERE fallbackTargetKey = :fallbackTargetKey ORDER BY updatedAt DESC LIMIT 1"
    )
    suspend fun getLatestByFallbackTargetKey(fallbackTargetKey: String): ManualLyricsSourceEntity?

    @Query(
        "SELECT * FROM manual_lyrics_sources WHERE canonicalMediaId = :canonicalMediaId ORDER BY updatedAt DESC LIMIT 1"
    )
    suspend fun getLatestByCanonicalMediaId(canonicalMediaId: String): ManualLyricsSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ManualLyricsSourceEntity): Long

    @Query("DELETE FROM manual_lyrics_sources WHERE exactTargetKey = :exactTargetKey")
    suspend fun deleteByExactTargetKey(exactTargetKey: String)

    @Query("DELETE FROM manual_lyrics_sources WHERE fallbackTargetKey = :fallbackTargetKey")
    suspend fun deleteByFallbackTargetKey(fallbackTargetKey: String)

    @Query("DELETE FROM manual_lyrics_sources WHERE canonicalMediaId = :canonicalMediaId")
    suspend fun deleteByCanonicalMediaId(canonicalMediaId: String)
}
