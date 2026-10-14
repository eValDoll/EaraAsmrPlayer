package com.asmr.player.data.remote.download


data class RelativeDownloadItem(
    val url: String,
    val relativePath: String,
    val dlsitePlayImageSeed: Int? = null,
    val dlsitePlayImageWidth: Int? = null,
    val dlsitePlayImageHeight: Int? = null,
)

data class DownloadBatchRequest(
    val albumDirectoryName: String,
    val logicalTaskKey: String,
    val items: List<RelativeDownloadItem>,
    val taskSubtitle: String = "",
    val albumTitle: String = "",
    val albumCircle: String = "",
    val albumCv: String = "",
    val albumTagsCsv: String = "",
    val albumCoverUrl: String = "",
    val albumDescription: String = "",
    val albumWorkId: String = "",
    val albumRjCode: String = "",
)

sealed interface EnqueueDownloadBatchResult {
    data class Accepted(val itemCount: Int) : EnqueueDownloadBatchResult
    data object DirectoryUnavailable : EnqueueDownloadBatchResult
    data object TaskBlocked : EnqueueDownloadBatchResult
}
