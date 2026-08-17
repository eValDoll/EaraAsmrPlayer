package com.asmr.player.ui.library

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.asmr.player.ui.common.NoImageLoadingIndicator
import com.asmr.player.ui.common.AsmrShimmerPlaceholder
import com.asmr.player.ui.common.CvChipsFlow
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.ImagePreviewItem
import com.asmr.player.ui.common.ImagePreviewPreparedItem
import com.asmr.player.ui.common.ImagePreviewRequest
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.util.Formatting
import com.asmr.player.util.MessageManager
import com.asmr.player.util.RemoteSubtitleSource

private val DlsiteGalleryThumbWidth = 140.dp
private val DlsiteGalleryThumbHeight = 100.dp
private val DlsiteGalleryThumbGap = 10.dp
private val DlsiteGallerySectionHeight = 120.dp
private const val DlsiteGalleryThumbCornerRadius = 12

@Composable
private fun DlsiteGalleryLoadingRow() {
    val placeholders = remember { listOf(0, 1, 2, 3) }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(DlsiteGallerySectionHeight)
            .padding(horizontal = AlbumDetailHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(DlsiteGalleryThumbGap),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        items(placeholders, key = { it }, contentType = { "galleryLoadingThumb" }) {
            AsmrShimmerPlaceholder(
                modifier = Modifier.size(width = DlsiteGalleryThumbWidth, height = DlsiteGalleryThumbHeight),
                cornerRadius = DlsiteGalleryThumbCornerRadius,
                animateHighlight = false,
            )
        }
    }
}

@Composable
private fun DlsiteStaticPlaceholderLine(
    widthFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    cornerRadius: Int = 8,
) {
    AsmrShimmerPlaceholder(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        cornerRadius = cornerRadius,
        animateHighlight = false,
    )
}

@Composable
private fun rememberDlsiteDirectoryListHeight(): Dp {
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    return remember(screenHeight) {
        (screenHeight * 0.48f).coerceIn(240.dp, 460.dp)
    }
}

@Composable
private fun rememberStableOneDirectoryContainerHeight(): Dp {
    return rememberDlsiteDirectoryListHeight() + 104.dp
}

@Composable
private fun DlsiteDirectoryLoadingPanel() {
    val fixedHeight = rememberDlsiteDirectoryListHeight()
    val colorScheme = AsmrTheme.colorScheme
    val headerSectionColor = directoryBrowserHeaderBackground(colorScheme)
    val actionSectionColor = colorScheme.surfaceVariant.copy(alpha = if (colorScheme.isDark) 0.24f else 0.42f)
    val listSectionColor = colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.28f else 0.62f)
    val sectionDividerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        color = colorScheme.surfaceVariant.copy(alpha = if (colorScheme.isDark) 0.28f else 0.46f),
        border = BorderStroke(0.5.dp, sectionDividerColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(listSectionColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerSectionColor)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AsmrShimmerPlaceholder(
                    modifier = Modifier.size(22.dp),
                    cornerRadius = 7,
                    animateHighlight = false,
                )
                AsmrShimmerPlaceholder(
                    modifier = Modifier.size(width = 54.dp, height = 22.dp),
                    cornerRadius = 8,
                    animateHighlight = false,
                )
                AsmrShimmerPlaceholder(
                    modifier = Modifier.size(width = 5.dp, height = 12.dp),
                    cornerRadius = 3,
                    animateHighlight = false,
                )
                AsmrShimmerPlaceholder(
                    modifier = Modifier.size(width = 82.dp, height = 22.dp),
                    cornerRadius = 8,
                    animateHighlight = false,
                )
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = sectionDividerColor,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(actionSectionColor)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DlsiteStaticPlaceholderLine(
                        widthFraction = 0.42f,
                        height = 12.dp,
                        cornerRadius = 6,
                    )
                    DlsiteStaticPlaceholderLine(
                        widthFraction = 0.64f,
                        height = 9.dp,
                        cornerRadius = 5,
                    )
                }
                AsmrShimmerPlaceholder(
                    modifier = Modifier.size(width = 78.dp, height = 30.dp),
                    cornerRadius = 15,
                    animateHighlight = false,
                )
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = sectionDividerColor,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fixedHeight),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                userScrollEnabled = false,
            ) {
                item(key = "directoryLoadingFolders", contentType = "folderLoadingGroup") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                colorScheme.surfaceVariant.copy(
                                    alpha = if (colorScheme.isDark) 0.52f else 0.72f
                                )
                            )
                    ) {
                        repeat(2) { index ->
                            DlsiteDirectoryFolderPlaceholder(
                                titleWidthFraction = if (index == 0) 0.64f else 0.48f,
                            )
                        }
                    }
                }
                items(
                    count = 4,
                    key = { index -> "directoryLoadingFile:$index" },
                    contentType = { "fileLoading" },
                ) { index ->
                    DlsiteDirectoryFilePlaceholder(
                        titleWidthFraction = when (index) {
                            0 -> 0.78f
                            1 -> 0.58f
                            2 -> 0.70f
                            else -> 0.52f
                        },
                        metaWidthFraction = if (index % 2 == 0) 0.36f else 0.24f,
                        showThumbnail = index == 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun DlsiteDirectoryFolderPlaceholder(
    titleWidthFraction: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 42.dp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsmrShimmerPlaceholder(
            modifier = Modifier.size(18.dp),
            cornerRadius = 5,
            animateHighlight = false,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            DlsiteStaticPlaceholderLine(
                widthFraction = titleWidthFraction,
                height = 14.dp,
                cornerRadius = 7,
            )
        }
        AsmrShimmerPlaceholder(
            modifier = Modifier.size(18.dp),
            cornerRadius = 6,
            animateHighlight = false,
        )
    }
}

@Composable
private fun DlsiteDirectoryFilePlaceholder(
    titleWidthFraction: Float,
    metaWidthFraction: Float,
    showThumbnail: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .padding(start = 8.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(if (showThumbnail) 42.dp else 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            AsmrShimmerPlaceholder(
                modifier = Modifier.size(if (showThumbnail) 42.dp else 21.dp),
                cornerRadius = if (showThumbnail) 8 else 5,
                animateHighlight = false,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            DlsiteStaticPlaceholderLine(
                widthFraction = titleWidthFraction,
                height = 13.dp,
                cornerRadius = 7,
            )
            DlsiteStaticPlaceholderLine(
                widthFraction = metaWidthFraction,
                height = 9.dp,
                cornerRadius = 5,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        AsmrShimmerPlaceholder(
            modifier = Modifier.size(20.dp),
            cornerRadius = 6,
            animateHighlight = false,
        )
        Spacer(modifier = Modifier.width(8.dp))
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
                    DlsiteStaticPlaceholderLine(
                        widthFraction = if (index == 0) 0.62f else 0.48f,
                        height = 15.dp
                    )
                    DlsiteStaticPlaceholderLine(
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
            imageVector = Icons.Rounded.PlayArrow,
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
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
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
                DlsiteStaticPlaceholderLine(
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
                                    cornerRadius = 14,
                                    animateHighlight = false,
                                )
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DlsiteStaticPlaceholderLine(widthFraction = 0.88f, height = 12.dp)
                                    DlsiteStaticPlaceholderLine(widthFraction = 0.46f, height = 10.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DlsiteSectionPlacementTweenSpec = tween<IntOffset>(
    durationMillis = 280,
    easing = FastOutSlowInEasing
)

private enum class DirectoryTreePanelState {
    Loading,
    Content,
    Empty,
    MissingRj
}

private enum class DlsiteContentKind {
    Loading,
    Content,
    Empty
}

private data class DlsiteContentPanel<T>(
    val kind: DlsiteContentKind,
    val value: T? = null
)

@Stable
private class DlsiteContentFadeState<T>(initialPanel: DlsiteContentPanel<T>) {
    var panel by mutableStateOf(initialPanel)
        private set

    val alpha = Animatable(1f)

    suspend fun update(targetPanel: DlsiteContentPanel<T>) {
        if (panel.kind != targetPanel.kind) {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 90)
            )
        }
        panel = targetPanel
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        )
    }
}

@Composable
private fun <T> rememberDlsiteContentFadeState(
    targetPanel: DlsiteContentPanel<T>,
    stateKey: Any
): DlsiteContentFadeState<T> {
    val state = remember(stateKey) { DlsiteContentFadeState(targetPanel) }
    LaunchedEffect(state, targetPanel) {
        state.update(targetPanel)
    }
    return state
}

private fun <T> Modifier.dlsiteContentFade(state: DlsiteContentFadeState<T>): Modifier {
    return graphicsLayer {
        alpha = state.alpha.value
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyItemScope.dlsiteAnimatedSectionModifier(
    modifier: Modifier = Modifier,
    animateIntro: Boolean = true
): Modifier {
    if (!animateIntro) return modifier
    return modifier.animateItem(
        fadeInSpec = null,
        placementSpec = DlsiteSectionPlacementTweenSpec,
        fadeOutSpec = null,
    )
}

@Composable
private fun StableOneDirectoryTreeContent(
    targetState: DirectoryTreePanelState,
    stateKey: Any,
    modifier: Modifier = Modifier,
    content: @Composable (DirectoryTreePanelState) -> Unit
) {
    val targetPanel = DlsiteContentPanel(
        kind = when (targetState) {
            DirectoryTreePanelState.Loading -> DlsiteContentKind.Loading
            DirectoryTreePanelState.Content -> DlsiteContentKind.Content
            DirectoryTreePanelState.Empty,
            DirectoryTreePanelState.MissingRj -> DlsiteContentKind.Empty
        },
        value = targetState
    )
    val fadeState = rememberDlsiteContentFadeState(targetPanel, stateKey)
    Box(
        modifier = modifier
            .height(rememberStableOneDirectoryContainerHeight())
            .clipToBounds()
            .dlsiteContentFade(fadeState)
    ) {
        content(fadeState.panel.value ?: targetState)
    }
}

@Composable
private fun DirectoryTreeAnimatedContent(
    targetState: DirectoryTreePanelState,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable (DirectoryTreePanelState) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 60)) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                        initialOffsetY = { height -> (height * 0.06f).toInt() }
                    )
                ).togetherWith(
                fadeOut(animationSpec = tween(durationMillis = 120)) +
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                        targetOffsetY = { height -> -(height * 0.03f).toInt() }
                    )
            ).using(SizeTransform(clip = false))
        },
        label = label,
        content = { state -> content(state) }
    )
}

internal fun shouldShowAsmrOneDirectoryLoading(
    isAwaitingAsmrOneLoad: Boolean,
    hasResolvedAsmrOneContent: Boolean,
    isLoadingAsmrOne: Boolean,
    hasAsmrOneTree: Boolean,
    hasDirectoryBrowser: Boolean
): Boolean {
    if (hasDirectoryBrowser && hasAsmrOneTree) return false
    return isAwaitingAsmrOneLoad ||
        !hasResolvedAsmrOneContent ||
        isLoadingAsmrOne ||
        hasAsmrOneTree
}

internal fun shouldShowDlsitePlayDirectoryLoading(
    isAwaitingInitialTarget: Boolean,
    hasResolvedDlsitePlayContent: Boolean,
    isLoadingDlsitePlay: Boolean,
    hasDlsitePlayTree: Boolean,
    hasDirectoryBrowser: Boolean
): Boolean {
    return !hasDirectoryBrowser && (
        isAwaitingInitialTarget ||
            !hasResolvedDlsitePlayContent ||
            isLoadingDlsitePlay ||
            hasDlsitePlayTree
        )
}

@Composable
internal fun AlbumDlsiteInfoBreadcrumbTabV2(
    album: Album,
    header: @Composable () -> Unit,
    galleryUrls: List<String>,
    trialTracks: List<Track>,
    trialDownloadEnabled: Boolean,
    isLoading: Boolean,
    isAwaitingInitialLoad: Boolean,
    isAwaitingAsmrOneLoad: Boolean,
    hasResolvedAsmrOneContent: Boolean,
    asmrOneTree: List<AsmrOneTrackNodeResponse>,
    isLoadingAsmrOne: Boolean,
    isLoadingTrial: Boolean,
    onRefreshAsmrOne: () -> Unit,
    onRefreshTrial: () -> Unit,
    onDownloadTrial: () -> Unit,
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
    animateIntro: Boolean,
    onPersistCurrentPath: (String) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    dlsiteRecommendations: DlsiteRecommendations,
    onOpenAlbumByRj: (String, DlsiteRecommendedWork?) -> Unit,
    loadRemoteFileSize: suspend (String) -> Long?
) {
    val scope = rememberCoroutineScope()
    val colorScheme = AsmrTheme.colorScheme
    val sectionActionIconColors = IconButtonDefaults.iconButtonColors(
        contentColor = colorScheme.textPrimary,
        disabledContentColor = colorScheme.textTertiary.copy(alpha = 0.7f)
    )
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
    PersistAlbumDetailListScroll(
        listState = listState,
        stateKey = treeStateKey,
        onPersistScroll = onPersistScroll
    )
    LaunchedEffect(currentPath, treeStateKey) {
        onPersistCurrentPath(currentPath)
    }
    val isInitialDlsiteLoading = isLoading || isAwaitingInitialLoad
    val isAsmrOnePending = shouldShowAsmrOneDirectoryLoading(
        isAwaitingAsmrOneLoad = isAwaitingAsmrOneLoad,
        hasResolvedAsmrOneContent = hasResolvedAsmrOneContent,
        isLoadingAsmrOne = isLoadingAsmrOne,
        hasAsmrOneTree = asmrOneTree.isNotEmpty(),
        hasDirectoryBrowser = browser != null
    )
    val galleryPanelTarget: DlsiteContentPanel<List<String>> = when {
        galleryUrls.isEmpty() && isInitialDlsiteLoading -> DlsiteContentPanel(DlsiteContentKind.Loading)
        galleryUrls.isEmpty() -> DlsiteContentPanel(DlsiteContentKind.Empty)
        else -> DlsiteContentPanel(DlsiteContentKind.Content, galleryUrls)
    }
    val galleryFadeState = rememberDlsiteContentFadeState(galleryPanelTarget, treeStateKey)
    val trialPanelTarget: DlsiteContentPanel<List<Track>> = when {
        trialTracks.isNotEmpty() -> DlsiteContentPanel(DlsiteContentKind.Content, trialTracks)
        isInitialDlsiteLoading || isLoadingTrial -> DlsiteContentPanel(DlsiteContentKind.Loading)
        else -> DlsiteContentPanel(DlsiteContentKind.Empty)
    }
    val trialFadeState = rememberDlsiteContentFadeState(trialPanelTarget, treeStateKey)
    val displayedTrialTracks = trialFadeState.panel.value.orEmpty()
    val videoTracks = remember(displayedTrialTracks) {
        displayedTrialTracks.filter { isVideoPreviewUrl(it.path) }
    }
    val audioTracks = remember(displayedTrialTracks) {
        displayedTrialTracks.filterNot { isVideoPreviewUrl(it.path) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .thinScrollbar(listState),
        state = listState,
        flingBehavior = rememberCalmScrollableFlingBehavior(),
        contentPadding = PaddingValues(top = topContentPadding, bottom = LocalBottomOverlayPadding.current)
    ) {
        item(key = "dlsite-header") { header() }
        item(key = "dlsite-one-header") {
            AlbumDetailSectionHeading(
                title = if (asmrOneTree.isNotEmpty()) "ONE（已收录）" else "ONE",
                modifier = dlsiteAnimatedSectionModifier(
                    Modifier.fillMaxWidth().padding(start = AlbumDetailHorizontalPadding, end = AlbumDetailHorizontalPadding, top = 8.dp, bottom = 0.dp),
                    animateIntro = animateIntro
                ),
                actions = {
                    IconButton(
                        onClick = onRefreshAsmrOne,
                        enabled = !isLoadingAsmrOne,
                        colors = sectionActionIconColors
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
        val asmrOnePanelState = when {
            asmrOneTree.isNotEmpty() && browser != null -> DirectoryTreePanelState.Content
            isAsmrOnePending -> DirectoryTreePanelState.Loading
            else -> DirectoryTreePanelState.Empty
        }
        item(key = "dlsite-one-content") {
            Box(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                StableOneDirectoryTreeContent(
                    targetState = asmrOnePanelState,
                    stateKey = treeStateKey,
                    modifier = Modifier.fillMaxWidth()
                ) { panelState ->
                    when (panelState) {
                        DirectoryTreePanelState.Content -> {
                            val browserValue = browser
                            if (browserValue == null) {
                                DlsiteDirectoryLoadingPanel()
                            } else {
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
                                    animateIntro = false,
                                    folderKeyPrefix = "asmr-folder",
                                    fileKeyPrefix = "asmr-file",
                                    fileContent = { file, selectionMode, selected, selectedPosition, enterSelectionMode, onSelectedChange ->
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
                                            selectedPosition = selectedPosition,
                                            onEnterSelectionMode = enterSelectionMode,
                                            onSelectedChange = onSelectedChange,
                                            onDownload = if (isDownloadableTreeFileType(file.fileType)) ({ onDownloadOne(file.path) }) else null,
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
                        DirectoryTreePanelState.Loading -> DlsiteDirectoryLoadingPanel()
                        DirectoryTreePanelState.Empty -> DlsiteSectionEmptyState(
                            text = "ONE 暂未收录",
                            artworkKind = DlsiteEmptyArtworkKind.One,
                            modifier = Modifier
                        )
                        DirectoryTreePanelState.MissingRj -> Unit
                    }
                }
            }
        }
        item(key = "dlsite-gallery-section") {
            Column(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                AlbumDetailSectionHeading(
                    title = "Gallery",
                    modifier = Modifier.padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DlsiteGallerySectionHeight)
                        .dlsiteContentFade(galleryFadeState),
                    contentAlignment = Alignment.Center
                ) {
                    when (galleryFadeState.panel.kind) {
                        DlsiteContentKind.Loading -> DlsiteGalleryLoadingRow()
                        DlsiteContentKind.Empty -> {
                            DlsiteSectionEmptyState(
                                text = "暂无样图",
                                artworkKind = DlsiteEmptyArtworkKind.Gallery,
                                modifier = Modifier.then(dlsiteAnimatedSectionModifier(Modifier, animateIntro))
                            )
                        }
                        DlsiteContentKind.Content -> {
                            val displayedGalleryUrls = galleryFadeState.panel.value.orEmpty()
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = AlbumDetailHorizontalPadding),
                                horizontalArrangement = Arrangement.spacedBy(DlsiteGalleryThumbGap),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                items(items = displayedGalleryUrls, key = { it }, contentType = { "galleryThumb" }) { url ->
                                    val model = remember(url) {
                                        val headers = DlsiteAntiHotlink.headersForImageUrl(url)
                                        if (headers.isEmpty()) url else CacheImageModel(data = url, headers = headers, keyTag = "dlsite")
                                    }
                                    Card(
                                        modifier = Modifier.size(width = DlsiteGalleryThumbWidth, height = DlsiteGalleryThumbHeight).clickable {
                                            buildGalleryImagePreviewRequest(
                                                galleryUrls = displayedGalleryUrls,
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
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                                    ) {
                                        AsmrAsyncImage(
                                            model = model,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            placeholderCornerRadius = DlsiteGalleryThumbCornerRadius,
                                            loading = NoImageLoadingIndicator,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "dlsite-trial-header") {
            AlbumDetailSectionHeading(
                title = "试听 / 试看",
                modifier = dlsiteAnimatedSectionModifier(
                    Modifier.fillMaxWidth().padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp),
                    animateIntro = animateIntro
                ),
                actions = {
                    IconButton(
                        onClick = onRefreshTrial,
                        enabled = !isLoading && !isLoadingTrial,
                        colors = sectionActionIconColors
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                    IconButton(
                        onClick = onDownloadTrial,
                        enabled = trialDownloadEnabled,
                        colors = sectionActionIconColors
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = "下载")
                    }
                }
            )
        }
        when (trialFadeState.panel.kind) {
            DlsiteContentKind.Loading -> {
                item(key = "dlsite-trial-content") {
                    Box(
                        modifier = dlsiteAnimatedSectionModifier(
                            Modifier
                                .fillMaxWidth()
                                .dlsiteContentFade(trialFadeState),
                            animateIntro
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        DlsiteTrialLoadingList()
                    }
                }
            }
            DlsiteContentKind.Empty -> {
                item(key = "dlsite-trial-content") {
                    DlsiteSectionEmptyState(
                        text = "暂无试听 / 试看",
                        artworkKind = DlsiteEmptyArtworkKind.Trial,
                        modifier = dlsiteAnimatedSectionModifier(
                            Modifier.dlsiteContentFade(trialFadeState),
                            animateIntro
                        )
                    )
                }
            }
            DlsiteContentKind.Content -> {
                if (isLoadingTrial && trialPanelTarget.kind == DlsiteContentKind.Content) {
                    item(key = "dlsite-trial-progress") {
                        LinearProgressIndicator(
                            modifier = dlsiteAnimatedSectionModifier(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AlbumDetailHorizontalPadding)
                                    .dlsiteContentFade(trialFadeState),
                                animateIntro = animateIntro
                            )
                        )
                    }
                }
                items(items = videoTracks, key = { track -> if (track.id > 0L) track.id else track.path }, contentType = { "trialVideo" }) { track ->
                    Column(
                        modifier = dlsiteAnimatedSectionModifier(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 8.dp)
                                .dlsiteContentFade(trialFadeState),
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
                    Box(
                        modifier = dlsiteAnimatedSectionModifier(
                            Modifier.fillMaxWidth().dlsiteContentFade(trialFadeState),
                            animateIntro
                        )
                    ) {
                        DlsiteTrialAudioItem(
                            track = track,
                            onClick = { onPlayTracks(album, audioTracks, track) },
                            onAddToPlaylist = { onAddToPlaylist(track) }
                        )
                    }
                }
            }
        }
        item(key = "dlsite-recommendations") {
            Box(modifier = dlsiteAnimatedSectionModifier(Modifier.fillMaxWidth(), animateIntro)) {
                if (isInitialDlsiteLoading) {
                    DlsiteRecommendationsLoadingBlocks()
                } else {
                    DlsiteRecommendationsBlocks(
                        recommendations = dlsiteRecommendations,
                        onOpenAlbumByRj = onOpenAlbumByRj
                    )
                }
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
    isAwaitingInitialTarget: Boolean,
    hasResolvedDlsitePlayContent: Boolean,
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

    var autoLoadDispatched by remember(treeStateKey) { mutableStateOf(false) }
    LaunchedEffect(loggedIn, rjCode, shouldAutoLoad) {
        if (loggedIn && shouldAutoLoad && !autoLoadDispatched) {
            autoLoadDispatched = true
            onEnsureLoaded()
        }
    }

    val headerItemCount = 2
    val restoredIndex = if (initialScroll.first <= 0) 0 else initialScroll.first + headerItemCount
    val listState = rememberSaveable("scroll:$treeStateKey", saver = LazyListState.Saver) {
        LazyListState(restoredIndex, initialScroll.second)
    }
    PersistAlbumDetailListScroll(
        listState = listState,
        stateKey = treeStateKey,
        indexOffset = headerItemCount,
        onPersistScroll = onPersistScroll
    )

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
    val isDirectoryPending = shouldShowDlsitePlayDirectoryLoading(
        isAwaitingInitialTarget = isAwaitingInitialTarget,
        hasResolvedDlsitePlayContent = hasResolvedDlsitePlayContent,
        isLoadingDlsitePlay = isLoading,
        hasDlsitePlayTree = tree.isNotEmpty(),
        hasDirectoryBrowser = browser != null
    )
    LaunchedEffect(currentPath, treeStateKey) {
        onPersistCurrentPath(currentPath)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .thinScrollbar(listState),
        state = listState,
        flingBehavior = rememberCalmScrollableFlingBehavior(),
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

        val dlsitePlayPanelState = when {
            rj.isBlank() -> DirectoryTreePanelState.MissingRj
            tree.isEmpty() && isDirectoryPending -> DirectoryTreePanelState.Loading
            tree.isEmpty() -> DirectoryTreePanelState.Empty
            isDirectoryPending || browser == null -> DirectoryTreePanelState.Loading
            else -> DirectoryTreePanelState.Content
        }
        item(key = "dlplay-content:$treeStateKey") {
            DirectoryTreeAnimatedContent(
                targetState = dlsitePlayPanelState,
                label = "dlsitePlayDirectoryTree",
                modifier = Modifier.fillMaxWidth()
            ) { panelState ->
                when (panelState) {
                    DirectoryTreePanelState.MissingRj -> Box(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("缺少作品编号，无法加载")
                    }
                    DirectoryTreePanelState.Empty -> Box(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无可播放资源")
                    }
                    DirectoryTreePanelState.Loading -> Box(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    }
                    DirectoryTreePanelState.Content -> {
                        val browserValue = browser
                        if (browserValue == null) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                            }
                        } else {
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
                                folderKeyPrefix = "dlplay-folder",
                                fileKeyPrefix = "dlplay-file",
                                fileContent = { file, selectionMode, selected, selectedPosition, enterSelectionMode, onSelectedChange ->
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
                                        selectedPosition = selectedPosition,
                                        onEnterSelectionMode = enterSelectionMode,
                                        onSelectedChange = onSelectedChange,
                                        onDownload = if (isDownloadableTreeFileType(file.fileType)) ({ onDownloadOne(file.path) }) else null,
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
            }
        }
    }
}

