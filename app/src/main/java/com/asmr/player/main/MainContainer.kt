package com.asmr.player

import android.os.Bundle
import android.view.KeyEvent
import android.view.Choreographer
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CloudDownload
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
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
import com.asmr.player.ui.library.LibraryFilterScreen
import com.asmr.player.ui.library.LibraryScreen
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.ui.library.BulkPhase
import com.asmr.player.data.remote.scraper.resolveRecommendedWorkCoverUrl
import com.asmr.player.performance.UiFrameWorkCoordinator
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
import com.asmr.player.ui.hotlistening.HotListeningScreen
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
import com.asmr.player.ui.common.EdgeToEdgeFullHeightSheet
import com.asmr.player.ui.common.EaraTopBarContainer
import com.asmr.player.ui.common.EaraMainTopBarHeight
import com.asmr.player.ui.common.EaraTopBarIconButton
import com.asmr.player.ui.common.resolveMainPageBackgroundColor
import com.asmr.player.ui.common.glassMenu
import com.asmr.player.ui.drawer.DrawerStatusViewModel
import com.asmr.player.ui.drawer.SiteStatus
import com.asmr.player.ui.drawer.SiteStatusType
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
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import com.asmr.player.ui.player.QueueSheetContent
import com.asmr.player.ui.player.SleepTimerSheetContent
import com.asmr.player.ui.player.MiniPlayerDisplayMode

import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.NowPlayingHomeLayoutMode
import com.asmr.player.data.settings.NowPlayingLyricsSettings
import com.asmr.player.util.MessageManager
import com.asmr.player.ui.common.StableWindowInsets
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
import com.asmr.player.ui.update.AppUpdateInstallResult
import com.asmr.player.ui.update.launchDownloadedApkInstall
import com.asmr.player.ui.update.openUpdateReleasePage
import com.asmr.player.ui.common.AppVolumeHearingWarningDialog
import com.asmr.player.ui.common.AppVolumeWarningSessionState
import com.asmr.player.ui.common.rememberAppVolumeWarningSessionState
import com.asmr.player.ui.common.rememberCurrentAudioOutputRouteKind
import com.asmr.player.ui.common.rememberProtectedAppVolumeChangeState
import com.asmr.player.ui.common.AudioOutputRouteIcon
import com.asmr.player.ui.common.DismissOutsideBoundsOverlay
import com.asmr.player.service.AudioOutputRouteKind
import com.asmr.player.service.PlaybackService
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.media3.common.MediaItem
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.playback.AppVolume
import com.asmr.player.ui.common.AppVolumeVerticalSlider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    onPageOffsetReader: (() -> Float) -> Unit,
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
    val pageOffsetReader = remember(pageOffsetProgress, rootView) {
        {
            pageOffsetProgress.value * rootView.width.toFloat().coerceAtLeast(1f)
        }
    }
    SideEffect {
        onPageOffsetReader(pageOffsetReader)
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
                            contentDescription = "手动输入作品编号",
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding)
            .background(pageBackgroundColor)
    ) {
        content()
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
        miniPlayerVisible = miniPlayerVisible,
        miniPlayerDisplayMode = miniPlayerDisplayMode,
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
    recentAlbumsPanelExpandedInitial: Boolean,
    startRouteFromIntent: String?,
    onShowQueue: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onContentReady: () -> Unit,
    showMiniPlayerBar: Boolean,
    coverBackgroundEnabled: Boolean,
    coverBackgroundClarity: Float,
    coverPreviewMode: CoverPreviewMode,
    nowPlayingHomeLayoutMode: NowPlayingHomeLayoutMode,
    nowPlayingHomeLayoutHintDismissed: Boolean,
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
    var albumDetailPageOffsetReader by remember { mutableStateOf<(() -> Float)?>(null) }
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
            albumDetailPageOffsetReader = null
        }
    }
    val bottomNavItems = remember { bottomChromeNavItems() }
    val storedMiniPlayerDisplayMode by settingsDataStore.miniPlayerDisplayMode.collectAsStateWithLifecycle(
        initialValue = MiniPlayerDisplayMode.CoverOnly.name
    )
    var miniPlayerDisplayMode by rememberSaveable { mutableStateOf(MiniPlayerDisplayMode.CoverOnly) }
    val primaryPagerRoutes = remember(bottomNavItems) { bottomNavItems.map { it.route } }
    val primaryPagerBeyondBoundsPageCount = remember(primaryPagerRoutes) {
        resolvePrimaryPagerBeyondBoundsPageCount(primaryPagerRoutes.size)
    }
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val downloadsViewModel: DownloadsViewModel = hiltViewModel(activityViewModelStoreOwner)
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
    val drawerStatusViewModel: DrawerStatusViewModel = hiltViewModel(activityViewModelStoreOwner)
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
    var hotListeningScrollToTopSignal by remember { mutableLongStateOf(0L) }
    val appVolumeWarningSessionState = rememberAppVolumeWarningSessionState()
    val audioOutputRouteKind = rememberCurrentAudioOutputRouteKind()
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // 使用 smallestScreenWidthDp 判定是否为手机 (一般 < 600dp 为手机)
    val isPhone = configuration.smallestScreenWidthDp < 600
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
    val videoOutputEnabled = shouldKeepVideoOutputEnabled(
        currentItemIsVideo = sharedPlayerIsVideo,
        miniPlayerEnabled = showMiniPlayerBar,
        nowPlayingVisible = nowPlayingVisible
    )
    DisposableEffect(playerViewModel, videoOutputEnabled) {
        playerViewModel.setVideoOutputEnabled(videoOutputEnabled)
        onDispose {
            if (videoOutputEnabled) playerViewModel.setVideoOutputEnabled(false)
        }
    }
    val sharedUseDragPreview = playerBackdropVisible &&
        coverBackgroundEnabled &&
        coverPreviewMode == CoverPreviewMode.Drag &&
        !sharedPlayerIsVideo
    val sharedUseMotionPreview = playerBackdropVisible &&
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
    val pendingPrimaryNavigationRouteState = rememberUpdatedState(pendingPrimaryNavigationRoute)
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
            primaryNavigationJob = scope.launch {
                var completed = false
                try {
                    primaryPagerState.stopScroll(MutatePriority.PreventUserInput)
                    val currentPage = primaryPagerState.currentPage
                    resolvePrimaryPagerApproachPage(
                        currentPage = currentPage,
                        targetPage = targetPage
                    )?.let { approachPage ->
                        primaryPagerState.scrollToPage(approachPage)
                    }
                    primaryPagerState.animateScrollToPage(targetPage)
                    if (currentPrimaryRouteState.value != route) {
                        navController.navigatePrimaryRoute(route)
                    }
                    completed = true
                } finally {
                    if (primaryNavigationRequestId == requestId) {
                        primaryNavigationJob = null
                        if (!completed && pendingPrimaryNavigationRouteState.value == route) {
                            pendingPrimaryNavigationRoute = null
                        }
                    }
                }
            }
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

    fun openAlbumDetailFromSearch(albumId: Long?, rj: String?, preferDlsitePlay: Boolean = false) {
        val seq = ++pendingDetailNavigationSeq
        pendingDetailNavigation = true
        cancelPendingDetailNavigation = false
        navigator.openAlbumDetail(albumId = albumId, rj = rj, preferDlsitePlay = preferDlsitePlay)
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
                messageManager.showInfo("正在打开系统安装器")
            }
            AppUpdateInstallResult.PermissionRequired -> {
                pendingAutomaticInstallPath = apkPath
                messageManager.showInfo("请允许 Eara 安装未知来源应用后继续安装")
            }
            AppUpdateInstallResult.FileInvalid -> {
                pendingAutomaticInstallPath = null
                messageManager.showError("下载文件无效，请重新下载")
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
                if (pendingPrimaryNavigationRouteState.value != null) return@collect
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

    LaunchedEffect(currentPrimaryRoute, hasPreviousBackStackEntry, nowPlayingVisible, drawerState.isOpen) {
        if (currentPrimaryRoute != Routes.Library || hasPreviousBackStackEntry || nowPlayingVisible || drawerState.isOpen) {
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
    val drawerContainerColor = if (colorScheme.isDark) Color(0xFF121212) else Color.White

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

    // 普通播放页保持原方向策略，仅视频全屏时锁定横屏。
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

    BackHandler(drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(pendingDetailNavigation && currentRoute == Routes.Search) {
        pendingDetailNavigation = false
        cancelPendingDetailNavigation = true
    }

    BackHandler(
        enabled = currentPrimaryRoute == Routes.Library &&
            !hasPreviousBackStackEntry &&
            !drawerState.isOpen &&
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
            messageManager.showInfo("再按一次返回退出应用")
        }
    }

    val drawerGesturesEnabled = false

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .glassMenu(
                        shape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
                        baseColor = drawerContainerColor,
                        elevation = if (colorScheme.isDark) 0.dp else 6.dp,
                        isDark = colorScheme.isDark
                    )
            ) {
                ModalDrawerSheet(
                    drawerContainerColor = Color.Transparent,
                    drawerContentColor = colorScheme.onSurface,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val navItems = listOf(
                        Triple(Icons.Rounded.Home, "本地库", "library"),
                        Triple(Icons.Rounded.Search, "在线搜索", "search"),
                        Triple(Icons.Rounded.Favorite, "我的收藏", "playlist_system/favorites"),
                        Triple(Icons.AutoMirrored.Rounded.QueueMusic, "我的列表", "playlists"),
                        Triple(Icons.Rounded.Folder, "我的分组", "groups"),
                        Triple(Icons.Rounded.Sync, "任务管理", "downloads"),
                        Triple(Icons.Rounded.Route, "ASMR 看板", "listening_calendar"),
                        Triple(Icons.Rounded.Settings, "设置", "settings")
                    )

                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(46.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                flingBehavior = rememberCalmScrollableFlingBehavior(),
                                contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
                            ) {
                                items(navItems, key = { it.third }) { (icon, label, route) ->
                                    val isAlbumDetailFromSearch =
                                        currentRoute?.startsWith("album_detail_rj") == true ||
                                            currentRoute?.startsWith("album_detail_online") == true
                                    val isAlbumDetailFromLibrary =
                                        currentRoute?.startsWith("album_detail/") == true &&
                                            !currentRoute.startsWith("album_detail_rj")
                                    val isSelected = when (route) {
                                        "library" -> currentRoute == route || isAlbumDetailFromLibrary
                                        "search" -> currentRoute == route || isAlbumDetailFromSearch
                                        "groups" -> currentRoute == route ||
                                            currentRoute?.startsWith("group/") == true ||
                                            currentRoute?.startsWith("group_picker") == true
                                        "playlist_system/favorites" -> {
                                            currentRoute == "playlist_system/{type}" &&
                                                navBackStackEntry?.arguments?.getString("type") == "favorites"
                                        }
                                        else -> currentRoute == route
                                    }
                                    DrawerNavCardItem(
                                        icon = icon,
                                        label = label,
                                        selected = isSelected,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        onClick = { openPrimaryRoute(route) }
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(18.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(drawerContainerColor, Color.Transparent)
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, drawerContainerColor)
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        DrawerSiteStatusFooter(drawerStatusViewModel, modifier = Modifier.padding(horizontal = 18.dp))
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }
            }
        }
    ) {
        val miniPlayerVisible = showMiniPlayerBar &&
            hasCurrentMediaItem &&
            !nowPlayingVisible
        val bottomChromeVisible = !nowPlayingVisible
        val rightPanelExpandedFromStore by settingsDataStore.recentAlbumsPanelExpanded
            .collectAsStateWithLifecycle(initialValue = recentAlbumsPanelExpandedInitial)
        val rightPanelExpandedState = remember(settingsDataStore, scope, recentAlbumsPanelExpandedInitial) {
            PersistedBooleanState(initial = recentAlbumsPanelExpandedInitial) { expanded ->
                scope.launch { settingsDataStore.setRecentAlbumsPanelExpanded(expanded) }
            }
        }
        LaunchedEffect(rightPanelExpandedFromStore) {
            rightPanelExpandedState.updateFromStore(rightPanelExpandedFromStore)
        }
        // 专辑详情始终覆盖在主页面之上；让底层顶栏/页面 active 标记在整个详情生命周期内
        // 保持稳定，退出时只更新必要的详情页位移和裁剪，不在同一帧重建整套主页面 chrome。
        val currentScreenIsPrimary = currentPrimaryRoute != null ||
            isAlbumDetailRoute || albumDetailExitInProgress
        val showBackButton = !currentScreenIsPrimary
        val showPrimaryBrand = currentScreenIsPrimary
        val hasOverlayRoute = currentPrimaryRoute == null && !albumDetailExitInProgress
        val albumDetailTransitionActive = isAlbumDetailRoute || albumDetailExitInProgress
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
        val useLargeBottomChrome = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact && !isPhone
        val navigationBarBottomPadding = StableWindowInsets.navigationBars
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()
        val bottomChromeBottomPadding = 24.dp + navigationBarBottomPadding
        val bottomOverlayPadding = bottomChromeOverlayHeight(useLargeBottomChrome) + navigationBarBottomPadding
        var secondaryPageTopPadding by remember { mutableStateOf(0.dp) }
        CompositionLocalProvider(
            LocalBottomOverlayPadding provides bottomOverlayPadding,
            LocalRightPanelExpandedState provides rightPanelExpandedState
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
                                albumDetailPageOffsetReader?.invoke()?.coerceIn(0f, width) ?: width
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
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        containerColor = Color.Transparent,
                        contentColor = colorScheme.onBackground,
                        topBar = {
                            Box {
                                EaraTopBarContainer {
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
                                                    resolvedTitleRoute == "library" -> "本地库"
                                                    resolvedTitleRoute == "library_filter" -> "筛选"
                                                    resolvedTitleRoute == "search" -> "在线搜索"
                                                    resolvedTitleRoute == Routes.SearchAssist -> "在线搜索"
                                                    resolvedTitleRoute == Routes.SearchAssistPattern -> "在线搜索"
                                                    resolvedTitleRoute == Routes.HotListening -> "热门收听"
                                                    resolvedTitleRoute == "playlists" -> "我的列表"
                                                    resolvedTitleRoute == "playlist/{playlistId}/{playlistName}" ->
                                                        playlistName.ifBlank { "我的列表" }
                                                    resolvedTitleRoute == "playlist_system/favorites" -> "我的收藏"
                                                    resolvedTitleRoute == "playlist_system/{type}" -> when (systemPlaylistType) {
                                                        "favorites" -> "我的收藏"
                                                        else -> "我的收藏"
                                                    }
                                                    resolvedTitleRoute == "groups" -> "我的分组"
                                                    resolvedTitleRoute == "group/{groupId}/{groupName}" ->
                                                        groupName.ifBlank { "我的分组" }
                                                    resolvedTitleRoute == "settings" -> "设置"
                                                    resolvedTitleRoute == "downloads" -> "任务管理"
                                                    resolvedTitleRoute == "listening_calendar" -> "ASMR 看板"
                                                    resolvedTitleRoute == "dlsite_login" -> "DLsite 登录"
                                                    resolvedTitleRoute?.startsWith("playlist_picker") == true -> "添加到我的列表"
                                                    resolvedTitleRoute?.startsWith("album_detail") == true -> "专辑详情"
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
                                                    if (showPrimaryBrand || albumDetailTransitionActive) {
                                                        PrimaryTopBarBrand(
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
                                                        Box {
                                                            EaraTopBarIconButton(
                                                                onClick = { navController.navigate("downloads") },
                                                                modifier = Modifier.padding(end = 4.dp)
                                                            ) {
                                                                Icon(Icons.Rounded.Inbox, contentDescription = "任务管理")
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
                                                                    Icon(imageVector = icon, contentDescription = "切换视图")
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
                                                                            text = { Text("专辑列表") },
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
                                                                            text = { Text("专辑卡片") },
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
                                                                            text = { Text("音轨列表") },
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
                                                                contentDescription = if (viewMode == 1) "切换为列表视图" else "切换为卡片视图"
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
                                                                contentDescription = if (viewMode == 1) "切换为列表视图" else "切换为卡片视图"
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
                                                            ) { Text("全部暂停") }
                                                        } else if (hasPausedDownloads) {
                                                            TextButton(
                                                                onClick = { downloadsViewModel.resumeAll() },
                                                                colors = ButtonDefaults.textButtonColors(contentColor = topBarContentColor)
                                                            ) { Text("全部继续") }
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
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (albumDetailTransitionActive) 1f else 0f)
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
                                        .padding(top = topContentPadding)
                                        .graphicsLayer {
                                            translationX = if (albumDetailTransitionActive) {
                                                0f
                                            } else {
                                                primaryPageParallaxOffset.value.toPx()
                                            }
                                        },
                                    beyondViewportPageCount = primaryPagerBeyondBoundsPageCount,
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
                                    // ViewModel 的 StateFlow 已经持有最新页面数据；隐藏页面无需继续
                                    // 收集、排序和转换数据。目标页在横向手势开始时会立即恢复收集。
                                    // 详情页退出动画中使用已保留的主页面快照状态；等详情真正弹栈后再恢复
                                    // 数据流，避免在返回手势首帧同时启动查询、排序和列表状态转换。
                                    val primaryRouteDataActive = primaryRouteImmediatelyActive &&
                                        !isAlbumDetailRoute
                                    val primaryRouteDataActiveState = rememberUpdatedState(
                                        primaryRouteDataActive
                                    )
                                    primaryContentStateHolder.SaveableStateProvider("primary_route:$route") {
                                        when (route) {
                                        Routes.Library -> {
                                            LibraryScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                scrollToTopSignal = libraryScrollToTopSignal,
                                                onAlbumClick = { album ->
                                                    AlbumCoverHintStore.recordLocalAlbum(album)
                                                    navigator.openAlbumDetail(
                                                        albumId = album.id,
                                                        rj = null
                                                    )
                                                },
                                                onPlayTracks = { album, tracks, startTrack ->
                                                    scope.launch {
                                                        playerViewModel.playTracksPrepared(album, tracks, startTrack)
                                                    }
                                                },
                                                onOpenPlaylistPicker = { item ->
                                                    albumBatchPlaylistPickerRequest = BatchPlaylistPickerRequest(listOf(item))
                                                },
                                                onOpenGroupPicker = { albumId ->
                                                    navController.navigateSingleTop("group_picker?albumId=$albumId")
                                                },
                                                onOpenFilterScreen = { navController.navigateSingleTop("library_filter") },
                                                onSearchKeyword = ::submitMetaSearchKeyword,
                                                viewModel = libraryViewModel
                                            )
                                        }

                                        Routes.Search -> {
                                            val searchViewModel: SearchViewModel = hiltViewModel(activityViewModelStoreOwner)
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
                                                        preferDlsitePlay = fromPurchasedOnly
                                                    )
                                                },
                                                viewModel = searchViewModel
                                            )
                                        }

                                        Routes.HotListening -> {
                                            val hotListeningViewModel: HotListeningViewModel = hiltViewModel(activityViewModelStoreOwner)
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
                                            SystemPlaylistScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                scrollToTopSignal = favoritesScrollToTopSignal,
                                                onPlayAll = { items, startItem ->
                                                    playerViewModel.playPlaylistItems(items, startItem)
                                                    if (startItem.isVideoPlaybackItem()) openNowPlaying()
                                                },
                                                viewModel = playlistsViewModel
                                            )
                                        }

                                        "playlists" -> {
                                            val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                                            PlaylistsScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                scrollToTopSignal = playlistsScrollToTopSignal,
                                                onPlaylistClick = { playlist ->
                                                    val encoded = URLEncoder.encode(playlist.name, "UTF-8")
                                                    navController.navigateSingleTop("playlist/${playlist.id}/$encoded")
                                                },
                                                viewModel = playlistsViewModel
                                            )
                                        }

                                        "groups" -> {
                                            val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(activityViewModelStoreOwner)
                                            com.asmr.player.ui.groups.AlbumGroupsScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                scrollToTopSignal = groupsScrollToTopSignal,
                                                onGroupClick = { group ->
                                                    val encoded = encodeRouteArg(group.name)
                                                    navController.navigateSingleTop("group/${group.id}/$encoded")
                                                },
                                                viewModel = albumGroupsViewModel
                                            )
                                        }

                                        "settings" -> {
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
                                            com.asmr.player.ui.calendar.ListeningCalendarScreen(
                                                windowSizeClass = windowSizeClass,
                                                isActive = primaryRouteActive,
                                                isDataActive = primaryRouteDataActive,
                                                onOpenDlsiteLogin = { navController.navigateSingleTop("dlsite_login") },
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
                composable(route = Routes.SearchAssist) {
                    SecondaryPageBackground(topPadding = secondaryPageTopPadding) {
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

                    SecondaryPageBackground(topPadding = secondaryPageTopPadding) {
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
                        navArgument("initialTab") { type = NavType.StringType; nullable = true; defaultValue = null }
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
                        onPageOffsetReader = { reader ->
                            if (navController.currentBackStackEntry?.id == backStackEntry.id) {
                                albumDetailPageOffsetReader = reader
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
                            onPlayTracks = { album, tracks, startTrack ->
                                scope.launch {
                                    playerViewModel.playTracksPrepared(album, tracks, startTrack)
                                }
                            },
                            onPlayMediaItems = { items, startIndex ->
                                playerViewModel.playMediaItems(items, startIndex)
                                if (items.getOrNull(startIndex).isVideoPlaybackItem()) openNowPlaying()
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
                            onOpenAlbumByRj = { targetRj, work ->
                                AlbumCoverHintStore.record(
                                    albumId = null,
                                    rjCode = targetRj,
                                    title = work?.title,
                                    circle = null,
                                    coverUrl = resolveRecommendedWorkCoverUrl(targetRj, work?.coverUrl)
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
                        onPageOffsetReader = { reader ->
                            if (navController.currentBackStackEntry?.id == backStackEntry.id) {
                                albumDetailPageOffsetReader = reader
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
                                    playerViewModel.playTracksPrepared(album, tracks, startTrack)
                                }
                            },
                            onPlayMediaItems = { items, startIndex ->
                                playerViewModel.playMediaItems(items, startIndex)
                                if (items.getOrNull(startIndex).isVideoPlaybackItem()) openNowPlaying()
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
                            onOpenAlbumByRj = { targetRj, work ->
                                AlbumCoverHintStore.record(
                                    albumId = null,
                                    rjCode = targetRj,
                                    title = work?.title,
                                    circle = null,
                                    coverUrl = resolveRecommendedWorkCoverUrl(targetRj, work?.coverUrl)
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
                        onPageOffsetReader = { reader ->
                            if (navController.currentBackStackEntry?.id == backStackEntry.id) {
                                albumDetailPageOffsetReader = reader
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
                                    playerViewModel.playTracksPrepared(album, tracks, startTrack)
                                }
                            },
                            onPlayMediaItems = { items, startIndex ->
                                playerViewModel.playMediaItems(items, startIndex)
                                if (items.getOrNull(startIndex).isVideoPlaybackItem()) openNowPlaying()
                            },
                            onAddToQueue = { album, track ->
                                playerViewModel.addTrackToQueue(album, track)
                            },
                            onOpenPlaylistPicker = { item ->
                                albumBatchPlaylistPickerRequest = BatchPlaylistPickerRequest(listOf(item))
                            },
                            onOpenDlsiteLogin = { navController.navigateSingleTop("dlsite_login") },
                            onOpenAlbumByRj = { targetRj, work ->
                                AlbumCoverHintStore.record(
                                    albumId = null,
                                    rjCode = targetRj,
                                    title = work?.title,
                                    circle = null,
                                    coverUrl = resolveRecommendedWorkCoverUrl(targetRj, work?.coverUrl)
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
                                if (items.getOrNull(startIndex).isVideoPlaybackItem()) openNowPlaying()
                            }
                        )
                    }
                }
                composable(
                    route = "group_picker?albumId={albumId}",
                    arguments = listOf(
                        navArgument("albumId") { type = NavType.LongType; defaultValue = 0L }
                    )
                ) { backStackEntry ->
                    val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
                    val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel(activityViewModelStoreOwner)
                    SecondaryPageBackground(topPadding = secondaryPageTopPadding) {
                        com.asmr.player.ui.groups.AlbumGroupPickerScreen(
                            windowSizeClass = windowSizeClass,
                            albumId = albumId,
                            onBack = { navController.popBackStack() },
                            viewModel = albumGroupsViewModel
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
                            onPlayAll = { items, startItem ->
                                playerViewModel.playPlaylistItems(items, startItem)
                                if (startItem.isVideoPlaybackItem()) openNowPlaying()
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
                                onPlayAll = { items, startItem ->
                                    playerViewModel.playPlaylistItems(items, startItem)
                                    if (startItem.isVideoPlaybackItem()) openNowPlaying()
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
                composable("listening_calendar") {
                    Box(modifier = Modifier.fillMaxSize())
                }
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
                    message = "请输入 DLsite 作品编号，支持 RJ、BJ、VJ；保存后将自动执行云同步。",
                    value = manualRjInput,
                    onValueChange = { manualRjInput = it },
                    placeholder = "作品编号（如 BJ02370869）",
                    confirmText = "同步",
                    confirmEnabled = manualRjInput.trim().isNotBlank(),
                    onConfirm = {
                        showManualRjDialog = false
                        albumDetailViewModel.manualSetRjAndSync(manualRjInput.trim())
                    },
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

        if (bottomChromeVisible) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val bottomChromeHorizontalPadding = if (useLargeBottomChrome) 16.dp else 12.dp
                val isCompactWidth = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
                val canUseRightPanel = !isCompactWidth &&
                    !isPhone &&
                    isLandscape &&
                    (currentRoute == "library" || currentRoute == "search")
                val rightPanelExpanded = rightPanelExpandedState.value
                val rightPanelWidth = (maxWidth - 560.dp).coerceAtMost(420.dp)
                val showRightPanel = canUseRightPanel && rightPanelWidth >= 300.dp
                val reservedRightTarget = if (!showRightPanel) {
                    0.dp
                } else if (rightPanelExpanded) {
                    rightPanelWidth + 12.dp
                } else {
                    36.dp + 12.dp
                }
                val reservedRight by animateDpAsState(
                    targetValue = reservedRightTarget,
                    animationSpec = tween(durationMillis = if (rightPanelExpanded) 220 else 180),
                    label = "miniPlayerReservedRight"
                )
                val chromeWidth = (maxWidth - reservedRight - (bottomChromeHorizontalPadding * 2)).coerceAtLeast(0.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .graphicsLayer { clip = false }
                        .padding(start = bottomChromeHorizontalPadding, bottom = bottomChromeBottomPadding)
                        .width(chromeWidth)
                ) {
                    PrimaryBottomChrome(
                        activeRoute = visualPrimaryRoute,
                        pagerState = primaryPagerState,
                        pagerRoutes = primaryPagerRoutes,
                        fallbackRoute = activePrimaryRoute,
                        lockedRoute = pendingPrimaryNavigationRoute,
                        miniPlayerVisible = miniPlayerVisible,
                        miniPlayerDisplayMode = miniPlayerDisplayMode,
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
                            .background(colorScheme.background)
                    )
                    if (!colorScheme.isDark) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colorScheme.primarySoft.copy(alpha = 0.14f))
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = nowPlayingBackdropAlpha }
                ) {
                    PlayerSharedBackdrop(
                        mediaItem = sharedPlayerItem,
                        enabled = coverBackgroundEnabled,
                        clarity = coverBackgroundClarity,
                        artworkAlignment = sharedPlayerBackdropAlignment
                    )
                }
                NowPlayingScreen(
                    windowSizeClass = windowSizeClass,
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
                    coverBackgroundEnabled = coverBackgroundEnabled,
                    coverBackgroundClarity = coverBackgroundClarity,
                    coverPreviewMode = coverPreviewMode,
                    nowPlayingHomeLayoutMode = nowPlayingHomeLayoutMode,
                    nowPlayingHomeLayoutHintDismissed = nowPlayingHomeLayoutHintDismissed,
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
                    EdgeToEdgeFullHeightSheet(
                        onDismissRequest = { nowPlayingPlaylistPickerRequest = null },
                        containerColor = colorScheme.background.copy(alpha = 0.96f),
                        contentColor = colorScheme.onBackground
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(StableWindowInsets.statusBars)
                                .windowInsetsPadding(StableWindowInsets.navigationBars)
                        ) {
                            PlaylistPickerScreen(
                                windowSizeClass = windowSizeClass,
                                items = request.items,
                                onBack = { nowPlayingPlaylistPickerRequest = null },
                                embeddedInDialog = true,
                                viewModel = playlistsViewModel
                            )
                        }
                    }
                }
                albumBatchPlaylistPickerRequest?.let { request ->
                    val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                    EdgeToEdgeFullHeightSheet(
                        onDismissRequest = { albumBatchPlaylistPickerRequest = null },
                        containerColor = colorScheme.background.copy(alpha = 0.96f),
                        contentColor = colorScheme.onBackground
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(StableWindowInsets.statusBars)
                                .windowInsetsPadding(StableWindowInsets.navigationBars)
                        ) {
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
        }

        if (!nowPlayingVisible) {
            albumBatchPlaylistPickerRequest?.let { request ->
                val playlistsViewModel: PlaylistsViewModel = hiltViewModel(activityViewModelStoreOwner)
                EdgeToEdgeFullHeightSheet(
                    onDismissRequest = { albumBatchPlaylistPickerRequest = null },
                    containerColor = colorScheme.background.copy(alpha = 0.96f),
                    contentColor = colorScheme.onBackground
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(StableWindowInsets.statusBars)
                            .windowInsetsPadding(StableWindowInsets.navigationBars)
                    ) {
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
                message = "发现新版本：${release.tagName}",
                onDismissRequest = { automaticUpdateDialogDismissed = true },
                actions = listOf(
                    FlatDialogAction(
                        text = "立即更新",
                        tone = FlatDialogActionTone.Primary,
                        onClick = {
                            automaticUpdateDialogDismissed = true
                            automaticUpdateInstallRequested = true
                            settingsViewModel.downloadLatestApk()
                            messageManager.showInfo("开始下载更新…")
                        }
                    ),
                    FlatDialogAction(
                        text = "不再提醒",
                        tone = FlatDialogActionTone.Danger,
                        onClick = {
                            automaticUpdateDialogDismissed = true
                            settingsViewModel.disableAutoUpdateCheck()
                            messageManager.showInfo("已关闭启动时自动检查更新")
                        }
                    ),
                    FlatDialogAction(
                        text = "详情",
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
                                messageManager.showError("无法打开 GitHub 发布页")
                            }
                        }
                    )
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "当前版本：${BuildConfig.VERSION_NAME}",
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
                            text = "安装包：${release.apkName}",
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

