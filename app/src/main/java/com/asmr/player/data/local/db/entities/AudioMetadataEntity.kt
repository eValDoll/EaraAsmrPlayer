package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.asmr.player.util.AudioTechnicalMetadata

@Entity(tableName = "audio_metadata")
data class AudioMetadataEntity(
    @PrimaryKey val sourcePath: String,
    val sampleRate: Int,
    val bitrate: Int,
    val channelCount: Int,
    val bitsPerSample: Int,
    val mimeType: String,
    val durationSeconds: Double,
) {
    internal fun toMetadata() = AudioTechnicalMetadata(
        sampleRate, bitrate, channelCount, bitsPerSample, mimeType, durationSeconds,
    )

    companion object {
        internal fun from(path: String, metadata: AudioTechnicalMetadata) = AudioMetadataEntity(
            path, metadata.sampleRate, metadata.bitrate, metadata.channelCount,
            metadata.bitsPerSample, metadata.mimeType, metadata.durationSeconds,
        )
    }
}
