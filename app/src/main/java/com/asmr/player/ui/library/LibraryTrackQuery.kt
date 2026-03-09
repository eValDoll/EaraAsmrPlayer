package com.asmr.player.ui.library

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

object LibraryTrackQueryBuilder {
    fun build(spec: LibraryQuerySpec): SupportSQLiteQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()

        val hasText = !spec.textQuery.isNullOrBlank()
        val hasIncludeTags = spec.includeTagIds.isNotEmpty()
        val hasExcludeTags = spec.excludeTagIds.isNotEmpty()

        val sql = StringBuilder()
        sql.append(
            """
            SELECT
                t.id AS trackId,
                t.albumId AS albumId,
                t.title AS trackTitle,
                t.path AS trackPath,
                t.duration AS duration,
                (
                    EXISTS(SELECT 1 FROM subtitles s WHERE s.trackId = t.id LIMIT 1) OR
                    EXISTS(SELECT 1 FROM remote_subtitle_sources rs WHERE rs.trackId = t.id LIMIT 1)
                ) AS hasSubtitles,
                t.`group` AS trackGroup,
                a.title AS albumTitle,
                a.circle AS circle,
                a.cv AS cv,
                a.coverUrl AS coverUrl,
                CASE WHEN a.coverThumbPath IS NOT NULL AND a.coverThumbPath != '' AND a.coverThumbPath LIKE '%_v2%' THEN a.coverThumbPath ELSE a.coverPath END AS coverPath,
                a.workId AS workId,
                a.rjCode AS rjCode
            FROM tracks t
            JOIN albums a ON a.id = t.albumId
            """.trimIndent()
        )

        if (spec.sort == LibrarySort.LastPlayedDesc) {
            sql.append(" LEFT JOIN album_play_stats ps ON ps.albumId = a.id")
        }

        val effectiveTagsSql = """
            SELECT trackId, tagId FROM track_tag
            UNION ALL
            SELECT t2.id AS trackId, at.tagId AS tagId
            FROM tracks t2
            JOIN album_tag at ON at.albumId = t2.albumId
        """.trimIndent()

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
                    t.title LIKE ? OR
                    EXISTS (
                        SELECT 1
                        FROM ($effectiveTagsSql) et
                        JOIN tags tg ON tg.id = et.tagId
                        WHERE et.trackId = t.id AND tg.name LIKE ?
                        LIMIT 1
                    )
                )
                """.trimIndent()
            )
            repeat(7) { args.add(like) }
        }

        if (hasIncludeTags) {
            val placeholders = spec.includeTagIds.joinToString(",") { "?" }
            where.add(
                "t.id IN (SELECT trackId FROM ($effectiveTagsSql) et WHERE et.tagId IN ($placeholders) GROUP BY trackId HAVING COUNT(DISTINCT et.tagId) = ?)"
            )
            args.addAll(spec.includeTagIds)
            args.add(spec.includeTagIds.size)
        }

        if (hasExcludeTags) {
            val placeholders = spec.excludeTagIds.joinToString(",") { "?" }
            where.add("t.id NOT IN (SELECT DISTINCT trackId FROM ($effectiveTagsSql) et WHERE et.tagId IN ($placeholders))")
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
                LibrarySort.AddedDesc -> "a.id DESC, t.path COLLATE NOCASE ASC, t.id ASC"
                LibrarySort.TitleAsc -> "a.title COLLATE NOCASE ASC, t.path COLLATE NOCASE ASC, t.id ASC"
                LibrarySort.RjAsc -> "a.rjCode COLLATE NOCASE ASC, a.id DESC, t.path COLLATE NOCASE ASC, t.id ASC"
                LibrarySort.CircleAsc -> "a.circle COLLATE NOCASE ASC, a.id DESC, t.path COLLATE NOCASE ASC, t.id ASC"
                LibrarySort.CvAsc -> "a.cv COLLATE NOCASE ASC, a.id DESC, t.path COLLATE NOCASE ASC, t.id ASC"
                LibrarySort.LastPlayedDesc -> "CASE WHEN ps.lastPlayedAt IS NULL THEN 1 ELSE 0 END ASC, ps.lastPlayedAt DESC, a.id DESC, t.path COLLATE NOCASE ASC, t.id ASC"
            }
        )

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    fun buildForAlbum(spec: LibraryQuerySpec, albumId: Long): SupportSQLiteQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()

        val hasText = !spec.textQuery.isNullOrBlank()
        val hasIncludeTags = spec.includeTagIds.isNotEmpty()
        val hasExcludeTags = spec.excludeTagIds.isNotEmpty()

        val sql = StringBuilder()
        sql.append(
            """
            SELECT
                t.id AS trackId,
                t.albumId AS albumId,
                t.title AS trackTitle,
                t.path AS trackPath,
                t.duration AS duration,
                (
                    EXISTS(SELECT 1 FROM subtitles s WHERE s.trackId = t.id LIMIT 1) OR
                    EXISTS(SELECT 1 FROM remote_subtitle_sources rs WHERE rs.trackId = t.id LIMIT 1)
                ) AS hasSubtitles,
                t.`group` AS trackGroup,
                a.title AS albumTitle,
                a.circle AS circle,
                a.cv AS cv,
                a.coverUrl AS coverUrl,
                CASE WHEN a.coverThumbPath IS NOT NULL AND a.coverThumbPath != '' AND a.coverThumbPath LIKE '%_v2%' THEN a.coverThumbPath ELSE a.coverPath END AS coverPath,
                a.workId AS workId,
                a.rjCode AS rjCode
            FROM tracks t
            JOIN albums a ON a.id = t.albumId
            """.trimIndent()
        )

        if (spec.sort == LibrarySort.LastPlayedDesc) {
            sql.append(" LEFT JOIN album_play_stats ps ON ps.albumId = a.id")
        }

        val effectiveTagsSql = """
            SELECT trackId, tagId FROM track_tag
            UNION ALL
            SELECT t2.id AS trackId, at.tagId AS tagId
            FROM tracks t2
            JOIN album_tag at ON at.albumId = t2.albumId
        """.trimIndent()

        where.add("t.albumId = ?")
        args.add(albumId)

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
                    t.title LIKE ? OR
                    EXISTS (
                        SELECT 1
                        FROM ($effectiveTagsSql) et
                        JOIN tags tg ON tg.id = et.tagId
                        WHERE et.trackId = t.id AND tg.name LIKE ?
                        LIMIT 1
                    )
                )
                """.trimIndent()
            )
            repeat(7) { args.add(like) }
        }

        if (hasIncludeTags) {
            val placeholders = spec.includeTagIds.joinToString(",") { "?" }
            where.add(
                "t.id IN (SELECT trackId FROM ($effectiveTagsSql) et WHERE et.tagId IN ($placeholders) GROUP BY trackId HAVING COUNT(DISTINCT et.tagId) = ?)"
            )
            args.addAll(spec.includeTagIds)
            args.add(spec.includeTagIds.size)
        }

        if (hasExcludeTags) {
            val placeholders = spec.excludeTagIds.joinToString(",") { "?" }
            where.add("t.id NOT IN (SELECT DISTINCT trackId FROM ($effectiveTagsSql) et WHERE et.tagId IN ($placeholders))")
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
                LibrarySort.AddedDesc -> "a.id DESC, t.path COLLATE NOCASE ASC, t.id ASC"
                LibrarySort.TitleAsc -> "a.title COLLATE NOCASE ASC, t.path COLLATE NOCASE ASC, t.id ASC"
                LibrarySort.RjAsc -> "a.rjCode COLLATE NOCASE ASC, a.id DESC, t.path COLLATE NOCASE ASC, t.id ASC"
                LibrarySort.CircleAsc -> "a.circle COLLATE NOCASE ASC, a.id DESC, t.path COLLATE NOCASE ASC, t.id ASC"
                LibrarySort.CvAsc -> "a.cv COLLATE NOCASE ASC, a.id DESC, t.path COLLATE NOCASE ASC, t.id ASC"
                LibrarySort.LastPlayedDesc -> "CASE WHEN ps.lastPlayedAt IS NULL THEN 1 ELSE 0 END ASC, ps.lastPlayedAt DESC, a.id DESC, t.path COLLATE NOCASE ASC, t.id ASC"
            }
        )

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    fun buildAlbumHeaders(spec: LibraryQuerySpec): SupportSQLiteQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()

        val hasText = !spec.textQuery.isNullOrBlank()
        val hasIncludeTags = spec.includeTagIds.isNotEmpty()
        val hasExcludeTags = spec.excludeTagIds.isNotEmpty()

        val sql = StringBuilder()
        sql.append(
            """
            SELECT DISTINCT
                a.id AS albumId,
                a.title AS albumTitle,
                a.circle AS circle,
                a.cv AS cv,
                a.coverUrl AS coverUrl,
                CASE WHEN a.coverThumbPath IS NOT NULL AND a.coverThumbPath != '' AND a.coverThumbPath LIKE '%_v2%' THEN a.coverThumbPath ELSE a.coverPath END AS coverPath,
                a.workId AS workId,
                a.rjCode AS rjCode
            FROM tracks t
            JOIN albums a ON a.id = t.albumId
            """.trimIndent()
        )

        if (spec.sort == LibrarySort.LastPlayedDesc) {
            sql.append(" LEFT JOIN album_play_stats ps ON ps.albumId = a.id")
        }

        val effectiveTagsSql = """
            SELECT trackId, tagId FROM track_tag
            UNION ALL
            SELECT t2.id AS trackId, at.tagId AS tagId
            FROM tracks t2
            JOIN album_tag at ON at.albumId = t2.albumId
        """.trimIndent()

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
                    t.title LIKE ? OR
                    EXISTS (
                        SELECT 1
                        FROM ($effectiveTagsSql) et
                        JOIN tags tg ON tg.id = et.tagId
                        WHERE et.trackId = t.id AND tg.name LIKE ?
                        LIMIT 1
                    )
                )
                """.trimIndent()
            )
            repeat(7) { args.add(like) }
        }

        if (hasIncludeTags) {
            val placeholders = spec.includeTagIds.joinToString(",") { "?" }
            where.add(
                "t.id IN (SELECT trackId FROM ($effectiveTagsSql) et WHERE et.tagId IN ($placeholders) GROUP BY trackId HAVING COUNT(DISTINCT et.tagId) = ?)"
            )
            args.addAll(spec.includeTagIds)
            args.add(spec.includeTagIds.size)
        }

        if (hasExcludeTags) {
            val placeholders = spec.excludeTagIds.joinToString(",") { "?" }
            where.add("t.id NOT IN (SELECT DISTINCT trackId FROM ($effectiveTagsSql) et WHERE et.tagId IN ($placeholders))")
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
