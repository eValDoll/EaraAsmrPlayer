package com.asmr.player

import com.asmr.player.i18n.appString
import com.asmr.player.R

import com.asmr.player.ui.common.LocalAlbumCoverShadowCache
import com.asmr.player.ui.common.rememberAlbumCoverShadowCache

import com.asmr.player.data.remote.crawler.AlbumResourceSource
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_COLLECTED_SOURCE_KEY
import com.asmr.player.translation.LocalPageTranslationHeader
import com.asmr.player.translation.PageTranslationAction
import com.asmr.player.translation.PageTranslationHeaderAction
import com.asmr.player.translation.PageTranslationHeaderState
import com.asmr.player.translation.PageTranslationHost
import android.view.Choreographer
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.Audiotrack
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.asmr.player.ui.library.AlbumDetailScreen
import com.asmr.player.ui.library.AlbumHeroBlurLayerCache
import com.asmr.player.ui.library.AlbumDetailUiState
import com.asmr.player.ui.library.AlbumDetailViewModel
import com.asmr.player.ui.library.CloudSyncSelectionDialog
import com.asmr.player.ui.library.JapaneseAsmrOnlineDownloadDialog
import com.asmr.player.ui.library.LibraryFilterScreen
import com.asmr.player.ui.library.LibraryScreen
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.ui.library.BulkPhase
import com.asmr.player.data.remote.scraper.resolveRecommendedWorkHeroCoverUrl
import com.asmr.player.performance.UiFrameWorkCoordinator
import com.asmr.player.ui.player.NowPlayingMotionLayout
import com.asmr.player.ui.player.NowPlayingMotionSpec
import com.asmr.player.ui.player.NowPlayingScreen
import com.asmr.player.ui.player.PlayerSharedBackdrop
import com.asmr.player.ui.player.playerSurfaceColors
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.player.rememberCoverDragPreviewState
import com.asmr.player.ui.player.rememberCoverMotionState
import com.asmr.player.ui.downloads.DownloadsScreen
import com.asmr.player.ui.downloads.DownloadsViewModel
import com.asmr.player.ui.downloads.DownloadItemState
import com.asmr.player.ui.dlsite.DlsiteLoginScreen
import com.asmr.player.ui.dlsite.DlsiteLoginViewModel
import com.asmr.player.ui.hotlistening.HotListeningScreen
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import com.asmr.player.ui.hotlistening.HotListeningViewModel
import com.asmr.player.hotlistening.ListeningTracker
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.playlists.PlaylistDetailScreen
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.playlists.PlaylistsScreen
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.playlists.SystemPlaylistScreen
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_CHINESE_TRANSLATED_ONLY_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_COLLECTED_ONLY_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_COLLECTED_SORT_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_HAS_SUBTITLE_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_ALL_AGES_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_LOCALE_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_ORDER_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_PRESALE_ONLY_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_PURCHASED_ONLY_KEY
import com.asmr.player.ui.search.SEARCH_ASSIST_RESULT_SIGNAL_KEY
import com.asmr.player.ui.search.SearchAssistSearchRequest
import com.asmr.player.ui.search.SearchAssistScreen
import com.asmr.player.ui.search.SearchScreen
import com.asmr.player.ui.search.SearchViewModel
import com.asmr.player.domain.model.SearchSource
import com.asmr.player.ui.settings.AppUpdateState
import com.asmr.player.ui.settings.SettingsScreen
import com.asmr.player.ui.settings.SettingsViewModel
import com.asmr.player.ui.settings.UpdateCheckSource
import com.asmr.player.ui.common.FlatActionDialog
import com.asmr.player.ui.common.FlatDialogAction
import com.asmr.player.ui.common.FlatDialogActionTone
import com.asmr.player.ui.common.FlatTextFieldDialog
import com.asmr.player.ui.common.RoundedTopSheet
import com.asmr.player.ui.common.EaraBrand
import com.asmr.player.ui.common.EaraTopBarContainer
import com.asmr.player.ui.common.ProgressiveHeaderBlurState
import com.asmr.player.ui.common.TabletLayout
import com.asmr.player.ui.common.TabletPage
import com.asmr.player.ui.common.LocalTabletLayout
import com.asmr.player.ui.common.rememberIsTabletDevice
import com.asmr.player.ui.nav.TabletNavigationRail
import com.asmr.player.ui.nav.TabletNavigationRailWidth
import com.asmr.player.ui.player.MiniPlayer
import com.asmr.player.ui.common.LocalMainHeaderPadding
import com.asmr.player.performance.tracePrimaryPagePreloadEvent
import com.asmr.player.ui.common.DeferredPageContentState
import com.asmr.player.ui.common.LocalDeferredPageContent
import com.asmr.player.ui.common.LocalPageAnimationsActive
import com.asmr.player.ui.common.LocalAlbumRowRasterCacheAllowed
import com.asmr.player.ui.common.rememberAlbumRowRasterCachePolicy
import com.asmr.player.ui.common.rememberAlbumRowRasterCacheTarget
import com.asmr.player.ui.common.LocalProgressiveHeaderBlur
import com.asmr.player.ui.common.progressiveHeaderContent
import com.asmr.player.ui.common.hasProgressiveMainHeader
import com.asmr.player.ui.common.rememberProgressiveHeaderBlur
import com.asmr.player.ui.common.EaraMainTopBarHeight
import com.asmr.player.ui.common.EaraTopBarIconButton
import com.asmr.player.ui.common.resolveMainPageBackgroundColor
import com.asmr.player.ui.nav.AlbumCoverHintStore
import com.asmr.player.ui.nav.AppNavigator
import com.asmr.player.ui.nav.BottomChrome
import com.asmr.player.ui.nav.BottomChromeNavItem
import com.asmr.player.ui.nav.Routes
import com.asmr.player.ui.nav.bottomChromeNavItems
import com.asmr.player.ui.nav.bottomChromeOverlayHeight
import com.asmr.player.ui.nav.isPrimaryRoute
import com.asmr.player.ui.nav.resolvePrimaryRoute
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import kotlinx.coroutines.launch
import java.net.URLEncoder
import androidx.compose.material.icons.automirrored.rounded.ArrowBack

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.asmr.player.ui.theme.AsmrTheme
import android.os.Build
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

import com.asmr.player.ui.player.MiniPlayerDisplayMode

import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.PlayerBackgroundMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.NowPlayingHomeLayoutMode
import com.asmr.player.data.settings.NowPlayingLyricsSettings
import com.asmr.player.util.MessageManager
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.ui.update.AppUpdateInstallResult
import com.asmr.player.ui.update.launchDownloadedApkInstall
import com.asmr.player.ui.update.openUpdateReleasePage
import com.asmr.player.ui.common.rememberAppVolumeWarningSessionState
import com.asmr.player.ui.common.rememberCurrentAudioOutputRouteKind
import com.asmr.player.ui.common.DismissOutsideBoundsOverlay
import com.asmr.player.service.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

import androidx.compose.foundation.border
import androidx.media3.common.MediaItem
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asmr.player.playback.AppVolume

internal data class PlaylistPickerRequest(
    val items: List<MediaItem>
)

internal data class BatchPlaylistPickerRequest(
    val items: List<MediaItem>
)

private const val SecondaryPageEnterDurationMs = 440
private const val SecondaryPageExitDurationMs = 420
private const val SecondaryPageTouchBlockDurationMs = 320
private const val AlbumDetailPresentedStateKey = "album_detail_presented"
private const val PrimaryPagerSnapThreshold = 0.16f
private const val PrimaryPageSelectionQuietWindowMs = 200L
private val SecondaryPageSlideEasing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
private val PrimaryPageParallaxOffset = 120.dp
private val AlbumDetailTopBarButtonShape = CircleShape
private val AlbumDetailBackTouchPassThroughWidth = 88.dp
private val AlbumDetailTopBarTouchPassThroughHeight = 64.dp

/**
 * 把过渡期的可见区域裁剪交给 RenderNode。边界只改变图层属性，不会让页面内容的
 * display list 每帧重新录制；矩形 outline 同时保留原有的精确裁剪范围。
 */
private class HorizontalRectClipShape(
    private val leftPx: Float,
    private val rightPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val left = leftPx.coerceIn(0f, size.width)
        val right = rightPx.coerceIn(left, size.width)
        return Outline.Rectangle(Rect(left, 0f, right, size.height))
    }
}

private fun NavBackStackEntry.usesSecondaryPageSlideTransition(): Boolean {
    if (isAlbumDetailRoute(destination.route)) return false
    return resolveCurrentPrimaryDestinationRoute(
        currentRoute = destination.route,
        playlistSystemType = arguments?.getString("type")
    ) == null
}

private fun secondaryPageEnterTransition(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = SecondaryPageEnterDurationMs,
            easing = SecondaryPageSlideEasing
        ),
        initialOffsetX = { fullWidth -> fullWidth }
    )
}

private fun secondaryPagePopExitTransition(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = SecondaryPageExitDurationMs,
            easing = SecondaryPageSlideEasing
        ),
        targetOffsetX = { fullWidth -> fullWidth }
    )
}

private fun Modifier.albumDetailTopBarButtonSurface(
    enabled: Boolean,
    shape: Shape = AlbumDetailTopBarButtonShape
): Modifier {
    return if (!enabled) {
        this
    } else {
        this
            .background(Color.Black.copy(alpha = 0.42f), shape)
            .border(0.5.dp, Color.White.copy(alpha = 0.24f), shape)
            .clip(shape)
    }
}

/**
 * 以命令式令牌管理系统栏 insets 动画的子树分发。这个状态不进入
 * Compose，回调到期时不会让整个 MainContainer 因不可见的标记而重组。
 */
private class InsetsAnimationDispatchSuppressor(
    private val target: View
) {
    private var nextToken = 0L
    private val activeTokens = mutableSetOf<Long>()
    private val callback = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
        override fun onProgress(
            insets: WindowInsetsCompat,
            runningAnimations: MutableList<WindowInsetsAnimationCompat>
        ): WindowInsetsCompat = insets
    }

    fun acquire(): Long {
        val token = ++nextToken
        if (activeTokens.add(token) && activeTokens.size == 1) {
            ViewCompat.setWindowInsetsAnimationCallback(target, callback)
        }
        return token
    }

    fun release(token: Long) {
        if (activeTokens.remove(token) && activeTokens.isEmpty()) {
            ViewCompat.setWindowInsetsAnimationCallback(target, null)
        }
    }

    fun clear() {
        activeTokens.clear()
        ViewCompat.setWindowInsetsAnimationCallback(target, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumDetailRouteFrame(
    backStackEntry: NavBackStackEntry,
    previousBackStackEntry: NavBackStackEntry?,
    stackPopTargetEntryId: String?,
    onPopBackStack: (String?) -> Unit,
    onPageProgressReader: (() -> Float) -> Unit,
    onExitStateChanged: (Boolean) -> Unit,
    onLocalAlbumRemoved: (AlbumDetailUiState.Removed) -> Unit = {},
    onEditRj: (String) -> Unit,
    content: @Composable (AlbumDetailViewModel, AlbumHeroBlurLayerCache) -> Unit
) {
    val viewModel = hiltViewModel<AlbumDetailViewModel>(backStackEntry)
    val previousAlbumDetailEntryId = previousBackStackEntry
        ?.takeIf { isAlbumDetailRoute(it.destination.route) }
        ?.id
    val usesStackTransition = previousAlbumDetailEntryId != null
    val alreadyPresented = backStackEntry.savedStateHandle
        .get<Boolean>(AlbumDetailPresentedStateKey) == true
    val skipEnterAnimation = alreadyPresented ||
        usesStackTransition ||
        stackPopTargetEntryId == backStackEntry.id
    val heroBlurGraphicsLayer = rememberGraphicsLayer()
    val heroBlurLayerCache = remember(heroBlurGraphicsLayer) {
        AlbumHeroBlurLayerCache(heroBlurGraphicsLayer)
    }
    val rootView = LocalView.current
    val pageOffsetProgress = remember(backStackEntry.id) {
        Animatable(if (skipEnterAnimation) 0f else 1f)
    }
    val pageProgressReader = remember(pageOffsetProgress) {
        { pageOffsetProgress.value }
    }
    SideEffect {
        onPageProgressReader(pageProgressReader)
    }
    var exitRequested by remember(backStackEntry.id) { mutableStateOf(false) }
    val currentPopBackStack by rememberUpdatedState(onPopBackStack)
    val currentExitStateChanged by rememberUpdatedState(onExitStateChanged)
    val currentLocalAlbumRemoved by rememberUpdatedState(onLocalAlbumRemoved)
    val closeAlbumDetail = {
        if (!exitRequested) {
            UiFrameWorkCoordinator.markFrameCritical(
                SecondaryPageExitDurationMs.toLong()
            )
            // 返回输入本来就在帧与帧之间处理。在同一个 snapshot 中提交
            // 退出标记和底层页 active 状态，避免下一帧再追加一次整树重组。
            if (!usesStackTransition) {
                currentExitStateChanged(true)
            }
            viewModel.cancelOnlineLoadsForExit()
            exitRequested = true
        }
    }
    LaunchedEffect(viewModel) {
        val removed = viewModel.uiState
            .filter { it is AlbumDetailUiState.Removed }
            .first() as AlbumDetailUiState.Removed
        currentLocalAlbumRemoved(removed)
        closeAlbumDetail()
    }
    BackHandler(enabled = !exitRequested, onBack = closeAlbumDetail)

    LaunchedEffect(backStackEntry.id) {
        // 方向切换可能让当前目的地短暂退出组合。把已展示状态保存在返回栈项中，
        // 重建后直接恢复到屏内，避免重新执行一次入场并与随后的退出动画叠加。
        backStackEntry.savedStateHandle[AlbumDetailPresentedStateKey] = true
        if (skipEnterAnimation) {
            pageOffsetProgress.snapTo(0f)
        } else {
            pageOffsetProgress.snapTo(1f)
            // 详情页先在屏幕外完成一次组合与绘制，再启动可见位移。导航提前使用原本的第二个
            // 准备帧，因此不会改变用户看到的动画起点、时长或缓动。
            withFrameNanos { }
            UiFrameWorkCoordinator.markFrameCritical(
                SecondaryPageEnterDurationMs.toLong()
            )
            pageOffsetProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = SecondaryPageEnterDurationMs,
                    easing = SecondaryPageSlideEasing
                )
            )
        }
        snapshotFlow { exitRequested }.filter { it }.first()

        if (usesStackTransition) {
            rootView.post { currentPopBackStack(previousAlbumDetailEntryId) }
            return@LaunchedEffect
        }

        // 退出前先让底层主页面恢复 active；此时详情页仍完全覆盖屏幕，不产生视觉变化。
        withFrameNanos { }
        UiFrameWorkCoordinator.markFrameCritical(
            SecondaryPageExitDurationMs.toLong()
        )
        pageOffsetProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = SecondaryPageExitDurationMs,
                easing = SecondaryPageSlideEasing
            )
        )
        // animateTo 在 Choreographer 的 animation 阶段恢复协程。如果在这里直接
        // pop，导航销毁会被计入最后一帧动画回调。页面已完全在屏外，
        // 立即投递到当前帧结束后的主线程队列空隙，避免二者叠在同一帧。
        rootView.post { currentPopBackStack(null) }
    }
    DisposableEffect(backStackEntry.id) {
        onDispose {
            currentExitStateChanged(false)
        }
    }

    PageTranslationHost(active = !exitRequested) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val offset = width * pageOffsetProgress.value.coerceIn(0f, 1f)
                    val visibleRight = if (offset >= width - 0.5f) {
                        // 保留屏外预绘制帧，避免动画起点改变。
                        width
                    } else {
                        (width - offset).coerceIn(0f, width)
                    }
                    // 位移和裁剪都只更新 RenderNode 属性，避免逐帧重新录制整张详情页。
                    translationX = offset
                    shape = HorizontalRectClipShape(0f, visibleRight)
                    clip = true
                }
        ) {
            content(viewModel, heroBlurLayerCache)
            AlbumDetailRouteTopBar(
                viewModel = viewModel,
                onBack = closeAlbumDetail,
                onEditRj = onEditRj,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumDetailRouteTopBar(
    viewModel: AlbumDetailViewModel,
    onBack: () -> Unit,
    onEditRj: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val detailState by viewModel.uiState.collectAsStateWithLifecycle()
    val detailModel = (detailState as? AlbumDetailUiState.Success)?.model
    val showManualBind = detailModel?.localAlbum?.id?.let { it > 0L } == true

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(StableWindowInsets.statusBars))
        CenterAlignedTopAppBar(
            modifier = Modifier.height(56.dp),
            title = {},
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White
            ),
            navigationIcon = {
                EaraTopBarIconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .albumDetailTopBarButtonSurface(true)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            },
            actions = {
                PageTranslationAction(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .albumDetailTopBarButtonSurface(true),
                    contentColor = Color.White,
                    activeContentColor = Color.White,
                )
                if (showManualBind) {
                    EaraTopBarIconButton(
                        onClick = {
                            val local = detailModel?.localAlbum
                            val currentRj = detailModel?.rjCode?.trim().orEmpty()
                                .ifBlank { local?.rjCode?.trim().orEmpty() }
                                .ifBlank { local?.workId?.trim().orEmpty() }
                            onEditRj(currentRj)
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .albumDetailTopBarButtonSurface(true)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = appString(R.string.ui_manually_enter_item_number),
                            tint = Color.White
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun SecondaryPageBackground(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val pageBackgroundColor = resolveMainPageBackgroundColor(colorScheme)

    TabletPage(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding)
            .background(pageBackgroundColor)
    ) {
        content()
    }
}

@Composable
private fun SearchAssistPageBackground(
    topPadding: Dp,
    blurState: ProgressiveHeaderBlurState?,
    isActive: Boolean,
    content: @Composable () -> Unit,
) {
    if (blurState == null) {
        SecondaryPageBackground(topPadding = topPadding, content = content)
        return
    }
    Box(
        Modifier.fillMaxSize()
            .drawWithContent {
                // The outgoing page must leave the stationary primary header exposed during its slide.
                clipRect(top = if (isActive) 0f else topPadding.toPx()) { this@drawWithContent.drawContent() }
            }
            .background(resolveMainPageBackgroundColor(AsmrTheme.colorScheme))
    ) {
        CompositionLocalProvider(
            LocalMainHeaderPadding provides topPadding,
            LocalProgressiveHeaderBlur provides blurState,
            content = content,
        )
    }
}

@Suppress("DEPRECATION")
private fun applyMainContainerSystemUi(
    window: android.view.Window,
    forceImmersive: Boolean,
    hideStatusBarForImmersivePage: Boolean,
    nowPlayingVisible: Boolean,
    isDark: Boolean
) {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (
        controller.systemBarsBehavior !=
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    ) {
        // 提前固定系统栏手势行为，避免首次进入沉浸页面时再修改 Window 参数并触发 relayout。
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (window.isStatusBarContrastEnforced) {
            window.isStatusBarContrastEnforced = false
        }
        if (window.isNavigationBarContrastEnforced) {
            window.isNavigationBarContrastEnforced = false
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // Edge-to-edge 内容始终按照短边刘海区域布局，路由切换时只改变系统栏可见性。
        // 若在专辑转场开始时同步修改 Window 属性，WindowManager 会触发一次昂贵的 relayout，
        // 与 Compose 转场争抢同一帧的主线程和 RenderThread 预算。
        val targetCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val attributes = window.attributes
        if (attributes.layoutInDisplayCutoutMode != targetCutoutMode) {
            attributes.layoutInDisplayCutoutMode = targetCutoutMode
            window.attributes = attributes
        }
    }

    when {
        forceImmersive -> {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            if (window.statusBarColor != android.graphics.Color.TRANSPARENT) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            }
            if (window.navigationBarColor != android.graphics.Color.TRANSPARENT) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        nowPlayingVisible -> {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            if (window.statusBarColor != android.graphics.Color.TRANSPARENT) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            }
            if (window.navigationBarColor != android.graphics.Color.TRANSPARENT) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        hideStatusBarForImmersivePage -> {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.show(WindowInsetsCompat.Type.navigationBars())
            if (window.statusBarColor != android.graphics.Color.TRANSPARENT) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            }
            if (window.navigationBarColor != android.graphics.Color.TRANSPARENT) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            // 状态栏已经不可见，不切换图标明暗标志；该 Window 参数变化会额外触发 relayout。
            controller.isAppearanceLightNavigationBars = !isDark
        }
        else -> {
            controller.show(WindowInsetsCompat.Type.systemBars())
            if (window.statusBarColor != android.graphics.Color.TRANSPARENT) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            }
            if (window.navigationBarColor != android.graphics.Color.TRANSPARENT) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }
}

@Suppress("DEPRECATION")
private fun restoreMainContainerSystemUi(
    window: android.view.Window,
    defaultSystemUi: DefaultSystemUiState?
) {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    controller.show(WindowInsetsCompat.Type.systemBars())
    defaultSystemUi?.let { ui ->
        window.statusBarColor = ui.statusBarColor
        window.navigationBarColor = ui.navigationBarColor
        controller.isAppearanceLightStatusBars = ui.lightStatusBars
        controller.isAppearanceLightNavigationBars = ui.lightNavigationBars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ui.statusBarContrastEnforced?.let { window.isStatusBarContrastEnforced = it }
            ui.navigationBarContrastEnforced?.let { window.isNavigationBarContrastEnforced = it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ui.layoutInDisplayCutoutMode?.let { mode ->
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = mode
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun captureDefaultSystemUiState(window: android.view.Window): DefaultSystemUiState {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    return DefaultSystemUiState(
        statusBarColor = window.statusBarColor,
        navigationBarColor = window.navigationBarColor,
        lightStatusBars = controller.isAppearanceLightStatusBars,
        lightNavigationBars = controller.isAppearanceLightNavigationBars,
        statusBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced
        } else {
            null
        },
        navigationBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else {
            null
        },
        layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode
        } else {
            null
        }
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PrimaryBottomChrome(
    activeRoute: String,
    pagerState: PagerState,
    pagerRoutes: List<String>,
    fallbackRoute: String,
    lockedRoute: String?,
    miniPlayerVisible: Boolean,
    miniPlayerDisplayMode: MiniPlayerDisplayMode,
    miniPlayerPlayFeedbackSignal: Long,
    onMiniPlayerDisplayModeChange: (MiniPlayerDisplayMode) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    onNavigate: (String) -> Unit,
    largeLayout: Boolean = false,
    modifier: Modifier = Modifier,
    navItems: List<BottomChromeNavItem> = bottomChromeNavItems()
) {
    val selectionProgresses by remember(
        pagerState,
        pagerRoutes,
        fallbackRoute,
        lockedRoute
    ) {
        derivedStateOf {
            computePrimaryNavSelectionProgresses(
                pagerRoutes = pagerRoutes,
                currentPage = pagerState.currentPage,
                currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                fallbackRoute = fallbackRoute,
                lockedRoute = lockedRoute
            )
        }
    }

    BottomChrome(
        activeRoute = activeRoute,
        selectionProgresses = selectionProgresses,
        animateSelection = !pagerState.isScrollInProgress || lockedRoute != null,
        miniPlayerVisible = miniPlayerVisible,
        miniPlayerDisplayMode = miniPlayerDisplayMode,
        miniPlayerPlayFeedbackSignal = miniPlayerPlayFeedbackSignal,
        onMiniPlayerDisplayModeChange = onMiniPlayerDisplayModeChange,
        onOpenNowPlaying = onOpenNowPlaying,
        onOpenQueue = onOpenQueue,
        onNavigate = onNavigate,
        largeLayout = largeLayout,
        modifier = modifier,
        navItems = navItems
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
fun MainContainer(
    windowSizeClass: WindowSizeClass,
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    settingsDataStore: SettingsDataStore,
    messageManager: MessageManager,
    listeningTracker: ListeningTracker,
    startRouteFromIntent: String?,
    onShowQueue: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onContentReady: () -> Unit,
    playerBackgroundMode: PlayerBackgroundMode,
    coverBackgroundEnabled: Boolean,
    dynamicFlowingBackgroundEnabled: Boolean,
    coverBackgroundClarity: Float,
    coverPreviewMode: CoverPreviewMode,
    nowPlayingHomeLayoutMode: NowPlayingHomeLayoutMode,
    nowPlayingHomeLayoutHintDismissed: Boolean?,
    nowPlayingLyricsSettings: NowPlayingLyricsSettings,
    lyricsPageSettings: LyricsPageSettings,
    forceImmersive: Boolean,
    volumeKeyEventTick: Long
) {
    val activityViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val mainRootView = LocalView.current
    var albumDetailStackPopTargetEntryId by remember { mutableStateOf<String?>(null) }
    var albumDetailPageProgressReader by remember { mutableStateOf<(() -> Float)?>(null) }
    var albumDetailEnterPreparing by remember { mutableStateOf(false) }
    var albumDetailEnterRequestId by remember { mutableLongStateOf(0L) }
    val albumDetailInsetsDispatchSuppressor = remember(mainRootView) {
        InsetsAnimationDispatchSuppressor(mainRootView.rootView)
    }
    DisposableEffect(albumDetailInsetsDispatchSuppressor) {
        onDispose { albumDetailInsetsDispatchSuppressor.clear() }
    }
    val navigator = remember(navController, mainRootView, albumDetailInsetsDispatchSuppressor) {
        AppNavigator(navController) scheduler@{ navigation ->
            if (albumDetailEnterPreparing) return@scheduler
            albumDetailEnterPreparing = true
            val insetsSuppressionToken = albumDetailInsetsDispatchSuppressor.acquire()
            val requestId = ++albumDetailEnterRequestId
            UiFrameWorkCoordinator.markFrameCritical(
                SecondaryPageEnterDurationMs.toLong()
            )
            mainRootView.postDelayed({
                albumDetailInsetsDispatchSuppressor.release(insetsSuppressionToken)
            }, SecondaryPageEnterDurationMs + 180L)
            Choreographer.getInstance().postFrameCallback navigationFrame@{
                if (albumDetailEnterRequestId != requestId) return@navigationFrame
                try {
                    navigation()
                } finally {
                    albumDetailEnterPreparing = false
                }
            }
        }
    }
    val hasPreviousBackStackEntry = navController.previousBackStackEntry != null
    val currentPlaylistSystemType = navBackStackEntry?.arguments?.getString("type")
    val startRoute = remember(startRouteFromIntent) {
        startRouteFromIntent?.trim().orEmpty()
    }
    val initialDestination = remember(startRoute) {
        if (startRoute == Routes.Search) Routes.Search else Routes.Library
    }
    var lastPrimaryRoute by rememberSaveable { mutableStateOf(initialDestination) }
    val currentPrimaryRoute = resolveCurrentPrimaryDestinationRoute(
        currentRoute = currentRoute,
        playlistSystemType = currentPlaylistSystemType
    )
    val activePrimaryRoute = resolvePrimaryRoute(
        currentRoute = currentRoute,
        lastPrimaryRoute = lastPrimaryRoute,
        playlistSystemType = currentPlaylistSystemType
    )
    LaunchedEffect(currentRoute) {
        UiFrameWorkCoordinator.markFrameCritical(
            maxOf(SecondaryPageEnterDurationMs, SecondaryPageExitDurationMs) + 120L
        )
        if (!isAlbumDetailRoute(currentRoute)) {
            albumDetailStackPopTargetEntryId = null
            albumDetailPageProgressReader = null
        }
    }
    val bottomNavItems = remember { bottomChromeNavItems() }
    val storedMiniPlayerDisplayMode by settingsDataStore.miniPlayerDisplayMode.collectAsStateWithLifecycle(
        initialValue = MiniPlayerDisplayMode.CoverOnly.name
    )
    var miniPlayerDisplayMode by rememberSaveable { mutableStateOf(MiniPlayerDisplayMode.CoverOnly) }
    var miniPlayerPlayFeedbackSignal by remember { mutableLongStateOf(0L) }
    fun requestMiniPlayerPlayFeedback() {
        miniPlayerPlayFeedbackSignal += 1L
    }
    val primaryPagerRoutes = remember(bottomNavItems) { bottomNavItems.map { it.route } }
    val primaryPagePreloadState = remember(primaryPagerRoutes) {
        PrimaryPagePreloadState(primaryPagerRoutes.size)
    }
    val initialLibraryContentReady = remember(libraryViewModel) { kotlinx.coroutines.CompletableDeferred<Unit>() }
    val initialPrimaryPage = remember(initialDestination, primaryPagerRoutes) {
        primaryPagerRoutes.indexOf(initialDestination).takeIf { it >= 0 } ?: 0
    }
    val primaryPagerState = rememberPagerState(
        initialPage = initialPrimaryPage,
        pageCount = { primaryPagerRoutes.size }
    )
    val primaryPagerFlingBehavior = PagerDefaults.flingBehavior(
        state = primaryPagerState,
        snapPositionalThreshold = PrimaryPagerSnapThreshold
    )
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val primaryContentStateHolder = rememberSaveableStateHolder()
    val coverShadowCache = rememberAlbumCoverShadowCache()
    var primaryPagerScrollLocked by remember { mutableStateOf(false) }
    var pendingPrimaryNavigationRoute by remember { mutableStateOf<String?>(null) }
    val visualPrimaryRoute = remember(activePrimaryRoute, pendingPrimaryNavigationRoute, primaryPagerRoutes) {
        resolvePrimaryNavVisualRoute(
            activeRoute = activePrimaryRoute,
            pendingRoute = pendingPrimaryNavigationRoute,
            pagerRoutes = primaryPagerRoutes
        )
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        onContentReady()
    }
    LaunchedEffect(Unit) {
        listeningTracker.start(this, playerViewModel.playback)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, listeningTracker) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                listeningTracker.flushNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(navController, startRoute, initialDestination) {
        if (startRoute.isBlank() || startRoute == initialDestination) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        if (isPrimaryRoute(startRoute)) {
            navController.navigatePrimaryRoute(startRoute)
        } else {
            navController.navigateSingleTop(startRoute)
        }
    }
    var blockNavTouches by remember { mutableStateOf(false) }
    var lastRouteForTouchBlock by remember { mutableStateOf(currentPrimaryRoute ?: currentRoute) }
    var touchBlockSeq by remember { mutableIntStateOf(0) }
    var pendingDetailNavigation by remember { mutableStateOf(false) }
    var pendingDetailNavigationSeq by remember { mutableIntStateOf(0) }
    var cancelPendingDetailNavigation by remember { mutableStateOf(false) }
    var albumDetailExitInProgress by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val downloadsViewModel: DownloadsViewModel = hiltViewModel(activityViewModelStoreOwner)
    val onlineAudioDownloadConfirmation by downloadsViewModel.onlineAudioDownloadConfirmation.collectAsStateWithLifecycle()
    val settingsViewModel: SettingsViewModel = hiltViewModel(activityViewModelStoreOwner)
    val hasCurrentMediaItem by remember(playerViewModel) {
        playerViewModel.playback
            .map { it.currentMediaItem != null }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val sharedPlayerItem by remember(playerViewModel) {
        playerViewModel.playback
            .map { it.currentMediaItem }
            .distinctUntilChanged { old, new ->
                old?.mediaId == new?.mediaId &&
                    old?.localConfiguration?.uri == new?.localConfiguration?.uri &&
                    old?.mediaMetadata?.artworkUri == new?.mediaMetadata?.artworkUri
            }
    }.collectAsStateWithLifecycle(initialValue = null)
    val bulkProgress by libraryViewModel.bulkProgress.collectAsStateWithLifecycle()
    val cloudSyncSelectionDialogState by libraryViewModel.cloudSyncSelectionDialogState.collectAsStateWithLifecycle()
    val appVolumePercent by playerViewModel.appVolumePercent.collectAsStateWithLifecycle()
    var showManualRjDialog by remember { mutableStateOf(false) }
    var manualRjInput by remember { mutableStateOf("") }
    var showHardwareVolumeOverlay by remember { mutableStateOf(false) }
    var hardwareVolumeOverlayInteracting by remember { mutableStateOf(false) }
    var hardwareVolumeOverlayHoldTick by remember { mutableLongStateOf(0L) }
    var lastHandledVolumeKeyTick by remember { mutableLongStateOf(0L) }
    var lastLibraryBackPressElapsedRealtime by remember { mutableLongStateOf(0L) }
    var nowPlayingVolumeEventTick by remember { mutableLongStateOf(0L) }
    var lastNonZeroAppVolumePercent by rememberSaveable { mutableIntStateOf(AppVolume.DefaultPercent) }
    var hardwareVolumeOverlayBounds by remember { mutableStateOf<Rect?>(null) }
    var libraryScrollToTopSignal by remember { mutableLongStateOf(0L) }
    var searchScrollToTopSignal by remember { mutableLongStateOf(0L) }
    var submittedSearchKeyword by rememberSaveable { mutableStateOf("") }
    var submittedSearchOrderName by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().orderName) }
    var submittedSearchPurchasedOnly by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().purchasedOnly) }
    var submittedSearchPresaleOnly by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().presaleOnly) }
    var submittedSearchChineseTranslatedOnly by rememberSaveable {
        mutableStateOf(SearchAssistSearchRequest().chineseTranslatedOnly)
    }
    var submittedSearchCollectedOnly by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().collectedOnly) }
    var submittedSearchCollectedSourceName by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().collectedSourceName) }
    var submittedSearchHasSubtitle by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().hasSubtitle) }
    var submittedSearchAllAges by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().allAges) }
    var submittedSearchCollectedSortName by rememberSaveable {
        mutableStateOf(SearchAssistSearchRequest().collectedSortName)
    }
    var submittedSearchLocale by rememberSaveable { mutableStateOf(SearchAssistSearchRequest().locale) }
    var submittedSearchSignal by rememberSaveable { mutableLongStateOf(0L) }
    var searchAssistInitialRequest by remember { mutableStateOf(SearchAssistSearchRequest()) }
    var favoritesScrollToTopSignal by remember { mutableLongStateOf(0L) }
    var playlistsScrollToTopSignal by remember { mutableLongStateOf(0L) }
    var groupsScrollToTopSignal by remember { mutableLongStateOf(0L) }
    var downloadsScrollToTopSignal by remember { mutableLongStateOf(0L) }
    var settingsScrollToTopSignal by remember { mutableLongStateOf(0L) }
    var settingsDetailPageVisible by rememberSaveable { mutableStateOf(false) }
    var cloudGroups by rememberSaveable { mutableStateOf(false) }
    var hotListeningScrollToTopSignal by remember { mutableLongStateOf(0L) }
    val appVolumeWarningSessionState = rememberAppVolumeWarningSessionState()
    val audioOutputRouteKind = rememberCurrentAudioOutputRouteKind()
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isPhone = !rememberIsTabletDevice()
    val useTabletRail = !isPhone && isLandscape && configuration.screenWidthDp >= 840
    val tabletRailInset = if (useTabletRail) TabletNavigationRailWidth else 0.dp
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val updateState by settingsViewModel.updateState.collectAsStateWithLifecycle()
    var automaticUpdateDialogDismissed by rememberSaveable { mutableStateOf(false) }
    var automaticUpdateInstallRequested by rememberSaveable { mutableStateOf(false) }
    var pendingAutomaticInstallPath by rememberSaveable { mutableStateOf<String?>(null) }
    var nowPlayingVisible by rememberSaveable { mutableStateOf(false) }
    var nowPlayingVideoFullscreen by remember { mutableStateOf(false) }
    var nowPlayingUsesInlineVolumeControl by remember { mutableStateOf(false) }
    var nowPlayingEqualizerVisible by remember { mutableStateOf(false) }
    var nowPlayingBackdropActive by rememberSaveable { mutableStateOf(false) }
    var nowPlayingPortraitExitPending by remember { mutableStateOf(false) }
    var nowPlayingRouteExitFinished by remember { mutableStateOf(false) }
    var nowPlayingBackdropExitDurationMs by rememberSaveable {
        mutableIntStateOf(NowPlayingMotionSpec.totalExitDurationMs(NowPlayingMotionLayout.PORTRAIT))
    }
    var nowPlayingPlaylistPickerRequest by remember { mutableStateOf<PlaylistPickerRequest?>(null) }
    var albumBatchPlaylistPickerRequest by remember { mutableStateOf<BatchPlaylistPickerRequest?>(null) }
    var libraryGroupPickerAlbumId by remember { mutableStateOf<Long?>(null) }
    val hideStatusBarForImmersivePage = shouldHideStatusBarForImmersivePage(
        currentRoute = currentRoute
            .takeUnless { albumDetailExitInProgress }
            .let { route -> if (albumDetailEnterPreparing) "album_detail_preparing" else route },
        nowPlayingVisible = nowPlayingVisible
    )
    val openNowPlaying = openNowPlaying@{
        if (nowPlayingVisible) return@openNowPlaying
        nowPlayingPortraitExitPending = false
        nowPlayingRouteExitFinished = false
        nowPlayingBackdropActive = true
        nowPlayingVisible = true
    }
    val finalizeNowPlayingClose: () -> Unit = {
        nowPlayingPlaylistPickerRequest = null
        albumBatchPlaylistPickerRequest = null
        nowPlayingBackdropActive = false
        nowPlayingPortraitExitPending = false
        nowPlayingRouteExitFinished = false
        nowPlayingVideoFullscreen = false
        nowPlayingUsesInlineVolumeControl = false
        nowPlayingEqualizerVisible = false
        nowPlayingVisible = false
    }
    val closeNowPlaying: () -> Unit = {
        nowPlayingRouteExitFinished = true
        if (isPhone && isLandscape) {
            nowPlayingPortraitExitPending = true
            nowPlayingBackdropActive = true
        } else {
            finalizeNowPlayingClose()
        }
    }
    LaunchedEffect(
        nowPlayingPortraitExitPending,
        nowPlayingRouteExitFinished,
        isPhone,
        isLandscape
    ) {
        if (
            nowPlayingPortraitExitPending &&
            nowPlayingRouteExitFinished &&
            (!isPhone || !isLandscape)
        ) {
            finalizeNowPlayingClose()
        }
    }
    val playerBackdropVisible = nowPlayingVisible
    val sharedPlayerIsVideo = sharedPlayerItem.isVideoPlaybackItem()
    val videoOutputEnabled = sharedPlayerIsVideo
    DisposableEffect(playerViewModel, videoOutputEnabled) {
        playerViewModel.setVideoOutputEnabled(videoOutputEnabled)
        onDispose {
            if (videoOutputEnabled) playerViewModel.setVideoOutputEnabled(false)
        }
    }
    val sharedUseDragPreview = playerBackdropVisible &&
        playerBackgroundMode == PlayerBackgroundMode.Theme &&
        coverBackgroundEnabled &&
        coverPreviewMode == CoverPreviewMode.Drag &&
        !sharedPlayerIsVideo
    val sharedUseMotionPreview = playerBackdropVisible &&
        playerBackgroundMode == PlayerBackgroundMode.Theme &&
        coverBackgroundEnabled &&
        coverPreviewMode == CoverPreviewMode.Motion &&
        !sharedPlayerIsVideo
    val sharedCoverMotionState = rememberCoverMotionState(
        enabled = sharedUseMotionPreview,
        resetKey = sharedPlayerItem?.mediaId
    )
    val sharedCoverDragPreviewState = rememberCoverDragPreviewState(
        enabled = sharedUseDragPreview,
        resetKey = sharedPlayerItem?.mediaId
    )
    val sharedPlayerBackdropAlignment = when {
        sharedUseDragPreview -> BiasAlignment(
            horizontalBias = sharedCoverDragPreviewState.horizontalBias,
            verticalBias = sharedCoverDragPreviewState.verticalBias
        )
        sharedUseMotionPreview -> BiasAlignment(
            horizontalBias = sharedCoverMotionState.horizontalBias,
            verticalBias = sharedCoverMotionState.verticalBias
        )
        else -> Alignment.Center
    }
    val nowPlayingBackdropAlpha by animateFloatAsState(
        targetValue = if (nowPlayingBackdropActive) 1f else 0f,
        animationSpec = if (nowPlayingBackdropActive) {
            tween(
                durationMillis = 360,
                easing = LinearOutSlowInEasing
            )
        } else {
            keyframes {
                durationMillis = nowPlayingBackdropExitDurationMs
                1f at 0
                1f at (nowPlayingBackdropExitDurationMs * 0.58f).toInt()
                0f at nowPlayingBackdropExitDurationMs using FastOutLinearInEasing
            }
        },
        label = "nowPlayingBackdropAlpha"
    )
    val currentPrimaryRouteState = rememberUpdatedState(currentPrimaryRoute)
    val primaryPagePreloadEnabled = currentPrimaryRoute != null &&
        !albumDetailExitInProgress && !nowPlayingBackdropActive &&
        !pendingDetailNavigation && pendingPrimaryNavigationRoute == null &&
        !primaryPagerState.isScrollInProgress && !blockNavTouches &&
        !settingsDetailPageVisible && !primaryPagerScrollLocked && !primaryPagePreloadState.interactionActive
    PreloadPrimaryPages(
        state = primaryPagePreloadState,
        pagerState = primaryPagerState,
        routes = primaryPagerRoutes,
        enabled = primaryPagePreloadEnabled,
    )
    var primaryNavigationJob by remember { mutableStateOf<Job?>(null) }
    var primaryNavigationRequestId by remember { mutableLongStateOf(0L) }
    DisposableEffect(Unit) {
        onDispose {
            primaryNavigationJob?.cancel()
        }
    }

    fun openPrimaryRoute(route: String, pagerRoutes: List<String> = primaryPagerRoutes) {
        val targetPage = pagerRoutes.indexOf(route)
        primaryNavigationJob?.cancel()
        primaryNavigationRequestId += 1L
        val requestId = primaryNavigationRequestId
        if (targetPage >= 0 && currentPrimaryRoute != null) {
            pendingPrimaryNavigationRoute = route
            UiFrameWorkCoordinator.markFrameCritical(PrimaryPageSelectionQuietWindowMs)
            primaryNavigationJob = scope.launch(start = CoroutineStart.LAZY) {
                var completed = false
                try {
                    primaryPagerState.stopScroll(MutatePriority.PreventUserInput)
                    primaryPagerState.scrollToPage(targetPage)
                    val currentEntry = navController.currentBackStackEntry
                    val currentDestination = resolveCurrentPrimaryDestinationRoute(
                        currentRoute = currentEntry?.destination?.route,
                        playlistSystemType = currentEntry?.arguments?.getString("type"),
                    )
                    if (currentDestination != route) {
                        navController.navigatePrimaryRoute(route)
                    }
                    completed = true
                } finally {
                    if (primaryNavigationRequestId == requestId) {
                        primaryNavigationJob = null
                        if (!completed && pendingPrimaryNavigationRoute == route) {
                            pendingPrimaryNavigationRoute = null
                        }
                    }
                }
            }
            primaryNavigationJob?.start()
        } else {
            primaryNavigationJob = null
            pendingPrimaryNavigationRoute = null
            navController.navigatePrimaryRoute(route)
        }
    }

    fun triggerPrimaryRouteScrollToTop(route: String) {
        when (route) {
            Routes.Library -> libraryScrollToTopSignal += 1L
            Routes.Search -> searchScrollToTopSignal += 1L
            Routes.HotListening -> hotListeningScrollToTopSignal += 1L
            "playlist_system/favorites" -> favoritesScrollToTopSignal += 1L
            "playlists" -> playlistsScrollToTopSignal += 1L
            "groups" -> groupsScrollToTopSignal += 1L
            "settings" -> settingsScrollToTopSignal += 1L
        }
    }

    fun openAlbumDetailFromSearch(albumId: Long?, rj: String?, preferDlsitePlay: Boolean = false, resourceSource: String? = null) {
        val seq = ++pendingDetailNavigationSeq
        pendingDetailNavigation = true
        cancelPendingDetailNavigation = false
        navigator.openAlbumDetail(albumId = albumId, rj = rj, preferDlsitePlay = preferDlsitePlay, resourceSource = resourceSource)
        scope.launch {
            delay(700)
            if (pendingDetailNavigationSeq == seq) {
                pendingDetailNavigation = false
            }
        }
    }

    fun submitSearchAssistRequest(request: SearchAssistSearchRequest) {
        val targetEntry = runCatching {
            navController.getBackStackEntry(Routes.Search)
        }.getOrNull() ?: navController.previousBackStackEntry
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_KEY, request.keyword)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_ORDER_KEY, request.orderName)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_PURCHASED_ONLY_KEY, request.purchasedOnly)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_PRESALE_ONLY_KEY, request.presaleOnly)
        targetEntry?.savedStateHandle?.set(
            SEARCH_ASSIST_RESULT_CHINESE_TRANSLATED_ONLY_KEY,
            request.chineseTranslatedOnly
        )
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_COLLECTED_ONLY_KEY, request.collectedOnly)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_COLLECTED_SOURCE_KEY, request.collectedSourceName)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_HAS_SUBTITLE_KEY, request.hasSubtitle)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_ALL_AGES_KEY, request.allAges)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_COLLECTED_SORT_KEY, request.collectedSortName)
        targetEntry?.savedStateHandle?.set(SEARCH_ASSIST_RESULT_LOCALE_KEY, request.locale)
        targetEntry?.savedStateHandle?.set(
            SEARCH_ASSIST_RESULT_SIGNAL_KEY,
            System.currentTimeMillis()
        )
        navController.popBackStack(Routes.Search, false)
    }

    fun submitMetaSearchKeyword(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return
        val request = SearchAssistSearchRequest(keyword = normalized)
        submittedSearchKeyword = request.keyword
        submittedSearchOrderName = request.orderName
        submittedSearchPurchasedOnly = request.purchasedOnly
        submittedSearchPresaleOnly = request.presaleOnly
        submittedSearchChineseTranslatedOnly = request.chineseTranslatedOnly
        submittedSearchCollectedOnly = request.collectedOnly
        submittedSearchCollectedSourceName = request.collectedSourceName
        submittedSearchHasSubtitle = request.hasSubtitle
        submittedSearchAllAges = request.allAges
        submittedSearchCollectedSortName = request.collectedSortName
        submittedSearchLocale = request.locale
        submittedSearchSignal = System.currentTimeMillis()
        openPrimaryRoute(Routes.Search)
    }

    fun handleAutomaticInstallResult(result: AppUpdateInstallResult, apkPath: String) {
        when (result) {
            AppUpdateInstallResult.Started -> {
                pendingAutomaticInstallPath = null
                messageManager.showInfo(appString(R.string.ui_opening_system_installer))
            }
            AppUpdateInstallResult.PermissionRequired -> {
                pendingAutomaticInstallPath = apkPath
                messageManager.showInfo(appString(R.string.ui_please_allow_eara_to_continue_installing_apps_from))
            }
            AppUpdateInstallResult.FileInvalid -> {
                pendingAutomaticInstallPath = null
                messageManager.showError(appString(R.string.ui_the_download_file_is_invalid_please_download_again))
            }
            is AppUpdateInstallResult.Failed -> {
                pendingAutomaticInstallPath = null
                messageManager.showError(result.message)
            }
        }
    }

    DisposableEffect(lifecycleOwner, pendingAutomaticInstallPath, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val apkPath = pendingAutomaticInstallPath ?: return@LifecycleEventObserver
            val canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()
            if (!canInstall) return@LifecycleEventObserver
            handleAutomaticInstallResult(
                result = launchDownloadedApkInstall(context, apkPath),
                apkPath = apkPath
            )
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(settingsViewModel) {
        settingsViewModel.checkUpdateAutomatically()
    }

    LaunchedEffect(updateState, automaticUpdateInstallRequested) {
        if (!automaticUpdateInstallRequested) return@LaunchedEffect
        when (val state = updateState) {
            is AppUpdateState.ReadyToInstall -> {
                if (state.source != UpdateCheckSource.Automatic) return@LaunchedEffect
                automaticUpdateInstallRequested = false
                handleAutomaticInstallResult(
                    result = launchDownloadedApkInstall(context, state.apkPath),
                    apkPath = state.apkPath
                )
            }
            is AppUpdateState.Failed -> {
                if (state.source != UpdateCheckSource.Automatic) return@LaunchedEffect
                automaticUpdateInstallRequested = false
                messageManager.showError(state.message)
            }
            else -> Unit
        }
    }

    LaunchedEffect(currentPrimaryRoute, primaryPagerRoutes, pendingPrimaryNavigationRoute, primaryNavigationJob) {
        val route = currentPrimaryRoute ?: return@LaunchedEffect
        val pendingRoute = pendingPrimaryNavigationRoute
        if (pendingRoute != null) {
            val pendingPage = primaryPagerRoutes.indexOf(pendingRoute)
            if (
                shouldClearPendingPrimaryNavigationRoute(
                    currentRoute = route,
                    pendingRoute = pendingRoute,
                    navigationInProgress = primaryNavigationJob != null,
                    pendingPage = pendingPage,
                    settledPage = primaryPagerState.settledPage
                )
            ) {
                pendingPrimaryNavigationRoute = null
            } else if (
                route == pendingRoute &&
                primaryNavigationJob == null &&
                shouldSyncPrimaryPagerToRoute(
                    targetPage = pendingPage,
                    settledPage = primaryPagerState.settledPage
                )
            ) {
                primaryPagerState.stopScroll(MutatePriority.PreventUserInput)
                primaryPagerState.scrollToPage(pendingPage)
            }
            return@LaunchedEffect
        }
        val targetPage = primaryPagerRoutes.indexOf(route)
        if (
            shouldSyncPrimaryPagerToRoute(
                targetPage = targetPage,
                settledPage = primaryPagerState.settledPage
            )
        ) {
            primaryPagerState.stopScroll(MutatePriority.PreventUserInput)
            primaryPagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(primaryPagerState) {
        snapshotFlow { primaryPagerState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
    }

    LaunchedEffect(primaryPagerState, primaryPagerRoutes) {
        snapshotFlow { primaryPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (pendingPrimaryNavigationRoute != null) return@collect
                val currentPrimary = currentPrimaryRouteState.value ?: return@collect
                val targetRoute = primaryPagerRoutes.getOrNull(page) ?: return@collect
                if (targetRoute != currentPrimary) {
                    navController.navigatePrimaryRoute(targetRoute)
                }
            }
    }

    LaunchedEffect(currentRoute, currentPrimaryRoute) {
        showHardwareVolumeOverlay = false
        hardwareVolumeOverlayInteracting = false
        hardwareVolumeOverlayBounds = null
        if (pendingDetailNavigation && currentRoute?.startsWith("album_detail") == true) {
            pendingDetailNavigation = false
        }
        if (cancelPendingDetailNavigation && currentRoute?.startsWith("album_detail") == true) {
            cancelPendingDetailNavigation = false
            navController.popBackStack()
            return@LaunchedEffect
        }
        val normalizedCurrentRoute = currentPrimaryRoute ?: currentRoute
        val last = lastRouteForTouchBlock
        val seq = ++touchBlockSeq
        val isPrimaryPagerSwitch =
            last != null &&
                normalizedCurrentRoute != null &&
                last != normalizedCurrentRoute &&
                last in primaryPagerRoutes &&
                normalizedCurrentRoute in primaryPagerRoutes
        val isReturningToPrimaryPage =
            last != null &&
                normalizedCurrentRoute != null &&
                last != normalizedCurrentRoute &&
                last !in primaryPagerRoutes &&
                normalizedCurrentRoute in primaryPagerRoutes
        if (
            last != null &&
            normalizedCurrentRoute != null &&
            last != normalizedCurrentRoute &&
            !isPrimaryPagerSwitch &&
            !isReturningToPrimaryPage
        ) {
            blockNavTouches = true
            try {
                delay(SecondaryPageTouchBlockDurationMs.toLong())
            } finally {
                if (touchBlockSeq == seq) {
                    blockNavTouches = false
                }
            }
        } else {
            blockNavTouches = false
        }
        lastRouteForTouchBlock = normalizedCurrentRoute
    }

    LaunchedEffect(activePrimaryRoute) {
        if (isPrimaryRoute(activePrimaryRoute)) {
            lastPrimaryRoute = activePrimaryRoute
        }
    }

    LaunchedEffect(currentPrimaryRoute, hasPreviousBackStackEntry, nowPlayingVisible) {
        if (currentPrimaryRoute != Routes.Library || hasPreviousBackStackEntry || nowPlayingVisible) {
            lastLibraryBackPressElapsedRealtime = 0L
        }
    }

    LaunchedEffect(storedMiniPlayerDisplayMode) {
        miniPlayerDisplayMode = runCatching {
            MiniPlayerDisplayMode.valueOf(storedMiniPlayerDisplayMode)
        }.getOrElse {
            MiniPlayerDisplayMode.CoverOnly
        }
    }

    LaunchedEffect(nowPlayingVisible) {
        if (!nowPlayingVisible) {
            nowPlayingUsesInlineVolumeControl = false
            nowPlayingEqualizerVisible = false
            return@LaunchedEffect
        }
        showHardwareVolumeOverlay = false
        hardwareVolumeOverlayInteracting = false
        hardwareVolumeOverlayBounds = null
        nowPlayingVolumeEventTick = 0L
    }

    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    val isAlbumDetailRoute = currentRoute?.startsWith("album_detail") == true
    val topBarContentColor = if (isAlbumDetailRoute) Color.White else colorScheme.onSurface

    val defaultSystemUi = remember(activity) {
        activity?.let { act -> captureDefaultSystemUiState(act.window) }
    }

    DisposableEffect(activity) {
        val act = activity ?: return@DisposableEffect onDispose { }
        onDispose {
            restoreMainContainerSystemUi(act.window, defaultSystemUi)
        }
    }

    DisposableEffect(albumDetailInsetsDispatchSuppressor, albumDetailExitInProgress) {
        if (albumDetailExitInProgress) {
            val token = albumDetailInsetsDispatchSuppressor.acquire()
            onDispose { albumDetailInsetsDispatchSuppressor.release(token) }
        } else {
            onDispose { }
        }
    }

    DisposableEffect(
        activity,
        defaultSystemUi,
        forceImmersive,
        hideStatusBarForImmersivePage,
        nowPlayingVisible,
        colorScheme.isDark
    ) {
        val act = activity ?: return@DisposableEffect onDispose { }
        applyMainContainerSystemUi(
            window = act.window,
            forceImmersive = forceImmersive,
            hideStatusBarForImmersivePage = hideStatusBarForImmersivePage,
            nowPlayingVisible = nowPlayingVisible,
            isDark = colorScheme.isDark
        )
        onDispose { }
    }

    // 手机沿用播放页方向策略，平板所有页面保持横屏。
    LaunchedEffect(
        nowPlayingVisible,
        isPhone,
        nowPlayingVideoFullscreen,
        nowPlayingPortraitExitPending
    ) {
        activity?.let { act ->
            act.requestedOrientation = resolveMainRequestedOrientation(
                isPhone = isPhone,
                nowPlayingVisible = nowPlayingVisible,
                videoFullscreen = nowPlayingVideoFullscreen,
                portraitExitPending = nowPlayingPortraitExitPending
            )
        }
    }
    LaunchedEffect(appVolumePercent) {
        if (appVolumePercent > 0) {
            lastNonZeroAppVolumePercent = appVolumePercent
        }
    }

    LaunchedEffect(volumeKeyEventTick) {
        if (volumeKeyEventTick <= 0L) return@LaunchedEffect
        if (volumeKeyEventTick == lastHandledVolumeKeyTick) return@LaunchedEffect
        lastHandledVolumeKeyTick = volumeKeyEventTick
        if (nowPlayingUsesInlineVolumeControl && !nowPlayingEqualizerVisible) {
            showHardwareVolumeOverlay = false
            nowPlayingVolumeEventTick = volumeKeyEventTick
            return@LaunchedEffect
        }
        showHardwareVolumeOverlay = true
        hardwareVolumeOverlayHoldTick = volumeKeyEventTick
    }

    LaunchedEffect(showHardwareVolumeOverlay, hardwareVolumeOverlayHoldTick, hardwareVolumeOverlayInteracting, nowPlayingUsesInlineVolumeControl, nowPlayingEqualizerVisible) {
        if (!showHardwareVolumeOverlay) return@LaunchedEffect
        if (nowPlayingUsesInlineVolumeControl && !nowPlayingEqualizerVisible) {
            showHardwareVolumeOverlay = false
            hardwareVolumeOverlayBounds = null
            return@LaunchedEffect
        }
        if (hardwareVolumeOverlayInteracting) return@LaunchedEffect
        val snapshot = hardwareVolumeOverlayHoldTick
        delay(2_000)
        if (!hardwareVolumeOverlayInteracting && hardwareVolumeOverlayHoldTick == snapshot) {
            showHardwareVolumeOverlay = false
            hardwareVolumeOverlayBounds = null
        }
    }

    BackHandler(pendingDetailNavigation && currentRoute == Routes.Search) {
        pendingDetailNavigation = false
        cancelPendingDetailNavigation = true
    }

    BackHandler(
        enabled = currentPrimaryRoute == Routes.Library &&
            !hasPreviousBackStackEntry &&
            !pendingDetailNavigation &&
            !nowPlayingVisible
    ) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastLibraryBackPressElapsedRealtime <= 2_000L) {
            activity?.let { currentActivity ->
                PlaybackService.requestShutdownForAppExit(currentActivity)
                currentActivity.finishAndRemoveTask()
            }
        } else {
            lastLibraryBackPressElapsedRealtime = now
            messageManager.showInfo(appString(R.string.ui_press_return_again_to_exit_the_app))
        }
    }

    Box(Modifier.fillMaxSize()) {
        val miniPlayerHasContent = hasCurrentMediaItem
        val miniPlayerVisible = miniPlayerHasContent && !nowPlayingVisible
        val bottomChromeVisible = !nowPlayingVisible
        // 专辑详情始终覆盖在主页面之上；让底层顶栏/页面 active 标记在整个详情生命周期内
        // 保持稳定，退出时只更新必要的详情页位移和裁剪，不在同一帧重建整套主页面 chrome。
        val currentScreenIsPrimary = currentPrimaryRoute != null ||
            isAlbumDetailRoute || albumDetailExitInProgress
        val showBackButton = !currentScreenIsPrimary
        val showPrimaryBrand = currentScreenIsPrimary
        val hasOverlayRoute = currentPrimaryRoute == null && !albumDetailExitInProgress
        val albumDetailTransitionActive = isAlbumDetailRoute || albumDetailExitInProgress
        val primaryPageExposed by remember(albumDetailTransitionActive, albumDetailPageProgressReader) {
            derivedStateOf {
                !albumDetailTransitionActive || (albumDetailPageProgressReader?.invoke() ?: 1f) > 0f
            }
        }
        val primaryPageParallaxActive = hasOverlayRoute && !albumDetailTransitionActive
        val primaryPageParallaxOffset = animateDpAsState(
            targetValue = if (primaryPageParallaxActive) -PrimaryPageParallaxOffset else 0.dp,
            animationSpec = tween(
                durationMillis = if (primaryPageParallaxActive) {
                    SecondaryPageEnterDurationMs
                } else {
                    SecondaryPageExitDurationMs
                },
                easing = SecondaryPageSlideEasing
            ),
            label = "primaryPageParallaxOffset"
        )
        val rowRasterCacheTarget = rememberAlbumRowRasterCacheTarget(
            eligiblePage = {
                val page = primaryPagerState.currentPage
                val route = primaryPagerRoutes.getOrNull(page)
                if (primaryPageExposed && !primaryPagerState.isScrollInProgress &&
                    primaryPagerState.currentPageOffsetFraction == 0f &&
                    (route == Routes.Library || route == Routes.Search || route == Routes.HotListening)
                ) page else null
            },
            transformIsIdentity = { primaryPageParallaxOffset.value == 0.dp },
        )
        val useLargeBottomChrome = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact && !isPhone
        val navigationBarBottomPadding = StableWindowInsets.navigationBars
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()
        val bottomChromeBottomPadding = 24.dp + navigationBarBottomPadding
        val bottomOverlayPadding = bottomChromeOverlayHeight(useLargeBottomChrome) + navigationBarBottomPadding
        // 播放页覆盖期间仍保留迷你播放栏占位，避免底层空态在转场中重新居中。
        val tabletBottomPadding = (if (miniPlayerHasContent) 96.dp else 16.dp) + navigationBarBottomPadding
        var secondaryPageTopPadding by remember { mutableStateOf(0.dp) }
        val pageTranslationHeader = remember { PageTranslationHeaderState() }
        val headerBlur = rememberProgressiveHeaderBlur()
        val showHeaderBlur = headerBlur != null && currentScreenIsPrimary &&
            hasProgressiveMainHeader(visualPrimaryRoute)
        val showSearchAssistBlur = headerBlur != null &&
            (currentRoute == Routes.SearchAssist || currentRoute == Routes.SearchAssistPattern)
        val searchAssistHeaderPadding = StableWindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            EaraMainTopBarHeight
        val mainHeader: @Composable () -> Unit = {
            Box {
                EaraTopBarContainer(blurEnabled = showHeaderBlur || showSearchAssistBlur) {
                    Column {
                        Spacer(modifier = Modifier.windowInsetsTopHeight(StableWindowInsets.statusBars))
                        CenterAlignedTopAppBar(
                            modifier = Modifier.height(EaraMainTopBarHeight),
                            title = {
                                val entry = navBackStackEntry
                                val resolvedTitleRoute = if (currentScreenIsPrimary || albumDetailTransitionActive) {
                                    visualPrimaryRoute
                                } else {
                                    currentRoute
                                }
                                val groupName = if (resolvedTitleRoute == "group/{groupId}/{groupName}") {
                                    decodeRouteArg(entry?.arguments?.getString("groupName").orEmpty())
                                } else ""
                                val playlistName = if (resolvedTitleRoute == "playlist/{playlistId}/{playlistName}") {
                                    decodeRouteArg(entry?.arguments?.getString("playlistName").orEmpty())
                                } else ""
                                val systemPlaylistType = if (resolvedTitleRoute == "playlist_system/{type}") {
                                    entry?.arguments?.getString("type").orEmpty()
                                } else ""
                                val appName = stringResource(R.string.app_name)
                                val titleText = when {
                                    resolvedTitleRoute == "library" -> appString(R.string.ui_library)
                                    resolvedTitleRoute == "library_filter" -> appString(R.string.ui_filter)
                                    resolvedTitleRoute == "search" -> appString(R.string.ui_search)
                                    resolvedTitleRoute == Routes.SearchAssist -> appString(R.string.ui_search)
                                    resolvedTitleRoute == Routes.SearchAssistPattern -> appString(R.string.ui_search)
                                    resolvedTitleRoute == Routes.HotListening -> appString(R.string.ui_popular)
                                    resolvedTitleRoute == "playlists" -> appString(R.string.ui_playlists)
                                    resolvedTitleRoute == "playlist/{playlistId}/{playlistName}" ->
                                        playlistName.ifBlank { appString(R.string.ui_playlists) }
                                    resolvedTitleRoute == "playlist_system/favorites" -> appString(R.string.ui_favorites)
                                    resolvedTitleRoute == "playlist_system/{type}" -> when (systemPlaylistType) {
                                        "favorites" -> appString(R.string.ui_favorites)
                                        else -> appString(R.string.ui_favorites)
                                    }
                                    resolvedTitleRoute == "groups" -> appString(R.string.ui_groups)
                                    resolvedTitleRoute == "group/{groupId}/{groupName}" ->
                                        groupName.ifBlank { appString(R.string.ui_groups) }
                                    resolvedTitleRoute == "settings" -> appString(R.string.ui_settings)
                                    resolvedTitleRoute == "downloads" -> appString(R.string.ui_tasks)
                                    resolvedTitleRoute == "listening_calendar" -> appString(R.string.ui_asmr_dashboard)
                                    resolvedTitleRoute == "dlsite_login" -> appString(R.string.ui_dlsite_login)
                                    resolvedTitleRoute == "asmr_one_login" -> appString(R.string.cloud_account)
                                    resolvedTitleRoute?.startsWith("playlist_picker") == true -> appString(R.string.ui_add_to_my_lists)
                                    resolvedTitleRoute?.startsWith("album_detail") == true -> appString(R.string.ui_album_details)
                                    else -> appName
                                }
                                AnimatedContent(
                                    targetState = titleText,
                                    modifier = Modifier
                                        .height(40.dp)
                                        .offset(y = 4.dp),
                                    contentAlignment = Alignment.Center,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing))
                                            + slideInHorizontally(animationSpec = tween(220, easing = LinearOutSlowInEasing)) { it / 4 })
                                            .togetherWith(
                                                fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing))
                                                    + slideOutHorizontally(animationSpec = tween(180, easing = FastOutLinearInEasing)) { -it / 4 }
                                            )
                                    },
                                    label = "headerTitle"
                                ) { targetText ->
                                    Text(
                                        text = targetText,
                                        color = if (albumDetailTransitionActive) {
                                            colorScheme.onSurface
                                        } else {
                                            topBarContentColor
                                        },
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            },
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.Transparent,
                                titleContentColor = topBarContentColor,
                                navigationIconContentColor = topBarContentColor,
                                actionIconContentColor = if (albumDetailTransitionActive) {
                                    colorScheme.onSurface
                                } else {
                                    topBarContentColor
                                }
                            ),
                            navigationIcon = {
                                Box {
                                    if (!useTabletRail && (showPrimaryBrand || albumDetailTransitionActive)) {
                                        EaraBrand(
                                            appName = stringResource(R.string.app_name),
                                            tint = colorScheme.primaryStrong
                                        )
                                    }
                                    if (showBackButton &&
                                        !albumDetailTransitionActive &&
                                        hasPreviousBackStackEntry
                                    ) {
                                        EaraTopBarIconButton(
                                            onClick = { navController.popBackStack() }
                                        ) {
                                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                                        }
                                    }
                                }
                            },
                            actions = {
                                val headerActionRoute = if (albumDetailTransitionActive) {
                                    visualPrimaryRoute
                                } else {
                                    currentRoute
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (headerActionRoute == "groups") {
                                        com.asmr.player.ui.asmrone.CloudSourceToggle(
                                            cloud = cloudGroups,
                                            onToggle = { cloudGroups = !cloudGroups },
                                        )
                                    }
                                    if (headerActionRoute != null &&
                                        (isPrimaryRoute(headerActionRoute) || headerActionRoute == "playlist_system/{type}") &&
                                        !(headerActionRoute == "settings" && settingsDetailPageVisible)
                                    ) {
                                        val downloadTasks by downloadsViewModel.tasks.collectAsStateWithLifecycle()
                                        val activeSubtitleTaskCount by downloadsViewModel.activeSubtitleTaskCount.collectAsStateWithLifecycle()
                                        val activeDownloadCount = remember(downloadTasks) {
                                            downloadTasks.sumOf { task ->
                                                task.items.count {
                                                    it.state == DownloadItemState.RUNNING || it.state == DownloadItemState.ENQUEUED
                                                }
                                            }
                                        }
                                        val activeTaskCount = activeDownloadCount + activeSubtitleTaskCount
                                        PageTranslationHeaderAction(headerActionRoute, Modifier.padding(end = 4.dp))
                                        Box {
                                            EaraTopBarIconButton(
                                                onClick = { navController.navigate("downloads") },
                                                modifier = Modifier.padding(end = 4.dp)
                                            ) {
                                                Icon(Icons.Rounded.Inbox, contentDescription = appString(R.string.ui_tasks))
                                            }
                                            if (activeTaskCount > 0) {
                                                Badge(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                ) {
                                                    Text(activeTaskCount.toString())
                                                }
                                            }
                                        }
                                    }
                                    if (headerActionRoute == "library") {
                                        val viewMode by libraryViewModel.libraryViewMode.collectAsStateWithLifecycle()
                                        if (viewMode != null) {
                                            var viewMenuExpanded by remember { mutableStateOf(false) }
                                            Box {
                                                val normalized = (viewMode ?: 0).coerceIn(0, 2)
                                                val icon = when (normalized) {
                                                    1 -> Icons.Rounded.GridView
                                                    2 -> Icons.Rounded.Audiotrack
                                                    else -> Icons.AutoMirrored.Rounded.ViewList
                                                }
                                                EaraTopBarIconButton(
                                                    onClick = { viewMenuExpanded = true },
                                                    modifier = Modifier.padding(end = 4.dp)
                                                ) {
                                                    Icon(imageVector = icon, contentDescription = appString(R.string.ui_switch_view))
                                                }
                                                MaterialTheme(
                                                    colorScheme = materialColorScheme.copy(
                                                        surface = dynamicContainerColor,
                                                        surfaceContainer = dynamicContainerColor
                                                    )
                                                ) {
                                                    DropdownMenu(
                                                        expanded = viewMenuExpanded,
                                                        onDismissRequest = { viewMenuExpanded = false },
                                                        modifier = Modifier.background(dynamicContainerColor)
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text(androidx.compose.ui.res.stringResource(R.string.ui_album_list)) },
                                                            leadingIcon = {
                                                                Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = null)
                                                            },
                                                            onClick = {
                                                                viewMenuExpanded = false
                                                                libraryViewModel.setLibraryViewMode(0)
                                                            }
                                                        )
                                                        HorizontalDivider(
                                                            modifier = Modifier.padding(horizontal = 8.dp),
                                                            thickness = 0.5.dp,
                                                            color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text(androidx.compose.ui.res.stringResource(R.string.ui_album_card)) },
                                                            leadingIcon = {
                                                                Icon(Icons.Rounded.GridView, contentDescription = null)
                                                            },
                                                            onClick = {
                                                                viewMenuExpanded = false
                                                                libraryViewModel.setLibraryViewMode(1)
                                                            }
                                                        )
                                                        HorizontalDivider(
                                                            modifier = Modifier.padding(horizontal = 8.dp),
                                                            thickness = 0.5.dp,
                                                            color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text(androidx.compose.ui.res.stringResource(R.string.ui_audio_track_list)) },
                                                            leadingIcon = {
                                                                Icon(Icons.Rounded.Audiotrack, contentDescription = null)
                                                            },
                                                            onClick = {
                                                                viewMenuExpanded = false
                                                                libraryViewModel.setLibraryViewMode(2)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else if (headerActionRoute == "search") {
                                        val searchViewModel: SearchViewModel = hiltViewModel(activityViewModelStoreOwner)
                                        val viewMode by searchViewModel.viewMode.collectAsStateWithLifecycle()
                                        EaraTopBarIconButton(
                                            onClick = { searchViewModel.setViewMode(if (viewMode == 1) 0 else 1) },
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (viewMode == 1) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.ViewModule,
                                                contentDescription = if (viewMode == 1) appString(R.string.ui_switch_to_list_view) else appString(R.string.ui_switch_to_card_view)
                                            )
                                        }
                                    } else if (headerActionRoute == Routes.HotListening) {
                                        val hotListeningViewModel: HotListeningViewModel = hiltViewModel(activityViewModelStoreOwner)
                                        val viewMode by hotListeningViewModel.viewMode.collectAsStateWithLifecycle()
                                        EaraTopBarIconButton(
                                            onClick = { hotListeningViewModel.setViewMode(if (viewMode == 1) 0 else 1) },
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (viewMode == 1) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.ViewModule,
                                                contentDescription = if (viewMode == 1) appString(R.string.ui_switch_to_list_view) else appString(R.string.ui_switch_to_card_view)
                                            )
                                        }
                                    } else if (headerActionRoute == "downloads") {
                                        val tasks by downloadsViewModel.tasks.collectAsStateWithLifecycle()
                                        val hasActiveDownloads = remember(tasks) {
                                            tasks.any { task ->
                                                task.items.any { it.state == DownloadItemState.RUNNING || it.state == DownloadItemState.ENQUEUED }
                                            }
                                        }
                                        val hasPausedDownloads = remember(tasks) {
                                            tasks.any { task ->
                                                task.items.any { it.state == DownloadItemState.PAUSED }
                                            }
                                        }

                                        if (hasActiveDownloads) {
                                            TextButton(
                                                onClick = { downloadsViewModel.pauseAll() },
                                                colors = ButtonDefaults.textButtonColors(contentColor = topBarContentColor)
                                            ) { Text(androidx.compose.ui.res.stringResource(R.string.ui_pause_all)) }
                                        } else if (hasPausedDownloads) {
                                            TextButton(
                                                onClick = { downloadsViewModel.resumeAll() },
                                                colors = ButtonDefaults.textButtonColors(contentColor = topBarContentColor)
                                            ) { Text(androidx.compose.ui.res.stringResource(R.string.ui_continue_all)) }
                                        }
                                    }
                                }
                            }
                        )

                        val p = bulkProgress
                        if (currentRoute == "library" && p?.phase == BulkPhase.ScanningLocal) {
                            if (p.total > 0) {
                                LinearProgressIndicator(
                                    progress = { p.fraction },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
        CompositionLocalProvider(
            LocalBottomOverlayPadding provides (if (useTabletRail) tabletBottomPadding else bottomOverlayPadding),
            LocalTabletLayout provides TabletLayout(!isPhone, configuration.screenWidthDp.dp - tabletRailInset, configuration.screenHeightDp.dp),
            LocalPageTranslationHeader provides pageTranslationHeader,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(resolveMainPageBackgroundColor(colorScheme))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            val visibleRight = if (albumDetailTransitionActive) {
                                val contentStart = tabletRailInset.toPx().coerceIn(0f, width)
                                val progress = (albumDetailPageProgressReader?.invoke() ?: 1f).coerceIn(0f, 1f)
                                contentStart + (width - contentStart) * progress
                            } else {
                                width
                            }
                            // 详情页是完全不透明的前景。只提交它左侧仍然可见的主页面区域，
                            // 裁剪保持在 RenderNode 属性更新路径，底层主页面不再跟随位移。
                            shape = HorizontalRectClipShape(0f, visibleRight)
                            clip = true
                        }
                ) {
                    Scaffold(
                        modifier = Modifier.padding(start = tabletRailInset),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        containerColor = Color.Transparent,
                        contentColor = colorScheme.onBackground,
                        topBar = {
                            if (showSearchAssistBlur) {
                                Spacer(Modifier.height(searchAssistHeaderPadding))
                            } else {
                                mainHeader()
                            }
                        }
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            val topContentPadding = padding.calculateTopPadding()
                            SideEffect {
                                if (secondaryPageTopPadding != topContentPadding) {
                                    secondaryPageTopPadding = topContentPadding
                                }
                            }
                            primaryContentStateHolder.SaveableStateProvider("primary_pager") {
                                HorizontalPager(
                                    state = primaryPagerState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .observePrimaryPagePreloadInteractions(primaryPagePreloadState)
                                        .padding(top = if (headerBlur == null) topContentPadding else 0.dp)
                                        .graphicsLayer {
                                            translationX = if (albumDetailTransitionActive) {
                                                0f
                                            } else {
                                                primaryPageParallaxOffset.value.toPx()
                                            }
                                        },
                                    beyondViewportPageCount = primaryPagePreloadState.beyondBoundsPageCount,
                                    flingBehavior = primaryPagerFlingBehavior,
                                    userScrollEnabled = !primaryPagerScrollLocked && !hasOverlayRoute,
                                    key = { primaryPagerRoutes[it] }
                                ) { page ->
                                    val route = primaryPagerRoutes[page]
                                    val primaryRouteActive = visualPrimaryRoute == route
                                    val pagerRouteVisible = primaryPagerState.currentPage == page ||
                                        (
                                            primaryPagerState.isScrollInProgress &&
                                                primaryPagerState.targetPage == page
                                            )
                                    val primaryRouteImmediatelyActive = !hasOverlayRoute &&
                                        (primaryRouteActive || pagerRouteVisible)
                                    val pageActuallyVisible by remember(page, primaryPagerState) {
                                        derivedStateOf {
                                            val layout = primaryPagerState.layoutInfo
                                            layout.visiblePagesInfo.any {
                                                it.index == page && it.offset < layout.viewportEndOffset &&
                                                    it.offset + layout.pageSize > layout.viewportStartOffset
                                            }
                                        }
                                    }
                                    val primaryPageContentVisible = primaryPageExposed &&
                                        (pageActuallyVisible || primaryRouteActive || pagerRouteVisible)
                                    val primaryRouteVisibleState = rememberUpdatedState(primaryPageContentVisible)
                                    val deferredPageContent = remember(route) {
                                        DeferredPageContentState(primaryRouteVisibleState, route)
                                    }
                                    LaunchedEffect(route, primaryPageContentVisible) {
                                        tracePrimaryPagePreloadEvent("visible:$route:$primaryPageContentVisible")
                                    }
                                    val primaryRouteWarming = primaryPagePreloadEnabled &&
                                        primaryPagePreloadState.requestedRoute == route &&
                                        !primaryPagePreloadState.isComplete(route)
                                    // 空闲期先让隐藏页消费首份真实数据并完成布局，再冻结订阅。
                                    // 详情页退出动画中使用已保留的主页面快照状态；等详情真正弹栈后再恢复
                                    // 数据流，避免在返回手势首帧同时启动查询、排序和列表状态转换。
                                    val primaryRouteDataActive = (primaryRouteImmediatelyActive || primaryRouteWarming) &&
                                        !isAlbumDetailRoute
                                    val primaryRouteDataActiveState = rememberUpdatedState(
                                        primaryRouteDataActive
                                    )
                                    val overlayHeaderPadding = if (headerBlur != null && hasProgressiveMainHeader(route)) {
                                        searchAssistHeaderPadding
                                    } else 0.dp
                                    val pageHeaderBlur = if (overlayHeaderPadding > 0.dp) rememberProgressiveHeaderBlur() else null
                                    val rowRasterCacheAllowed = when (route) {
                                        Routes.Library, Routes.Search, Routes.HotListening -> {
                                            rememberAlbumRowRasterCachePolicy {
                                                rowRasterCacheTarget.value == page
                                            }
                                        }
                                        else -> null
                                    }
                                    CompositionLocalProvider(
                                        LocalBottomOverlayPadding provides (if (useTabletRail) tabletBottomPadding else bottomOverlayPadding),
                                        LocalAlbumRowRasterCacheAllowed provides rowRasterCacheAllowed,
                                        LocalAlbumCoverShadowCache provides coverShadowCache,
                                        LocalDeferredPageContent provides deferredPageContent,
                                        LocalMainHeaderPadding provides overlayHeaderPadding,
                                        LocalPageAnimationsActive provides primaryPageContentVisible,
                                        LocalProgressiveHeaderBlur provides pageHeaderBlur,
                                    ) {
                                    TabletPage(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (route !in listOf(Routes.Library, Routes.Search, Routes.HotListening)) {
                                                    Modifier.progressiveHeaderContent()
                                                } else Modifier
                                            )
                                            .padding(top = if (headerBlur != null && !hasProgressiveMainHeader(route)) topContentPadding else 0.dp)
                                            // 页面内容使用独立 RenderNode 保留 display list；Pager 滚动时
                                            // 只更新图层位置，避免逐帧重录复杂列表和设置页的绘制命令。
                                            .graphicsLayer { clip = false }
                                    ) {
                                        primaryContentStateHolder.SaveableStateProvider("primary_route:$route") {
                                            when (route) {
                                        Routes.Library -> {
                                            PrimaryPagePreloadEffect(primaryPagePreloadState, route, primaryRouteWarming) {
                                                initialLibraryContentReady.await()
                                            }
                                            LibraryScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                scrollToTopSignal = libraryScrollToTopSignal,
                                                onInitialContentReady = { initialLibraryContentReady.complete(Unit) },
                                                onAlbumClick = { album ->
                                                    AlbumCoverHintStore.recordLocalAlbum(album)
                                                    navigator.openAlbumDetail(
                                                        albumId = album.id,
                                                        rj = null
                                                    )
                                                },
                                                onPlayTracks = { album, tracks, startTrack ->
                                                    scope.launch {
                                                        if (playerViewModel.playTracksPrepared(album, tracks, startTrack)) {
                                                            requestMiniPlayerPlayFeedback()
                                                        }
                                                    }
                                                },
                                                onOpenPlaylistPicker = { item ->
                                                    albumBatchPlaylistPickerRequest = BatchPlaylistPickerRequest(listOf(item))
                                                },
                                                onOpenGroupPicker = { albumId ->
                                                    libraryGroupPickerAlbumId = albumId
                                                },
                                                onOpenFilterScreen = { navController.navigateSingleTop("library_filter") },
                                                onSearchKeyword = ::submitMetaSearchKeyword,
                                                viewModel = libraryViewModel
                                            )
                                        }

                                        Routes.Search -> {
                                            val searchViewModel: SearchViewModel = hiltViewModel(activityViewModelStoreOwner)
                                            PrimaryPagePreloadEffect(primaryPagePreloadState, route, primaryRouteWarming) {
                                                searchViewModel.uiState.first {
                                                    it is com.asmr.player.ui.search.SearchUiState.Success ||
                                                        it is com.asmr.player.ui.search.SearchUiState.Error
                                                }
                                            }
                                            SearchScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                scrollToTopSignal = searchScrollToTopSignal,
                                                submittedSearchKeyword = submittedSearchKeyword,
                                                submittedSearchOrderName = submittedSearchOrderName,
                                                submittedSearchPurchasedOnly = submittedSearchPurchasedOnly,
                                                submittedSearchPresaleOnly = submittedSearchPresaleOnly,
                                                submittedSearchChineseTranslatedOnly = submittedSearchChineseTranslatedOnly,
                                                submittedSearchCollectedOnly = submittedSearchCollectedOnly,
                                                submittedSearchCollectedSourceName = submittedSearchCollectedSourceName,
                                                submittedSearchHasSubtitle = submittedSearchHasSubtitle,
                                                submittedSearchAllAges = submittedSearchAllAges,
                                                submittedSearchCollectedSortName = submittedSearchCollectedSortName,
                                                submittedSearchLocale = submittedSearchLocale,
                                                submittedSearchSignal = submittedSearchSignal,
                                                onHorizontalPagerScrollLockChanged = { active ->
                                                    primaryPagerScrollLocked = active
                                                },
                                                onOpenSearchAssist = { request ->
                                                    searchAssistInitialRequest = request
                                                    navController.navigateSingleTop(Routes.searchAssist(request.keyword))
                                                },
                                                onAlbumClick = searchAlbumClick@ { album, fromPurchasedOnly, hasResolvedDetail ->
                                                    val workNo = album.rjCode.ifBlank { album.workId }
                                                    if (workNo.isBlank()) return@searchAlbumClick
                                                    AlbumCoverHintStore.record(
                                                        albumId = album.id,
                                                        rjCode = workNo,
                                                        title = album.title,
                                                        circle = album.circle,
                                                        cv = album.cv,
                                                        coverUrl = album.coverUrl,
                                                        tags = album.tags,
                                                        tagDetails = album.tagDetails,
                                                        metadataLocale = album.metadataLocale,
                                                        ratingValue = album.ratingValue,
                                                        ratingCount = album.ratingCount,
                                                        releaseDate = album.releaseDate,
                                                        dlCount = album.dlCount,
                                                        priceJpy = album.priceJpy,
                                                        hasAsmrOne = album.hasAsmrOne,
                                                        description = album.description,
                                                        hasResolvedDlsiteInfo = hasResolvedDetail && !fromPurchasedOnly
                                                    )
                                                    openAlbumDetailFromSearch(
                                                        albumId = album.id,
                                                        rj = workNo,
                                                        preferDlsitePlay = fromPurchasedOnly,
                                                        resourceSource = album.collectedSourceName.takeIf { !it.isNullOrBlank() }
                                                    )
                                                },
                                                viewModel = searchViewModel
                                            )
                                        }

                                        Routes.HotListening -> {
                                            val hotListeningViewModel: HotListeningViewModel = hiltViewModel(activityViewModelStoreOwner)
                                            PrimaryPagePreloadEffect(primaryPagePreloadState, route, primaryRouteWarming) {
                                                hotListeningViewModel.uiState.first {
                                                    it !is com.asmr.player.ui.hotlistening.HotListeningUiState.Loading
                                                }
                                            }
                                            HotListeningScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActiveState,
                                                scrollToTopSignal = hotListeningScrollToTopSignal,
                                                onAlbumClick = { album ->
                                                    AlbumCoverHintStore.record(
                                                        albumId = album.id,
                                                        rjCode = album.rjCode.ifBlank { album.workId },
                                                        title = album.title,
                                                        circle = album.circle,
                                                        cv = album.cv,
                                                        coverUrl = album.coverUrl,
                                                        tags = album.tags,
                                                        tagDetails = album.tagDetails,
                                                        metadataLocale = album.metadataLocale,
                                                        ratingValue = album.ratingValue,
                                                        ratingCount = album.ratingCount,
                                                        releaseDate = album.releaseDate,
                                                        dlCount = album.dlCount,
                                                        priceJpy = album.priceJpy,
                                                        hasAsmrOne = album.hasAsmrOne,
                                                        description = album.description,
                                                        hasResolvedDlsiteInfo = true
                                                    )
                                                    navigator.openAlbumDetailByRj(album.rjCode.ifBlank { album.workId })
                                                },
                                                onSearchKeyword = ::submitMetaSearchKeyword,
                                                viewModel = hotListeningViewModel
                                            )
                                        }

                                        "playlist_system/favorites" -> {
                                            val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                                            val playlistDetailViewModel: com.asmr.player.ui.playlists.PlaylistDetailViewModel =
                                                hiltViewModel(activityViewModelStoreOwner)
                                            PrimaryPagePreloadEffect(primaryPagePreloadState, route, primaryRouteWarming) {
                                                playlistsViewModel.awaitInitialContent()
                                                playlistDetailViewModel.awaitInitialContent()
                                            }
                                            SystemPlaylistScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                scrollToTopSignal = favoritesScrollToTopSignal,
                                                playback = playerViewModel.playlistPlayback,
                                                onResumeAll = { items, startItem, positionMs ->
                                                    playerViewModel.resumePlaylistItems(items, startItem, positionMs)
                                                    if (startItem.isVideoPlaybackItem()) openNowPlaying()
                                                    else requestMiniPlayerPlayFeedback()
                                                },
                                                onPlayAll = { items, startItem ->
                                                    playerViewModel.playPlaylistItems(items, startItem)
                                                    if (startItem.isVideoPlaybackItem()) {
                                                        openNowPlaying()
                                                    } else {
                                                        requestMiniPlayerPlayFeedback()
                                                    }
                                                },
                                                viewModel = playlistsViewModel,
                                                playlistDetailViewModel = playlistDetailViewModel,
                                            )
                                        }

                                        "playlists" -> {
                                            val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                                            PrimaryPagePreloadEffect(primaryPagePreloadState, route, primaryRouteWarming) {
                                                playlistsViewModel.awaitInitialContent()
                                            }
                                            PlaylistsScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                scrollToTopSignal = playlistsScrollToTopSignal,
                                                onPlaylistClick = { playlist ->
                                                    val encoded = URLEncoder.encode(playlist.name, "UTF-8")
                                                    navController.navigateSingleTop("playlist/${playlist.id}/$encoded")
                                                },
                                                detailContent = { playlist ->
                                                    val detailViewModel: com.asmr.player.ui.playlists.PlaylistDetailViewModel =
                                                        hiltViewModel(activityViewModelStoreOwner, key = "tablet_playlist_detail")
                                                    PlaylistDetailScreen(
                                                        windowSizeClass = windowSizeClass,
                                                        playlistId = playlist.id,
                                                        title = playlist.name,
                                                        isActive = primaryRouteActive,
                                                        isDataActive = primaryRouteDataActive,
                                                        viewModel = detailViewModel,
                                                        playback = playerViewModel.playlistPlayback,
                                                        onPlayAll = { items, startItem ->
                                                            playerViewModel.playPlaylistItems(items, startItem)
                                                            if (startItem.isVideoPlaybackItem()) openNowPlaying() else requestMiniPlayerPlayFeedback()
                                                        },
                                                        onResumeAll = { items, startItem, positionMs ->
                                                            playerViewModel.resumePlaylistItems(items, startItem, positionMs)
                                                            if (startItem.isVideoPlaybackItem()) openNowPlaying() else requestMiniPlayerPlayFeedback()
                                                        },
                                                    )
                                                },
                                                viewModel = playlistsViewModel
                                            )
                                        }

                                        "groups" -> {
                                            val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(activityViewModelStoreOwner)
                                            PrimaryPagePreloadEffect(primaryPagePreloadState, route, primaryRouteWarming) {
                                                albumGroupsViewModel.awaitInitialContent()
                                            }
                                            com.asmr.player.ui.groups.AlbumGroupsScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                scrollToTopSignal = groupsScrollToTopSignal,
                                                cloud = cloudGroups,
                                                onOpenAsmrOneLogin = { navController.navigateSingleTop("asmr_one_login") },
                                                onCloudAlbumClick = { album ->
                                                    val workNo = album.rjCode.ifBlank { album.workId }.ifBlank { album.asmrOneWorkId?.toString().orEmpty() }
                                                    AlbumCoverHintStore.record(album.id, workNo, album.title, album.circle, album.coverUrl)
                                                    openAlbumDetailFromSearch(albumId = null, rj = workNo)
                                                },
                                                onGroupClick = { group ->
                                                    val encoded = encodeRouteArg(group.name)
                                                    navController.navigateSingleTop("group/${group.id}/$encoded")
                                                },
                                                detailContent = { group ->
                                                    val detailViewModel: com.asmr.player.ui.groups.AlbumGroupDetailViewModel =
                                                        hiltViewModel(activityViewModelStoreOwner, key = "tablet_group_detail")
                                                    com.asmr.player.ui.groups.AlbumGroupDetailScreen(
                                                        windowSizeClass = windowSizeClass,
                                                        groupId = group.id,
                                                        title = group.name,
                                                        isDataActive = primaryRouteDataActive,
                                                        viewModel = detailViewModel,
                                                        onPlayMediaItems = { items, startIndex ->
                                                            playerViewModel.playMediaItems(items, startIndex)
                                                            if (items.getOrNull(startIndex).isVideoPlaybackItem()) openNowPlaying() else requestMiniPlayerPlayFeedback()
                                                        },
                                                    )
                                                },
                                                viewModel = albumGroupsViewModel
                                            )
                                        }

                                        "settings" -> {
                                            PrimaryPagePreloadEffect(primaryPagePreloadState, route, primaryRouteWarming)
                                            SettingsScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                viewModel = settingsViewModel,
                                                libraryViewModel = libraryViewModel,
                                                scrollToTopSignal = settingsScrollToTopSignal,
                                                onHorizontalControlInteractionChanged = { active ->
                                                    primaryPagerScrollLocked = active
                                                },
                                                onDetailPageChanged = { visible ->
                                                    settingsDetailPageVisible = visible
                                                },
                                            )
                                        }

                                        "listening_calendar" -> {
                                            val listeningCalendarViewModel: com.asmr.player.ui.calendar.ListeningCalendarViewModel =
                                                hiltViewModel(activityViewModelStoreOwner)
                                            PrimaryPagePreloadEffect(primaryPagePreloadState, route, primaryRouteWarming) {
                                                listeningCalendarViewModel.awaitInitialContent()
                                            }
                                            com.asmr.player.ui.calendar.ListeningCalendarScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                onOpenDlsiteLogin = { navController.navigateSingleTop("dlsite_login") },
                                                onOpenAsmrOneLogin = { navController.navigateSingleTop("asmr_one_login") },
                                                onOpenAlbum = { session ->
                                                    AlbumCoverHintStore.record(
                                                        albumId = session.albumId.takeIf { it > 0L },
                                                        rjCode = session.rjCode,
                                                        title = session.title,
                                                        circle = session.circle,
                                                        cv = session.cv,
                                                        coverUrl = session.coverUrl,
                                                        tags = session.tags
                                                            .split(',')
                                                            .map { it.trim() }
                                                            .filter { it.isNotBlank() }
                                                    )
                                                    if (session.albumId > 0L) {
                                                        navigator.openAlbumDetail(albumId = session.albumId, rj = null)
                                                    } else if (session.rjCode.isNotBlank()) {
                                                        navigator.openAlbumDetailByRjStacked(session.rjCode)
                                                    }
                                                },
                                                viewModel = listeningCalendarViewModel
                                            )
                                        }

                                        }
                                    }
                                }
                            }
                            }
                            }

                        }
                    }
                }

                NavHost(
                                navController = navController,
                                startDestination = initialDestination,
                                enterTransition = {
                                    if (isAlbumDetailStackTransition(
                                            initialRoute = initialState.destination.route,
                                            targetRoute = targetState.destination.route
                                        ) || targetState.usesSecondaryPageSlideTransition()
                                    ) {
                                        secondaryPageEnterTransition()
                                    } else {
                                        EnterTransition.None
                                    }
                                },
                                exitTransition = { ExitTransition.None },
                                popEnterTransition = { EnterTransition.None },
                                popExitTransition = {
                                    if (isAlbumDetailStackTransition(
                                            initialRoute = initialState.destination.route,
                                            targetRoute = targetState.destination.route
                                        ) || initialState.usesSecondaryPageSlideTransition()
                                    ) {
                                        secondaryPagePopExitTransition()
                                    } else {
                                        ExitTransition.None
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = tabletRailInset)
                            ) {

                composable("library") {
                    Box(modifier = Modifier.fillMaxSize())
                }
                                composable("library_filter") {
                    SecondaryPageBackground(topPadding = secondaryPageTopPadding) {
                        LibraryFilterScreen(
                            onClose = { navController.popBackStack() },
                            viewModel = libraryViewModel
                        )
                    }
                }
                composable("search") { backStackEntry ->
                    val submittedKeyword by backStackEntry.savedStateHandle
                        .getStateFlow(SEARCH_ASSIST_RESULT_KEY, "")
                        .collectAsStateWithLifecycle()
                    val submittedOrderName by backStackEntry.savedStateHandle
                        .getStateFlow(SEARCH_ASSIST_RESULT_ORDER_KEY, SearchAssistSearchRequest().orderName)
                        .collectAsStateWithLifecycle()
                    val submittedPurchasedOnly by backStackEntry.savedStateHandle
                        .getStateFlow(SEARCH_ASSIST_RESULT_PURCHASED_ONLY_KEY, SearchAssistSearchRequest().purchasedOnly)
                        .collectAsStateWithLifecycle()
                    val submittedPresaleOnly by backStackEntry.savedStateHandle
                        .getStateFlow(SEARCH_ASSIST_RESULT_PRESALE_ONLY_KEY, SearchAssistSearchRequest().presaleOnly)
                        .collectAsStateWithLifecycle()
                    val submittedChineseTranslatedOnly by backStackEntry.savedStateHandle
                        .getStateFlow(
                            SEARCH_ASSIST_RESULT_CHINESE_TRANSLATED_ONLY_KEY,
                            SearchAssistSearchRequest().chineseTranslatedOnly
                        )
                        .collectAsStateWithLifecycle()
                    val submittedCollectedOnly by backStackEntry.savedStateHandle
                        .getStateFlow(SEARCH_ASSIST_RESULT_COLLECTED_ONLY_KEY, SearchAssistSearchRequest().collectedOnly)
                        .collectAsStateWithLifecycle()
                    val submittedCollectedSourceName by backStackEntry.savedStateHandle
                        .getStateFlow(SEARCH_ASSIST_RESULT_COLLECTED_SOURCE_KEY, SearchAssistSearchRequest().collectedSourceName)
                        .collectAsStateWithLifecycle()
                    val submittedHasSubtitle by backStackEntry.savedStateHandle
                        .getStateFlow(SEARCH_ASSIST_RESULT_HAS_SUBTITLE_KEY, SearchAssistSearchRequest().hasSubtitle)
                        .collectAsStateWithLifecycle()
                    val submittedAllAges by backStackEntry.savedStateHandle
                        .getStateFlow(SEARCH_ASSIST_RESULT_ALL_AGES_KEY, SearchAssistSearchRequest().allAges)
                        .collectAsStateWithLifecycle()
                    val submittedCollectedSortName by backStackEntry.savedStateHandle
                        .getStateFlow(
                            SEARCH_ASSIST_RESULT_COLLECTED_SORT_KEY,
                            SearchAssistSearchRequest().collectedSortName
                        )
                        .collectAsStateWithLifecycle()
                    val submittedLocale by backStackEntry.savedStateHandle
                        .getStateFlow(SEARCH_ASSIST_RESULT_LOCALE_KEY, SearchAssistSearchRequest().locale)
                        .collectAsStateWithLifecycle()
                    val submittedSignal by backStackEntry.savedStateHandle
                        .getStateFlow(SEARCH_ASSIST_RESULT_SIGNAL_KEY, 0L)
                        .collectAsStateWithLifecycle()

                    LaunchedEffect(
                        submittedSignal,
                        submittedKeyword,
                        submittedOrderName,
                        submittedPurchasedOnly,
                        submittedPresaleOnly,
                        submittedChineseTranslatedOnly,
                        submittedCollectedOnly,
                        submittedCollectedSourceName,
                        submittedHasSubtitle,
                        submittedAllAges,
                        submittedCollectedSortName,
                        submittedLocale
                    ) {
                        if (submittedSignal <= 0L) return@LaunchedEffect
                        submittedSearchKeyword = submittedKeyword
                        submittedSearchOrderName = submittedOrderName
                        submittedSearchPurchasedOnly = submittedPurchasedOnly
                        submittedSearchPresaleOnly = submittedPresaleOnly
                        submittedSearchChineseTranslatedOnly = submittedChineseTranslatedOnly
                        submittedSearchCollectedOnly = submittedCollectedOnly
                        submittedSearchCollectedSourceName = submittedCollectedSourceName
                        submittedSearchHasSubtitle = submittedHasSubtitle
                        submittedSearchAllAges = submittedAllAges
                        submittedSearchCollectedSortName = submittedCollectedSortName
                        submittedSearchLocale = submittedLocale
                        submittedSearchSignal = submittedSignal
                        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_KEY] = ""
                        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_ORDER_KEY] =
                            SearchAssistSearchRequest().orderName
                        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_PURCHASED_ONLY_KEY] =
                            SearchAssistSearchRequest().purchasedOnly
                        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_PRESALE_ONLY_KEY] =
                            SearchAssistSearchRequest().presaleOnly
                        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_CHINESE_TRANSLATED_ONLY_KEY] =
                            SearchAssistSearchRequest().chineseTranslatedOnly
                        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_COLLECTED_ONLY_KEY] =
                            SearchAssistSearchRequest().collectedOnly
                        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_COLLECTED_SOURCE_KEY] =
                            SearchAssistSearchRequest().collectedSourceName
                        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_HAS_SUBTITLE_KEY] =
                            SearchAssistSearchRequest().hasSubtitle
                        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_ALL_AGES_KEY] =
                            SearchAssistSearchRequest().allAges
                        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_COLLECTED_SORT_KEY] =
                            SearchAssistSearchRequest().collectedSortName
                        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_LOCALE_KEY] =
                            SearchAssistSearchRequest().locale
                        backStackEntry.savedStateHandle[SEARCH_ASSIST_RESULT_SIGNAL_KEY] = 0L
                    }

                    Box(modifier = Modifier.fillMaxSize())
                }
                composable(route = Routes.SearchAssist) { backStackEntry ->
                    SearchAssistPageBackground(
                        topPadding = if (headerBlur != null) searchAssistHeaderPadding else secondaryPageTopPadding,
                        blurState = headerBlur,
                        isActive = showSearchAssistBlur && navBackStackEntry?.id == backStackEntry.id,
                    ) {
                        SearchAssistScreen(
                            windowSizeClass = windowSizeClass,
                            initialRequest = searchAssistInitialRequest,
                            onSubmitSearch = ::submitSearchAssistRequest
                        )
                    }
                }
                composable(
                    route = Routes.SearchAssistPattern,
                    arguments = listOf(
                        navArgument("keyword") {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { backStackEntry ->
                    val initialKeyword = Uri.decode(
                        backStackEntry.arguments?.getString("keyword").orEmpty()
                    )
                    val initialRequest = if (initialKeyword.isBlank()) {
                        searchAssistInitialRequest
                    } else {
                        searchAssistInitialRequest.copy(keyword = initialKeyword)
                    }

                    SearchAssistPageBackground(
                        topPadding = if (headerBlur != null) searchAssistHeaderPadding else secondaryPageTopPadding,
                        blurState = headerBlur,
                        isActive = showSearchAssistBlur && navBackStackEntry?.id == backStackEntry.id,
                    ) {
                        SearchAssistScreen(
                            windowSizeClass = windowSizeClass,
                            initialRequest = initialRequest,
                            onSubmitSearch = ::submitSearchAssistRequest
                        )
                    }
                }
                composable("hot_listening") {
                    Box(modifier = Modifier.fillMaxSize())
                }
                composable(
                    route = Routes.AlbumDetailByRjPattern,
                    arguments = listOf(
                        navArgument("rj") { defaultValue = "" },
                        navArgument("initialTab") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("resourceSource") { type = NavType.StringType; nullable = true; defaultValue = null }
                    )
                ) { backStackEntry ->
                    val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                    val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(activityViewModelStoreOwner)
                    val rj = backStackEntry.arguments?.getString("rj").orEmpty()
                    AlbumDetailRouteFrame(
                        backStackEntry = backStackEntry,
                        previousBackStackEntry = navController.previousBackStackEntry,
                        stackPopTargetEntryId = albumDetailStackPopTargetEntryId,
                        onPopBackStack = { targetEntryId ->
                            albumDetailStackPopTargetEntryId = targetEntryId
                            navController.popBackStack()
                        },
                        onPageProgressReader = { reader ->
                            if (navController.currentBackStackEntry?.id == backStackEntry.id) {
                                albumDetailPageProgressReader = reader
                            }
                        },
                        onExitStateChanged = { albumDetailExitInProgress = it },
                        onEditRj = { currentRj ->
                            manualRjInput = currentRj
                            showManualRjDialog = true
                        }
                    ) { albumDetailViewModel, heroBlurLayerCache ->
                        AlbumDetailScreen(
                            windowSizeClass = windowSizeClass,
                            rjCode = rj,
                            initialTab = backStackEntry.arguments
                                ?.getString("initialTab")
                                .toAlbumDetailInitialTab(),
                            initialResourceSource = AlbumResourceSource.entries.firstOrNull {
                                it.name == backStackEntry.arguments?.getString("resourceSource")
                            },
                            onPlayTracks = { album, tracks, startTrack ->
                                scope.launch {
                                    if (playerViewModel.playTracksPrepared(album, tracks, startTrack)) {
                                        requestMiniPlayerPlayFeedback()
                                    }
                                }
                            },
                            onPlayMediaItems = { items, startIndex ->
                                playerViewModel.playMediaItems(items, startIndex)
                                val startItem = items.getOrNull(startIndex)
                                if (startItem.isVideoPlaybackItem()) {
                                    openNowPlaying()
                                } else if (startItem != null) {
                                    requestMiniPlayerPlayFeedback()
                                }
                            },
                            onAddToQueue = { album, track ->
                                playerViewModel.addTrackToQueue(album, track)
                            },
                            onAddMediaItemsToQueue = { items ->
                                playerViewModel.addMediaItemsToQueue(items)
                            },
                            onAddMediaItemsToFavorites = { items ->
                                playlistsViewModel.addItemsToFavoritesInBackground(items)
                            },
                            onOpenPlaylistPicker = { item ->
                                albumBatchPlaylistPickerRequest = BatchPlaylistPickerRequest(listOf(item))
                            },
                            onOpenDlsiteLogin = { navController.navigateSingleTop("dlsite_login") },
                            onOpenAsmrOneLogin = { navController.navigateSingleTop("asmr_one_login") },
                            onOpenAlbumByRj = { targetRj, work ->
                                AlbumCoverHintStore.record(
                                    albumId = null,
                                    rjCode = targetRj,
                                    title = work?.title,
                                    circle = null,
                                    coverUrl = resolveRecommendedWorkHeroCoverUrl(targetRj, work?.coverUrl)
                                )
                                navigator.openAlbumDetailByRjStacked(targetRj)
                            },
                            onSearchKeyword = ::submitMetaSearchKeyword,
                            playlistsViewModel = playlistsViewModel,
                            albumGroupsViewModel = albumGroupsViewModel,
                            settingsViewModel = settingsViewModel,
                            libraryViewModel = libraryViewModel,
                            heroBlurLayerCache = heroBlurLayerCache,
                            viewModel = albumDetailViewModel
                        )
                    }
                }
                composable(
                    route = Routes.AlbumDetailByIdPattern,
                    arguments = listOf(
                        navArgument("albumId") { type = NavType.LongType },
                        navArgument("rjCode") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("initialTab") { type = NavType.StringType; nullable = true; defaultValue = null }
                    )
                ) { backStackEntry ->
                    val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                    val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(activityViewModelStoreOwner)
                    val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
                    val rjCode = backStackEntry.arguments?.getString("rjCode")
                    AlbumDetailRouteFrame(
                        backStackEntry = backStackEntry,
                        previousBackStackEntry = navController.previousBackStackEntry,
                        stackPopTargetEntryId = albumDetailStackPopTargetEntryId,
                        onPopBackStack = { targetEntryId ->
                            albumDetailStackPopTargetEntryId = targetEntryId
                            navController.popBackStack()
                        },
                        onPageProgressReader = { reader ->
                            if (navController.currentBackStackEntry?.id == backStackEntry.id) {
                                albumDetailPageProgressReader = reader
                            }
                        },
                        onExitStateChanged = { albumDetailExitInProgress = it },
                        onLocalAlbumRemoved = { removed ->
                            playerViewModel.removeAlbumFromQueue(removed.albumId, removed.mediaIds)
                        },
                        onEditRj = { currentRj ->
                            manualRjInput = currentRj
                            showManualRjDialog = true
                        }
                    ) { albumDetailViewModel, heroBlurLayerCache ->
                        AlbumDetailScreen(
                            windowSizeClass = windowSizeClass,
                            albumId = albumId,
                            rjCode = rjCode,
                            initialTab = backStackEntry.arguments
                                ?.getString("initialTab")
                                .toAlbumDetailInitialTab(),
                            onPlayTracks = { album, tracks, startTrack ->
                                scope.launch {
                                    if (playerViewModel.playTracksPrepared(album, tracks, startTrack)) {
                                        requestMiniPlayerPlayFeedback()
                                    }
                                }
                            },
                            onPlayMediaItems = { items, startIndex ->
                                playerViewModel.playMediaItems(items, startIndex)
                                val startItem = items.getOrNull(startIndex)
                                if (startItem.isVideoPlaybackItem()) {
                                    openNowPlaying()
                                } else if (startItem != null) {
                                    requestMiniPlayerPlayFeedback()
                                }
                            },
                            onAddToQueue = { album, track ->
                                playerViewModel.addTrackToQueue(album, track)
                            },
                            onAddMediaItemsToQueue = { items ->
                                playerViewModel.addMediaItemsToQueue(items)
                            },
                            onAddMediaItemsToFavorites = { items ->
                                playlistsViewModel.addItemsToFavoritesInBackground(items)
                            },
                            onOpenPlaylistPicker = { item ->
                                albumBatchPlaylistPickerRequest = BatchPlaylistPickerRequest(listOf(item))
                            },
                            onOpenDlsiteLogin = { navController.navigateSingleTop("dlsite_login") },
                            onOpenAsmrOneLogin = { navController.navigateSingleTop("asmr_one_login") },
                            onOpenAlbumByRj = { targetRj, work ->
                                AlbumCoverHintStore.record(
                                    albumId = null,
                                    rjCode = targetRj,
                                    title = work?.title,
                                    circle = null,
                                    coverUrl = resolveRecommendedWorkHeroCoverUrl(targetRj, work?.coverUrl)
                                )
                                navigator.openAlbumDetailByRjStacked(targetRj)
                            },
                            onSearchKeyword = ::submitMetaSearchKeyword,
                            playlistsViewModel = playlistsViewModel,
                            albumGroupsViewModel = albumGroupsViewModel,
                            settingsViewModel = settingsViewModel,
                            libraryViewModel = libraryViewModel,
                            heroBlurLayerCache = heroBlurLayerCache,
                            viewModel = albumDetailViewModel
                        )
                    }
                }
                composable(
                    route = "album_detail_online/{rj}",
                    arguments = listOf(navArgument("rj") { defaultValue = "" })
                ) { backStackEntry ->
                    val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                    val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(activityViewModelStoreOwner)
                    val rj = backStackEntry.arguments?.getString("rj").orEmpty()
                    AlbumDetailRouteFrame(
                        backStackEntry = backStackEntry,
                        previousBackStackEntry = navController.previousBackStackEntry,
                        stackPopTargetEntryId = albumDetailStackPopTargetEntryId,
                        onPopBackStack = { targetEntryId ->
                            albumDetailStackPopTargetEntryId = targetEntryId
                            navController.popBackStack()
                        },
                        onPageProgressReader = { reader ->
                            if (navController.currentBackStackEntry?.id == backStackEntry.id) {
                                albumDetailPageProgressReader = reader
                            }
                        },
                        onExitStateChanged = { albumDetailExitInProgress = it },
                        onEditRj = { currentRj ->
                            manualRjInput = currentRj
                            showManualRjDialog = true
                        }
                    ) { albumDetailViewModel, heroBlurLayerCache ->
                        AlbumDetailScreen(
                            windowSizeClass = windowSizeClass,
                            rjCode = rj,
                            onPlayTracks = { album, tracks, startTrack ->
                                scope.launch {
                                    if (playerViewModel.playTracksPrepared(album, tracks, startTrack)) {
                                        requestMiniPlayerPlayFeedback()
                                    }
                                }
                            },
                            onPlayMediaItems = { items, startIndex ->
                                playerViewModel.playMediaItems(items, startIndex)
                                val startItem = items.getOrNull(startIndex)
                                if (startItem.isVideoPlaybackItem()) {
                                    openNowPlaying()
                                } else if (startItem != null) {
                                    requestMiniPlayerPlayFeedback()
                                }
                            },
                            onAddToQueue = { album, track ->
                                playerViewModel.addTrackToQueue(album, track)
                            },
                            onOpenPlaylistPicker = { item ->
                                albumBatchPlaylistPickerRequest = BatchPlaylistPickerRequest(listOf(item))
                            },
                            onOpenDlsiteLogin = { navController.navigateSingleTop("dlsite_login") },
                            onOpenAsmrOneLogin = { navController.navigateSingleTop("asmr_one_login") },
                            onOpenAlbumByRj = { targetRj, work ->
                                AlbumCoverHintStore.record(
                                    albumId = null,
                                    rjCode = targetRj,
                                    title = work?.title,
                                    circle = null,
                                    coverUrl = resolveRecommendedWorkHeroCoverUrl(targetRj, work?.coverUrl)
                                )
                                navigator.openAlbumDetailByRjStacked(targetRj)
                            },
                            onSearchKeyword = ::submitMetaSearchKeyword,
                            playlistsViewModel = playlistsViewModel,
                            albumGroupsViewModel = albumGroupsViewModel,
                            settingsViewModel = settingsViewModel,
                            libraryViewModel = libraryViewModel,
                            heroBlurLayerCache = heroBlurLayerCache,
                            viewModel = albumDetailViewModel
                        )
                    }
                }
                composable(
                    route = "album_detail_online/{source}/{workId}",
                    arguments = listOf(
                        navArgument("source") { defaultValue = SearchSource.DLSite.name },
                        navArgument("workId") { defaultValue = "" }
                    )
                ) { backStackEntry ->
                    val workId = backStackEntry.arguments?.getString("workId").orEmpty()
                    LaunchedEffect(workId) {
                        if (workId.isNotBlank()) {
                            navController.navigate("album_detail_online/$workId") {
                                launchSingleTop = true
                                popUpTo("album_detail_online/{source}/{workId}") { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }
                }
                composable("playlists") {
                    Box(modifier = Modifier.fillMaxSize())
                }
                composable("groups") {
                    Box(modifier = Modifier.fillMaxSize())
                }
                composable(
                    route = "group/{groupId}/{groupName}",
                    arguments = listOf(
                        navArgument("groupId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("groupName") { defaultValue = "" }
                    )
                ) { backStackEntry ->
                    val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
                    val groupName = decodeRouteArg(backStackEntry.arguments?.getString("groupName").orEmpty())
                    SecondaryPageBackground(topPadding = secondaryPageTopPadding) {
                        com.asmr.player.ui.groups.AlbumGroupDetailScreen(
                            windowSizeClass = windowSizeClass,
                            groupId = groupId,
                            title = groupName,
                            onPlayMediaItems = { items, startIndex ->
                                playerViewModel.playMediaItems(items, startIndex)
                                val startItem = items.getOrNull(startIndex)
                                if (startItem.isVideoPlaybackItem()) {
                                    openNowPlaying()
                                } else if (startItem != null) {
                                    requestMiniPlayerPlayFeedback()
                                }
                            }
                        )
                    }
                }
                composable(
                    route = "playlist/{playlistId}/{playlistName}",
                    arguments = listOf(
                        navArgument("playlistId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("playlistName") { defaultValue = "" }
                    )
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                    val playlistName = decodeRouteArg(backStackEntry.arguments?.getString("playlistName").orEmpty())
                    SecondaryPageBackground(topPadding = secondaryPageTopPadding) {
                        PlaylistDetailScreen(
                            windowSizeClass = windowSizeClass,
                            playlistId = playlistId,
                            title = playlistName,
                            playback = playerViewModel.playlistPlayback,
                            onResumeAll = { items, startItem, positionMs ->
                                playerViewModel.resumePlaylistItems(items, startItem, positionMs)
                                if (startItem.isVideoPlaybackItem()) openNowPlaying()
                                else requestMiniPlayerPlayFeedback()
                            },
                            onPlayAll = { items, startItem ->
                                playerViewModel.playPlaylistItems(items, startItem)
                                if (startItem.isVideoPlaybackItem()) {
                                    openNowPlaying()
                                } else {
                                    requestMiniPlayerPlayFeedback()
                                }
                            }
                        )
                    }
                }
                composable("playlist_system/{type}") { backStackEntry ->
                    val type = backStackEntry.arguments?.getString("type").orEmpty()
                    if (type == "favorites") {
                        Box(modifier = Modifier.fillMaxSize())
                    } else {
                        val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                        SecondaryPageBackground(topPadding = secondaryPageTopPadding) {
                            SystemPlaylistScreen(
                                windowSizeClass = windowSizeClass,
                                playback = playerViewModel.playlistPlayback,
                                onResumeAll = { items, startItem, positionMs ->
                                    playerViewModel.resumePlaylistItems(items, startItem, positionMs)
                                    if (startItem.isVideoPlaybackItem()) openNowPlaying()
                                    else requestMiniPlayerPlayFeedback()
                                },
                                onPlayAll = { items, startItem ->
                                    playerViewModel.playPlaylistItems(items, startItem)
                                    if (startItem.isVideoPlaybackItem()) {
                                        openNowPlaying()
                                    } else {
                                        requestMiniPlayerPlayFeedback()
                                    }
                                },
                                viewModel = playlistsViewModel
                            )
                        }
                    }
                }
                composable("settings") {
                    Box(modifier = Modifier.fillMaxSize())
                }
                composable("downloads") {
                    SecondaryPageBackground(topPadding = secondaryPageTopPadding) {
                        DownloadsScreen(
                            windowSizeClass = windowSizeClass,
                            scrollToTopSignal = downloadsScrollToTopSignal,
                            viewModel = downloadsViewModel
                        )
                    }
                }
                composable("dlsite_login") {
                    val dlsiteLoginViewModel: DlsiteLoginViewModel = hiltViewModel(activityViewModelStoreOwner)
                    SecondaryPageBackground(topPadding = secondaryPageTopPadding) {
                        DlsiteLoginScreen(
                            windowSizeClass = windowSizeClass,
                            onDone = { navController.popBackStack() },
                            viewModel = dlsiteLoginViewModel
                        )
                    }
                }
                composable("asmr_one_login") {
                    SecondaryPageBackground(topPadding = secondaryPageTopPadding) {
                        com.asmr.player.ui.asmrone.AsmrOneLoginScreen(
                            windowSizeClass = windowSizeClass,
                            onDone = { navController.popBackStack() },
                        )
                    }
                }
                composable("listening_calendar") {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }

                if (showSearchAssistBlur) {
                    mainHeader()
                }

                    if (blockNavTouches || albumDetailExitInProgress) {
                        if (isAlbumDetailRoute) {
                            val albumDetailTopBarTouchPassThroughHeight =
                                StableWindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                                    AlbumDetailTopBarTouchPassThroughHeight
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(albumDetailTopBarTouchPassThroughHeight)
                                    .padding(start = AlbumDetailBackTouchPassThroughWidth)
                                    .pointerInteropFilter { true }
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = albumDetailTopBarTouchPassThroughHeight)
                                    .pointerInteropFilter { true }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInteropFilter { true }
                            )
                        }
                    }
            if (showManualRjDialog && navBackStackEntry != null &&
                (currentRoute?.startsWith("album_detail/{albumId}") == true || currentRoute?.startsWith("album_detail/") == true)
            ) {
                val albumDetailViewModel: AlbumDetailViewModel = hiltViewModel(navBackStackEntry!!)
                FlatTextFieldDialog(
                    onDismissRequest = { showManualRjDialog = false },
                    message = appString(R.string.ui_please_enter_the_dlsite_work_number_support_rj),
                    value = manualRjInput,
                    onValueChange = { manualRjInput = it },
                    placeholder = appString(R.string.ui_work_number_such_as_bj02370869),
                    confirmText = appString(R.string.ui_synchronous),
                    confirmEnabled = manualRjInput.trim().isNotBlank(),
                    onConfirm = {
                        showManualRjDialog = false
                        albumDetailViewModel.manualSetRjAndSync(manualRjInput.trim())
                    },
                )
            }

            onlineAudioDownloadConfirmation?.let { files ->
                JapaneseAsmrOnlineDownloadDialog(
                    missingFileCount = files.size,
                    onConfirm = downloadsViewModel::confirmOnlineAudioDownload,
                    onCancel = downloadsViewModel::cancelOnlineAudioDownload,
                )
            }

            cloudSyncSelectionDialogState?.let { dialogState ->
                val ignoreAllHandler = if (bulkProgress != null) {
                    { libraryViewModel.ignoreAllCloudSyncSelections() }
                } else {
                    null
                }
                CloudSyncSelectionDialog(
                    state = dialogState,
                    onSelect = libraryViewModel::confirmCloudSyncSelection,
                    onCancel = libraryViewModel::cancelCloudSyncSelection,
                    onIgnoreAll = ignoreAllHandler
                )
            }

        if (useTabletRail && !nowPlayingVisible) {
            TabletNavigationRail(
                activeRoute = visualPrimaryRoute,
                onNavigate = { route ->
                    if (route == visualPrimaryRoute && currentPrimaryRoute != null) triggerPrimaryRouteScrollToTop(route)
                    else openPrimaryRoute(route)
                },
                modifier = Modifier.align(Alignment.CenterStart),
            )
            if (miniPlayerVisible) {
                MiniPlayer(
                    displayMode = MiniPlayerDisplayMode.Expanded,
                    onDisplayModeChange = {},
                    allowCollapse = false,
                    onOpenNowPlaying = openNowPlaying,
                    onOpenQueue = onShowQueue,
                    playFeedbackSignal = miniPlayerPlayFeedbackSignal,
                    largeLayout = true,
                    viewModel = playerViewModel,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(start = tabletRailInset + 16.dp, end = 16.dp, bottom = 16.dp + navigationBarBottomPadding)
                        .fillMaxWidth(),
                )
            }
        }

        if (bottomChromeVisible && !useTabletRail) {
            Box(modifier = Modifier.fillMaxSize()) {
                val bottomChromeHorizontalPadding = if (useLargeBottomChrome) 16.dp else 12.dp
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .graphicsLayer { clip = false }
                        .padding(
                            start = bottomChromeHorizontalPadding,
                            end = bottomChromeHorizontalPadding,
                            bottom = bottomChromeBottomPadding
                        )
                        .fillMaxWidth()
                ) {
                    PrimaryBottomChrome(
                        activeRoute = visualPrimaryRoute,
                        pagerState = primaryPagerState,
                        pagerRoutes = primaryPagerRoutes,
                        fallbackRoute = activePrimaryRoute,
                        lockedRoute = pendingPrimaryNavigationRoute,
                        miniPlayerVisible = miniPlayerVisible,
                        miniPlayerDisplayMode = miniPlayerDisplayMode,
                        miniPlayerPlayFeedbackSignal = miniPlayerPlayFeedbackSignal,
                        largeLayout = useLargeBottomChrome,
                        navItems = bottomNavItems,
                        onMiniPlayerDisplayModeChange = { nextMode ->
                            miniPlayerDisplayMode = nextMode
                            scope.launch { settingsDataStore.setMiniPlayerDisplayMode(nextMode.name) }
                        },
                        onOpenNowPlaying = {
                            if (!nowPlayingVisible) {
                                openNowPlaying()
                            }
                        },
                        onOpenQueue = onShowQueue,
                        onNavigate = { route ->
                            if (pendingPrimaryNavigationRoute == null && shouldTriggerPrimaryRouteScrollToTop(
                                    requestedRoute = route,
                                    visualPrimaryRoute = visualPrimaryRoute,
                                    activePrimaryRoute = activePrimaryRoute,
                                    currentPrimaryRoute = currentPrimaryRoute
                                )) {
                                triggerPrimaryRouteScrollToTop(route)
                                return@PrimaryBottomChrome
                            }
                            openPrimaryRoute(route)
                        }
                    )
                }
            }
        }

        if (nowPlayingVisible) {
            val playerBackdropColors = remember(colorScheme, playerBackgroundMode, coverBackgroundEnabled) {
                playerSurfaceColors(colorScheme, playerBackgroundMode, coverBackgroundEnabled)
            }
            val backdropIsPlaying by remember(playerViewModel) {
                playerViewModel.playback.map { it.isPlaying }.distinctUntilChanged()
            }.collectAsStateWithLifecycle(initialValue = false)
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInteropFilter { true }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = nowPlayingBackdropAlpha }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(playerBackdropColors.background)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = nowPlayingBackdropAlpha }
                ) {
                    PlayerSharedBackdrop(
                        mediaItem = sharedPlayerItem,
                        backgroundMode = playerBackgroundMode,
                        coverEnabled = coverBackgroundEnabled,
                        clarity = coverBackgroundClarity,
                        artworkAlignment = sharedPlayerBackdropAlignment,
                        isPlaying = backdropIsPlaying,
                        isActive = nowPlayingBackdropActive,
                        dynamicEnabled = dynamicFlowingBackgroundEnabled
                    )
                }
                NowPlayingScreen(
                    hardwareVolumeEventTick = nowPlayingVolumeEventTick,
                    onInlineVolumeControlVisibilityChanged = { nowPlayingUsesInlineVolumeControl = it },
                    onEqualizerVisibilityChanged = { nowPlayingEqualizerVisible = it },
                    onVideoFullscreenChanged = { nowPlayingVideoFullscreen = it },
                    onBack = closeNowPlaying,
                    onRouteExitStarted = { exitDurationMs ->
                        nowPlayingBackdropExitDurationMs = exitDurationMs
                        if (isPhone && isLandscape) {
                            nowPlayingPortraitExitPending = true
                            nowPlayingBackdropActive = true
                        } else {
                            nowPlayingBackdropActive = false
                        }
                    },
                    onShowQueue = onShowQueue,
                    onShowSleepTimer = onShowSleepTimer,
                    onOpenPlaylistPicker = { item ->
                        nowPlayingPlaylistPickerRequest = PlaylistPickerRequest(items = listOf(item))
                    },
                    viewModel = playerViewModel,
                    playerBackgroundMode = playerBackgroundMode,
                    coverBackgroundEnabled = coverBackgroundEnabled,
                    dynamicFlowingBackgroundEnabled = dynamicFlowingBackgroundEnabled,
                    coverBackgroundClarity = coverBackgroundClarity,
                    coverPreviewMode = coverPreviewMode,
                    nowPlayingHomeLayoutMode = nowPlayingHomeLayoutMode,
                    nowPlayingHomeLayoutHintDismissed = nowPlayingHomeLayoutHintDismissed,
                    onNowPlayingHomeLayoutHintShown = {
                        scope.launch { settingsDataStore.setNowPlayingHomeLayoutHintDismissed() }
                    },
                    onNowPlayingHomeLayoutModeChange = { mode ->
                        scope.launch {
                            settingsDataStore.setNowPlayingHomeLayoutMode(mode, dismissHint = true)
                        }
                    },
                    nowPlayingLyricsSettings = nowPlayingLyricsSettings,
                    lyricsPageSettings = lyricsPageSettings,
                    audioOutputRouteKind = audioOutputRouteKind,
                    warningSessionState = appVolumeWarningSessionState,
                    renderBackdrop = false,
                    sharedArtworkAlignment = sharedPlayerBackdropAlignment,
                    sharedCoverDragPreviewState = sharedCoverDragPreviewState
                )
                nowPlayingPlaylistPickerRequest?.let { request ->
                    val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                    RoundedTopSheet(onDismissRequest = { nowPlayingPlaylistPickerRequest = null }) {
                        PlaylistPickerScreen(
                            windowSizeClass = windowSizeClass,
                            items = request.items,
                            onBack = { nowPlayingPlaylistPickerRequest = null },
                            embeddedInDialog = true,
                            viewModel = playlistsViewModel
                        )
                    }
                }
                albumBatchPlaylistPickerRequest?.let { request ->
                    val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                    RoundedTopSheet(onDismissRequest = { albumBatchPlaylistPickerRequest = null }) {
                        PlaylistPickerScreen(
                            windowSizeClass = windowSizeClass,
                            items = request.items,
                            onBack = { albumBatchPlaylistPickerRequest = null },
                            embeddedInDialog = true,
                            viewModel = playlistsViewModel
                        )
                    }
                }
            }
        }

        if (!nowPlayingVisible) {
            albumBatchPlaylistPickerRequest?.let { request ->
                val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                RoundedTopSheet(onDismissRequest = { albumBatchPlaylistPickerRequest = null }) {
                    PlaylistPickerScreen(
                        windowSizeClass = windowSizeClass,
                        items = request.items,
                        onBack = { albumBatchPlaylistPickerRequest = null },
                        embeddedInDialog = true,
                        viewModel = playlistsViewModel
                    )
                }
            }
        }

        libraryGroupPickerAlbumId?.let { albumId ->
            val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(activityViewModelStoreOwner)
            RoundedTopSheet(onDismissRequest = { libraryGroupPickerAlbumId = null }) {
                com.asmr.player.ui.groups.AlbumGroupPickerScreen(
                    windowSizeClass = windowSizeClass,
                    albumId = albumId,
                    onBack = { libraryGroupPickerAlbumId = null },
                    embeddedInDialog = true,
                    viewModel = albumGroupsViewModel
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (showHardwareVolumeOverlay) {
                DismissOutsideBoundsOverlay(
                    targetBoundsInRoot = hardwareVolumeOverlayBounds,
                    onDismiss = {
                        showHardwareVolumeOverlay = false
                        hardwareVolumeOverlayBounds = null
                    }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 18.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                AnimatedVisibility(
                    visible = showHardwareVolumeOverlay,
                    enter = fadeIn(animationSpec = tween(140)) + slideInHorizontally(animationSpec = tween(180)) { it / 3 },
                    exit = fadeOut(animationSpec = tween(160)) + slideOutHorizontally(animationSpec = tween(180)) { it / 3 }
                ) {
                    HardwareVolumeOverlay(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            hardwareVolumeOverlayBounds = coordinates.boundsInRoot()
                        },
                        volumePercent = appVolumePercent,
                        audioOutputRouteKind = audioOutputRouteKind,
                        onVolumeChange = {
                            playerViewModel.setAppVolumePercent(it)
                            hardwareVolumeOverlayHoldTick += 1L
                        },
                        onToggleMute = {
                            if (appVolumePercent > 0) {
                                playerViewModel.setAppVolumePercent(0)
                            } else {
                                playerViewModel.setAppVolumePercent(
                                    lastNonZeroAppVolumePercent.coerceAtLeast(AppVolume.StepPercent)
                                )
                            }
                            hardwareVolumeOverlayHoldTick += 1L
                        },
                        onInteractionActiveChanged = { active ->
                            hardwareVolumeOverlayInteracting = active
                            if (!active) {
                                hardwareVolumeOverlayHoldTick += 1L
                            }
                        },
                        warningSessionState = appVolumeWarningSessionState
                    )
                }
            }
        }

        val automaticUpdateAvailable = (updateState as? AppUpdateState.UpdateAvailable)
            ?.takeIf { it.source == UpdateCheckSource.Automatic && !automaticUpdateDialogDismissed }

        ClipboardRjNavigationPrompt(
            enabled = !forceImmersive && automaticUpdateAvailable == null,
            settingsDataStore = settingsDataStore,
            onNavigate = { rjCode ->
                closeNowPlaying()
                navigator.openAlbumDetailByRjStacked(rjCode)
            }
        )

        automaticUpdateAvailable?.let { available ->
            val release = available.release
            FlatActionDialog(
                message = appString(R.string.ui_new_version_found, release.tagName),
                onDismissRequest = { automaticUpdateDialogDismissed = true },
                actions = listOf(
                    FlatDialogAction(
                        text = appString(R.string.ui_update_now),
                        tone = FlatDialogActionTone.Primary,
                        onClick = {
                            automaticUpdateDialogDismissed = true
                            automaticUpdateInstallRequested = true
                            settingsViewModel.downloadLatestApk()
                            messageManager.showInfo(appString(R.string.ui_start_downloading_updates))
                        }
                    ),
                    FlatDialogAction(
                        text = appString(R.string.ui_don_t_remind_again),
                        tone = FlatDialogActionTone.Danger,
                        onClick = {
                            automaticUpdateDialogDismissed = true
                            settingsViewModel.disableAutoUpdateCheck()
                            messageManager.showInfo(appString(R.string.ui_automatically_checking_for_updates_on_startup_is_turned))
                        }
                    ),
                    FlatDialogAction(
                        text = appString(R.string.ui_details),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_github),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        onClick = {
                            automaticUpdateDialogDismissed = true
                            if (!openUpdateReleasePage(context, release)) {
                                messageManager.showError(appString(R.string.ui_unable_to_open_github_releases_page))
                            }
                        }
                    )
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = appString(R.string.ui_current_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.textSecondary
                    )
                    if (release.title.isNotBlank() && release.title != release.tagName) {
                        Text(
                            text = release.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (release.apkName.isNotBlank()) {
                        Text(
                            text = appString(R.string.ui_installation_package, release.apkName),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

}

}

