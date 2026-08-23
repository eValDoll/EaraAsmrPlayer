package com.asmr.player.ui.library

import android.content.Intent
import android.graphics.PathMeasure as AndroidPathMeasure
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy as LayerCompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.scraper.DLSITE_DOMAIN
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
import com.asmr.player.data.remote.scraper.storeSegment
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.cache.CacheImageModel
import com.asmr.player.data.remote.dlsite.DlsiteLanguageEdition
import com.asmr.player.ui.dlsite.DlsitePlayViewModel
import com.asmr.player.util.DlsiteAntiHotlink
import com.asmr.player.util.SmartSortKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import com.asmr.player.data.lyrics.deriveLyricsRelativePathNoExt
import com.asmr.player.ui.common.SubtitleStamp
import com.asmr.player.ui.common.DiscPlaceholder
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.AsmrImageLoadingPlaceholder
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.ImagePreviewDialog
import com.asmr.player.ui.common.ImagePreviewRequest
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.consumeTapThrough
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.groups.AlbumGroupPickerScreen
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.settings.SettingsViewModel
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.util.Formatting
import com.asmr.player.util.MessageManager
import com.asmr.player.util.RemoteSubtitleSource
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class AlbumHeaderButtonGroupState {
    DownloadOnly,
    Save,
    Lossless
}

private enum class OnlineDownloadSource {
    AsmrOne,
    DlsitePlay,
    DlsiteTrial
}

private enum class IncrementalAlbumAction {
    Download,
    Save
}

private data class PendingOnlineSaveSelection(
    val paths: Set<String>,
    val useDlsitePlayTree: Boolean
)

private data class DlsitePlayAuthSnapshot(
    val canAuthenticate: Boolean,
    val fingerprint: Int
)

private fun readDlsitePlayAuthSnapshot(authStore: DlsiteAuthStore): DlsitePlayAuthSnapshot {
    val cookie = authStore.getPlayCookie().trim()
    val expiresAt = authStore.getPlayCookieExpiresAtMs()
    return DlsitePlayAuthSnapshot(
        canAuthenticate = cookie.isNotBlank() && (expiresAt == null || expiresAt > System.currentTimeMillis()),
        fingerprint = 31 * cookie.hashCode() + (expiresAt?.hashCode() ?: 0)
    )
}

internal data class PreparedTrackPlayback(
    val tracks: List<Track>,
    val startTrack: Track,
    val onlineLyrics: Map<String, List<RemoteSubtitleSource>> = emptyMap()
)

internal data class PreparedMediaPlayback(
    val items: List<MediaItem>,
    val startIndex: Int
)

private val AlbumDetailHeroContentGap = 8.dp
private val AlbumDetailHeroTransitionHeight = 96.dp
private val AlbumDetailHeroBlurRampHeight = 188.dp
private val AlbumDetailHeroBlurRadius = 32.dp
private val AlbumDetailScrolledContentFadeSpan = 10.dp
private const val AlbumDetailInitialIntroDurationMs = 1200L
private const val AlbumDetailHeroIntroDurationMs = 520
private const val AlbumDetailHeaderEnterDurationMs = 320
private const val AlbumDetailHeroIntroStartScale = 1.35f
private const val AlbumDetailHeroBlurRadiusMaxPx = 96f
private const val AlbumDetailHeroBlurSampleMarginMultiplier = 3f
private const val AlbumDetailHeroOvershootResistance = 0.30f
private const val AlbumDetailHeroOvershootReleaseMultiplier = 0.72f
private const val AlbumDetailHeroExpandOvershootScale = 0.16f
private const val AlbumDetailHeroFlingVelocityMin = 2400f
private const val AlbumDetailHeroFlingVelocityMax = 12_000f
private const val AlbumDetailHeroFlingOvershootPortion = 0.24f
private const val AlbumDetailHeroFlingOvershootMaxPortion = 0.14f
private const val AlbumDetailHeroFlingApproachMillis = 560
private const val AlbumDetailHeroFlingSettleMillis = 980
private const val AlbumDetailCvRevealDelayMs = 0
private const val AlbumDetailTagsRevealDelayMs = 90
private const val AlbumHeaderActionStateTransitionMillis = 800
internal val AlbumDetailHorizontalPadding = 8.dp
private val AlbumLandscapeArtworkStartPadding = 68.dp
private val AlbumLandscapeArtworkTopPadding = 36.dp
private val AlbumLandscapeSpectrumTopPadding = 12.dp
private val AlbumLandscapeHeaderEndPadding = 32.dp
private val AlbumLandscapeHeaderLift = 52.dp
private const val AlbumLandscapeCoverShadowStartAlpha = 0.16f
private val AlbumLandscapeArtworkContentGap = 12.dp
private val AlbumLandscapeSurfaceBorderWidth = 0.5.dp
private val AlbumLandscapeCollapsedArtworkShiftX = 24.dp
private val AlbumLandscapeCollapsedArtworkShiftY = 18.dp
private const val AlbumLandscapeSpectrumArtworkOffsetFraction = 0.15f
private const val AlbumLandscapeSpectrumCollapseFollowFraction = 0.82f

internal fun shouldUseAlbumDetailLandscapeLayout(
    compactWidth: Boolean,
    screenWidthDp: Int,
    screenHeightDp: Int
): Boolean {
    return !compactWidth && screenWidthDp > screenHeightDp
}

internal fun albumLandscapeHeaderStart(artworkSize: Dp): Dp {
    return AlbumLandscapeArtworkStartPadding + artworkSize + AlbumLandscapeArtworkContentGap
}

internal fun albumLandscapeArtworkRight(artworkSize: Dp): Dp {
    return AlbumLandscapeArtworkStartPadding + artworkSize
}

internal fun albumLandscapeCollapseDistance(artworkSize: Dp): Dp {
    return (artworkSize * 0.28f).coerceAtMost(128.dp)
}

internal fun albumLandscapeCoverScale(collapsePx: Float, collapseMaxPx: Float): Float {
    val progress = albumLandscapeCollapseProgress(collapsePx, collapseMaxPx)
    return 1f - progress * 0.30f
}

internal fun albumLandscapeCollapseProgress(collapsePx: Float, collapseMaxPx: Float): Float {
    if (collapseMaxPx <= 0f) return 0f
    return (collapsePx / collapseMaxPx).coerceIn(0f, 1f)
}

internal fun albumLandscapePlaybackProgress(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toDouble() / durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
}

internal fun albumLandscapePulseSweepFraction(phase: Float): Float {
    val clamped = phase.coerceIn(0f, 1f)
    val remaining = 1f - clamped
    return 1f - remaining * remaining
}

internal fun albumLandscapeCoverShadowAlpha(imageAlpha: Float): Float {
    val normalizedImageAlpha = imageAlpha.coerceIn(0f, 1f)
    return (
        (normalizedImageAlpha - AlbumLandscapeCoverShadowStartAlpha) /
            (1f - AlbumLandscapeCoverShadowStartAlpha)
        ).coerceIn(0f, normalizedImageAlpha)
}

internal fun albumLandscapeDirectoryTop(
    headerHeight: Dp,
    headerLift: Dp
): Dp {
    return (headerHeight - headerLift + 4.dp).coerceAtLeast(0.dp)
}

internal fun albumLandscapePulseEnabled(
    isPlaying: Boolean,
    progress: Float
): Boolean {
    return isPlaying && progress > 0f
}

internal fun albumLandscapeSurfaceHeight(contentViewportHeight: Dp, artworkSize: Dp): Dp {
    return contentViewportHeight + albumLandscapeCollapseDistance(artworkSize)
}

internal fun albumLandscapePaneViewportHeightPx(
    surfaceHeightPx: Int,
    collapsePx: Float,
    collapseMaxPx: Float
): Int {
    if (surfaceHeightPx <= 0) return 0
    val safeCollapseMaxPx = collapseMaxPx.coerceAtLeast(0f)
    val hiddenBottomPx = (
        safeCollapseMaxPx - collapsePx.coerceIn(0f, safeCollapseMaxPx)
        ).roundToInt()
    return (surfaceHeightPx - hiddenBottomPx).coerceIn(0, surfaceHeightPx)
}

internal fun albumLandscapeSpectrumOffsetY(artworkSize: Dp): Dp {
    return AlbumLandscapeSpectrumTopPadding +
        artworkSize * AlbumLandscapeSpectrumArtworkOffsetFraction
}

internal fun albumLandscapeSpectrumTranslationY(collapsePx: Float): Float {
    return -collapsePx.coerceAtLeast(0f) * AlbumLandscapeSpectrumCollapseFollowFraction
}

private fun Modifier.albumLandscapePaneViewportHeight(
    collapsePx: () -> Float,
    collapseMaxPx: Float
): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    } else {
        val viewportHeight = albumLandscapePaneViewportHeightPx(
            surfaceHeightPx = constraints.maxHeight,
            collapsePx = collapsePx(),
            collapseMaxPx = collapseMaxPx
        ).coerceAtLeast(constraints.minHeight)
        val placeable = measurable.measure(
            constraints.copy(
                minHeight = viewportHeight,
                maxHeight = viewportHeight
            )
        )
        layout(placeable.width, viewportHeight) {
            placeable.placeRelative(0, 0)
        }
    }
}

private val AlbumDetailHeroBounceBackSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow
)

private val AlbumHeaderEnterTweenSpec = tween<Float>(
    durationMillis = AlbumDetailHeaderEnterDurationMs,
    easing = FastOutLinearInEasing
)

private val AlbumHeaderExpandTweenSpec = tween<IntSize>(
    durationMillis = 320,
    easing = FastOutLinearInEasing
)

private val DlsiteSectionResizeTweenSpec = tween<IntSize>(
    durationMillis = 280,
    easing = FastOutSlowInEasing
)

private class AlbumDetailHeroMotionState {
    var collapsePx by mutableFloatStateOf(0f)
    var visualOvershootPx by mutableFloatStateOf(0f)
    var visualOvershootJob: Job? = null

    fun cancelVisualOvershootAnimation() {
        visualOvershootJob?.cancel()
        visualOvershootJob = null
    }
}

internal fun dlsiteSectionRevealModifier(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
): Modifier {
    return if (enabled) {
        modifier.animateContentSize(animationSpec = DlsiteSectionResizeTweenSpec)
    } else {
        modifier
    }
}

internal fun shouldAnimateAlbumHeaderMetaReveal(
    presentInitially: Boolean,
    hasContent: Boolean,
    animationsEnabled: Boolean
): Boolean {
    return animationsEnabled && !presentInitially && hasContent
}

internal data class AlbumDetailOnlineLoadPlan(
    val loadDlsite: Boolean = false,
    val loadAsmrOne: Boolean = false,
    val loadDlsitePlay: Boolean = false
)

internal fun albumDetailOnlineLoadPlan(
    selectedTab: Int,
    hasResolvedInitialDlsiteTarget: Boolean,
    isInitialRouteReady: Boolean,
    hasValidLocalRj: Boolean = false,
    hasResolvedAsmrOneContent: Boolean = false,
    hasAsmrOneTree: Boolean = false,
    hasDlsitePlayCredentials: Boolean = false
): AlbumDetailOnlineLoadPlan {
    if (!isInitialRouteReady) return AlbumDetailOnlineLoadPlan()
    return when (selectedTab) {
        0 -> AlbumDetailOnlineLoadPlan(
            loadAsmrOne = hasValidLocalRj,
            loadDlsitePlay = hasValidLocalRj &&
                hasResolvedAsmrOneContent &&
                !hasAsmrOneTree &&
                hasDlsitePlayCredentials
        )
        1 -> AlbumDetailOnlineLoadPlan(
            loadDlsite = true,
            loadAsmrOne = hasResolvedInitialDlsiteTarget
        )
        2 -> AlbumDetailOnlineLoadPlan(
            loadDlsite = true,
            loadDlsitePlay = hasResolvedInitialDlsiteTarget
        )
        else -> AlbumDetailOnlineLoadPlan()
    }
}

internal fun canUseAsmrOneOnlineTreeActions(
    selectedTab: Int,
    hasAsmrOneTree: Boolean
): Boolean {
    return selectedTab == 1 && hasAsmrOneTree
}

internal fun asmrOneDirectoryTreeStateKey(
    currentRj: String,
    baseRj: String
): String {
    val targetRj = currentRj.trim().uppercase()
        .ifBlank { baseRj.trim().uppercase() }
    return "tree:asmrOne:$targetRj"
}

internal fun albumHeaderDownloadEnabled(
    selectedTab: Int,
    hasAsmrOneTree: Boolean,
    hasDlsitePlayTree: Boolean,
    hasResolvedInitialDlsiteTarget: Boolean,
    hasValidLocalRj: Boolean = false,
    hasDlsitePlayCredentials: Boolean = true
): Boolean {
    return when (selectedTab) {
        0 -> hasValidLocalRj && (hasAsmrOneTree || (hasDlsitePlayCredentials && hasDlsitePlayTree))
        1 -> canUseAsmrOneOnlineTreeActions(selectedTab, hasAsmrOneTree)
        2 -> hasResolvedInitialDlsiteTarget && hasDlsitePlayTree
        else -> false
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    windowSizeClass: WindowSizeClass,
    albumId: Long? = null,
    rjCode: String? = null,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit = { _, _ -> },
    onAddToQueue: (Album, Track) -> Boolean = { _, _ -> false },
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit = {},
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit = {},
    onOpenPlaylistPicker: (MediaItem) -> Unit = {},
    onOpenDlsiteLogin: () -> Unit = {},
    onOpenAlbumByRj: (String, DlsiteRecommendedWork?) -> Unit = { _, _ -> },
    onSearchKeyword: (String) -> Unit = {},
    initialTab: Int? = null,
    playlistsViewModel: PlaylistsViewModel = hiltViewModel(),
    albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    heroBlurLayerCache: AlbumHeroBlurLayerCache,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cloudSyncSelectionDialogState by viewModel.cloudSyncSelectionDialogState.collectAsStateWithLifecycle()
    val colorScheme = AsmrTheme.colorScheme
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val authStore = remember(context) { DlsiteAuthStore(context) }
    var dlsitePlayAuthSnapshot by remember(authStore) {
        mutableStateOf(readDlsitePlayAuthSnapshot(authStore))
    }
    val hasDlsitePlayCredentials = dlsitePlayAuthSnapshot.canAuthenticate
    val actionScope = rememberCoroutineScope()
    val screenKey = remember(albumId, rjCode) {
        val idPart = albumId?.takeIf { it > 0 }?.toString().orEmpty()
        val rjPart = rjCode?.trim().orEmpty().uppercase()
        if (rjPart.isNotBlank()) "album:$rjPart" else "albumId:$idPart"
    }
    val introSessionKey = remember(screenKey) { "intro:${UUID.randomUUID()}" }
    // 入口决定固定展示的二级页面：本地库->本地，在线/搜索->DL，preferDlsitePlay->DL Play。
    // 不再提供页内 tab 切换与左右滑动。
    val selectedTab = remember(albumId, initialTab) {
        initialTab?.coerceIn(0, 2) ?: if (albumId != null && albumId > 0) 0 else 1
    }
    var isInitialRouteReady by remember(screenKey) {
        mutableStateOf(viewModel.hasCachedAlbum(albumId, rjCode))
    }
    var showAsmrDownloadDialog by remember { mutableStateOf(false) }
    var showOnlineSaveDialog by remember { mutableStateOf(false) }
    var pendingOnlineSaveSelection by remember { mutableStateOf<PendingOnlineSaveSelection?>(null) }
    var batchPlaylistItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var groupPickerAlbumId by remember { mutableStateOf<Long?>(null) }
    var downloadSource by remember { mutableStateOf(OnlineDownloadSource.AsmrOne) }
    var onlineSaveSource by remember { mutableStateOf(OnlineDownloadSource.AsmrOne) }
    var downloadDisabledPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var saveDisabledPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var incrementalPreparationJob by remember { mutableStateOf<Job?>(null) }
    var metaActionKeyword by rememberSaveable { mutableStateOf<String?>(null) }

    fun openMetaActions(value: String) {
        val keyword = value.trim()
        if (keyword.isNotBlank()) metaActionKeyword = keyword
    }

    DisposableEffect(lifecycleOwner, authStore, selectedTab, viewModel) {
        fun refreshAuthSnapshot() {
            val updated = readDlsitePlayAuthSnapshot(authStore)
            actionScope.launch {
                val didAuthStateChange = updated != dlsitePlayAuthSnapshot
                dlsitePlayAuthSnapshot = updated
                if (didAuthStateChange && selectedTab == 0) {
                    incrementalPreparationJob?.cancel()
                    showAsmrDownloadDialog = false
                    showOnlineSaveDialog = false
                    viewModel.invalidateDlsitePlayAccess()
                }
            }
        }
        val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshAuthSnapshot()
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAuthSnapshot()
            }
        }
        authStore.registerListener(preferenceListener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            authStore.unregisterListener(preferenceListener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.setListenTogetherRjSummaryPollingEnabled(true)
    }
    LaunchedEffect(albumId, rjCode) {
        val hasCachedAlbum = viewModel.hasCachedAlbum(albumId, rjCode)
        isInitialRouteReady = hasCachedAlbum
        if (!hasCachedAlbum) withFrameNanos { }
        viewModel.loadAlbumAndAwait(albumId, rjCode, force = false)
        isInitialRouteReady = true
    }
    DisposableEffect(screenKey, viewModel) {
        onDispose {
            viewModel.setListenTogetherRjSummaryPollingEnabled(false)
            viewModel.cancelActiveLoads()
            incrementalPreparationJob?.cancel()
        }
    }
    LaunchedEffect(pendingOnlineSaveSelection) {
        val selected = pendingOnlineSaveSelection ?: return@LaunchedEffect
        pendingOnlineSaveSelection = null
        viewModel.saveOnlineSelectedToLibrary(
            selectedLeafPaths = selected.paths,
            useDlsitePlayTree = selected.useDlsitePlayTree
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AsmrTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
        val configuration = LocalConfiguration.current
        val useLandscapeArtworkTide = shouldUseAlbumDetailLandscapeLayout(
            compactWidth = isCompact,
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp
        )

        Column(
            modifier = when {
                isCompact || useLandscapeArtworkTide -> Modifier.fillMaxSize()
                else -> Modifier
                    .fillMaxHeight()
                    .widthIn(max = 800.dp)
                    .fillMaxWidth()
            }
        ) {
            when (val state = uiState) {
                is AlbumDetailUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    }
                }
                is AlbumDetailUiState.Success -> {
                    LaunchedEffect(screenKey) {
                        if (viewModel.isInitialIntroSettled()) return@LaunchedEffect
                        delay(AlbumDetailInitialIntroDurationMs)
                        // 这只是供之后新到数据判断是否还需入场动画的生命周期标记。
                        // 已经在树上的动画会自行完整收尾，计时结束时无需强制整页重组。
                        viewModel.markInitialIntroSettled()
                    }
                    val model = state.model
                    val album = model.displayAlbum
                    val asmrOneTree = model.asmrOneTree
                    val localActionRj = remember(
                        model.baseRjCode,
                        model.localAlbum?.rjCode,
                        model.localAlbum?.workId,
                        model.localAlbum?.title,
                        model.localAlbum?.path
                    ) {
                        resolveAlbumDetailRj(model.baseRjCode, model.localAlbum)
                    }
                    val hasValidLocalRj = localActionRj.isNotBlank()
                    val localOnlineSource = when {
                        asmrOneTree.isNotEmpty() -> OnlineDownloadSource.AsmrOne
                        hasDlsitePlayCredentials && model.dlsitePlayTree.isNotEmpty() -> OnlineDownloadSource.DlsitePlay
                        else -> null
                    }
                    fun prepareIncrementalAlbumAction(
                        action: IncrementalAlbumAction,
                        source: OnlineDownloadSource,
                        tree: List<AsmrOneTrackNodeResponse>
                    ) {
                        if (tree.isEmpty()) return

                        incrementalPreparationJob?.cancel()
                        incrementalPreparationJob = actionScope.launch {
                            val existing = try {
                                viewModel.resolveLocalIncrementalSelectionPaths(tree)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                viewModel.messageManager.showError("读取本地文件状态失败，请稍后重试")
                                return@launch
                            }
                            when (action) {
                                IncrementalAlbumAction.Download -> {
                                    downloadSource = source
                                    downloadDisabledPaths = existing.downloadedPaths
                                    showAsmrDownloadDialog = true
                                }
                                IncrementalAlbumAction.Save -> {
                                    onlineSaveSource = source
                                    saveDisabledPaths = existing.savedPaths
                                    showOnlineSaveDialog = true
                                }
                            }
                        }
                    }
                    val trialDownloadTree = remember(model.dlsiteTrialTracks) {
                        buildDlsiteTrialDownloadTree(model.dlsiteTrialTracks)
                    }
                    val shouldPlayInitialAnimations = !viewModel.isInitialIntroSettled()
                    val shouldAnimateHeaderIntro = true
                    var showTagManager by remember { mutableStateOf(false) }
                    var tagManageTrack by remember { mutableStateOf<Track?>(null) }
                    var localPreviewFile by remember { mutableStateOf<LocalTreeUiEntry.File?>(null) }
                    var onlinePreviewFile by remember { mutableStateOf<AsmrTreeUiEntry.File?>(null) }
                    var imagePreviewRequest by remember { mutableStateOf<ImagePreviewRequest?>(null) }
                    var landscapeActiveListState by remember(screenKey, useLandscapeArtworkTide) {
                        mutableStateOf<LazyListState?>(null)
                    }
                    var landscapeFixedHeaderHeightPx by remember(screenKey, useLandscapeArtworkTide) {
                        mutableIntStateOf(0)
                    }
                    // 横竖屏使用的详情组合树差异很大。按布局模式隔离整个子树，旋转时先
                    // 完整移除旧树再插入新树，避免复用旧 SlotTable 位置造成结构错位。
                    key(useLandscapeArtworkTide) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val heroDensity = LocalDensity.current
                        val landscapePageWidth = maxWidth
                        val pageContainerColor = dynamicPageContainerColor(colorScheme)
                        val landscapeArtworkSize = minOf(
                            maxWidth * 0.30f,
                            maxHeight * 0.56f
                        ).coerceIn(300.dp, 460.dp)
                        val landscapeContentTop = AlbumLandscapeArtworkTopPadding +
                            landscapeArtworkSize * 0.50f
                        val landscapeContentWaveDepth = (landscapeArtworkSize * 0.50f)
                            .coerceIn(166.dp, 220.dp)
                        val landscapeHeaderFallbackHeight = (landscapeContentWaveDepth + 36.dp)
                            .coerceIn(202.dp, 256.dp)
                        val landscapeHeaderMeasuredHeight = with(heroDensity) {
                            landscapeFixedHeaderHeightPx.toDp()
                        }
                        val landscapeDirectoryTop = if (landscapeFixedHeaderHeightPx > 0) {
                            albumLandscapeDirectoryTop(
                                headerHeight = landscapeHeaderMeasuredHeight,
                                headerLift = AlbumLandscapeHeaderLift
                            )
                        } else {
                            albumLandscapeDirectoryTop(
                                headerHeight = landscapeHeaderFallbackHeight,
                                headerLift = AlbumLandscapeHeaderLift
                            )
                        }
                        val landscapeHeaderStart = albumLandscapeHeaderStart(landscapeArtworkSize)
                        val landscapeHeaderAvailableWidth = (
                            maxWidth - landscapeHeaderStart - AlbumLandscapeHeaderEndPadding
                            ).coerceAtLeast(320.dp)
                        val heroHeightLimit = if (isCompact) {
                            maxHeight * 0.62f
                        } else {
                            maxHeight * 0.64f
                        }
                        val heroMinHeight = if (isCompact) 280.dp else 360.dp
                        val heroPreferredHeight = if (isCompact) {
                            maxWidth * 0.88f
                        } else {
                            maxWidth * 0.78f
                        }
                        val heroHeight = if (useLandscapeArtworkTide) {
                            maxHeight
                        } else {
                            heroPreferredHeight
                                .coerceAtLeast(heroMinHeight)
                                .coerceAtMost(heroHeightLimit.coerceAtLeast(heroMinHeight))
                        }
                        val contentViewportTop = if (useLandscapeArtworkTide) {
                            landscapeContentTop
                        } else {
                            heroHeight + AlbumDetailHeroContentGap
                        }
                        val contentViewportHeight = (maxHeight - contentViewportTop).coerceAtLeast(0.dp)
                        val contentFadeStartY = 0.dp
                        val contentFadeEndY = AlbumDetailScrolledContentFadeSpan

                        // 随滑动自适应缩放 hero：布局边界仍是 0%~50% 折叠。
                        // 只有展开端允许封面图继续放大，松手后缓慢回落；折叠端到 50% 后直接交给列表滚动。
                        val heroCollapseMaxPx = with(heroDensity) {
                            if (useLandscapeArtworkTide) {
                                albumLandscapeCollapseDistance(landscapeArtworkSize).toPx()
                            } else {
                                (heroHeight * 0.5f).toPx()
                            }
                        }
                        val heroVisualOvershootMaxPx = with(heroDensity) {
                            if (useLandscapeArtworkTide) 0f else (heroHeight * 0.10f).toPx()
                        }
                        val contentViewportTopPx = with(heroDensity) { contentViewportTop.toPx() }
                        val heroMotion = remember(screenKey) { AlbumDetailHeroMotionState() }
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(heroCollapseMaxPx, heroVisualOvershootMaxPx) {
                            heroMotion.collapsePx = heroMotion.collapsePx.coerceIn(0f, heroCollapseMaxPx)
                            heroMotion.visualOvershootPx = heroMotion.visualOvershootPx.coerceIn(
                                -heroVisualOvershootMaxPx,
                                0f
                            )
                        }
                        DisposableEffect(heroMotion) {
                            onDispose { heroMotion.cancelVisualOvershootAnimation() }
                        }
                        val heroNestedScroll = remember(
                            heroCollapseMaxPx,
                            heroVisualOvershootMaxPx,
                            heroMotion,
                            scope
                        ) {
                            object : NestedScrollConnection {
                                private fun settleVisualOvershoot(initialVelocity: Float = 0f): Boolean {
                                    val start = heroMotion.visualOvershootPx
                                    if (abs(start) < 0.5f) return false
                                    heroMotion.cancelVisualOvershootAnimation()
                                    heroMotion.visualOvershootJob = scope.launch {
                                        animate(
                                            initialValue = start,
                                            targetValue = 0f,
                                            initialVelocity = initialVelocity,
                                            animationSpec = AlbumDetailHeroBounceBackSpec
                                        ) { value, _ ->
                                            heroMotion.visualOvershootPx = value
                                        }
                                    }
                                    return true
                                }

                                private fun dragOvershootDelta(delta: Float): Float {
                                    val progress = (-heroMotion.visualOvershootPx / heroVisualOvershootMaxPx)
                                        .coerceIn(0f, 1f)
                                    val resistance = AlbumDetailHeroOvershootResistance * (1f - progress * progress * 0.62f)
                                    return delta * resistance
                                }

                                private fun applyCollapseDelta(delta: Float): Float {
                                    if (delta == 0f) return 0f
                                    heroMotion.cancelVisualOvershootAnimation()
                                    val current = heroMotion.collapsePx.coerceIn(0f, heroCollapseMaxPx)
                                    var remaining = delta
                                    var consumed = 0f

                                    if (remaining > 0f && heroMotion.visualOvershootPx < 0f) {
                                        val visualRelease = (remaining * AlbumDetailHeroOvershootReleaseMultiplier)
                                            .coerceAtMost(-heroMotion.visualOvershootPx)
                                        if (visualRelease > 0f) {
                                            heroMotion.visualOvershootPx += visualRelease
                                            remaining -= visualRelease / AlbumDetailHeroOvershootReleaseMultiplier
                                            consumed += visualRelease / AlbumDetailHeroOvershootReleaseMultiplier
                                        }
                                    }

                                    if (remaining != 0f) {
                                        val collapseTarget = (current + remaining).coerceIn(0f, heroCollapseMaxPx)
                                        val collapseApplied = collapseTarget - current
                                        if (collapseApplied != 0f) {
                                            heroMotion.collapsePx = collapseTarget
                                            remaining -= collapseApplied
                                            consumed += collapseApplied
                                        }
                                    }

                                    if (remaining < 0f && heroVisualOvershootMaxPx > 0f) {
                                        val visualDelta = dragOvershootDelta(remaining)
                                        val visualTarget = (heroMotion.visualOvershootPx + visualDelta)
                                            .coerceIn(-heroVisualOvershootMaxPx, 0f)
                                        heroMotion.visualOvershootPx = visualTarget
                                        consumed += remaining
                                    }

                                    return consumed
                                }

                                private fun flingOvershootTarget(velocityY: Float): Float {
                                    if (velocityY <= AlbumDetailHeroFlingVelocityMin) return 0f
                                    val velocityProgress = ((velocityY - AlbumDetailHeroFlingVelocityMin) /
                                        (AlbumDetailHeroFlingVelocityMax - AlbumDetailHeroFlingVelocityMin))
                                        .coerceIn(0f, 1f)
                                    val eased = velocityProgress * velocityProgress
                                    val target = heroVisualOvershootMaxPx * AlbumDetailHeroFlingOvershootPortion * eased
                                    val cappedTarget = target.coerceAtMost(
                                        heroVisualOvershootMaxPx * AlbumDetailHeroFlingOvershootMaxPortion
                                    )
                                    return -cappedTarget
                                }

                                private fun absorbFlingOvershoot(velocityY: Float): Boolean {
                                    val target = flingOvershootTarget(velocityY)
                                    if (target >= -0.5f) return settleVisualOvershoot()
                                    heroMotion.cancelVisualOvershootAnimation()
                                    heroMotion.visualOvershootJob = scope.launch {
                                        if (target < heroMotion.visualOvershootPx) {
                                            animate(
                                                initialValue = heroMotion.visualOvershootPx,
                                                targetValue = target,
                                                animationSpec = tween(
                                                    durationMillis = AlbumDetailHeroFlingApproachMillis,
                                                    easing = FastOutSlowInEasing
                                                )
                                            ) { value, _ -> heroMotion.visualOvershootPx = value }
                                        }
                                        animate(
                                            initialValue = heroMotion.visualOvershootPx,
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                durationMillis = AlbumDetailHeroFlingSettleMillis,
                                                easing = FastOutSlowInEasing
                                            )
                                        ) { value, _ -> heroMotion.visualOvershootPx = value }
                                    }
                                    return true
                                }

                                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                    val dy = available.y
                                    // 向上浏览（手指上滑，dy<0）：先把滚动用于折叠 hero，再交给列表。
                                    if (dy < 0f && (
                                            heroMotion.collapsePx < heroCollapseMaxPx ||
                                                heroMotion.visualOvershootPx < 0f
                                            )
                                    ) {
                                        val applied = applyCollapseDelta(-dy)
                                        val consumed = if (applied != 0f) -applied else dy
                                        return Offset(0f, consumed)
                                    }
                                    return Offset.Zero
                                }

                                override fun onPostScroll(
                                    consumed: Offset,
                                    available: Offset,
                                    source: NestedScrollSource
                                ): Offset {
                                    val dy = available.y
                                    // 列表已到顶仍有下滑剩余（dy>0）：把剩余滚动用于展开 hero。
                                    if (dy > 0f && (
                                            heroMotion.collapsePx > 0f ||
                                                heroMotion.visualOvershootPx > -heroVisualOvershootMaxPx
                                            )
                                    ) {
                                        val applied = applyCollapseDelta(-dy)
                                        val released = if (applied != 0f) -applied else dy
                                        return Offset(0f, released)
                                    }
                                    return Offset.Zero
                                }

                                override suspend fun onPreFling(available: Velocity): Velocity {
                                    settleVisualOvershoot()
                                    return Velocity.Zero
                                }

                                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                                    if (available.y > 0f && heroMotion.collapsePx <= 0.5f) {
                                        absorbFlingOvershoot(available.y)
                                    } else {
                                        settleVisualOvershoot()
                                    }
                                    return Velocity.Zero
                                }
                            }
                        }

                        fun headerAlbumForTab(tab: Int): Album {
                            return if (tab == 0) (model.localAlbum ?: album) else album
                        }

                        fun shouldShowCoverLoading(tab: Int, headerAlbum: Album): Boolean {
                            val headerHasCover = headerAlbum.coverPath.trim().isNotBlank() ||
                                headerAlbum.coverUrl.trim().isNotBlank()
                            return !headerHasCover && when (tab) {
                                0 -> false
                                1 -> model.isLoadingDlsite ||
                                    model.isLoadingAsmrOne ||
                                    !model.hasResolvedInitialDlsiteTarget
                                else -> model.isLoadingDlsite ||
                                    model.isLoadingDlsitePlay ||
                                    !model.hasResolvedInitialDlsiteTarget
                            }
                        }

                        val activeHeroAlbum = headerAlbumForTab(selectedTab)
                        val showHeroCoverLoadingState = shouldShowCoverLoading(selectedTab, activeHeroAlbum)

                        val headerContent: @Composable (Int) -> Unit = { tab ->
                            val isLocalTab = tab == 0
                            val resolvedInitialTarget = model.hasResolvedInitialDlsiteTarget
                            val canUseAsmrOneTreeActions = canUseAsmrOneOnlineTreeActions(
                                selectedTab = tab,
                                hasAsmrOneTree = asmrOneTree.isNotEmpty()
                            )
                            val headerDownloadEnabled = albumHeaderDownloadEnabled(
                                selectedTab = tab,
                                hasAsmrOneTree = asmrOneTree.isNotEmpty(),
                                hasDlsitePlayTree = model.dlsitePlayTree.isNotEmpty(),
                                hasResolvedInitialDlsiteTarget = resolvedInitialTarget,
                                hasValidLocalRj = hasValidLocalRj,
                                hasDlsitePlayCredentials = hasDlsitePlayCredentials
                            )
                            val headerAlbum = headerAlbumForTab(tab)
                            val incrementalSource = when (tab) {
                                0 -> localOnlineSource
                                1 -> OnlineDownloadSource.AsmrOne
                                2 -> OnlineDownloadSource.DlsitePlay
                                else -> null
                            }
                            val incrementalTree = when (incrementalSource) {
                                OnlineDownloadSource.AsmrOne -> asmrOneTree
                                OnlineDownloadSource.DlsitePlay -> model.dlsitePlayTree
                                else -> emptyList()
                            }
                            val headerDlsiteEditions = if (isLocalTab) {
                                emptyList()
                            } else {
                                model.dlsiteEditions.ifEmpty {
                                    listOf(
                                        DlsiteLanguageEdition(
                                            workno = model.baseRjCode.ifBlank { model.rjCode },
                                            lang = "JPN",
                                            label = "日本語",
                                            displayOrder = 1
                                        )
                                    )
                                }
                            }
                            AlbumHeader(
                                album = headerAlbum,
                                dlsiteUrl = model.dlsiteWorkno.takeIf { it.isNotBlank() }?.let { "$DLSITE_DOMAIN${storeSegment()}/work/=/product_id/$it.html" }.orEmpty(),
                                asmrOneUrl = model.asmrOneWorkId?.takeIf { it.isNotBlank() }?.let { "https://asmr.one/work/$it" }.orEmpty(),
                                dlsiteEditions = headerDlsiteEditions,
                                dlsiteSelectedLang = model.dlsiteSelectedLang,
                                onDlsiteLangSelected = { viewModel.selectDlsiteLanguage(it) },
                                showSaveAction = tab != 2,
                                onDownloadClick = {
                                    incrementalSource?.let { source ->
                                        prepareIncrementalAlbumAction(
                                            action = IncrementalAlbumAction.Download,
                                            source = source,
                                            tree = incrementalTree
                                        )
                                    }
                                },
                                showDlsitePlayLossless = tab == 2,
                                onLosslessDownloadClick = {
                                    viewModel.downloadDlsitePlayLosslessArchive()
                                },
                                onSaveClick = {
                                    incrementalSource?.let { source ->
                                        prepareIncrementalAlbumAction(
                                            action = IncrementalAlbumAction.Save,
                                            source = source,
                                            tree = incrementalTree
                                        )
                                    }
                                },
                                downloadEnabled = headerDownloadEnabled,
                                losslessDownloadEnabled = tab == 2 && resolvedInitialTarget && model.dlsitePlayTree.isNotEmpty(),
                                saveEnabled = if (isLocalTab) headerDownloadEnabled else canUseAsmrOneTreeActions,
                                showGroupButton = isLocalTab && model.localAlbum != null,
                                onOpenGroupPicker = { id -> groupPickerAlbumId = id },
                                introSessionKey = introSessionKey,
                                animateIntro = shouldAnimateHeaderIntro,
                                availableWidth = if (useLandscapeArtworkTide) {
                                    landscapeHeaderAvailableWidth
                                } else {
                                    (maxWidth - AlbumDetailHorizontalPadding * 2).coerceAtLeast(0.dp)
                                },
                                messageManager = viewModel.messageManager,
                                onMetaLongClick = ::openMetaActions,
                                landscapeFloatingActions = useLandscapeArtworkTide
                            )
                        }

                        val listHeaderContent: @Composable (Int) -> Unit = { tab ->
                            if (!useLandscapeArtworkTide) {
                                headerContent(tab)
                            }
                        }

                        val landscapeFixedHeaderContent: @Composable () -> Unit = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AlbumDetailLandscapeIdentity(
                                    album = headerAlbumForTab(selectedTab),
                                    introSessionKey = introSessionKey,
                                    animateIntro = shouldPlayInitialAnimations,
                                    pageContainerColor = pageContainerColor,
                                    listenTogetherRjListenerCount = model.listenTogetherRjListenerCount,
                                    messageManager = viewModel.messageManager,
                                    onMetaLongClick = ::openMetaActions
                                )
                                headerContent(selectedTab)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(pageContainerColor)
                                .clipToBounds()
                                .zIndex(0f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .consumeTapThrough()
                            )

                            if (useLandscapeArtworkTide) {
                                AlbumDetailLandscapeArtworkBackdrop(
                                    album = activeHeroAlbum,
                                    coverSessionKey = screenKey,
                                    artworkSize = landscapeArtworkSize,
                                    pageContainerColor = pageContainerColor,
                                    collapsePx = { heroMotion.collapsePx },
                                    modifier = Modifier
                                        .matchParentSize()
                                        .zIndex(0f)
                                )
                                AlbumDetailLandscapeArtworkCover(
                                    album = activeHeroAlbum,
                                    coverSessionKey = screenKey,
                                    introSessionKey = introSessionKey,
                                    animateIntro = shouldAnimateHeaderIntro,
                                    artworkSize = landscapeArtworkSize,
                                    showCoverLoadingState = showHeroCoverLoadingState,
                                    collapsePx = { heroMotion.collapsePx },
                                    collapseMaxPx = heroCollapseMaxPx,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .zIndex(1f)
                                )
                            } else {
                                AlbumDetailHeroBackground(
                                    album = activeHeroAlbum,
                                    coverSessionKey = screenKey,
                                    introSessionKey = introSessionKey,
                                    animateIntro = shouldAnimateHeaderIntro,
                                    height = heroHeight,
                                    pageContainerColor = pageContainerColor,
                                    listenTogetherRjListenerCount = model.listenTogetherRjListenerCount,
                                    showCoverLoadingState = showHeroCoverLoadingState,
                                    messageManager = viewModel.messageManager,
                                    onMetaLongClick = ::openMetaActions,
                                    blurLayerCache = heroBlurLayerCache,
                                    collapsePx = { heroMotion.collapsePx },
                                    collapseMaxPx = heroCollapseMaxPx,
                                    visualOvershootPx = { heroMotion.visualOvershootPx },
                                    visualOvershootMaxPx = heroVisualOvershootMaxPx,
                                    modifier = Modifier.align(Alignment.TopCenter)
                                )
                            }

                            LaunchedEffect(
                                selectedTab,
                                model.rjCode,
                                model.dlsiteWorkno,
                                model.hasResolvedInitialDlsiteTarget,
                                model.hasResolvedAsmrOneContent,
                                asmrOneTree.isNotEmpty(),
                                hasDlsitePlayCredentials,
                                dlsitePlayAuthSnapshot.fingerprint,
                                localActionRj,
                                isInitialRouteReady
                            ) {
                                val loadPlan = albumDetailOnlineLoadPlan(
                                    selectedTab = selectedTab,
                                    hasResolvedInitialDlsiteTarget = model.hasResolvedInitialDlsiteTarget,
                                    isInitialRouteReady = isInitialRouteReady,
                                    hasValidLocalRj = hasValidLocalRj,
                                    hasResolvedAsmrOneContent = model.hasResolvedAsmrOneContent,
                                    hasAsmrOneTree = asmrOneTree.isNotEmpty(),
                                    hasDlsitePlayCredentials = hasDlsitePlayCredentials
                                )
                                if (loadPlan.loadDlsite) {
                                    viewModel.ensureDlsiteLoaded()
                                }
                                if (loadPlan.loadAsmrOne) {
                                    viewModel.ensureAsmrOneLoaded()
                                }
                                if (loadPlan.loadDlsitePlay) {
                                    viewModel.ensureDlsitePlayLoaded(showFailureMessage = selectedTab != 0)
                                }
                            }

                            val asmrOneTreeStateKey = asmrOneDirectoryTreeStateKey(
                                currentRj = model.rjCode,
                                baseRj = model.baseRjCode
                            )
                            val asmrOneScrollStateKey = "scroll:$asmrOneTreeStateKey"
                            val landscapeContentShape = rememberAlbumLandscapeContentShape(
                                waveDepth = landscapeContentWaveDepth
                            )
                            val contentSurfaceModifier = if (useLandscapeArtworkTide) {
                                Modifier
                                    .nestedScroll(heroNestedScroll)
                                    .shadow(
                                        elevation = if (colorScheme.isDark) 3.dp else 8.dp,
                                        shape = landscapeContentShape,
                                        clip = false
                                    )
                                    .clip(landscapeContentShape)
                                    .background(
                                        colorScheme.surface.copy(
                                            alpha = if (colorScheme.isDark) 0.92f else 0.94f
                                        )
                                    )
                                    .border(
                                        width = AlbumLandscapeSurfaceBorderWidth,
                                        color = colorScheme.primary.copy(
                                            alpha = if (colorScheme.isDark) 0.20f else 0.12f
                                        ),
                                        shape = landscapeContentShape
                                    )
                            } else {
                                Modifier
                                    .nestedScroll(heroNestedScroll)
                                    .clipToBounds()
                                    .background(pageContainerColor)
                                    .albumDetailScrolledContentFade(
                                        fadeStartY = contentFadeStartY,
                                        fadeEndY = contentFadeEndY,
                                        fadeColor = pageContainerColor
                                    )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(
                                        if (useLandscapeArtworkTide) {
                                            albumLandscapeSurfaceHeight(
                                                contentViewportHeight = contentViewportHeight,
                                                artworkSize = landscapeArtworkSize
                                            )
                                        } else {
                                            contentViewportHeight + heroHeight * 0.5f
                                        }
                                    )
                                    .offset {
                                        IntOffset(
                                            0,
                                            (contentViewportTopPx - heroMotion.collapsePx).roundToInt()
                                        )
                                    }
                                    .then(contentSurfaceModifier)
                                    .zIndex(if (useLandscapeArtworkTide) 2f else 0f)
                            ) {
                                if (useLandscapeArtworkTide) {
                                    AlbumLandscapeCurvePlaybackProgress(
                                        waveDepth = landscapeContentWaveDepth,
                                        modifier = Modifier
                                            .matchParentSize()
                                            .zIndex(1f)
                                    )
                                }

                                Box(
                                    modifier = if (useLandscapeArtworkTide) {
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .fillMaxHeight()
                                            .width(
                                                (landscapePageWidth - landscapeHeaderStart)
                                                    .coerceAtLeast(320.dp)
                                            )
                                            .padding(top = landscapeDirectoryTop)
                                    } else {
                                        Modifier.fillMaxSize()
                                    }
                                ) {
                                    when (selectedTab) {
                                    0 -> {
                                        val local = model.localAlbum
                                        if (local != null) {
                                            val localTreeStateKey = remember(albumId, rjCode, local.id) {
                                                val rjNorm = rjCode?.trim().orEmpty().uppercase()
                                                when {
                                                    albumId != null && albumId > 0 -> "localTree:id:$albumId"
                                                    rjNorm.isNotBlank() -> "localTree:rj:$rjNorm"
                                                    else -> "localTree:localId:${local.id}"
                                                }
                                            }
                                            AlbumLocalBreadcrumbTabV2(
                                                stateKey = localTreeStateKey,
                                                initialCurrentPath = viewModel.getPreferredTreeCurrentPath(localTreeStateKey)
                                                    .ifBlank { viewModel.getTreeCurrentPath(localTreeStateKey) },
                                                onPersistCurrentPath = { path ->
                                                    viewModel.persistTreeCurrentPath(localTreeStateKey, path)
                                                },
                                                initialScroll = viewModel.getListScrollPosition("scroll:$localTreeStateKey"),
                                                onPersistScroll = { index, offset ->
                                                    viewModel.persistListScrollPosition("scroll:$localTreeStateKey", index, offset)
                                                },
                                                topContentPadding = 0.dp,
                                                album = local,
                                                header = { listHeaderContent(0) },
                                                onPlayMediaItems = onPlayMediaItems,
                                                onAddToQueue = { track ->
                                                    onAddToQueue(local, track)
                                                },
                                                onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                                                onAddMediaItemsToFavorites = onAddMediaItemsToFavorites,
                                                onOpenBatchPlaylistPicker = { items -> batchPlaylistItems = items },
                                                preferredCurrentPath = viewModel.getPreferredTreeCurrentPath(localTreeStateKey),
                                                onTogglePreferredCurrentPath = { path, enabled ->
                                                    if (enabled) {
                                                        viewModel.persistPreferredTreeCurrentPath(localTreeStateKey, path)
                                                        viewModel.messageManager.showSuccess("已设为默认打开目录")
                                                    } else {
                                                        viewModel.clearPreferredTreeCurrentPath(localTreeStateKey)
                                                    }
                                                },
                                                onAddToPlaylist = { track ->
                                                    val target = PlaylistAddTarget.fromTrack(local, track)
                                                    onOpenPlaylistPicker(target.toMediaItem())
                                                },
                                                onDownloadOnlineTrack = { track, relativePath ->
                                                    viewModel.downloadSavedOnlineTrack(track, relativePath)
                                                },
                                                onManageTrackTags = { track ->
                                                    tagManageTrack = track
                                                },
                                                onRemoveTrack = { track ->
                                                    if (track.id > 0L) libraryViewModel.removeTrackFromAlbum(track.id)
                                                },
                                                onDeleteTreeEntry = { target, onComplete ->
                                                    libraryViewModel.deleteAlbumTreeEntry(
                                                        album = local,
                                                        target = target,
                                                        onComplete = onComplete,
                                                    )
                                                },
                                                onSetCoverFromImage = { pathOrUri ->
                                                    viewModel.setLocalCoverPath(pathOrUri)
                                                },
                                                onPreviewImages = { request -> imagePreviewRequest = request },
                                                onPreviewFile = { localPreviewFile = it },
                                                onSubtitleGenerationError = viewModel.messageManager::showError,
                                                onSubtitleGenerationUnavailable = viewModel.messageManager::showWarning,
                                                onSubtitleGenerationQueued = viewModel.messageManager::showInfo,
                                                onListStateAvailable = { landscapeActiveListState = it },
                                                animateIntro = shouldPlayInitialAnimations
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (albumId != null && albumId > 0) {
                                                    EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                                                } else {
                                                    Text("未下载到本地")
                                                }
                                            }
                                        }
                                    }
                                    1 -> AlbumDlsiteInfoBreadcrumbTabV2(
                                        album = album,
                                        header = { listHeaderContent(1) },
                                        galleryUrls = model.dlsiteGalleryUrls,
                                        trialTracks = model.dlsiteTrialTracks,
                                        trialDownloadEnabled = trialDownloadTree.isNotEmpty(),
                                        isLoading = model.isLoadingDlsite,
                                        isAwaitingInitialLoad = !model.hasLoadedInitialDlsiteContent,
                                        isAwaitingAsmrOneLoad = !model.hasResolvedAsmrOneContent &&
                                            asmrOneTree.isEmpty(),
                                        hasResolvedAsmrOneContent = model.hasResolvedAsmrOneContent,
                                        asmrOneTree = asmrOneTree,
                                        isLoadingAsmrOne = model.isLoadingAsmrOne,
                                        isLoadingTrial = model.isLoadingDlsiteTrial,
                                        onRefreshAsmrOne = { viewModel.refreshAsmrOneSection() },
                                        onRefreshTrial = { viewModel.refreshDlsiteTrialSection() },
                                        onDownloadTrial = {
                                            downloadDisabledPaths = emptySet()
                                            downloadSource = OnlineDownloadSource.DlsiteTrial
                                            showAsmrDownloadDialog = true
                                        },
                                        onPlayTracks = onPlayTracks,
                                        onPlayMediaItems = onPlayMediaItems,
                                        onAddToQueue = { track ->
                                            onAddToQueue(album, track)
                                        },
                                        onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                                        onAddMediaItemsToFavorites = onAddMediaItemsToFavorites,
                                        onOpenBatchPlaylistPicker = { items -> batchPlaylistItems = items },
                                        onDownloadOne = { relPath ->
                                            viewModel.downloadAsmrOneSelected(setOf(relPath))
                                        },
                                        onAddToPlaylistOne = { relPath ->
                                            val target = PlaylistAddTarget.fromAsmrOne(album, asmrOneTree, relPath) ?: return@AlbumDlsiteInfoBreadcrumbTabV2
                                            onOpenPlaylistPicker(target.toMediaItem())
                                        },
                                        onAddToPlaylist = { track ->
                                            val target = PlaylistAddTarget.fromTrack(album, track)
                                            onOpenPlaylistPicker(target.toMediaItem())
                                        },
                                        onPreviewImages = { request -> imagePreviewRequest = request },
                                        onPreviewFile = { onlinePreviewFile = it },
                                        treeStateKey = asmrOneTreeStateKey,
                                        initialCurrentPath = viewModel.getTreeCurrentPath(asmrOneTreeStateKey),
                                        topContentPadding = 0.dp,
                                        animateIntro = shouldPlayInitialAnimations,
                                        onPersistCurrentPath = { path ->
                                            viewModel.persistTreeCurrentPath(asmrOneTreeStateKey, path)
                                        },
                                        initialScroll = viewModel.getListScrollPosition(asmrOneScrollStateKey),
                                        onPersistScroll = { index, offset ->
                                            viewModel.persistListScrollPosition(asmrOneScrollStateKey, index, offset)
                                        },
                                        onListStateAvailable = { landscapeActiveListState = it },
                                        dlsiteRecommendations = model.dlsiteRecommendations,
                                        onOpenAlbumByRj = onOpenAlbumByRj,
                                        loadRemoteFileSize = { viewModel.loadRemoteFileSize(it) }
                                    )
                                    else -> AlbumDlsitePlayBreadcrumbTabV2(
                                        header = { listHeaderContent(2) },
                                        album = album,
                                        rjCode = model.rjCode,
                                        tree = model.dlsitePlayTree,
                                        isLoading = model.isLoadingDlsitePlay,
                                        shouldAutoLoad = selectedTab == 2 && model.hasResolvedInitialDlsiteTarget,
                                        isAwaitingInitialTarget = selectedTab == 2 && !model.hasResolvedInitialDlsiteTarget,
                                        hasResolvedDlsitePlayContent = model.hasResolvedDlsitePlayContent,
                                        onOpenLogin = onOpenDlsiteLogin,
                                        onEnsureLoaded = { viewModel.ensureDlsitePlayLoaded() },
                                        onPlayMediaItems = onPlayMediaItems,
                                        onAddToQueue = { track ->
                                            onAddToQueue(album, track)
                                        },
                                        onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                                        onAddMediaItemsToFavorites = onAddMediaItemsToFavorites,
                                        onOpenBatchPlaylistPicker = { items -> batchPlaylistItems = items },
                                        onDownloadOne = { relPath ->
                                            viewModel.downloadDlsitePlaySelected(setOf(relPath))
                                        },
                                        onPreviewImages = { request -> imagePreviewRequest = request },
                                        onPreviewFile = { onlinePreviewFile = it },
                                        prepareImagePreview = viewModel::prepareDlsitePlayImagePreview,
                                        treeStateKey = "tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}",
                                        initialCurrentPath = viewModel.getTreeCurrentPath("tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}"),
                                        topContentPadding = 0.dp,
                                        animateIntro = shouldPlayInitialAnimations,
                                        onPersistCurrentPath = { path ->
                                            val rj = model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()
                                            viewModel.persistTreeCurrentPath("tree:dlsitePlay:$rj", path)
                                        },
                                        initialScroll = viewModel.getListScrollPosition("scroll:tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}"),
                                        onPersistScroll = { index, offset ->
                                            viewModel.persistListScrollPosition("scroll:tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}", index, offset)
                                        },
                                        onListStateAvailable = { landscapeActiveListState = it },
                                        loadRemoteFileSize = { viewModel.loadRemoteFileSize(it) }
                                    )
                                    }
                                }

                                if (useLandscapeArtworkTide) {
                                    AlbumDetailLandscapeSimilarWorksPane(
                                        seedRjCode = model.baseRjCode.ifBlank { model.rjCode },
                                        isRouteReady = isInitialRouteReady,
                                        onOpenAlbumByRj = onOpenAlbumByRj,
                                        viewModel = viewModel,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .width(landscapeHeaderStart)
                                            .albumLandscapePaneViewportHeight(
                                                collapsePx = { heroMotion.collapsePx },
                                                collapseMaxPx = heroCollapseMaxPx
                                            )
                                            .padding(
                                                start = 20.dp,
                                                end = 12.dp,
                                                top = landscapeContentWaveDepth * 1.10f
                                            )
                                    )

                                }
                            }

                            if (useLandscapeArtworkTide) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .width(
                                            (landscapePageWidth - landscapeHeaderStart)
                                                .coerceAtLeast(320.dp)
                                        )
                                        .wrapContentHeight()
                                        .offset {
                                            IntOffset(
                                                0,
                                                (
                                                    contentViewportTopPx -
                                                        AlbumLandscapeHeaderLift.toPx() -
                                                        heroMotion.collapsePx
                                                    ).roundToInt()
                                            )
                                        }
                                        .padding(end = AlbumLandscapeHeaderEndPadding)
                                        .onSizeChanged { size ->
                                            landscapeFixedHeaderHeightPx = size.height
                                        }
                                        .pointerInput(
                                            heroCollapseMaxPx,
                                            landscapeActiveListState
                                        ) {
                                            detectVerticalDragGestures { change, dragAmount ->
                                                val requestedScroll = -dragAmount
                                                var consumedScroll = 0f

                                                if (requestedScroll > 0f) {
                                                    val currentCollapse = heroMotion.collapsePx
                                                    val targetCollapse = (currentCollapse + requestedScroll)
                                                        .coerceIn(0f, heroCollapseMaxPx)
                                                    val appliedCollapse = targetCollapse - currentCollapse
                                                    if (appliedCollapse != 0f) {
                                                        heroMotion.cancelVisualOvershootAnimation()
                                                        heroMotion.collapsePx = targetCollapse
                                                        consumedScroll += appliedCollapse
                                                    }
                                                    val listDelta = requestedScroll - appliedCollapse
                                                    if (listDelta > 0f) {
                                                        consumedScroll += landscapeActiveListState
                                                            ?.dispatchRawDelta(listDelta)
                                                            ?: 0f
                                                    }
                                                } else if (requestedScroll < 0f) {
                                                    val listConsumed = landscapeActiveListState
                                                        ?.dispatchRawDelta(requestedScroll)
                                                        ?: 0f
                                                    consumedScroll += listConsumed
                                                    val collapseDelta = requestedScroll - listConsumed
                                                    if (collapseDelta < 0f) {
                                                        val currentCollapse = heroMotion.collapsePx
                                                        val targetCollapse = (currentCollapse + collapseDelta)
                                                            .coerceIn(0f, heroCollapseMaxPx)
                                                        val appliedCollapse = targetCollapse - currentCollapse
                                                        if (appliedCollapse != 0f) {
                                                            heroMotion.cancelVisualOvershootAnimation()
                                                            heroMotion.collapsePx = targetCollapse
                                                            consumedScroll += appliedCollapse
                                                        }
                                                    }
                                                }

                                                if (consumedScroll != 0f) change.consume()
                                            }
                                        }
                                        .zIndex(3f)
                                ) {
                                    landscapeFixedHeaderContent()
                                }
                            }

                        }
                    }
                }

                val canSaveOnline = if (selectedTab == 0) {
                    hasValidLocalRj && localOnlineSource != null
                } else {
                    canUseAsmrOneOnlineTreeActions(
                        selectedTab = selectedTab,
                        hasAsmrOneTree = asmrOneTree.isNotEmpty()
                    )
                }
                if (showAsmrDownloadDialog) {
                    val downloadTree = when (downloadSource) {
                        OnlineDownloadSource.AsmrOne -> asmrOneTree
                        OnlineDownloadSource.DlsitePlay -> model.dlsitePlayTree
                        OnlineDownloadSource.DlsiteTrial -> trialDownloadTree
                    }
                    AsmrOneDownloadDialog(
                        albumTitle = album.title,
                        trackTree = downloadTree,
                        disabledPaths = downloadDisabledPaths,
                        onDismiss = { showAsmrDownloadDialog = false },
                        onConfirm = { selected ->
                            when (downloadSource) {
                                OnlineDownloadSource.AsmrOne -> viewModel.downloadAsmrOneSelected(selected)
                                OnlineDownloadSource.DlsitePlay -> viewModel.downloadDlsitePlaySelected(selected)
                                OnlineDownloadSource.DlsiteTrial -> viewModel.downloadDlsiteTrialSelected(selected)
                            }
                            showAsmrDownloadDialog = false
                        }
                    )
                }

                if (showOnlineSaveDialog && canSaveOnline) {
                    val saveTree = when (onlineSaveSource) {
                        OnlineDownloadSource.DlsitePlay -> model.dlsitePlayTree
                        else -> asmrOneTree
                    }
                    OnlineSaveDialog(
                        albumTitle = album.title,
                        trackTree = saveTree,
                        disabledPaths = saveDisabledPaths,
                        onDismiss = { showOnlineSaveDialog = false },
                        onConfirm = { selected ->
                            pendingOnlineSaveSelection = PendingOnlineSaveSelection(
                                paths = selected,
                                useDlsitePlayTree = onlineSaveSource == OnlineDownloadSource.DlsitePlay
                            )
                            showOnlineSaveDialog = false
                        }
                    )
                }

                groupPickerAlbumId?.let { targetAlbumId ->
                    Dialog(
                        onDismissRequest = { groupPickerAlbumId = null },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        AlbumDetailPickerSheetSurface(
                            color = MaterialTheme.colorScheme.background,
                            contentColor = colorScheme.textPrimary
                        ) {
                            AlbumGroupPickerScreen(
                                windowSizeClass = windowSizeClass,
                                albumId = targetAlbumId,
                                onBack = { groupPickerAlbumId = null },
                                embeddedInDialog = true
                            )
                        }
                    }
                }

                batchPlaylistItems?.let { items ->
                    Dialog(
                        onDismissRequest = { batchPlaylistItems = null },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = colorScheme.background.copy(alpha = 0.96f),
                            contentColor = colorScheme.textPrimary
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp)
                            ) {
                                PlaylistPickerScreen(
                                    windowSizeClass = windowSizeClass,
                                    items = items,
                                    onBack = { batchPlaylistItems = null },
                                    embeddedInDialog = true
                                )
                            }
                        }
                    }
                }

                if (localPreviewFile != null) {
                    FilePreviewDialog(
                        title = localPreviewFile!!.title,
                        absolutePath = localPreviewFile!!.absolutePath,
                        fileType = localPreviewFile!!.fileType,
                        messageManager = viewModel.messageManager,
                        loadOnlineText = viewModel::loadOnlineTextPreview,
                        onDismiss = { localPreviewFile = null }
                    )
                }

                if (onlinePreviewFile != null) {
                    FilePreviewDialog(
                        title = onlinePreviewFile!!.title,
                        absolutePath = onlinePreviewFile!!.url ?: "",
                        fileType = onlinePreviewFile!!.fileType,
                        messageManager = viewModel.messageManager,
                        loadOnlineText = viewModel::loadOnlineTextPreview,
                        onDismiss = { onlinePreviewFile = null }
                    )
                }

                imagePreviewRequest?.let { request ->
                    ImagePreviewDialog(
                        request = request,
                        messageManager = viewModel.messageManager,
                        onDismiss = { imagePreviewRequest = null }
                    )
                }

                metaActionKeyword?.let { keyword ->
                    val searchBlockedKeywords by settingsViewModel.searchBlockedKeywords.collectAsStateWithLifecycle()
                    AlbumMetaActionDialog(
                        keyword = keyword,
                        onDismissRequest = { metaActionKeyword = null },
                        onSearch = onSearchKeyword,
                        onCreatePlaylist = playlistsViewModel::createPlaylist,
                        onCreateGroup = albumGroupsViewModel::createGroup,
                        onAddBlockedKeyword = { value ->
                            val normalized = value.trim()
                            if (normalized.isNotBlank()) {
                                val exists = searchBlockedKeywords.any {
                                    it.equals(normalized, ignoreCase = true)
                                }
                                settingsViewModel.addSearchBlockedKeyword(normalized)
                                if (exists) {
                                    viewModel.messageManager.showInfo("屏蔽词已存在：$normalized")
                                } else {
                                    viewModel.messageManager.showSuccess("已添加屏蔽词：$normalized")
                                }
                            }
                        },
                    )
                }

                val track = tagManageTrack
                if ((track != null && track.id > 0L) || showTagManager) {
                    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
                    val userTagsByTrackId by viewModel.userTagsByTrackId.collectAsStateWithLifecycle()
                    if (track != null && track.id > 0L) {
                        TagAssignDialog(
                            title = track.title,
                            inheritedTags = album.tags,
                            userTags = userTagsByTrackId[track.id].orEmpty(),
                            allTags = availableTags,
                            onApplyUserTags = { list ->
                                viewModel.setUserTagsForTrack(track.id, list)
                                tagManageTrack = null
                            },
                            onDismiss = { tagManageTrack = null },
                            onOpenTagManager = { showTagManager = true }
                        )
                    }

                    if (showTagManager) {
                        Dialog(
                            onDismissRequest = { showTagManager = false },
                            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                TagManagerSheet(
                                    tags = availableTags,
                                    onRename = { tagId, newName -> libraryViewModel.renameUserTag(tagId, newName) },
                                    onDelete = { tagId -> libraryViewModel.deleteUserTag(tagId) },
                                    onClose = { showTagManager = false }
                                )
                            }
                        }
                    }
                }
            }
                is AlbumDetailUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        cloudSyncSelectionDialogState?.let { dialogState ->
            CloudSyncSelectionDialog(
                state = dialogState,
                onSelect = viewModel::confirmCloudSyncSelection,
                onCancel = viewModel::cancelCloudSyncSelection
            )
        }
    }
}

private data class AlbumHeroBlurSource(
    val painter: BitmapPainter,
    val alpha: State<Float>
)

class AlbumHeroBlurLayerCache(
    val layer: GraphicsLayer
) {
    private var contentKey: Any? = null
    private var layerSize: IntSize = IntSize.Zero
    private var fullHeroSize: IntSize = IntSize.Zero

    fun matches(
        contentKey: Any?,
        layerSize: IntSize,
        fullHeroSize: IntSize
    ): Boolean =
        this.contentKey == contentKey &&
            this.layerSize == layerSize &&
            this.fullHeroSize == fullHeroSize

    fun markRecorded(
        contentKey: Any?,
        layerSize: IntSize,
        fullHeroSize: IntSize
    ) {
        this.contentKey = contentKey
        this.layerSize = layerSize
        this.fullHeroSize = fullHeroSize
    }
}

@Composable
private fun rememberAlbumLandscapeContentShape(
    waveDepth: Dp
): Shape {
    val density = LocalDensity.current
    val waveDepthPx = with(density) { waveDepth.toPx() }
    return remember(waveDepthPx) {
        GenericShape { size, _ ->
            val depth = waveDepthPx.coerceIn(0f, size.height * 0.40f)
            addAlbumLandscapeTopCurve(size = size, depth = depth)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
    }
}

private fun Path.addAlbumLandscapeTopCurve(size: Size, depth: Float) {
    addCatmullRomSpline(
        points = listOf(
            Offset(0f, depth * 1.06f),
            Offset(size.width * 0.14f, depth * 1.08f),
            Offset(size.width * 0.25f, depth * 0.98f),
            Offset(size.width * 0.34f, depth * 0.62f),
            Offset(size.width * 0.44f, depth * 0.28f),
            Offset(size.width * 0.60f, depth * 0.12f),
            Offset(size.width * 0.80f, depth * 0.13f),
            Offset(size.width, depth * 0.18f)
        ),
        smoothness = 0.84f
    )
}

@Composable
private fun AlbumLandscapeCurvePlaybackProgress(
    waveDepth: Dp,
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val playbackIndicator by remember(playerViewModel) {
        playerViewModel.playback
            .map { playback ->
                AlbumLandscapePlaybackIndicator(
                    progress = albumLandscapePlaybackProgress(
                        playback.positionMs,
                        playback.durationMs
                    ),
                    isPlaying = playback.isPlaying
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = AlbumLandscapePlaybackIndicator())
    val progressState = rememberUpdatedState(playbackIndicator.progress)
    val pulseEnabled = albumLandscapePulseEnabled(
        isPlaying = playbackIndicator.isPlaying,
        progress = playbackIndicator.progress
    )
    val pulseEnabledState = rememberUpdatedState(pulseEnabled)
    val pulsePhase = remember { Animatable(0f) }
    LaunchedEffect(pulseEnabled) {
        if (!pulseEnabled) {
            pulsePhase.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            pulsePhase.snapTo(0f)
            pulsePhase.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 3_200, easing = LinearEasing)
            )
            delay(260L)
        }
    }
    val colorScheme = AsmrTheme.colorScheme
    val trackColor = colorScheme.primary.copy(
        alpha = if (colorScheme.isDark) 0.24f else 0.18f
    )
    val activeColor = colorScheme.primaryStrong.copy(alpha = 0.92f)
    val pulseColor = colorScheme.primaryStrong

    Box(
        modifier = modifier.drawWithCache {
            val depth = waveDepth.toPx().coerceIn(0f, size.height * 0.40f)
            val curvePath = Path().apply {
                addAlbumLandscapeTopCurve(size = size, depth = depth)
            }
            val pathMeasure = AndroidPathMeasure(curvePath.asAndroidPath(), false)
            val activePath = Path()
            val pulsePath = Path()
            val pulsePosition = FloatArray(2)
            val trackStroke = Stroke(width = 0.75.dp.toPx(), cap = StrokeCap.Round)
            val activeStroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            val pulseGlowStroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            val pulseCoreStroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            val maximumPulseLength = 58.dp.toPx()

            onDrawBehind {
                drawPath(path = curvePath, color = trackColor, style = trackStroke)
                val fraction = progressState.value.coerceIn(0f, 1f)
                if (fraction > 0f && pathMeasure.length > 0f) {
                    activePath.reset()
                    pathMeasure.getSegment(
                        0f,
                        pathMeasure.length * fraction,
                        activePath.asAndroidPath(),
                        true
                    )
                    drawPath(path = activePath, color = activeColor, style = activeStroke)

                    if (pulseEnabledState.value) {
                        val activeLength = pathMeasure.length * fraction
                        val rawPulsePhase = pulsePhase.value.coerceIn(0f, 1f)
                        val pulseEnd = activeLength *
                            albumLandscapePulseSweepFraction(rawPulsePhase)
                        val pulseLength = minOf(maximumPulseLength, activeLength * 0.24f)
                        val pulseStart = (pulseEnd - pulseLength).coerceAtLeast(0f)
                        val pulseEnvelope = kotlin.math.sin(Math.PI * rawPulsePhase)
                            .toFloat()
                            .coerceIn(0f, 1f)
                        if (pulseEnvelope > 0f && pulseEnd > pulseStart) {
                            pulsePath.reset()
                            pathMeasure.getSegment(
                                pulseStart,
                                pulseEnd,
                                pulsePath.asAndroidPath(),
                                true
                            )
                            drawPath(
                                path = pulsePath,
                                color = pulseColor.copy(alpha = 0.20f * pulseEnvelope),
                                style = pulseGlowStroke
                            )
                            drawPath(
                                path = pulsePath,
                                color = pulseColor.copy(alpha = 0.96f * pulseEnvelope),
                                style = pulseCoreStroke
                            )
                            if (pathMeasure.getPosTan(pulseEnd, pulsePosition, null)) {
                                drawCircle(
                                    color = pulseColor.copy(alpha = 0.88f * pulseEnvelope),
                                    radius = 2.8.dp.toPx(),
                                    center = Offset(pulsePosition[0], pulsePosition[1])
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

private data class AlbumLandscapePlaybackIndicator(
    val progress: Float = 0f,
    val isPlaying: Boolean = false
)

private fun Path.addCatmullRomSpline(
    points: List<Offset>,
    smoothness: Float
) {
    if (points.isEmpty()) return
    moveTo(points.first().x, points.first().y)
    if (points.size == 1) return

    val tangentScale = smoothness.coerceIn(0f, 1f) / 6f
    for (index in 0 until points.lastIndex) {
        val p0 = points[(index - 1).coerceAtLeast(0)]
        val p1 = points[index]
        val p2 = points[index + 1]
        val p3 = points[(index + 2).coerceAtMost(points.lastIndex)]
        cubicTo(
            p1.x + (p2.x - p0.x) * tangentScale,
            p1.y + (p2.y - p0.y) * tangentScale,
            p2.x - (p3.x - p1.x) * tangentScale,
            p2.y - (p3.y - p1.y) * tangentScale,
            p2.x,
            p2.y
        )
    }
}

private val AlbumLandscapeArtworkRibbonShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height * 0.88f)
    cubicTo(
        size.width * 0.82f,
        size.height * 0.98f,
        size.width * 0.66f,
        size.height * 0.84f,
        size.width * 0.48f,
        size.height * 0.94f
    )
    cubicTo(
        size.width * 0.30f,
        size.height,
        size.width * 0.14f,
        size.height * 0.88f,
        0f,
        size.height * 0.92f
    )
    close()
}

@Composable
private fun rememberAlbumLandscapeArtworkRibbonCoreShape(edgeInset: Dp): Shape {
    val density = LocalDensity.current
    val edgeInsetPx = with(density) { edgeInset.toPx() }
    return remember(edgeInsetPx) {
        GenericShape { size, _ ->
            val inset = edgeInsetPx.coerceAtMost(size.height * 0.08f)
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * 0.88f - inset)
            cubicTo(
                size.width * 0.82f,
                size.height * 0.98f - inset,
                size.width * 0.66f,
                size.height * 0.84f - inset,
                size.width * 0.48f,
                size.height * 0.94f - inset
            )
            cubicTo(
                size.width * 0.30f,
                size.height - inset,
                size.width * 0.14f,
                size.height * 0.88f - inset,
                0f,
                size.height * 0.92f - inset
            )
            close()
        }
    }
}

@Composable
private fun AlbumDetailLandscapeArtworkBackdrop(
    album: Album,
    coverSessionKey: String,
    artworkSize: Dp,
    pageContainerColor: Color,
    collapsePx: () -> Float,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val coverSource = rememberStableAlbumHeroCoverSource(album, coverSessionKey)
    val imageModel = rememberAlbumCoverImageModel(coverSource)
    val clearRibbonShape = rememberAlbumLandscapeArtworkRibbonCoreShape(edgeInset = 10.dp)

    val ribbonHeight = (AlbumLandscapeArtworkTopPadding + artworkSize * 0.92f)
        .coerceAtLeast(340.dp)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ribbonHeight)
        ) {
            // 先对裁剪后的副本做模糊，让模糊只从曲线边缘向外扩散。
            AsmrAsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                alpha = if (colorScheme.isDark) 0.30f else 0.22f,
                peekAnySizeForInitial = true,
                loadAtOriginalSize = true,
                fadeInMillis = AlbumDetailHeroIntroDurationMs,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(
                        radius = 18.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
                    .clip(AlbumLandscapeArtworkRibbonShape),
                placeholder = { _ -> },
                loading = { _ -> },
                empty = { _ -> }
            )

            // 清晰副本覆盖在模糊副本中央，保留封面内容细节。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(clearRibbonShape)
            ) {
                AsmrAsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    alpha = if (colorScheme.isDark) 0.30f else 0.22f,
                    peekAnySizeForInitial = true,
                    loadAtOriginalSize = true,
                    fadeInMillis = AlbumDetailHeroIntroDurationMs,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = { _ -> },
                    loading = { _ -> },
                    empty = { _ -> }
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to pageContainerColor.copy(alpha = 0.36f),
                                    0.16f to Color.Transparent,
                                    0.72f to pageContainerColor.copy(alpha = 0.16f),
                                    1f to pageContainerColor.copy(alpha = 0.70f)
                                )
                            )
                        )
                )
            }
        }

        AlbumLandscapeSpectrum(
            lineColor = colorScheme.primaryStrong,
            modifier = Modifier
                .fillMaxWidth()
                .height(94.dp)
                .offset(y = albumLandscapeSpectrumOffsetY(artworkSize))
                .graphicsLayer {
                    translationY = albumLandscapeSpectrumTranslationY(collapsePx())
                }
        )
    }
}

@Composable
private fun AlbumDetailLandscapeArtworkCover(
    album: Album,
    coverSessionKey: String,
    introSessionKey: String,
    animateIntro: Boolean,
    artworkSize: Dp,
    showCoverLoadingState: Boolean,
    collapsePx: () -> Float,
    collapseMaxPx: Float,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val coverSource = rememberStableAlbumHeroCoverSource(album, coverSessionKey)
    val imageModel = rememberAlbumCoverImageModel(coverSource)
    val heroIntroProgress = remember(introSessionKey) {
        Animatable(if (animateIntro) 0f else 1f)
    }
    var coverPainterAlphaState by remember(imageModel, coverSessionKey) {
        mutableStateOf<State<Float>?>(null)
    }
    var shouldRenderCoverShadow by remember(imageModel, coverSessionKey) {
        mutableStateOf(coverSource.isBlank())
    }
    LaunchedEffect(coverSource, coverPainterAlphaState) {
        if (coverSource.isBlank()) {
            shouldRenderCoverShadow = true
            return@LaunchedEffect
        }
        shouldRenderCoverShadow = false
        val painterAlphaState = coverPainterAlphaState ?: return@LaunchedEffect
        snapshotFlow { painterAlphaState.value }
            .first { imageAlpha ->
                imageAlpha > AlbumLandscapeCoverShadowStartAlpha
            }
        // 至少让已经开始渐入的图片先完整绘制一帧，再把阴影节点插入组合树。
        withFrameNanos { }
        shouldRenderCoverShadow = true
    }
    LaunchedEffect(introSessionKey, animateIntro) {
        if (!animateIntro) {
            heroIntroProgress.snapTo(1f)
            return@LaunchedEffect
        }
        heroIntroProgress.snapTo(0f)
        withFrameNanos { }
        heroIntroProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = AlbumDetailHeroIntroDurationMs,
                easing = FastOutSlowInEasing
            )
        )
    }

    val artworkShape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier.graphicsLayer {
            alpha = heroIntroProgress.value.coerceIn(0f, 1f)
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
    ) {
        Box(
            modifier = Modifier
                .offset(
                    x = AlbumLandscapeArtworkStartPadding,
                    y = AlbumLandscapeArtworkTopPadding
                )
                .size(artworkSize)
                .graphicsLayer {
                    val currentCollapsePx = collapsePx()
                    val collapseProgress = albumLandscapeCollapseProgress(
                        collapsePx = currentCollapsePx,
                        collapseMaxPx = collapseMaxPx
                    )
                    val scale = albumLandscapeCoverScale(
                        collapsePx = currentCollapsePx,
                        collapseMaxPx = collapseMaxPx
                    )
                    scaleX = scale
                    scaleY = scale
                    translationX = AlbumLandscapeCollapsedArtworkShiftX.toPx() * collapseProgress
                    translationY = AlbumLandscapeCollapsedArtworkShiftY.toPx() * collapseProgress
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .graphicsLayer {
                    val intro = heroIntroProgress.value.coerceIn(0f, 1f)
                    val scale = 0.96f + intro * 0.04f
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                }
        ) {
            if (shouldRenderCoverShadow) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val imageAlpha = coverPainterAlphaState?.value ?: 1f
                            alpha = albumLandscapeCoverShadowAlpha(imageAlpha)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                        .shadow(
                            elevation = if (colorScheme.isDark) 6.dp else 14.dp,
                            shape = artworkShape,
                            clip = false
                        )
                        .background(colorScheme.surfaceVariant, artworkShape)
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(artworkShape)
            ) {
                AsmrAsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    placeholderCornerRadius = 26,
                    peekAnySizeForInitial = true,
                    loadAtOriginalSize = true,
                    fadeInMillis = AlbumDetailHeroIntroDurationMs,
                    onBitmapPainterState = { painter, alphaState ->
                        coverPainterAlphaState = if (painter != null) alphaState else null
                    },
                    modifier = Modifier.fillMaxSize(),
                    placeholder = { m -> DiscPlaceholder(modifier = m, cornerRadius = 26) },
                    // 加载阶段保持封面槽为空，避免占位底色被误认为先出现的黑框。
                    loading = { _ -> },
                    empty = { m ->
                        if (showCoverLoadingState) {
                            AsmrImageLoadingPlaceholder(
                                modifier = m,
                                cornerRadius = 26,
                                indicatorSize = 34.dp
                            )
                        } else {
                            DiscPlaceholder(modifier = m, cornerRadius = 26)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AlbumDetailLandscapeIdentity(
    album: Album,
    introSessionKey: String,
    animateIntro: Boolean,
    pageContainerColor: Color,
    listenTogetherRjListenerCount: Int?,
    messageManager: MessageManager,
    onMetaLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val identity = rememberStableAlbumHeroIdentity(album, introSessionKey)
    val copyMeta = rememberAlbumMetaCopyAction(messageManager)
    val introProgress = remember(introSessionKey) {
        Animatable(if (animateIntro) 0f else 1f)
    }
    LaunchedEffect(introSessionKey, animateIntro) {
        if (!animateIntro) {
            introProgress.snapTo(1f)
            return@LaunchedEffect
        }
        introProgress.snapTo(0f)
        withFrameNanos { }
        introProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = AlbumDetailHeroIntroDurationMs,
                easing = FastOutSlowInEasing
            )
        )
    }

    val titleShadow = Shadow(
        color = if (colorScheme.isDark) {
            Color.Black.copy(alpha = 0.48f)
        } else {
            pageContainerColor.copy(alpha = 0.92f)
        },
        offset = Offset(0f, 2f),
        blurRadius = 11f
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = introProgress.value.coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .padding(horizontal = AlbumDetailHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = identity.title,
            modifier = Modifier.clickable { copyMeta("标题", identity.title) },
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                shadow = titleShadow
            ),
            color = colorScheme.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumHeroPrimaryMetaLightweight(
                rjCode = identity.rj,
                circle = identity.circle,
                emphasized = true,
                modifier = Modifier.weight(1f),
                rjOnClick = { copyMeta("作品编号", identity.rj) },
                circleOnClick = { copyMeta("社团", identity.circle) },
                circleOnLongClick = { onMetaLongClick(identity.circle) }
            )
            AlbumOnlineListenerInfo(
                listenerCount = listenTogetherRjListenerCount,
                visible = identity.rj.isNotBlank(),
                emphasized = true,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun AlbumDetailLandscapeSimilarWorksPane(
    seedRjCode: String,
    isRouteReady: Boolean,
    onOpenAlbumByRj: (String, DlsiteRecommendedWork?) -> Unit,
    viewModel: AlbumDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val colorScheme = AsmrTheme.colorScheme
    val state by viewModel.similarWorksState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(seedRjCode, isRouteReady, viewModel) {
        if (isRouteReady) viewModel.ensureSimilarWorksLoaded(seedRjCode)
    }
    DisposableEffect(seedRjCode, viewModel) {
        onDispose(viewModel::cancelSimilarWorksLoad)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "相似作品",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.textPrimary
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                (!isRouteReady && state.works.isEmpty()) ||
                    (state.isLoading && state.works.isEmpty()) -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(22.dp),
                        color = colorScheme.primaryStrong,
                        strokeWidth = 2.dp
                    )
                }

                state.works.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (state.failed) "相似作品加载失败" else "暂时没有相似作品推荐",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        if (state.failed) {
                            TextButton(onClick = {
                                viewModel.ensureSimilarWorksLoaded(seedRjCode, force = true)
                            }) {
                                Text("重试")
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = LocalBottomOverlayPadding.current + 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = state.works,
                            key = AlbumDetailSimilarWork::rjCode
                        ) { work ->
                            AlbumDetailSimilarWorkCard(
                                work = work,
                                onClick = {
                                    onOpenAlbumByRj(
                                        work.rjCode,
                                        DlsiteRecommendedWork(
                                            rjCode = work.rjCode,
                                            title = work.title,
                                            coverUrl = work.coverUrl
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailSimilarWorkCard(
    work: AlbumDetailSimilarWork,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val imageModel = rememberAlbumCoverImageModel(work.coverUrl)
    val containerColor = colorScheme.surface.copy(
        alpha = if (colorScheme.isDark) 0.72f else 0.84f
    ).compositeOver(colorScheme.background)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .background(containerColor)
            .border(
                width = 0.5.dp,
                color = colorScheme.primaryStrong.copy(
                    alpha = if (colorScheme.isDark) 0.22f else 0.14f
                ),
                shape = shape
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsmrAsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholderCornerRadius = 0,
            peekAnySizeForInitial = true,
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = work.title,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = work.cv.ifBlank { work.rjCode },
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AlbumDetailHeroBackground(
    album: Album,
    coverSessionKey: String,
    introSessionKey: String,
    animateIntro: Boolean,
    height: Dp,
    pageContainerColor: Color,
    listenTogetherRjListenerCount: Int?,
    showCoverLoadingState: Boolean,
    messageManager: MessageManager,
    onMetaLongClick: (String) -> Unit,
    blurLayerCache: AlbumHeroBlurLayerCache,
    modifier: Modifier = Modifier,
    collapsePx: () -> Float = { 0f },
    collapseMaxPx: Float = 0f,
    visualOvershootPx: () -> Float = { 0f },
    visualOvershootMaxPx: Float = 1f
) {
    val coverSource = rememberStableAlbumHeroCoverSource(album, coverSessionKey)
    val imageModel = rememberAlbumCoverImageModel(coverSource)
    var blurSource by remember(imageModel) {
        mutableStateOf<AlbumHeroBlurSource?>(null)
    }
    val density = LocalDensity.current
    val fullHeightPx = with(density) { height.toPx() }
    val heroIntroProgress = remember(introSessionKey) {
        Animatable(if (animateIntro) 0f else 1f)
    }
    LaunchedEffect(introSessionKey, animateIntro) {
        if (!animateIntro) {
            heroIntroProgress.snapTo(1f)
            return@LaunchedEffect
        }
        heroIntroProgress.snapTo(0f)
        withFrameNanos { }
        heroIntroProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = AlbumDetailHeroIntroDurationMs,
                easing = FastOutSlowInEasing
            )
        )
    }
    val blurRadiusPx = with(density) {
        AlbumDetailHeroBlurRadius.toPx().coerceAtMost(AlbumDetailHeroBlurRadiusMaxPx)
    }
    val blurRampHeightPx = with(density) {
        AlbumDetailHeroBlurRampHeight.toPx().coerceAtMost(fullHeightPx * 0.52f)
    }
    // Gaussian blur 在可见渐变上方只需要保留完整的 3σ 采样范围。把透明区域也放进
    // 离屏 RenderNode 会让 GPU 每帧处理整张 hero，虽然那些像素最终都会被蒙版丢弃。
    val blurLayerHeightPx = (
        blurRampHeightPx + blurRadiusPx * AlbumDetailHeroBlurSampleMarginMultiplier
        ).coerceAtMost(fullHeightPx)
    val blurLayerHeight = with(density) { blurLayerHeightPx.toDp() }
    val blurRenderEffect = remember(blurRadiusPx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffect
                .createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        } else {
            null
        }
    }
    val legacyBlurModifier = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Modifier.blur(AlbumDetailHeroBlurRadius)
    } else {
        Modifier
    }

    // hero 的可见高度跟随手势变化，但内部始终按完整高度测量。这样封面、毛玻璃和文字不必逐帧
    // 重测；底部元素只通过图层位移跟随折叠，模糊缓存也能在滚动期间持续复用。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .layout { measurable, constraints ->
                val measuredFullHeight = fullHeightPx
                    .roundToInt()
                    .coerceAtLeast(1)
                    .coerceIn(constraints.minHeight, constraints.maxHeight)
                val collapse = collapsePx().coerceIn(0f, collapseMaxPx)
                val targetHeight = (measuredFullHeight - collapse)
                    .coerceAtLeast(1f)
                    .roundToInt()
                val placeable = measurable.measure(
                    constraints.copy(
                        minHeight = measuredFullHeight,
                        maxHeight = measuredFullHeight
                    )
                )
                layout(placeable.width, targetHeight) {
                    placeable.place(0, 0)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .consumeTapThrough()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val intro = heroIntroProgress.value.coerceIn(0f, 1f)
                    val introScale = AlbumDetailHeroIntroStartScale -
                        (AlbumDetailHeroIntroStartScale - 1f) * intro
                    val overshootProgress = (
                        -visualOvershootPx() / visualOvershootMaxPx.coerceAtLeast(1f)
                        ).coerceIn(0f, 1f)
                    val scale = introScale * (
                        1f + overshootProgress * AlbumDetailHeroExpandOvershootScale
                        )
                    alpha = intro
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        ) {
            AsmrAsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                placeholderCornerRadius = 0,
                peekAnySizeForInitial = true,
                loadAtOriginalSize = true,
                onBitmapPainterState = { painter, alpha ->
                    blurSource = painter?.let { AlbumHeroBlurSource(it, alpha) }
                },
                modifier = Modifier.fillMaxSize(),
                placeholder = { m -> DiscPlaceholder(modifier = m, cornerRadius = 0) },
                loading = { m ->
                    AsmrImageLoadingPlaceholder(modifier = m, cornerRadius = 0, indicatorSize = 36.dp)
                },
                empty = { m ->
                    if (showCoverLoadingState) {
                        AsmrImageLoadingPlaceholder(modifier = m, cornerRadius = 0, indicatorSize = 36.dp)
                    } else {
                        DiscPlaceholder(modifier = m, cornerRadius = 0)
                    }
                },
            )
            // 渐进式毛玻璃：从标题区域开始叠加模糊副本，让标题和元信息下方仍保留封面纹理。
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(blurLayerHeight)
                    .graphicsLayer {
                        translationY = -collapsePx().coerceIn(0f, collapseMaxPx)
                    }
                    .clipToBounds()
                    .then(legacyBlurModifier)
                    .drawWithCache {
                        val rampStartY = (size.height - blurRampHeightPx).coerceAtLeast(0f)
                        val stops = (0..6).map { i ->
                            val t = i / 6f
                            val eased = t * t * (3f - 2f * t)
                            t to Color.White.copy(alpha = 0.18f + eased * 0.82f)
                        }.toTypedArray()
                        val mask = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                *stops,
                                1f to Color.White
                            ),
                            startY = rampStartY,
                            endY = size.height
                        )
                        val layerSize = IntSize(
                            width = size.width.roundToInt().coerceAtLeast(1),
                            height = size.height.roundToInt().coerceAtLeast(1)
                        )
                        val fullHeroSize = Size(size.width, fullHeightPx)
                        val fullHeroIntSize = IntSize(
                            width = fullHeroSize.width.roundToInt().coerceAtLeast(1),
                            height = fullHeroSize.height.roundToInt().coerceAtLeast(1)
                        )
                        val sliceTop = (fullHeightPx - size.height).coerceAtLeast(0f)
                        val source = blurSource
                        if (source == null) {
                            onDrawBehind {
                                if (
                                    blurLayerCache.matches(
                                        contentKey = imageModel,
                                        layerSize = layerSize,
                                        fullHeroSize = fullHeroIntSize
                                    )
                                ) {
                                    blurLayerCache.layer.alpha = 1f
                                    drawLayer(blurLayerCache.layer)
                                }
                            }
                        } else {
                            val intrinsicSize = source.painter.intrinsicSize
                            if (intrinsicSize.width <= 0f || intrinsicSize.height <= 0f) {
                                onDrawBehind {}
                            } else {
                                val scaleFactor = ContentScale.Crop.computeScaleFactor(
                                    srcSize = intrinsicSize,
                                    dstSize = fullHeroSize
                                )
                                val scaledSize = Size(
                                    width = intrinsicSize.width * scaleFactor.scaleX,
                                    height = intrinsicSize.height * scaleFactor.scaleY
                                )
                                val alignedOffset = Alignment.TopCenter.align(
                                    size = IntSize(
                                        width = scaledSize.width.roundToInt(),
                                        height = scaledSize.height.roundToInt()
                                    ),
                                    space = fullHeroIntSize,
                                    layoutDirection = layoutDirection
                                )
                                if (
                                    !blurLayerCache.matches(
                                        contentKey = imageModel,
                                        layerSize = layerSize,
                                        fullHeroSize = fullHeroIntSize
                                    )
                                ) {
                                    blurLayerCache.layer.renderEffect = blurRenderEffect
                                    blurLayerCache.layer.compositingStrategy =
                                        LayerCompositingStrategy.Offscreen
                                    blurLayerCache.layer.record(
                                        density = this,
                                        layoutDirection = layoutDirection,
                                        size = layerSize
                                    ) {
                                        translate(
                                            left = alignedOffset.x.toFloat(),
                                            top = alignedOffset.y.toFloat() - sliceTop
                                        ) {
                                            with(source.painter) {
                                                // 毛玻璃内容只录制一次；淡入 alpha 在合成属性上更新，
                                                // 避免每帧重做大面积高斯模糊。
                                                draw(size = scaledSize)
                                            }
                                        }
                                        drawRect(brush = mask, blendMode = BlendMode.DstIn)
                                    }
                                    blurLayerCache.markRecorded(
                                        contentKey = imageModel,
                                        layerSize = layerSize,
                                        fullHeroSize = fullHeroIntSize
                                    )
                                }
                                onDrawBehind {
                                    blurLayerCache.layer.alpha = source.alpha.value
                                    drawLayer(blurLayerCache.layer)
                                }
                            }
                        }
                    }
            )
        }
        // 顶部深色蒙版，保证返回按钮等控件的可读性
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val topMask = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.44f),
                            0.42f to Color.Black.copy(alpha = 0.16f),
                            0.70f to Color.Transparent
                        )
                    )
                    onDrawBehind {
                        drawRect(
                            brush = topMask,
                            alpha = heroIntroProgress.value.coerceIn(0f, 1f)
                        )
                    }
                }
        )
        // 只在封面容器内部做底缘融色，让封面边缘轻轻透出页面背景。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(AlbumDetailHeroTransitionHeight * 1.7f)
                .graphicsLayer {
                    translationY = -collapsePx().coerceIn(0f, collapseMaxPx)
                }
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.28f to pageContainerColor.copy(alpha = 0.08f),
                            0.52f to pageContainerColor.copy(alpha = 0.30f),
                            0.74f to pageContainerColor.copy(alpha = 0.70f),
                            0.88f to pageContainerColor,
                            1f to pageContainerColor
                        )
                    )
                )
        )
        AlbumHeroIdentityOverlay(
            album = album,
            introSessionKey = introSessionKey,
            listenTogetherRjListenerCount = listenTogetherRjListenerCount,
            messageManager = messageManager,
            onMetaLongClick = onMetaLongClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .graphicsLayer {
                    translationY = -collapsePx().coerceIn(0f, collapseMaxPx)
                }
        )
    }
}

@Composable
private fun AlbumHeroIdentityOverlay(
    album: Album,
    introSessionKey: String,
    listenTogetherRjListenerCount: Int?,
    messageManager: MessageManager,
    onMetaLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val copyMeta = rememberAlbumMetaCopyAction(messageManager)
    val identity = rememberStableAlbumHeroIdentity(album, introSessionKey)
    val rj = identity.rj
    val circle = identity.circle
    val showMetaRow = rj.isNotBlank() || circle.isNotBlank() ||
        (listenTogetherRjListenerCount != null && rj.isNotBlank())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = AlbumDetailHorizontalPadding,
                end = AlbumDetailHorizontalPadding,
                bottom = 4.dp
            ),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            text = identity.title,
            modifier = Modifier.clickable { copyMeta("标题", identity.title) },
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                shadow = Shadow(
                    color = if (colorScheme.isDark) Color.White.copy(alpha = 0.58f) else Color.Black.copy(alpha = 0.58f),
                    offset = Offset(0f, 2f),
                    blurRadius = 8f
                )
            ),
            color = if (colorScheme.isDark) Color.White else Color.Black,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        if (showMetaRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumHeroPrimaryMetaLightweight(
                    rjCode = rj,
                    circle = circle,
                    modifier = Modifier.weight(1f),
                    rjOnClick = { copyMeta("作品编号", rj) },
                    circleOnClick = { copyMeta("社团", circle) },
                    circleOnLongClick = { onMetaLongClick(circle) },
                )
                AlbumOnlineListenerInfo(
                    listenerCount = listenTogetherRjListenerCount,
                    visible = rj.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AlbumOnlineListenerInfo(
    listenerCount: Int?,
    visible: Boolean,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier
) {
    val count = listenerCount?.coerceAtLeast(0)
    val colorScheme = AsmrTheme.colorScheme
    val contentColor = if (colorScheme.isDark) {
        Color.White.copy(alpha = 0.96f)
    } else {
        colorScheme.textPrimary.copy(alpha = 0.90f)
    }
    val textShadow = Shadow(
        color = if (colorScheme.isDark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.58f),
        offset = Offset(0f, 1f),
        blurRadius = 5f
    )

    AnimatedVisibility(
        visible = visible && count != null,
        enter = fadeIn(animationSpec = AlbumHeaderEnterTweenSpec) + expandHorizontally(
            animationSpec = AlbumHeaderExpandTweenSpec,
            expandFrom = Alignment.Start
        ),
        exit = fadeOut(animationSpec = tween(durationMillis = 120)) + shrinkHorizontally(
            animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing),
            shrinkTowards = Alignment.Start
        )
    ) {
        if (count != null) {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = com.asmr.player.R.drawable.ic_users_round),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(if (emphasized) 14.dp else 12.dp)
                )
                Text(
                    text = "$count 人正在听",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (emphasized) 12.sp else MaterialTheme.typography.labelSmall.fontSize,
                        shadow = textShadow
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal data class StableAlbumHeroIdentity(
    val title: String,
    val rj: String,
    val circle: String
)

internal fun resolveStableAlbumHeroIdentity(
    stable: StableAlbumHeroIdentity,
    current: StableAlbumHeroIdentity
): StableAlbumHeroIdentity {
    val stableTitleIsPlaceholder = stable.title.isBlank() ||
        stable.title == "专辑" ||
        stable.title.equals(stable.rj, ignoreCase = true)
    val currentTitleIsResolved = current.title.isNotBlank() &&
        current.title != "专辑" &&
        !current.title.equals(current.rj, ignoreCase = true)
    return StableAlbumHeroIdentity(
        title = if (stableTitleIsPlaceholder && currentTitleIsResolved) current.title else stable.title,
        rj = stable.rj.ifBlank { current.rj },
        circle = stable.circle.ifBlank { current.circle }
    )
}

@Composable
private fun rememberStableAlbumHeroIdentity(album: Album, identitySessionKey: String): StableAlbumHeroIdentity {
    val current = StableAlbumHeroIdentity(
        title = album.title.trim().ifBlank { "专辑" },
        rj = album.rjCode.ifBlank { album.workId }.trim(),
        circle = album.circle.trim()
    )
    var stable by remember(identitySessionKey) { mutableStateOf(current) }
    LaunchedEffect(current) {
        stable = resolveStableAlbumHeroIdentity(stable, current)
    }
    return stable
}

@Composable
private fun rememberStableAlbumHeroCoverSource(album: Album, coverSessionKey: String): String {
    val currentLocal = album.coverPath.trim()
    val current = currentLocal.ifEmpty { album.coverUrl.trim() }
    var stable by remember(coverSessionKey) { mutableStateOf(current) }
    LaunchedEffect(currentLocal, current) {
        stable = resolveStableAlbumHeroCoverSource(
            stable = stable,
            currentLocal = currentLocal,
            current = current
        )
    }
    return stable
}

@Composable
private fun rememberAlbumCoverImageModel(data: String): Any {
    return remember(data) {
        val headers = if (data.startsWith("http", ignoreCase = true)) {
            DlsiteAntiHotlink.headersForImageUrl(data)
        } else {
            emptyMap()
        }
        if (headers.isEmpty()) {
            data
        } else {
            CacheImageModel(data = data, headers = headers, keyTag = "dlsite")
        }
    }
}

private fun Modifier.albumDetailScrolledContentFade(
    fadeStartY: Dp,
    fadeEndY: Dp,
    fadeColor: Color
): Modifier {
    return drawWithCache {
            val fadeStartPx = fadeStartY.toPx().coerceAtLeast(0f)
            val fadeEndPx = fadeEndY.toPx().coerceAtLeast(fadeStartPx + 1f)
            val rampStart = (fadeStartPx / fadeEndPx).coerceIn(0f, 1f)
            val rampSpan = (1f - rampStart).coerceAtLeast(0.0001f)
            fun stopAt(t: Float): Pair<Float, Color> {
                val eased = t * t * (3f - 2f * t)
                return (rampStart + rampSpan * t) to fadeColor.copy(alpha = 1f - eased)
            }
            val fadeBrush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to fadeColor,
                    rampStart to fadeColor,
                    stopAt(0.2f),
                    stopAt(0.4f),
                    stopAt(0.6f),
                    stopAt(0.8f),
                    1f to Color.Transparent
                ),
                startY = 0f,
                endY = fadeEndPx
            )
            // 页面背景是纯色，用缓存的覆盖渐变即可得到同样的溶解效果；不再为整块长列表
            // 建立离屏缓冲区，也不在每个滚动帧重新创建 Brush 和色标数组。
            onDrawWithContent {
                drawContent()
                drawRect(
                    brush = fadeBrush,
                    size = androidx.compose.ui.geometry.Size(size.width, fadeEndPx)
                )
            }
        }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AlbumHeader(
    album: Album,
    dlsiteUrl: String,
    asmrOneUrl: String,
    dlsiteEditions: List<DlsiteLanguageEdition>,
    dlsiteSelectedLang: String,
    onDlsiteLangSelected: (String) -> Unit,
    showSaveAction: Boolean,
    onDownloadClick: () -> Unit,
    showDlsitePlayLossless: Boolean,
    onLosslessDownloadClick: () -> Unit,
    onSaveClick: () -> Unit,
    downloadEnabled: Boolean,
    losslessDownloadEnabled: Boolean,
    saveEnabled: Boolean,
    showGroupButton: Boolean,
    onOpenGroupPicker: (albumId: Long) -> Unit,
    introSessionKey: String,
    animateIntro: Boolean,
    availableWidth: Dp,
    messageManager: MessageManager,
    onMetaLongClick: (String) -> Unit,
    landscapeFloatingActions: Boolean = false
) {
    val copyMeta = rememberAlbumMetaCopyAction(messageManager)

    val headerAnimationScopeKey = remember(introSessionKey) { "albumHeader:$introSessionKey" }

    // 首帧已有的信息直接显示；网络到达后新增的声优与标签分别向下滑入并展开，
    // 避免把整列元信息一次性替换而造成突跳。
    val cvPresentInitially = remember(headerAnimationScopeKey) { album.cv.isNotBlank() }
    val tagsPresentInitially = remember(headerAnimationScopeKey) { album.tags.isNotEmpty() }
    val headerContainerModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = AlbumDetailHorizontalPadding)
    Column(
        modifier = headerContainerModifier.padding(
            top = 4.dp,
            bottom = if (landscapeFloatingActions) 2.dp else 12.dp
        )
        // 不用 spacedBy 控制信息行之间的间距：cv/tags 行在网络数据到达后会以 0 高度组合、再通过
        // AnimatedVisibility 纵向展开，而 spacedBy 的固定间距会在"0 高度的折叠内容刚组合"的那一帧
        // 立即出现，把下方按钮行瞬间下推一截，造成展开前的下沉抖动。改为把行间距/与按钮行的间距作为
        // 每个信息行自身的底部 padding 放进 reveal 内部——这样间距属于被 expandVertically 裁剪的高度，
        // 会随展开动画一起从 0 平滑增长，按钮行始终被平滑下移而非瞬间跳变。
    ) {
        val metaRevealKey = headerAnimationScopeKey + ":meta"
        AlbumHeaderLateMetaReveal(
            revealKey = "$metaRevealKey:cv",
            hasContent = album.cv.isNotBlank(),
            presentInitially = cvPresentInitially,
            delayMillis = AlbumDetailCvRevealDelayMs,
            animationsEnabled = animateIntro
        ) {
            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                AlbumHeaderCvLightweight(
                    cvText = album.cv,
                    emphasized = landscapeFloatingActions,
                    onCvClick = { cv -> copyMeta("声优", cv) },
                    onCvLongClick = onMetaLongClick
                )
            }
        }
        AlbumHeaderLateMetaReveal(
            revealKey = "$metaRevealKey:tags",
            hasContent = album.tags.isNotEmpty(),
            presentInitially = tagsPresentInitially,
            delayMillis = AlbumDetailTagsRevealDelayMs,
            animationsEnabled = animateIntro
        ) {
            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                AlbumHeaderTagsLightweight(
                    tags = album.tags,
                    emphasized = landscapeFloatingActions,
                    onTagClick = { tag -> copyMeta("标签", tag) },
                    onTagLongClick = onMetaLongClick
                )
            }
        }

        AlbumHeaderActionBar(
            groupState = when {
                showDlsitePlayLossless -> AlbumHeaderButtonGroupState.Lossless
                showSaveAction -> AlbumHeaderButtonGroupState.Save
                else -> AlbumHeaderButtonGroupState.DownloadOnly
            },
            onDownloadClick = onDownloadClick,
            onSaveClick = onSaveClick,
            onLosslessDownloadClick = onLosslessDownloadClick,
            downloadEnabled = downloadEnabled,
            saveEnabled = saveEnabled,
            losslessDownloadEnabled = losslessDownloadEnabled,
            showGroupAction = showGroupButton,
            groupEnabled = album.id > 0L,
            onGroupClick = {
                val id = album.id
                if (id > 0L) onOpenGroupPicker(id)
            },
            dlsiteEditions = dlsiteEditions,
            dlsiteSelectedLang = dlsiteSelectedLang,
            onDlsiteLangSelected = onDlsiteLangSelected,
            dlsiteUrl = dlsiteUrl,
            asmrOneUrl = asmrOneUrl,
            availableWidth = availableWidth,
            floating = landscapeFloatingActions,
        )
    }
}

@Composable
private fun AlbumHeaderActionBar(
    groupState: AlbumHeaderButtonGroupState,
    onDownloadClick: () -> Unit,
    onSaveClick: () -> Unit,
    onLosslessDownloadClick: () -> Unit,
    downloadEnabled: Boolean,
    saveEnabled: Boolean,
    losslessDownloadEnabled: Boolean,
    showGroupAction: Boolean,
    groupEnabled: Boolean,
    onGroupClick: () -> Unit,
    dlsiteEditions: List<DlsiteLanguageEdition>,
    dlsiteSelectedLang: String,
    onDlsiteLangSelected: (String) -> Unit,
    dlsiteUrl: String,
    asmrOneUrl: String,
    availableWidth: Dp,
    floating: Boolean = false,
) {
    val context = LocalContext.current
    val colorScheme = AsmrTheme.colorScheme
    val compact = availableWidth < 400.dp
    val shape = RoundedCornerShape(15.dp)
    val containerColor = if (colorScheme.isDark) {
        colorScheme.surfaceVariant.copy(alpha = 0.58f)
    } else {
        colorScheme.surface.copy(alpha = 0.88f)
    }
    val borderColor = colorScheme.onSurfaceVariant.copy(
        alpha = if (colorScheme.isDark) 0.18f else 0.12f
    )
    val langCandidates = remember(dlsiteEditions) {
        dlsiteEditions
            .filter { it.lang in setOf("JPN", "CHI_HANS", "CHI_HANT") }
            .distinctBy { it.lang }
            .sortedWith(compareBy({ it.displayOrder }, { it.lang }))
    }
    val selectedLangLabel = remember(dlsiteSelectedLang, langCandidates) {
        langCandidates.firstOrNull { it.lang.equals(dlsiteSelectedLang, ignoreCase = true) }
            ?.let { dlsiteLanguageButtonLabel(it.lang) }
            ?: dlsiteLanguageButtonLabel(dlsiteSelectedLang)
    }
    var languageMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val secondaryAction = when (groupState) {
        AlbumHeaderButtonGroupState.Save -> Triple("保存", Icons.Rounded.Bookmark, saveEnabled)
        AlbumHeaderButtonGroupState.Lossless -> Triple("无损下载", Icons.Rounded.LibraryMusic, losslessDownloadEnabled)
        AlbumHeaderButtonGroupState.DownloadOnly -> null
    }
    val hasSecondaryAction = secondaryAction != null
    val downloadShape = if (hasSecondaryAction) {
        RoundedCornerShape(topStart = 11.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 11.dp)
    } else {
        RoundedCornerShape(11.dp)
    }
    val secondaryShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 11.dp,
        bottomEnd = 11.dp,
        bottomStart = 0.dp,
    )

    if (floating) {
        val floatingShape = RoundedCornerShape(10.dp)
        val floatingInnerStartShape = if (hasSecondaryAction) {
            RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
        } else {
            RoundedCornerShape(8.dp)
        }
        val floatingInnerEndShape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
        val floatingSegmentShape = RoundedCornerShape(6.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(min = 160.dp, max = 220.dp)
                    .fillMaxHeight(),
                shape = floatingShape,
                color = containerColor,
                contentColor = colorScheme.textPrimary,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumHeaderBarAction(
                        label = "下载",
                        icon = Icons.Rounded.Download,
                        showLabel = true,
                        enabled = downloadEnabled,
                        style = AlbumHeaderActionStyle.Primary,
                        shape = floatingInnerStartShape,
                        onClick = onDownloadClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    secondaryAction?.let { (label, icon, enabled) ->
                        AlbumHeaderBarAction(
                            label = label,
                            icon = icon,
                            showLabel = true,
                            enabled = enabled,
                            style = AlbumHeaderActionStyle.Secondary,
                            shape = floatingInnerEndShape,
                            onClick = when (groupState) {
                                AlbumHeaderButtonGroupState.Save -> onSaveClick
                                AlbumHeaderButtonGroupState.Lossless -> onLosslessDownloadClick
                                AlbumHeaderButtonGroupState.DownloadOnly -> ({})
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .fillMaxHeight(),
                shape = floatingShape,
                color = containerColor,
                contentColor = colorScheme.textPrimary,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showGroupAction) {
                        AlbumHeaderBarAction(
                            label = "分组",
                            icon = Icons.Rounded.CreateNewFolder,
                            showLabel = true,
                            enabled = groupEnabled,
                            onClick = onGroupClick,
                            shape = floatingSegmentShape,
                            modifier = Modifier
                                .widthIn(min = 82.dp)
                                .fillMaxHeight(),
                        )
                    }

                    if (langCandidates.isNotEmpty()) {
                        if (showGroupAction) {
                            VerticalDivider(
                                modifier = Modifier.height(16.dp),
                                thickness = 0.5.dp,
                                color = borderColor,
                            )
                        }
                        val languageSelectable = langCandidates.size > 1
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                        ) {
                            AlbumHeaderBarAction(
                                label = selectedLangLabel,
                                icon = Icons.Rounded.Translate,
                                showLabel = true,
                                enabled = languageSelectable,
                                onClick = { languageMenuExpanded = true },
                                shape = floatingSegmentShape,
                                modifier = Modifier
                                    .widthIn(min = 88.dp)
                                    .fillMaxHeight(),
                            )
                            AlbumHeaderLanguageDropdownMenu(
                                expanded = languageMenuExpanded,
                                candidates = langCandidates,
                                selectedLang = dlsiteSelectedLang,
                                onDismiss = { languageMenuExpanded = false },
                                onSelect = { lang ->
                                    languageMenuExpanded = false
                                    onDlsiteLangSelected(lang)
                                }
                            )
                        }
                    }

                    if (showGroupAction || langCandidates.isNotEmpty()) {
                        VerticalDivider(
                            modifier = Modifier.height(16.dp),
                            thickness = 0.5.dp,
                            color = borderColor,
                        )
                    }
                    AlbumHeaderBarAction(
                        label = "DLsite",
                        showLabel = true,
                        enabled = dlsiteUrl.isNotBlank(),
                        onClick = {
                            if (dlsiteUrl.isNotBlank()) {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(dlsiteUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        shape = floatingSegmentShape,
                        modifier = Modifier
                            .widthIn(min = 78.dp)
                            .fillMaxHeight(),
                    )

                    VerticalDivider(
                        modifier = Modifier.height(16.dp),
                        thickness = 0.5.dp,
                        color = borderColor,
                    )
                    AlbumHeaderBarAction(
                        label = "ONE",
                        showLabel = true,
                        enabled = asmrOneUrl.isNotBlank(),
                        onClick = {
                            if (asmrOneUrl.isNotBlank()) {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(asmrOneUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        shape = floatingSegmentShape,
                        modifier = Modifier
                            .widthIn(min = 66.dp)
                            .fillMaxHeight(),
                    )
                }
            }
        }
        return
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = shape,
        color = containerColor,
        contentColor = colorScheme.textPrimary,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumHeaderBarAction(
                    label = "下载",
                    icon = Icons.Rounded.Download,
                    showLabel = true,
                    enabled = downloadEnabled,
                    style = AlbumHeaderActionStyle.Primary,
                    shape = downloadShape,
                    onClick = onDownloadClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )

                secondaryAction?.let { (label, icon, enabled) ->
                    AlbumHeaderBarAction(
                        label = label,
                        icon = icon,
                        showLabel = true,
                        enabled = enabled,
                        style = AlbumHeaderActionStyle.Secondary,
                        shape = secondaryShape,
                        onClick = when (groupState) {
                            AlbumHeaderButtonGroupState.Save -> onSaveClick
                            AlbumHeaderButtonGroupState.Lossless -> onLosslessDownloadClick
                            AlbumHeaderButtonGroupState.DownloadOnly -> ({})
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }

            VerticalDivider(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .height(20.dp),
                thickness = 0.5.dp,
                color = borderColor,
            )

            if (showGroupAction) {
                AlbumHeaderBarAction(
                    label = "分组",
                    icon = Icons.Rounded.CreateNewFolder,
                    showLabel = true,
                    enabled = groupEnabled,
                    onClick = onGroupClick,
                    modifier = Modifier
                        .width(70.dp)
                        .fillMaxHeight(),
                )
            }

            if (langCandidates.isNotEmpty()) {
                val languageSelectable = langCandidates.size > 1
                Box(
                    modifier = Modifier
                        .width(if (compact) 60.dp else 88.dp)
                        .fillMaxHeight()
                ) {
                    AlbumHeaderBarAction(
                        label = selectedLangLabel,
                        icon = Icons.Rounded.Translate,
                        showLabel = true,
                        enabled = languageSelectable,
                        onClick = { languageMenuExpanded = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                    AlbumHeaderLanguageDropdownMenu(
                        expanded = languageMenuExpanded,
                        candidates = langCandidates,
                        selectedLang = dlsiteSelectedLang,
                        onDismiss = { languageMenuExpanded = false },
                        onSelect = { lang ->
                            languageMenuExpanded = false
                            onDlsiteLangSelected(lang)
                        }
                    )
                }
            }

            listOf(
                Triple("DLsite", dlsiteUrl, 64.dp),
                Triple("ONE", asmrOneUrl, if (compact) 44.dp else 56.dp),
            ).forEach { (label, url, width) ->
                AlbumHeaderBarAction(
                    label = label,
                    showLabel = true,
                    enabled = url.isNotBlank(),
                    onClick = {
                        if (url.isNotBlank()) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier
                        .width(width)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

private enum class AlbumHeaderActionStyle {
    Standard,
    Primary,
    Secondary,
}

@Composable
private fun AlbumHeaderBarAction(
    label: String,
    showLabel: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: AlbumHeaderActionStyle = AlbumHeaderActionStyle.Standard,
    shape: RoundedCornerShape = RoundedCornerShape(11.dp),
) {
    val colorScheme = AsmrTheme.colorScheme
    val targetContentColor = when {
        !enabled -> colorScheme.textTertiary.copy(alpha = 0.72f)
        style == AlbumHeaderActionStyle.Primary -> colorScheme.onPrimary
        else -> colorScheme.primaryStrong
    }
    val targetContainerColor = when {
        !enabled -> Color.Transparent
        style == AlbumHeaderActionStyle.Primary -> colorScheme.primaryStrong
        style == AlbumHeaderActionStyle.Secondary -> colorScheme.primary.copy(
            alpha = if (colorScheme.isDark) 0.28f else 0.15f
        )
        else -> Color.Transparent
    }
    // 网络数据到达后，下载/保存按钮从禁用态切到可用态时用约 800ms 渐入，避免状态瞬间“刷新”出来。
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = tween(durationMillis = AlbumHeaderActionStateTransitionMillis),
        label = "albumHeaderBarActionContent"
    )
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = AlbumHeaderActionStateTransitionMillis),
        label = "albumHeaderBarActionContainer"
    )

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .clip(shape)
                .background(containerColor, shape)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = if (showLabel && icon != null) 7.dp else 5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (showLabel) null else label,
                    tint = contentColor,
                    modifier = Modifier.size(17.dp),
                )
            }
            if (showLabel) {
                if (icon != null) Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (style == AlbumHeaderActionStyle.Standard) {
                            FontWeight.Medium
                        } else {
                            FontWeight.SemiBold
                        },
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun dlsiteLanguageButtonLabel(lang: String): String {
    return when (lang.trim().uppercase()) {
        "CHI_HANS" -> "简中"
        "CHI_HANT" -> "繁中"
        "JPN" -> "日语"
        else -> lang.trim().ifBlank { "日语" }
    }
}

@Composable
private fun AlbumHeaderLanguageDropdownMenu(
    expanded: Boolean,
    candidates: List<DlsiteLanguageEdition>,
    selectedLang: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val menuContainer = if (colorScheme.isDark) {
        colorScheme.surfaceVariant.copy(alpha = 0.98f)
    } else {
        colorScheme.surface.copy(alpha = 0.98f)
    }
    MaterialTheme(
        colorScheme = materialColorScheme.copy(
            surface = menuContainer,
            onSurface = colorScheme.textPrimary,
            surfaceVariant = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.22f else 0.12f),
            onSurfaceVariant = colorScheme.textSecondary
        )
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier
                .background(menuContainer, RoundedCornerShape(14.dp))
                .border(
                    width = 0.5.dp,
                    color = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.28f else 0.20f),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            candidates.forEachIndexed { index, edition ->
                val selected = edition.lang.equals(selectedLang, ignoreCase = true)
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        thickness = 0.5.dp,
                        color = colorScheme.onSurfaceVariant.copy(alpha = if (colorScheme.isDark) 0.22f else 0.16f)
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = dlsiteLanguageButtonLabel(edition.lang),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) colorScheme.primary else colorScheme.textPrimary,
                            maxLines = 1
                        )
                    },
                    onClick = { onSelect(edition.lang) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (selected) colorScheme.primary else colorScheme.textSecondary
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(width = 22.dp, height = 6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.46f else 0.30f))
                            )
                        }
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = colorScheme.textPrimary,
                        leadingIconColor = colorScheme.textSecondary,
                        trailingIconColor = colorScheme.primary,
                        disabledTextColor = colorScheme.textTertiary,
                        disabledLeadingIconColor = colorScheme.textTertiary,
                        disabledTrailingIconColor = colorScheme.textTertiary
                    )
                )
            }
        }
    }
}

@Composable
private fun AlbumHeaderLateMetaReveal(
    revealKey: String,
    hasContent: Boolean,
    presentInitially: Boolean,
    delayMillis: Int,
    animationsEnabled: Boolean,
    content: @Composable () -> Unit
) {
    val shouldAnimate = shouldAnimateAlbumHeaderMetaReveal(
        presentInitially = presentInitially,
        hasContent = hasContent,
        animationsEnabled = animationsEnabled
    )
    var visible by remember(revealKey) {
        mutableStateOf(presentInitially && hasContent)
    }
    LaunchedEffect(revealKey, hasContent, shouldAnimate) {
        when {
            !hasContent -> visible = false
            !shouldAnimate -> visible = true
            else -> {
                visible = false
                if (delayMillis > 0) delay(delayMillis.toLong())
                withFrameNanos { }
                visible = true
            }
        }
    }
    AnimatedVisibility(
        visible = visible && hasContent,
        enter = fadeIn(animationSpec = AlbumHeaderEnterTweenSpec) + expandVertically(
            animationSpec = AlbumHeaderExpandTweenSpec,
            expandFrom = Alignment.Top
        ) + slideInVertically(
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            initialOffsetY = { fullHeight -> -(fullHeight * 0.55f).roundToInt() }
        ),
        exit = fadeOut(animationSpec = tween(durationMillis = 120)) + shrinkVertically(
            animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing),
            shrinkTowards = Alignment.Top
        )
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
internal fun isVideoPreviewUrl(url: String): Boolean {
    val u = url.substringBefore('#').substringBefore('?').lowercase()
    return u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".webm") || u.endsWith(".m3u8")
}

internal data class PlaylistAddTarget(
    val mediaId: String,
    val uri: String,
    val title: String,
    val artist: String,
    val artworkUri: String,
    val albumTitle: String = "",
    val albumId: Long = 0L,
    val trackId: Long = 0L,
    val rjCode: String = "",
    val albumWorkId: String = "",
    val trackGroup: String = "",
    val lyricsRelativePathNoExt: String = "",
    val remoteSubtitleSources: List<RemoteSubtitleSource> = emptyList(),
    val mimeType: String? = null,
    val isVideo: Boolean = false
) {
    fun toMediaItem(): MediaItem {
        return MediaItemFactory.fromDetails(
            mediaId = mediaId,
            uri = uri,
            title = title,
            artist = artist,
            albumTitle = albumTitle,
            artworkUri = artworkUri,
            albumId = albumId,
            trackId = trackId,
            rjCode = rjCode,
            albumWorkId = albumWorkId,
            trackGroup = trackGroup,
            lyricsRelativePathNoExt = lyricsRelativePathNoExt,
            remoteSubtitleSources = remoteSubtitleSources,
            mimeType = mimeType,
            isVideo = isVideo
        )
    }

    companion object {
        fun fromTrack(album: Album, track: Track): PlaylistAddTarget {
            val rj = album.rjCode.ifBlank { album.workId }
            val artist = albumArtistLabel(album).ifBlank { rj }
            val artwork = albumArtworkLabel(album)
            val title = track.title.ifBlank { track.path.substringAfterLast('/').substringAfterLast('\\') }
            return PlaylistAddTarget(
                mediaId = track.path,
                uri = track.path,
                title = title,
                artist = artist.orEmpty(),
                artworkUri = artwork,
                albumTitle = album.title,
                albumId = album.id,
                trackId = track.id,
                rjCode = rj,
                albumWorkId = album.workId,
                trackGroup = track.group,
                lyricsRelativePathNoExt = deriveLyricsRelativePathNoExt(track.path, album.getAllLocalPaths())
            )
        }

        fun fromVideo(
            album: Album,
            title: String,
            uriOrPath: String
        ): PlaylistAddTarget? {
            val trimmed = uriOrPath.trim()
            if (trimmed.isBlank()) return null
            return PlaylistAddTarget(
                mediaId = trimmed,
                uri = trimmed,
                title = title.ifBlank { trimmed.substringAfterLast('/').substringAfterLast('\\') },
                artist = albumArtistLabel(album),
                artworkUri = albumArtworkLabel(album),
                albumTitle = album.title,
                albumId = album.id,
                rjCode = album.rjCode.ifBlank { album.workId },
                albumWorkId = album.workId,
                mimeType = MediaItemFactory.guessMimeType(trimmed),
                isVideo = true
            )
        }

        fun fromAsmrOne(album: Album, tree: List<AsmrOneTrackNodeResponse>, relativePath: String): PlaylistAddTarget? {
            val leaf = flattenAsmrOneTracksForUi(tree).firstOrNull { it.relativePath == relativePath } ?: return null
            return fromTrack(album, leaf.toTrack())
        }
    }
}
