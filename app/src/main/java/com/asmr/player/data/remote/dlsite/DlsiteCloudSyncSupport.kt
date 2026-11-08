package com.asmr.player.data.remote.dlsite

import com.asmr.player.i18n.appString
import com.asmr.player.i18n.AppLanguage
import com.asmr.player.R

import com.asmr.player.domain.model.Album
import com.asmr.player.util.DlsiteWorkNo
import kotlinx.coroutines.CancellationException

internal const val CLOUD_SYNC_LOCALE_ZH_CN = "zh_CN"
internal const val CLOUD_SYNC_LOCALE_ZH_TW = "zh_TW"
internal const val CLOUD_SYNC_LOCALE_JA_JP = "ja_JP"

private val CLOUD_SYNC_LOCALE_PRIORITY = listOf(
    CLOUD_SYNC_LOCALE_ZH_CN,
    CLOUD_SYNC_LOCALE_ZH_TW,
    CLOUD_SYNC_LOCALE_JA_JP
)
private val CLOUD_SYNC_TITLE_BLOCK_REGEX = Regex("\u3010[^\u3010\u3011]*\u3011")

private fun cloudSyncLocales(preferredLocale: String): List<String> =
    (listOf(preferredLocale) + CLOUD_SYNC_LOCALE_PRIORITY).distinct()

internal data class DlsiteCloudSyncCandidate(
    val workno: String,
    val title: String,
    val cv: String,
    val coverUrl: String
)

internal sealed interface DlsiteCloudSyncSearchResult {
    data class Unique(val workno: String, val locale: String) : DlsiteCloudSyncSearchResult
    data class Ambiguous(
        val locale: String,
        val candidates: List<DlsiteCloudSyncCandidate>
    ) : DlsiteCloudSyncSearchResult
    data object NotFound : DlsiteCloudSyncSearchResult
}

internal sealed interface DlsiteCloudSyncResolveResult {
    data class Success(
        val workno: String,
        val locale: String,
        val details: Album
    ) : DlsiteCloudSyncResolveResult

    data class Ambiguous(
        val locale: String,
        val candidates: List<DlsiteCloudSyncCandidate>
    ) : DlsiteCloudSyncResolveResult
    data object NotFound : DlsiteCloudSyncResolveResult
}

internal fun sanitizeDlsiteCloudSyncKeyword(raw: String): String {
    return CLOUD_SYNC_TITLE_BLOCK_REGEX
        .replace(raw, " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

internal suspend fun searchDlsiteWorknoWithLocaleFallback(
    keyword: String,
    preferredLocale: String = AppLanguage.metadataLocale(),
    search: suspend (keyword: String, locale: String) -> List<Album>
): DlsiteCloudSyncSearchResult {
    val normalizedKeyword = sanitizeDlsiteCloudSyncKeyword(keyword)
    if (normalizedKeyword.isBlank()) return DlsiteCloudSyncSearchResult.NotFound

    for (locale in cloudSyncLocales(preferredLocale)) {
        val results = try { search(normalizedKeyword, locale) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { continue }
        val candidates = results.toDlsiteCloudSyncCandidates()
        when {
            candidates.size > 1 -> {
                return DlsiteCloudSyncSearchResult.Ambiguous(locale = locale, candidates = candidates)
            }

            candidates.size == 1 -> {
                return DlsiteCloudSyncSearchResult.Unique(
                    workno = candidates.first().workno,
                    locale = locale
                )
            }
        }
    }

    return DlsiteCloudSyncSearchResult.NotFound
}

private fun List<Album>.toDlsiteCloudSyncCandidates(): List<DlsiteCloudSyncCandidate> {
    return asSequence()
        .mapNotNull { album ->
            val workno = DlsiteWorkNo.normalizeWorkNo(album.rjCode)
                .ifBlank { DlsiteWorkNo.normalizeWorkNo(album.workId) }
            if (workno.isBlank()) return@mapNotNull null
            DlsiteCloudSyncCandidate(
                workno = workno,
                title = album.title.trim().ifBlank { workno },
                cv = album.cv.trim(),
                coverUrl = album.coverUrl.trim()
            )
        }
        .distinctBy { it.workno }
        .toList()
}

internal suspend fun resolveDlsiteCloudSync(
    keyword: String,
    baseWorkno: String,
    preferredLocale: String = AppLanguage.metadataLocale(),
    search: suspend (keyword: String, locale: String) -> List<Album>,
    fetchDetails: suspend (workno: String, locale: String) -> Album?
): DlsiteCloudSyncResolveResult {
    val workno = DlsiteWorkNo.normalizeWorkNo(baseWorkno)
    if (workno.isNotBlank()) return resolveSelectedDlsiteCloudSync(workno, preferredLocale, fetchDetails)

    return when (val searchResult = searchDlsiteWorknoWithLocaleFallback(keyword, preferredLocale, search)) {
        is DlsiteCloudSyncSearchResult.Unique -> resolveSelectedDlsiteCloudSync(searchResult.workno, preferredLocale, fetchDetails)
        is DlsiteCloudSyncSearchResult.Ambiguous -> DlsiteCloudSyncResolveResult.Ambiguous(
            locale = searchResult.locale,
            candidates = searchResult.candidates
        )
        DlsiteCloudSyncSearchResult.NotFound -> DlsiteCloudSyncResolveResult.NotFound
    }
}

internal suspend fun resolveSelectedDlsiteCloudSync(
    workno: String,
    preferredLocale: String = AppLanguage.metadataLocale(),
    fetchDetails: suspend (workno: String, locale: String) -> Album?
): DlsiteCloudSyncResolveResult {
    val normalizedWorkno = DlsiteWorkNo.normalizeWorkNo(workno)
    if (normalizedWorkno.isBlank()) return DlsiteCloudSyncResolveResult.NotFound

    for (locale in cloudSyncLocales(preferredLocale)) {
        val details = try { fetchDetails(normalizedWorkno, locale) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { continue } ?: continue
        val hasMismatchedWorkno = sequenceOf(details.rjCode, details.workId)
            .map { DlsiteWorkNo.normalizeWorkNo(it) }
            .any { it.isNotBlank() && it != normalizedWorkno }
        if (hasMismatchedWorkno) continue
        // Keep the established canonical tag names when fetching English display metadata.
        val canonical = if (locale == "en_US") {
            try {
                fetchDetails(normalizedWorkno, CLOUD_SYNC_LOCALE_ZH_CN)?.takeIf { candidate ->
                    sequenceOf(candidate.rjCode, candidate.workId).all {
                        val id = DlsiteWorkNo.normalizeWorkNo(it)
                        id.isBlank() || id == normalizedWorkno
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        } else null
        return DlsiteCloudSyncResolveResult.Success(
            workno = normalizedWorkno,
            locale = locale,
            details = if (canonical == null) details else details.copy(
                tags = canonical.tags.ifEmpty { details.tags },
                tagDetails = com.asmr.player.domain.model.mergeAlbumTags(canonical.tagDetails, details.tagDetails)
            )
        )
    }
    return DlsiteCloudSyncResolveResult.NotFound
}

internal fun resolveCloudSyncRjCode(existingRjCode: String, existingWorkId: String, resolvedWorkno: String): String {
    val normalizedResolved = DlsiteWorkNo.normalizeWorkNo(resolvedWorkno)
    require(normalizedResolved.isNotBlank()) { appString(R.string.ui_invalid_work_number) }
    val normalizedExistingRj = DlsiteWorkNo.normalizeWorkNo(existingRjCode)
    val currentWorkno = normalizedExistingRj
        .ifBlank { DlsiteWorkNo.normalizeWorkNo(existingWorkId) }
    require(currentWorkno.isBlank() || currentWorkno == normalizedResolved) { appString(R.string.ui_the_work_number_changed_try_syncing_again) }
    return if (normalizedExistingRj.isNotBlank()) existingRjCode else normalizedResolved
}

internal fun resolveCloudSyncWorkId(existingWorkId: String, resolvedWorkno: String): String {
    val normalizedResolved = resolvedWorkno.trim().uppercase()
    if (normalizedResolved.isBlank()) return existingWorkId

    val trimmedExisting = existingWorkId.trim()
    return when {
        trimmedExisting.isBlank() -> normalizedResolved
        DlsiteWorkNo.normalizeWorkNo(trimmedExisting).isNotBlank() -> normalizedResolved
        else -> existingWorkId
    }
}
