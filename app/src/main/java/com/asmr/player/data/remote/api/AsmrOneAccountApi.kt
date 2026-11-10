package com.asmr.player.data.remote.api

import com.asmr.player.domain.model.Album
import com.google.gson.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface AsmrOneAccountApi {
    @POST("auth/me")
    suspend fun login(@Body credentials: AsmrLoginRequest): AsmrAuthResponse

    @GET("auth/me")
    suspend fun me(@Header("Authorization") authorization: String): AsmrAuthResponse

    @GET("playlist/get-playlists")
    suspend fun playlists(@Header("Authorization") authorization: String, @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 20, @Query("filterBy") filter: String = "all"): AsmrPlaylistPage

    @GET("playlist/get-playlist-metadata")
    suspend fun metadata(@Header("Authorization") authorization: String, @Query("id") id: String): AsmrCloudPlaylist

    @GET("playlist/get-playlist-works")
    suspend fun works(@Header("Authorization") authorization: String, @Query("id") id: String,
        @Query("page") page: Int, @Query("pageSize") pageSize: Int = 20): AsmrWorkPage

    @GET("playlist/get-work-exist-status-in-my-playlists")
    suspend fun membership(@Header("Authorization") authorization: String, @Query("workID") workId: Long,
        @Query("page") page: Int, @Query("pageSize") pageSize: Int = 20,
        @Query("version") version: Int = 2): AsmrPlaylistPage

    @POST("playlist/create-playlist")
    suspend fun create(@Header("Authorization") authorization: String, @Body request: AsmrCreatePlaylist): JsonElement

    @POST("playlist/edit-playlist-metadata")
    suspend fun edit(@Header("Authorization") authorization: String, @Body request: AsmrEditPlaylist): JsonElement

    @POST("playlist/delete-playlist")
    suspend fun delete(@Header("Authorization") authorization: String, @Body request: AsmrPlaylistId): JsonElement

    @POST("playlist/add-works-to-playlist")
    suspend fun add(@Header("Authorization") authorization: String, @Body request: AsmrPlaylistWorks): JsonElement

    @POST("playlist/remove-works-from-playlist")
    suspend fun remove(@Header("Authorization") authorization: String, @Body request: AsmrPlaylistWorks): JsonElement

    @POST("playlist/like-playlist")
    suspend fun like(@Header("Authorization") authorization: String, @Body request: AsmrPlaylistId): JsonElement

    @POST("playlist/remove-like-playlist")
    suspend fun unlike(@Header("Authorization") authorization: String, @Body request: AsmrPlaylistId): JsonElement

    @GET("review")
    suspend fun reviews(@Header("Authorization") authorization: String, @Query("page") page: Int,
        @Query("filter") progress: String? = null, @Query("order") order: String = "updated_at",
        @Query("sort") sort: String = "desc"): AsmrWorkPage

    @GET("work/{id}")
    suspend fun work(@Header("Authorization") authorization: String, @Path("id") id: Long): AsmrCloudWork

    @GET("search/{keyword}")
    suspend fun searchWork(@Header("Authorization") authorization: String, @Path("keyword") keyword: String,
        @Query("page") page: Int = 1): AsmrWorkPage

    @PUT("review")
    suspend fun progress(@Header("Authorization") authorization: String, @Body request: AsmrProgressRequest): JsonElement
}

// Credentials deliberately have no generated toString().
class AsmrLoginRequest(val name: String, val password: String)
class AsmrAuthResponse(val user: AsmrCloudUser? = null, val token: String? = null)
data class AsmrCloudUser(val loggedIn: Boolean = false, val name: String? = null)
data class AsmrCloudPagination(val page: Int = 0, val currentPage: Int = 0, val pageSize: Int = 0, val totalCount: Int = 0) {
    fun validate(expectedPage: Int) {
        check((page.takeIf { it > 0 } ?: currentPage) == expectedPage && pageSize > 0 && totalCount >= 0) {
            "Invalid ASMR.one pagination"
        }
    }
}
data class AsmrPlaylistPage(val playlists: List<AsmrCloudPlaylist>? = null, val pagination: AsmrCloudPagination? = null)
data class AsmrWorkPage(val works: List<AsmrCloudWork>? = null, val pagination: AsmrCloudPagination? = null)
data class AsmrCloudPlaylist(
    val id: String = "", val name: String? = null, val user_name: String? = null,
    val privacy: Int = 0, val description: String? = null, val works_count: Int = 0,
    val mainCoverUrl: String? = null, val updated_at: String? = null, val exist: Boolean? = null
) {
    val isSystem: Boolean get() = name.orEmpty().startsWith("__SYS_PLAYLIST_")
    companion object {
        const val LIKED = "__SYS_PLAYLIST_LIKED"
        const val MARKED = "__SYS_PLAYLIST_MARKED"
    }
}
data class AsmrCloudWork(
    val id: Long = 0, val title: String? = null, val source_id: String? = null,
    val circle: Circle? = null, val vas: List<Artist>? = null, val tags: List<Tag>? = null,
    val mainCoverUrl: String? = null, val progress: String? = null,
    val userRating: JsonElement? = null, val review_text: String? = null
) {
    fun toAlbum(): Album = Album(
        title = title.orEmpty(), path = "https://asmr.one/work/$id",
        workId = source_id.orEmpty(), rjCode = source_id.orEmpty().takeIf { it.startsWith("RJ", true) }.orEmpty(),
        circle = circle?.name.orEmpty(), cv = vas.orEmpty().joinToString(" / ") { it.name },
        tags = tags.orEmpty().map { it.name }, tagDetails = tags.orEmpty().map { it.toAlbumTag() },
        coverUrl = mainCoverUrl.orEmpty(), hasAsmrOne = true,
        asmrOneWorkId = id.takeIf { it in 1..Int.MAX_VALUE }?.toInt(), collectedSourceName = "AsmrOne"
    )
}
data class AsmrPlaylistId(val id: String)
data class AsmrPlaylistWorks(val id: String, val works: List<Long>)
data class AsmrCreatePlaylist(val name: String, val privacy: Int = 0, val locale: String,
    val description: String = "", val works: List<Long> = emptyList())
data class AsmrPlaylistMetadata(val name: String, val privacy: Int, val description: String)
data class AsmrEditPlaylist(val id: String, val data: AsmrPlaylistMetadata)
data class AsmrProgressRequest(val work_id: Long, val rating: JsonElement?, val review_text: String?, val progress: String)

fun interface AsmrOneAccountApiProvider {
    suspend fun api(): AsmrOneAccountApi
}
