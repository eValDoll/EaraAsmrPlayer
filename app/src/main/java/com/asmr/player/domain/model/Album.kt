package com.asmr.player.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Album(
    val id: Long = 0L,
    val title: String,
    /** 本地库翻译任务写入的显示名覆盖；为空时用 title。仅来自本地库实体，在线作品恒为空。 */
    val displayTitle: String = "",
    val path: String,
    val localPath: String? = null,
    val downloadPath: String? = null,
    val circle: String = "",
    val cv: String = "",
    val tags: List<String> = emptyList(),
    val coverUrl: String = "",
    val coverPath: String = "",
    val manualCoverPath: String = "",
    val coverThumbPath: String = "",
    val workId: String = "",
    val rjCode: String = "",
    val ratingValue: Double? = null,
    val ratingCount: Int = 0,
    val releaseDate: String = "",
    val dlCount: Int = 0,
    val priceJpy: Int = 0,
    val hasAsmrOne: Boolean = false,
    val collectionAvailability: CollectionAvailability? = null,
    /** ASMR.ONE 的数字作品 ID；在线搜索缺少作品编号时用于后台补全。 */
    val asmrOneWorkId: Int? = null,
    val collectedSourceName: String? = null,
    val collectedPageUrl: String? = null,
    val description: String = "",
    val audioTrackCount: Int = 0,
    val audioTotalDuration: Double = 0.0,
    val audioTotalSizeBytes: Long = 0L,
    val tracks: List<Track> = emptyList()
) {
    fun getAllLocalPaths(): List<String> {
        val roots = mutableListOf<String>()
        val p = path.trim()
        if (p.isNotBlank() && !p.startsWith("http", ignoreCase = true) && !p.startsWith("web://", ignoreCase = true)) {
            roots.add(p)
        }
        localPath?.trim()?.takeIf { it.isNotBlank() }?.let { roots.add(it) }
        downloadPath?.trim()?.takeIf { it.isNotBlank() }?.let { roots.add(it) }
        return roots.distinct()
    }

}
