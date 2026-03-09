package com.asmr.player.data.local.db.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity

data class DownloadTaskWithItems(
    @Embedded val task: DownloadTaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val items: List<DownloadItemEntity>
)

