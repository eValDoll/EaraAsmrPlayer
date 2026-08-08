package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "subtitle_tasks", primaryKeys = ["id"])
data class SubtitleTaskEntity(
    val id: String,
    val origin: String,
    val title: String,
    val rjCode: String,
    val state: String,
    val warning: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "subtitle_task_items",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = SubtitleTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["trackId"], unique = true),
        Index(value = ["state", "queueSequence"]),
        Index(value = ["nextAttemptAt"])
    ]
)
data class SubtitleTaskItemEntity(
    val id: String,
    val taskId: String,
    val trackId: Long,
    val trackTitle: String,
    val trackPath: String,
    val mode: String,
    val queueSequence: Long,
    val state: String,
    val suspendedFromState: String,
    val transcriptionChunkCursor: Int,
    val transcriptionProgress: Int,
    val transcribedMs: Long,
    val totalDurationMs: Long,
    val translationCursor: Int,
    val translationTotal: Int,
    val translationBatchIndex: Int,
    val translationBatchTotal: Int,
    val attempt: Int,
    val nextAttemptAt: Long,
    val errorMessage: String,
    val originalHash: String,
    val lastPublishedHash: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "subtitle_task_snapshots",
    primaryKeys = ["itemId", "captionIndex"],
    foreignKeys = [
        ForeignKey(
            entity = SubtitleTaskItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["itemId"])]
)
data class SubtitleTaskSnapshotEntity(
    val itemId: String,
    val captionIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

@Entity(
    tableName = "subtitle_transcription_chunks",
    primaryKeys = ["itemId", "chunkIndex"],
    foreignKeys = [
        ForeignKey(
            entity = SubtitleTaskItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["itemId"])]
)
data class SubtitleTranscriptionChunkEntity(
    val itemId: String,
    val chunkIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val segmentsJson: String,
    val tokensJson: String,
    val createdAt: Long
)

@Entity(
    tableName = "subtitle_translation_sources",
    primaryKeys = ["itemId", "sourceIndex"],
    foreignKeys = [
        ForeignKey(
            entity = SubtitleTaskItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["itemId"])]
)
data class SubtitleTranslationSourceEntity(
    val itemId: String,
    val sourceIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

@Entity(
    tableName = "subtitle_fallback_captions",
    primaryKeys = ["itemId", "captionIndex"],
    foreignKeys = [
        ForeignKey(
            entity = SubtitleTaskItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["itemId"])]
)
data class SubtitleFallbackCaptionEntity(
    val itemId: String,
    val captionIndex: Int,
    val firstSourceIndex: Int,
    val lastSourceIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

@Entity(
    tableName = "subtitle_committed_captions",
    primaryKeys = ["itemId", "captionIndex"],
    foreignKeys = [
        ForeignKey(
            entity = SubtitleTaskItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["itemId"])]
)
data class SubtitleCommittedCaptionEntity(
    val itemId: String,
    val captionIndex: Int,
    val firstSourceIndex: Int,
    val lastSourceIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val correctedJapanese: String,
    val chineseText: String
)
