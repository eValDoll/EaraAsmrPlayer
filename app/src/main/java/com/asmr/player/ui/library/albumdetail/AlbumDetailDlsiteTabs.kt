package com.asmr.player.ui.library

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.zIndex
import com.asmr.player.ui.common.rememberDominantColor
import com.asmr.player.ui.common.SubtitleStamp
import com.asmr.player.ui.common.AudioItemMenuButtonSize
import com.asmr.player.ui.common.DiscPlaceholder
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.AsmrShimmerPlaceholder
import com.asmr.player.ui.common.CvChipsFlow
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.ImagePreviewItem
import com.asmr.player.ui.common.ImagePreviewPreparedItem
import com.asmr.player.ui.common.ImagePreviewRequest
import com.asmr.player.ui.common.collapsibleHeaderUiState
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

@Composable
private fun DlsiteGalleryLoadingRow() {
    val placeholders = remember { listOf(0, 1, 2, 3) }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        items(placeholders, key = { it }, contentType = { "galleryLoadingThumb" }) {
            AsmrShimmerPlaceholder(
                modifier = Modifier.size(width = 140.dp, height = 100.dp),
                cornerRadius = 12
            )
        }
    }
}

@Composable
private fun DlsiteSectionPlaceholderLine(
    widthFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    cornerRadius: Int = 8
) {
    AsmrShimmerPlaceholder(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        cornerRadius = cornerRadius
    )
}

@Composable
private fun DlsiteDirectoryLoadingPanel() {
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val fixedHeight = remember(screenHeight) {
        (screenHeight * 0.48f).coerceIn(240.dp, 460.dp)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        color = AsmrTheme.colorScheme.surface.copy(alpha = 0.44f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DlsiteSectionPlaceholderLine(widthFraction = 0.56f, height = 16.dp)
                DlsiteSectionPlaceholderLine(widthFraction = 0.32f, height = 12.dp)
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsmrShimmerPlaceholder(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    cornerRadius = 12
                )
                AsmrShimmerPlaceholder(
                    modifier = Modifier.size(width = 92.dp, height = 34.dp),
                    cornerRadius = 16
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fixedHeight)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(5) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (index % 3 == 0) 0.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val iconSize = if (index % 3 == 0) 18.dp else 14.dp
                        AsmrShimmerPlaceholder(
                            modifier = Modifier.size(iconSize),
                            cornerRadius = 999
                        )
                        DlsiteSectionPlaceholderLine(
                            widthFraction = if (index % 3 == 0) 0.72f else 0.54f,
                            modifier = Modifier.weight(1f),
                            height = 14.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DlsiteTrialLoadingList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
                color = AsmrTheme.colorScheme.surface.copy(alpha = 0.36f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DlsiteSectionPlaceholderLine(
                        widthFraction = if (index == 0) 0.62f else 0.48f,
                        height = 15.dp
                    )
                    DlsiteSectionPlaceholderLine(
                        widthFraction = if (index == 2) 0.26f else 0.18f,
                        height = 11.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun DlsiteTrialAudioItem(
    track: Track,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
) {
    val colorScheme = AsmrTheme.colorScheme
    val isOnline = remember(track.path) { track.path.trim().startsWith("http", ignoreCase = true) }
    val durationText = remember(track.duration) { Formatting.formatTrackSeconds(track.duration) }
    val subtitleText = remember(isOnline, durationText) {
        when {
            isOnline && durationText.isNotBlank() -> "在线 · $durationText"
            isOnline -> "在线"
            durationText.isNotBlank() -> durationText
            else -> "在线播放"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = colorScheme.primary
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = track.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.textPrimary
            )
            Text(
                text = subtitleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.textTertiary
            )
        }
        if (onAddToPlaylist != null) {
            IconButton(
                onClick = onAddToPlaylist,
                modifier = Modifier.size(AudioItemMenuButtonSize)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private enum class DlsiteEmptyArtworkKind {
    Gallery,
    One,
    Trial,
}

@Composable
private fun DlsiteSectionEmptyState(
    text: String,
    artworkKind: DlsiteEmptyArtworkKind,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DlsiteSectionEmptyArtwork(
            kind = artworkKind,
            modifier = Modifier.size(width = 92.dp, height = 60.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = AsmrTheme.colorScheme.textSecondary
        )
    }
}

@Composable
private fun DlsiteSectionEmptyArtwork(
    kind: DlsiteEmptyArtworkKind,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val strokeColor = colorScheme.textTertiary.copy(alpha = if (colorScheme.isDark) 0.86f else 0.76f)
    val accentColor = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.76f else 0.68f)

    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.05f
        when (kind) {
            DlsiteEmptyArtworkKind.Gallery -> drawGalleryEmptyArtwork(
                strokeColor = strokeColor,
                accentColor = accentColor,
                strokeWidth = strokeWidth
            )
            DlsiteEmptyArtworkKind.One -> drawOneEmptyArtwork(
                strokeColor = strokeColor,
                accentColor = accentColor,
                strokeWidth = strokeWidth
            )
            DlsiteEmptyArtworkKind.Trial -> drawTrialEmptyArtwork(
                strokeColor = strokeColor,
                accentColor = accentColor,
                strokeWidth = strokeWidth
            )
        }
    }
}

private fun DrawScope.drawGalleryEmptyArtwork(
    strokeColor: Color,
    accentColor: Color,
    strokeWidth: Float
) {
    val frameSize = Size(size.width * 0.34f, size.height * 0.48f)
    val corner = CornerRadius(strokeWidth * 1.8f, strokeWidth * 1.8f)

    fun drawPhotoFrame(origin: Offset) {
        drawRoundRect(
            color = strokeColor,
            topLeft = origin,
            size = frameSize,
            cornerRadius = corner,
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = accentColor,
            radius = strokeWidth * 0.8f,
            center = origin + Offset(frameSize.width * 0.72f, frameSize.height * 0.24f)
        )
        drawLine(
            color = strokeColor,
            start = origin + Offset(frameSize.width * 0.16f, frameSize.height * 0.72f),
            end = origin + Offset(frameSize.width * 0.40f, frameSize.height * 0.48f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = strokeColor,
            start = origin + Offset(frameSize.width * 0.40f, frameSize.height * 0.48f),
            end = origin + Offset(frameSize.width * 0.58f, frameSize.height * 0.62f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = strokeColor,
            start = origin + Offset(frameSize.width * 0.58f, frameSize.height * 0.62f),
            end = origin + Offset(frameSize.width * 0.82f, frameSize.height * 0.42f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }

    drawPhotoFrame(Offset(size.width * 0.14f, size.height * 0.26f))
    drawPhotoFrame(Offset(size.width * 0.42f, size.height * 0.14f))
    drawLine(
        color = strokeColor,
        start = Offset(size.width * 0.20f, size.height * 0.84f),
        end = Offset(size.width * 0.80f, size.height * 0.84f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawOneEmptyArtwork(
    strokeColor: Color,
    accentColor: Color,
    strokeWidth: Float
) {
    val folderSize = Size(size.width * 0.28f, size.height * 0.18f)
    val folderTopLeft = Offset(size.width * 0.10f, size.height * 0.14f)
    val corner = CornerRadius(strokeWidth * 1.6f, strokeWidth * 1.6f)

    drawRoundRect(
        color = strokeColor,
        topLeft = folderTopLeft,
        size = folderSize,
        cornerRadius = corner,
        style = Stroke(width = strokeWidth)
    )

    drawLine(
        color = strokeColor,
        start = Offset(size.width * 0.18f, size.height * 0.14f),
        end = Offset(size.width * 0.26f, size.height * 0.14f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    val trunkX = size.width * 0.28f
    val trunkStartY = size.height * 0.40f
    val branchYs = listOf(size.height * 0.50f, size.height * 0.66f, size.height * 0.82f)
    drawLine(
        color = strokeColor,
        start = Offset(trunkX, trunkStartY),
        end = Offset(trunkX, branchYs.last()),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = strokeColor,
        start = Offset(size.width * 0.24f, size.height * 0.32f),
        end = Offset(trunkX, trunkStartY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    branchYs.forEach { y ->
        drawLine(
            color = strokeColor,
            start = Offset(trunkX, y),
            end = Offset(size.width * 0.48f, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = accentColor,
            radius = strokeWidth * 0.72f,
            center = Offset(size.width * 0.48f, y)
        )
        drawLine(
            color = strokeColor,
            start = Offset(size.width * 0.58f, y),
            end = Offset(size.width * 0.82f, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawTrialEmptyArtwork(
    strokeColor: Color,
    accentColor: Color,
    strokeWidth: Float
) {
    val screenTopLeft = Offset(size.width * 0.10f, size.height * 0.18f)
    val screenSize = Size(size.width * 0.46f, size.height * 0.34f)
    val corner = CornerRadius(strokeWidth * 1.8f, strokeWidth * 1.8f)

    drawRoundRect(
        color = strokeColor,
        topLeft = screenTopLeft,
        size = screenSize,
        cornerRadius = corner,
        style = Stroke(width = strokeWidth)
    )

    val playCenter = screenTopLeft + Offset(screenSize.width * 0.50f, screenSize.height * 0.50f)
    drawLine(
        color = accentColor,
        start = Offset(playCenter.x - size.width * 0.03f, playCenter.y - size.height * 0.09f),
        end = Offset(playCenter.x - size.width * 0.03f, playCenter.y + size.height * 0.09f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = accentColor,
        start = Offset(playCenter.x - size.width * 0.03f, playCenter.y - size.height * 0.09f),
        end = Offset(playCenter.x + size.width * 0.08f, playCenter.y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = accentColor,
        start = Offset(playCenter.x - size.width * 0.03f, playCenter.y + size.height * 0.09f),
        end = Offset(playCenter.x + size.width * 0.08f, playCenter.y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    val barWidth = size.width * 0.07f
    val barBottom = size.height * 0.80f
    val barXs = listOf(0.62f, 0.72f, 0.82f)
    val barHeights = listOf(0.18f, 0.30f, 0.22f)
    barXs.zip(barHeights).forEach { (xFraction, heightFraction) ->
        val height = size.height * heightFraction
        val left = size.width * xFraction - barWidth / 2f
        val top = barBottom - height
        drawLine(
            color = strokeColor,
            start = Offset(left + barWidth / 2f, barBottom),
            end = Offset(left + barWidth / 2f, top),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }

    drawLine(
        color = strokeColor,
        start = Offset(size.width * 0.10f, size.height * 0.80f),
        end = Offset(size.width * 0.94f, size.height * 0.80f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

@Composable
private fun DlsiteRecommendationsLoadingBlocks() {
    val placeholders = remember { listOf(0, 1, 2) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        placeholders.forEach { sectionIndex ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DlsiteSectionPlaceholderLine(
                    widthFraction = when (sectionIndex) {
                        0 -> 0.34f
                        1 -> 0.28f
                        else -> 0.52f
                    },
                    height = 18.dp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(listOf(0, 1, 2, 3), key = { it }, contentType = { "dlsiteRecommendationLoadingCard" }) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 1.dp,
                            color = AsmrTheme.colorScheme.surface.copy(alpha = 0.35f),
                            modifier = Modifier.width(132.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(bottom = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsmrShimmerPlaceholder(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    cornerRadius = 14
                                )
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DlsiteSectionPlaceholderLine(widthFraction = 0.88f, height = 12.dp)
                                    DlsiteSectionPlaceholderLine(widthFraction = 0.46f, height = 10.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DlsiteSectionPlacementSpring = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessLow
)

@OptIn(ExperimentalFoundationApi::class)
private fun LazyItemScope.dlsiteAnimatedSectionModifier(
    modifier: Modifier = Modifier,
    animateIntro: Boolean = true
): Modifier {
    if (!animateIntro) return modifier
    return dlsiteElasticItemModifier(
        modifier = modifier.animateItemPlacement(animationSpec = DlsiteSectionPlacementSpring),
        enabled = true
    )
}

@Composable
internal fun AlbumDlsiteInfoBreadcrumbTabV2(
    album: Album,
    header: @Composable () -> Unit,
    dlsiteInfo: Album?,
    galleryUrls: List<String>,
    trialTracks: List<Track>,
    isLoading: Boolean,
    asmrOneTree: List<AsmrOneTrackNodeResponse>,
    isLoadingAsmrOne: Boolean,
    isLoadingTrial: Boolean,
    onRefreshAsmrOne: () -> Unit,
    onRefreshTrial: () -> Unit,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
    onAddToQueue: (Track) -> Boolean,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onDownloadOne: (String) -> Unit,
    onAddToPlaylistOne: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPreviewImages: (ImagePreviewRequest) -> Unit,
    onPreviewFile: (AsmrTreeUiEntry.File) -> Unit,
    treeStateKey: String,
    initialCurrentPath: String,
    topContentPadding: Dp,
    chromeState: com.asmr.player.ui.common.CollapsibleHeaderState,
    animateIntro: Boolean,
    onPersistCurrentPath: (String) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    dlsiteRecommendations: DlsiteRecommendations,
    onOpenAlbumByRj: (String) -> Unit,
    loadRemoteFileSize: suspend (String) -> Long?
) {
    val scope = rememberCoroutineScope()
    val videoTracks = remember(trialTracks) { trialTracks.filter { isVideoPreviewUrl(it.path) } }
    val audioTracks = remember(trialTracks) { trialTracks.filterNot { isVideoPreviewUrl(it.path) } }
    var currentPath by rememberSaveable(treeStateKey) { mutableStateOf(initialCurrentPath.trim().trim('/')) }
    val asmrLeafTracks by produceState(initialValue = emptyList<AsmrOneLeafUi>(), key1 = asmrOneTree) {
        value = withContext(Dispatchers.Default) { flattenAsmrOneTracksForUi(asmrOneTree) }
    }
    val asmrLeafByRelPath by produceState(initialValue = emptyMap<String, AsmrOneLeafUi>(), key1 = asmrLeafTracks) {
        value = withContext(Dispatchers.Default) { asmrLeafTracks.associateBy { it.relativePath } }
    }
    val remoteIndex by produceState<RemoteTreeIndex?>(
        initialValue = null,
        asmrOneTree,
        album.id,
        album.coverPath,
        album.coverUrl,
    ) {
        value = withContext(Dispatchers.Default) { buildRemoteTreeIndex(asmrOneTree, album) }
    }
    val browser by produceState<DirectoryBrowserResult?>(initialValue = null, key1 = remoteIndex, key2 = currentPath) {
        value = remoteIndex?.let { index ->
            withContext(Dispatchers.Default) { buildRemoteDirectoryBrowser(index, currentPath) }
        }
    }
    val listState = rememberSaveable("scroll:$treeStateKey", saver = LazyListState.Saver) {
        LazyListState(initialScroll.first, initialScroll.second)
    }
    LaunchedEffect(listState, treeStateKey) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (idx, off) -> onPersistScroll(idx, off) }
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            chromeState.expand()
        }
    }
    LaunchedEffect(currentPath, treeStateKey) {
        onPersistCurrentPath(currentPath)
    }
    val isInitialDlsiteLoading = dlsiteInfo == null && isLoading

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(chromeState.nestedScrollConnection)
            .thinScrollbar(listState),
        state = listState,
        contentPadding = PaddingValues(top = topContentPadding, bottom = LocalBottomOverlayPadding.current)
    ) {
        item(key = "dlsite-header") { header() }
        if (isInitialDlsiteLoading) {
            item(key = "dlsite-gallery-section") {
                Column(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                    Text(
                        text = "Gallery",
                        modifier = Modifier.padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    DlsiteGalleryLoadingRow()
                }
            }
            item(key = "dlsite-one-header") {
                Row(
                    modifier = dlsiteAnimatedSectionModifier(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp),
                        animateIntro = animateIntro
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ONE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            item(key = "dlsite-one-content") {
                Box(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                    DlsiteDirectoryLoadingPanel()
                }
            }
            item(key = "dlsite-trial-loading") {
                Column(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                Row(
                    modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "试听 / 试看",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                DlsiteTrialLoadingList()
                }
            }
            item(key = "dlsite-recommendations") {
                Box(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                    DlsiteRecommendationsLoadingBlocks()
                }
            }
            return@LazyColumn
        }
        item(key = "dlsite-gallery-section") {
            Column(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
            Text(
                text = "Gallery",
                modifier = Modifier.padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (galleryUrls.isEmpty()) {
                DlsiteSectionEmptyState(
                    text = "暂无样图",
                    artworkKind = DlsiteEmptyArtworkKind.Gallery,
                    modifier = Modifier.then(dlsiteAnimatedSectionModifier(Modifier, animateIntro))
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AlbumDetailHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    items(items = galleryUrls, key = { it }, contentType = { "galleryThumb" }) { url ->
                        val model = remember(url) {
                            val headers = DlsiteAntiHotlink.headersForImageUrl(url)
                            if (headers.isEmpty()) url else CacheImageModel(data = url, headers = headers, keyTag = "dlsite")
                        }
                        Card(
                            modifier = Modifier.size(width = 140.dp, height = 100.dp).clickable {
                                buildGalleryImagePreviewRequest(
                                    galleryUrls = galleryUrls,
                                    clickedUrl = url,
                                    toPreviewItem = { galleryUrl ->
                                        val headers = DlsiteAntiHotlink.headersForImageUrl(galleryUrl)
                                        val previewModel: Any = if (headers.isEmpty()) {
                                            galleryUrl
                                        } else {
                                            CacheImageModel(data = galleryUrl, headers = headers, keyTag = "dlsite")
                                        }
                                        ImagePreviewItem(
                                            key = galleryUrl,
                                            title = galleryUrl.substringBefore('?').substringAfterLast('/').ifBlank { "Gallery" },
                                            imageModel = previewModel,
                                            openPathOrUrl = galleryUrl
                                        )
                                    }
                                )?.let(onPreviewImages)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            AsmrAsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                placeholderCornerRadius = 12,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            }
        }
        item(key = "dlsite-one-header") {
            Row(
                modifier = dlsiteAnimatedSectionModifier(
                    Modifier.fillMaxWidth().padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp),
                    animateIntro = animateIntro
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (asmrOneTree.isNotEmpty()) "ONE（已收录）" else "ONE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onRefreshAsmrOne, enabled = !isLoadingAsmrOne) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }
        if (asmrOneTree.isNotEmpty() && browser != null) {
            item(key = "dlsite-one-content") {
                Box(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                    val browserValue = browser ?: return@Box
                    DirectoryBrowserPanelV4(
                    panelKey = treeStateKey,
                    currentPath = currentPath,
                    breadcrumbs = browserValue.breadcrumbs,
                    batchTargets = browserValue.batchTargets,
                    folders = browserValue.folders,
                    files = browserValue.files,
                    onNavigate = { path -> currentPath = path },
                    onAddToFavorites = onAddMediaItemsToFavorites,
                        onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                        onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                        animateIntro = animateIntro,
                        parentChromeState = chromeState,
                        folderKeyPrefix = "asmr-folder",
                        fileKeyPrefix = "asmr-file",
                    fileContent = { file, selectionMode, selected, enterSelectionMode, onSelectedChange ->
                        val leaf = asmrLeafByRelPath[file.path]
                        DirectoryFileRow(
                            file = file.copy(showSubtitleStamp = file.subtitleSources.isNotEmpty()),
                            loadRemoteFileSize = loadRemoteFileSize,
                            onPrimary = {
                                when (file.fileType) {
                                    TreeFileType.Audio -> {
                                        scope.launch {
                                            val prepared = withContext(Dispatchers.Default) {
                                                val start = asmrLeafByRelPath[file.path] ?: return@withContext null
                                                val folderPath = file.path.substringBeforeLast('/', "")
                                                val siblingLeaves = asmrLeafTracks.filter {
                                                    it.relativePath.substringBeforeLast('/', "") == folderPath
                                                }
                                                val queueLeaves = siblingLeaves.ifEmpty { listOf(start) }
                                                PreparedTrackPlayback(
                                                    tracks = queueLeaves.sortedBy { SmartSortKey.of(it.title) }.map { it.toTrack() },
                                                    startTrack = start.toTrack(),
                                                    onlineLyrics = queueLeaves.associate { it.url to it.subtitles }
                                                )
                                            } ?: return@launch
                                            com.asmr.player.util.OnlineLyricsStore.replaceAll(prepared.onlineLyrics)
                                            onPlayTracks(album, prepared.tracks, prepared.startTrack)
                                        }
                                    }
                                    TreeFileType.Video -> {
                                        val item = file.playlistTarget?.toMediaItem()
                                        if (item != null) {
                                            onPlayMediaItems(listOf(item), 0)
                                        } else {
                                            onPreviewFile(
                                                AsmrTreeUiEntry.File(
                                                    path = file.path,
                                                    title = file.title,
                                                    depth = 0,
                                                    fileType = file.fileType,
                                                    isPlayable = false,
                                                    url = file.url
                                                )
                                            )
                                        }
                                    }
                                    TreeFileType.Image -> {
                                        buildDirectoryImagePreviewRequest(
                                            files = browserValue.files,
                                            clickedPath = file.path,
                                            toPreviewItem = { imageFile ->
                                                val imageUrl = imageFile.url.takeIf { it.isNotBlank() } ?: return@buildDirectoryImagePreviewRequest null
                                                ImagePreviewItem(
                                                    key = imageFile.path,
                                                    title = imageFile.title,
                                                    openPathOrUrl = imageUrl,
                                                    prepareImage = {
                                                        ImagePreviewPreparedItem(
                                                            imageModel = imageUrl,
                                                            openPathOrUrl = imageUrl
                                                        )
                                                    }
                                                )
                                            }
                                        )?.let(onPreviewImages) ?: onPreviewFile(
                                            AsmrTreeUiEntry.File(
                                                path = file.path,
                                                title = file.title,
                                                depth = 0,
                                                fileType = file.fileType,
                                                isPlayable = false,
                                                url = file.url
                                            )
                                        )
                                    }
                                    else -> onPreviewFile(
                                        AsmrTreeUiEntry.File(
                                            path = file.path,
                                            title = file.title,
                                            depth = 0,
                                            fileType = file.fileType,
                                            isPlayable = false,
                                            url = file.url
                                        )
                                    )
                                }
                            },
                            selectionMode = selectionMode,
                            selected = selected,
                            onEnterSelectionMode = enterSelectionMode,
                            onSelectedChange = onSelectedChange,
                            onDownload = if (file.fileType == TreeFileType.Audio || file.fileType == TreeFileType.Video) ({ onDownloadOne(file.path) }) else null,
                            onAddToQueue = if (leaf != null) ({
                                com.asmr.player.util.OnlineLyricsStore.set(leaf.url, leaf.subtitles)
                                onAddToQueue(leaf.toTrack())
                            }) else null,
                            onAddToPlaylist = if (file.fileType == TreeFileType.Audio) ({ onAddToPlaylistOne(file.path) }) else null
                        )
                    }
                    )
                }
            }
        } else if (isLoadingAsmrOne || (asmrOneTree.isNotEmpty() && browser == null)) {
            item(key = "dlsite-one-content") {
                Box(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                    DlsiteDirectoryLoadingPanel()
                }
            }
        } else {
            item(key = "dlsite-one-content") {
                DlsiteSectionEmptyState(
                    text = "ONE 暂未收录",
                    artworkKind = DlsiteEmptyArtworkKind.One,
                    modifier = dlsiteAnimatedSectionModifier(Modifier, animateIntro)
                )
            }
        }
        item(key = "dlsite-trial-header") {
            Row(
                modifier = dlsiteAnimatedSectionModifier(
                    Modifier.fillMaxWidth().padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp),
                    animateIntro = animateIntro
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "试听 / 试看",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onRefreshTrial, enabled = !isLoading && !isLoadingTrial) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }
        if (trialTracks.isEmpty()) {
            item(key = "dlsite-trial-content") {
                if (isLoadingTrial) {
                    Box(
                        modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro),
                        contentAlignment = Alignment.Center
                    ) {
                        DlsiteTrialLoadingList()
                    }
                } else {
                    DlsiteSectionEmptyState(
                        text = "暂无试听 / 试看",
                        artworkKind = DlsiteEmptyArtworkKind.Trial,
                        modifier = dlsiteAnimatedSectionModifier(Modifier, animateIntro)
                    )
                }
            }
        } else {
            if (isLoadingTrial) {
                item(key = "dlsite-trial-progress") {
                    LinearProgressIndicator(
                        modifier = dlsiteAnimatedSectionModifier(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AlbumDetailHorizontalPadding),
                            animateIntro = animateIntro
                        )
                    )
                }
            }
            items(items = videoTracks, key = { track -> if (track.id > 0L) track.id else track.path }, contentType = { "trialVideo" }) { track ->
                Column(
                    modifier = dlsiteAnimatedSectionModifier(
                        Modifier.fillMaxWidth().padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp),
                        animateIntro = animateIntro
                    )
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InlineVideoPlayer(
                        url = track.path,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    )
                }
            }
            items(items = audioTracks, key = { track -> if (track.id > 0L) track.id else track.path }, contentType = { "trialAudioTrack" }) { track ->
                Box(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                    DlsiteTrialAudioItem(
                        track = track,
                        onClick = { onPlayTracks(album, audioTracks, track) },
                        onAddToPlaylist = { onAddToPlaylist(track) }
                    )
                }
            }
        }
        item(key = "dlsite-recommendations") {
            Box(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                DlsiteRecommendationsBlocks(
                    recommendations = dlsiteRecommendations,
                    onOpenAlbumByRj = onOpenAlbumByRj
                )
            }
        }
    }
}


@Composable
internal fun AlbumDlsitePlayBreadcrumbTabV2(
    header: @Composable () -> Unit,
    album: Album,
    rjCode: String,
    tree: List<AsmrOneTrackNodeResponse>,
    isLoading: Boolean,
    shouldAutoLoad: Boolean,
    onOpenLogin: () -> Unit,
    onEnsureLoaded: () -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
    onAddToQueue: (Track) -> Boolean,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onDownloadOne: (String) -> Unit,
    onPreviewImages: (ImagePreviewRequest) -> Unit,
    onPreviewFile: (AsmrTreeUiEntry.File) -> Unit,
    treeStateKey: String,
    initialCurrentPath: String,
    topContentPadding: Dp,
    chromeState: com.asmr.player.ui.common.CollapsibleHeaderState,
    animateIntro: Boolean,
    onPersistCurrentPath: (String) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    loadRemoteFileSize: suspend (String) -> Long?,
    prepareImagePreview: suspend (String, String?, Boolean, Int?, Int?) -> String?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val authStore = remember { DlsiteAuthStore(context) }
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(authStore.isPlayLoggedIn()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loggedIn = authStore.isPlayLoggedIn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!loggedIn) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("需要先登录 DLsite 才能使用已购播放/下载")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onOpenLogin) { Text("去登录") }
            }
        }
        return
    }

    LaunchedEffect(loggedIn, rjCode, shouldAutoLoad) {
        if (loggedIn && shouldAutoLoad) onEnsureLoaded()
    }

    val headerItemCount = 2
    val restoredIndex = if (initialScroll.first <= 0) 0 else initialScroll.first + headerItemCount
    val listState = rememberSaveable("scroll:$treeStateKey", saver = LazyListState.Saver) {
        LazyListState(restoredIndex, initialScroll.second)
    }
    LaunchedEffect(listState, treeStateKey) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (idx, off) ->
                val persistedIndex = (idx - headerItemCount).coerceAtLeast(0)
                onPersistScroll(persistedIndex, off)
            }
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            chromeState.expand()
        }
    }

    val rj = rjCode.trim().uppercase()
    var currentPath by rememberSaveable(treeStateKey) { mutableStateOf(initialCurrentPath.trim().trim('/')) }
    val leafTracks by produceState(initialValue = emptyList<AsmrOneLeafUi>(), key1 = tree) {
        value = withContext(Dispatchers.Default) { flattenAsmrOneTracksForUi(tree) }
    }
    val leafByRelPath by produceState(initialValue = emptyMap<String, AsmrOneLeafUi>(), key1 = leafTracks) {
        value = withContext(Dispatchers.Default) { leafTracks.associateBy { it.relativePath } }
    }
    val remoteIndex by produceState<RemoteTreeIndex?>(
        initialValue = null,
        tree,
        album.id,
        album.coverPath,
        album.coverUrl,
    ) {
        value = withContext(Dispatchers.Default) { buildRemoteTreeIndex(tree, album) }
    }
    val browser by produceState<DirectoryBrowserResult?>(initialValue = null, key1 = remoteIndex, key2 = currentPath) {
        value = remoteIndex?.let { index ->
            withContext(Dispatchers.Default) { buildRemoteDirectoryBrowser(index, currentPath) }
        }
    }
    LaunchedEffect(currentPath, treeStateKey) {
        onPersistCurrentPath(currentPath)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(chromeState.nestedScrollConnection)
            .thinScrollbar(listState),
        state = listState,
        contentPadding = PaddingValues(top = topContentPadding, bottom = LocalBottomOverlayPadding.current)
    ) {
        item(key = "dlplay-header:$treeStateKey") { header() }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已购内容",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onOpenLogin) { Text("登录 / 切换账号") }
            }
        }

        if (rj.isBlank()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    Text("缺少 RJ 编号，无法加载")
                }
            }
            return@LazyColumn
        }

        if (tree.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    } else {
                        Text("暂无可播放资源")
                    }
                }
            }
            return@LazyColumn
        }

        if (browser == null) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                }
            }
            return@LazyColumn
        }

        item {
            val browserValue = browser ?: return@item
            DirectoryBrowserPanelV4(
                panelKey = treeStateKey,
                currentPath = currentPath,
                breadcrumbs = browserValue.breadcrumbs,
                batchTargets = browserValue.batchTargets,
                folders = browserValue.folders,
                files = browserValue.files,
                onNavigate = { path -> currentPath = path },
                onAddToFavorites = onAddMediaItemsToFavorites,
                onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                animateIntro = animateIntro,
                parentChromeState = chromeState,
                folderKeyPrefix = "dlplay-folder",
                fileKeyPrefix = "dlplay-file",
                fileContent = { file, selectionMode, selected, enterSelectionMode, onSelectedChange ->
                    val leaf = leafByRelPath[file.path]
                    DirectoryFileRow(
                        file = file.copy(showSubtitleStamp = file.subtitleSources.isNotEmpty()),
                        loadRemoteFileSize = loadRemoteFileSize,
                        onPrimary = {
                            when (file.fileType) {
                                TreeFileType.Audio, TreeFileType.Video -> {
                                    scope.launch {
                                        val prepared = withContext(Dispatchers.Default) {
                                            val folderPath = file.path.substringBeforeLast('/', "")
                                            val siblings = browserValue.files
                                                .filter { sibling ->
                                                    sibling.path.substringBeforeLast('/', "") == folderPath &&
                                                        (sibling.fileType == TreeFileType.Audio || sibling.fileType == TreeFileType.Video) &&
                                                        sibling.playlistTarget != null
                                                }
                                                .sortedBy { SmartSortKey.of(it.title) }
                                            val items = siblings.mapNotNull { it.playlistTarget?.toMediaItem() }
                                            if (items.isEmpty()) return@withContext null
                                            val clickedId = file.playlistTarget?.mediaId.orEmpty()
                                            val startIndex = items.indexOfFirst { it.mediaId == clickedId }
                                                .takeIf { it >= 0 } ?: 0
                                            PreparedMediaPlayback(items, startIndex)
                                        }
                                        if (prepared != null) {
                                            if (leaf != null) {
                                                com.asmr.player.util.OnlineLyricsStore.set(leaf.url, leaf.subtitles)
                                            }
                                            onPlayMediaItems(prepared.items, prepared.startIndex)
                                        } else {
                                            onPreviewFile(
                                                AsmrTreeUiEntry.File(
                                                    path = file.path,
                                                    title = file.title,
                                                    depth = 0,
                                                    fileType = file.fileType,
                                                    isPlayable = false,
                                                    url = file.url
                                                )
                                            )
                                        }
                                    }
                                }
                                TreeFileType.Image -> {
                                    val request = buildDirectoryImagePreviewRequest(
                                        files = browserValue.files,
                                        clickedPath = file.path,
                                        toPreviewItem = { imageFile ->
                                            val imageUrl = imageFile.url.takeIf { it.isNotBlank() } ?: return@buildDirectoryImagePreviewRequest null
                                            ImagePreviewItem(
                                                key = imageFile.path,
                                                title = imageFile.title,
                                                openPathOrUrl = imageUrl,
                                                prepareImage = {
                                                    val prepared = prepareImagePreview(
                                                        imageUrl,
                                                        imageFile.dlsitePlayOptimizedName,
                                                        imageFile.dlsitePlayImageCrypt,
                                                        imageFile.dlsitePlayImageWidth,
                                                        imageFile.dlsitePlayImageHeight
                                                    ) ?: imageUrl
                                                    ImagePreviewPreparedItem(
                                                        imageModel = prepared,
                                                        openPathOrUrl = prepared
                                                    )
                                                }
                                            )
                                        }
                                    )
                                    if (request != null) {
                                        onPreviewImages(request)
                                    } else {
                                        onPreviewFile(
                                            AsmrTreeUiEntry.File(
                                                path = file.path,
                                                title = file.title,
                                                depth = 0,
                                                fileType = file.fileType,
                                                isPlayable = false,
                                                url = file.url
                                            )
                                        )
                                    }
                                }
                                else -> onPreviewFile(
                                    AsmrTreeUiEntry.File(
                                        path = file.path,
                                        title = file.title,
                                        depth = 0,
                                        fileType = file.fileType,
                                        isPlayable = false,
                                        url = file.url
                                    )
                                )
                            }
                        },
                        selectionMode = selectionMode,
                        selected = selected,
                        onEnterSelectionMode = enterSelectionMode,
                        onSelectedChange = onSelectedChange,
                        onDownload = if (file.fileType == TreeFileType.Audio || file.fileType == TreeFileType.Video) ({ onDownloadOne(file.path) }) else null,
                        onAddToQueue = if (leaf != null) ({
                            com.asmr.player.util.OnlineLyricsStore.set(leaf.url, leaf.subtitles)
                            onAddToQueue(leaf.toTrack())
                        }) else null,
                        onAddToPlaylist = null
                    )
                }
            )
        }
    }
}

