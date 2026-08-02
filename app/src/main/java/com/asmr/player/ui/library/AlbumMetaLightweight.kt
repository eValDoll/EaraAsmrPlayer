package com.asmr.player.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.R
import com.asmr.player.ui.theme.AsmrTheme

/**
 * 轻量级专辑信息行 - RJ 和社团
 *
 * 设计特点：
 * - RJ 使用轻量级边框样式
 * - 社团名称直接显示，前置小图标
 * - 横向排列，可滚动
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumHeaderPrimaryMetaLightweight(
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
            .clipToBounds()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // RJ 号 - 保留轻量级边框
        if (normalizedRj.isNotBlank()) {
            Text(
                text = normalizedRj,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (rjOnClick != null) {
                            Modifier.combinedClickable(onClick = rjOnClick)
                        } else {
                            Modifier
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = colorScheme.primary.copy(alpha = 0.38f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        // 社团 - 图标 + 纯文本
        if (normalizedCircle.isNotBlank()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .combinedClickable(
                        onClick = { circleOnClick?.invoke() },
                        onLongClick = circleOnLongClick
                    )
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_album_meta_club),
                    contentDescription = null,
                    tint = colorScheme.textSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = normalizedCircle,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    color = colorScheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 轻量级声优列表
 *
 * 设计特点：
 * - 小图标 + 纯文本
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
            .clipToBounds()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 声优图标
        Icon(
            painter = painterResource(id = R.drawable.ic_album_meta_cv),
            contentDescription = null,
            tint = colorScheme.textSecondary,
            modifier = Modifier.size(14.dp)
        )

        // 声优列表，用中点分隔
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

/**
 * 轻量级标签列表
 *
 * 设计特点：
 * - 小图标 + 井号文本
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
            .clipToBounds()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 标签图标
        Icon(
            painter = painterResource(id = R.drawable.ic_album_meta_tags),
            contentDescription = null,
            tint = colorScheme.textSecondary,
            modifier = Modifier.size(14.dp)
        )

        // 标签列表
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

/**
 * 整合的轻量级信息区域
 *
 * 包含 RJ、社团、声优、标签的完整布局
 */
@Composable
internal fun AlbumHeaderMetaLightweight(
    rjCode: String,
    circle: String,
    cvText: String,
    tags: List<String>,
    modifier: Modifier = Modifier,
    rjOnClick: (() -> Unit)? = null,
    circleOnClick: (() -> Unit)? = null,
    circleOnLongClick: (() -> Unit)? = null,
    onCvClick: ((String) -> Unit)? = null,
    onCvLongClick: ((String) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    onTagLongClick: ((String) -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // RJ 和社团
        AlbumHeaderPrimaryMetaLightweight(
            rjCode = rjCode,
            circle = circle,
            rjOnClick = rjOnClick,
            circleOnClick = circleOnClick,
            circleOnLongClick = circleOnLongClick
        )

        // 声优
        if (cvText.isNotBlank()) {
            AlbumHeaderCvLightweight(
                cvText = cvText,
                onCvClick = onCvClick,
                onCvLongClick = onCvLongClick
            )
        }

        // 标签
        if (tags.isNotEmpty()) {
            AlbumHeaderTagsLightweight(
                tags = tags,
                onTagClick = onTagClick,
                onTagLongClick = onTagLongClick
            )
        }
    }
}

// 辅助函数
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
