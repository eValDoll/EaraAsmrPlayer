package com.asmr.player.data.remote.crawler

import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS
import com.asmr.player.data.remote.api.Asmr100Api
import com.asmr.player.data.remote.api.Asmr200Api
import com.asmr.player.data.remote.api.Asmr200Work
import com.asmr.player.data.remote.api.Asmr300Api
import com.asmr.player.data.remote.api.AsmrOneApi
import com.asmr.player.data.remote.api.AsmrOneEndpoint
import com.asmr.player.data.remote.api.AsmrOneLanguageEdition
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.api.Circle
import com.asmr.player.data.remote.api.Pagination
import com.asmr.player.data.remote.api.SearchResponse
import com.asmr.player.data.remote.api.WorkDetailsResponse
import com.asmr.player.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

data class AsmrOneSearchTrace(
    val keyword: String,
    val site: Int
)

data class AsmrOneSearchResult(
    val response: SearchResponse,
    val trace: AsmrOneSearchTrace
)

data class AsmrOneTracksResult(
    val tree: List<AsmrOneTrackNodeResponse>,
    val site: Int?
)

@Singleton
class AsmrOneCrawler @Inject constructor(
    private val asmrOneApi: AsmrOneApi,
    private val asmr100Api: Asmr100Api,
    private val asmr200Api: Asmr200Api,
    private val asmr300Api: Asmr300Api,
    private val settingsRepository: SettingsRepository
) {
    suspend fun searchWithTrace(keyword: String, page: Int = 1): AsmrOneSearchResult {
        val normalized = keyword.trim()
        val selected = selectedMetadataApi()
        if (normalized.isBlank()) {
            return emptySearchResult(normalized, page, selected.site)
        }

        val response = runCatching {
            withTimeout(ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS) {
                selected.api.search(
                    keyword = normalized,
                    page = page,
                    silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON
                )
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            null
        }
        return AsmrOneSearchResult(
            response = response ?: emptySearchResponse(page),
            trace = AsmrOneSearchTrace(keyword = normalized, site = selected.site)
        )
    }

    suspend fun getDetails(workId: String): WorkDetailsResponse {
        val normalized = workId.trim()
        require(normalized.isNotBlank()) { "asmr.one work id is blank" }
        val selected = selectedMetadataApi()
        return withTimeout(ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS) {
            selected.api.getWorkDetails(
                workId = normalized,
                silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON
            )
        }
    }

    suspend fun getTracksWithTrace(workId: String): AsmrOneTracksResult {
        val normalized = workId.trim()
        if (normalized.isBlank()) return AsmrOneTracksResult(emptyList(), null)
        val selectedSite = currentSite()
        return fetchAsmrOneTracksFromSelectedSite(
            preferredSite = selectedSite,
            fetchSelected = { site ->
                withTimeout(ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS) {
                    getTracksFromSelectedSite(site, normalized)
                }
            }
        )
    }

    suspend fun selectedEndpoint(): Int = currentSite()

    private suspend fun currentSite(): Int {
        val configured = runCatching { settingsRepository.asmrOneSite.first() }.getOrDefault(200)
        return AsmrOneEndpoint.normalize(configured)
    }

    private suspend fun selectedMetadataApi(): AsmrSelectedApiEntry {
        return selectedApi(
            preferredSite = resolveAsmrOneMetadataEndpoint(currentSite()),
            asmrOneApi = asmrOneApi,
            asmr100Api = asmr100Api,
            asmr200Api = asmr200Api,
            asmr300Api = asmr300Api
        )
    }

    private suspend fun getTracksFromSelectedSite(
        site: Int,
        workId: String
    ): List<AsmrOneTrackNodeResponse> {
        return selectedApi(site, asmrOneApi, asmr100Api, asmr200Api, asmr300Api)
            .api
            .getTracks(workId, silentIoError = NetworkHeaders.SILENT_IO_ERROR_ON)
    }
}

private val RJ_CODE_REGEX = Regex("""RJ\d{6,}""")

internal fun resolveAsmrOneMetadataEndpoint(preferredSite: Int): Int {
    val normalized = AsmrOneEndpoint.normalize(preferredSite)
    return if (normalized == AsmrOneEndpoint.BACKUP) {
        AsmrOneEndpoint.MIRROR_200
    } else {
        normalized
    }
}

private fun emptySearchResult(keyword: String, page: Int, site: Int): AsmrOneSearchResult {
    return AsmrOneSearchResult(
        response = SearchResponse(
            works = emptyList(),
            pagination = Pagination(totalCount = 0, pageSize = 0, page = page)
        ),
        trace = AsmrOneSearchTrace(keyword = keyword, site = site)
    )
}

private fun emptySearchResponse(page: Int): SearchResponse {
    return SearchResponse(
        works = emptyList(),
        pagination = Pagination(totalCount = 0, pageSize = 0, page = page)
    )
}

private data class AsmrSelectedApiEntry(
    val site: Int,
    val api: AsmrSelectedApi
)

private interface AsmrSelectedApi {
    suspend fun search(
        keyword: String,
        page: Int = 1,
        silentIoError: String? = null
    ): SearchResponse

    suspend fun getWorkDetails(
        workId: String,
        silentIoError: String? = null
    ): WorkDetailsResponse

    suspend fun getTracks(
        workId: String,
        silentIoError: String? = null
    ): List<AsmrOneTrackNodeResponse>
}

private fun AsmrOneApi.asSelected(): AsmrSelectedApi = object : AsmrSelectedApi {
    override suspend fun search(keyword: String, page: Int, silentIoError: String?) =
        this@asSelected.search(keyword = keyword, page = page, silentIoError = silentIoError)

    override suspend fun getWorkDetails(workId: String, silentIoError: String?) =
        this@asSelected.getWorkDetails(workId, silentIoError = silentIoError)

    override suspend fun getTracks(workId: String, silentIoError: String?) =
        this@asSelected.getTracks(workId, silentIoError = silentIoError)
}

private fun Asmr100Api.asSelected(): AsmrSelectedApi = object : AsmrSelectedApi {
    override suspend fun search(keyword: String, page: Int, silentIoError: String?) =
        mapMirrorSearchResponse(
            response = this@asSelected.search(keyword = keyword, page = page, silentIoError = silentIoError),
            keyword = keyword,
            page = page
        )

    override suspend fun getWorkDetails(workId: String, silentIoError: String?) =
        this@asSelected.getWorkDetails(workId, silentIoError = silentIoError)

    override suspend fun getTracks(workId: String, silentIoError: String?) =
        this@asSelected.getTracks(workId, silentIoError = silentIoError)
}

private fun Asmr200Api.asSelected(): AsmrSelectedApi = object : AsmrSelectedApi {
    override suspend fun search(keyword: String, page: Int, silentIoError: String?) =
        mapMirrorSearchResponse(
            response = this@asSelected.search(keyword = keyword, page = page, silentIoError = silentIoError),
            keyword = keyword,
            page = page
        )

    override suspend fun getWorkDetails(workId: String, silentIoError: String?) =
        this@asSelected.getWorkDetails(workId, silentIoError = silentIoError)

    override suspend fun getTracks(workId: String, silentIoError: String?) =
        this@asSelected.getTracks(workId, silentIoError = silentIoError)
}

private fun Asmr300Api.asSelected(): AsmrSelectedApi = object : AsmrSelectedApi {
    override suspend fun search(keyword: String, page: Int, silentIoError: String?) =
        mapMirrorSearchResponse(
            response = this@asSelected.search(keyword = keyword, page = page, silentIoError = silentIoError),
            keyword = keyword,
            page = page
        )

    override suspend fun getWorkDetails(workId: String, silentIoError: String?) =
        this@asSelected.getWorkDetails(workId, silentIoError = silentIoError)

    override suspend fun getTracks(workId: String, silentIoError: String?) =
        this@asSelected.getTracks(workId, silentIoError = silentIoError)
}

internal suspend fun fetchAsmrOneTracksFromSelectedSite(
    preferredSite: Int,
    fetchSelected: suspend (Int) -> List<AsmrOneTrackNodeResponse>
): AsmrOneTracksResult {
    val selectedSite = AsmrOneEndpoint.normalize(preferredSite)
    return AsmrOneTracksResult(
        tree = fetchSelected(selectedSite),
        site = selectedSite
    )
}

private fun selectedApi(
    preferredSite: Int,
    asmrOneApi: AsmrOneApi,
    asmr100Api: Asmr100Api,
    asmr200Api: Asmr200Api,
    asmr300Api: Asmr300Api
): AsmrSelectedApiEntry {
    return when (val selectedSite = AsmrOneEndpoint.normalize(preferredSite)) {
        AsmrOneEndpoint.BACKUP -> error("Eara backup does not provide the direct ASMR.one API")
        AsmrOneEndpoint.MAIN -> AsmrSelectedApiEntry(selectedSite, asmrOneApi.asSelected())
        AsmrOneEndpoint.MIRROR_100 -> AsmrSelectedApiEntry(selectedSite, asmr100Api.asSelected())
        AsmrOneEndpoint.MIRROR_300 -> AsmrSelectedApiEntry(selectedSite, asmr300Api.asSelected())
        else -> AsmrSelectedApiEntry(selectedSite, asmr200Api.asSelected())
    }
}

private fun mapMirrorSearchResponse(
    response: com.asmr.player.data.remote.api.Asmr200SearchResponse,
    keyword: String,
    page: Int
): SearchResponse {
    val normalizedRj = RJ_CODE_REGEX.find(keyword.trim().uppercase())?.value.orEmpty()
    val works = mapBackupWorks(response.works, normalizedRj)
    return SearchResponse(
        works = works,
        pagination = Pagination(totalCount = works.size, pageSize = works.size, page = page)
    )
}

private fun mapBackupWorks(
    works: List<Asmr200Work>,
    normalizedRj: String
): List<WorkDetailsResponse> {
    return works.mapNotNull { work ->
        if (work.id <= 0) return@mapNotNull null
        val mappedSourceId = run {
            val hasMatchingEdition = normalizedRj.isNotBlank() && work.language_editions.orEmpty().any { edition ->
                edition.workno?.trim()?.equals(normalizedRj, ignoreCase = true) == true
            }
            if (hasMatchingEdition) {
                normalizedRj
            } else {
                work.source_id.orEmpty().ifBlank { normalizedRj }
            }
        }
        WorkDetailsResponse(
            id = work.id,
            source_id = mappedSourceId,
            original_workno = work.original_workno,
            language_editions = work.language_editions?.map { edition ->
                AsmrOneLanguageEdition(
                    lang = edition.lang,
                    label = edition.label,
                    workno = edition.workno
                )
            },
            title = work.title.orEmpty(),
            circle = work.circle ?: work.name?.takeIf { it.isNotBlank() }?.let(::Circle),
            vas = work.vas,
            tags = work.tags,
            duration = work.duration ?: 0,
            mainCoverUrl = work.mainCoverUrl.orEmpty(),
            dl_count = work.dl_count ?: 0,
            price = work.price ?: 0
        )
    }
}

fun asmrOneWorkMatchesRj(work: WorkDetailsResponse, rj: String): Boolean {
    val normalized = rj.trim().uppercase()
    if (normalized.isBlank()) return false
    if (work.source_id.trim().uppercase() == normalized) return true
    if (work.original_workno.orEmpty().trim().uppercase() == normalized) return true
    return work.language_editions.orEmpty().any { edition ->
        edition.workno.orEmpty().trim().uppercase() == normalized
    }
}
