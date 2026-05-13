package com.asmr.player.ui.nav

import androidx.navigation.NavHostController
import java.net.URLEncoder

object Routes {
    const val Library = "library"
    const val Search = "search"
    const val NowPlaying = "now_playing"

    const val AlbumDetailByIdPattern = "album_detail/{albumId}?rjCode={rjCode}&initialTab={initialTab}"
    const val AlbumDetailOnlineByRjPattern = "album_detail_online/{rj}"

    const val AlbumDetailByRjPattern = "album_detail_rj/{rj}?initialTab={initialTab}"
    fun albumDetailByRj(rj: String, initialTab: String? = null): String {
        val encoded = URLEncoder.encode(rj, "UTF-8")
        return buildString {
            append("album_detail_rj/")
            append(encoded)
            if (!initialTab.isNullOrBlank()) {
                append("?initialTab=")
                append(URLEncoder.encode(initialTab, "UTF-8"))
            }
        }
    }
}

class AppNavigator(
    private val navController: NavHostController
) {
    fun openAlbumDetail(albumId: Long?, rj: String?, preferDlsitePlay: Boolean = false) {
        val normalizedRj = rj?.trim().orEmpty()
        if (normalizedRj.isNotBlank()) {
            openAlbumDetailByRj(normalizedRj, preferDlsitePlay)
            return
        }
        val id = albumId ?: 0L
        if (id <= 0L) return
        val route = buildString {
            append("album_detail/")
            append(id)
            if (preferDlsitePlay) append("?initialTab=dlsitePlay")
        }
        val refreshToken = System.currentTimeMillis()
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        val popToRoute = when {
            currentRoute == Routes.Search -> Routes.Search
            currentRoute == Routes.Library -> Routes.Library
            else -> currentRoute ?: Routes.Library
        }
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = false
            popUpTo(popToRoute) { inclusive = false; saveState = true }
        }
        runCatching { navController.getBackStackEntry(route) }
            .getOrNull()
            ?.savedStateHandle
            ?.set("refreshToken", refreshToken)
            ?: navController.currentBackStackEntry?.savedStateHandle?.set("refreshToken", refreshToken)
    }

    fun openAlbumDetailByRj(rj: String, preferDlsitePlay: Boolean = false) {
        val normalized = rj.trim().uppercase()
        if (normalized.isBlank()) return
        val route = Routes.albumDetailByRj(
            normalized,
            initialTab = if (preferDlsitePlay) "dlsitePlay" else null
        )
        val refreshToken = System.currentTimeMillis()
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        val popToRoute = when {
            currentRoute == Routes.Search -> Routes.Search
            currentRoute == Routes.Library -> Routes.Library
            else -> currentRoute ?: Routes.Library
        }
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = false
            popUpTo(popToRoute) { inclusive = false; saveState = true }
        }
        runCatching { navController.getBackStackEntry(route) }
            .getOrNull()
            ?.savedStateHandle
            ?.set("refreshToken", refreshToken)
            ?: navController.currentBackStackEntry?.savedStateHandle?.set("refreshToken", refreshToken)
    }

    fun openAlbumDetailByRjStacked(rj: String) {
        val normalized = rj.trim().uppercase()
        if (normalized.isBlank()) return
        val route = Routes.albumDetailByRj(normalized)
        navController.navigate(route) {
            launchSingleTop = false
            restoreState = false
        }
    }
}
