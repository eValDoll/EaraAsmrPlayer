package com.asmr.player.data.repository

import com.asmr.player.data.remote.api.AsmrOneAvailabilityApi
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncResolveResult
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.AlbumTag
import com.asmr.player.domain.model.localizedTags
import com.asmr.player.domain.model.mergeAlbumTags
import com.asmr.player.i18n.AppLanguage
import com.asmr.player.util.DlsiteWorkNo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncTagRepository internal constructor(
    private val fetchDictionary: suspend () -> List<AlbumTag>,
    private val nowMillis: () -> Long,
) {
    @Inject constructor(api: AsmrOneAvailabilityApi) : this(api::getTagDictionary, System::currentTimeMillis)

    private val mutex = Mutex()
    private var dictionary: Map<String, List<AlbumTag>> = emptyMap()
    private var retryAt = 0L

    private suspend fun dictionary(): Map<String, List<AlbumTag>> = mutex.withLock {
        if (nowMillis() < retryAt) return@withLock dictionary
        try {
            dictionary = fetchDictionary().flatMap { tag ->
                (listOf(tag.originalName) + tag.names.orEmpty().values).distinct().map { it to tag }
            }.groupBy({ it.first }, { it.second })
            retryAt = nowMillis() + 3_600_000L
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A batch shares successful data and a brief failure backoff.
            retryAt = nowMillis() + 30_000L
        }
        dictionary
    }

    internal suspend fun enrich(
        result: DlsiteCloudSyncResolveResult,
        fallback: Album,
        locale: String = AppLanguage.metadataLocale(),
    ): DlsiteCloudSyncResolveResult {
        if (result is DlsiteCloudSyncResolveResult.Ambiguous) return result
        val success = result as? DlsiteCloudSyncResolveResult.Success
        val workno = success?.workno ?: DlsiteWorkNo.normalizeWorkNo(fallback.rjCode.ifBlank { fallback.workId })
        if (workno.isBlank()) return result
        val details = success?.details ?: fallback
        val before = details.copy(
            tags = details.tags.ifEmpty { fallback.tags },
            tagDetails = mergeAlbumTags(fallback.tagDetails, details.tagDetails),
        )
        val missing = before.tags.filter { label ->
            before.tagDetails.orEmpty().none { tag ->
                (tag.originalName == label || label in tag.names.orEmpty().values) && tag.localizedName(locale) != null
            }
        }
        val additions = if (missing.isEmpty()) emptyList() else {
            val aliases = dictionary()
            missing.flatMap { aliases[it].orEmpty() }
        }
        val enriched = before.copy(tagDetails = mergeAlbumTags(before.tagDetails, additions))
        if (success == null && before.localizedTags(locale) == enriched.localizedTags(locale)) return result
        return DlsiteCloudSyncResolveResult.Success(workno, success?.locale ?: locale, enriched)
    }
}
