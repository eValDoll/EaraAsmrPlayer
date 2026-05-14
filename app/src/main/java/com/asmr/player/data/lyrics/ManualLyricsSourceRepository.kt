package com.asmr.player.data.lyrics

import android.content.Context
import android.net.Uri
import com.asmr.player.data.local.db.dao.ManualLyricsSourceDao
import com.asmr.player.data.local.db.entities.ManualLyricsSourceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManualLyricsSourceRepository @Inject constructor(
    private val manualLyricsSourceDao: ManualLyricsSourceDao,
    @ApplicationContext private val context: Context
) {
    suspend fun findBestMatch(target: LyricsTargetContext): ManualLyricsSourceEntity? {
        return manualLyricsSourceDao.getByExactTargetKey(target.exactTargetKey)
            ?: manualLyricsSourceDao.getLatestByFallbackTargetKey(target.fallbackTargetKey)
            ?: manualLyricsSourceDao.getLatestByCanonicalMediaId(target.canonicalMediaId)
    }

    suspend fun upsert(target: LyricsTargetContext, sourceUri: String): ManualLyricsSourceEntity {
        val now = System.currentTimeMillis()
        val existing = manualLyricsSourceDao.getByExactTargetKey(target.exactTargetKey)
        val displayName = resolveDisplayName(sourceUri)
        val entity = ManualLyricsSourceEntity(
            id = existing?.id ?: 0L,
            exactTargetKey = target.exactTargetKey,
            fallbackTargetKey = target.fallbackTargetKey,
            canonicalMediaId = target.canonicalMediaId,
            sourceUri = sourceUri.trim(),
            displayName = displayName,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        manualLyricsSourceDao.upsert(entity)
        return entity
    }

    suspend fun clearForAutoLyrics(target: LyricsTargetContext) {
        manualLyricsSourceDao.deleteByExactTargetKey(target.exactTargetKey)
        manualLyricsSourceDao.deleteByFallbackTargetKey(target.fallbackTargetKey)
        manualLyricsSourceDao.deleteByCanonicalMediaId(target.canonicalMediaId)
    }

    private fun resolveDisplayName(sourceUri: String): String {
        val trimmed = sourceUri.trim()
        if (trimmed.isBlank()) return ""
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return trimmed.substringAfterLast('/')
        if (uri.scheme.equals("content", ignoreCase = true)) {
            val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
            val resolved = runCatching {
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            }.getOrNull().orEmpty().trim()
            if (resolved.isNotBlank()) return resolved
        }
        return trimmed.substringAfterLast('/').ifBlank { trimmed }
    }
}
