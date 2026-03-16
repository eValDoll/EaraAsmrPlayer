package com.asmr.player.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.BuildConfig
import com.asmr.player.data.settings.FloatingLyricsSettings
import com.asmr.player.ui.library.BulkPhase
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.withAddedBottomPadding
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    windowSizeClass: WindowSizeClass,
    viewModel: SettingsViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    val floatingLyricsEnabled by viewModel.floatingLyricsEnabled.collectAsState()
    val floatingSettings by viewModel.floatingLyricsSettings.collectAsState()
    val dynamicPlayerHueEnabled by viewModel.dynamicPlayerHueEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val staticHueArgb by viewModel.staticHueArgb.collectAsState()
    val staticHueArgbLight by viewModel.staticHueArgbLight.collectAsState()
    val staticHueArgbDark by viewModel.staticHueArgbDark.collectAsState()
    val coverBackgroundEnabled by viewModel.coverBackgroundEnabled.collectAsState()
    val coverBackgroundClarity by viewModel.coverBackgroundClarity.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val scanRoots by libraryViewModel.scanRoots.collectAsState()
    val bulkProgress by libraryViewModel.bulkProgress.collectAsState()
    val isGlobalSyncRunning by libraryViewModel.isGlobalSyncRunning.collectAsState()
    val context = LocalContext.current
    val colorScheme = AsmrTheme.colorScheme
    
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
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
        contentWindowInsets = WindowInsets.navigationBars,
        containerColor = Color.Transparent,
        contentColor = colorScheme.onBackground
    ) { padding ->
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
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
            }

            LazyColumn(
                modifier = contentModifier,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
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
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = buttonColors.contentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("刷新本地")
                    }
                    FilledTonalButton(
                        onClick = { libraryViewModel.syncMetadata() },
                        modifier = Modifier.weight(1f),
                        colors = buttonColors,
                        enabled = !isGlobalSyncRunning
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = buttonColors.contentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("云同步")
                    }
                }

                FilledTonalButton(
                    onClick = { pickRootLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = buttonColors
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = buttonColors.contentColor)
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
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = colorScheme.onSurface)
                                }
                                IconButton(onClick = { libraryViewModel.syncMetadataForRoot(root) }, enabled = !isGlobalSyncRunning) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = colorScheme.onSurface)
                                }
                                IconButton(onClick = { pendingRemoveRoot = root }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = colorScheme.onSurface)
                                }
                            }
                            }
                    }
                }
            }
        }

                item(key = "group:appearance") {
                    SettingsGroup(title = "外观") {
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

                Spacer(modifier = Modifier.height(8.dp))
                Text("主题色", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val currentHueArgb = if (themeMode == "light") staticHueArgbLight else staticHueArgbDark
                    ThemeColorDot(
                        color = null,
                        selected = currentHueArgb == null,
                        onClick = { viewModel.setStaticHueArgb(null) }
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
                if (coverBackgroundEnabled) {
                    key("cover_background_clarity_slider") {
                        DeferredCommitSettingsSliderRow(
                            committedValue = coverBackgroundClarity,
                            range = 0f..1f,
                            textForValue = { value ->
                                "封面背景清晰度：${(value.coerceIn(0f, 1f) * 100).toInt()}%"
                            },
                            onValueCommitted = viewModel::setCoverBackgroundClarity
                        )
                    }
                }
            }

                }

                // 悬浮歌词
                item(key = "group:floating_lyrics") {
                    SettingsGroup(title = "悬浮歌词") {
                SettingsToggleRow(
                    text = "开启悬浮歌词",
                    checked = floatingLyricsEnabled,
                    onCheckedChange = { viewModel.setFloatingLyricsEnabled(it) }
                )

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
                    // 字体大小
                    SettingsSliderRow(
                        text = "字体大小: ${floatingSettings.size.toInt()}",
                        value = floatingSettings.size,
                        range = 12f..32f,
                        onValueChange = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(size = it)) }
                    )

                    // 背景透明度
                    SettingsSliderRow(
                        text = "背景透明度: ${(floatingSettings.opacity * 100).toInt()}%",
                        value = floatingSettings.opacity,
                        range = 0f..1f,
                        onValueChange = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(opacity = it)) }
                    )

                    // 垂直位置
                    SettingsSliderRow(
                        text = "垂直位置 (Y轴)",
                        value = floatingSettings.yOffset.toFloat(),
                        range = 0f..2000f,
                        onValueChange = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(yOffset = it.toInt())) }
                    )

                    // 对齐方式
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
                                icon = {},
                                label = { Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, null) }
                            )
                            SegmentedButton(
                                selected = floatingSettings.align == 1,
                                onClick = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(align = 1)) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                icon = {},
                                label = { Icon(Icons.Default.FormatAlignCenter, null) }
                            )
                            SegmentedButton(
                                selected = floatingSettings.align == 2,
                                onClick = { viewModel.updateFloatingLyricsSettings(floatingSettings.copy(align = 2)) },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                icon = {},
                                label = { Icon(Icons.AutoMirrored.Filled.FormatAlignRight, null) }
                            )
                        }
                    }

                    val presetColors = remember {
                        listOf(
                            0xFFFFFFFF.toInt(),
                            0xFFFFEB3B.toInt(),
                            0xFF00E5FF.toInt(),
                            0xFF69F0AE.toInt(),
                            0xFFFF4081.toInt()
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

                    // 点击穿透
                    SettingsToggleRow(
                        text = "点击穿透 (锁定位置)",
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

                        val busy = updateState is AppUpdateState.Checking || updateState is AppUpdateState.Downloading
                        FilledTonalButton(
                            onClick = { viewModel.checkUpdate() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = buttonColors,
                            enabled = !busy
                        ) {
                            if (updateState is AppUpdateState.Checking) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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
                                        val apkFile = File(s.apkPath)
                                        if (!apkFile.exists() || apkFile.length() <= 0L) return@FilledTonalButton
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            val canInstall = context.packageManager.canRequestPackageInstalls()
                                            if (!canInstall) {
                                                val intent = Intent(
                                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                                    Uri.parse("package:${context.packageName}")
                                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                                return@FilledTonalButton
                                            }
                                        }
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            apkFile
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/vnd.android.package-archive")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        runCatching { context.startActivity(intent) }
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

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }

    val removeRoot = pendingRemoveRoot
    if (removeRoot != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoveRoot = null },
            title = { Text("移除目录") },
            text = { Text("将从列表中移除该目录，后续不会再扫描它。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = runCatching { Uri.parse(removeRoot) }.getOrNull()
                        if (uri != null) {
                            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
                        }
                        libraryViewModel.removeScanRootAndDeleteAlbums(removeRoot)
                        pendingRemoveRoot = null
                    }
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveRoot = null }) {
                    Text("取消")
                }
            }
        )
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
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colorScheme = AsmrTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surface.copy(alpha = 0.5f),
            contentColor = colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSliderRow(
    text: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null
) {
    val sliderInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
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
    textForValue: (Float) -> String,
    onValueCommitted: (Float) -> Unit
) {
    val coercedCommittedValue = committedValue.coerceIn(range.start, range.endInclusive)
    var draftValue by rememberSaveable(range.start, range.endInclusive) {
        mutableStateOf(coercedCommittedValue)
    }
    var pendingCommit by rememberSaveable { mutableStateOf<Float?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()

    LaunchedEffect(coercedCommittedValue, isDragging, pendingCommit) {
        when {
            isDragging -> Unit
            pendingCommit != null -> {
                if (abs(coercedCommittedValue - pendingCommit!!) <= 0.001f) {
                    draftValue = coercedCommittedValue
                    pendingCommit = null
                }
            }
            abs(draftValue - coercedCommittedValue) > 0.001f -> {
                draftValue = coercedCommittedValue
            }
        }
    }

    SettingsSliderRow(
        text = textForValue(draftValue),
        value = draftValue,
        range = range,
        onValueChange = { newValue ->
            draftValue = newValue.coerceIn(range.start, range.endInclusive)
        },
        onValueChangeFinished = {
            val valueToCommit = draftValue.coerceIn(range.start, range.endInclusive)
            pendingCommit = valueToCommit
            onValueCommitted(valueToCommit)
        },
        interactionSource = interactionSource
    )
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
