package com.asmr.player.ui.settings

import com.asmr.player.i18n.appString
import com.asmr.player.R

import com.asmr.player.ui.common.LocalTabletLayout
import com.asmr.player.ui.common.LocalMainHeaderPadding
import com.asmr.player.ui.common.DeferredPageItem

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asmr.player.BuildConfig
import com.asmr.player.cache.AppCacheLimits
import com.asmr.player.cache.AppCacheState
import com.asmr.player.data.remote.download.DownloadDestination
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.DeepSeekReasoningEffort
import com.asmr.player.data.settings.DeepSeekTranslationSettings
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.NowPlayingLyricsSettings
import com.asmr.player.data.settings.PlayerBackgroundMode
import com.asmr.player.subtitle.SubtitleDeviceCapability
import com.asmr.player.subtitle.SubtitleModelDownloadSource
import com.asmr.player.subtitle.SubtitleModelInstallationState
import com.asmr.player.subtitle.SubtitleModelOperation
import com.asmr.player.subtitle.SubtitleModelState
import com.asmr.player.subtitle.SubtitleTranscriptionModels
import com.asmr.player.subtitle.configuredSubtitleModelDownloadSources
import com.asmr.player.subtitle.DEEPSEEK_SUBTITLE_MODEL
import com.asmr.player.subtitle.DeepSeekAccountState
import com.asmr.player.subtitle.formatDeepSeekBalances
import com.asmr.player.subtitle.formatDeepSeekTokenTotal
import com.asmr.player.ui.library.BulkPhase
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.util.documentTreeDisplayPath
import com.asmr.player.ui.common.AppSupportStatusSection
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.FlatActionDialog
import com.asmr.player.ui.common.FlatDialogAction
import com.asmr.player.ui.common.FlatDialogActionTone
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.resolveMainPageBackgroundColor
import com.asmr.player.ui.common.smoothScrollToTop
import com.asmr.player.ui.common.withAddedBottomPadding
import com.asmr.player.ui.common.collectAsStateWhileActive
import com.asmr.player.ui.update.launchDownloadedApkInstall
import com.asmr.player.util.Formatting
import java.io.File
import kotlin.math.roundToInt

private val SettingsPageHorizontalPadding = 8.dp
private const val MONOCHROME_THEME_SENTINEL = 0x01000000
private const val SettingsDetailEnterDurationMs = 440
private const val SettingsDetailExitDurationMs = 420
private val SettingsDetailSlideEasing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

private enum class SettingsSection(
    private val titleProvider: () -> String,
    private val descriptionProvider: () -> String,
    private val iconProvider: () -> ImageVector,
) {
    LocalLibrary( { appString(R.string.ui_library) }, { appString(R.string.ui_manage_scan_directories_refresh_local_content_and_synchronize) }, { Icons.Rounded.Folder }),
    BlockedKeywords( { appString(R.string.ui_blocked_keywords) }, { appString(R.string.ui_filter_keywords_you_don_t_want_to_see) }, { Icons.Rounded.Block }),
    Appearance( { appString(R.string.ui_appearance) }, { appString(R.string.ui_adjust_theme_theme_color_and_playback_page_background) }, { Icons.Rounded.Palette }),
    Playback( { appString(R.string.ui_playback) }, { appString(R.string.ui_manage_audio_output_and_fades) }, { Icons.Rounded.Headphones }),
    Lyrics( { appString(R.string.ui_lyrics) }, { appString(R.string.ui_configure_the_display_effect_of_lyrics_page_and) }, { Icons.Rounded.Lyrics }),
    Translation( { appString(R.string.ui_language_and_translation) }, { appString(R.string.ui_manage_page_translation_subtitle_models_and_translation_apis) }, { Icons.Rounded.Translate }),
    SupportStatus( { appString(R.string.ui_services_and_proxy) }, { appString(R.string.ui_test_service_connectivity_and_configure_proxy_and_dns) }, { Icons.Rounded.Router }),
    AppCache( { appString(R.string.ui_app_cache) }, { appString(R.string.ui_set_cache_capacity_limit_and_clear_cache) }, { Icons.Rounded.Storage }),
    About( { appString(R.string.ui_about) }, { appString(R.string.ui_view_version_information_and_check_for_app_updates) }, { Icons.Rounded.Info });

    val title: String get() = titleProvider()
    val description: String get() = descriptionProvider()


    val icon: ImageVector get() = iconProvider()
}

private fun settingsDetailEnterTransition() = slideInHorizontally(
    animationSpec = tween(
        durationMillis = SettingsDetailEnterDurationMs,
        easing = SettingsDetailSlideEasing,
    ),
    initialOffsetX = { fullWidth -> fullWidth },
)

private fun settingsDetailExitTransition() = slideOutHorizontally(
    animationSpec = tween(
        durationMillis = SettingsDetailExitDurationMs,
        easing = SettingsDetailSlideEasing,
    ),
    targetOffsetX = { fullWidth -> fullWidth },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    isDataActive: Boolean = isActive,
    viewModel: SettingsViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    scrollToTopSignal: Long = 0L,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit = {},
    onDetailPageChanged: (Boolean) -> Unit = {},
) {
    val tabletLayout = LocalTabletLayout.current
    val twoPane = tabletLayout.twoPane
    var selectedSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    val effectiveSection = selectedSection ?: if (twoPane) SettingsSection.Appearance else null
    var retainedSection by remember { mutableStateOf(selectedSection) }
    val currentOnDetailPageChanged by rememberUpdatedState(onDetailPageChanged)
    val localLibraryDataActive = isDataActive && effectiveSection == SettingsSection.LocalLibrary
    val blockedKeywordsDataActive = isDataActive && effectiveSection == SettingsSection.BlockedKeywords
    val appearanceDataActive = isDataActive && effectiveSection == SettingsSection.Appearance
    val playbackDataActive = isDataActive && effectiveSection == SettingsSection.Playback
    val lyricsDataActive = isDataActive && effectiveSection == SettingsSection.Lyrics
    val translationDataActive = isDataActive && effectiveSection == SettingsSection.Translation
    val supportStatusDataActive = isDataActive && effectiveSection == SettingsSection.SupportStatus
    val aboutDataActive = isDataActive && effectiveSection == SettingsSection.About
    val appCacheDataActive = isDataActive && effectiveSection == SettingsSection.AppCache

    LaunchedEffect(isActive, selectedSection, twoPane) {
        currentOnDetailPageChanged(isActive && !twoPane && selectedSection != null)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnDetailPageChanged(false) }
    }
    LaunchedEffect(translationDataActive, viewModel) {
        if (translationDataActive) viewModel.prepareSettingsData()
    }
    val floatingLyricsEnabled by viewModel.floatingLyricsEnabled.collectAsStateWhileActive(lyricsDataActive)
    val floatingSettings by viewModel.floatingLyricsSettings.collectAsStateWhileActive(lyricsDataActive)
    val nowPlayingLyricsSettings by viewModel.nowPlayingLyricsSettings.collectAsStateWhileActive(lyricsDataActive)
    val lyricsPageSettings by viewModel.lyricsPageSettings.collectAsStateWhileActive(lyricsDataActive)
    val dynamicPlayerHueEnabled by viewModel.dynamicPlayerHueEnabled.collectAsStateWhileActive(appearanceDataActive)
    val themeMode by viewModel.themeMode.collectAsStateWhileActive(appearanceDataActive)
    val staticHueArgbLight by viewModel.staticHueArgbLight.collectAsStateWhileActive(appearanceDataActive)
    val staticHueArgbDark by viewModel.staticHueArgbDark.collectAsStateWhileActive(appearanceDataActive)
    val playerBackgroundMode by viewModel.playerBackgroundMode.collectAsStateWhileActive(appearanceDataActive)
    val coverBackgroundEnabled by viewModel.coverBackgroundEnabled.collectAsStateWhileActive(appearanceDataActive)
    val dynamicFlowingBackgroundEnabled by viewModel.dynamicFlowingBackgroundEnabled.collectAsStateWhileActive(appearanceDataActive)
    val coverBackgroundClarity by viewModel.coverBackgroundClarity.collectAsStateWhileActive(appearanceDataActive)
    val coverPreviewMode by viewModel.coverPreviewMode.collectAsStateWhileActive(appearanceDataActive)
    val pauseOnOutputDisconnect by viewModel.pauseOnOutputDisconnect.collectAsStateWhileActive(playbackDataActive)
    val resumeOnOutputConnect by viewModel.resumeOnOutputConnect.collectAsStateWhileActive(playbackDataActive)
    val pauseOnOtherAudio by viewModel.pauseOnOtherAudio.collectAsStateWhileActive(playbackDataActive)
    val playFadeInMs by viewModel.playFadeInMs.collectAsStateWhileActive(playbackDataActive)
    val pauseFadeOutMs by viewModel.pauseFadeOutMs.collectAsStateWhileActive(playbackDataActive)
    val sfwHideSystemControls by viewModel.sfwHideSystemControls.collectAsStateWhileActive(playbackDataActive)
    val searchBlockedKeywords by viewModel.searchBlockedKeywords.collectAsStateWhileActive(blockedKeywordsDataActive)
    val networkRouteSettings by viewModel.networkRouteSettings.collectAsStateWhileActive(supportStatusDataActive)
    val appCacheState by viewModel.appCacheState.collectAsStateWhileActive(appCacheDataActive)
    val subtitleModelState by viewModel.subtitleModelState.collectAsStateWhileActive(translationDataActive)
    val deepSeekApiKeyState by viewModel.deepSeekApiKeyState.collectAsStateWhileActive(translationDataActive)
    val deepSeekAccountState by viewModel.deepSeekAccountState.collectAsStateWhileActive(translationDataActive)
    val deepSeekTranslationSettings by viewModel.deepSeekTranslationSettings.collectAsStateWhileActive(translationDataActive)
    val translationApiState by viewModel.translationApiState.collectAsStateWhileActive(translationDataActive)
    val customTranslationTokenTotal by viewModel.customTranslationTokenTotal.collectAsStateWhileActive(translationDataActive)
    val updateState by viewModel.updateState.collectAsStateWhileActive(aboutDataActive)
    val autoUpdateCheckEnabled by viewModel.autoUpdateCheckEnabled.collectAsStateWhileActive(aboutDataActive)
    val scanRoots by libraryViewModel.scanRoots.collectAsStateWhileActive(localLibraryDataActive)
    val downloadDestination by viewModel.downloadDestination.collectAsStateWhileActive(localLibraryDataActive)
    val bulkProgress by libraryViewModel.bulkProgress.collectAsStateWhileActive(localLibraryDataActive)
    val isGlobalSyncRunning by libraryViewModel.isGlobalSyncRunning.collectAsStateWhileActive(localLibraryDataActive)
    val context = LocalContext.current
    val colorScheme = AsmrTheme.colorScheme
    val rootListState = rememberLazyListState()
    val sectionListStates = remember { SettingsSection.entries.associateWith { androidx.compose.foundation.lazy.LazyListState() } }
    val detailListState = sectionListStates.getValue(effectiveSection ?: retainedSection ?: SettingsSection.Appearance)
    val listState = if (effectiveSection == null) rootListState else detailListState
    val segmentedButtonColors = SegmentedButtonDefaults.colors(
        activeContainerColor = colorScheme.primarySoft,
        activeContentColor = if (colorScheme.isDark) colorScheme.onPrimaryContainer else colorScheme.primaryStrong,
        activeBorderColor = colorScheme.primaryStrong,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = colorScheme.onSurfaceVariant,
        inactiveBorderColor = colorScheme.primaryStrong.copy(alpha = 0.4f),
        disabledActiveContainerColor = colorScheme.primarySoft.copy(alpha = 0.48f),
        disabledActiveContentColor = if (colorScheme.isDark) {
            colorScheme.onPrimaryContainer.copy(alpha = 0.48f)
        } else {
            colorScheme.primaryStrong.copy(alpha = 0.48f)
        },
        disabledActiveBorderColor = colorScheme.primaryStrong.copy(alpha = 0.24f),
        disabledInactiveContainerColor = Color.Transparent,
        disabledInactiveContentColor = colorScheme.primaryStrong.copy(alpha = 0.38f),
        disabledInactiveBorderColor = colorScheme.primaryStrong.copy(alpha = 0.2f)
    )
    
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    if (lyricsDataActive) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, context) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    overlayGranted = Settings.canDrawOverlays(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }
    var activeTipKey by remember { mutableStateOf<String?>(null) }
    var searchBlockedKeywordInput by rememberSaveable { mutableStateOf("") }
    var showClearAppCacheConfirmation by remember { mutableStateOf(false) }
    var pendingDeleteSubtitleModelId by remember { mutableStateOf<String?>(null) }
    val subtitleModelSourceIds = remember {
        mutableStateMapOf<String, String>().apply {
            SubtitleTranscriptionModels.all.forEach { model ->
                this[model.id] = SubtitleModelDownloadSource.HuggingFace.id
            }
        }
    }
    var deepSeekApiKeyInput by remember { mutableStateOf("") }
    LaunchedEffect(deepSeekApiKeyState.saveVersion) {
        if (deepSeekApiKeyState.saveVersion > 0L) deepSeekApiKeyInput = ""
    }
    DisposableEffect(onHorizontalControlInteractionChanged) {
        onDispose { onHorizontalControlInteractionChanged(false) }
    }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        overlayGranted = Settings.canDrawOverlays(context)
        viewModel.setFloatingLyricsEnabled(overlayGranted)
    }
    val pickRootLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                val ok = runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }.isSuccess
                if (ok) {
                    val uriString = uri.toString()
                    val added = libraryViewModel.addScanRoot(uriString)
                    if (added) {
                        libraryViewModel.scanSingleRoot(uriString)
                    }
                }
            }
        }
    )
    var pendingDownloadDestination by remember { mutableStateOf<DownloadDestination?>(null) }
    val pickDownloadRootLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                val granted = runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                    true
                }.getOrDefault(false)
                if (granted) {
                    pendingDownloadDestination = DownloadDestination.DocumentTree(
                        root = uri.toString(),
                        label = formatTreeRootLabel(uri.toString()),
                    )
                } else {
                    libraryViewModel.messageManager.showError(appString(R.string.ui_unable_to_obtain_read_and_write_permissions_for))
                }
            }
        },
    )
    var pendingRemoveRoot by remember { mutableStateOf<String?>(null) }

    // 屏幕尺寸判断
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    BackHandler(enabled = isActive && !twoPane && selectedSection != null) {
        selectedSection = null
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        contentColor = colorScheme.onBackground
    ) { padding ->
        LaunchedEffect(isActive) {
            if (isActive) return@LaunchedEffect
            if (!twoPane) {
                selectedSection = null
                retainedSection = null
            }
            deepSeekApiKeyInput = ""
            rootListState.stopScroll(MutatePriority.PreventUserInput)
            detailListState.stopScroll(MutatePriority.PreventUserInput)
        }
        LaunchedEffect(appCacheDataActive) {
            if (appCacheDataActive) viewModel.refreshAppCacheSize()
        }
        LaunchedEffect(selectedSection) {
            if (selectedSection != null && !twoPane) detailListState.scrollToItem(0)
        }
        LaunchedEffect(scrollToTopSignal) {
            if (scrollToTopSignal == 0L) return@LaunchedEffect
            listState.smoothScrollToTop()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = tabletLayout.horizontalPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val contentModifier = if (isCompact || tabletLayout.isTablet) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
            }
            LazyColumn(
                state = rootListState,
                modifier = if (twoPane) Modifier.align(Alignment.TopStart).width(tabletLayout.masterWidth).fillMaxHeight() else contentModifier,
                flingBehavior = rememberCalmScrollableFlingBehavior(),
                contentPadding = PaddingValues(start = SettingsPageHorizontalPadding, end = SettingsPageHorizontalPadding, top = LocalMainHeaderPadding.current + 10.dp, bottom = 10.dp)
                    .withAddedBottomPadding(LocalBottomOverlayPadding.current),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "settings_sections") {
                    SettingsSectionsPanel(
                        selectedSection = if (twoPane) effectiveSection else null,
                        onSectionClick = { section ->
                            retainedSection = section
                            selectedSection = section
                        },
                    )
                }
                item(key = "root_bottom_spacer") {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }

            AnimatedVisibility(
                visible = effectiveSection != null,
                modifier = if (twoPane) Modifier.fillMaxSize().padding(start = tabletLayout.masterWidth + 16.dp) else contentModifier,
                enter = if (twoPane) EnterTransition.None else settingsDetailEnterTransition(),
                exit = if (twoPane) ExitTransition.None else settingsDetailExitTransition(),
                label = "settingsDetailTransition",
            ) {
                val currentSection = effectiveSection ?: retainedSection ?: return@AnimatedVisibility
                LazyColumn(
                    state = detailListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(resolveMainPageBackgroundColor(colorScheme)),
                    flingBehavior = rememberCalmScrollableFlingBehavior(),
                    contentPadding = PaddingValues(start = SettingsPageHorizontalPadding, end = SettingsPageHorizontalPadding, top = LocalMainHeaderPadding.current + 10.dp, bottom = 10.dp)
                        .withAddedBottomPadding(LocalBottomOverlayPadding.current),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "detail_header:${currentSection.name}") {
                        SettingsDetailHeader(
                            section = currentSection,
                            showBack = !twoPane,
                            onBack = { selectedSection = null },
                        )
                    }

                if (currentSection == SettingsSection.LocalLibrary) {
                    item(key = "group:local") {
                        SettingsDetailCard {
                val isDark = AsmrTheme.colorScheme.isDark
                val buttonColors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colorScheme.primarySoft,
                    contentColor = if (isDark) colorScheme.onPrimaryContainer else colorScheme.primaryStrong
                )

                Text(androidx.compose.ui.res.stringResource(R.string.ui_download_catalog), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    contentColor = colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(downloadDestination.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = downloadDestination.displayPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.requestDownloadDirectoryChange {
                                        pickDownloadRootLauncher.launch(null)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = buttonColors,
                                enabled = !isGlobalSyncRunning,
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = buttonColors.contentColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(androidx.compose.ui.res.stringResource(R.string.ui_select_directory))
                            }
                            TextButton(
                                onClick = {
                                    viewModel.requestDownloadDirectoryChange {
                                        pendingDownloadDestination = DownloadDestination.Default(
                                            root = File(context.getExternalFilesDir(null), "albums").absolutePath,
                                        )
                                    }
                                },
                                enabled = downloadDestination is DownloadDestination.DocumentTree && !isGlobalSyncRunning,
                            ) {
                                Text(androidx.compose.ui.res.stringResource(R.string.ui_reset_default))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = { libraryViewModel.scanAllRoots() },
                        modifier = Modifier.weight(1f),
                        colors = buttonColors,
                        enabled = !isGlobalSyncRunning
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = buttonColors.contentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(androidx.compose.ui.res.stringResource(R.string.ui_refresh_local))
                    }
                    FilledTonalButton(
                        onClick = { libraryViewModel.syncMetadata() },
                        modifier = Modifier.weight(1f),
                        colors = buttonColors,
                        enabled = !isGlobalSyncRunning
                    ) {
                        Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = buttonColors.contentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(androidx.compose.ui.res.stringResource(R.string.ui_cloud_sync))
                    }
                }

                FilledTonalButton(
                    onClick = { pickRootLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = buttonColors
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = buttonColors.contentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.ui_add_directory))
                }

                bulkProgress?.let { progress ->
                    val title = when (progress.phase) {
                        BulkPhase.ScanningLocal -> appString(R.string.ui_scanning_local_library)
                        BulkPhase.SyncingCloud -> appString(R.string.ui_cloud_sync_in_progress)
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.surface.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    if (progress.currentAlbumTitle.isNotBlank()) {
                                        Text(
                                            text = appString(R.string.ui_album_456ccf, progress.current, progress.total, progress.currentAlbumTitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        Text(
                                            text = appString(R.string.ui_progress, progress.current, progress.total),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.textSecondary
                                        )
                                    }
                                }
                                TextButton(onClick = { libraryViewModel.cancelBulkTask() }) { Text(androidx.compose.ui.res.stringResource(R.string.ui_cancel)) }
                            }
                            if (progress.total > 0) {
                                LinearProgressIndicator(
                                    progress = { progress.fraction },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            if (progress.currentFile.isNotBlank()) {
                                Text(
                                    text = appString(R.string.ui_scanning, progress.currentFile),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ui_directory_added), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (scanRoots.isEmpty()) {
                        Text(androidx.compose.ui.res.stringResource(R.string.ui_none_yet), style = MaterialTheme.typography.bodySmall, color = colorScheme.textSecondary)
                    } else {
                        scanRoots.forEach { root ->
                            val label = remember(root) { formatTreeRootLabel(root) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        documentTreeDisplayPath(context, root),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { libraryViewModel.scanSingleRoot(root) }, enabled = !isGlobalSyncRunning) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colorScheme.onSurface)
                                }
                                IconButton(onClick = { libraryViewModel.syncMetadataForRoot(root) }, enabled = !isGlobalSyncRunning) {
                                    Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = colorScheme.onSurface)
                                }
                                IconButton(onClick = { pendingRemoveRoot = root }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = colorScheme.onSurface)
                                }
                            }
                            }
                    }
                }
            }
        }
                }

                if (currentSection == SettingsSection.BlockedKeywords) {
                    item(key = "group:block_words") {
                        SettingsDetailCard {
                        SearchBlockedKeywordsSection(
                            input = searchBlockedKeywordInput,
                            keywords = searchBlockedKeywords,
                            onInputChange = { searchBlockedKeywordInput = it },
                            onAddKeyword = {
                                val keyword = searchBlockedKeywordInput.trim()
                                if (keyword.isNotBlank()) {
                                    viewModel.addSearchBlockedKeyword(keyword)
                                    searchBlockedKeywordInput = ""
                                }
                            },
                            onRemoveKeyword = viewModel::removeSearchBlockedKeyword
                        )
                    }
                }
                }

                if (currentSection == SettingsSection.Appearance) {
                    item(key = "group:appearance") {
                        SettingsDetailCard {
                            Text(androidx.compose.ui.res.stringResource(R.string.ui_theme_mode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                ThemeModeChip(
                                    label = appString(R.string.ui_system),
                                    selected = themeMode == "system",
                                    onClick = { viewModel.setThemeMode("system") }
                                )
                                ThemeModeChip(
                                    label = appString(R.string.ui_light_color),
                                    selected = themeMode == "light",
                                    onClick = { viewModel.setThemeMode("light") }
                                )
                                ThemeModeChip(
                                    label = appString(R.string.ui_dark),
                                    selected = themeMode == "dark",
                                    onClick = { viewModel.setThemeMode("dark") }
                                )
                                ThemeModeChip(
                                    label = appString(R.string.ui_soft_dark_color),
                                    selected = themeMode == "soft_dark",
                                    onClick = { viewModel.setThemeMode("soft_dark") }
                                )
                            }

                            Text(androidx.compose.ui.res.stringResource(R.string.ui_theme_color), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val currentHueArgb = if (themeMode == "light") staticHueArgbLight else staticHueArgbDark
                                ThemeColorDot(
                                    color = null,
                                    selected = currentHueArgb == null,
                                    onClick = { viewModel.setStaticHueArgb(null) }
                                )
                                ThemeMonochromeDot(
                                    selected = currentHueArgb == MONOCHROME_THEME_SENTINEL,
                                    onClick = { viewModel.setStaticHueArgb(MONOCHROME_THEME_SENTINEL) }
                                )
                                // 浅色主题用深色调（深红、深蓝、墨綠等），深色/柔和深色主题用高饱和亮色
                                val presets = if (themeMode == "light") {
                                    listOf(
                                        Color(0xFF0B3D2E), // 墨綠
                                        Color(0xFF0D47A1), // 深蓝
                                        Color(0xFF880E4F), // 深玫红
                                        Color(0xFF4A148C), // 深紫
                                        Color(0xFF7B1A1A), // 深砖红
                                        Color(0xFF004D40)  // 深青綠
                                    )
                                } else {
                                    // dark / soft_dark：饱和度稍高的亮色，在暗背景上清晰醒目
                                    listOf(
                                        Color(0xFF29B6F6), // 亮天蓝
                                        Color(0xFF26C17A), // 亮翠綠
                                        Color(0xFF7C4DFF), // 亮紫罗兰
                                        Color(0xFFFF5252), // 亮珊瑚红
                                        Color(0xFFFFCA28), // 亮琥珀黄
                                        Color(0xFF26C7C7)  // 亮青色
                                    )
                                }
                                presets.forEach { c ->
                                    ThemeColorDot(
                                        color = c,
                                        selected = currentHueArgb == c.toArgb(),
                                        onClick = { viewModel.setStaticHueArgb(c.toArgb()) }
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                        SettingsToggleRow(
                            text = appString(R.string.ui_cover_dynamic_theme_global),
                            checked = dynamicPlayerHueEnabled,
                            onCheckedChange = viewModel::setDynamicPlayerHueEnabled
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(androidx.compose.ui.res.stringResource(R.string.ui_player_lyrics_background), style = MaterialTheme.typography.bodyMedium)
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = playerBackgroundMode == PlayerBackgroundMode.Theme,
                                    onClick = { viewModel.setPlayerBackgroundMode(PlayerBackgroundMode.Theme) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                    colors = segmentedButtonColors,
                                    icon = {},
                                    label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_theme_color)) }
                                )
                                SegmentedButton(
                                    selected = playerBackgroundMode == PlayerBackgroundMode.Solid,
                                    onClick = {
                                        if (activeTipKey == "cover_preview_mode") activeTipKey = null
                                        viewModel.setPlayerBackgroundMode(PlayerBackgroundMode.Solid)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                    colors = segmentedButtonColors,
                                    icon = {},
                                    label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_solid_color)) }
                                )
                                SegmentedButton(
                                    selected = playerBackgroundMode == PlayerBackgroundMode.Flowing,
                                    onClick = {
                                        if (activeTipKey == "cover_preview_mode") activeTipKey = null
                                        viewModel.setPlayerBackgroundMode(PlayerBackgroundMode.Flowing)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                    colors = segmentedButtonColors,
                                    icon = {},
                                    label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_flowing_light)) }
                                )
                            }
                        }
                        if (playerBackgroundMode == PlayerBackgroundMode.Flowing) {
                            SettingsToggleRow(
                                text = appString(R.string.ui_animated_flowing_background),
                                checked = dynamicFlowingBackgroundEnabled,
                                onCheckedChange = viewModel::setDynamicFlowingBackgroundEnabled
                            )
                        } else if (playerBackgroundMode == PlayerBackgroundMode.Theme) {
                            SettingsToggleRow(
                                text = appString(R.string.ui_play_page_lyric_page_cover_background),
                                checked = coverBackgroundEnabled,
                                onCheckedChange = viewModel::setCoverBackgroundEnabled
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(androidx.compose.ui.res.stringResource(R.string.ui_background_cover_preview_method), style = MaterialTheme.typography.bodyMedium)
                                PreviewModeInfoTip(
                                    active = activeTipKey == "cover_preview_mode",
                                    onToggle = {
                                        activeTipKey = if (activeTipKey == "cover_preview_mode") null else "cover_preview_mode"
                                    }
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                SingleChoiceSegmentedButtonRow {
                                    SegmentedButton(
                                        selected = coverPreviewMode == CoverPreviewMode.Disabled,
                                        onClick = { viewModel.setCoverPreviewMode(CoverPreviewMode.Disabled) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                        colors = segmentedButtonColors,
                                        icon = {},
                                        label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_close)) }
                                    )
                                    SegmentedButton(
                                        selected = coverPreviewMode == CoverPreviewMode.Drag,
                                        onClick = { viewModel.setCoverPreviewMode(CoverPreviewMode.Drag) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                        colors = segmentedButtonColors,
                                        icon = {},
                                        label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_slide)) }
                                    )
                                    SegmentedButton(
                                        selected = coverPreviewMode == CoverPreviewMode.Motion,
                                        onClick = { viewModel.setCoverPreviewMode(CoverPreviewMode.Motion) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                        colors = segmentedButtonColors,
                                        icon = {},
                                        label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_turn)) }
                                    )
                                }
                            }
                            if (coverBackgroundEnabled) {
                                key("cover_background_clarity_slider") {
                                    DeferredCommitSettingsSliderRow(
                                        committedValue = coverBackgroundClarity,
                                        range = 0f..1f,
                                        stepSize = 0.05f,
                                        textForValue = { value ->
                                            appString(R.string.ui_cover_background_clarity, (value.coerceIn(0f, 1f) * 100).toInt())
                                        },
                                        onValueCommitted = viewModel::setCoverBackgroundClarity,
                                        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                                    )
                                }
                            }
                        }
                    }
                }
                }

                if (currentSection == SettingsSection.Playback) {
                    item(key = "group:playback") {
                        SettingsDetailCard {
                            SettingsToggleRow(
                                text = appString(R.string.ui_sfw_switch),
                                checked = sfwHideSystemControls,
                                onCheckedChange = viewModel::setSfwHideSystemControls,
                                infoKey = "sfw_hide_system_controls",
                                infoTitle = "SFW",
                                infoText = appString(R.string.ui_when_turned_on_it_will_try_to_hide),
                                activeTipKey = activeTipKey,
                                onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    role = androidx.compose.ui.semantics.Role.Button,
                                    onClick = viewModel::openSystemAudioEffects
                                )
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = appString(R.string.ui_system_sound_effects_some_systems_will_turn_on),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = colorScheme.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        SettingsToggleRow(
                            text = appString(R.string.ui_pause_playback_immediately_when_speakers_wired_bluetooth_headphones),
                            checked = pauseOnOutputDisconnect,
                            onCheckedChange = viewModel::setPauseOnOutputDisconnect,
                            infoKey = "pause_on_output_disconnect",
                            infoTitle = appString(R.string.ui_automatic_pause_when_output_is_disconnected),
                            infoText = appString(R.string.ui_if_the_external_amplifier_headphone_or_bluetooth_output),
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                        )
                        SettingsToggleRow(
                            text = appString(R.string.ui_continue_playback_when_connected_to_wired_bluetooth_headphones),
                            checked = resumeOnOutputConnect,
                            onCheckedChange = viewModel::setResumeOnOutputConnect,
                            infoKey = "resume_on_output_connect",
                            infoTitle = appString(R.string.ui_output_access_automatic_recovery),
                            infoText = appString(R.string.ui_when_an_external_output_such_as_headphones_bluetooth),
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                        )
                        DeferredCommitSettingsSliderRow(
                            committedValue = playFadeInMs.toFloat(),
                            range = 0f..3000f,
                            stepSize = 100f,
                            textForValue = { value -> appString(R.string.ui_gradually_increase_volume_while_playing_ms, value.toInt()) },
                            onValueCommitted = { viewModel.setPlayFadeInMs(it.toInt()) },
                            infoKey = "play_fade_in",
                            infoTitle = appString(R.string.ui_play_fade_in),
                            infoText = appString(R.string.ui_when_you_click_play_the_volume_will_smoothly),
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key },
                            onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                        )
                        DeferredCommitSettingsSliderRow(
                            committedValue = pauseFadeOutMs.toFloat(),
                            range = 0f..3000f,
                            stepSize = 100f,
                            textForValue = { value -> appString(R.string.ui_gradually_lower_volume_while_paused_ms, value.toInt()) },
                            onValueCommitted = { viewModel.setPauseFadeOutMs(it.toInt()) },
                            infoKey = "pause_fade_out",
                            infoTitle = appString(R.string.ui_pause_fade),
                            infoText = appString(R.string.ui_when_you_click_pause_the_volume_will_gradually),
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key },
                            onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                        )
                        SettingsToggleRow(
                            text = appString(R.string.ui_pause_when_other_applications_play_audio_video),
                            checked = pauseOnOtherAudio,
                            onCheckedChange = viewModel::setPauseOnOtherAudio,
                            infoKey = "pause_on_other_audio",
                            infoTitle = appString(R.string.ui_audio_focus_paused),
                            infoText = appString(R.string.ui_pause_playback_when_another_music_or_video_app),
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                        )
                    }
                }
                }

                // 歌词
                if (currentSection == SettingsSection.Lyrics) {
                    item(key = "group:floating_lyrics") {
                        SettingsDetailCard {
                            SettingsToggleRow(
                                text = appString(R.string.ui_turn_on_floating_lyrics),
                                checked = floatingLyricsEnabled && overlayGranted,
                                onCheckedChange = { enabled ->
                                    overlayGranted = Settings.canDrawOverlays(context)
                                    viewModel.setFloatingLyricsEnabled(enabled && overlayGranted)
                                    if (enabled && !overlayGranted) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        runCatching {
                                            overlayLauncher.launch(intent)
                                        }.recoverCatching {
                                            overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                                        }.onFailure {
                                            libraryViewModel.messageManager.showError(appString(R.string.ui_unable_to_open_overlay_permission_settings))
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                            Text(androidx.compose.ui.res.stringResource(R.string.ui_suspended_lyrics_details), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                            if (floatingLyricsEnabled && overlayGranted) {
                                SettingsSliderRow(
                                    text = appString(R.string.ui_floating_lyrics_background_transparency, ((1f - floatingSettings.backgroundOpacity) * 100).roundToInt()),
                                    value = 1f - floatingSettings.backgroundOpacity,
                                    range = 0f..1f,
                                    onValueChange = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(backgroundOpacity = 1f - it)) },
                                    onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                                )

                                SettingsSliderRow(
                                    text = appString(R.string.ui_font_size, floatingSettings.size.toInt()),
                                    value = floatingSettings.size,
                                    range = 12f..32f,
                                    onValueChange = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(size = it)) },
                                    onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                                )

                                SettingsSliderRow(
                                    text = appString(R.string.ui_vertical_position_y_axis),
                                    value = floatingSettings.yOffset.toFloat(),
                                    range = 0f..2000f,
                                    onValueChange = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(yOffset = it.toInt())) },
                                    onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.ui_alignment), style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.weight(1f))
                                    SingleChoiceSegmentedButtonRow {
                                        SegmentedButton(
                                            selected = floatingSettings.align == 0,
                                            onClick = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(align = 0)) },
                                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                            colors = segmentedButtonColors,
                                            icon = {},
                                            label = { Icon(Icons.AutoMirrored.Rounded.FormatAlignLeft, null) }
                                        )
                                        SegmentedButton(
                                            selected = floatingSettings.align == 1,
                                            onClick = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(align = 1)) },
                                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                            colors = segmentedButtonColors,
                                            icon = {},
                                            label = { Icon(Icons.Rounded.FormatAlignCenter, null) }
                                        )
                                        SegmentedButton(
                                            selected = floatingSettings.align == 2,
                                            onClick = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(align = 2)) },
                                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                            colors = segmentedButtonColors,
                                            icon = {},
                                            label = { Icon(Icons.AutoMirrored.Rounded.FormatAlignRight, null) }
                                        )
                                    }
                                }

                                val presetColors = remember {
                                    listOf(
                                        0xFFFFFFFF.toInt(),
                                        0xFFFFE14D.toInt(),
                                        0xFF39D5FF.toInt(),
                                        0xFF5CFF95.toInt(),
                                        0xFFFF5FA2.toInt()
                                    )
                                }
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.ui_lyrics_color), style = MaterialTheme.typography.bodyMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        presetColors.forEach { c ->
                                            val selected = floatingSettings.color == c
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(c))
                                                    .border(
                                                        width = if (selected) 2.dp else 1.dp,
                                                        color = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.25f),
                                                        shape = CircleShape
                                                    )
                                                    .clickable { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(color = c)) }
                                            )
                                        }
                                    }
                                }

                                SettingsToggleRow(
                                    text = appString(R.string.ui_click_to_penetrate_lock_position),
                                    checked = !floatingSettings.touchable,
                                    onCheckedChange = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(touchable = !it)) }
                                )
                            }
                        }
                    }
                    item(key = "group:lyrics") {
                        SettingsDetailCard {
                            NowPlayingLyricsSettingsSection(
                                settings = nowPlayingLyricsSettings,
                                onSettingsChange = viewModel::updateNowPlayingLyricsSettings,
                                onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

                            LyricsPageSettingsSection(
                                settings = lyricsPageSettings,
                                segmentedButtonColors = segmentedButtonColors,
                                onSettingsChange = { next -> viewModel.updateLyricsPageSettings(next) },
                                onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                            )
                        }
                    }
                }
                if (currentSection == SettingsSection.Translation) {
                    item(key = "app_language") { SettingsDetailCard { AppLanguageSettings() } }
                    item(key = "group:page_translation") {
                        SettingsDetailCard { PageTranslationSettingsSection(isActive = translationDataActive) }
                    }
                    item(key = "group:translation_config") {
                        SettingsDetailCard {
                        SubtitleTranslationSettingsHeader(
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                        )
                        SubtitleModelSettingsSection(
                            state = subtitleModelState,
                            selectedSourceIds = subtitleModelSourceIds,
                            deviceSupported = remember(context) {
                                SubtitleDeviceCapability.evaluate(context).supported
                            },
                            segmentedButtonColors = segmentedButtonColors,
                            onSourceSelected = { modelId, source ->
                                subtitleModelSourceIds[modelId] = source.id
                            },
                            onDownload = viewModel::downloadSubtitleModel,
                            onCancelDownload = viewModel::cancelSubtitleModelDownload,
                            onSelect = viewModel::selectSubtitleModel,
                            onDelete = { modelId -> pendingDeleteSubtitleModelId = modelId },
                            onClearFailure = viewModel::clearSubtitleModelFailure
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                        TranslationApiSettingsSection(
                            state = translationApiState,
                            onProviderSelected = viewModel::selectTranslationProvider,
                            onSaveCustom = viewModel::saveCustomTranslationApi,
                            onLoadModels = viewModel::loadCustomTranslationModels,
                            customTotalTokens = customTranslationTokenTotal,
                            deepSeekContent = {
                                DeepSeekTranslationSettingsSection(
                                    state = deepSeekApiKeyState,
                                    accountState = deepSeekAccountState,
                                    settings = deepSeekTranslationSettings,
                                    apiKeyInput = deepSeekApiKeyInput,
                                    compact = isCompact,
                                    segmentedButtonColors = segmentedButtonColors,
                                    onApiKeyInputChanged = { deepSeekApiKeyInput = it },
                                    onSave = { viewModel.saveDeepSeekApiKey(deepSeekApiKeyInput) },
                                    onThinkingEnabledChanged = viewModel::setDeepSeekThinkingEnabled,
                                    onReasoningEffortChanged = viewModel::setDeepSeekReasoningEffort,
                                    onFinalPolishEnabledChanged = viewModel::setDeepSeekFinalPolishEnabled,
                                    model = translationApiState.settings.deepSeekModel,
                                    modelSaving = translationApiState.saving || !translationApiState.loaded,
                                    onModelSelected = viewModel::selectDeepSeekModel,
                                    onLoadModels = viewModel::loadDeepSeekModels,
                                    activeTipKey = activeTipKey,
                                    onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                                )
                            },
                            customOptions = {
                                SettingsToggleRow(
                                    text = appString(R.string.ui_final_touches),
                                    checked = deepSeekTranslationSettings.finalPolishEnabled,
                                    onCheckedChange = viewModel::setDeepSeekFinalPolishEnabled,
                                    infoKey = "final_polish",
                                    infoTitle = appString(R.string.ui_final_touches),
                                    infoText = appString(R.string.ui_after_the_translation_is_completed_you_can_swipe),
                                    activeTipKey = activeTipKey,
                                    onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                                )
                            }
                        )
                    }
                }
                }
                if (currentSection == SettingsSection.About) {
                    item(key = "group:about_update") {
                        SettingsDetailCard {
                        val isDark = AsmrTheme.colorScheme.isDark
                        val buttonColors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colorScheme.primarySoft,
                            contentColor = if (isDark) colorScheme.onPrimaryContainer else colorScheme.primaryStrong
                        )

                        Text(
                            text = appString(R.string.ui_current_version_4f5e78, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        SettingsToggleRow(
                            text = appString(R.string.ui_automatically_check_for_updates_on_startup),
                            checked = autoUpdateCheckEnabled,
                            onCheckedChange = viewModel::setAutoUpdateCheckEnabled
                        )

                        val busy = updateState is AppUpdateState.Checking || updateState is AppUpdateState.Downloading
                        FilledTonalButton(
                            onClick = { viewModel.checkUpdate() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = buttonColors,
                            enabled = !busy
                        ) {
                            if (updateState is AppUpdateState.Checking) {
                                EaraLogoLoadingIndicator(size = 18.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(androidx.compose.ui.res.stringResource(R.string.ui_checking))
                            } else {
                                Text(androidx.compose.ui.res.stringResource(R.string.ui_check_for_updates))
                            }
                        }

                        when (val s = updateState) {
                            is AppUpdateState.UpToDate -> {
                                Text(
                                    text = appString(R.string.ui_already_latest, s.latestVersionName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.textSecondary
                                )
                            }
                            is AppUpdateState.UpdateAvailable -> {
                                Text(
                                    text = appString(R.string.ui_new_version_found, s.release.tagName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.textSecondary
                                )
                                FilledTonalButton(
                                    onClick = { viewModel.downloadLatestApk() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = buttonColors,
                                    enabled = !busy
                                ) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.ui_download_and_install))
                                }
                            }
                            is AppUpdateState.Downloading -> {
                                val total = s.totalBytes
                                val downloaded = s.downloadedBytes
                                val progress = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = appString(R.string.ui_downloading_460547, s.release.apkName),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (progress != null) {
                                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                    } else {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                            is AppUpdateState.ReadyToInstall -> {
                                Text(
                                    text = appString(R.string.ui_download_completed, s.release.tagName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.textSecondary
                                )
                                FilledTonalButton(
                                    onClick = {
                                        launchDownloadedApkInstall(context, s.apkPath)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = buttonColors
                                ) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.ui_install_updates))
                                }
                            }
                            is AppUpdateState.Failed -> {
                                Text(
                                    text = s.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                TextButton(
                                    onClick = { viewModel.resetUpdateState() },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.ui_close))
                                }
                            }
                            else -> {}
                        }
                    }
                }
                }

                if (currentSection == SettingsSection.SupportStatus) {
                    item(key = "group:support_status") {
                        SettingsDetailCard {
                        AppSupportStatusSection(
                            networkSettings = networkRouteSettings,
                            onUseSystemProxy = viewModel::useSystemProxy,
                            onAdvancedProxyApplied = viewModel::setAdvancedProxy,
                            onUseSystemDns = viewModel::useSystemDns,
                            onCustomDnsServerApplied = viewModel::setCustomDnsServer,
                        )
                    }
                }
                }

                if (currentSection == SettingsSection.AppCache) {
                    item(key = "group:app_cache") {
                        SettingsDetailCard {
                        AppCacheSettingsSection(
                            state = appCacheState,
                            onMaxSizeChanged = viewModel::setAppCacheMaxSizeMb,
                            onClearClick = { showClearAppCacheConfirmation = true },
                            onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged,
                        )
                    }
                }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
            }
        }
    }

    val removeRoot = pendingRemoveRoot
    if (removeRoot != null) {
        FlatActionDialog(
            onDismissRequest = { pendingRemoveRoot = null },
            message = appString(R.string.ui_the_directory_will_be_removed_from_the_list),
            actions = listOf(
                FlatDialogAction(appString(R.string.ui_cancel), onClick = { pendingRemoveRoot = null }),
                FlatDialogAction(
                    text = appString(R.string.ui_remove),
                    tone = FlatDialogActionTone.Danger,
                    onClick = {
                        val uri = runCatching { Uri.parse(removeRoot) }.getOrNull()
                        if (uri != null) {
                            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
                        }
                        libraryViewModel.removeScanRootAndDeleteAlbums(removeRoot)
                        pendingRemoveRoot = null
                    }
                )
            )
        )
    }
    val nextDownloadDestination = pendingDownloadDestination
    if (nextDownloadDestination != null) {
        FlatActionDialog(
            onDismissRequest = { pendingDownloadDestination = null },
            message = appString(R.string.ui_after_the_switch_the_local_libraries_download_tasks),
            actions = listOf(
                FlatDialogAction(appString(R.string.ui_cancel), onClick = { pendingDownloadDestination = null }),
                FlatDialogAction(
                    text = if (nextDownloadDestination is DownloadDestination.Default) appString(R.string.ui_reset) else appString(R.string.ui_switch),
                    tone = FlatDialogActionTone.Danger,
                    onClick = {
                        pendingDownloadDestination = null
                        viewModel.changeDownloadDirectory(nextDownloadDestination) {
                            libraryViewModel.scanCurrentDownloadDestinationAsImport()
                        }
                    },
                ),
            ),
        )
    }
    if (showClearAppCacheConfirmation) {
        FlatActionDialog(
            onDismissRequest = { showClearAppCacheConfirmation = false },
            message = appString(R.string.ui_the_cache_generated_by_online_images_online_audio),
            actions = listOf(
                FlatDialogAction(appString(R.string.ui_cancel), onClick = { showClearAppCacheConfirmation = false }),
                FlatDialogAction(
                    text = appString(R.string.ui_clean_up),
                    tone = FlatDialogActionTone.Danger,
                    onClick = {
                        showClearAppCacheConfirmation = false
                        viewModel.clearAppCache()
                    }
                )
            )
        )
    }
    pendingDeleteSubtitleModelId?.let { modelId ->
        val modelName = SubtitleTranscriptionModels.fromId(modelId)?.optionName ?: appString(R.string.ui_subtitles)
        FlatActionDialog(
            onDismissRequest = { pendingDeleteSubtitleModelId = null },
            message = appString(R.string.ui_are_you_sure_you_want_to_delete_the, modelName),
            actions = listOf(
                FlatDialogAction(appString(R.string.ui_cancel), onClick = { pendingDeleteSubtitleModelId = null }),
                FlatDialogAction(
                    text = appString(R.string.ui_delete_model),
                    tone = FlatDialogActionTone.Danger,
                    onClick = {
                        pendingDeleteSubtitleModelId = null
                        viewModel.deleteSubtitleModel(modelId)
                    }
                )
            )
        )
    }
}

@Composable
private fun NowPlayingLyricsSettingsSection(
    settings: NowPlayingLyricsSettings,
    onSettingsChange: (NowPlayingLyricsSettings) -> Unit,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit = {}
) {
    Text(androidx.compose.ui.res.stringResource(R.string.ui_play_page_lyrics), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    SettingsToggleRow(
        text = appString(R.string.ui_display_multiple_lines_in_full),
        checked = settings.multilineEnabled,
        onCheckedChange = { onSettingsChange(settings.copy(multilineEnabled = it)) }
    )
    Text(
        text = appString(R.string.ui_the_vertical_screen_classic_layout_uses_a_fixed),
        style = MaterialTheme.typography.bodySmall,
        color = AsmrTheme.colorScheme.textSecondary
    )
    SettingsSliderRow(
        text = appString(R.string.ui_highlight_font_size_sp, settings.highlightFontSizeSp.toInt()),
        value = settings.highlightFontSizeSp,
        range = 18f..36f,
        stepSize = 1f,
        onValueChange = { onSettingsChange(settings.copy(highlightFontSizeSp = it)) },
        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
    )
}

@Composable
internal fun SubtitleTranslationSettingsHeader(activeTipKey: String?, onToggleTip: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = appString(R.string.ui_subtitle_translation),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AsmrTheme.colorScheme.textPrimary
        )
        SettingsInfoTip(
            active = activeTipKey == "subtitle_translation",
            title = appString(R.string.ui_subtitle_translation),
            text = appString(R.string.ui_batch_translation_requires_local_audio_a_downloaded_and) +
                appString(R.string.ui_download_online_audio_first_once_configured_tap_batch),
            onToggle = { onToggleTip("subtitle_translation") },
            showBackground = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeepSeekTranslationSettingsSection(
    state: DeepSeekApiKeyUiState,
    accountState: DeepSeekAccountState = DeepSeekAccountState(),
    settings: DeepSeekTranslationSettings,
    apiKeyInput: String,
    compact: Boolean,
    segmentedButtonColors: SegmentedButtonColors,
    onApiKeyInputChanged: (String) -> Unit,
    onSave: () -> Unit,
    onThinkingEnabledChanged: (Boolean) -> Unit,
    onReasoningEffortChanged: (DeepSeekReasoningEffort) -> Unit,
    onFinalPolishEnabledChanged: (Boolean) -> Unit,
    activeTipKey: String? = null,
    onToggleTip: ((String) -> Unit)? = null,
    model: String = DEEPSEEK_SUBTITLE_MODEL,
    modelSaving: Boolean = false,
    onModelSelected: (String) -> Unit = {},
    onLoadModels: suspend (String) -> List<String>? = { null },
) {
    val colorScheme = AsmrTheme.colorScheme
    val actionButtonColors = settingsPrimaryTonalButtonColors()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "DeepSeek",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .testTag("deepseek_model_name")
        )
        if (state.configured) {
            Row(
                modifier = Modifier.weight(2f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = appString(R.string.ui_token_balance, formatDeepSeekTokenTotal(accountState.totalTokens), formatDeepSeekBalances(accountState.balances)),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    color = if (accountState.balanceAvailable == false) {
                        MaterialTheme.colorScheme.error
                    } else {
                        colorScheme.textSecondary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("deepseek_account_summary")
                )
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = appString(R.string.ui_api_key_configured),
                    tint = Color(0xFF3E9B63),
                    modifier = Modifier
                        .size(20.dp)
                        .testTag("deepseek_api_key_configured")
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val inputModifier = if (compact) {
            Modifier.weight(1f)
        } else {
            Modifier.widthIn(max = 280.dp)
        }
        TranslationSettingsTextField(
            label = "API Key",
            value = apiKeyInput,
            onValueChange = onApiKeyInputChanged,
            modifier = inputModifier
                .heightIn(min = 48.dp),
            tag = "deepseek_api_key_input",
            placeholder = "API Key（sk-…）",
            showLabel = false,
            visualTransformation = PasswordVisualTransformation(),
            keyboardType = KeyboardType.Password,
            enabled = !state.saving,
            isError = state.errorMessage != null,
            errorMessage = state.errorMessage.orEmpty(),
        )
        FilledTonalButton(
            onClick = onSave,
            enabled = apiKeyInput.isNotBlank() && !state.saving,
            modifier = Modifier
                .height(48.dp)
                .testTag("deepseek_api_key_action"),
            colors = actionButtonColors,
            shape = RoundedCornerShape(14.dp)
        ) {
            if (state.saving) {
                EaraLogoLoadingIndicator(size = 18.dp)
            } else {
                Text(if (state.configured) appString(R.string.ui_replace) else appString(R.string.ui_save))
            }
        }
    }
    state.errorMessage?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    key(apiKeyInput, state.saveVersion) {
        TranslationSettingsModelField(
            model = model, onModelChanged = onModelSelected,
            enabled = !state.saving && !modelSaving,
            canLoad = state.configured || apiKeyInput.isNotBlank(),
            loadInitially = state.configured && apiKeyInput.isBlank(),
            onLoad = { onLoadModels(apiKeyInput) },
            tagPrefix = "deepseek", allowManual = false,
        )
    }

    SettingsToggleRow(
        text = appString(R.string.ui_thinking_model),
        checked = settings.thinkingEnabled,
        onCheckedChange = onThinkingEnabledChanged
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = appString(R.string.ui_thinking_level),
            style = MaterialTheme.typography.bodyMedium,
            color = if (settings.thinkingEnabled) colorScheme.textPrimary else colorScheme.textTertiary
        )
        Spacer(modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .testTag("deepseek_reasoning_effort")
        ) {
            DeepSeekReasoningEffort.entries.forEachIndexed { index, effort ->
                SegmentedButton(
                    selected = settings.reasoningEffort == effort,
                    onClick = { onReasoningEffortChanged(effort) },
                    enabled = settings.thinkingEnabled,
                    modifier = Modifier.testTag("deepseek_reasoning_${effort.wireValue}"),
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = DeepSeekReasoningEffort.entries.size
                    ),
                    colors = segmentedButtonColors,
                    icon = {},
                    label = {
                        Text(
                            when (effort) {
                                DeepSeekReasoningEffort.LOW -> "Low"
                                DeepSeekReasoningEffort.HIGH -> "High"
                                DeepSeekReasoningEffort.MAX -> "Max"
                            }
                        )
                    }
                )
            }
        }
    }

    SettingsToggleRow(
        text = appString(R.string.ui_final_touches),
        checked = settings.finalPolishEnabled,
        onCheckedChange = onFinalPolishEnabledChanged,
        infoKey = "final_polish",
        infoTitle = appString(R.string.ui_final_touches),
        infoText = appString(R.string.ui_after_the_translation_is_completed_you_can_swipe),
        activeTipKey = activeTipKey,
        onToggleTip = onToggleTip
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubtitleModelSettingsSection(
    state: SubtitleModelState,
    selectedSourceIds: Map<String, String>,
    deviceSupported: Boolean,
    segmentedButtonColors: SegmentedButtonColors,
    onSourceSelected: (String, SubtitleModelDownloadSource) -> Unit,
    onDownload: (String, SubtitleModelDownloadSource) -> Unit,
    onCancelDownload: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearFailure: (String) -> Unit
) {
    var selectedModelId by rememberSaveable {
        mutableStateOf(
            state.operation?.modelId ?: SubtitleTranscriptionModels.SENSE_VOICE_SMALL_INT8.id
        )
    }
    LaunchedEffect(state.operation?.modelId) {
        state.operation?.modelId?.let { selectedModelId = it }
    }
    val model = SubtitleTranscriptionModels.fromId(selectedModelId)
        ?: SubtitleTranscriptionModels.default
    val installation = state.installation(model.id)
    val installed = installation is SubtitleModelInstallationState.Available
    val isActive = state.activeModelId == model.id
    val operation = state.operation?.takeIf { it.modelId == model.id }
    val running = operation is SubtitleModelOperation.Queued ||
        operation is SubtitleModelOperation.Downloading ||
        operation is SubtitleModelOperation.Verifying
    val anotherOperationRunning = state.operation != null &&
        state.operation.modelId != model.id &&
        state.operation !is SubtitleModelOperation.Failed
    val availableSources = configuredSubtitleModelDownloadSources(model)
    val selectedSource = operation?.source
        ?: SubtitleModelDownloadSource.fromId(selectedSourceIds[model.id])
        ?: availableSources.firstOrNull()
        ?: SubtitleModelDownloadSource.HuggingFace
    val colors = AsmrTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SubtitleTranscriptionModels.all.forEachIndexed { index, candidate ->
                SegmentedButton(
                    selected = model.id == candidate.id,
                    onClick = { selectedModelId = candidate.id },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = SubtitleTranscriptionModels.all.size
                    ),
                    colors = segmentedButtonColors,
                    icon = {},
                    modifier = Modifier.testTag("subtitle_model_choice_${candidate.id}"),
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (state.activeModelId == candidate.id) {
                                    appString(R.string.ui_current, candidate.optionName)
                                } else {
                                    candidate.optionName
                                },
                                maxLines = 1
                            )
                            Text(
                                text = Formatting.formatFileSize(candidate.artifactBytes),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = when {
                    isActive && installed -> appString(R.string.ui_currently_in_use)
                    isActive -> appString(R.string.ui_current_not_installed)
                    installed -> appString(R.string.ui_installed)
                    else -> appString(R.string.ui_not_installed)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) colors.primaryStrong else colors.textSecondary,
                modifier = Modifier.testTag("subtitle_model_status_${model.id}")
            )
        }

        if (!installed && availableSources.size > 1) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                availableSources.forEachIndexed { index, source ->
                    SegmentedButton(
                        selected = selectedSource == source,
                        onClick = {
                            onClearFailure(model.id)
                            onSourceSelected(model.id, source)
                        },
                        enabled = !running && !anotherOperationRunning,
                        shape = SegmentedButtonDefaults.itemShape(index, availableSources.size),
                        colors = segmentedButtonColors,
                        icon = {},
                        label = { Text(source.displayName) }
                    )
                }
            }
        }

        when (operation) {
            null -> Unit
            is SubtitleModelOperation.Queued -> {
                Text(androidx.compose.ui.res.stringResource(R.string.ui_waiting_to_download_subtitle_component), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is SubtitleModelOperation.Downloading -> {
                Text(operation.stage.displayName, style = MaterialTheme.typography.bodySmall)
                if (operation.totalBytes > 0L) {
                    val progress = (operation.downloadedBytes.toFloat() / operation.totalBytes)
                        .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            is SubtitleModelOperation.Verifying -> {
                Text(operation.stage.displayName, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is SubtitleModelOperation.Failed -> Text(
                text = operation.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        when {
            running -> FilledTonalButton(
                onClick = onCancelDownload,
                modifier = Modifier.fillMaxWidth(),
                colors = settingsPrimaryTonalButtonColors()
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.ui_cancel_download))
            }
            installed && !isActive -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onSelect(model.id) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("subtitle_model_select_${model.id}"),
                    colors = settingsPrimaryTonalButtonColors()
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ui_set_as_current))
                }
                FilledTonalButton(
                    onClick = { onDelete(model.id) },
                    colors = subtitleModelDeleteButtonColors()
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = appString(R.string.ui_delete_model))
                }
            }
            installed -> FilledTonalButton(
                onClick = { onDelete(model.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = subtitleModelDeleteButtonColors()
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(androidx.compose.ui.res.stringResource(R.string.ui_delete_model))
            }
            else -> FilledTonalButton(
                onClick = { onDownload(model.id, selectedSource) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("subtitle_model_download_${model.id}"),
                enabled = deviceSupported && availableSources.contains(selectedSource) &&
                    !anotherOperationRunning,
                colors = settingsPrimaryTonalButtonColors()
            ) {
                Text(
                    when {
                        !deviceSupported -> appString(R.string.ui_the_device_does_not_support)
                        availableSources.isEmpty() -> appString(R.string.ui_source_not_available)
                        anotherOperationRunning -> appString(R.string.ui_other_models_are_being_downloaded)
                        operation is SubtitleModelOperation.Failed -> appString(R.string.ui_download_again)
                        else -> appString(R.string.ui_download_model)
                    }
                )
            }
        }
    }
}

@Composable
private fun subtitleModelDeleteButtonColors(): ButtonColors =
    ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        disabledContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.48f),
        disabledContentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.48f)
    )

@Composable
private fun settingsPrimaryTonalButtonColors(): ButtonColors {
    val colorScheme = AsmrTheme.colorScheme
    val contentColor = if (colorScheme.isDark) {
        colorScheme.onPrimaryContainer
    } else {
        colorScheme.primaryStrong
    }
    return ButtonDefaults.filledTonalButtonColors(
        containerColor = colorScheme.primarySoft,
        contentColor = contentColor,
        disabledContainerColor = colorScheme.primarySoft.copy(alpha = 0.48f),
        disabledContentColor = contentColor.copy(alpha = 0.48f)
    )
}

@Composable
private fun AppCacheSettingsSection(
    state: AppCacheState,
    onMaxSizeChanged: (Int) -> Unit,
    onClearClick: () -> Unit,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragging || isPressed
    var draftSizeMb by remember { mutableFloatStateOf(state.maxSizeMb.toFloat()) }

    LaunchedEffect(state.maxSizeMb, isInteracting) {
        if (!isInteracting) draftSizeMb = state.maxSizeMb.toFloat()
    }

    Text(
        text = appString(R.string.ui_current_occupation, formatCacheSize(state.usedSizeBytes)),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = appString(R.string.ui_the_space_is_shared_by_network_pictures_online),
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.textSecondary,
    )
    SettingsSliderRow(
        text = appString(R.string.ui_cache_space_limit_mb, draftSizeMb.roundToInt()),
        value = draftSizeMb,
        range = AppCacheLimits.MinSizeMb.toFloat()..AppCacheLimits.MaxSizeMb.toFloat(),
        stepSize = AppCacheLimits.SizeStepMb.toFloat(),
        onValueChange = { draftSizeMb = it },
        onValueChangeFinished = { onMaxSizeChanged(draftSizeMb.roundToInt()) },
        interactionSource = interactionSource,
        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = appString(R.string.ui_minimum_mb, AppCacheLimits.MinSizeMb),
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textSecondary,
        )
        Text(
            text = appString(R.string.ui_maximum_mb, AppCacheLimits.MaxSizeMb),
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textSecondary,
        )
    }
    FilledTonalButton(
        onClick = onClearClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("clearAppCacheButton"),
        enabled = !state.isClearing,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = colorScheme.primarySoft,
            contentColor = if (colorScheme.isDark) colorScheme.onPrimaryContainer else colorScheme.primaryStrong,
        ),
    ) {
        if (state.isClearing) {
            EaraLogoLoadingIndicator(size = 18.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(androidx.compose.ui.res.stringResource(R.string.ui_cleaning_up))
        } else {
            Icon(Icons.Rounded.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(androidx.compose.ui.res.stringResource(R.string.ui_clear_app_cache))
        }
    }
}

private fun formatCacheSize(sizeBytes: Long): String {
    val safeBytes = sizeBytes.coerceAtLeast(0L)
    val megabytes = safeBytes / (1024.0 * 1024.0)
    return if (megabytes < 0.1) {
        "0 MB"
    } else if (megabytes < 10.0) {
        String.format(java.util.Locale.ROOT, "%.1f MB", megabytes)
    } else {
        "${megabytes.roundToInt()} MB"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsPageSettingsSection(
    settings: LyricsPageSettings,
    segmentedButtonColors: SegmentedButtonColors,
    onSettingsChange: (LyricsPageSettings) -> Unit,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit = {}
) {
    Text(androidx.compose.ui.res.stringResource(R.string.ui_lyrics_page), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    SettingsSliderRow(
        text = appString(R.string.ui_font_size_sp, settings.fontSizeSp.toInt()),
        value = settings.fontSizeSp,
        range = 18f..36f,
        stepSize = 1f,
        onValueChange = { onSettingsChange(settings.copy(fontSizeSp = it)) },
        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
    )
    SettingsSliderRow(
        text = appString(R.string.ui_font_shadow_sp, "%.1f".format(settings.strokeWidthSp)),
        value = settings.strokeWidthSp,
        range = 0f..3f,
        stepSize = 0.1f,
        onValueChange = { onSettingsChange(settings.copy(strokeWidthSp = it)) },
        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
    )
    SettingsSliderRow(
        text = appString(R.string.ui_line_spacing_x, "%.2f".format(settings.lineHeightMultiplier)),
        value = settings.lineHeightMultiplier,
        range = 0.1f..3.0f,
        stepSize = 0.1f,
        onValueChange = { onSettingsChange(settings.copy(lineHeightMultiplier = it)) },
        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(androidx.compose.ui.res.stringResource(R.string.ui_display_area), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = settings.displayAreaMode == 0,
                onClick = { onSettingsChange(settings.copy(displayAreaMode = 0)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                colors = segmentedButtonColors,
                icon = {},
                label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_full_screen)) }
            )
            SegmentedButton(
                selected = settings.displayAreaMode == 1,
                onClick = { onSettingsChange(settings.copy(displayAreaMode = 1)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                colors = segmentedButtonColors,
                icon = {},
                label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_upper_1_4)) }
            )
            SegmentedButton(
                selected = settings.displayAreaMode == 2,
                onClick = { onSettingsChange(settings.copy(displayAreaMode = 2)) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                colors = segmentedButtonColors,
                icon = {},
                label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_medium_1_4)) }
            )
            SegmentedButton(
                selected = settings.displayAreaMode == 3,
                onClick = { onSettingsChange(settings.copy(displayAreaMode = 3)) },
                shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                colors = segmentedButtonColors,
                icon = {},
                label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_next_1_4)) }
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(androidx.compose.ui.res.stringResource(R.string.ui_alignment), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = settings.align == 0,
                onClick = { onSettingsChange(settings.copy(align = 0)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                colors = segmentedButtonColors,
                icon = {},
                label = { Icon(Icons.AutoMirrored.Rounded.FormatAlignLeft, null) }
            )
            SegmentedButton(
                selected = settings.align == 1,
                onClick = { onSettingsChange(settings.copy(align = 1)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                colors = segmentedButtonColors,
                icon = {},
                label = { Icon(Icons.Rounded.FormatAlignCenter, null) }
            )
            SegmentedButton(
                selected = settings.align == 2,
                onClick = { onSettingsChange(settings.copy(align = 2)) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                colors = segmentedButtonColors,
                icon = {},
                label = { Icon(Icons.AutoMirrored.Rounded.FormatAlignRight, null) }
            )
        }
    }
}

private fun formatTreeRootLabel(uriString: String): String {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return uriString
    val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull().orEmpty()
    if (treeId.isBlank()) return uriString
    val doc = treeId.substringAfterLast(':', treeId)
    return doc.ifBlank { treeId }
}

@Composable
private fun SearchBlockedKeywordsSection(
    input: String,
    keywords: List<String>,
    onInputChange: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (String) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val addButtonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = colorScheme.primarySoft,
        contentColor = if (isDark) colorScheme.onPrimaryContainer else colorScheme.primaryStrong
    )
    var showHelp by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = appString(R.string.ui_block_keywords),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.textPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { showHelp = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = appString(R.string.ui_search_advanced_usage),
                    tint = colorScheme.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchBlockedKeywordInputField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )
            FilledTonalButton(
                onClick = onAddKeyword,
                enabled = input.isNotBlank(),
                colors = addButtonColors,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.ui_add_to))
            }
        }

        if (keywords.isEmpty()) {
            Text(
                text = appString(R.string.ui_no_blocked_keywords_yet),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.textSecondary
            )
        } else {
            SearchBlockedKeywordsChips(
                keywords = keywords,
                onRemoveKeyword = onRemoveKeyword
            )
        }
    }
    if (showHelp) {
        SearchBlockedKeywordsHelpDialog(onDismissRequest = { showHelp = false })
    }
}

@Composable
private fun SearchBlockedKeywordsHelpDialog(
    onDismissRequest: () -> Unit
) {
    FlatActionDialog(
        message = appString(R.string.ui_search_advanced_usage),
        onDismissRequest = onDismissRequest,
        actions = listOf(
            FlatDialogAction(
                text = appString(R.string.ui_knew),
                tone = FlatDialogActionTone.Primary,
                onClick = onDismissRequest
            )
        )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchHelpText(androidx.compose.ui.res.stringResource(R.string.ui_and_search_separated_by_spaces_rain_sound_helps))
            SearchHelpText(androidx.compose.ui.res.stringResource(R.string.ui_or_search_separated_by_vertical_lines_in_english))
            SearchHelpText(androidx.compose.ui.res.stringResource(R.string.ui_exclude_search_space_and_minus_sign_rain_sound))
            SearchHelpText(androidx.compose.ui.res.stringResource(R.string.ui_complete_search_wrapped_in_english_double_quotes_simplified))
        }
    }
}

@Composable
private fun SearchHelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = AsmrTheme.colorScheme.textSecondary
    )
}

@Composable
private fun SearchBlockedKeywordsChips(
    keywords: List<String>,
    onRemoveKeyword: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontalSpacing = 8.dp
    val verticalSpacing = 4.dp
    SubcomposeLayout(modifier = modifier.fillMaxWidth()) { constraints ->
        val itemSpacingPx = horizontalSpacing.roundToPx()
        val lineSpacingPx = verticalSpacing.roundToPx()
        val looseConstraints = Constraints()

        val keywordPlaceables = keywords.mapIndexed { index, keyword ->
            subcompose("keyword:$index:$keyword") {
                SearchBlockedKeywordChip(
                    keyword = keyword,
                    onRemoveKeyword = onRemoveKeyword
                )
            }.first().measure(looseConstraints)
        }

        val naturalSingleLineWidth = keywordPlaceables.sumOf { it.width } +
            itemSpacingPx * (keywordPlaceables.size - 1).coerceAtLeast(0)

        val contentFitsSingleLine = naturalSingleLineWidth <= constraints.maxWidth
        if (contentFitsSingleLine) {
            val rowHeight = keywordPlaceables.maxOfOrNull { it.height } ?: 0
            return@SubcomposeLayout layout(constraints.maxWidth, rowHeight) {
                var x = 0
                keywordPlaceables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(
                        x,
                        (rowHeight - placeable.height) / 2
                    )
                    x += placeable.width
                    if (index < keywordPlaceables.lastIndex) {
                        x += itemSpacingPx
                    }
                }
            }
        }
        val lines = mutableListOf<MutableList<Int>>()
        val lineHeights = mutableListOf<Int>()
        var currentLine = mutableListOf<Int>()
        var currentWidth = 0
        var currentHeight = 0

        fun commitLine() {
            if (currentLine.isEmpty()) return
            lines += currentLine
            lineHeights += currentHeight
            currentLine = mutableListOf()
            currentWidth = 0
            currentHeight = 0
        }

        keywordPlaceables.forEachIndexed { index, placeable ->
            val nextWidth = if (currentLine.isEmpty()) {
                placeable.width
            } else {
                currentWidth + itemSpacingPx + placeable.width
            }
            if (currentLine.isNotEmpty() && nextWidth > constraints.maxWidth) {
                commitLine()
            }
            currentWidth = if (currentLine.isEmpty()) {
                placeable.width
            } else {
                currentWidth + itemSpacingPx + placeable.width
            }
            currentHeight = maxOf(currentHeight, placeable.height)
            currentLine += index
        }
        commitLine()

        val layoutHeight = lineHeights.sum() +
            lineSpacingPx * (lineHeights.size - 1).coerceAtLeast(0)

        layout(constraints.maxWidth, layoutHeight) {
            var y = 0
            lines.forEachIndexed { lineIndex, line ->
                val lineHeight = lineHeights[lineIndex]
                var x = 0
                line.forEachIndexed { itemIndex, placeableIndex ->
                    val placeable = keywordPlaceables[placeableIndex]
                    placeable.placeRelative(
                        x,
                        y + (lineHeight - placeable.height) / 2
                    )
                    x += placeable.width
                    if (itemIndex < line.lastIndex) {
                        x += itemSpacingPx
                    }
                }
                y += lineHeight + lineSpacingPx
            }
        }
    }
}

@Composable
private fun SearchBlockedKeywordInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.textPrimary),
        cursorBrush = SolidColor(colorScheme.primary),
        decorationBox = { innerTextField ->
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                color = colorScheme.surface.copy(alpha = 0.38f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        tint = colorScheme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = appString(R.string.ui_block_keywords_such_as_fanfic),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            }
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SearchBlockedKeywordChip(
    keyword: String,
    onRemoveKeyword: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        InputChip(
            selected = false,
            onClick = { onRemoveKeyword(keyword) },
            modifier = modifier,
            label = {
                Text(
                    text = keyword,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = appString(R.string.ui_delete_b7bef2, keyword),
                    modifier = Modifier.size(16.dp)
                )
            }
        )
    }
}

@Composable
private fun SettingsSectionsPanel(
    selectedSection: SettingsSection? = null,
    onSectionClick: (SettingsSection) -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        contentColor = colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            SettingsSection.entries.forEachIndexed { index, section ->
                DeferredPageItem(
                    placeholder = { Spacer(Modifier.fillMaxWidth().height(64.dp)) },
                ) {
                    SettingsSectionOption(
                        section = section,
                        selected = section == selectedSection,
                        onClick = { onSectionClick(section) },
                    )
                }
                if (index < SettingsSection.entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 58.dp, end = 14.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionOption(
    section: SettingsSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colorScheme.primarySoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("settingsSection:${section.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = section.description,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.textSecondary,
                maxLines = if (LocalTabletLayout.current.twoPane) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = appString(R.string.ui_enter, section.title),
            tint = colorScheme.textSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SettingsDetailHeader(
    section: SettingsSection,
    showBack: Boolean = true,
    onBack: () -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("settingsDetailBack"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = appString(R.string.ui_return_to_settings),
                tint = colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.textPrimary,
            )
            Text(
                text = section.description,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.textSecondary,
            )
        }
    }
}

@Composable
private fun SettingsDetailCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        contentColor = colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsToggleRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    infoKey: String? = null,
    infoTitle: String = text,
    infoText: String? = null,
    activeTipKey: String? = null,
    onToggleTip: ((String) -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingsRowLabel(
            text = text,
            infoKey = infoKey,
            infoTitle = infoTitle,
            infoText = infoText,
            activeTipKey = activeTipKey,
            onToggleTip = onToggleTip,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSliderRow(
    text: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    stepSize: Float? = null,
    infoKey: String? = null,
    infoTitle: String = text,
    infoText: String? = null,
    activeTipKey: String? = null,
    onToggleTip: ((String) -> Unit)? = null,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit = {}
) {
    val sliderInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isDragging by sliderInteractionSource.collectIsDraggedAsState()
    val isPressed by sliderInteractionSource.collectIsPressedAsState()
    val isInteracting = isDragging || isPressed
    val steps = stepSize
        ?.takeIf { it > 0f }
        ?.let { ((range.endInclusive - range.start) / it).toInt() - 1 }
        ?.coerceAtLeast(0)
        ?: 0
    LaunchedEffect(isInteracting, onHorizontalControlInteractionChanged) {
        onHorizontalControlInteractionChanged(isInteracting)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsRowLabel(
            text = text,
            infoKey = infoKey,
            infoTitle = infoTitle,
            infoText = infoText,
            activeTipKey = activeTipKey,
            onToggleTip = onToggleTip
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = sliderInteractionSource,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DeferredCommitSettingsSliderRow(
    committedValue: Float,
    range: ClosedFloatingPointRange<Float>,
    stepSize: Float? = null,
    textForValue: (Float) -> String,
    onValueCommitted: (Float) -> Unit,
    infoKey: String? = null,
    infoTitle: String = "",
    infoText: String? = null,
    activeTipKey: String? = null,
    onToggleTip: ((String) -> Unit)? = null,
    onHorizontalControlInteractionChanged: (Boolean) -> Unit = {}
) {
    SettingsSliderRow(
        text = textForValue(committedValue),
        value = committedValue.coerceIn(range.start, range.endInclusive),
        range = range,
        stepSize = stepSize,
        infoKey = infoKey,
        infoTitle = infoTitle.ifBlank { textForValue(committedValue) },
        infoText = infoText,
        activeTipKey = activeTipKey,
        onToggleTip = onToggleTip,
        onValueChange = onValueCommitted,
        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
    )
}

@Composable
private fun SettingsRowLabel(
    text: String,
    infoKey: String? = null,
    infoTitle: String = text,
    infoText: String? = null,
    activeTipKey: String? = null,
    onToggleTip: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (infoKey != null && !infoText.isNullOrBlank() && onToggleTip != null) {
            SettingsInfoTip(
                active = activeTipKey == infoKey,
                title = infoTitle,
                text = infoText,
                onToggle = { onToggleTip(infoKey) }
            )
        }
    }
}

@Composable
private fun SettingsInfoTip(
    active: Boolean,
    title: String,
    text: String,
    onToggle: () -> Unit,
    showBackground: Boolean = true
) {
    val density = LocalDensity.current
    val offset = with(density) { IntOffset(0, 26.dp.roundToPx()) }

    Surface(
        shape = CircleShape,
        color = if (showBackground) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else Color.Transparent
    ) {
        Box {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = appString(R.string.ui_description, title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (active) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = offset,
                    onDismissRequest = onToggle,
                    properties = PopupProperties(
                        focusable = true,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true
                    )
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 10.dp,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = 260.dp).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colorScheme.primarySoft,
            selectedLabelColor = if (isDark) colorScheme.onPrimaryContainer else colorScheme.primaryStrong
        )
    )
}

@Composable
private fun ThemeColorDot(color: Color?, selected: Boolean, onClick: () -> Unit) {
    val fill = color ?: AsmrTheme.colorScheme.primaryStrong
    val borderColor = if (selected) AsmrTheme.colorScheme.onSurface else AsmrTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val borderWidth = if (selected) 2.dp else 1.dp
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(fill)
            .border(borderWidth, borderColor, CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun ThemeMonochromeDot(selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) AsmrTheme.colorScheme.onSurface else AsmrTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val borderWidth = if (selected) 2.dp else 1.dp
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(Color(0xFF68717C), Color(0xFFE1E7ED))
                )
            )
            .border(borderWidth, borderColor, CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun PreviewModeInfoTip(active: Boolean, onToggle: () -> Unit) {
    val density = LocalDensity.current
    val offset = with(density) { IntOffset(0, 26.dp.roundToPx()) }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Box {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (active) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = offset,
                    onDismissRequest = onToggle,
                    properties = PopupProperties(
                        focusable = true,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true
                    )
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 10.dp,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = 260.dp).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = appString(R.string.ui_background_cover_preview_method),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = appString(R.string.ui_off_the_background_and_cover_remain_centered_and),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = appString(R.string.ui_sliding_both_the_play_page_cover_and_lyrics),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = appString(R.string.ui_rotate_preview_other_areas_of_the_cover_by),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
