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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CloudDownload
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
import com.asmr.player.ui.common.rememberDominantColorCenterWeighted
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
import com.asmr.player.ui.drawer.StatisticsViewModel
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.PrewarmDominantColorCenterWeighted
import com.asmr.player.ui.common.PrewarmVideoFrameDominantColorCenterWeighted
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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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

@Composable
internal fun HardwareVolumeOverlay(
    volumePercent: Int,
    audioOutputRouteKind: AudioOutputRouteKind,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onInteractionActiveChanged: (Boolean) -> Unit,
    warningSessionState: AppVolumeWarningSessionState,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val accentColor = colorScheme.primary
    val protectedVolumeChangeState = rememberProtectedAppVolumeChangeState(
        warningSessionState = warningSessionState,
        onApplyVolumeChange = onVolumeChange
    )

    Box(
        modifier = modifier
            .padding(12.dp)
            .graphicsLayer { clip = false }
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.92f else 0.96f),
            contentColor = colorScheme.onSurface,
            shadowElevation = if (colorScheme.isDark) 0.dp else 10.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .width(88.dp)
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AudioOutputRouteIcon(
                    routeKind = audioOutputRouteKind,
                    isMuted = volumePercent == 0,
                    tint = accentColor,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onToggleMute)
                )
                AppVolumeVerticalSlider(
                    valuePercent = volumePercent,
                    onValueChange = { nextPercent, source ->
                        protectedVolumeChangeState.requestVolumeChange(
                            currentPercent = volumePercent,
                            targetPercent = nextPercent,
                            source = source
                        )
                    },
                    accentColor = accentColor,
                    onInteractionActiveChanged = onInteractionActiveChanged
                )
                Text(
                    text = "${AppVolume.clampPercent(volumePercent)}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    AppVolumeHearingWarningDialog(state = protectedVolumeChangeState)
}

@Stable
internal class PersistedBooleanState(
    initial: Boolean,
    private val save: (Boolean) -> Unit
) : MutableState<Boolean> {
    private var backing by mutableStateOf(initial)

    override var value: Boolean
        get() = backing
        set(value) {
            if (backing == value) return
            backing = value
            save(value)
        }

    override fun component1(): Boolean = value

    override fun component2(): (Boolean) -> Unit = { value = it }

    fun updateFromStore(value: Boolean) {
        backing = value
    }
}

@Composable
internal fun DrawerNavCardItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val unselectedColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)
    val selectedColor = if (isDark) Color(0xFF2A2A2A) else Color.White
    val containerColor = if (selected) selectedColor else unselectedColor
    val selectedContentColor = colorScheme.primaryStrong
    val contentColor = if (selected) selectedContentColor else colorScheme.textPrimary
    val elevation = if (selected || isDark) 0.dp else 2.dp
    val shape = RoundedCornerShape(18.dp)

    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isDark) {
                    Modifier.border(
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = shape
                    )
                } else Modifier
            ),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val iconTint = if (selected) selectedContentColor else colorScheme.textSecondary
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconTint
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun DrawerSiteStatusFooter(
    viewModel: DrawerStatusViewModel,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val dlsite by viewModel.dlsite.collectAsState()
    val asmr by viewModel.asmr.collectAsState()
    val site by viewModel.asmrOneSite.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DrawerSiteRow(
            name = "dlsite.com",
            status = dlsite,
            onTest = { viewModel.testDlsite() }
        )

        DrawerSiteRow(
            status = asmr,
            onTest = { viewModel.testAsmrOne() },
            nameContent = {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { expanded = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "asmr-$site",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.textPrimary,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colorScheme.textSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf(100, 200, 300).forEach { opt ->
                            val selected = opt == site
                            DropdownMenuItem(
                                text = { Text(opt.toString()) },
                                onClick = {
                                    viewModel.setAsmrOneSite(opt)
                                    expanded = false
                                },
                                leadingIcon = {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = colorScheme.primary
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun DrawerSiteRow(
    status: SiteStatus,
    onTest: () -> Unit,
    name: String? = null,
    nameContent: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val dotColor = when (status.type) {
        SiteStatusType.Ok -> Color(0xFF2E7D32) // 绿色
        SiteStatusType.Fail -> Color(0xFFC62828) // 红色
        SiteStatusType.Testing -> Color(0xFFF9A825) // 黄色
        SiteStatusType.Unknown -> colorScheme.onSurface.copy(alpha = 0.35f)
    }
    val statusIcon = when (status.type) {
        SiteStatusType.Ok -> Icons.Default.Check
        SiteStatusType.Fail -> Icons.Default.Close
        SiteStatusType.Testing -> Icons.Default.Refresh
        SiteStatusType.Unknown -> null
    }
    
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(status.type) {
        if (status.type == SiteStatusType.Testing) {
            while (true) {
                rotationAngle = (rotationAngle + 10f) % 360f
                kotlinx.coroutines.delay(50)
            }
        } else {
            rotationAngle = 0f
        }
    }
    
    val shape = RoundedCornerShape(16.dp)
    val elevation = if (isDark) 0.dp else 1.dp
    val containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isDark) {
                    Modifier.border(
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = shape
                    )
                } else Modifier
            ),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            if (name != null) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.textPrimary,
                    maxLines = 1
                )
            } else if (nameContent != null) {
                nameContent()
            }

            Spacer(modifier = Modifier.weight(1f))
            if (statusIcon != null) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            rotationZ = rotationAngle
                        },
                    tint = dotColor
                )
            }
            FilledTonalButton(
                onClick = onTest,
                modifier = Modifier.height(30.dp).widthIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colorScheme.primarySoft,
                    contentColor = colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "测试", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            if (trailing != null) {
                Box(modifier = Modifier.height(32.dp)) {
                    trailing()
                }
            }
        }
    }
}

@Composable
internal fun DailyStatisticsFooter(
    viewModel: StatisticsViewModel,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.todayStats.collectAsState()
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val shape = RoundedCornerShape(16.dp)
    val elevation = if (isDark) 0.dp else 1.dp
    val containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isDark) {
                    Modifier.border(
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = shape
                    )
                } else Modifier
            ),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "今日收听统计",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textSecondary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem(
                    icon = Icons.Default.AccessTime,
                    label = "时长",
                    value = formatStatsDuration(stats?.listeningDurationMs ?: 0L)
                )
                StatItem(
                    icon = Icons.Default.Audiotrack,
                    label = "音轨",
                    value = "${stats?.trackCount ?: 0}"
                )
                StatItem(
                    icon = Icons.Default.CloudDownload,
                    label = "流量",
                    value = formatStatsTraffic(stats?.networkTrafficBytes ?: 0L)
                )
            }
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, label: String, value: String) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val iconBackground = if (isDark) Color(0xFF1E1E1E) else Color.White
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(if (isDark) Modifier else Modifier.shadow(elevation = 1.dp, shape = CircleShape, clip = false))
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colorScheme.textSecondary
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.textPrimary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textSecondary,
            fontSize = 10.sp
        )
    }
}

private fun formatStatsDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "${hours}h${minutes % 60}m"
    } else {
        "${minutes}m"
    }
}

private fun formatStatsTraffic(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.1fG", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format("%.1fM", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format("%.1fK", bytes / 1024.0)
        else -> "${bytes}B"
    }
}

