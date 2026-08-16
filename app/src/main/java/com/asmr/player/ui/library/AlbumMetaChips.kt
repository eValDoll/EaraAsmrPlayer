package com.asmr.player.ui.library

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.asmr.player.R
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.MessageManager

private val AlbumMetaPillShape = RoundedCornerShape(999.dp)
private val AlbumMetaTagShape = RoundedCornerShape(7.dp)
private val AlbumHeaderMetaExpandCollapseSpec = tween<IntSize>(
    durationMillis = 280,
    easing = FastOutSlowInEasing,
)
private const val AlbumHeaderMetaCollapsedLines = 2

private data class AlbumMetaPalette(
    val container: Color,
    val content: Color,
    val border: Color,
)

internal enum class AlbumMetaLeadingVisual {
    None,
    Icon,
}

@Composable
internal fun rememberAlbumMetaCopyAction(
    messageManager: MessageManager,
): (String, String) -> Unit {
    val clipboard = LocalClipboardManager.current
    return remember(clipboard, messageManager) {
        { label: String, value: String ->
            val normalizedValue = value.trim()
            if (normalizedValue.isBlank()) {
                Unit
            } else {
                clipboard.setText(AnnotatedString(normalizedValue))
                messageManager.showSuccess("$label 已复制")
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

@Composable
internal fun AlbumCvChipsSingleLine(
    cvText: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    onCvClick: ((String) -> Unit)? = null,
    onCvLongClick: ((String) -> Unit)? = null,
    leadingVisual: AlbumMetaLeadingVisual = AlbumMetaLeadingVisual.None,
) {
    val cvs = remember(cvText) { parseAlbumCvNames(cvText) }
    if (cvs.isEmpty()) return

    Row(
        modifier = modifier
            .clipToBounds()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLabel) {
            if (leadingVisual == AlbumMetaLeadingVisual.Icon) {
                AlbumMetaBadge(
                    text = "",
                    tone = AlbumMetaTone.CvLabel,
                    shape = AlbumMetaPillShape,
                    leadingIcon = AlbumMetaLeadingIconKind.Cv,
                )
            } else {
                AlbumMetaBadge(
                    text = "CV",
                    tone = AlbumMetaTone.CvLabel,
                    shape = AlbumMetaPillShape,
                    textWeight = FontWeight.SemiBold,
                )
            }
        }
        cvs.forEach { cv ->
            AlbumMetaBadge(
                text = cv,
                tone = AlbumMetaTone.CvValue,
                shape = AlbumMetaPillShape,
                maxWidth = 200.dp,
                onClick = { onCvClick?.invoke(cv) },
                onLongClick = onCvLongClick?.let { longClick -> { longClick(cv) } },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlbumCvChipsFlow(
    cvText: String,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
    showLabel: Boolean = true,
    onCvClick: ((String) -> Unit)? = null,
    onCvLongClick: ((String) -> Unit)? = null,
    leadingVisual: AlbumMetaLeadingVisual = AlbumMetaLeadingVisual.None,
) {
    val cvs = remember(cvText) { parseAlbumCvNames(cvText) }
    if (cvs.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
    ) {
        if (showLabel) {
            if (leadingVisual == AlbumMetaLeadingVisual.Icon) {
                AlbumMetaBadge(
                    text = "",
                    tone = AlbumMetaTone.CvLabel,
                    shape = AlbumMetaPillShape,
                    leadingIcon = AlbumMetaLeadingIconKind.Cv,
                )
            } else {
                AlbumMetaBadge(
                    text = "CV",
                    tone = AlbumMetaTone.CvLabel,
                    shape = AlbumMetaPillShape,
                    textWeight = FontWeight.SemiBold,
                )
            }
        }
        cvs.forEach { cv ->
            AlbumMetaBadge(
                text = cv,
                tone = AlbumMetaTone.CvValue,
                shape = AlbumMetaPillShape,
                maxWidth = 200.dp,
                onClick = { onCvClick?.invoke(cv) },
                onLongClick = onCvLongClick?.let { longClick -> { longClick(cv) } },
            )
        }
    }
}

@Composable
internal fun AlbumTagsSingleLine(
    tags: List<String>,
    modifier: Modifier = Modifier,
    onTagClick: ((String) -> Unit)? = null,
    onTagLongClick: ((String) -> Unit)? = null,
    leadingVisual: AlbumMetaLeadingVisual = AlbumMetaLeadingVisual.None,
) {
    val normalizedTags = remember(tags) { normalizeAlbumTags(tags) }
    if (normalizedTags.isEmpty()) return

    Row(
        modifier = modifier
            .clipToBounds()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingVisual == AlbumMetaLeadingVisual.Icon) {
            AlbumMetaBadge(
                text = "",
                tone = AlbumMetaTone.Tag,
                shape = AlbumMetaTagShape,
                leadingIcon = AlbumMetaLeadingIconKind.Tags,
            )
        }
        normalizedTags.forEach { tag ->
            AlbumMetaBadge(
                text = if (tag.startsWith("#")) tag else "#$tag",
                tone = AlbumMetaTone.Tag,
                shape = AlbumMetaTagShape,
                maxWidth = 220.dp,
                onClick = { onTagClick?.invoke(tag) },
                onLongClick = onTagLongClick?.let { longClick -> { longClick(tag) } },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlbumTagsFlow(
    tags: List<String>,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
    onTagClick: ((String) -> Unit)? = null,
    onTagLongClick: ((String) -> Unit)? = null,
    leadingVisual: AlbumMetaLeadingVisual = AlbumMetaLeadingVisual.None,
) {
    val normalizedTags = remember(tags) { normalizeAlbumTags(tags) }
    if (normalizedTags.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
    ) {
        if (leadingVisual == AlbumMetaLeadingVisual.Icon) {
            AlbumMetaBadge(
                text = "",
                tone = AlbumMetaTone.Tag,
                shape = AlbumMetaTagShape,
                leadingIcon = AlbumMetaLeadingIconKind.Tags,
            )
        }
        normalizedTags.forEach { tag ->
            AlbumMetaBadge(
                text = if (tag.startsWith("#")) tag else "#$tag",
                tone = AlbumMetaTone.Tag,
                shape = AlbumMetaTagShape,
                maxWidth = 220.dp,
                onClick = { onTagClick?.invoke(tag) },
                onLongClick = onTagLongClick?.let { longClick -> { longClick(tag) } },
            )
        }
    }
}

@Composable
internal fun AlbumHeaderCvFlow(
    cvText: String,
    modifier: Modifier = Modifier,
    horizontalArrangement: Dp = 8.dp,
    verticalArrangement: Dp = 6.dp,
    onCvClick: ((String) -> Unit)? = null,
    onCvLongClick: ((String) -> Unit)? = null,
) {
    val cvs = remember(cvText) { parseAlbumCvNames(cvText) }
    if (cvs.isEmpty()) return
    var expanded by rememberSaveable(cvText) { mutableStateOf(false) }

    AlbumHeaderMetaFlow(
        modifier = modifier,
        label = "声优",
        labelTone = AlbumMetaTone.CvLabel,
        labelIcon = AlbumMetaLeadingIconKind.Cv,
        itemCount = cvs.size,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        horizontalSpacing = horizontalArrangement,
        verticalSpacing = verticalArrangement,
    ) { index ->
        val cv = cvs[index]
        AlbumMetaBadge(
            text = cv,
            tone = AlbumMetaTone.CvValue,
            shape = AlbumMetaTagShape,
            maxWidth = 200.dp,
            onClick = { onCvClick?.invoke(cv) },
            onLongClick = onCvLongClick?.let { longClick -> { longClick(cv) } },
        )
    }
}

@Composable
internal fun AlbumHeaderTagsFlow(
    tags: List<String>,
    modifier: Modifier = Modifier,
    horizontalArrangement: Dp = 8.dp,
    verticalArrangement: Dp = 6.dp,
    onTagClick: ((String) -> Unit)? = null,
    onTagLongClick: ((String) -> Unit)? = null,
) {
    val normalizedTags = remember(tags) { normalizeAlbumTags(tags) }
    if (normalizedTags.isEmpty()) return
    var expanded by rememberSaveable(normalizedTags) { mutableStateOf(false) }

    AlbumHeaderMetaFlow(
        modifier = modifier,
        label = "标签",
        labelTone = AlbumMetaTone.Tag,
        labelIcon = AlbumMetaLeadingIconKind.Tags,
        itemCount = normalizedTags.size,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        horizontalSpacing = horizontalArrangement,
        verticalSpacing = verticalArrangement,
    ) { index ->
        val tag = normalizedTags[index]
        AlbumMetaBadge(
            text = if (tag.startsWith("#")) tag else "#$tag",
            tone = AlbumMetaTone.Tag,
            shape = AlbumMetaTagShape,
            maxWidth = 220.dp,
            onClick = { onTagClick?.invoke(tag) },
            onLongClick = onTagLongClick?.let { longClick -> { longClick(tag) } },
        )
    }
}

@Composable
private fun AlbumHeaderMetaFlow(
    label: String,
    labelTone: AlbumMetaTone,
    labelIcon: AlbumMetaLeadingIconKind,
    itemCount: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    modifier: Modifier = Modifier,
    itemContent: @Composable (Int) -> Unit,
) {
    val labelGap = with(LocalDensity.current) {
        minOf(horizontalSpacing, MaterialTheme.typography.labelSmall.fontSize.toDp() / 2f)
    }
    val labelTextMinWidth = with(LocalDensity.current) {
        MaterialTheme.typography.labelSmall.fontSize.toDp() * label.length + 4.dp
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = AlbumHeaderMetaExpandCollapseSpec,
                alignment = Alignment.TopStart,
            ),
        horizontalArrangement = Arrangement.spacedBy(labelGap),
        verticalAlignment = Alignment.Top,
    ) {
        AlbumMetaBadge(
            text = label,
            tone = labelTone,
            shape = AlbumMetaTagShape,
            textWeight = FontWeight.SemiBold,
            leadingIcon = labelIcon,
            minTextWidth = labelTextMinWidth,
        )
        AlbumMetaMeasuredFlow(
            modifier = Modifier.weight(1f),
            itemCount = itemCount,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
            itemContent = itemContent,
        )
    }
}

@Composable
private fun AlbumMetaMeasuredFlow(
    itemCount: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    modifier: Modifier = Modifier,
    itemContent: @Composable (Int) -> Unit,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val horizontalSpacingPx = horizontalSpacing.roundToPx()
        val verticalSpacingPx = verticalSpacing.roundToPx()
        val availableWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val itemPlaceables = subcompose("items") {
            repeat(itemCount) { index ->
                key(index) { itemContent(index) }
            }
        }.map { measurable -> measurable.measure(childConstraints) }

        val allItemWidths = itemPlaceables.map { it.width }
        val allItemsFitCollapsed = albumMetaFlowLineCount(
            itemWidths = allItemWidths,
            maxWidth = availableWidth,
            horizontalSpacing = horizontalSpacingPx,
        ) <= AlbumHeaderMetaCollapsedLines

        val displayedPlaceables = when {
            allItemsFitCollapsed -> itemPlaceables

            expanded -> {
                val collapsePlaceable = subcompose("collapse") {
                    AlbumMetaToggleAction(
                        text = "收起",
                        expanded = true,
                        onClick = { onExpandedChange(false) },
                    )
                }.single().measure(childConstraints)
                itemPlaceables + collapsePlaceable
            }

            else -> {
                val overflowProbe = subcompose("overflowProbe") {
                    AlbumMetaToggleAction(
                        text = "展开 $itemCount",
                        expanded = false,
                        onClick = { onExpandedChange(true) },
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }.single().measure(childConstraints)
                val visibleCount = albumMetaCollapsedVisibleCount(
                    itemWidths = allItemWidths,
                    overflowWidth = overflowProbe.width,
                    maxWidth = availableWidth,
                    horizontalSpacing = horizontalSpacingPx,
                    maxLines = AlbumHeaderMetaCollapsedLines,
                )
                val hiddenCount = itemCount - visibleCount
                val overflowPlaceable = subcompose("overflow:$hiddenCount") {
                    AlbumMetaToggleAction(
                        text = "展开 $hiddenCount",
                        expanded = false,
                        onClick = { onExpandedChange(true) },
                    )
                }.single().measure(childConstraints)
                itemPlaceables.take(visibleCount) + overflowPlaceable
            }
        }

        val flowMeasure = measureAlbumMetaFlow(
            placeables = displayedPlaceables,
            maxWidth = availableWidth,
            horizontalSpacing = horizontalSpacingPx,
            verticalSpacing = verticalSpacingPx,
        )
        val layoutWidth = flowMeasure.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val layoutHeight = flowMeasure.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(layoutWidth, layoutHeight) {
            displayedPlaceables.forEachIndexed { index, placeable ->
                val position = flowMeasure.positions[index]
                placeable.placeRelative(position.first, position.second)
            }
        }
    }
}

@Composable
private fun AlbumMetaToggleAction(
    text: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = modifier
            .clip(AlbumMetaTagShape)
            .clickable(onClick = onClick)
            .padding(start = 5.dp, end = 3.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.primaryStrong,
            maxLines = 1,
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = colorScheme.primaryStrong,
            modifier = Modifier.size(14.dp),
        )
    }
}

private data class AlbumMetaFlowMeasure(
    val width: Int,
    val height: Int,
    val positions: List<Pair<Int, Int>>,
)

private fun measureAlbumMetaFlow(
    placeables: List<Placeable>,
    maxWidth: Int,
    horizontalSpacing: Int,
    verticalSpacing: Int,
): AlbumMetaFlowMeasure {
    if (placeables.isEmpty()) return AlbumMetaFlowMeasure(0, 0, emptyList())
    var x = 0
    var y = 0
    var rowHeight = 0
    var measuredWidth = 0
    val positions = ArrayList<Pair<Int, Int>>(placeables.size)

    placeables.forEach { placeable ->
        if (x > 0 && x + horizontalSpacing + placeable.width > maxWidth) {
            y += rowHeight + verticalSpacing
            x = 0
            rowHeight = 0
        }
        val itemX = if (x == 0) 0 else x + horizontalSpacing
        positions += itemX to y
        x = itemX + placeable.width
        rowHeight = maxOf(rowHeight, placeable.height)
        measuredWidth = maxOf(measuredWidth, x)
    }
    return AlbumMetaFlowMeasure(
        width = measuredWidth,
        height = y + rowHeight,
        positions = positions,
    )
}

internal fun albumMetaFlowLineCount(
    itemWidths: List<Int>,
    maxWidth: Int,
    horizontalSpacing: Int,
): Int {
    if (itemWidths.isEmpty()) return 0
    if (maxWidth <= 0) return itemWidths.size
    var lineCount = 1
    var rowWidth = 0
    itemWidths.forEach { rawWidth ->
        val width = rawWidth.coerceAtMost(maxWidth)
        if (rowWidth > 0 && rowWidth + horizontalSpacing + width > maxWidth) {
            lineCount += 1
            rowWidth = width
        } else {
            rowWidth = if (rowWidth == 0) width else rowWidth + horizontalSpacing + width
        }
    }
    return lineCount
}

internal fun albumMetaCollapsedVisibleCount(
    itemWidths: List<Int>,
    overflowWidth: Int,
    maxWidth: Int,
    horizontalSpacing: Int,
    maxLines: Int,
): Int {
    if (itemWidths.isEmpty() || maxLines <= 0) return 0
    var low = 0
    var high = itemWidths.lastIndex
    var result = 0
    while (low <= high) {
        val visibleCount = (low + high) ushr 1
        val lineCount = albumMetaFlowLineCountWithTrailing(
            itemWidths = itemWidths,
            visibleCount = visibleCount,
            trailingWidth = overflowWidth,
            maxWidth = maxWidth,
            horizontalSpacing = horizontalSpacing,
        )
        if (lineCount <= maxLines) {
            result = visibleCount
            low = visibleCount + 1
        } else {
            high = visibleCount - 1
        }
    }
    return result
}

private fun albumMetaFlowLineCountWithTrailing(
    itemWidths: List<Int>,
    visibleCount: Int,
    trailingWidth: Int,
    maxWidth: Int,
    horizontalSpacing: Int,
): Int {
    val widths = object : AbstractList<Int>() {
        override val size: Int = visibleCount + 1
        override fun get(index: Int): Int {
            return if (index < visibleCount) itemWidths[index] else trailingWidth
        }
    }
    return albumMetaFlowLineCount(widths, maxWidth, horizontalSpacing)
}

private enum class AlbumMetaTone {
    CvLabel,
    CvValue,
    Tag,
}

private enum class AlbumMetaLeadingIconKind {
    Cv,
    Tags,
}

@Composable
private fun AlbumMetaLeadingIcon(
    kind: AlbumMetaLeadingIconKind,
    modifier: Modifier = Modifier,
    iconSize: Dp = 14.dp,
) {
    val colorScheme = AsmrTheme.colorScheme
    val (iconRes, tone) = when (kind) {
        AlbumMetaLeadingIconKind.Cv -> R.drawable.ic_album_meta_cv to AlbumMetaTone.CvLabel
        AlbumMetaLeadingIconKind.Tags -> R.drawable.ic_album_meta_tags to AlbumMetaTone.Tag
    }
    val tint = albumMetaPalette(tone, colorScheme).content

    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(iconSize)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumMetaBadge(
    text: String,
    tone: AlbumMetaTone,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    maxWidth: androidx.compose.ui.unit.Dp? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    textWeight: FontWeight? = null,
    leadingIcon: AlbumMetaLeadingIconKind? = null,
    minTextWidth: Dp? = null,
) {
    val colorScheme = AsmrTheme.colorScheme
    val palette = albumMetaPalette(tone, colorScheme)
    val styledModifier = modifier
        .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
        .clip(shape)
        .let {
            if (onClick != null || onLongClick != null) {
                it.combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick,
                )
            } else {
                it
            }
        }
        .background(palette.container, shape)
        .border(0.5.dp, palette.border, shape)
        .padding(
            horizontal = 7.dp,
            vertical = 2.dp,
        )

    Row(
        modifier = styledModifier,
        horizontalArrangement = Arrangement.spacedBy(if (leadingIcon != null && text.isNotBlank()) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            AlbumMetaLeadingIcon(
                kind = leadingIcon,
                iconSize = if (text.isBlank()) 14.dp else 12.dp,
            )
        }
        if (text.isNotBlank()) {
            Text(
                text = text,
                modifier = Modifier.then(
                    if (minTextWidth != null) Modifier.widthIn(min = minTextWidth) else Modifier
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = textWeight,
                color = palette.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun albumMetaPalette(
    tone: AlbumMetaTone,
    colorScheme: com.asmr.player.ui.theme.AsmrColorScheme,
): AlbumMetaPalette {
    return when (tone) {
        AlbumMetaTone.CvLabel -> AlbumMetaPalette(
            container = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.16f else 0.1f),
            content = colorScheme.primary,
            border = colorScheme.primary.copy(alpha = 0.18f),
        )
        AlbumMetaTone.CvValue -> AlbumMetaPalette(
            container = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.1f else 0.06f),
            content = colorScheme.primary,
            border = colorScheme.primary.copy(alpha = 0.14f),
        )
        AlbumMetaTone.Tag -> AlbumMetaPalette(
            container = colorScheme.surfaceVariant.copy(alpha = if (colorScheme.isDark) 0.75f else 0.92f),
            content = colorScheme.textSecondary,
            border = colorScheme.onSurfaceVariant.copy(alpha = if (colorScheme.isDark) 0.32f else 0.18f),
        )
    }
}
