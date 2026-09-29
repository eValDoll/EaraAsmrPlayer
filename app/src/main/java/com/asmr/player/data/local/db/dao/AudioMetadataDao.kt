package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmr.player.data.local.db.entities.AudioMetadataEntity

@Dao
interface AudioMetadataDao {
    @Query("SELECT * FROM audio_metadata WHERE sourcePath = :path")
    suspend fun get(path: String): AudioMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: AudioMetadataEntity)
}
