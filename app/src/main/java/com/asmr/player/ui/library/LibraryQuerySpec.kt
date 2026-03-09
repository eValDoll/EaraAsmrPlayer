package com.asmr.player.ui.library

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

data class LibraryQuerySpec(
    val textQuery: String? = null,
    val includeTagIds: Set<Long> = emptySet(),
    val excludeTagIds: Set<Long> = emptySet(),
    val cvs: Set<String> = emptySet(),
    val circles: Set<String> = emptySet(),
    val source: LibrarySourceFilter? = null,
    val sort: LibrarySort = LibrarySort.AddedDesc
)

enum class LibrarySourceFilter {
    LocalOnly,
    DownloadOnly,
    LocalAndDownload,
    Both
}

enum class LibrarySort {
    AddedDesc,
    TitleAsc,
    RjAsc,
    CircleAsc,
    CvAsc,
    LastPlayedDesc
}

object LibraryQueryBuilder {
    fun build(spec: LibraryQuerySpec): SupportSQLiteQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()

        val hasText = !spec.textQuery.isNullOrBlank()
        val hasIncludeTags = spec.includeTagIds.isNotEmpty()
        val hasExcludeTags = spec.excludeTagIds.isNotEmpty()

        val sql = StringBuilder()
        sql.append("SELECT a.* FROM albums a")
        if (spec.sort == LibrarySort.LastPlayedDesc) {
            sql.append(" LEFT JOIN album_play_stats ps ON ps.albumId = a.id")
        }

        if (hasText) {
            val like = "%${spec.textQuery.orEmpty().trim()}%"
            where.add(
                """
                (
                    a.title LIKE ? OR
                    a.circle LIKE ? OR
                    a.cv LIKE ? OR
                    a.rjCode LIKE ? OR
                    a.workId LIKE ? OR
                    a.tags LIKE ?
                )
                """.trimIndent()
            )
            repeat(6) { args.add(like) }
        }

        if (hasIncludeTags) {
            val placeholders = spec.includeTagIds.joinToString(",") { "?" }
            where.add(
                "a.id IN (SELECT albumId FROM album_tag WHERE tagId IN ($placeholders) GROUP BY albumId HAVING COUNT(DISTINCT tagId) = ?)"
            )
            args.addAll(spec.includeTagIds)
            args.add(spec.includeTagIds.size)
        }

        if (hasExcludeTags) {
            val placeholders = spec.excludeTagIds.joinToString(",") { "?" }
            where.add("a.id NOT IN (SELECT albumId FROM album_tag WHERE tagId IN ($placeholders))")
            args.addAll(spec.excludeTagIds)
        }

        if (spec.cvs.isNotEmpty()) {
            val normalizedExpr = "(',' || REPLACE(REPLACE(REPLACE(REPLACE(IFNULL(a.cv,''), '，', ','), ' ', ''), '　', ''), ',,', ',') || ',')"
            val clauses = spec.cvs.map { "$normalizedExpr LIKE ?" }
            where.add("(${clauses.joinToString(" OR ")})")
            args.addAll(spec.cvs.map { "%," + normalizeCvToken(it) + ",%" })
        }

        if (spec.circles.isNotEmpty()) {
            val placeholders = spec.circles.joinToString(",") { "?" }
            where.add("a.circle IN ($placeholders)")
            args.addAll(spec.circles)
        }

        when (spec.source) {
            LibrarySourceFilter.LocalOnly -> where.add("(a.localPath IS NOT NULL AND a.localPath != '' AND (a.downloadPath IS NULL OR a.downloadPath = ''))")
            LibrarySourceFilter.DownloadOnly -> where.add("(a.downloadPath IS NOT NULL AND a.downloadPath != '' AND (a.localPath IS NULL OR a.localPath = ''))")
            LibrarySourceFilter.LocalAndDownload -> where.add("(a.localPath IS NOT NULL AND a.localPath != '' AND a.downloadPath IS NOT NULL AND a.downloadPath != '')")
            LibrarySourceFilter.Both -> {}
            null -> {}
        }

        if (where.isNotEmpty()) {
            sql.append(" WHERE ")
            sql.append(where.joinToString(" AND "))
        }

        sql.append(" ORDER BY ")
        sql.append(
            when (spec.sort) {
                LibrarySort.AddedDesc -> "a.id DESC"
                LibrarySort.TitleAsc -> "a.title COLLATE NOCASE ASC, a.id DESC"
                LibrarySort.RjAsc -> "a.rjCode COLLATE NOCASE ASC, a.id DESC"
                LibrarySort.CircleAsc -> "a.circle COLLATE NOCASE ASC, a.id DESC"
                LibrarySort.CvAsc -> "a.cv COLLATE NOCASE ASC, a.id DESC"
                LibrarySort.LastPlayedDesc -> "CASE WHEN ps.lastPlayedAt IS NULL THEN 1 ELSE 0 END ASC, ps.lastPlayedAt DESC, a.id DESC"
            }
        )

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    private fun normalizeCvToken(input: String): String {
        return input
            .trim()
            .replace("，", ",")
            .replace(" ", "")
            .replace("　", "")
            .replace(",", "")
            .lowercase()
    }
}
