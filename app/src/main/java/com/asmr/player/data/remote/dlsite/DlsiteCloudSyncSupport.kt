package com.asmr.player.data.remote.dlsite

import com.asmr.player.domain.model.Album

internal const val CLOUD_SYNC_LOCALE_ZH_CN = "zh_CN"
internal const val CLOUD_SYNC_LOCALE_ZH_TW = "zh_TW"
internal const val CLOUD_SYNC_LOCALE_JA_JP = "ja_JP"

private val CLOUD_SYNC_LANGUAGE_PRIORITY = listOf(
    "CHI_HANS" to CLOUD_SYNC_LOCALE_ZH_CN,
    "CHI_HANT" to CLOUD_SYNC_LOCALE_ZH_TW,
    "JPN" to CLOUD_SYNC_LOCALE_JA_JP
)
private val CLOUD_SYNC_TITLE_BLOCK_REGEX = Regex("\u3010[^\u3010\u3011]*\u3011")

internal data class DlsiteCloudSyncAttempt(
    val workno: String,
    val locale: String
)

internal data class DlsiteCloudSyncResolvedDetails(
    val workno: String,
    val locale: String,
    val details: Album
)

internal sealed interface DlsiteCloudSyncSearchResult {
    data class Unique(val workno: String, val locale: String) : DlsiteCloudSyncSearchResult
    data class Ambiguous(val locale: String, val count: Int) : DlsiteCloudSyncSearchResult
    data object NotFound : DlsiteCloudSyncSearchResult
}

internal sealed interface DlsiteCloudSyncResolveResult {
    data class Success(
        val workno: String,
        val locale: String,
        val details: Album
    ) : DlsiteCloudSyncResolveResult

    data object Ambiguous : DlsiteCloudSyncResolveResult
    data object NotFound : DlsiteCloudSyncResolveResult
}

internal fun sanitizeDlsiteCloudSyncKeyword(raw: String): String {
    return CLOUD_SYNC_TITLE_BLOCK_REGEX
        .replace(raw, " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

internal fun buildDlsiteCloudSyncAttempts(
    baseWorkno: String,
    editions: List<DlsiteLanguageEdition>?
): List<DlsiteCloudSyncAttempt> {
    val normalizedBase = baseWorkno.trim().uppercase()
    if (normalizedBase.isBlank()) return emptyList()

    val editionByLang = editions.orEmpty()
        .asSequence()
        .mapNotNull { edition ->
            val lang = edition.lang.trim().uppercase()
            val workno = edition.workno.trim().uppercase()
            if (lang.isBlank() || workno.isBlank()) null else lang to workno
        }
        .toMap()

    val attempts = CLOUD_SYNC_LANGUAGE_PRIORITY.mapNotNull { (lang, locale) ->
        val workno = editionByLang[lang] ?: return@mapNotNull null
        DlsiteCloudSyncAttempt(workno = workno, locale = locale)
    }

    if (attempts.isNotEmpty()) return attempts.distinct()

    return CLOUD_SYNC_LANGUAGE_PRIORITY.map { (_, locale) ->
        DlsiteCloudSyncAttempt(workno = normalizedBase, locale = locale)
    }
}

internal suspend fun searchDlsiteWorknoWithLocaleFallback(
    keyword: String,
    search: suspend (keyword: String, locale: String) -> List<Album>
): DlsiteCloudSyncSearchResult {
    val normalizedKeyword = sanitizeDlsiteCloudSyncKeyword(keyword)
    if (normalizedKeyword.isBlank()) return DlsiteCloudSyncSearchResult.NotFound

    for ((_, locale) in CLOUD_SYNC_LANGUAGE_PRIORITY) {
        val results = runCatching { search(normalizedKeyword, locale) }.getOrNull() ?: continue
        when {
            results.size > 1 -> return DlsiteCloudSyncSearchResult.Ambiguous(locale = locale, count = results.size)
            results.size == 1 -> {
                val workno = results.first().rjCode.trim().uppercase()
                if (workno.isNotBlank()) {
                    return DlsiteCloudSyncSearchResult.Unique(workno = workno, locale = locale)
                }
            }
        }
    }

    return DlsiteCloudSyncSearchResult.NotFound
}

internal suspend fun fetchDlsiteDetailsWithLocaleFallback(
    baseWorkno: String,
    fetchLanguageEditions: suspend (productId: String) -> List<DlsiteLanguageEdition>,
    fetchDetails: suspend (workno: String, locale: String) -> Album?
): DlsiteCloudSyncResolvedDetails? {
    val normalizedBase = baseWorkno.trim().uppercase()
    if (normalizedBase.isBlank()) return null

    val editions = runCatching { fetchLanguageEditions(normalizedBase) }.getOrNull()
    val attempts = buildDlsiteCloudSyncAttempts(normalizedBase, editions)

    for (attempt in attempts) {
        val details = runCatching { fetchDetails(attempt.workno, attempt.locale) }.getOrNull() ?: continue
        return DlsiteCloudSyncResolvedDetails(
            workno = attempt.workno,
            locale = attempt.locale,
            details = details
        )
    }

    return null
}

internal suspend fun resolveDlsiteCloudSync(
    keyword: String,
    baseWorkno: String,
    search: suspend (keyword: String, locale: String) -> List<Album>,
    fetchLanguageEditions: suspend (productId: String) -> List<DlsiteLanguageEdition>,
    fetchDetails: suspend (workno: String, locale: String) -> Album?
): DlsiteCloudSyncResolveResult {
    val normalizedKeyword = keyword.trim()
    var workno = baseWorkno.trim().uppercase()

    if (workno.isBlank()) {
        when (val searchResult = searchDlsiteWorknoWithLocaleFallback(normalizedKeyword, search)) {
            is DlsiteCloudSyncSearchResult.Unique -> workno = searchResult.workno
            is DlsiteCloudSyncSearchResult.Ambiguous -> return DlsiteCloudSyncResolveResult.Ambiguous
            DlsiteCloudSyncSearchResult.NotFound -> return DlsiteCloudSyncResolveResult.NotFound
        }
    }

    fetchDlsiteDetailsWithLocaleFallback(workno, fetchLanguageEditions, fetchDetails)?.let { resolved ->
        return DlsiteCloudSyncResolveResult.Success(
            workno = resolved.workno,
            locale = resolved.locale,
            details = resolved.details
        )
    }

    if (normalizedKeyword.isBlank()) return DlsiteCloudSyncResolveResult.NotFound

    return when (val searchResult = searchDlsiteWorknoWithLocaleFallback(normalizedKeyword, search)) {
        is DlsiteCloudSyncSearchResult.Unique -> {
            if (searchResult.workno.equals(workno, ignoreCase = true)) {
                DlsiteCloudSyncResolveResult.NotFound
            } else {
                val resolved = fetchDlsiteDetailsWithLocaleFallback(searchResult.workno, fetchLanguageEditions, fetchDetails)
                if (resolved != null) {
                    DlsiteCloudSyncResolveResult.Success(
                        workno = resolved.workno,
                        locale = resolved.locale,
                        details = resolved.details
                    )
                } else {
                    DlsiteCloudSyncResolveResult.NotFound
                }
            }
        }

        is DlsiteCloudSyncSearchResult.Ambiguous -> DlsiteCloudSyncResolveResult.Ambiguous
        DlsiteCloudSyncSearchResult.NotFound -> DlsiteCloudSyncResolveResult.NotFound
    }
}

internal fun resolveCloudSyncWorkId(existingWorkId: String, resolvedWorkno: String): String {
    val normalizedResolved = resolvedWorkno.trim().uppercase()
    if (normalizedResolved.isBlank()) return existingWorkId

    val trimmedExisting = existingWorkId.trim()
    return when {
        trimmedExisting.isBlank() -> normalizedResolved
        Regex("""RJ\d+""", RegexOption.IGNORE_CASE).matches(trimmedExisting) -> normalizedResolved
        else -> existingWorkId
    }
}
