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
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.asmr.player.ui.common.AsmrShimmerPlaceholder
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.ImagePreviewDialog
import com.asmr.player.ui.common.ImagePreviewRequest
import com.asmr.player.ui.common.collapsibleHeaderUiState
import com.asmr.player.ui.common.consumeTapThrough
import com.asmr.player.ui.common.rememberCollapsibleHeaderState
import com.asmr.player.ui.playlists.PlaylistPickerScreen
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
private val AlbumDetailScrolledContentFadeSpan = 10.dp
private const val AlbumDetailInitialIntroDurationMs = 1200L
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
private const val AlbumDetailHeroTitleRevealDelayMs = 120
private const val AlbumDetailHeroMetaRevealDelayMs = 280
private const val AlbumDetailCvRevealDelayMs = 220
private const val AlbumDetailTagsRevealDelayMs = 360
private const val AlbumDetailActionsRevealDelayMs = 500
private const val AlbumDetailHeaderMotionSettleMs = 520L
internal val AlbumDetailHorizontalPadding = 8.dp

private val AlbumDetailHeroBounceBackSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow
)

private val AlbumHeaderEnterTweenSpec = tween<Float>(
    durationMillis = 320,
    easing = FastOutLinearInEasing
)

private val AlbumHeaderExpandTweenSpec = tween<IntSize>(
    durationMillis = 320,
    easing = FastOutLinearInEasing
)

private val AlbumHeaderActionMorphSpec = tween<Float>(
    durationMillis = 280,
    easing = FastOutSlowInEasing
)

private val DlsiteSectionResizeTweenSpec = tween<IntSize>(
    durationMillis = 280,
    easing = FastOutSlowInEasing
)

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
    deferMetaRevealExpected: Boolean,
    presentInitially: Boolean
): Boolean {
    return deferMetaRevealExpected || !presentInitially
}

internal data class AlbumDetailOnlineLoadPlan(
    val loadDlsite: Boolean = false,
    val loadAsmrOne: Boolean = false,
    val loadDlsitePlay: Boolean = false
)

internal fun albumDetailOnlineLoadPlan(
    selectedTab: Int,
    hasResolvedInitialDlsiteTarget: Boolean
): AlbumDetailOnlineLoadPlan {
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
    refreshToken: Long = 0L,
    onConsumeRefreshToken: (() -> Unit)? = null,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit = { _, _ -> },
    onAddToQueue: (Album, Track) -> Boolean = { _, _ -> false },
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit = {},
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit = {},
    onOpenPlaylistPicker: (MediaItem) -> Unit = {},
    onOpenGroupPicker: (albumId: Long) -> Unit = { _ -> },
    onOpenDlsiteLogin: () -> Unit = {},
    onOpenAlbumByRj: (String) -> Unit = {},
    initialTab: Int? = null,
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
    var initialIntroSettled by remember(screenKey) { mutableStateOf(false) }
    var showAsmrDownloadDialog by remember { mutableStateOf(false) }
    var showOnlineSaveDialog by remember { mutableStateOf(false) }
    var pendingOnlineSaveSelection by remember { mutableStateOf<Set<String>?>(null) }
    var batchPlaylistItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var downloadSource by remember { mutableStateOf(OnlineDownloadSource.AsmrOne) }

    LaunchedEffect(albumId, rjCode) {
        viewModel.loadAlbum(albumId, rjCode, force = false)
    }
    LaunchedEffect(refreshToken) {
        if (refreshToken == 0L) return@LaunchedEffect
        viewModel.loadAlbum(albumId, rjCode, force = true)
        onConsumeRefreshToken?.invoke()
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
                    LaunchedEffect(screenKey, initialIntroSettled) {
                        if (initialIntroSettled) return@LaunchedEffect
                        delay(AlbumDetailInitialIntroDurationMs)
                        initialIntroSettled = true
                    }
                    val model = state.model
                    val album = model.displayAlbum
                    val asmrOneTree = model.asmrOneTree
                    val trialDownloadTree = remember(model.dlsiteTrialTracks) {
                        buildDlsiteTrialDownloadTree(model.dlsiteTrialTracks)
                    }
                    val shouldPlayInitialAnimations = !initialIntroSettled
                    val shouldAnimateHeaderIntro = true
                    val availableTags by viewModel.availableTags.collectAsState()
                    val userTagsByTrackId by viewModel.userTagsByTrackId.collectAsState()
                    val libraryViewModel: LibraryViewModel = hiltViewModel()
                    var showTagManager by remember { mutableStateOf(false) }
                    var tagManageTrack by remember { mutableStateOf<Track?>(null) }
                    var localPreviewFile by remember { mutableStateOf<LocalTreeUiEntry.File?>(null) }
                    var onlinePreviewFile by remember { mutableStateOf<AsmrTreeUiEntry.File?>(null) }
                    var imagePreviewRequest by remember { mutableStateOf<ImagePreviewRequest?>(null) }
                    // tab 标签栏已移除，但各二级页面组件仍需要一个折叠头状态用于嵌套滚动协调。
                    // 由于不再渲染 chrome，其 heightPx 始终为 0，相关调用均为安全空操作。
                    val tabChromeState = rememberCollapsibleHeaderState()

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
                        val heroCollapseAnim = remember { Animatable(0f) }
                        val heroVisualOvershootAnim = remember { Animatable(0f) }
                        val heroCollapsePx = heroCollapseAnim.value.coerceIn(0f, heroCollapseMaxPx)
                        val heroVisualOvershootPx = heroVisualOvershootAnim.value
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(heroCollapseMaxPx, heroVisualOvershootMaxPx) {
                            heroCollapseAnim.snapTo(
                                heroCollapseAnim.value.coerceIn(0f, heroCollapseMaxPx)
                            )
                            heroVisualOvershootAnim.snapTo(
                                heroVisualOvershootAnim.value.coerceIn(
                                    -heroVisualOvershootMaxPx,
                                    0f
                                )
                            )
                        }
                        val heroNestedScroll = remember(heroCollapseMaxPx, heroVisualOvershootMaxPx, scope) {
                            object : NestedScrollConnection {
                                private fun settleVisualOvershoot(initialVelocity: Float = 0f): Boolean {
                                    if (abs(heroVisualOvershootAnim.value) < 0.5f) return false
                                    scope.launch {
                                        heroVisualOvershootAnim.animateTo(
                                            0f,
                                            animationSpec = AlbumDetailHeroBounceBackSpec,
                                            initialVelocity = initialVelocity
                                        )
                                    }
                                    return true
                                }

                                private fun dragOvershootDelta(delta: Float): Float {
                                    val progress = (-heroVisualOvershootAnim.value / heroVisualOvershootMaxPx)
                                        .coerceIn(0f, 1f)
                                    val resistance = AlbumDetailHeroOvershootResistance * (1f - progress * progress * 0.62f)
                                    return delta * resistance
                                }

                                private fun applyCollapseDelta(delta: Float): Float {
                                    if (delta == 0f) return 0f
                                    scope.launch { heroCollapseAnim.stop() }
                                    scope.launch { heroVisualOvershootAnim.stop() }
                                    val current = heroCollapseAnim.value.coerceIn(0f, heroCollapseMaxPx)
                                    var remaining = delta
                                    var consumed = 0f

                                    if (remaining > 0f && heroVisualOvershootAnim.value < 0f) {
                                        val visualRelease = (remaining * AlbumDetailHeroOvershootReleaseMultiplier)
                                            .coerceAtMost(-heroVisualOvershootAnim.value)
                                        if (visualRelease > 0f) {
                                            scope.launch {
                                                heroVisualOvershootAnim.snapTo(heroVisualOvershootAnim.value + visualRelease)
                                            }
                                            remaining -= visualRelease / AlbumDetailHeroOvershootReleaseMultiplier
                                            consumed += visualRelease / AlbumDetailHeroOvershootReleaseMultiplier
                                        }
                                    }

                                    if (remaining != 0f) {
                                        val collapseTarget = (current + remaining).coerceIn(0f, heroCollapseMaxPx)
                                        val collapseApplied = collapseTarget - current
                                        if (collapseApplied != 0f) {
                                            scope.launch { heroCollapseAnim.snapTo(collapseTarget) }
                                            remaining -= collapseApplied
                                            consumed += collapseApplied
                                        }
                                    }

                                    if (remaining < 0f) {
                                        val visualDelta = dragOvershootDelta(remaining)
                                        val visualTarget = (heroVisualOvershootAnim.value + visualDelta)
                                            .coerceIn(-heroVisualOvershootMaxPx, 0f)
                                        scope.launch { heroVisualOvershootAnim.snapTo(visualTarget) }
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
                                    scope.launch {
                                        heroVisualOvershootAnim.stop()
                                        if (target < heroVisualOvershootAnim.value) {
                                            heroVisualOvershootAnim.animateTo(
                                                target,
                                                animationSpec = tween(
                                                    durationMillis = AlbumDetailHeroFlingApproachMillis,
                                                    easing = FastOutSlowInEasing
                                                )
                                            )
                                        }
                                        heroVisualOvershootAnim.animateTo(
                                            0f,
                                            animationSpec = tween(
                                                durationMillis = AlbumDetailHeroFlingSettleMillis,
                                                easing = FastOutSlowInEasing
                                            )
                                        )
                                    }
                                    return true
                                }

                                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                    val dy = available.y
                                    // 向上浏览（手指上滑，dy<0）：先把滚动用于折叠 hero，再交给列表。
                                    if (dy < 0f && (
                                            heroCollapseAnim.value < heroCollapseMaxPx ||
                                                heroVisualOvershootAnim.value < 0f
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
                                            heroCollapseAnim.value > 0f ||
                                                heroVisualOvershootAnim.value > -heroVisualOvershootMaxPx
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
                                    if (available.y > 0f && heroCollapseAnim.value <= 0.5f) {
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
                                canSaveOnline = canUseAsmrOneTreeActions,
                                onDownloadClick = {
                                    downloadSource = if (tab == 2) {
                                        OnlineDownloadSource.DlsitePlay
                                    } else {
                                        OnlineDownloadSource.AsmrOne
                                    }
                                    showAsmrDownloadDialog = true
                                },
                                showDlsitePlayLossless = tab == 2 && resolvedInitialTarget,
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
                                onOpenGroupPicker = onOpenGroupPicker,
                                introSessionKey = introSessionKey,
                                animateIntro = shouldAnimateHeaderIntro,
                                deferMetaRevealExpected = !isLocalTab,
                                messageManager = viewModel.messageManager
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
                                collapsePx = { heroCollapsePx },
                                collapseMaxPx = heroCollapseMaxPx,
                                visualOvershootPx = { heroVisualOvershootPx },
                                visualOvershootMaxPx = heroVisualOvershootMaxPx,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )

                            LaunchedEffect(
                                selectedTab,
                                model.rjCode,
                                model.dlsiteWorkno,
                                model.hasResolvedInitialDlsiteTarget
                            ) {
                                val loadPlan = albumDetailOnlineLoadPlan(
                                    selectedTab = selectedTab,
                                    hasResolvedInitialDlsiteTarget = model.hasResolvedInitialDlsiteTarget
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

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(contentViewportHeight + heroHeight * 0.5f)
                                    .offset {
                                        IntOffset(0, (contentViewportTopPx - heroCollapsePx).roundToInt())
                                    }
                                    .nestedScroll(heroNestedScroll)
                                    .clipToBounds()
                                    .albumDetailScrolledContentFade(
                                        fadeStartY = contentFadeStartY,
                                        fadeEndY = contentFadeEndY
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
                                                chromeState = tabChromeState,
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
                                        treeStateKey = "tree:asmrOne:${model.rjCode.trim().uppercase()}",
                                        initialCurrentPath = viewModel.getTreeCurrentPath("tree:asmrOne:${model.rjCode.trim().uppercase()}"),
                                        topContentPadding = 0.dp,
                                        chromeState = tabChromeState,
                                        animateIntro = shouldPlayInitialAnimations,
                                        onPersistCurrentPath = { path ->
                                            val rj = model.rjCode.trim().uppercase()
                                            viewModel.persistTreeCurrentPath("tree:asmrOne:$rj", path)
                                        },
                                        initialScroll = viewModel.getListScrollPosition("scroll:tree:asmrOne:${model.rjCode.trim().uppercase()}"),
                                        onPersistScroll = { index, offset ->
                                            viewModel.persistListScrollPosition("scroll:tree:asmrOne:${model.rjCode.trim().uppercase()}", index, offset)
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
                                        chromeState = tabChromeState,
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

                val track = tagManageTrack
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
    modifier: Modifier = Modifier,
    collapsePx: () -> Float = { 0f },
    collapseMaxPx: Float = 0f,
    visualOvershootPx: () -> Float = { 0f },
    visualOvershootMaxPx: Float = 1f
) {
    val coverSource = rememberStableAlbumHeroCoverSource(album, coverSessionKey)
    val imageModel = rememberAlbumCoverImageModel(coverSource)
    val density = LocalDensity.current
    val fullHeightPx = with(density) { height.toPx() }
    val visualOvershootScale = run {
        val max = visualOvershootMaxPx.coerceAtLeast(1f)
        val expandProgress = (-visualOvershootPx() / max).coerceIn(0f, 1f)
        1f + expandProgress * AlbumDetailHeroExpandOvershootScale
    }
    val blurModifier = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.graphicsLayer {
                val blurPx = 84.dp.toPx()
                renderEffect = RenderEffect
                    .createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
        } else {
            Modifier.blur(64.dp)
        }
    }

    // 真实“折叠”而非整体缩放：只压缩 hero 的布局高度（宽度保持满宽 -> 不会出现左右空白）。
    // 封面填充折叠后的盒子高度（fillMaxSize + Crop）：盒子变矮时 Crop 的缩放因子随之减小，
    // 宽方向原本被裁掉的左右两侧逐渐显露出来（封面通常较宽，折叠即“横向缩小”露出更多画面）。
    // 折叠过程中保留当前位图、稳定后再按新尺寸重载，避免逐帧重复解码与闪烁。
    // 标题/元信息底部对齐，会随折叠后的底边上移但尺寸保持不变。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val collapse = collapsePx().coerceIn(0f, collapseMaxPx)
                val targetHeight = (fullHeightPx - collapse).coerceAtLeast(1f).roundToInt()
                val placeable = measurable.measure(
                    constraints.copy(minHeight = targetHeight, maxHeight = targetHeight)
                )
                layout(placeable.width, targetHeight) {
                    placeable.place(0, 0)
                }
            }
            .clipToBounds()
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .consumeTapThrough()
        )
        AsmrAsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            placeholderCornerRadius = 0,
            peekAnySizeForInitial = false,
            loadAtOriginalSize = true,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    scaleX = visualOvershootScale
                    scaleY = visualOvershootScale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .drawWithCache {
                    val fadeHeightPx = AlbumDetailHeroBlurRampHeight.toPx()
                        .coerceAtMost(size.height * 0.52f)
                    val fadeStartY = (size.height - fadeHeightPx).coerceAtLeast(0f)
                    val stops = (0..5).map { i ->
                        val t = i / 5f
                        val eased = t * t * (3f - 2f * t)
                        val alpha = 1f - eased * 0.38f
                        t to Color.White.copy(alpha = alpha)
                    }.toTypedArray()
                    val mask = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.White,
                            *stops,
                            1f to Color.Transparent
                        ),
                        startY = fadeStartY,
                        endY = size.height
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = mask, blendMode = BlendMode.DstIn)
                    }
                },
            placeholder = { m -> DiscPlaceholder(modifier = m, cornerRadius = 0) },
            loading = { m -> AsmrShimmerPlaceholder(modifier = m, cornerRadius = 0) },
            empty = { m ->
                if (showCoverLoadingState) {
                    AsmrShimmerPlaceholder(modifier = m, cornerRadius = 0)
                } else {
                    DiscPlaceholder(modifier = m, cornerRadius = 0)
                }
            },
        )
        // 渐进式毛玻璃：从标题区域开始叠加模糊副本，让标题和元信息下方仍保留封面纹理。
        AsmrAsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            placeholderCornerRadius = 0,
            peekAnySizeForInitial = false,
            loadAtOriginalSize = true,
            modifier = Modifier
                .fillMaxSize()
                .then(blurModifier)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    scaleX = visualOvershootScale
                    scaleY = visualOvershootScale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .drawWithCache {
                    val rampHeightPx = AlbumDetailHeroBlurRampHeight.toPx()
                        .coerceAtMost(size.height * 0.52f)
                    val rampStartY = (size.height - rampHeightPx).coerceAtLeast(0f)
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
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = mask, blendMode = BlendMode.DstIn)
                    }
                },
            placeholder = {},
            loading = {},
            empty = {},
        )
        // 顶部深色蒙版，保证返回按钮等控件的可读性
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.44f),
                            0.42f to Color.Black.copy(alpha = 0.16f),
                            0.70f to Color.Transparent
                        )
                    )
                )
        )
        // 只在封面容器内部做底缘融色，让封面边缘轻轻透出页面背景。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(AlbumDetailHeroTransitionHeight * 1.7f)
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
            animateIntro = animateIntro,
            listenTogetherRjListenerCount = listenTogetherRjListenerCount,
            messageManager = messageManager,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun AlbumHeroIdentityOverlay(
    album: Album,
    introSessionKey: String,
    animateIntro: Boolean,
    listenTogetherRjListenerCount: Int?,
    messageManager: MessageManager,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val copyMeta = rememberAlbumMetaCopyAction(messageManager)
    val identity = rememberStableAlbumHeroIdentity(album, introSessionKey)
    val rj = identity.rj
    val circle = identity.circle
    val showMetaRow = rj.isNotBlank() || circle.isNotBlank() ||
        (listenTogetherRjListenerCount != null && rj.isNotBlank())
    val heroRevealKey = remember(introSessionKey) { "albumHero:$introSessionKey" }

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
        AlbumHeaderInfoReveal(
            revealKey = "$heroRevealKey:title",
            delayMillis = AlbumDetailHeroTitleRevealDelayMs,
            enabled = animateIntro,
            expandLayout = false
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
        }

        if (showMetaRow) {
            AlbumHeaderInfoReveal(
                revealKey = "$heroRevealKey:meta",
                delayMillis = AlbumDetailHeroMetaRevealDelayMs,
                enabled = animateIntro,
                expandLayout = false
            ) {
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
                        appearance = AlbumMetaAppearance.OnImage,
                        leadingVisual = AlbumMetaLeadingVisual.Icon,
                    )
                    if (listenTogetherRjListenerCount != null && rj.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val listenerContainer = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.36f else 0.30f)
                        val listenerContent = if (colorScheme.isDark) {
                            Color.White.copy(alpha = 0.96f)
                        } else {
                            colorScheme.textPrimary.copy(alpha = 0.88f)
                        }
                        val listenerBorder = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.52f else 0.42f)
                        Surface(
                            color = listenerContainer,
                            contentColor = listenerContent,
                            shape = RoundedCornerShape(999.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 0.5.dp,
                                color = listenerBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = com.asmr.player.R.drawable.ic_users_round),
                                    contentDescription = null,
                                    tint = listenerContent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${listenTogetherRjListenerCount.coerceAtLeast(0)} 人正在听",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = listenerContent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
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
    fadeEndY: Dp
): Modifier {
    return this
        .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawWithContent {
            drawContent()
            val fadeStartPx = fadeStartY.toPx().coerceAtLeast(0f)
            val fadeEndPx = fadeEndY.toPx().coerceAtLeast(fadeStartPx + 1f)
            val rampStart = (fadeStartPx / fadeEndPx).coerceIn(0f, 1f)
            val rampSpan = (1f - rampStart).coerceAtLeast(0.0001f)
            // 用 smoothstep 缓动的多段渐变替代线性裁切，使内容向上滚入 hero 区域时
            // 平滑自然地溶解消失，而不是生硬地一刀切。
            fun stopAt(t: Float): Pair<Float, Color> {
                val eased = t * t * (3f - 2f * t)
                return (rampStart + rampSpan * t) to Color.White.copy(alpha = eased)
            }
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        rampStart to Color.Transparent,
                        stopAt(0.2f),
                        stopAt(0.4f),
                        stopAt(0.6f),
                        stopAt(0.8f),
                        1f to Color.White
                    ),
                    startY = 0f,
                    endY = fadeEndPx
                ),
                blendMode = BlendMode.DstIn
            )
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
    canSaveOnline: Boolean,
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
    deferMetaRevealExpected: Boolean,
    messageManager: MessageManager
) {
    val context = LocalContext.current
    val colorScheme = AsmrTheme.colorScheme
    val copyMeta = rememberAlbumMetaCopyAction(messageManager)

    val headerAnimationScopeKey = remember(introSessionKey) { "albumHeader:$introSessionKey" }
    var headerIntroPlayed by rememberSaveable(headerAnimationScopeKey) { mutableStateOf(false) }
    LaunchedEffect(headerAnimationScopeKey, animateIntro) {
        if (headerIntroPlayed) return@LaunchedEffect
        if (!animateIntro) {
            headerIntroPlayed = true
            return@LaunchedEffect
        }
        delay(AlbumDetailActionsRevealDelayMs + AlbumDetailHeaderMotionSettleMs)
        headerIntroPlayed = true
    }

    // 记录“首帧时各信息块是否已存在”：本地库专辑进入时 cv/tags 已就绪，应直接淡入不撑开（消除下沉抖动）；
    // 在线专辑即使从列表 hint 拿到了 cv，也仍按延迟元信息处理，保留延迟淡入/展开的进入节奏。
    val cvPresentInitially = remember(headerAnimationScopeKey) { album.cv.isNotBlank() }
    val tagsPresentInitially = remember(headerAnimationScopeKey) { album.tags.isNotEmpty() }
    val cvExpandLayout = shouldExpandAlbumHeaderMetaReveal(deferMetaRevealExpected, cvPresentInitially)
    val tagsExpandLayout = shouldExpandAlbumHeaderMetaReveal(deferMetaRevealExpected, tagsPresentInitially)
    val headerHasDeferredMeta = deferMetaRevealExpected

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
        modifier = dlsiteSectionRevealModifier(
            modifier = headerContainerModifier,
            enabled = animateIntro && !headerIntroPlayed && !headerHasDeferredMeta
        )
            .padding(top = 10.dp, bottom = 12.dp)
        // 不用 spacedBy 控制信息行之间的间距：cv/tags 行在网络数据到达后会以 0 高度组合、再通过
        // AnimatedVisibility 纵向展开，而 spacedBy 的固定间距会在“0 高度的折叠内容刚组合”的那一帧
        // 立即出现，把下方按钮行瞬间下推一截，造成展开前的下沉抖动。改为把行间距/与按钮行的间距作为
        // 每个信息行自身的底部 padding 放进 reveal 内部——这样间距属于被 expandVertically 裁剪的高度，
        // 会随展开动画一起从 0 平滑增长，按钮行始终被平滑下移而非瞬间跳变。
    ) {
                // cv 行与 tags 行同属“信息行”，行间距与行内换行间距（6.dp）保持一致；
                // 末尾信息行携带 8.dp 底部 padding 作为与下方按钮行的间距。
                if (album.cv.isNotBlank()) {
                    AlbumHeaderInfoReveal(
                        revealKey = "$headerAnimationScopeKey:cv",
                        delayMillis = AlbumDetailCvRevealDelayMs,
                        enabled = animateIntro,
                        expandLayout = cvExpandLayout
                    ) {
                        Box(modifier = Modifier.padding(bottom = if (album.tags.isNotEmpty()) 6.dp else 8.dp)) {
                            AlbumCvChipsFlow(
                                cvText = album.cv,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                onCvClick = { cv -> copyMeta("CV", cv) },
                                leadingVisual = AlbumMetaLeadingVisual.Icon,
                            )
                        }
                    }
                }

                if (album.tags.isNotEmpty()) {
                    AlbumHeaderInfoReveal(
                        revealKey = "$headerAnimationScopeKey:tags",
                        delayMillis = AlbumDetailTagsRevealDelayMs,
                        enabled = animateIntro,
                        expandLayout = tagsExpandLayout
                    ) {
                        Box(modifier = Modifier.padding(bottom = 8.dp)) {
                            AlbumTagsFlow(
                                tags = album.tags,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                onTagClick = { tag -> copyMeta("标签", tag) },
                                leadingVisual = AlbumMetaLeadingVisual.Icon,
                            )
                        }
                    }
                }

                AlbumHeaderInfoReveal(
                    revealKey = "$headerAnimationScopeKey:actions",
                    delayMillis = AlbumDetailActionsRevealDelayMs,
                    enabled = animateIntro,
                    expandLayout = false
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        val compact = maxWidth < 400.dp
                        val ultraCompact = maxWidth < 340.dp
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
                                    canSaveOnline -> AlbumHeaderButtonGroupState.Save
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
    var displayedSecondaryState by remember { mutableStateOf(groupState) }
    LaunchedEffect(groupState) {
        if (groupState != AlbumHeaderButtonGroupState.DownloadOnly) {
            displayedSecondaryState = groupState
        }
    }
    val morphProgress by animateFloatAsState(
        targetValue = if (hasSecondaryAction) 1f else 0f,
        animationSpec = AlbumHeaderActionMorphSpec,
        label = "albumHeaderDownloadActionMorph",
        finishedListener = { value ->
            if (value == 0f && groupState == AlbumHeaderButtonGroupState.DownloadOnly) {
                displayedSecondaryState = AlbumHeaderButtonGroupState.DownloadOnly
            }
        }
    )
    val enabledProgress by animateFloatAsState(
        targetValue = if (downloadEnabled) 1f else 0f,
        animationSpec = AlbumHeaderActionMorphSpec,
        label = "albumHeaderDownloadActionEnabled"
    )
    val radius = 10.dp
    val secondaryContentAlpha = ((morphProgress - 0.22f) / 0.78f).coerceIn(0f, 1f)
    val mainEndRadius = radius * (1f - morphProgress)
    val mainShape = RoundedCornerShape(
        topStart = radius,
        bottomStart = radius,
        topEnd = mainEndRadius,
        bottomEnd = mainEndRadius
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
    val primaryContainer = lerp(disabledContainer, colorScheme.primary, enabledProgress)
    val primaryContent = lerp(disabledContent, colorScheme.onPrimary, enabledProgress)
    val secondaryContainer = colorScheme.primary.copy(
        alpha = if (colorScheme.isDark) 0.22f else 0.14f
    )
    val activeSecondaryState = if (hasSecondaryAction) groupState else displayedSecondaryState

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
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(morphProgress.coerceAtLeast(0.001f))
                .graphicsLayer { alpha = morphProgress }
        ) {
            when (activeSecondaryState) {
                AlbumHeaderButtonGroupState.Save -> Button(
                    onClick = onSaveClick,
                    enabled = saveEnabled && secondaryContentAlpha > 0.92f,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = secondaryContentAlpha },
                    shape = secondaryShape,
                    contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = secondaryContainer,
                        contentColor = colorScheme.primary,
                        disabledContainerColor = secondaryContainer,
                        disabledContentColor = colorScheme.primary.copy(alpha = 0.72f)
                    )
                ) {
                    Icon(Icons.Rounded.Bookmark, contentDescription = null, modifier = Modifier.size(iconSize))
                    Spacer(modifier = Modifier.width(iconGap))
                    Text("保存", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }

                AlbumHeaderButtonGroupState.Lossless -> Button(
                    onClick = onLosslessDownloadClick,
                    enabled = losslessDownloadEnabled && secondaryContentAlpha > 0.92f,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = secondaryContentAlpha },
                    shape = secondaryShape,
                    contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = secondaryContainer,
                        contentColor = colorScheme.primary,
                        disabledContainerColor = secondaryContainer,
                        disabledContentColor = colorScheme.primary.copy(alpha = 0.72f)
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

@Composable
private fun AlbumHeaderInfoReveal(
    revealKey: String,
    delayMillis: Int = 0,
    enabled: Boolean = true,
    ready: Boolean = true,
    expandLayout: Boolean = true,
    content: @Composable () -> Unit
) {
    var hasPlayed by rememberSaveable(revealKey) { mutableStateOf(false) }
    LaunchedEffect(revealKey, enabled, ready) {
        if (!enabled && !hasPlayed) {
            hasPlayed = true
        }
    }
    if (!enabled || hasPlayed) {
        content()
        return
    }
    if (!ready) {
        content()
        return
    }
    var visible by remember(revealKey) { mutableStateOf(false) }
    LaunchedEffect(revealKey, ready, enabled) {
        visible = false
        if (delayMillis > 0) {
            delay(delayMillis.toLong())
        }
        withFrameNanos { }
        visible = true
        delay(AlbumDetailRevealSettleMs)
        hasPlayed = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AlbumHeaderEnterTweenSpec,
        label = "albumHeaderInfoAlpha"
    )
    if (!expandLayout) {
        // 进入时就已存在的内容（RJ、cv/tags、按钮行）：只做淡入，不做纵向平移，
        // 避免先超过最终位置再回到目标位置。
        Box(
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha
            }
        ) {
            content()
        }
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
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha
            }
        ) {
            content()
        }
    }
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
