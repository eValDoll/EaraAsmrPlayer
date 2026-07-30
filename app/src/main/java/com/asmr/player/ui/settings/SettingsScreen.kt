package com.asmr.player.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.BuildConfig
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.FloatingLyricsSettings
import com.asmr.player.data.settings.LyricsPageSettings
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
import kotlin.math.abs

private val SettingsPageHorizontalPadding = 8.dp
private const val MONOCHROME_THEME_SENTINEL = 0x01000000

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
) {
    val floatingLyricsEnabled by viewModel.floatingLyricsEnabled.collectAsStateWhileActive(isDataActive)
    val floatingSettings by viewModel.floatingLyricsSettings.collectAsStateWhileActive(isDataActive)
    val lyricsPageSettings by viewModel.lyricsPageSettings.collectAsStateWhileActive(isDataActive)
    val dynamicPlayerHueEnabled by viewModel.dynamicPlayerHueEnabled.collectAsStateWhileActive(isDataActive)
    val themeMode by viewModel.themeMode.collectAsStateWhileActive(isDataActive)
    val staticHueArgbLight by viewModel.staticHueArgbLight.collectAsStateWhileActive(isDataActive)
    val staticHueArgbDark by viewModel.staticHueArgbDark.collectAsStateWhileActive(isDataActive)
    val coverBackgroundEnabled by viewModel.coverBackgroundEnabled.collectAsStateWhileActive(isDataActive)
    val coverBackgroundClarity by viewModel.coverBackgroundClarity.collectAsStateWhileActive(isDataActive)
    val coverPreviewMode by viewModel.coverPreviewMode.collectAsStateWhileActive(isDataActive)
    val pauseOnOutputDisconnect by viewModel.pauseOnOutputDisconnect.collectAsStateWhileActive(isDataActive)
    val resumeOnOutputConnect by viewModel.resumeOnOutputConnect.collectAsStateWhileActive(isDataActive)
    val pauseOnOtherAudio by viewModel.pauseOnOtherAudio.collectAsStateWhileActive(isDataActive)
    val playFadeInMs by viewModel.playFadeInMs.collectAsStateWhileActive(isDataActive)
    val pauseFadeOutMs by viewModel.pauseFadeOutMs.collectAsStateWhileActive(isDataActive)
    val sfwHideSystemControls by viewModel.sfwHideSystemControls.collectAsStateWhileActive(isDataActive)
    val showMiniPlayerBar by viewModel.showMiniPlayerBar.collectAsStateWhileActive(isDataActive)
    val searchBlockedKeywords by viewModel.searchBlockedKeywords.collectAsStateWhileActive(isDataActive)
    val updateState by viewModel.updateState.collectAsStateWhileActive(isDataActive)
    val autoUpdateCheckEnabled by viewModel.autoUpdateCheckEnabled.collectAsStateWhileActive(isDataActive)
    val scanRoots by libraryViewModel.scanRoots.collectAsStateWhileActive(isDataActive)
    val bulkProgress by libraryViewModel.bulkProgress.collectAsStateWhileActive(isDataActive)
    val isGlobalSyncRunning by libraryViewModel.isGlobalSyncRunning.collectAsStateWhileActive(isDataActive)
    val context = LocalContext.current
    val colorScheme = AsmrTheme.colorScheme
    val listState = rememberLazyListState()
    val segmentedButtonColors = SegmentedButtonDefaults.colors(
        activeContainerColor = colorScheme.primarySoft,
        activeContentColor = if (colorScheme.isDark) colorScheme.onPrimaryContainer else colorScheme.primaryStrong,
        activeBorderColor = colorScheme.primaryStrong,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = colorScheme.onSurfaceVariant,
        inactiveBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    )
    
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var activeTipKey by remember { mutableStateOf<String?>(null) }
    var searchBlockedKeywordInput by rememberSaveable { mutableStateOf("") }
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
    Scaffold(
        contentWindowInsets = StableWindowInsets.navigationBars,
        containerColor = Color.Transparent,
        contentColor = colorScheme.onBackground
    ) { padding ->
        LaunchedEffect(isActive) {
            if (isActive) return@LaunchedEffect
            listState.stopScroll(MutatePriority.PreventUserInput)
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
                state = listState,
                modifier = contentModifier.thinScrollbar(listState),
                flingBehavior = rememberCalmScrollableFlingBehavior(),
                contentPadding = PaddingValues(horizontal = SettingsPageHorizontalPadding, vertical = 10.dp)
                    .withAddedBottomPadding(LocalBottomOverlayPadding.current),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "group:local") {
                    SettingsGroup(title = "本地库") {
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

                item(key = "group:block_words") {
                    SettingsGroup(title = "屏蔽词") {
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

                item(key = "group:appearance") {
                    SettingsGroup(
                        title = "外观",
                        collapsible = true,
                        initiallyExpanded = false,
                        collapsedContent = {
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
                        }
                    ) {
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
                        /*
                        SettingsToggleRow(
                            text = "封面随手机转动查看完整图片",
                            checked = coverMotionEnabled,
                            onCheckedChange = viewModel::setCoverMotionEnabled
                        )
                        */
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

                item(key = "group:playback") {
                    SettingsGroup(
                        title = "播放设置",
                        collapsible = true,
                        initiallyExpanded = false,
                        collapsedContent = {
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
                        }
                    ) {
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

                // 悬浮歌词
                item(key = "group:lyrics") {
                    SettingsGroup(
                        title = "歌词",
                        collapsible = true,
                        initiallyExpanded = false,
                        collapsedContent = {
                            SettingsToggleRow(
                                text = "开启悬浮歌词",
                                checked = floatingLyricsEnabled,
                                onCheckedChange = { viewModel.setFloatingLyricsEnabled(it) }
                            )
                        }
                    ) {
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
                item(key = "group:about_update") {
                    SettingsGroup(title = "关于") {
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

                item(key = "group:support_status") {
                    SettingsGroup(title = "支持与状态") {
                        AppSupportStatusSection()
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(40.dp))
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
            SearchHelpText("和 搜索(空格分割)：「学校 制服」，表示同时包含指定关键词")
            SearchHelpText("或 搜索(英文竖线分割)：「学校|制服」，表示包含一个或多个指定关键词均可")
            SearchHelpText("排除 搜索(空格与减号)：「学校 -制服」，表示排除指定关键词")
            SearchHelpText("完整 搜索(英文双引号包裹)：「\"【简体中文】 舔耳\"」，表示将多个词当做完整词组搜索")
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
                                text = "屏蔽关键词，例如：NTR",
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
private fun SettingsGroup(
    title: String,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    collapsedContent: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (collapsible) {
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { expanded = !expanded }
                            .padding(start = 2.dp, end = 4.dp, bottom = 6.dp)
                    } else {
                        Modifier.padding(bottom = 6.dp)
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (collapsible) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.textSecondary
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "收起$title" else "展开$title",
                    tint = colorScheme.textSecondary
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surface.copy(alpha = 0.5f),
            contentColor = colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (collapsible) {
                    collapsedContent?.invoke(this)
                    if (expanded) {
                        if (collapsedContent != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                        }
                        content()
                    }
                } else {
                    content()
                }
            }
        }
    }
}

/*
@Composable
internal fun BackgroundEffectTypeSelectorRow(
    backgroundEffectEnabled: Boolean,
    selectedType: BackgroundEffectType,
    onBackgroundEffectEnabledChange: (Boolean) -> Unit,
    onSelected: (BackgroundEffectType) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    val selectorShape = RoundedCornerShape(12.dp)
    val selectorBorderColor = MaterialTheme.colorScheme.outline.copy(
        alpha = if (colorScheme.isDark) 0.26f else 0.18f
    )
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = if (!backgroundEffectEnabled) {
        "关闭"
    } else {
        when (selectedType) {
            BackgroundEffectType.Flow -> "光点"
            BackgroundEffectType.Ripple -> "呼吸波纹"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(BACKGROUND_EFFECT_TYPE_ROW_TAG),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("背景特效", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.wrapContentSize(Alignment.TopEnd)
        ) {
            Surface(
                shape = selectorShape,
                color = dynamicContainerColor,
                contentColor = colorScheme.onSurface,
                border = BorderStroke(1.dp, selectorBorderColor),
                modifier = Modifier
                    .clip(selectorShape)
                    .clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.textSecondary,
                        modifier = Modifier.testTag(BACKGROUND_EFFECT_VALUE_TAG)
                    )
                    Text(
                        text = "▼",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    surface = dynamicContainerColor,
                    surfaceContainer = dynamicContainerColor
                )
            ) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    offset = DpOffset(x = 0.dp, y = 6.dp),
                    modifier = Modifier.background(dynamicContainerColor)
                ) {
                    DropdownMenuItem(
                        text = { Text("关闭") },
                        onClick = {
                            expanded = false
                            onBackgroundEffectEnabledChange(false)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    DropdownMenuItem(
                        text = { Text("光点") },
                        onClick = {
                            expanded = false
                            onSelected(BackgroundEffectType.Flow)
                            onBackgroundEffectEnabledChange(true)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("呼吸波纹") },
                        onClick = {
                            expanded = false
                            onSelected(BackgroundEffectType.Ripple)
                            onBackgroundEffectEnabledChange(true)
                        }
                    )
                }
            }
        }
    }
}
*/
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
