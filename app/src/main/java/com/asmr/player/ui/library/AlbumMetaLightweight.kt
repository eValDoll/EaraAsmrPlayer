package com.asmr.player.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.R
import com.asmr.player.ui.theme.AsmrTheme

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
                    tint = colorScheme.textTertiary,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(13.dp)
                )
                Text(
                    text = normalizedCircle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 13.sp,
                    ),
                    color = colorScheme.textSecondary,
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
    maxVisibleItems: Int = 2,
    onCvClick: ((String) -> Unit)? = null,
    onCvLongClick: ((String) -> Unit)? = null,
) {
    val cvs = remember(cvText) { parseAlbumCvNames(cvText) }
    AlbumItemInlineValuesLightweight(
        values = cvs,
        iconRes = R.drawable.ic_album_meta_cv,
        maxVisibleItems = maxVisibleItems,
        modifier = modifier,
        separatorText = "/",
        onValueClick = onCvClick,
        onValueLongClick = onCvLongClick,
    )
}

@Composable
internal fun AlbumItemTagsLightweight(
    tags: List<String>,
    modifier: Modifier = Modifier,
    maxVisibleItems: Int = 3,
    onTagClick: ((String) -> Unit)? = null,
    onTagLongClick: ((String) -> Unit)? = null,
) {
    val normalizedTags = remember(tags) { normalizeAlbumTags(tags) }
    AlbumItemInlineValuesLightweight(
        values = normalizedTags,
        iconRes = R.drawable.ic_album_meta_tags,
        maxVisibleItems = maxVisibleItems,
        modifier = modifier,
        valuePrefix = "#",
        onValueClick = onTagClick,
        onValueLongClick = onTagLongClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumItemInlineValuesLightweight(
    values: List<String>,
    iconRes: Int,
    maxVisibleItems: Int,
    modifier: Modifier = Modifier,
    valuePrefix: String = "",
    separatorText: String? = null,
    itemSpacing: Dp = 8.dp,
    onValueClick: ((String) -> Unit)? = null,
    onValueLongClick: ((String) -> Unit)? = null,
) {
    if (values.isEmpty()) return

    val colorScheme = AsmrTheme.colorScheme
    val visibleCount = maxVisibleItems.coerceAtLeast(1).coerceAtMost(values.size)
    val visibleValues = values.take(visibleCount)
    val hiddenCount = values.size - visibleCount

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = colorScheme.textTertiary,
            modifier = Modifier
                .padding(end = 6.dp)
                .size(13.dp)
        )
        visibleValues.forEachIndexed { index, value ->
            if (index > 0 && separatorText != null) {
                Text(
                    text = separatorText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = colorScheme.textTertiary,
                )
            }
            Text(
                text = valuePrefix + value.removePrefix(valuePrefix),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .then(
                        if (index > 0 && separatorText == null) {
                            Modifier.padding(start = itemSpacing)
                        } else {
                            Modifier
                        }
                    )
                    .weight(1f, fill = false)
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
        if (hiddenCount > 0) {
            Text(
                text = "+$hiddenCount",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colorScheme.textTertiary,
                maxLines = 1,
                modifier = Modifier.padding(start = itemSpacing)
            )
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
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cvs.forEachIndexed { index, cv ->
                if (index > 0) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.textTertiary.copy(alpha = 0.6f),
                    )
                }
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
                .horizontalScroll(rememberScrollState()),
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
