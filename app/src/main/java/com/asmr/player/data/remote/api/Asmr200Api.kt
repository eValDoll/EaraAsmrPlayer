package com.asmr.player.data.remote.api

import com.google.gson.annotations.SerializedName
import com.asmr.player.data.remote.NetworkHeaders
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface Asmr200Api {
    @GET("search/{keyword}")
    suspend fun search(
        @Path("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("order") order: String = "create_date",
        @Query("sort") sort: String = "desc",
        @Query("pageSize") pageSize: Int = 20,
        @Query("subtitle") subtitle: Int = 0,
        @Query("includeTranslationWorks") includeTranslationWorks: Boolean = true,
        @Header(NetworkHeaders.HEADER_SILENT_IO_ERROR) silentIoError: String? = null
    ): Asmr200SearchResponse

    @GET("work/{workId}")
    suspend fun getWorkDetails(
        @Path("workId") workId: String,
        @Header(NetworkHeaders.HEADER_SILENT_IO_ERROR) silentIoError: String? = null
    ): WorkDetailsResponse

    @GET("tracks/{workId}")
    suspend fun getTracks(
        @Path("workId") workId: String,
        @Header(NetworkHeaders.HEADER_SILENT_IO_ERROR) silentIoError: String? = null
    ): List<AsmrOneTrackNodeResponse>

    companion object {
        const val BASE_URL = "https://api.asmr-200.com/api/"
    }
}

data class Asmr200SearchResponse(
    val works: List<Asmr200Work> = emptyList()
)

data class Asmr200Work(
    val id: Int = 0,
    val source_id: String? = null,
    val title: String? = null,
    val duration: Int? = null,
    @SerializedName(value = "mainCoverUrl", alternate = ["main_cover_url", "main_cover_url_small", "main_cover_url_large"])
    val mainCoverUrl: String? = null,
    val dl_count: Int? = null,
    val price: Int? = null,
    val circle: Circle? = null,
    val name: String? = null,
    val vas: List<Artist>? = null,
    val tags: List<Tag>? = null,
    val original_workno: String? = null,
    val language_editions: List<Asmr200LanguageEdition>? = null
)

data class Asmr200LanguageEdition(
    val lang: String? = null,
    val label: String? = null,
    val workno: String? = null
)
