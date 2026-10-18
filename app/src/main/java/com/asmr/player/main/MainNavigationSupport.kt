package com.asmr.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.*
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.asmr.player.ui.nav.Routes
import java.net.URLDecoder
import java.net.URLEncoder

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.asmr.player.ui.theme.AsmrTheme
import androidx.compose.animation.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.asmr.player.ui.theme.LocalThemeTransitionTrigger
import com.asmr.player.ui.theme.ThemeTransitionTriggerRequest



import androidx.media3.common.MediaItem

internal fun encodeRouteArg(value: String): String = URLEncoder.encode(value, "UTF-8")

internal fun decodeRouteArg(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }
    .getOrDefault(value)

internal fun String?.toAlbumDetailInitialTab(): Int? {
    return when (this?.trim()) {
        "local" -> 0
        "dl" -> 1
        "dlsitePlay" -> 2
        else -> null
    }
}

internal fun computePrimaryNavSelectionProgresses(
    pagerRoutes: List<String>,
    currentPage: Int,
    currentPageOffsetFraction: Float,
    fallbackRoute: String,
    lockedRoute: String? = null
): Map<String, Float> {
    if (pagerRoutes.isEmpty()) {
        return mapOf(fallbackRoute to 1f)
    }

    val resolvedLockedRoute = lockedRoute?.takeIf { it in pagerRoutes }
    if (resolvedLockedRoute != null) {
        return mapOf(resolvedLockedRoute to 1f)
    }

    val selectionProgresses = LinkedHashMap<String, Float>(pagerRoutes.size)
    pagerRoutes.forEachIndexed { page, route ->
        val pageOffset = kotlin.math.abs((currentPage - page) + currentPageOffsetFraction)
        val progress = (1f - pageOffset.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        if (progress > 0f) {
            val existing = selectionProgresses[route] ?: 0f
            selectionProgresses[route] = maxOf(existing, progress)
        }
    }

    if (selectionProgresses.isEmpty()) {
        selectionProgresses[fallbackRoute] = 1f
    }

    return selectionProgresses
}

internal fun resolvePrimaryNavVisualRoute(
    activeRoute: String,
    pendingRoute: String?,
    pagerRoutes: List<String>
): String {
    return pendingRoute?.takeIf { it in pagerRoutes } ?: activeRoute
}

internal fun resolvePrimaryPagerApproachPage(
    currentPage: Int,
    targetPage: Int
): Int? {
    if (kotlin.math.abs(targetPage - currentPage) <= 1) return null
    return targetPage + if (targetPage > currentPage) -1 else 1
}

internal fun shouldSyncPrimaryPagerToRoute(
    targetPage: Int,
    settledPage: Int
): Boolean = targetPage >= 0 && settledPage != targetPage

internal fun shouldClearPendingPrimaryNavigationRoute(
    currentRoute: String?,
    pendingRoute: String?,
    navigationInProgress: Boolean,
    pendingPage: Int,
    settledPage: Int
): Boolean {
    if (pendingRoute.isNullOrBlank()) return false
    if (currentRoute != pendingRoute || navigationInProgress) return false
    return pendingPage >= 0 && settledPage == pendingPage
}

internal fun resolvePrimaryPagerBeyondBoundsPageCount(pageCount: Int): Int {
    return (pageCount - 1).coerceIn(0, 1)
}

internal fun resolveCurrentPrimaryDestinationRoute(
    currentRoute: String?,
    playlistSystemType: String? = null
): String? {
    return when {
        currentRoute == Routes.Library -> Routes.Library
        currentRoute == Routes.Search -> Routes.Search
        currentRoute == Routes.HotListening -> Routes.HotListening
        currentRoute == "playlists" -> "playlists"
        currentRoute == "groups" -> "groups"
        currentRoute == "settings" -> "settings"
        currentRoute == "listening_calendar" -> "listening_calendar"
        currentRoute == "playlist_system/{type}" && playlistSystemType == "favorites" -> "playlist_system/favorites"
        else -> null
    }
}

internal fun shouldHideStatusBarForImmersivePage(
    currentRoute: String?,
    nowPlayingVisible: Boolean
): Boolean {
    if (nowPlayingVisible) return true
    return isAlbumDetailRoute(currentRoute)
}

internal fun isAlbumDetailRoute(route: String?): Boolean {
    return route?.startsWith("album_detail") == true
}

internal fun isAlbumDetailStackTransition(
    initialRoute: String?,
    targetRoute: String?
): Boolean {
    return isAlbumDetailRoute(initialRoute) && isAlbumDetailRoute(targetRoute)
}

internal fun NavHostController.navigateSingleTop(route: String, popUpToRoute: String? = null) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        if (!popUpToRoute.isNullOrBlank()) {
            popUpTo(popUpToRoute) {
                saveState = true
            }
        }
    }
}

internal fun NavHostController.navigatePrimaryRoute(
    route: String,
    popUpToRoute: String = Routes.Library
) {
    navigate(route) {
        launchSingleTop = true
        restoreState = false
        popUpTo(popUpToRoute) {
            inclusive = false
            saveState = false
        }
    }
}

internal fun shouldScrollPrimaryRouteToTop(
    requestedRoute: String,
    activePrimaryRoute: String,
    currentPrimaryRoute: String?
): Boolean = requestedRoute == activePrimaryRoute &&
    (currentPrimaryRoute == requestedRoute || currentPrimaryRoute == null)

internal fun shouldTriggerPrimaryRouteScrollToTop(
    requestedRoute: String,
    visualPrimaryRoute: String,
    activePrimaryRoute: String,
    currentPrimaryRoute: String?
): Boolean {
    if (requestedRoute == visualPrimaryRoute) return true
    return shouldScrollPrimaryRouteToTop(
        requestedRoute = requestedRoute,
        activePrimaryRoute = activePrimaryRoute,
        currentPrimaryRoute = currentPrimaryRoute
    )
}

@Composable
internal fun PrimaryTopBarBrand(
    appName: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val trigger = LocalThemeTransitionTrigger.current
    val isDark = AsmrTheme.colorScheme.isDark
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }

    val clickModifier = if (trigger != null) {
        Modifier
            .onGloballyPositioned { coordinates ->
                positionInRoot = coordinates.positionInWindow()
            }
            .pointerInput(isDark) {
                detectTapGestures {
                    val origin = Offset(
                        positionInRoot.x + it.x,
                        positionInRoot.y + it.y
                    )
                    val targetPref = if (isDark) "light" else "dark"
                    trigger(
                        ThemeTransitionTriggerRequest(
                            origin = origin,
                            targetPref = targetPref
                        )
                    )
                }
            }
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .padding(start = 6.dp)
            .height(44.dp)
            .then(clickModifier)
            .padding(start = 2.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(38.dp)
        )
        Text(
            text = appName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal data class DefaultSystemUiState(
    val statusBarColor: Int,
    val navigationBarColor: Int,
    val lightStatusBars: Boolean,
    val lightNavigationBars: Boolean,
    val statusBarContrastEnforced: Boolean? = null,
    val navigationBarContrastEnforced: Boolean? = null,
    val layoutInDisplayCutoutMode: Int? = null
)

internal data class ThemeMediaSource(
    val artworkUri: Uri? = null,
    val videoUri: Uri? = null,
    val isVideo: Boolean = false
)

private val VideoPlaybackExtensions = setOf("mp4", "m4v", "webm", "mkv", "mov")

private fun isVideoPlaybackSource(
    uriText: String,
    mimeType: String,
    metadataFlag: Boolean
): Boolean {
    val fileExtension = uriText
        .substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('.', "")
        .lowercase()
    return metadataFlag || mimeType.startsWith("video/") || fileExtension in VideoPlaybackExtensions
}

internal fun MediaItem?.isVideoPlaybackItem(): Boolean {
    val item = this ?: return false
    return isVideoPlaybackSource(
        uriText = item.localConfiguration?.uri?.toString().orEmpty(),
        mimeType = item.localConfiguration?.mimeType.orEmpty(),
        metadataFlag = item.mediaMetadata.extras?.getBoolean("is_video") == true
    )
}

internal fun PlaylistItemEntity?.isVideoPlaybackItem(): Boolean {
    val item = this ?: return false
    return isVideoPlaybackSource(
        uriText = item.uri,
        mimeType = item.mimeType,
        metadataFlag = item.isVideo
    )
}

internal fun resolveMainRequestedOrientation(
    isPhone: Boolean,
    nowPlayingVisible: Boolean,
    videoFullscreen: Boolean,
    portraitExitPending: Boolean
): Int = when {
    isPhone && portraitExitPending -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    nowPlayingVisible && videoFullscreen -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    isPhone && nowPlayingVisible -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    isPhone -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    // 平板跟随系统旋转偏好；SENSOR 会绕过用户的旋转锁定。
    else -> ActivityInfo.SCREEN_ORIENTATION_USER
}

internal fun shouldKeepVideoOutputEnabled(
    currentItemIsVideo: Boolean,
    miniPlayerEnabled: Boolean,
    nowPlayingVisible: Boolean
): Boolean {
    return currentItemIsVideo &&
        (miniPlayerEnabled || nowPlayingVisible)
}

internal fun MediaItem?.toThemeMediaSource(): ThemeMediaSource {
    val item = this ?: return ThemeMediaSource()
    val metadata = item.mediaMetadata
    val artworkUri = metadata.artworkUri?.takeUnless { uri ->
        val uriTextValue = uri.toString()
        uri.scheme.equals("android.resource", ignoreCase = true) ||
            uriTextValue.contains("ic_placeholder", ignoreCase = true)
    }
    val videoUri = item.localConfiguration?.uri
    return ThemeMediaSource(
        artworkUri = artworkUri,
        videoUri = videoUri,
        isVideo = item.isVideoPlaybackItem()
    )
}

internal fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
