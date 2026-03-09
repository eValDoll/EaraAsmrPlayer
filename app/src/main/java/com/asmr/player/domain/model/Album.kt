package com.asmr.player.domain.model

data class Album(
    val id: Long = 0L,
    val title: String,
    val path: String,
    val localPath: String? = null,
    val downloadPath: String? = null,
    val circle: String = "",
    val cv: String = "",
    val tags: List<String> = emptyList(),
    val coverUrl: String = "",
    val coverPath: String = "",
    val coverThumbPath: String = "",
    val workId: String = "",
    val rjCode: String = "",
    val ratingValue: Double? = null,
    val ratingCount: Int = 0,
    val releaseDate: String = "",
    val dlCount: Int = 0,
    val priceJpy: Int = 0,
    val hasAsmrOne: Boolean = false,
    val description: String = "",
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

    fun getGroupedTracks(): Map<String, List<Track>> {
        if (tracks.isEmpty()) return emptyMap()
        val paths = getAllLocalPaths()
        val groups = tracks.groupBy { track ->
            if (track.group.isNotEmpty()) return@groupBy track.group
            val trackPath = track.path
            val matchingRoot = paths.firstOrNull { trackPath.startsWith(it) }
            if (matchingRoot != null) {
                val relative = trackPath.removePrefix(matchingRoot).trimStart('/', '\\')
                relative.firstSegmentOrEmpty()
            } else {
                ""
            }
        }
        return groups.toSortedMap().mapValues { (_, list) ->
            list.sortedBy { it.path }
        }
    }
}

private fun String.firstSegmentOrEmpty(): String {
    val idx1 = indexOf('/')
    val idx2 = indexOf('\\')
    val idx = when {
        idx1 == -1 -> idx2
        idx2 == -1 -> idx1
        else -> minOf(idx1, idx2)
    }
    return if (idx <= 0) "" else substring(0, idx)
}
