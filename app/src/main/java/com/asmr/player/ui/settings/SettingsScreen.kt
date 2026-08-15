package com.asmr.player.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Sync
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
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.BuildConfig
import com.asmr.player.cache.AppCacheLimits
import com.asmr.player.cache.AppCacheState
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.DeepSeekReasoningEffort
import com.asmr.player.data.settings.DeepSeekTranslationSettings
import com.asmr.player.data.settings.FloatingLyricsSettings
import com.asmr.player.data.settings.LyricsPageSettings
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
import com.asmr.player.ui.common.AppSupportStatusSection
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.FlatActionDialog
import com.asmr.player.ui.common.FlatDialogAction
import com.asmr.player.ui.common.FlatDialogActionTone
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.common.smoothScrollToTop
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.common.withAddedBottomPadding
import com.asmr.player.ui.common.collectAsStateWhileActive
import com.asmr.player.ui.update.launchDownloadedApkInstall
import com.asmr.player.util.Formatting
import kotlin.math.abs
import kotlin.math.roundToInt

private val SettingsPageHorizontalPadding = 8.dp
private const val MONOCHROME_THEME_SENTINEL = 0x01000000
private const val SettingsDetailEnterDurationMs = 440
private const val SettingsDetailExitDurationMs = 420
private val SettingsDetailSlideEasing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

private enum class SettingsSection(
    val title: String,
    val description: String,
    val icon: ImageVector,
) {
    LocalLibrary("本地库", "管理扫描目录、刷新本地内容与同步元数据", Icons.Rounded.Folder),
    BlockedKeywords("屏蔽词", "过滤搜索结果中不想看到的关键词", Icons.Rounded.Block),
    Appearance("外观", "调整主题、主题色与播放页背景", Icons.Rounded.Palette),
    Playback("播放设置", "管理迷你播放栏、音频输出与淡入淡出", Icons.Rounded.Headphones),
    Lyrics("歌词", "配置歌词页与悬浮歌词的显示效果", Icons.Rounded.Lyrics),
    Translation("翻译配置", "管理本地字幕模型与 DeepSeek 翻译", Icons.Rounded.Translate),
    SupportStatus("支持与状态", "查看项目支持方式与相关服务状态", Icons.Rounded.Favorite),
    AppCache("APP 缓存", "设置缓存容量上限并清理缓存", Icons.Rounded.Storage),
    About("关于", "查看版本信息并检查应用更新", Icons.Rounded.Info),
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
    var selectedSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    var retainedSection by remember { mutableStateOf(selectedSection) }
    val currentOnDetailPageChanged by rememberUpdatedState(onDetailPageChanged)
    val localLibraryDataActive = isDataActive && selectedSection == SettingsSection.LocalLibrary
    val blockedKeywordsDataActive = isDataActive && selectedSection == SettingsSection.BlockedKeywords
    val appearanceDataActive = isDataActive && selectedSection == SettingsSection.Appearance
    val playbackDataActive = isDataActive && selectedSection == SettingsSection.Playback
    val lyricsDataActive = isDataActive && selectedSection == SettingsSection.Lyrics
    val translationDataActive = isDataActive && selectedSection == SettingsSection.Translation
    val aboutDataActive = isDataActive && selectedSection == SettingsSection.About
    val appCacheDataActive = isDataActive && selectedSection == SettingsSection.AppCache

    LaunchedEffect(selectedSection) {
        currentOnDetailPageChanged(selectedSection != null)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnDetailPageChanged(false) }
    }
    LaunchedEffect(translationDataActive, viewModel) {
        if (translationDataActive) viewModel.prepareSettingsData()
    }
    val floatingLyricsEnabled by viewModel.floatingLyricsEnabled.collectAsStateWhileActive(lyricsDataActive)
    val floatingSettings by viewModel.floatingLyricsSettings.collectAsStateWhileActive(lyricsDataActive)
    val lyricsPageSettings by viewModel.lyricsPageSettings.collectAsStateWhileActive(lyricsDataActive)
    val dynamicPlayerHueEnabled by viewModel.dynamicPlayerHueEnabled.collectAsStateWhileActive(appearanceDataActive)
    val themeMode by viewModel.themeMode.collectAsStateWhileActive(appearanceDataActive)
    val staticHueArgbLight by viewModel.staticHueArgbLight.collectAsStateWhileActive(appearanceDataActive)
    val staticHueArgbDark by viewModel.staticHueArgbDark.collectAsStateWhileActive(appearanceDataActive)
    val coverBackgroundEnabled by viewModel.coverBackgroundEnabled.collectAsStateWhileActive(appearanceDataActive)
    val coverBackgroundClarity by viewModel.coverBackgroundClarity.collectAsStateWhileActive(appearanceDataActive)
    val coverPreviewMode by viewModel.coverPreviewMode.collectAsStateWhileActive(appearanceDataActive)
    val pauseOnOutputDisconnect by viewModel.pauseOnOutputDisconnect.collectAsStateWhileActive(playbackDataActive)
    val resumeOnOutputConnect by viewModel.resumeOnOutputConnect.collectAsStateWhileActive(playbackDataActive)
    val pauseOnOtherAudio by viewModel.pauseOnOtherAudio.collectAsStateWhileActive(playbackDataActive)
    val playFadeInMs by viewModel.playFadeInMs.collectAsStateWhileActive(playbackDataActive)
    val pauseFadeOutMs by viewModel.pauseFadeOutMs.collectAsStateWhileActive(playbackDataActive)
    val sfwHideSystemControls by viewModel.sfwHideSystemControls.collectAsStateWhileActive(playbackDataActive)
    val showMiniPlayerBar by viewModel.showMiniPlayerBar.collectAsStateWhileActive(playbackDataActive)
    val searchBlockedKeywords by viewModel.searchBlockedKeywords.collectAsStateWhileActive(blockedKeywordsDataActive)
    val appCacheState by viewModel.appCacheState.collectAsStateWhileActive(appCacheDataActive)
    val subtitleModelState by viewModel.subtitleModelState.collectAsStateWhileActive(translationDataActive)
    val deepSeekApiKeyState by viewModel.deepSeekApiKeyState.collectAsStateWhileActive(translationDataActive)
    val deepSeekAccountState by viewModel.deepSeekAccountState.collectAsStateWhileActive(translationDataActive)
    val deepSeekTranslationSettings by viewModel.deepSeekTranslationSettings.collectAsStateWhileActive(translationDataActive)
    val updateState by viewModel.updateState.collectAsStateWhileActive(aboutDataActive)
    val autoUpdateCheckEnabled by viewModel.autoUpdateCheckEnabled.collectAsStateWhileActive(aboutDataActive)
    val scanRoots by libraryViewModel.scanRoots.collectAsStateWhileActive(localLibraryDataActive)
    val bulkProgress by libraryViewModel.bulkProgress.collectAsStateWhileActive(localLibraryDataActive)
    val isGlobalSyncRunning by libraryViewModel.isGlobalSyncRunning.collectAsStateWhileActive(localLibraryDataActive)
    val context = LocalContext.current
    val colorScheme = AsmrTheme.colorScheme
    val rootListState = rememberLazyListState()
    val detailListState = rememberLazyListState()
    val listState = if (selectedSection == null) rootListState else detailListState
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
    var pendingRemoveRoot by remember { mutableStateOf<String?>(null) }

    // 屏幕尺寸判断
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    BackHandler(enabled = isActive && selectedSection != null) {
        selectedSection = null
    }
    Scaffold(
        contentWindowInsets = StableWindowInsets.navigationBars,
        containerColor = Color.Transparent,
        contentColor = colorScheme.onBackground
    ) { padding ->
        LaunchedEffect(isActive) {
            if (isActive) return@LaunchedEffect
            deepSeekApiKeyInput = ""
            rootListState.stopScroll(MutatePriority.PreventUserInput)
            detailListState.stopScroll(MutatePriority.PreventUserInput)
        }
        LaunchedEffect(appCacheDataActive) {
            if (appCacheDataActive) viewModel.refreshAppCacheSize()
        }
        LaunchedEffect(selectedSection) {
            if (selectedSection != null) detailListState.scrollToItem(0)
        }
        LaunchedEffect(scrollToTopSignal) {
            if (scrollToTopSignal == 0L) return@LaunchedEffect
            listState.smoothScrollToTop()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            val contentModifier = if (isCompact) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
            }
            LazyColumn(
                state = rootListState,
                modifier = contentModifier.thinScrollbar(rootListState),
                flingBehavior = rememberCalmScrollableFlingBehavior(),
                contentPadding = PaddingValues(horizontal = SettingsPageHorizontalPadding, vertical = 10.dp)
                    .withAddedBottomPadding(LocalBottomOverlayPadding.current),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "settings_sections") {
                    SettingsSectionsPanel(
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
                visible = selectedSection != null,
                modifier = contentModifier,
                enter = settingsDetailEnterTransition(),
                exit = settingsDetailExitTransition(),
                label = "settingsDetailTransition",
            ) {
                val currentSection = retainedSection ?: return@AnimatedVisibility
                LazyColumn(
                    state = detailListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.background)
                        .thinScrollbar(detailListState),
                    flingBehavior = rememberCalmScrollableFlingBehavior(),
                    contentPadding = PaddingValues(horizontal = SettingsPageHorizontalPadding, vertical = 10.dp)
                        .withAddedBottomPadding(LocalBottomOverlayPadding.current),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "detail_header:${currentSection.name}") {
                        SettingsDetailHeader(
                            section = currentSection,
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
                        Text("刷新本地")
                    }
                    FilledTonalButton(
                        onClick = { libraryViewModel.syncMetadata() },
                        modifier = Modifier.weight(1f),
                        colors = buttonColors,
                        enabled = !isGlobalSyncRunning
                    ) {
                        Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = buttonColors.contentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("云同步")
                    }
                }

                FilledTonalButton(
                    onClick = { pickRootLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = buttonColors
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = buttonColors.contentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加目录")
                }

                bulkProgress?.let { progress ->
                    val title = when (progress.phase) {
                        BulkPhase.ScanningLocal -> "正在扫描本地库"
                        BulkPhase.SyncingCloud -> "正在云同步"
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
                                            text = "专辑 ${progress.current}/${progress.total}：${progress.currentAlbumTitle}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        Text(
                                            text = "进度 ${progress.current}/${progress.total}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.textSecondary
                                        )
                                    }
                                }
                                TextButton(onClick = { libraryViewModel.cancelBulkTask() }) { Text("取消") }
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
                                    text = "正在扫描：${progress.currentFile}",
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
                    Text("已添加目录", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (scanRoots.isEmpty()) {
                        Text("暂无", style = MaterialTheme.typography.bodySmall, color = colorScheme.textSecondary)
                    } else {
                        scanRoots.forEach { root ->
                            val label = remember(root) { formatTreeRootLabel(root) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                    Text(root, style = MaterialTheme.typography.bodySmall, color = colorScheme.textSecondary, maxLines = 1)
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
                            Text("主题模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                ThemeModeChip(
                                    label = "系统",
                                    selected = themeMode == "system",
                                    onClick = { viewModel.setThemeMode("system") }
                                )
                                ThemeModeChip(
                                    label = "浅色",
                                    selected = themeMode == "light",
                                    onClick = { viewModel.setThemeMode("light") }
                                )
                                ThemeModeChip(
                                    label = "深色",
                                    selected = themeMode == "dark",
                                    onClick = { viewModel.setThemeMode("dark") }
                                )
                                ThemeModeChip(
                                    label = "柔和深色",
                                    selected = themeMode == "soft_dark",
                                    onClick = { viewModel.setThemeMode("soft_dark") }
                                )
                            }

                            Text("主题色", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
                            text = "封面动态主题（全局）",
                            checked = dynamicPlayerHueEnabled,
                            onCheckedChange = viewModel::setDynamicPlayerHueEnabled
                        )

                        SettingsToggleRow(
                            text = "播放页/歌词页封面背景",
                            checked = coverBackgroundEnabled,
                            onCheckedChange = viewModel::setCoverBackgroundEnabled
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("背景封面预览方式", style = MaterialTheme.typography.bodyMedium)
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
                                    label = { Text("关闭") }
                                )
                                SegmentedButton(
                                    selected = coverPreviewMode == CoverPreviewMode.Drag,
                                    onClick = { viewModel.setCoverPreviewMode(CoverPreviewMode.Drag) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                    colors = segmentedButtonColors,
                                    icon = {},
                                    label = { Text("滑动") }
                                )
                                SegmentedButton(
                                    selected = coverPreviewMode == CoverPreviewMode.Motion,
                                    onClick = { viewModel.setCoverPreviewMode(CoverPreviewMode.Motion) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                    colors = segmentedButtonColors,
                                    icon = {},
                                    label = { Text("转动") }
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
                                        "封面背景清晰度：${(value.coerceIn(0f, 1f) * 100).toInt()}%"
                                    },
                                    onValueCommitted = viewModel::setCoverBackgroundClarity,
                                    onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                                )
                            }
                        }
                    }
                }
                }

                if (currentSection == SettingsSection.Playback) {
                    item(key = "group:playback") {
                        SettingsDetailCard {
                            SettingsToggleRow(
                                text = "迷你播放栏开关",
                                checked = showMiniPlayerBar,
                                onCheckedChange = viewModel::setShowMiniPlayerBar,
                                infoKey = "show_mini_player_bar",
                                infoTitle = "迷你播放栏",
                                infoText = "关闭后，应用底部的迷你播放栏会隐藏，同时页面底部不会再为它预留空白。",
                                activeTipKey = activeTipKey,
                                onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                            )
                            SettingsToggleRow(
                                text = "SFW开关",
                                checked = sfwHideSystemControls,
                                onCheckedChange = viewModel::setSfwHideSystemControls,
                                infoKey = "sfw_hide_system_controls",
                                infoTitle = "SFW",
                                infoText = "开启后会尽量隐藏系统锁屏和通知栏里的媒体控制按钮，但仍保留后台播放所需的前台通知。",
                                activeTipKey = activeTipKey,
                                onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                        SettingsToggleRow(
                            text = "断开扬声器、有线/蓝牙耳机或蓝牙关闭时立刻暂停播放",
                            checked = pauseOnOutputDisconnect,
                            onCheckedChange = viewModel::setPauseOnOutputDisconnect,
                            infoKey = "pause_on_output_disconnect",
                            infoTitle = "输出断开自动暂停",
                            infoText = "播放中如果外放、耳机或蓝牙输出被移除，会立刻暂停，避免声音突然外放。",
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                        )
                        SettingsToggleRow(
                            text = "连接有线/蓝牙耳机或其他外接输出时继续播放",
                            checked = resumeOnOutputConnect,
                            onCheckedChange = viewModel::setResumeOnOutputConnect,
                            infoKey = "resume_on_output_connect",
                            infoTitle = "输出接入自动恢复",
                            infoText = "检测到耳机、蓝牙耳机、USB 音频、HDMI 或 AUX 等外接输出接入时，如果播放器当前处于暂停，会自动尝试恢复播放；手机扬声器不触发。",
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                        )
                        DeferredCommitSettingsSliderRow(
                            committedValue = playFadeInMs.toFloat(),
                            range = 0f..3000f,
                            stepSize = 100f,
                            textForValue = { value -> "播放时逐渐增强音量: ${value.toInt()}ms" },
                            onValueCommitted = { viewModel.setPlayFadeInMs(it.toInt()) },
                            infoKey = "play_fade_in",
                            infoTitle = "播放淡入",
                            infoText = "点击播放时，音量会在设定时长内从低到高平滑升到正常值。",
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key },
                            onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                        )
                        DeferredCommitSettingsSliderRow(
                            committedValue = pauseFadeOutMs.toFloat(),
                            range = 0f..3000f,
                            stepSize = 100f,
                            textForValue = { value -> "暂停时逐渐降低音量: ${value.toInt()}ms" },
                            onValueCommitted = { viewModel.setPauseFadeOutMs(it.toInt()) },
                            infoKey = "pause_fade_out",
                            infoTitle = "暂停淡出",
                            infoText = "点击暂停时，音量会在设定时长内逐渐降到 0，然后再真正暂停。",
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key },
                            onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                        )
                        SettingsToggleRow(
                            text = "其他应用播放音/视频时暂停",
                            checked = pauseOnOtherAudio,
                            onCheckedChange = viewModel::setPauseOnOtherAudio,
                            infoKey = "pause_on_other_audio",
                            infoTitle = "音频焦点暂停",
                            infoText = "当其他音乐或视频应用抢占音频焦点时暂停播放；普通通知提示音不会触发。",
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
                        )
                    }
                }
                }

                // 悬浮歌词
                if (currentSection == SettingsSection.Lyrics) {
                    item(key = "group:lyrics") {
                        SettingsDetailCard {
                            SettingsToggleRow(
                                text = "开启悬浮歌词",
                                checked = floatingLyricsEnabled,
                                onCheckedChange = { viewModel.setFloatingLyricsEnabled(it) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                        LyricsPageSettingsSection(
                            settings = lyricsPageSettings,
                            segmentedButtonColors = segmentedButtonColors,
                            onSettingsChange = { next -> viewModel.updateLyricsPageSettings(next) },
                            onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        Text("悬浮歌词细节", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                        if (!overlayGranted && floatingLyricsEnabled) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    overlayLauncher.launch(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text("授权悬浮窗权限")
                            }
                        }

                        if (floatingLyricsEnabled && overlayGranted) {
                            SettingsSliderRow(
                                text = "字体大小: ${floatingSettings.size.toInt()}",
                                value = floatingSettings.size,
                                range = 12f..32f,
                                onValueChange = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(size = it)) },
                                onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                            )

                            SettingsSliderRow(
                                text = "背景透明度: ${(floatingSettings.opacity * 100).toInt()}%",
                                value = floatingSettings.opacity,
                                range = 0f..1f,
                                onValueChange = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(opacity = it)) },
                                onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                            )

                            SettingsSliderRow(
                                text = "垂直位置 (Y轴)",
                                value = floatingSettings.yOffset.toFloat(),
                                range = 0f..2000f,
                                onValueChange = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(yOffset = it.toInt())) },
                                onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("对齐方式", style = MaterialTheme.typography.bodyMedium)
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
                                Text("歌词颜色", style = MaterialTheme.typography.bodyMedium)
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
                                text = "点击穿透(锁定位置)",
                                checked = !floatingSettings.touchable,
                                onCheckedChange = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(touchable = !it)) }
                            )
                        }
                    }
                }
                }
                if (currentSection == SettingsSection.Translation) {
                    item(key = "group:translation_config") {
                        SettingsDetailCard {
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
                            activeTipKey = activeTipKey,
                            onToggleTip = { key -> activeTipKey = if (activeTipKey == key) null else key }
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
                            text = "当前版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        SettingsToggleRow(
                            text = "启动时自动检查更新",
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
                                Text("检查中…")
                            } else {
                                Text("检查更新")
                            }
                        }

                        when (val s = updateState) {
                            is AppUpdateState.UpToDate -> {
                                Text(
                                    text = "已是最新：${s.latestVersionName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.textSecondary
                                )
                            }
                            is AppUpdateState.UpdateAvailable -> {
                                Text(
                                    text = "发现新版本：${s.release.tagName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.textSecondary
                                )
                                FilledTonalButton(
                                    onClick = { viewModel.downloadLatestApk() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = buttonColors,
                                    enabled = !busy
                                ) {
                                    Text("下载并安装")
                                }
                            }
                            is AppUpdateState.Downloading -> {
                                val total = s.totalBytes
                                val downloaded = s.downloadedBytes
                                val progress = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "正在下载：${s.release.apkName}",
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
                                    text = "下载完成：${s.release.tagName}",
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
                                    Text("安装更新")
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
                                    Text("关闭")
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
                        AppSupportStatusSection()
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
            message = "将从列表中移除该目录，后续不会再扫描它。",
            actions = listOf(
                FlatDialogAction("取消", onClick = { pendingRemoveRoot = null }),
                FlatDialogAction(
                    text = "移除",
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
    if (showClearAppCacheConfirmation) {
        FlatActionDialog(
            onDismissRequest = { showClearAppCacheConfirmation = false },
            message = "将清理网络图片、在线音频播放和在线预览产生的缓存，不会删除下载内容、本地媒体或收藏数据。",
            actions = listOf(
                FlatDialogAction("取消", onClick = { showClearAppCacheConfirmation = false }),
                FlatDialogAction(
                    text = "清理",
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
        val modelName = SubtitleTranscriptionModels.fromId(modelId)?.optionName ?: "字幕"
        FlatActionDialog(
            onDismissRequest = { pendingDeleteSubtitleModelId = null },
            message = "确定删除“$modelName”模型？约 29 MiB 的公共运行时会保留，之后下载任一模型时无需重复安装。",
            actions = listOf(
                FlatDialogAction("取消", onClick = { pendingDeleteSubtitleModelId = null }),
                FlatDialogAction(
                    text = "删除模型",
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
    onToggleTip: ((String) -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    val actionButtonColors = settingsPrimaryTonalButtonColors()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = DEEPSEEK_SUBTITLE_MODEL,
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
                modifier = Modifier.width(if (compact) 208.dp else 248.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Token ${formatDeepSeekTokenTotal(accountState.totalTokens)} · 余额 ${formatDeepSeekBalances(accountState.balances)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (accountState.balanceAvailable == false) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("deepseek_account_summary")
                )
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "API Key 已配置",
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
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = onApiKeyInputChanged,
            modifier = inputModifier
                .height(48.dp)
                .testTag("deepseek_api_key_input"),
            placeholder = {
                Text(
                    text = "API Key（sk-…）",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !state.saving,
            isError = state.errorMessage != null
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
                Text(if (state.configured) "替换" else "保存")
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

    SettingsToggleRow(
        text = "思考模式",
        checked = settings.thinkingEnabled,
        onCheckedChange = onThinkingEnabledChanged
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "思考等级",
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
        text = "最终润色",
        checked = settings.finalPolishEnabled,
        onCheckedChange = onFinalPolishEnabledChanged,
        infoKey = "final_polish",
        infoTitle = "最终润色",
        infoText = "翻译完成后，可在任务管理中左滑作品卡片，对现有中文字幕进行整体润色。此操作会额外消耗 Token。",
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
                                    "${candidate.optionName} · 当前"
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
                    isActive && installed -> "当前使用"
                    isActive -> "当前（未安装）"
                    installed -> "已安装"
                    else -> "未安装"
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
                Text("等待下载字幕组件", style = MaterialTheme.typography.bodySmall)
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
                Text("取消下载")
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
                    Text("设为当前")
                }
                FilledTonalButton(
                    onClick = { onDelete(model.id) },
                    colors = subtitleModelDeleteButtonColors()
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除模型")
                }
            }
            installed -> FilledTonalButton(
                onClick = { onDelete(model.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = subtitleModelDeleteButtonColors()
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("删除模型")
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
                        !deviceSupported -> "设备不支持"
                        availableSources.isEmpty() -> "来源不可用"
                        anotherOperationRunning -> "其他模型正在下载"
                        operation is SubtitleModelOperation.Failed -> "重新下载"
                        else -> "下载模型"
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
        text = "当前占用：${formatCacheSize(state.usedSizeBytes)}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "空间由网络图片、在线音频播放和在线预览缓存共享。缓存满后会优先清理较早使用的资源。",
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.textSecondary,
    )
    SettingsSliderRow(
        text = "缓存空间上限：${draftSizeMb.roundToInt()} MB",
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
            text = "最小 ${AppCacheLimits.MinSizeMb} MB",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textSecondary,
        )
        Text(
            text = "最大 ${AppCacheLimits.MaxSizeMb} MB",
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
            Text("正在清理…")
        } else {
            Icon(Icons.Rounded.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("清理 APP 缓存")
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
    Text("歌词页", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    SettingsSliderRow(
        text = "字体大小: ${settings.fontSizeSp.toInt()}sp",
        value = settings.fontSizeSp,
        range = 18f..36f,
        stepSize = 1f,
        onValueChange = { onSettingsChange(settings.copy(fontSizeSp = it)) },
        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
    )
    SettingsSliderRow(
        text = "字体阴影: ${"%.1f".format(settings.strokeWidthSp)}sp",
        value = settings.strokeWidthSp,
        range = 0f..3f,
        stepSize = 0.1f,
        onValueChange = { onSettingsChange(settings.copy(strokeWidthSp = it)) },
        onHorizontalControlInteractionChanged = onHorizontalControlInteractionChanged
    )
    SettingsSliderRow(
        text = "行间距: ${"%.2f".format(settings.lineHeightMultiplier)}x",
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
        Text("显示区域", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = settings.displayAreaMode == 0,
                onClick = { onSettingsChange(settings.copy(displayAreaMode = 0)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                colors = segmentedButtonColors,
                icon = {},
                label = { Text("全屏") }
            )
            SegmentedButton(
                selected = settings.displayAreaMode == 1,
                onClick = { onSettingsChange(settings.copy(displayAreaMode = 1)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                colors = segmentedButtonColors,
                icon = {},
                label = { Text("上1/4") }
            )
            SegmentedButton(
                selected = settings.displayAreaMode == 2,
                onClick = { onSettingsChange(settings.copy(displayAreaMode = 2)) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                colors = segmentedButtonColors,
                icon = {},
                label = { Text("中1/4") }
            )
            SegmentedButton(
                selected = settings.displayAreaMode == 3,
                onClick = { onSettingsChange(settings.copy(displayAreaMode = 3)) },
                shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                colors = segmentedButtonColors,
                icon = {},
                label = { Text("下1/4") }
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("对齐方式", style = MaterialTheme.typography.bodyMedium)
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
                text = "屏蔽关键词",
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
                    contentDescription = "搜索高级用法",
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
                Text("添加")
            }
        }

        if (keywords.isEmpty()) {
            Text(
                text = "暂无屏蔽关键词",
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
        message = "搜索高级用法",
        onDismissRequest = onDismissRequest,
        actions = listOf(
            FlatDialogAction(
                text = "知道了",
                tone = FlatDialogActionTone.Primary,
                onClick = onDismissRequest
            )
        )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchHelpText("和 搜索(空格分割)：「雨声 助眠」，表示同时包含指定关键词")
            SearchHelpText("或 搜索(英文竖线分割)：「雨声|助眠」，表示包含一个或多个指定关键词均可")
            SearchHelpText("排除 搜索(空格与减号)：「雨声 -助眠」，表示排除指定关键词")
            SearchHelpText("完整 搜索(英文双引号包裹)：「\"【简体中文】 雨声\"」，表示将多个词当做完整词组搜索")
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
                                text = "屏蔽关键词，例如：同人",
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
                    contentDescription = "删除 $keyword",
                    modifier = Modifier.size(16.dp)
                )
            }
        )
    }
}

@Composable
private fun SettingsSectionsPanel(
    onSectionClick: (SettingsSection) -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface.copy(alpha = 0.32f),
        contentColor = colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            SettingsSection.entries.forEachIndexed { index, section ->
                SettingsSectionOption(
                    section = section,
                    onClick = { onSectionClick(section) },
                )
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
    onClick: () -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = "进入${section.title}",
            tint = colorScheme.textSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SettingsDetailHeader(
    section: SettingsSection,
    onBack: () -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("settingsDetailBack"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回设置",
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
        color = colorScheme.surface.copy(alpha = 0.5f),
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
private fun SettingsInfoTip(active: Boolean, title: String, text: String, onToggle: () -> Unit) {
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
                    contentDescription = "${title}说明",
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
                                text = "背景封面预览方式",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "关闭：背景与封面保持居中静止",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "滑动：播放页封面与歌词页背景都使用双指拖动预览，且会临时屏蔽左侧菜单侧滑",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "转动：通过转动手机预览封面其他区域",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
