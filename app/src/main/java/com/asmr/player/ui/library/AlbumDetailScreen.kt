package com.asmr.player.ui.library

import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy as LayerCompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import com.asmr.player.ui.common.consumeTapThrough
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.groups.AlbumGroupPickerScreen
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.settings.SettingsViewModel
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.thinScrollbar
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
private const val AlbumDetailRevealSettleMs = 420L
private const val AlbumDetailCvRevealDelayMs = 220
private const val AlbumDetailTagsRevealDelayMs = 360
internal val AlbumDetailHorizontalPadding = 8.dp

private class AlbumDetailIntroState(var settled: Boolean)

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

private val AlbumHeaderActionColorTweenSpec = tween<Float>(
    durationMillis = 280,
    easing = FastOutSlowInEasing
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

internal fun shouldExpandAlbumHeaderMetaReveal(
    presentInitially: Boolean
): Boolean {
    return !presentInitially
}

internal data class AlbumDetailOnlineLoadPlan(
    val loadDlsite: Boolean = false,
    val loadAsmrOne: Boolean = false,
    val loadDlsitePlay: Boolean = false
)

internal fun albumDetailOnlineLoadPlan(
    selectedTab: Int,
    hasResolvedInitialDlsiteTarget: Boolean,
    isInitialRouteReady: Boolean
): AlbumDetailOnlineLoadPlan {
    if (!isInitialRouteReady) return AlbumDetailOnlineLoadPlan()
    return when (selectedTab) {
        1 -> AlbumDetailOnlineLoadPlan(loadDlsite = true, loadAsmrOne = true)
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

internal fun albumHeaderDownloadEnabled(
    selectedTab: Int,
    hasAsmrOneTree: Boolean,
    hasDlsitePlayTree: Boolean,
    hasResolvedInitialDlsiteTarget: Boolean
): Boolean {
    return when (selectedTab) {
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
    val uiState by viewModel.uiState.collectAsState()
    val cloudSyncSelectionDialogState by viewModel.cloudSyncSelectionDialogState.collectAsState()
    val colorScheme = AsmrTheme.colorScheme
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
    var isInitialRouteReady by remember(screenKey) { mutableStateOf(false) }
    val initialIntroState = remember(screenKey) { AlbumDetailIntroState(settled = false) }
    var showAsmrDownloadDialog by remember { mutableStateOf(false) }
    var showOnlineSaveDialog by remember { mutableStateOf(false) }
    var pendingOnlineSaveSelection by remember { mutableStateOf<Set<String>?>(null) }
    var batchPlaylistItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var groupPickerAlbumId by remember { mutableStateOf<Long?>(null) }
    var downloadSource by remember { mutableStateOf(OnlineDownloadSource.AsmrOne) }
    var metaActionKeyword by rememberSaveable { mutableStateOf<String?>(null) }

    fun openMetaActions(value: String) {
        val keyword = value.trim()
        if (keyword.isNotBlank()) metaActionKeyword = keyword
    }

    LaunchedEffect(viewModel) {
        viewModel.setListenTogetherRjSummaryPollingEnabled(true)
    }
    LaunchedEffect(albumId, rjCode) {
        isInitialRouteReady = false
        withFrameNanos { }
        viewModel.loadAlbumAndAwait(albumId, rjCode, force = false)
        isInitialRouteReady = true
    }
    DisposableEffect(screenKey, viewModel) {
        onDispose {
            viewModel.setListenTogetherRjSummaryPollingEnabled(false)
            viewModel.cancelActiveLoads()
        }
    }
    LaunchedEffect(pendingOnlineSaveSelection) {
        val selected = pendingOnlineSaveSelection ?: return@LaunchedEffect
        pendingOnlineSaveSelection = null
        viewModel.saveOnlineSelectedToLibrary(selected)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AsmrTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter // 仅用于平板适配：居中显示内容
    ) {
        val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
        
        Column(
            modifier = if (isCompact) {
                Modifier.fillMaxSize()
            } else {
                // 仅用于平板适配：限制内容区域最大宽度并填充可用空间
                Modifier
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
                        if (initialIntroState.settled) return@LaunchedEffect
                        delay(AlbumDetailInitialIntroDurationMs)
                        // 这只是供之后新到数据判断是否还需入场动画的生命周期标记。
                        // 已经在树上的动画会自行完整收尾，计时结束时无需强制整页重组。
                        initialIntroState.settled = true
                    }
                    val model = state.model
                    val album = model.displayAlbum
                    val asmrOneTree = model.asmrOneTree
                    val trialDownloadTree = remember(model.dlsiteTrialTracks) {
                        buildDlsiteTrialDownloadTree(model.dlsiteTrialTracks)
                    }
                    val shouldPlayInitialAnimations = !initialIntroState.settled
                    val shouldAnimateHeaderIntro = true
                    var showTagManager by remember { mutableStateOf(false) }
                    var tagManageTrack by remember { mutableStateOf<Track?>(null) }
                    var localPreviewFile by remember { mutableStateOf<LocalTreeUiEntry.File?>(null) }
                    var onlinePreviewFile by remember { mutableStateOf<AsmrTreeUiEntry.File?>(null) }
                    var imagePreviewRequest by remember { mutableStateOf<ImagePreviewRequest?>(null) }
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val pageContainerColor = dynamicPageContainerColor(colorScheme)
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
                        val heroHeight = heroPreferredHeight
                            .coerceAtLeast(heroMinHeight)
                            .coerceAtMost(heroHeightLimit.coerceAtLeast(heroMinHeight))
                        val contentViewportTop = heroHeight + AlbumDetailHeroContentGap
                        val contentViewportHeight = (maxHeight - contentViewportTop).coerceAtLeast(0.dp)
                        val contentFadeStartY = 0.dp
                        val contentFadeEndY = AlbumDetailScrolledContentFadeSpan

                        // 随滑动自适应缩放 hero：布局边界仍是 0%~50% 折叠。
                        // 只有展开端允许封面图继续放大，松手后缓慢回落；折叠端到 50% 后直接交给列表滚动。
                        val heroDensity = LocalDensity.current
                        val heroCollapseMaxPx = with(heroDensity) { (heroHeight * 0.5f).toPx() }
                        val heroVisualOvershootMaxPx = with(heroDensity) { (heroHeight * 0.10f).toPx() }
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

                                    if (remaining < 0f) {
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
                            val headerAlbum = headerAlbumForTab(tab)
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
                                dlsiteUrl = model.dlsiteWorkno.takeIf { it.isNotBlank() }?.let { "https://www.dlsite.com/maniax/work/=/product_id/$it.html" }.orEmpty(),
                                asmrOneUrl = model.asmrOneWorkId?.takeIf { it.isNotBlank() }?.let { "https://asmr.one/work/$it" }.orEmpty(),
                                dlsiteEditions = headerDlsiteEditions,
                                dlsiteSelectedLang = model.dlsiteSelectedLang,
                                onDlsiteLangSelected = { viewModel.selectDlsiteLanguage(it) },
                                showSaveAction = tab == 1,
                                onDownloadClick = {
                                    downloadSource = if (tab == 2) {
                                        OnlineDownloadSource.DlsitePlay
                                    } else {
                                        OnlineDownloadSource.AsmrOne
                                    }
                                    showAsmrDownloadDialog = true
                                },
                                showDlsitePlayLossless = tab == 2,
                                onLosslessDownloadClick = {
                                    viewModel.downloadDlsitePlayLosslessArchive()
                                },
                                onSaveClick = {
                                    showOnlineSaveDialog = true
                                },
                                downloadEnabled = albumHeaderDownloadEnabled(
                                    selectedTab = tab,
                                    hasAsmrOneTree = asmrOneTree.isNotEmpty(),
                                    hasDlsitePlayTree = model.dlsitePlayTree.isNotEmpty(),
                                    hasResolvedInitialDlsiteTarget = resolvedInitialTarget
                                ),
                                losslessDownloadEnabled = tab == 2 && resolvedInitialTarget && model.dlsitePlayTree.isNotEmpty(),
                                saveEnabled = canUseAsmrOneTreeActions,
                                showGroupButton = isLocalTab && model.localAlbum != null,
                                onOpenGroupPicker = { id -> groupPickerAlbumId = id },
                                introSessionKey = introSessionKey,
                                animateIntro = shouldAnimateHeaderIntro,
                                availableWidth = (maxWidth - AlbumDetailHorizontalPadding * 2)
                                    .coerceAtLeast(0.dp),
                                messageManager = viewModel.messageManager,
                                onMetaLongClick = ::openMetaActions
                            )
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

                            LaunchedEffect(
                                selectedTab,
                                model.rjCode,
                                model.dlsiteWorkno,
                                model.hasResolvedInitialDlsiteTarget,
                                isInitialRouteReady
                            ) {
                                val loadPlan = albumDetailOnlineLoadPlan(
                                    selectedTab = selectedTab,
                                    hasResolvedInitialDlsiteTarget = model.hasResolvedInitialDlsiteTarget,
                                    isInitialRouteReady = isInitialRouteReady
                                )
                                if (loadPlan.loadDlsite) {
                                    viewModel.ensureDlsiteLoaded()
                                }
                                if (loadPlan.loadAsmrOne) {
                                    viewModel.ensureAsmrOneLoaded()
                                }
                                if (loadPlan.loadDlsitePlay) {
                                    viewModel.ensureDlsitePlayLoaded()
                                }
                            }

                            val asmrOneTreeStableRj = model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()
                            val asmrOneTreeStateKey = "tree:asmrOne:$asmrOneTreeStableRj"
                            val asmrOneScrollStateKey = "scroll:$asmrOneTreeStateKey"

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(contentViewportHeight + heroHeight * 0.5f)
                                    .offset {
                                        IntOffset(
                                            0,
                                            (contentViewportTopPx - heroMotion.collapsePx).roundToInt()
                                        )
                                    }
                                    .nestedScroll(heroNestedScroll)
                                    .clipToBounds()
                                    .background(pageContainerColor)
                                    .albumDetailScrolledContentFade(
                                        fadeStartY = contentFadeStartY,
                                        fadeEndY = contentFadeEndY,
                                        fadeColor = pageContainerColor
                                    )
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
                                                header = { headerContent(0) },
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
                                                onManageTrackTags = { track ->
                                                    tagManageTrack = track
                                                },
                                                onRemoveTrack = { track ->
                                                    if (track.id > 0L) libraryViewModel.removeTrackFromAlbum(track.id)
                                                },
                                                onSetCoverFromImage = { pathOrUri ->
                                                    viewModel.setLocalCoverPath(pathOrUri)
                                                },
                                                onPreviewImages = { request -> imagePreviewRequest = request },
                                                onPreviewFile = { localPreviewFile = it },
                                                onSubtitleGenerationError = viewModel.messageManager::showError,
                                                onSubtitleGenerationUnavailable = viewModel.messageManager::showInfo,
                                                onSubtitleGenerationQueued = viewModel.messageManager::showInfo,
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
                                        header = { headerContent(1) },
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
                                        dlsiteRecommendations = model.dlsiteRecommendations,
                                        onOpenAlbumByRj = onOpenAlbumByRj,
                                        loadRemoteFileSize = { viewModel.loadRemoteFileSize(it) }
                                    )
                                    else -> AlbumDlsitePlayBreadcrumbTabV2(
                                        header = { headerContent(2) },
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
                                        loadRemoteFileSize = { viewModel.loadRemoteFileSize(it) }
                                    )
                                }
                            }
                    }
                }

                val canSaveOnline = canUseAsmrOneOnlineTreeActions(
                    selectedTab = selectedTab,
                    hasAsmrOneTree = asmrOneTree.isNotEmpty()
                )
                if (showAsmrDownloadDialog) {
                    val downloadTree = when (downloadSource) {
                        OnlineDownloadSource.AsmrOne -> asmrOneTree
                        OnlineDownloadSource.DlsitePlay -> model.dlsitePlayTree
                        OnlineDownloadSource.DlsiteTrial -> trialDownloadTree
                    }
                    AsmrOneDownloadDialog(
                        albumTitle = album.title,
                        trackTree = downloadTree,
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
                    val saveTree = if (model.dlsitePlayTree.isNotEmpty()) model.dlsitePlayTree else asmrOneTree
                    OnlineSaveDialog(
                        albumTitle = album.title,
                        trackTree = saveTree,
                        onDismiss = { showOnlineSaveDialog = false },
                        onConfirm = { selected ->
                            pendingOnlineSaveSelection = selected
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
                    val searchBlockedKeywords by settingsViewModel.searchBlockedKeywords.collectAsState()
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
                    val availableTags by viewModel.availableTags.collectAsState()
                    val userTagsByTrackId by viewModel.userTagsByTrackId.collectAsState()
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
                AlbumPrimaryMetaRow(
                    rjCode = rj,
                    circle = circle,
                    modifier = Modifier.weight(1f),
                    rjOnClick = { copyMeta("RJ", rj) },
                    circleOnClick = { copyMeta("社团", circle) },
                    circleOnLongClick = { onMetaLongClick(circle) },
                    appearance = AlbumMetaAppearance.OnImage,
                    leadingVisual = AlbumMetaLeadingVisual.Icon,
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
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "$count 人正在听",
                    style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class StableAlbumHeroIdentity(
    val title: String,
    val rj: String,
    val circle: String
)

@Composable
private fun rememberStableAlbumHeroIdentity(album: Album, identitySessionKey: String): StableAlbumHeroIdentity {
    val current = StableAlbumHeroIdentity(
        title = album.title.trim().ifBlank { "专辑" },
        rj = album.rjCode.ifBlank { album.workId }.trim(),
        circle = album.circle.trim()
    )
    return remember(identitySessionKey) { current }
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
    onMetaLongClick: (String) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = AsmrTheme.colorScheme
    val copyMeta = rememberAlbumMetaCopyAction(messageManager)

    val headerAnimationScopeKey = remember(introSessionKey) { "albumHeader:$introSessionKey" }

    // 记录"首帧时各信息块是否已存在"：本地库专辑进入时 cv/tags 已就绪，应直接显示，不做渐入或撑开；
    // 列表 hint 已经提供的信息首帧直接占住最终高度，只有网络到达后才新增的信息才纵向展开。
    val cvPresentInitially = remember(headerAnimationScopeKey) { album.cv.isNotBlank() }
    val tagsPresentInitially = remember(headerAnimationScopeKey) { album.tags.isNotEmpty() }
    val cvExpandLayout = shouldExpandAlbumHeaderMetaReveal(cvPresentInitially)
    val tagsExpandLayout = shouldExpandAlbumHeaderMetaReveal(tagsPresentInitially)
    val headerContainerModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = AlbumDetailHorizontalPadding)
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

    Column(
        modifier = headerContainerModifier.padding(top = 4.dp, bottom = 12.dp)
        // 不用 spacedBy 控制信息行之间的间距：cv/tags 行在网络数据到达后会以 0 高度组合、再通过
        // AnimatedVisibility 纵向展开，而 spacedBy 的固定间距会在"0 高度的折叠内容刚组合"的那一帧
        // 立即出现，把下方按钮行瞬间下推一截，造成展开前的下沉抖动。改为把行间距/与按钮行的间距作为
        // 每个信息行自身的底部 padding 放进 reveal 内部——这样间距属于被 expandVertically 裁剪的高度，
        // 会随展开动画一起从 0 平滑增长，按钮行始终被平滑下移而非瞬间跳变。
    ) {
        // 使用新的轻量级信息组件替换原有的胶囊样式
        val metaRevealKey = headerAnimationScopeKey + ":meta"
        AlbumHeaderLateMetaReveal(
            revealKey = metaRevealKey,
            delayMillis = AlbumDetailCvRevealDelayMs,
            enabled = animateIntro,
            lateArrival = cvExpandLayout || tagsExpandLayout
        ) {
            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                val labelCv = "声优"
                val labelTag = "标签"
                // 只显示声优和标签，RJ和社团已经在Hero封面底部显示
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 声优
                    if (album.cv.isNotBlank()) {
                        AlbumHeaderCvLightweight(
                            cvText = album.cv,
                            onCvClick = { cv -> copyMeta(labelCv, cv) },
                            onCvLongClick = onMetaLongClick
                        )
                    }

                    // 标签
                    if (album.tags.isNotEmpty()) {
                        AlbumHeaderTagsLightweight(
                            tags = album.tags,
                            onTagClick = { tag -> copyMeta(labelTag, tag) },
                            onTagLongClick = onMetaLongClick
                        )
                    }
                }
            }
        }

        Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                        val compact = availableWidth < 400.dp
                        val ultraCompact = availableWidth < 340.dp
                        val actionGap = if (compact) 8.dp else 10.dp
                        val primaryButtonPadding = when {
                            ultraCompact -> 6.dp
                            compact -> 8.dp
                            else -> 12.dp
                        }
                        val smallButtonPadding = when {
                            ultraCompact -> 6.dp
                            compact -> 8.dp
                            else -> 12.dp
                        }
                        val primaryIconSize = if (compact) 16.dp else 18.dp
                        val primaryIconGap = if (compact) 4.dp else 6.dp
                        val selectorMinWidth = when {
                            ultraCompact -> 68.dp
                            compact -> 76.dp
                            else -> 96.dp
                        }
                        val selectorMaxWidth = when {
                            ultraCompact -> 92.dp
                            compact -> 104.dp
                            else -> 140.dp
                        }
                        val externalMinWidth = when {
                            ultraCompact -> 46.dp
                            compact -> 50.dp
                            else -> 56.dp
                        }
                        val externalMaxWidth = when {
                            ultraCompact -> 58.dp
                            compact -> 64.dp
                            else -> 76.dp
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(actionGap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(36.dp)
                                    .weight(1f)
                            ) {
                                val groupState = when {
                                    showDlsitePlayLossless -> AlbumHeaderButtonGroupState.Lossless
                                    showSaveAction -> AlbumHeaderButtonGroupState.Save
                                    else -> AlbumHeaderButtonGroupState.DownloadOnly
                                }
                                AlbumHeaderDownloadAction(
                                    groupState = groupState,
                                    onDownloadClick = onDownloadClick,
                                    onSaveClick = onSaveClick,
                                    onLosslessDownloadClick = onLosslessDownloadClick,
                                    downloadEnabled = downloadEnabled,
                                    saveEnabled = saveEnabled,
                                    losslessDownloadEnabled = losslessDownloadEnabled,
                                    horizontalPadding = primaryButtonPadding,
                                    iconSize = primaryIconSize,
                                    iconGap = primaryIconGap,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            if (showGroupButton) {
                                OutlinedButton(
                                    onClick = {
                                        val id = album.id
                                        if (id > 0L) onOpenGroupPicker(id)
                                    },
                                    enabled = album.id > 0L,
                                    modifier = Modifier
                                        .height(36.dp)
                                        .widthIn(min = selectorMinWidth, max = selectorMaxWidth),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = smallButtonPadding, vertical = 0.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.3f))
                                ) {
                                    Icon(
                                        Icons.Rounded.CreateNewFolder,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(primaryIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(primaryIconGap))
                                    Text("分组", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary, maxLines = 1)
                                }
                            }

                            if (langCandidates.isNotEmpty()) {
                                val languageSelectable = langCandidates.size > 1
                                val languageContainerColor = colorScheme.primary.copy(
                                    alpha = if (languageSelectable) {
                                        if (colorScheme.isDark) 0.18f else 0.10f
                                    } else {
                                        if (colorScheme.isDark) 0.08f else 0.06f
                                    }
                                )
                                val languageContentColor = if (languageSelectable) {
                                    colorScheme.primary
                                } else {
                                    colorScheme.textSecondary
                                }
                                Box {
                                    OutlinedButton(
                                        onClick = {
                                            if (languageSelectable) languageMenuExpanded = true
                                        },
                                        enabled = languageSelectable,
                                        modifier = Modifier
                                            .height(36.dp)
                                            .widthIn(min = selectorMinWidth, max = selectorMaxWidth),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = smallButtonPadding, vertical = 0.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            colorScheme.primary.copy(alpha = if (languageSelectable) 0.34f else 0.16f)
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = languageContainerColor,
                                            contentColor = languageContentColor,
                                            disabledContainerColor = languageContainerColor,
                                            disabledContentColor = languageContentColor
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Translate,
                                            contentDescription = null,
                                            tint = languageContentColor,
                                            modifier = Modifier.size(primaryIconSize)
                                        )
                                        Spacer(modifier = Modifier.width(primaryIconGap))
                                        Text(
                                            text = selectedLangLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = languageContentColor,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.width(if (compact) 2.dp else 4.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowDropDown,
                                            contentDescription = null,
                                            tint = if (languageSelectable) colorScheme.primary else colorScheme.textTertiary,
                                            modifier = Modifier.size(primaryIconSize)
                                        )
                                    }
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
                                "DLsite" to dlsiteUrl,
                                "ONE" to asmrOneUrl
                            ).forEach { (label, url) ->
                                OutlinedButton(
                                    onClick = {
                                        if (url.isNotBlank()) {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    },
                                    enabled = url.isNotBlank(),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .widthIn(min = externalMinWidth, max = externalMaxWidth),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = smallButtonPadding, vertical = 0.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.3f))
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = colorScheme.primary, maxLines = 1)
                                }
                            }
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
private fun AlbumHeaderDownloadAction(
    groupState: AlbumHeaderButtonGroupState,
    onDownloadClick: () -> Unit,
    onSaveClick: () -> Unit,
    onLosslessDownloadClick: () -> Unit,
    downloadEnabled: Boolean,
    saveEnabled: Boolean,
    losslessDownloadEnabled: Boolean,
    horizontalPadding: Dp,
    iconSize: Dp,
    iconGap: Dp,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val hasSecondaryAction = groupState != AlbumHeaderButtonGroupState.DownloadOnly
    val secondaryActionEnabled = when (groupState) {
        AlbumHeaderButtonGroupState.Save -> saveEnabled
        AlbumHeaderButtonGroupState.Lossless -> losslessDownloadEnabled
        AlbumHeaderButtonGroupState.DownloadOnly -> false
    }
    val downloadColorProgress by animateFloatAsState(
        targetValue = if (downloadEnabled) 1f else 0f,
        animationSpec = AlbumHeaderActionColorTweenSpec,
        label = "albumHeaderDownloadColor"
    )
    val secondaryColorProgress by animateFloatAsState(
        targetValue = if (secondaryActionEnabled) 1f else 0f,
        animationSpec = AlbumHeaderActionColorTweenSpec,
        label = "albumHeaderSecondaryActionColor"
    )
    val radius = 10.dp
    val mainShape = RoundedCornerShape(
        topStart = radius,
        bottomStart = radius,
        topEnd = if (hasSecondaryAction) 0.dp else radius,
        bottomEnd = if (hasSecondaryAction) 0.dp else radius
    )
    val secondaryShape = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = radius,
        bottomEnd = radius
    )
    val disabledContainer = colorScheme.surfaceVariant.copy(
        alpha = if (colorScheme.isDark) 0.54f else 0.74f
    )
    val disabledContent = colorScheme.textTertiary.copy(alpha = if (colorScheme.isDark) 0.72f else 0.86f)
    val primaryContainer = lerp(disabledContainer, colorScheme.primary, downloadColorProgress)
    val primaryContent = lerp(disabledContent, colorScheme.onPrimary, downloadColorProgress)
    val secondaryDisabledContainer = colorScheme.surfaceVariant.copy(
        alpha = if (colorScheme.isDark) 0.42f else 0.62f
    )
    val secondaryActiveContainer = colorScheme.primary.copy(
        alpha = if (colorScheme.isDark) 0.22f else 0.14f
    )
    val secondaryContainer = lerp(
        secondaryDisabledContainer,
        secondaryActiveContainer,
        secondaryColorProgress
    )
    val secondaryContent = lerp(disabledContent, colorScheme.primary, secondaryColorProgress)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onDownloadClick,
            enabled = downloadEnabled,
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
            shape = mainShape,
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryContainer,
                contentColor = primaryContent,
                disabledContainerColor = primaryContainer,
                disabledContentColor = primaryContent
            )
        ) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(iconSize))
            Spacer(modifier = Modifier.width(iconGap))
            Text("下载", style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        if (hasSecondaryAction) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                when (groupState) {
                    AlbumHeaderButtonGroupState.Save -> Button(
                        onClick = onSaveClick,
                        enabled = saveEnabled,
                        modifier = Modifier.fillMaxSize(),
                        shape = secondaryShape,
                        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = secondaryContainer,
                            contentColor = secondaryContent,
                            disabledContainerColor = secondaryContainer,
                            disabledContentColor = secondaryContent
                        )
                    ) {
                        Icon(Icons.Rounded.Bookmark, contentDescription = null, modifier = Modifier.size(iconSize))
                        Spacer(modifier = Modifier.width(iconGap))
                        Text("保存", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }

                    AlbumHeaderButtonGroupState.Lossless -> Button(
                        onClick = onLosslessDownloadClick,
                        enabled = losslessDownloadEnabled,
                        modifier = Modifier.fillMaxSize(),
                        shape = secondaryShape,
                        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = secondaryContainer,
                            contentColor = secondaryContent,
                            disabledContainerColor = secondaryContainer,
                            disabledContentColor = secondaryContent
                        )
                    ) {
                        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(iconSize))
                        Spacer(modifier = Modifier.width(iconGap))
                        Text("无损下载", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }

                    AlbumHeaderButtonGroupState.DownloadOnly -> Unit
                }
            }
        }
    }
}

@Composable
private fun AlbumHeaderLateMetaReveal(
    revealKey: String,
    delayMillis: Int,
    enabled: Boolean,
    lateArrival: Boolean,
    content: @Composable () -> Unit
) {
    if (!lateArrival) {
        content()
        return
    }
    var hasPlayed by rememberSaveable(revealKey) { mutableStateOf(false) }
    LaunchedEffect(revealKey, enabled) {
        if (!enabled && !hasPlayed) {
            hasPlayed = true
        }
    }
    if (!enabled) {
        content()
        return
    }
    var visible by remember(revealKey) { mutableStateOf(false) }
    LaunchedEffect(revealKey, enabled) {
        visible = false
        if (delayMillis > 0) {
            delay(delayMillis.toLong())
        }
        withFrameNanos { }
        visible = true
        delay(AlbumDetailRevealSettleMs)
        hasPlayed = true
    }
    if (hasPlayed) {
        content()
        return
    }
    // 网络数据到达后才出现的内容（在线 cv/tags）：保留原有的纵向展开，
    // 但避免再叠加父级 animateContentSize，减少同一尺寸变化被双重动画驱动。
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = AlbumHeaderEnterTweenSpec) + expandVertically(
            animationSpec = AlbumHeaderExpandTweenSpec,
            expandFrom = Alignment.Top
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
