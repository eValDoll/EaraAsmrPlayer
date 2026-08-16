package com.asmr.player.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.R
import com.asmr.player.ui.theme.AsmrTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumHeroPrimaryMetaLightweight(
    rjCode: String,
    circle: String,
    modifier: Modifier = Modifier,
    rjOnClick: (() -> Unit)? = null,
    circleOnClick: (() -> Unit)? = null,
    circleOnLongClick: (() -> Unit)? = null,
) {
    val normalizedRj = remember(rjCode) { rjCode.trim() }
    val normalizedCircle = remember(circle) { circle.trim() }
    if (normalizedRj.isBlank() && normalizedCircle.isBlank()) return

    val colorScheme = AsmrTheme.colorScheme
    val contentColor = if (colorScheme.isDark) {
        Color.White.copy(alpha = 0.94f)
    } else {
        Color.Black.copy(alpha = 0.84f)
    }
    val rjColor = colorScheme.primary
    val secondaryContentColor = contentColor.copy(alpha = 0.76f)
    val textShadow = Shadow(
        color = if (colorScheme.isDark) {
            Color.Black.copy(alpha = 0.42f)
        } else {
            Color.White.copy(alpha = 0.62f)
        },
        offset = Offset(0f, 1f),
        blurRadius = 5f,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (normalizedRj.isNotBlank()) {
            Text(
                text = normalizedRj,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .combinedClickable(onClick = { rjOnClick?.invoke() })
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.35.sp,
                    shadow = textShadow,
                ),
                color = rjColor,
                maxLines = 1,
            )
        }

        if (normalizedRj.isNotBlank() && normalizedCircle.isNotBlank()) {
            Spacer(
                modifier = Modifier
                    .width(0.5.dp)
                    .height(12.dp)
                    .background(secondaryContentColor.copy(alpha = 0.46f))
            )
        }

        if (normalizedCircle.isNotBlank()) {
            Row(
                modifier = Modifier
                    .then(if (normalizedRj.isNotBlank()) Modifier.weight(1f) else Modifier.fillMaxWidth())
                    .clip(RoundedCornerShape(5.dp))
                    .combinedClickable(
                        onClick = { circleOnClick?.invoke() },
                        onLongClick = circleOnLongClick,
                    )
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_album_meta_club),
                    contentDescription = null,
                    tint = secondaryContentColor,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = normalizedCircle,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        shadow = textShadow,
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumItemPrimaryMetaLightweight(
    rjCode: String,
    circle: String,
    modifier: Modifier = Modifier,
    rjOnClick: (() -> Unit)? = null,
    circleOnClick: (() -> Unit)? = null,
    circleOnLongClick: (() -> Unit)? = null,
) {
    val normalizedRj = remember(rjCode) { rjCode.trim() }
    val normalizedCircle = remember(circle) { circle.trim() }
    if (normalizedRj.isBlank() && normalizedCircle.isBlank()) return

    val colorScheme = AsmrTheme.colorScheme
    val creatorTextColor = MaterialTheme.colorScheme.secondary
    val creatorIconColor = if (colorScheme.isDark) Color.White else Color.Black

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (normalizedCircle.isNotBlank()) {
            Row(
                modifier = Modifier
                    .then(
                        if (normalizedRj.isNotBlank()) {
                            Modifier.weight(1f)
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_album_meta_club),
                    contentDescription = null,
                    tint = creatorIconColor,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(13.dp)
                )
                Text(
                    text = normalizedCircle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 13.sp,
                    ),
                    color = creatorTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (circleOnClick != null || circleOnLongClick != null) {
                                Modifier.combinedClickable(
                                    onClick = { circleOnClick?.invoke() },
                                    onLongClick = circleOnLongClick,
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                )
            }
        } else if (normalizedRj.isNotBlank()) {
            Spacer(modifier = Modifier.weight(1f))
        }

        if (normalizedRj.isNotBlank()) {
            Text(
                text = normalizedRj,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.92f else 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (rjOnClick != null) {
                            Modifier.combinedClickable(onClick = rjOnClick)
                        } else {
                            Modifier
                        }
                    )
                    .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
            )
        }
    }
}

@Composable
internal fun AlbumItemCvLightweight(
    cvText: String,
    modifier: Modifier = Modifier,
    layout: AlbumInlineValuesLayout = AlbumInlineValuesLayout.Scrollable,
    onCvClick: ((String) -> Unit)? = null,
    onCvLongClick: ((String) -> Unit)? = null,
) {
    val cvs = remember(cvText) { parseAlbumCvNames(cvText) }
    AlbumItemInlineValuesLightweight(
        values = cvs,
        iconRes = R.drawable.ic_album_meta_cv,
        layout = layout,
        prominent = true,
        modifier = modifier,
        onValueClick = onCvClick,
        onValueLongClick = onCvLongClick,
    )
}

@Composable
internal fun AlbumItemTagsLightweight(
    tags: List<String>,
    modifier: Modifier = Modifier,
    layout: AlbumInlineValuesLayout = AlbumInlineValuesLayout.Scrollable,
    onTagClick: ((String) -> Unit)? = null,
    onTagLongClick: ((String) -> Unit)? = null,
) {
    val normalizedTags = remember(tags) { normalizeAlbumTags(tags) }
    AlbumItemInlineValuesLightweight(
        values = normalizedTags,
        iconRes = R.drawable.ic_album_meta_tags,
        layout = layout,
        modifier = modifier,
        valuePrefix = "#",
        onValueClick = onTagClick,
        onValueLongClick = onTagLongClick,
    )
}

internal enum class AlbumInlineValuesLayout {
    Scrollable,
    Flow,
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AlbumItemInlineValuesLightweight(
    values: List<String>,
    iconRes: Int,
    layout: AlbumInlineValuesLayout,
    modifier: Modifier = Modifier,
    valuePrefix: String = "",
    prominent: Boolean = false,
    onValueClick: ((String) -> Unit)? = null,
    onValueLongClick: ((String) -> Unit)? = null,
) {
    if (values.isEmpty()) return

    val colorScheme = AsmrTheme.colorScheme
    val valueColor = if (prominent) {
        MaterialTheme.colorScheme.secondary
    } else {
        colorScheme.textSecondary
    }
    val iconColor = if (prominent) {
        if (colorScheme.isDark) Color.White else Color.Black
    } else {
        colorScheme.textTertiary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier
                .padding(top = 3.dp, end = 6.dp)
                .size(13.dp)
        )

        when (layout) {
            AlbumInlineValuesLayout.Scrollable -> {
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScrollEdgeFade(scrollState)
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumItemInlineValueItems(
                        values = values,
                        valuePrefix = valuePrefix,
                        valueColor = valueColor,
                        onValueClick = onValueClick,
                        onValueLongClick = onValueLongClick,
                    )
                }
            }

            AlbumInlineValuesLayout.Flow -> FlowRow(
                modifier = Modifier
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                AlbumItemInlineValueItems(
                    values = values,
                    valuePrefix = valuePrefix,
                    valueColor = valueColor,
                    onValueClick = onValueClick,
                    onValueLongClick = onValueLongClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumItemInlineValueItems(
    values: List<String>,
    valuePrefix: String,
    valueColor: Color,
    onValueClick: ((String) -> Unit)?,
    onValueLongClick: ((String) -> Unit)?,
) {
    values.forEach { value ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = valuePrefix + value.removePrefix(valuePrefix),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (onValueClick != null || onValueLongClick != null) {
                            Modifier.combinedClickable(
                                onClick = { onValueClick?.invoke(value) },
                                onLongClick = onValueLongClick?.let { longClick ->
                                    { longClick(value) }
                                },
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }
    }
}

private fun Modifier.horizontalScrollEdgeFade(scrollState: ScrollState): Modifier {
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithCache {
        val fadeWidth = 18.dp.toPx().coerceAtMost(size.width / 3f)
        val leftFade = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startX = 0f,
            endX = fadeWidth,
        )
        val rightFade = Brush.horizontalGradient(
            colors = listOf(Color.Black, Color.Transparent),
            startX = size.width - fadeWidth,
            endX = size.width,
        )
        onDrawWithContent {
            drawContent()
            if (scrollState.value > 0) {
                drawRect(
                    brush = leftFade,
                    size = Size(fadeWidth, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (scrollState.value < scrollState.maxValue) {
                drawRect(
                    brush = rightFade,
                    topLeft = Offset(size.width - fadeWidth, 0f),
                    size = Size(fadeWidth, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }
}

/**
 * 轻量级声优列表
 *
 * 设计特点：
 * - 小图标固定在左侧 + 纯文本可滚动
 * - 中点分隔
 * - 可横向滚动
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumHeaderCvLightweight(
    cvText: String,
    modifier: Modifier = Modifier,
    onCvClick: ((String) -> Unit)? = null,
    onCvLongClick: ((String) -> Unit)? = null,
) {
    val cvs = remember(cvText) { parseAlbumCvNames(cvText) }
    if (cvs.isEmpty()) return

    val colorScheme = AsmrTheme.colorScheme
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 声优图标 - 固定在左侧
        Icon(
            painter = painterResource(id = R.drawable.ic_album_meta_cv),
            contentDescription = null,
            tint = colorScheme.textSecondary,
            modifier = Modifier.size(14.dp)
        )

        // 声优列表 - 可横向滚动
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScrollEdgeFade(scrollState)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cvs.forEach { cv ->
                Text(
                    text = cv,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    color = colorScheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .combinedClickable(
                            onClick = { onCvClick?.invoke(cv) },
                            onLongClick = onCvLongClick?.let { longClick -> { longClick(cv) } }
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * 轻量级标签列表
 *
 * 设计特点：
 * - 小图标固定在左侧 + 井号文本可滚动
 * - 单行横向滚动显示
 * - 与声优列表保持一致的布局
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumHeaderTagsLightweight(
    tags: List<String>,
    modifier: Modifier = Modifier,
    onTagClick: ((String) -> Unit)? = null,
    onTagLongClick: ((String) -> Unit)? = null,
) {
    val normalizedTags = remember(tags) { normalizeAlbumTags(tags) }
    if (normalizedTags.isEmpty()) return

    val colorScheme = AsmrTheme.colorScheme
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 标签图标 - 固定在左侧
        Icon(
            painter = painterResource(id = R.drawable.ic_album_meta_tags),
            contentDescription = null,
            tint = colorScheme.textSecondary,
            modifier = Modifier.size(14.dp)
        )

        // 标签列表 - 可横向滚动
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScrollEdgeFade(scrollState)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            normalizedTags.forEach { tag ->
                Text(
                    text = if (tag.startsWith("#")) tag else "#$tag",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    color = colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .combinedClickable(
                            onClick = { onTagClick?.invoke(tag) },
                            onLongClick = onTagLongClick?.let { longClick -> { longClick(tag) } }
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun parseAlbumCvNames(cvText: String): List<String> {
    return cvText
        .split(',', '，', '、', '/', '\n', ';', '；', '|')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun normalizeAlbumTags(tags: List<String>): List<String> {
    return tags
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}
