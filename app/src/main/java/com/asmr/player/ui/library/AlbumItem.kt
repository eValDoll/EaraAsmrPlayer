package com.asmr.player.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.CoverContentRow
import com.asmr.player.ui.common.albumCoverImageModel
import com.asmr.player.ui.common.NoImageLoadingIndicator
import com.asmr.player.ui.common.AsmrShimmerPlaceholder
import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.delay

internal val AlbumListItemCornerRadius = 12.dp
internal val AlbumGridItemCornerRadius = 12.dp
internal val AlbumGridItemSpacing = 12.dp
private val AlbumItemHorizontalPadding = 12.dp
private val AlbumItemVerticalPadding = 4.dp
private val AlbumItemCoverContentSpacing = 10.dp
private val AlbumListCoverShadowBlurRadius = 10.dp
private val AlbumGridCoverShadowBlurRadius = 13.dp
private val AlbumGridInfoHorizontalPadding = 2.dp
private val AlbumGridInfoVerticalPadding = 8.dp
private val AlbumGridInfoMinHeight = 128.dp
private val AlbumDetailSkeletonHeight = 18.dp
private val AlbumListBadgeScrim = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color.Black.copy(alpha = 0.18f),
        Color.Black.copy(alpha = 0.42f),
        Color.Black.copy(alpha = 0.68f)
    )
)
private val AlbumGridBadgeScrim = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color.Black.copy(alpha = 0.18f),
        Color.Black.copy(alpha = 0.44f),
        Color.Black.copy(alpha = 0.70f)
    )
)
private val AlbumOnlineDetailResizeSpring = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)
private const val AlbumOnlineDetailExitSettleMillis = 320L
private const val AlbumCoverDepthFadeMillis = 320
private const val AlbumCollectedRibbonFadeMillis = 240
private const val AlbumStatsSeparator = "  "
internal const val ALBUM_ITEM_CARD_TAG = "album_item_card"
internal const val ALBUM_ITEM_STATS_TAG = "album_item_stats"
internal const val ALBUM_ITEM_TAGS_TAG = "album_item_tags"

private fun Album.hasRatingInfo(): Boolean {
    return (ratingValue?.let { it > 0.0 } == true) || ratingCount > 0
}

@Immutable
internal data class AlbumStatsText(
    val leading: String,
    val date: String,
) {
    val animationKey: String
        get() = "$leading\n$date"
}

internal fun Album.formatAlbumStats(
    includeDownloadCount: Boolean,
    usePlaceholders: Boolean,
): AlbumStatsText {
    val ratingText = ratingValue
        ?.takeIf { it > 0.0 }
        ?.let { value ->
            buildString {
                append("★")
                append(String.format("%.1f", value))
                if (ratingCount > 0) append("($ratingCount)")
            }
        }

    if (usePlaceholders) {
        return AlbumStatsText(
            leading = listOf(
                ratingText ?: "★—",
                priceJpy.takeIf { it > 0 }?.let { "¥$it" } ?: "¥—",
            ).joinToString(separator = AlbumStatsSeparator),
            date = releaseDate.takeIf { it.isNotBlank() } ?: "—",
        )
    }

    val leading = buildString {
        ratingText?.let { append(it) }
        if (includeDownloadCount && dlCount > 0) {
            if (isNotEmpty()) append(AlbumStatsSeparator)
            append("DL $dlCount")
        }
        if (priceJpy > 0) {
            if (isNotEmpty()) append(AlbumStatsSeparator)
            append("¥$priceJpy")
        }
    }
    return AlbumStatsText(leading = leading, date = releaseDate)
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AlbumItem(
    album: Album,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onRjClick: ((String) -> Unit)? = null,
    onCircleClick: ((String) -> Unit)? = null,
    onCircleLongClick: ((String) -> Unit)? = null,
    onCvClick: ((String) -> Unit)? = null,
    onCvLongClick: ((String) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    onTagLongClick: ((String) -> Unit)? = null,
    coverBadge: AlbumCoverBadge? = null,
    onlineDetailLoading: Boolean = false,
    onlineCvLoading: Boolean = onlineDetailLoading,
    animateOnlineDetails: Boolean = true,
    coverFadeIn: Boolean = true,
    coverFadeInState: State<Boolean>? = null,
    coverReloadKey: Any? = null,
    coverRetainPainterDuringReload: Boolean = false,
    cacheDrawLayer: Boolean = false,
    coverOverlay: @Composable BoxScope.() -> Unit = {},
    showCollectedIndicator: Boolean = true,
    showStatsPlaceholders: Boolean = false,
) {
    val colorScheme = AsmrTheme.colorScheme
    val coverShape = remember { RoundedCornerShape(AlbumListItemCornerRadius) }
    val imageModel = remember(album.coverThumbPath, album.coverPath, album.coverUrl) {
        albumCoverImageModel(album)
    }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val listItemHeight = (screenWidthDp.dp * 0.28f).coerceIn(124.dp, 152.dp)
    val coverSize = listItemHeight
    val density = LocalDensity.current
    val coverRequestSize = remember(coverSize, density) {
        val sizePx = with(density) { coverSize.roundToPx() }
        IntSize(sizePx, sizePx)
    }
    var isNearWindow by remember { mutableStateOf(true) }
    var coverPainterAlphaState by remember(imageModel, coverReloadKey) {
        mutableStateOf<State<Float>?>(null)
    }
    val isCoverFadeComplete = (coverPainterAlphaState?.value ?: 0f) >= 1f
    val coverDepthProgress by key(imageModel, coverReloadKey) {
        animateFloatAsState(
            targetValue = if (isCoverFadeComplete) 1f else 0f,
            animationSpec = tween(durationMillis = AlbumCoverDepthFadeMillis),
            label = "albumListCoverDepth",
        )
    }
    val dividerColor = colorScheme.onSurfaceVariant.copy(
        alpha = if (colorScheme.isDark) 0.28f else 0.18f
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AlbumItemHorizontalPadding, vertical = AlbumItemVerticalPadding)
            .drawBehind {
                val strokeWidth = 0.75.dp.toPx()
                val y = size.height - strokeWidth / 2f
                drawLine(
                    color = dividerColor,
                    start = Offset(
                        x = (coverSize + AlbumItemCoverContentSpacing).toPx(),
                        y = y,
                    ),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth,
                )
            }
            .testTag(ALBUM_ITEM_CARD_TAG)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                val itemHeight = coordinates.size.height.toFloat().coerceAtLeast(1f)
                val rootHeight = coordinates.findRootCoordinates().size.height.toFloat()
                // Lazy 的复用槽会保留已经离开窗口的 Modifier.Node。提前在卡片越过
                // 一个自身高度后撤销离屏合成，让复用槽只保存显示列表，不继续占用
                // 大块 GPU 纹理；上下各留一张卡片作为滚入前的预热区。
                val nearWindow =
                    position.y + itemHeight >= -itemHeight &&
                        position.y <= rootHeight + itemHeight
                if (isNearWindow != nearWindow) {
                    isNearWindow = nearWindow
                }
            }
            .then(
                if (cacheDrawLayer) {
                    Modifier.graphicsLayer {
                        compositingStrategy = if (isNearWindow) {
                            CompositingStrategy.Offscreen
                        } else {
                            CompositingStrategy.Auto
                        }
                    }
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        CoverContentRow(
            coverWidth = coverSize,
            minHeight = coverSize,
            spacing = AlbumItemCoverContentSpacing,
            fillContentHeight = true,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = listItemHeight),
            cover = {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AlbumCoverDepthShadow(
                        progress = coverDepthProgress,
                        isDark = colorScheme.isDark,
                        shape = coverShape,
                        glowColor = colorScheme.primary,
                        blurRadius = AlbumListCoverShadowBlurRadius,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(coverShape)
                    ) {
                        AsmrAsyncImage(
                            model = imageModel,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholderCornerRadius = 0,
                            fadeIn = coverFadeIn,
                            fadeInState = coverFadeInState,
                            reloadKey = coverReloadKey,
                            retainPainterDuringReload = coverRetainPainterDuringReload,
                            peekAnySizeForInitial = true,
                            requestSize = coverRequestSize,
                            loading = NoImageLoadingIndicator,
                            onBitmapPainterState = { painter, alphaState ->
                                coverPainterAlphaState = if (painter != null) alphaState else null
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        coverOverlay()
                        if (coverBadge?.bottomScrim == true) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.34f)
                                    .background(AlbumListBadgeScrim)
                            )
                        }
                        coverBadge?.let { badge ->
                            AlbumCoverMetricBadge(
                                badge = badge,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(
                                        end = if (badge.compactOffset) 4.dp else 6.dp,
                                        bottom = if (badge.compactOffset) 4.dp else 6.dp
                                    )
                            )
                        }
                    }
                    AnimatedCollectedCoverRibbon(
                        visible = isCoverFadeComplete && showCollectedIndicator && album.hasAsmrOne,
                    )
                }
            },
            content = {
                val statsText = remember(
                    album.ratingValue,
                    album.ratingCount,
                    album.dlCount,
                    album.priceJpy,
                    album.releaseDate,
                    showStatsPlaceholders,
                ) {
                    album.formatAlbumStats(
                        includeDownloadCount = true,
                        usePlaceholders = showStatsPlaceholders,
                    )
                }
                val tagsStateContent = remember(album.tags) {
                    album.tags.joinToString(separator = "\n")
                }

                BalancedColumn(
                    modifier = Modifier
                        .padding(top = 5.dp, bottom = 5.dp),
                    minGap = 4.dp,
                    maxGap = 10.dp,
                ) {
                    val rj = album.rjCode.ifBlank { album.workId }
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colorScheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    AlbumItemPrimaryMetaLightweight(
                        rjCode = rj,
                        circle = album.circle,
                        modifier = Modifier.fillMaxWidth(),
                        rjOnClick = onRjClick?.let { click -> { click(rj) } },
                        circleOnClick = onCircleClick?.let { click -> { click(album.circle) } },
                        circleOnLongClick = onCircleLongClick?.let { longClick -> { longClick(album.circle) } },
                    )

                    AlbumOnlineDetailAnimatedLine(
                        content = if (onlineCvLoading) "" else album.cv,
                        loading = onlineCvLoading,
                        animated = animateOnlineDetails,
                    ) {
                        AlbumItemCvLightweight(
                            cvText = album.cv,
                            modifier = Modifier.fillMaxWidth(),
                            onCvClick = onCvClick,
                            onCvLongClick = onCvLongClick,
                        )
                    }
                    AlbumOnlineDetailAnimatedLine(
                        content = tagsStateContent,
                        loading = onlineDetailLoading,
                        animated = animateOnlineDetails,
                        loadingContent = {
                            AlbumDetailSkeletonLine(widthFraction = 0.86f)
                        }
                    ) {
                        AlbumItemTagsLightweight(
                            tags = album.tags,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(ALBUM_ITEM_TAGS_TAG),
                            onTagClick = onTagClick,
                            onTagLongClick = onTagLongClick,
                        )
                    }

                    AlbumOnlineDetailAnimatedLine(
                        content = if (showStatsPlaceholders && onlineDetailLoading) {
                            ""
                        } else {
                            statsText.animationKey
                        },
                        loading = if (showStatsPlaceholders) {
                            onlineDetailLoading
                        } else {
                            onlineDetailLoading && !album.hasRatingInfo()
                        },
                        animated = animateOnlineDetails,
                        modifier = Modifier.testTag(ALBUM_ITEM_STATS_TAG)
                    ) {
                        AlbumStatsLine(
                            stats = statsText,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
        )
    }
}

@Composable
internal fun BalancedColumn(
    modifier: Modifier = Modifier,
    minGap: Dp = 4.dp,
    maxGap: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val childConstraints = constraints.copy(minHeight = 0)
        val placeables = ArrayList<Placeable>(measurables.size)
        var childrenHeight = 0
        var widestChild = 0
        for (index in measurables.indices) {
            val placeable = measurables[index].measure(childConstraints)
            placeables.add(placeable)
            childrenHeight += placeable.height
            if (placeable.width > widestChild) widestChild = placeable.width
        }

        val layoutWidth = if (constraints.maxWidth != Constraints.Infinity) {
            constraints.maxWidth
        } else {
            maxOf(constraints.minWidth, widestChild)
        }

        val layoutHeight = if (constraints.maxHeight != Constraints.Infinity) {
            maxOf(constraints.minHeight, constraints.maxHeight, childrenHeight)
        } else {
            maxOf(constraints.minHeight, childrenHeight)
        }

        val remaining = (layoutHeight - childrenHeight).coerceAtLeast(0)
        val gapCount = placeables.size + 1
        val idealGap = if (gapCount > 0) remaining / gapCount else 0
        val minGapPx = minGap.roundToPx()
        val maxGapPx = maxGap.roundToPx()
        val gap = idealGap.coerceIn(minGapPx, maxGapPx)
        val used = gap * gapCount
        val extra = remaining - used

        layout(layoutWidth, layoutHeight) {
            var y = (extra / 2) + gap
            for (index in placeables.indices) {
                val placeable = placeables[index]
                placeable.placeRelative(0, y)
                y += placeable.height + gap
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onRjClick: ((String) -> Unit)? = null,
    onCircleClick: ((String) -> Unit)? = null,
    onCircleLongClick: ((String) -> Unit)? = null,
    onCvClick: ((String) -> Unit)? = null,
    onCvLongClick: ((String) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    onTagLongClick: ((String) -> Unit)? = null,
    coverBadge: AlbumCoverBadge? = null,
    onlineDetailLoading: Boolean = false,
    onlineCvLoading: Boolean = onlineDetailLoading,
    animateOnlineDetails: Boolean = true,
    coverFadeIn: Boolean = true,
    coverFadeInState: State<Boolean>? = null,
    coverReloadKey: Any? = null,
    coverRetainPainterDuringReload: Boolean = false,
    coverOverlay: @Composable BoxScope.() -> Unit = {},
    showCollectedIndicator: Boolean = true,
    showStatsPlaceholders: Boolean = false,
) {
    val colorScheme = AsmrTheme.colorScheme
    val coverShape = remember { RoundedCornerShape(AlbumGridItemCornerRadius) }
    val imageModel = remember(album.coverThumbPath, album.coverPath, album.coverUrl) {
        albumCoverImageModel(album)
    }
    var coverPainterAlphaState by remember(imageModel, coverReloadKey) {
        mutableStateOf<State<Float>?>(null)
    }
    val isCoverFadeComplete = (coverPainterAlphaState?.value ?: 0f) >= 1f
    val coverDepthProgress by key(imageModel, coverReloadKey) {
        animateFloatAsState(
            targetValue = if (isCoverFadeComplete) 1f else 0f,
            animationSpec = tween(durationMillis = AlbumCoverDepthFadeMillis),
            label = "albumGridCoverDepth",
        )
    }
    Column(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            AlbumCoverDepthShadow(
                progress = coverDepthProgress,
                isDark = colorScheme.isDark,
                shape = coverShape,
                glowColor = colorScheme.primary,
                blurRadius = AlbumGridCoverShadowBlurRadius,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(coverShape)
            ) {
                AsmrAsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholderCornerRadius = 0,
                    fadeIn = coverFadeIn,
                    fadeInState = coverFadeInState,
                    reloadKey = coverReloadKey,
                    retainPainterDuringReload = coverRetainPainterDuringReload,
                    peekAnySizeForInitial = true,
                    loading = NoImageLoadingIndicator,
                    onBitmapPainterState = { painter, alphaState ->
                        coverPainterAlphaState = if (painter != null) alphaState else null
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                coverOverlay()
                if (coverBadge?.bottomScrim == true) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.36f)
                            .background(AlbumGridBadgeScrim)
                    )
                }
                coverBadge?.let { badge ->
                    AlbumCoverMetricBadge(
                        badge = badge,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = if (badge.compactOffset) 5.dp else 8.dp,
                                bottom = if (badge.compactOffset) 5.dp else 8.dp
                            )
                    )
                }
            }
            AnimatedCollectedCoverRibbon(
                visible = isCoverFadeComplete && showCollectedIndicator && album.hasAsmrOne,
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AlbumGridInfoMinHeight)
                .padding(horizontal = AlbumGridInfoHorizontalPadding, vertical = AlbumGridInfoVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colorScheme.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            val rj = album.rjCode.ifBlank { album.workId }
            AlbumItemPrimaryMetaLightweight(
                rjCode = rj,
                circle = album.circle,
                modifier = Modifier.fillMaxWidth(),
                rjOnClick = onRjClick?.let { click -> { click(rj) } },
                circleOnClick = onCircleClick?.let { click -> { click(album.circle) } },
                circleOnLongClick = onCircleLongClick?.let { longClick -> { longClick(album.circle) } },
            )

            AlbumOnlineDetailAnimatedLine(
                content = if (onlineCvLoading) "" else album.cv,
                loading = onlineCvLoading,
                animated = animateOnlineDetails,
                loadingContent = {
                    AlbumDetailSkeletonLine(widthFraction = 0.72f)
                }
            ) {
                AlbumItemCvLightweight(
                    cvText = album.cv,
                    modifier = Modifier.fillMaxWidth(),
                    layout = AlbumInlineValuesLayout.Flow,
                    onCvClick = onCvClick,
                    onCvLongClick = onCvLongClick,
                )
            }

            val statsText = remember(
                album.ratingValue,
                album.ratingCount,
                album.priceJpy,
                album.releaseDate,
                showStatsPlaceholders,
            ) {
                album.formatAlbumStats(
                    includeDownloadCount = false,
                    usePlaceholders = showStatsPlaceholders,
                )
            }
            val tagsStateContent = remember(album.tags) {
                album.tags.joinToString(separator = "\n")
            }

            AlbumOnlineDetailAnimatedLine(
                content = tagsStateContent,
                loading = onlineDetailLoading,
                animated = animateOnlineDetails,
                loadingContent = {
                    AlbumDetailSkeletonLine(widthFraction = 0.92f)
                }
            ) {
                AlbumItemTagsLightweight(
                    tags = album.tags,
                    modifier = Modifier.fillMaxWidth(),
                    layout = AlbumInlineValuesLayout.Flow,
                    onTagClick = onTagClick,
                    onTagLongClick = onTagLongClick,
                )
            }

            AlbumOnlineDetailAnimatedLine(
                content = if (showStatsPlaceholders && onlineDetailLoading) {
                    ""
                } else {
                    statsText.animationKey
                },
                loading = if (showStatsPlaceholders) {
                    onlineDetailLoading
                } else {
                    onlineDetailLoading && !album.hasRatingInfo()
                },
                animated = animateOnlineDetails,
            ) {
                AlbumStatsLine(
                    stats = statsText,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AlbumOnlineDetailAnimatedLine(
    content: String,
    loading: Boolean,
    animated: Boolean,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit = {
        AlbumDetailSkeletonLine(widthFraction = 0.62f)
    },
    contentBlock: @Composable () -> Unit,
) {
    if (!animated) {
        when {
            content.isNotBlank() -> Box(modifier = modifier.fillMaxWidth()) {
                contentBlock()
            }
            loading -> Box(modifier = modifier.fillMaxWidth()) {
                loadingContent()
            }
        }
        return
    }
    val stateKey = onlineDetailLineStateKey(content = content, loading = loading)
    var keepMounted by remember { mutableStateOf(stateKey != "empty") }
    LaunchedEffect(stateKey) {
        if (stateKey != "empty") {
            keepMounted = true
        } else if (keepMounted) {
            delay(AlbumOnlineDetailExitSettleMillis)
            keepMounted = false
        }
    }
    if (stateKey == "empty" && !keepMounted) return
    AlbumOnlineDetailAnimatedLine(
        stateKey = stateKey,
        modifier = modifier,
    ) { targetState ->
        when (targetState) {
            "loading" -> loadingContent()
            "empty" -> Unit
            else -> contentBlock()
        }
    }
}

private fun onlineDetailLineStateKey(
    content: String,
    loading: Boolean,
): String {
    return when {
        content.isNotBlank() -> "content:$content"
        loading -> "loading"
        else -> "empty"
    }
}

@Composable
private fun AlbumOnlineDetailAnimatedLine(
    stateKey: String,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    AnimatedContent(
        targetState = stateKey,
        modifier = modifier
            .fillMaxWidth(),
        transitionSpec = {
            (
                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { height -> height / 3 }
                ) togetherWith (
                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                    slideOutVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) { height -> -height / 4 }
                ) using SizeTransform(
                clip = false,
                sizeAnimationSpec = { _, _ -> AlbumOnlineDetailResizeSpring }
            )
        },
        label = "albumOnlineDetailLine"
    ) { targetState ->
        content(targetState)
    }
}

@Composable
private fun AlbumDetailSkeletonLine(
    widthFraction: Float,
    modifier: Modifier = Modifier,
) {
    val fraction = widthFraction.coerceIn(0.2f, 1f)
    AsmrShimmerPlaceholder(
        modifier = modifier
            .fillMaxWidth(fraction)
            .height(AlbumDetailSkeletonHeight),
        cornerRadius = 7,
    )
}

@Immutable
data class AlbumCoverBadge(
    val icon: ImageVector,
    val text: String,
    val showContainer: Boolean = true,
    val bottomScrim: Boolean = false,
    val compactOffset: Boolean = false
)

@Composable
private fun AlbumCoverMetricBadge(
    badge: AlbumCoverBadge,
    modifier: Modifier = Modifier
) {
    if (badge.text.isBlank()) return
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .then(
                    if (badge.showContainer) {
                        Modifier.background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(4.dp))
                    } else {
                        Modifier
                    }
                )
                .padding(
                    horizontal = if (badge.showContainer) 5.dp else 0.dp,
                    vertical = if (badge.showContainer) 3.dp else 0.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(if (badge.showContainer) 3.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialIcon(
                imageVector = badge.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (badge.showContainer) 12.dp else 10.dp)
            )
            Text(
                text = badge.text,
                style = if (badge.showContainer) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                },
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AlbumStatsLine(
    stats: AlbumStatsText,
    modifier: Modifier = Modifier,
) {
    if (stats.leading.isBlank() && stats.date.isBlank()) return
    val colorScheme = AsmrTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall.copy(lineHeight = 19.sp)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stats.leading.isNotBlank()) {
            Text(
                text = stats.leading,
                style = style,
                color = colorScheme.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        if (stats.date.isNotBlank()) {
            Text(
                text = stats.date,
                style = style,
                color = colorScheme.textTertiary,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun AlbumCoverDepthShadow(
    progress: Float,
    isDark: Boolean,
    shape: Shape,
    glowColor: Color,
    blurRadius: Dp,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0f) return
    val layerColor = if (isDark) glowColor else Color.Black
    val resolvedBlurRadius = if (isDark) blurRadius * 1.35f else blurRadius
    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = progress * if (isDark) 0.82f else 0.72f
                scaleX = 0.93f
                scaleY = 0.93f
                translationY = if (isDark) 1.dp.toPx() else 4.dp.toPx()
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .blur(
                radius = resolvedBlurRadius,
                edgeTreatment = BlurredEdgeTreatment.Unbounded,
            )
            .background(layerColor, shape)
    )
}

@Composable
private fun CollectedCoverRibbon(modifier: Modifier = Modifier) {
    val colorScheme = AsmrTheme.colorScheme
    Box(
        modifier = modifier
            .size(74.dp)
            .drawBehind {
                val innerEdge = 32.dp.toPx()
                val outerEdge = 58.dp.toPx()
                val foldSize = 8.dp.toPx()
                val ribbonPath = Path().apply {
                    moveTo(0f, innerEdge)
                    lineTo(innerEdge, 0f)
                    lineTo(outerEdge, 0f)
                    lineTo(0f, outerEdge)
                    close()
                }
                drawPath(
                    path = ribbonPath,
                    brush = Brush.linearGradient(
                        colors = listOf(colorScheme.primaryStrong, colorScheme.primary),
                        start = Offset.Zero,
                        end = Offset(outerEdge, outerEdge),
                    ),
                )
                val foldColor = colorScheme.primaryStrong.copy(alpha = 0.78f)
                drawPath(
                    path = Path().apply {
                        moveTo(outerEdge, 0f)
                        lineTo(outerEdge - foldSize, foldSize)
                        lineTo(outerEdge, foldSize)
                        close()
                    },
                    color = foldColor,
                )
                drawPath(
                    path = Path().apply {
                        moveTo(0f, outerEdge)
                        lineTo(foldSize, outerEdge - foldSize)
                        lineTo(foldSize, outerEdge)
                        close()
                    },
                    color = foldColor,
                )
            },
    ) {
        Text(
            text = "收录",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = colorScheme.onPrimary,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-14).dp, y = (-14).dp)
                .width(44.dp)
                .rotate(-45f),
        )
    }
}

@Composable
private fun BoxScope.AnimatedCollectedCoverRibbon(visible: Boolean) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = (-3).dp, y = (-3).dp),
        enter = fadeIn(animationSpec = tween(AlbumCollectedRibbonFadeMillis)),
        exit = fadeOut(animationSpec = tween(AlbumCollectedRibbonFadeMillis)),
    ) {
        CollectedCoverRibbon()
    }
}
