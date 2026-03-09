package com.asmr.player.data.remote.api

import com.asmr.player.data.remote.NetworkHeaders
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface Asmr100Api {
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
        const val BASE_URL = "https://api.asmr-100.com/api/"
    }
}

