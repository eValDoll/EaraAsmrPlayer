package com.asmr.player.data.remote.crawler

import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS
import com.asmr.player.data.remote.api.Asmr100Api
import com.asmr.player.data.remote.api.Asmr200Api
import com.asmr.player.data.remote.api.AsmrOneApi
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.api.Asmr300Api
import com.asmr.player.data.remote.api.Asmr200Work
import com.asmr.player.data.remote.api.AsmrOneLanguageEdition
import com.asmr.player.data.remote.api.Circle
import com.asmr.player.data.remote.api.Pagination
import com.asmr.player.data.remote.api.SearchResponse
import com.asmr.player.data.remote.api.WorkDetailsResponse
import com.asmr.player.data.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

data class AsmrOneSearchTrace(
    val keyword: String,
    val primarySucceeded: Boolean,
    val primaryHasWorks: Boolean,
    val fallbackAttempted: Boolean,
    val fallbackUsed: Boolean,
    val fallbackSite: Int?
)

data class AsmrOneSearchResult(
    val response: SearchResponse,
    val trace: AsmrOneSearchTrace
)

@Singleton
class AsmrOneCrawler @Inject constructor(
    private val api: AsmrOneApi,
    private val asmr100Api: Asmr100Api,
    private val asmr200Api: Asmr200Api,
    private val asmr300Api: Asmr300Api,
    private val settingsRepository: SettingsRepository
) {
    suspend fun searchPrimaryOnly(keyword: String, page: Int = 1): SearchResponse? {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return null
        return tryTwice { api.search(keyword = normalized, page = page, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON) }
    }

    suspend fun searchBackupPreferredRjIds(keyword: String, page: Int = 1): Set<String> {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return emptySet()
        val preferredSite = runCatching { settingsRepository.asmrOneSite.first() }.getOrDefault(200)
        val backup = selectedBackupApi(preferredSite, asmr100Api, asmr200Api, asmr300Api)
        val from = tryTwice {
            backup.api.search(keyword = normalized, page = page, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
        } ?: return emptySet()
        return from.works
            .asSequence()
            .flatMap { w ->
                sequenceOf(
                    w.source_id.orEmpty(),
                    w.original_workno.orEmpty(),
                    *w.language_editions.orEmpty().map { it.workno.orEmpty() }.toTypedArray()
                )
            }
            .mapNotNull { s -> RJ_CODE_REGEX.find(s.trim().uppercase())?.value }
            .toSet()
    }

    suspend fun searchWithTrace(keyword: String, page: Int = 1): AsmrOneSearchResult {
        val normalized = keyword.trim()
        val primaryTry = runCatching {
            api.search(keyword = normalized, page = page, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
        }
        val primary = primaryTry.getOrNull()
        val primarySucceeded = primaryTry.isSuccess && primary != null
        val primaryHasWorks = primary?.works?.isNotEmpty() == true
        if (primaryHasWorks) {
            return AsmrOneSearchResult(
                response = primary!!,
                trace = AsmrOneSearchTrace(
                    keyword = normalized,
                    primarySucceeded = primarySucceeded,
                    primaryHasWorks = true,
                    fallbackAttempted = false,
                    fallbackUsed = false,
                    fallbackSite = null
                )
            )
        }

        val isRj = normalized.startsWith("RJ", ignoreCase = true)
        if (!isRj) {
            val resp = primary ?: SearchResponse(works = emptyList(), pagination = Pagination(0, 20, page))
            return AsmrOneSearchResult(
                response = resp,
                trace = AsmrOneSearchTrace(
                    keyword = normalized,
                    primarySucceeded = primarySucceeded,
                    primaryHasWorks = false,
                    fallbackAttempted = false,
                    fallbackUsed = false,
                    fallbackSite = null
                )
            )
        }

        val rj = RJ_CODE_REGEX.find(normalized.trim().uppercase())?.value ?: normalized.trim().uppercase()
        val digits = rj.removePrefix("RJ")
        val digitsTrimmed = digits.trimStart('0').ifBlank { digits }
        val digitCandidates = linkedSetOf(digits, digitsTrimmed).filter { it.isNotBlank() }

        val preferredSite = runCatching { settingsRepository.asmrOneSite.first() }.getOrDefault(200)
        val backup = selectedBackupApi(preferredSite, asmr100Api, asmr200Api, asmr300Api)
        for (digitsCandidate in digitCandidates) {
            val from = runCatching {
                backup.api.search(
                    keyword = " $digitsCandidate",
                    page = page,
                    silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON
                )
            }.getOrNull()
            val mapped = mapBackupWorks(from?.works.orEmpty(), rj)
            if (mapped.isNotEmpty()) {
                return AsmrOneSearchResult(
                    response = SearchResponse(
                        works = mapped,
                        pagination = Pagination(totalCount = mapped.size, pageSize = mapped.size, page = page)
                    ),
                    trace = AsmrOneSearchTrace(
                        keyword = normalized,
                        primarySucceeded = primarySucceeded,
                        primaryHasWorks = false,
                        fallbackAttempted = true,
                        fallbackUsed = true,
                        fallbackSite = backup.site
                    )
                )
            }
        }

        val resp = primary ?: SearchResponse(works = emptyList(), pagination = Pagination(0, 20, page))
        return AsmrOneSearchResult(
            response = resp,
            trace = AsmrOneSearchTrace(
                keyword = normalized,
                primarySucceeded = primarySucceeded,
                primaryHasWorks = false,
                fallbackAttempted = true,
                fallbackUsed = false,
                fallbackSite = null
            )
        )
    }

    suspend fun search(keyword: String, page: Int = 1): SearchResponse {
        return searchWithTrace(keyword, page).response
    }

    suspend fun hasWorkOnPrimary(sourceId: String): Boolean? {
        val normalized = sourceId.trim().uppercase()
        val rj = RJ_CODE_REGEX.find(normalized)?.value ?: return null

        fun contains(resp: SearchResponse): Boolean {
            return resp.works.any { w -> asmrOneWorkMatchesRj(w, rj) }
        }

        val resp1 = tryTwice { api.search(keyword = rj, page = 1, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON) }
        if (resp1 != null && contains(resp1)) return true

        val resp2 = tryTwice { api.search(keyword = " $rj", page = 1, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON) }
        if (resp2 != null && contains(resp2)) return true

        if (resp1 == null || resp2 == null) return null
        return false
    }

    suspend fun hasWorkWithFallback(sourceId: String): Boolean? {
        val normalized = sourceId.trim().uppercase()
        val rj = RJ_CODE_REGEX.find(normalized)?.value ?: return null
        val digits = rj.removePrefix("RJ")
        val digitsTrimmed = digits.trimStart('0').ifBlank { digits }
        val digitCandidates = linkedSetOf(digits, digitsTrimmed).filter { it.isNotBlank() }

        fun contains(resp: SearchResponse): Boolean {
            return resp.works.any { w -> asmrOneWorkMatchesRj(w, rj) }
        }

        var anySucceeded = false

        val primary1 = tryTwice { api.search(keyword = rj, page = 1, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON) }
        if (primary1 != null) {
            anySucceeded = true
            if (contains(primary1)) return true
        }
        val primary2 = tryTwice { api.search(keyword = " $rj", page = 1, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON) }
        if (primary2 != null) {
            anySucceeded = true
            if (contains(primary2)) return true
        }

        val preferredSite = runCatching { settingsRepository.asmrOneSite.first() }.getOrDefault(200)
        val backup = selectedBackupApi(preferredSite, asmr100Api, asmr200Api, asmr300Api)
        for (digitsCandidate in digitCandidates) {
            val from = runCatching {
                backup.api.search(keyword = " $digitsCandidate", page = 1, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
            }.getOrNull()
            if (from != null) anySucceeded = true
            val mapped = mapBackupWorks(from?.works.orEmpty(), rj)
            if (mapped.any { w -> asmrOneWorkMatchesRj(w, rj) }) return true
        }

        return if (anySucceeded) false else null
    }

    suspend fun hasWorkFast(sourceId: String, timeoutMs: Long = 2_000L): Boolean? {
        val normalized = sourceId.trim().uppercase()
        val rj = RJ_CODE_REGEX.find(normalized)?.value ?: return null
        val digits = rj.removePrefix("RJ")
        val digitsTrimmed = digits.trimStart('0').ifBlank { digits }
        val digitCandidates = linkedSetOf(digits, digitsTrimmed).filter { it.isNotBlank() }
        if (digitCandidates.isEmpty()) return null

        val preferredSite = runCatching { settingsRepository.asmrOneSite.first() }.getOrDefault(200)
        val backup = selectedBackupApi(preferredSite, asmr100Api, asmr200Api, asmr300Api)
        var anySucceeded = false
        for (digitsCandidate in digitCandidates) {
            val from = runCatching {
                withTimeoutOrNull(timeoutMs) {
                    backup.api.search(
                        keyword = " $digitsCandidate",
                        page = 1,
                        silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON
                    )
                }
            }.getOrNull()
            if (from != null) anySucceeded = true
            val mapped = mapBackupWorks(from?.works.orEmpty(), rj)
            if (mapped.any { w -> asmrOneWorkMatchesRj(w, rj) }) return true
        }
        return if (anySucceeded) false else null
    }

    suspend fun getDetails(workId: String): WorkDetailsResponse {
        val normalized = workId.trim()
        val preferredSite = runCatching { settingsRepository.asmrOneSite.first() }.getOrDefault(200)
        val backup = selectedBackupApi(preferredSite, asmr100Api, asmr200Api, asmr300Api)
        val primaryTry = runCatching {
            withTimeout(PRIMARY_CALL_TIMEOUT_MS) {
                api.getWorkDetails(normalized, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
            }
        }
        val primary = primaryTry.getOrNull()
        if (primaryTry.isSuccess && primary != null) return primary

        var last: Throwable = primaryTry.exceptionOrNull() ?: RuntimeException("unknown error")
        if (last is CancellationException && last !is TimeoutCancellationException) throw last
        val result = runCatching {
            withTimeout(BACKUP_CALL_TIMEOUT_MS) {
                backup.api.getWorkDetails(normalized, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
            }
        }.getOrElse { e ->
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            last = e
            null
        }
        if (result != null) return result
        throw last
    }

    suspend fun getDetailsFromSite(site: Int, workId: String): WorkDetailsResponse {
        val normalized = workId.trim()
        return withTimeout(PRIMARY_CALL_TIMEOUT_MS) {
            when (selectedAsmrOneBackupSite(site)) {
                100 -> asmr100Api.getWorkDetails(normalized, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
                300 -> asmr300Api.getWorkDetails(normalized, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
                else -> asmr200Api.getWorkDetails(normalized, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
            }
        }
    }

    suspend fun getTracks(workId: String): List<AsmrOneTrackNodeResponse> {
        val normalized = workId.trim()
        val preferredSite = runCatching { settingsRepository.asmrOneSite.first() }.getOrDefault(200)
        val backup = selectedBackupApi(preferredSite, asmr100Api, asmr200Api, asmr300Api)
        val primaryTry = runCatching {
            withTimeout(ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS) {
                api.getTracks(normalized, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
            }
        }
        val primary = primaryTry.getOrNull()
        if (primaryTry.isSuccess && primary != null) return primary

        var last: Throwable = primaryTry.exceptionOrNull() ?: RuntimeException("unknown error")
        if (last is CancellationException && last !is TimeoutCancellationException) throw last
        val result = runCatching {
            withTimeout(ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS) {
                backup.api.getTracks(normalized, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
            }
        }.getOrElse { e ->
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            last = e
            null
        }
        if (result != null) return result
        throw last
    }

    suspend fun getTracksFromSite(site: Int, workId: String): List<AsmrOneTrackNodeResponse> {
        val normalized = workId.trim()
        return withTimeout(ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS) {
            getTracksFromSelectedSite(site, normalized)
        }
    }

    private suspend fun getTracksFromSelectedSite(site: Int, workId: String): List<AsmrOneTrackNodeResponse> {
        return when (selectedAsmrOneBackupSite(site)) {
            100 -> asmr100Api.getTracks(workId, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
            300 -> asmr300Api.getTracks(workId, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
            else -> asmr200Api.getTracks(workId, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
        }
    }
}

private val RJ_CODE_REGEX = Regex("""RJ\d{6,}""")
private const val PRIMARY_CALL_TIMEOUT_MS = 1_500L
private const val BACKUP_CALL_TIMEOUT_MS = 1_500L

private suspend fun <T> tryTwice(delayMs: Long = 150L, block: suspend () -> T): T? {
    repeat(2) { idx ->
        val result = runCatching { block() }
        if (result.isSuccess) return result.getOrNull()
        if (idx == 0) delay(delayMs)
    }
    return null
}

private data class AsmrBackupApiEntry(
    val site: Int,
    val api: AsmrBackupApi
)

private interface AsmrBackupApi {
    suspend fun search(
        keyword: String,
        page: Int = 1,
        silentIoError: String? = null
    ): com.asmr.player.data.remote.api.Asmr200SearchResponse
    suspend fun getWorkDetails(workId: String, silentIoError: String? = null): WorkDetailsResponse
    suspend fun getTracks(workId: String, silentIoError: String? = null): List<AsmrOneTrackNodeResponse>
}

private fun Asmr100Api.asBackup(): AsmrBackupApi = object : AsmrBackupApi {
    override suspend fun search(keyword: String, page: Int, silentIoError: String?) =
        this@asBackup.search(keyword = keyword, page = page, silentIoError = silentIoError)
    override suspend fun getWorkDetails(workId: String, silentIoError: String?) =
        this@asBackup.getWorkDetails(workId, silentIoError = silentIoError)
    override suspend fun getTracks(workId: String, silentIoError: String?) =
        this@asBackup.getTracks(workId, silentIoError = silentIoError)
}

private fun Asmr200Api.asBackup(): AsmrBackupApi = object : AsmrBackupApi {
    override suspend fun search(keyword: String, page: Int, silentIoError: String?) =
        this@asBackup.search(keyword = keyword, page = page, silentIoError = silentIoError)
    override suspend fun getWorkDetails(workId: String, silentIoError: String?) =
        this@asBackup.getWorkDetails(workId, silentIoError = silentIoError)
    override suspend fun getTracks(workId: String, silentIoError: String?) =
        this@asBackup.getTracks(workId, silentIoError = silentIoError)
}

private fun Asmr300Api.asBackup(): AsmrBackupApi = object : AsmrBackupApi {
    override suspend fun search(keyword: String, page: Int, silentIoError: String?) =
        this@asBackup.search(keyword = keyword, page = page, silentIoError = silentIoError)
    override suspend fun getWorkDetails(workId: String, silentIoError: String?) =
        this@asBackup.getWorkDetails(workId, silentIoError = silentIoError)
    override suspend fun getTracks(workId: String, silentIoError: String?) =
        this@asBackup.getTracks(workId, silentIoError = silentIoError)
}

internal fun selectedAsmrOneBackupSite(preferredSite: Int): Int {
    return when (preferredSite) {
        100, 300 -> preferredSite
        else -> 200
    }
}

private fun selectedBackupApi(
    preferredSite: Int,
    asmr100Api: Asmr100Api,
    asmr200Api: Asmr200Api,
    asmr300Api: Asmr300Api
): AsmrBackupApiEntry {
    return when (val selectedSite = selectedAsmrOneBackupSite(preferredSite)) {
        100 -> AsmrBackupApiEntry(selectedSite, asmr100Api.asBackup())
        300 -> AsmrBackupApiEntry(selectedSite, asmr300Api.asBackup())
        else -> AsmrBackupApiEntry(selectedSite, asmr200Api.asBackup())
    }
}

private fun mapBackupWorks(works: List<Asmr200Work>, normalizedRj: String): List<WorkDetailsResponse> {
    return works.mapNotNull { w ->
        if (w.id <= 0) return@mapNotNull null
        val mappedSourceId = run {
            val editions = w.language_editions.orEmpty()
            val hasEdition = editions.any { it.workno?.trim()?.equals(normalizedRj, ignoreCase = true) == true }
            if (hasEdition) normalizedRj else w.source_id.orEmpty().ifBlank { normalizedRj }
        }
        WorkDetailsResponse(
            id = w.id,
            source_id = mappedSourceId,
            original_workno = w.original_workno,
            language_editions = w.language_editions?.map { AsmrOneLanguageEdition(lang = it.lang, label = it.label, workno = it.workno) },
            title = w.title.orEmpty(),
            circle = w.circle ?: w.name?.takeIf { it.isNotBlank() }?.let { Circle(it) },
            vas = w.vas,
            tags = w.tags,
            duration = w.duration ?: 0,
            mainCoverUrl = w.mainCoverUrl.orEmpty(),
            dl_count = w.dl_count ?: 0,
            price = w.price ?: 0
        )
    }
}

internal fun asmrOneWorkMatchesRj(w: WorkDetailsResponse, rj: String): Boolean {
    val sid = RJ_CODE_REGEX.find(w.source_id.trim().uppercase())?.value
    if (sid == rj) return true
    val ow = RJ_CODE_REGEX.find(w.original_workno.orEmpty().trim().uppercase())?.value
    if (ow == rj) return true
    val editions = w.language_editions.orEmpty()
    return editions.any { e ->
        val workno = RJ_CODE_REGEX.find(e.workno.orEmpty().trim().uppercase())?.value
        workno == rj
    }
}
