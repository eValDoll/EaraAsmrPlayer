package com.asmr.player

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CloudDownload
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.asmr.player.ui.library.AlbumDetailScreen
import com.asmr.player.ui.library.AlbumDetailUiState
import com.asmr.player.ui.library.AlbumDetailViewModel
import com.asmr.player.ui.library.CloudSyncSelectionDialog
import com.asmr.player.ui.library.LibraryFilterScreen
import com.asmr.player.ui.library.LibraryScreen
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.ui.library.BulkPhase
import com.asmr.player.ui.player.MiniPlayer
import com.asmr.player.ui.player.NowPlayingMotionLayout
import com.asmr.player.ui.player.NowPlayingMotionSpec
import com.asmr.player.ui.player.NowPlayingScreen
import com.asmr.player.ui.player.PlayerSharedBackdrop
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.player.rememberCoverDragPreviewState
import com.asmr.player.ui.player.rememberCoverMotionState
import com.asmr.player.ui.sidepanel.LocalRightPanelExpandedState
import com.asmr.player.ui.downloads.DownloadsScreen
import com.asmr.player.ui.downloads.DownloadsViewModel
import com.asmr.player.ui.downloads.DownloadItemState
import com.asmr.player.ui.dlsite.DlsiteLoginScreen
import com.asmr.player.ui.dlsite.DlsiteLoginViewModel
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.playlists.PlaylistDetailScreen
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.playlists.PlaylistsScreen
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.playlists.SystemPlaylistScreen
import com.asmr.player.ui.search.SearchScreen
import com.asmr.player.ui.search.SearchViewModel
import com.asmr.player.domain.model.SearchSource
import com.asmr.player.ui.settings.SettingsScreen
import com.asmr.player.ui.settings.SettingsViewModel
import com.asmr.player.ui.common.glassMenu
import com.asmr.player.ui.drawer.DrawerStatusViewModel
import com.asmr.player.ui.drawer.SiteStatus
import com.asmr.player.ui.drawer.SiteStatusType
import com.asmr.player.ui.nav.AppNavigator
import com.asmr.player.ui.nav.BottomChrome
import com.asmr.player.ui.nav.Routes
import com.asmr.player.ui.nav.bottomChromeNavItems
import com.asmr.player.ui.nav.bottomChromeOverlayHeight
import com.asmr.player.ui.nav.isPrimaryRoute
import com.asmr.player.ui.nav.resolvePrimaryPagerRoutes
import com.asmr.player.ui.nav.resolvePrimaryRoute
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.splash.EaraSplashOverlay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import androidx.compose.material.icons.automirrored.rounded.ArrowBack

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.AsmrTheme
import androidx.compose.ui.draw.blur
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.asmr.player.ui.theme.LocalThemeTransitionTrigger
import com.asmr.player.ui.theme.ThemeTransitionTriggerRequest

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import com.asmr.player.ui.player.QueueSheetContent
import com.asmr.player.ui.player.SleepTimerSheetContent
import com.asmr.player.ui.player.MiniPlayerDisplayMode

import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.util.MessageManager
import com.asmr.player.ui.common.NonTouchableAppMessageOverlay
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.common.VisibleAppMessage
import com.asmr.player.ui.theme.HuePalette
import com.asmr.player.ui.theme.PlayerTheme
import com.asmr.player.ui.theme.ThemeMode
import com.asmr.player.ui.theme.DefaultBrandPrimaryDark
import com.asmr.player.ui.theme.DefaultBrandPrimaryLight
import com.asmr.player.ui.theme.deriveHuePalette
import kotlin.math.roundToInt
import com.asmr.player.ui.theme.neutralPaletteForMode
import com.asmr.player.ui.theme.rememberDynamicHuePalette
import com.asmr.player.ui.theme.rememberDynamicHuePaletteFromVideoFrame
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.ui.common.AppVolumeHearingWarningDialog
import com.asmr.player.ui.common.AppVolumeWarningSessionState
import com.asmr.player.ui.common.rememberAppVolumeWarningSessionState
import com.asmr.player.ui.common.rememberCurrentAudioOutputRouteKind
import com.asmr.player.ui.common.rememberProtectedAppVolumeChangeState
import com.asmr.player.ui.common.AudioOutputRouteIcon
import com.asmr.player.ui.common.DismissOutsideBoundsOverlay
import com.asmr.player.service.AudioOutputRouteKind
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.media3.common.MediaItem
import androidx.lifecycle.lifecycleScope
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.playback.AppVolume
import com.asmr.player.ui.common.AppVolumeVerticalSlider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    return currentRoute?.startsWith("album_detail") == true
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

@OptIn(ExperimentalMaterial3Api::class)
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
            .fillMaxHeight()
            .padding(start = 10.dp)
            .then(clickModifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(42.dp)
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

internal fun MediaItem?.toThemeMediaSource(): ThemeMediaSource {
    val item = this ?: return ThemeMediaSource()
    val metadata = item.mediaMetadata
    val artworkUri = metadata.artworkUri?.takeUnless { uri ->
        val uriTextValue = uri.toString()
        uri.scheme.equals("android.resource", ignoreCase = true) ||
            uriTextValue.contains("ic_placeholder", ignoreCase = true)
    }
    val videoUri = item.localConfiguration?.uri
    val mimeType = item.localConfiguration?.mimeType.orEmpty()
    val uriText = videoUri?.toString().orEmpty()
    val fileExtension = uriText
        .substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('.', "")
        .lowercase()
    val isVideo = metadata.extras?.getBoolean("is_video") == true ||
        mimeType.startsWith("video/") ||
        fileExtension in setOf("mp4", "m4v", "webm", "mkv", "mov")
    return ThemeMediaSource(
        artworkUri = artworkUri,
        videoUri = videoUri,
        isVideo = isVideo
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
