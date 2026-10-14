package com.asmr.player.data.repository

import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.entities.AlbumTagEntity
import com.asmr.player.data.local.db.entities.TagEntity
import com.asmr.player.data.local.db.entities.TagSource
import com.asmr.player.util.TagNormalizer
import javax.inject.Inject
import javax.inject.Singleton

internal fun buildTagsToken(tagsCsv: String): String {
    return tagsCsv.split(",")
        .map { TagNormalizer.normalize(it) }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" ")
}

internal fun parseAlbumTags(tagsCsv: String): List<Pair<String, String>> {
    return tagsCsv.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it to TagNormalizer.normalize(it) }
        .filter { it.second.isNotBlank() }
        .distinctBy { it.second }
}

/** Owns album tag normalization, persistence and full-text search indexing. */
@Singleton
class AlbumTagRepository @Inject constructor(private val database: AppDatabase) {
    suspend fun upsertAlbumFtsIndex(albumId: Long, entity: AlbumEntity) {
        val userTagsCsv = database.tagDao().getAlbumTagsCsvOnce(albumId, TagSource.USER).orEmpty()
        val combinedTagsCsv = buildString {
            append(entity.tags)
            if (userTagsCsv.isNotBlank()) {
                if (isNotEmpty() && last() != ',') append(',')
                append(userTagsCsv)
            }
        }
        val tagsToken = buildTagsToken(combinedTagsCsv)
        database.albumFtsDao().upsert(
            listOf(
                AlbumFtsEntity(
                    albumId = albumId,
                    title = entity.title,
                    circle = entity.circle,
                    cv = entity.cv,
                    rjCode = entity.rjCode,
                    workId = entity.workId,
                    tagsToken = tagsToken
                )
            )
        )
    }

    suspend fun upsertAlbumTagsFromCsv(albumId: Long, tagsCsv: String, source: Int) = database.withTransaction {
        val tags = parseAlbumTags(tagsCsv)
        if (tags.isEmpty()) return@withTransaction

        val tagEntities = tags.map { (name, normalized) ->
            TagEntity(name = name, nameNormalized = normalized)
        }
        val tagDao = database.tagDao()
        tagDao.insertTags(tagEntities)

        val normalizedList = tags.map { it.second }
        val persisted = tagDao.getTagsByNormalized(normalizedList)
        val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })

        tagDao.deleteAlbumTagsByAlbumIdExceptSource(albumId, TagSource.USER)
        val refs = normalizedList.mapNotNull { normalized ->
            val tagId = idByNormalized[normalized] ?: return@mapNotNull null
            AlbumTagEntity(albumId = albumId, tagId = tagId, source = source)
        }
        if (refs.isNotEmpty()) tagDao.insertAlbumTags(refs)
    }

    suspend fun ensureTagTablesInitialized() = database.withTransaction {
        val tagDao = database.tagDao()
        val tagCount = tagDao.countTags()
        if (tagCount > 0L) return@withTransaction

        val albums = database.albumDao().getAllAlbumsOnce()
        val allPairs = albums.flatMap { parseAlbumTags(it.tags) }
        if (allPairs.isEmpty()) return@withTransaction

        val firstNameByNormalized = LinkedHashMap<String, String>()
        allPairs.forEach { (name, normalized) ->
            if (!firstNameByNormalized.containsKey(normalized)) firstNameByNormalized[normalized] = name
        }

        val tagEntities = firstNameByNormalized.map { (normalized, name) ->
            TagEntity(name = name, nameNormalized = normalized)
        }
        tagDao.insertTags(tagEntities)
        val persisted = tagDao.getTagsByNormalized(firstNameByNormalized.keys.toList())
        val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })

        albums.forEach { album ->
            val pairs = parseAlbumTags(album.tags)
            if (pairs.isEmpty()) return@forEach
            val refs = pairs.mapNotNull { (_, normalized) ->
                val tagId = idByNormalized[normalized] ?: return@mapNotNull null
                AlbumTagEntity(albumId = album.id, tagId = tagId, source = TagSource.AUTO)
            }
            if (refs.isNotEmpty()) {
                tagDao.deleteAlbumTagsByAlbumIdExceptSource(album.id, TagSource.USER)
                tagDao.insertAlbumTags(refs)
            }
        }
    }
}
