package com.asmr.player.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.domain.model.Album
import com.asmr.player.cache.CacheImageModel
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.CoverContentRow
import com.asmr.player.ui.common.NoImageLoadingIndicator
import com.asmr.player.ui.common.AsmrShimmerPlaceholder
import com.asmr.player.ui.library.AlbumMetaLeadingVisual.Icon
import com.asmr.player.util.DlsiteAntiHotlink

import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.delay

internal val AlbumListItemCornerRadius = 6.dp
internal val AlbumGridItemCornerRadius = 6.dp
internal val AlbumGridItemSpacing = 6.dp
private val AlbumItemHorizontalPadding = 8.dp
private val AlbumItemVerticalPadding = 2.dp
private val AlbumItemCoverContentSpacing = 8.dp
private val AlbumGridInfoHorizontalPadding = 6.dp
private val AlbumGridInfoVerticalPadding = 8.dp
private val AlbumDetailSkeletonHeight = 18.dp
private val AlbumOnlineDetailResizeSpring = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)
private const val AlbumOnlineDetailExitSettleMillis = 320L
internal const val ALBUM_ITEM_CARD_TAG = "album_item_card"
internal const val ALBUM_ITEM_STATS_TAG = "album_item_stats"

private fun Album.hasRatingInfo(): Boolean {
    return (ratingValue?.let { it > 0.0 } == true) || ratingCount > 0
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
    onCvClick: ((String) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    coverBadge: AlbumCoverBadge? = null,
    onlineDetailLoading: Boolean = false,
    onlineCvLoading: Boolean = onlineDetailLoading,
    coverReloadKey: Any? = null,
    coverRetainPainterDuringReload: Boolean = false,
) {
    val colorScheme = AsmrTheme.colorScheme
    val shape = remember { RoundedCornerShape(AlbumListItemCornerRadius) }
    val coverShape = remember {
        RoundedCornerShape(
            topStart = AlbumListItemCornerRadius,
            bottomStart = AlbumListItemCornerRadius,
            topEnd = 0.dp,
            bottomEnd = 0.dp
        )
    }
    val data = album.coverThumbPath.takeIf { it.isNotBlank() && it.contains("_v2") }
        .orEmpty()
        .ifBlank { album.coverPath }
        .ifEmpty { album.coverUrl }
    val imageModel = remember(data) {
        val headers = if (data.startsWith("http", ignoreCase = true)) DlsiteAntiHotlink.headersForImageUrl(data) else emptyMap()
        if (headers.isEmpty()) data else CacheImageModel(data = data, headers = headers, keyTag = "dlsite")
    }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val listItemHeight = (screenWidthDp.dp * 0.24f).coerceIn(112.dp, 140.dp)
    val coverSize = listItemHeight

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AlbumItemHorizontalPadding, vertical = AlbumItemVerticalPadding)
            .testTag(ALBUM_ITEM_CARD_TAG)
            .clip(shape)
            .background(colorScheme.surface.copy(alpha = 0.5f))
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
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    AsmrAsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholderCornerRadius = 0,
                        reloadKey = coverReloadKey,
                        retainPainterDuringReload = coverRetainPainterDuringReload,
                        peekAnySizeForInitial = true,
                        loading = NoImageLoadingIndicator,
                        modifier = Modifier.fillMaxSize().clip(coverShape),
                    )
                    if (coverBadge?.bottomScrim == true) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .fillMaxHeight(0.34f)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.18f),
                                            Color.Black.copy(alpha = 0.42f),
                                            Color.Black.copy(alpha = 0.68f)
                                        )
                                    )
                                )
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
            },
            content = {
                val statsText = remember(
                    album.ratingValue,
                    album.ratingCount,
                    album.dlCount,
                    album.priceJpy,
                    album.releaseDate
                ) {
                    buildString {
                        val rv = album.ratingValue
                        if (rv != null && rv > 0.0) {
                            append("★")
                            append(String.format("%.1f", rv))
                            if (album.ratingCount > 0) append("(${album.ratingCount})")
                        }
                        if (album.dlCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("DL ${album.dlCount}")
                        }
                        if (album.priceJpy > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("¥${album.priceJpy}")
                        }
                        if (album.releaseDate.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(album.releaseDate)
                        }
                    }
                }

                BalancedColumn(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 4.dp, end = 12.dp),
                    minGap = 4.dp,
                    maxGap = 12.dp,
                ) {
                    val rj = album.rjCode.ifBlank { album.workId }
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    AlbumPrimaryMetaRow(
                        rjCode = rj,
                        circle = album.circle,
                        modifier = Modifier.fillMaxWidth(),
                        rjOnClick = onRjClick?.let { click -> { click(rj) } },
                        circleOnClick = onCircleClick?.let { click -> { click(album.circle) } },
                        leadingVisual = Icon,
                        order = AlbumPrimaryMetaOrder.CircleThenRj,
                    )

                    AlbumOnlineDetailAnimatedLine(
                        content = if (onlineCvLoading) "" else album.cv,
                        loading = onlineCvLoading
                    ) {
                        AlbumCvChipsSingleLine(
                            cvText = album.cv,
                            modifier = Modifier.fillMaxWidth(),
                            onCvClick = onCvClick,
                            leadingVisual = Icon,
                        )
                    }
                    AlbumOnlineDetailAnimatedLine(
                        content = album.tags.joinToString(separator = "\n"),
                        loading = onlineDetailLoading,
                        loadingContent = {
                            AlbumDetailSkeletonLine(widthFraction = 0.86f)
                        }
                    ) {
                        AlbumTagsSingleLine(
                            tags = album.tags,
                            modifier = Modifier.fillMaxWidth(),
                            onTagClick = onTagClick,
                            leadingVisual = Icon,
                        )
                    }

                    AlbumOnlineDetailAnimatedLine(
                        content = statsText,
                        loading = onlineDetailLoading && !album.hasRatingInfo(),
                        modifier = Modifier.testTag(ALBUM_ITEM_STATS_TAG)
                    ) {
                        Text(
                            text = statsText,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
        )
        
        if (album.hasAsmrOne) {
            CollectedStamp(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
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
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minHeight = 0))
        }

        val layoutWidth = if (constraints.maxWidth != Constraints.Infinity) {
            constraints.maxWidth
        } else {
            maxOf(constraints.minWidth, placeables.maxOfOrNull { it.width } ?: 0)
        }

        val childrenHeight = placeables.sumOf { it.height }
        val layoutHeight = if (constraints.maxHeight != Constraints.Infinity) {
            maxOf(constraints.minHeight, constraints.maxHeight, childrenHeight)
        } else {
            maxOf(constraints.minHeight, childrenHeight)
        }

        val remaining = (layoutHeight - childrenHeight).coerceAtLeast(0)
        val gapCount = placeables.size + 1
        val idealGap = if (gapCount > 0) remaining / gapCount else 0
        val gap = idealGap.coerceIn(minGap.roundToPx(), maxGap.roundToPx())
        val used = gap * gapCount
        val extra = remaining - used

        layout(layoutWidth, layoutHeight) {
            var y = (extra / 2) + gap
            placeables.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height + gap
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onRjClick: ((String) -> Unit)? = null,
    onCircleClick: ((String) -> Unit)? = null,
    onCvClick: ((String) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    coverBadge: AlbumCoverBadge? = null,
    onlineDetailLoading: Boolean = false,
    onlineCvLoading: Boolean = onlineDetailLoading,
    coverReloadKey: Any? = null,
    coverRetainPainterDuringReload: Boolean = false,
) {
    val colorScheme = AsmrTheme.colorScheme
    val shape = remember { RoundedCornerShape(AlbumGridItemCornerRadius) }
    val coverShape = remember {
        RoundedCornerShape(
            topStart = AlbumGridItemCornerRadius,
            topEnd = AlbumGridItemCornerRadius,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
    }
    val data = album.coverThumbPath.takeIf { it.isNotBlank() && it.contains("_v2") }
        .orEmpty()
        .ifBlank { album.coverPath }
        .ifEmpty { album.coverUrl }
    val imageModel = remember(data) {
        val headers = if (data.startsWith("http", ignoreCase = true)) DlsiteAntiHotlink.headersForImageUrl(data) else emptyMap()
        if (headers.isEmpty()) data else CacheImageModel(data = data, headers = headers, keyTag = "dlsite")
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(colorScheme.surface.copy(alpha = 0.3f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            AsmrAsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholderCornerRadius = 0,
                reloadKey = coverReloadKey,
                retainPainterDuringReload = coverRetainPainterDuringReload,
                peekAnySizeForInitial = true,
                loading = NoImageLoadingIndicator,
                modifier = Modifier.fillMaxSize().clip(coverShape),
            )
            if (coverBadge?.bottomScrim == true) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.36f)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.18f),
                                    Color.Black.copy(alpha = 0.44f),
                                    Color.Black.copy(alpha = 0.70f)
                                )
                            )
                        )
                )
            }
            
            val rj = album.rjCode.ifBlank { album.workId }
            if (rj.isNotBlank()) {
                Text(
                    text = rj,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .let { base ->
                            if (onRjClick != null) {
                                base.clickable { onRjClick(rj) }
                            } else {
                                base
                            }
                        }
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            AlbumOnlineDetailAnimatedOverlay(
                visible = album.releaseDate.isNotBlank(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = album.releaseDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
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

            if (album.hasAsmrOne) {
                CollectedStamp(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }
        
        Column(
            modifier = Modifier.padding(horizontal = AlbumGridInfoHorizontalPadding, vertical = AlbumGridInfoVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.textPrimary,
                overflow = TextOverflow.Clip
            )
            
            AlbumPrimaryMetaRow(
                rjCode = "",
                circle = album.circle,
                modifier = Modifier.fillMaxWidth(),
                circleOnClick = onCircleClick?.let { click -> { click(album.circle) } },
                leadingVisual = Icon,
            )

            AlbumOnlineDetailAnimatedLine(
                content = if (onlineCvLoading) "" else album.cv,
                loading = onlineCvLoading,
                loadingContent = {
                    AlbumDetailSkeletonLine(widthFraction = 0.72f)
                }
            ) {
                AlbumCvChipsFlow(
                    cvText = album.cv,
                    onCvClick = onCvClick,
                    leadingVisual = Icon,
                )
            }

            val statsText = remember(album.ratingValue, album.ratingCount, album.priceJpy) {
                buildString {
                    val rv = album.ratingValue
                    if (rv != null && rv > 0.0) {
                        append("★")
                        append(String.format("%.1f", rv))
                        if (album.ratingCount > 0) append("(${album.ratingCount})")
                    }
                    if (album.priceJpy > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("¥${album.priceJpy}")
                    }
                }
            }

            AlbumOnlineDetailAnimatedLine(
                content = album.tags.joinToString(separator = "\n"),
                loading = onlineDetailLoading,
                loadingContent = {
                    AlbumDetailSkeletonLine(widthFraction = 0.92f)
                }
            ) {
                AlbumTagsFlow(
                    tags = album.tags,
                    modifier = Modifier.padding(top = 2.dp),
                    onTagClick = onTagClick,
                    leadingVisual = Icon,
                )
            }

            AlbumOnlineDetailAnimatedLine(
                content = statsText,
                loading = onlineDetailLoading && !album.hasRatingInfo()
            ) {
                Text(
                    text = statsText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AlbumOnlineDetailAnimatedLine(
    content: String,
    loading: Boolean,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit = {
        AlbumDetailSkeletonLine(widthFraction = 0.62f)
    },
    contentBlock: @Composable () -> Unit,
) {
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
private fun AlbumOnlineDetailAnimatedOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = visible,
        modifier = modifier,
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
                    ) { height -> height / 4 }
                ) using SizeTransform(clip = false)
        },
        label = "albumOnlineDetailOverlay"
    ) { targetVisible ->
        if (targetVisible) {
            content()
        }
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
private fun CollectedStamp(modifier: Modifier = Modifier) {
    val dangerColor = AsmrTheme.colorScheme.danger
    Box(
        modifier = modifier
            .rotate(15f)
            .background(dangerColor.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "收录",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
