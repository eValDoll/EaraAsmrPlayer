package com.asmr.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asmr.player.R
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.MessageManager

private val AlbumMetaPillShape = RoundedCornerShape(999.dp)
private val AlbumMetaTagShape = RoundedCornerShape(7.dp)
private val AlbumMetaDenseTagShape = RoundedCornerShape(6.dp)

private data class AlbumMetaPalette(
    val container: Color,
    val content: Color,
    val border: Color,
)

internal enum class AlbumMetaAppearance {
    Default,
    OnImage,
}

internal enum class AlbumMetaLeadingVisual {
    None,
    Icon,
}

internal enum class AlbumPrimaryMetaOrder {
    RjThenCircle,
    CircleThenRj,
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
internal fun AlbumPrimaryMetaRow(
    rjCode: String,
    circle: String,
    modifier: Modifier = Modifier,
    rjOnClick: (() -> Unit)? = null,
    circleOnClick: (() -> Unit)? = null,
    circleOnLongClick: (() -> Unit)? = null,
    appearance: AlbumMetaAppearance = AlbumMetaAppearance.Default,
    leadingVisual: AlbumMetaLeadingVisual = AlbumMetaLeadingVisual.None,
    order: AlbumPrimaryMetaOrder = AlbumPrimaryMetaOrder.RjThenCircle,
) {
    val normalizedRj = remember(rjCode) { rjCode.trim() }
    val normalizedCircle = remember(circle) { circle.trim() }
    if (normalizedRj.isBlank() && normalizedCircle.isBlank()) return

    val rjBadge: @Composable () -> Unit = {
        if (normalizedRj.isNotBlank()) {
            AlbumMetaBadge(
                text = normalizedRj,
                tone = AlbumMetaTone.Rj,
                shape = AlbumMetaDenseTagShape,
                onClick = rjOnClick,
                appearance = appearance,
            )
        }
    }
    val circleBadge: @Composable () -> Unit = {
        if (normalizedCircle.isNotBlank()) {
            AlbumMetaBadge(
                text = normalizedCircle,
                tone = AlbumMetaTone.Circle,
                shape = AlbumMetaPillShape,
                onClick = circleOnClick,
                onLongClick = circleOnLongClick,
                appearance = appearance,
                leadingIcon = if (leadingVisual == AlbumMetaLeadingVisual.Icon) AlbumMetaLeadingIconKind.Club else null,
            )
        }
    }

    Row(
        modifier = modifier
            .clipToBounds()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (order) {
            AlbumPrimaryMetaOrder.RjThenCircle -> {
                rjBadge()
                circleBadge()
            }
            AlbumPrimaryMetaOrder.CircleThenRj -> {
                circleBadge()
                rjBadge()
            }
        }
    }
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

private enum class AlbumMetaTone {
    Rj,
    Circle,
    CvLabel,
    CvValue,
    Tag,
}

private enum class AlbumMetaLeadingIconKind {
    Club,
    Cv,
    Tags,
}

@Composable
private fun AlbumMetaLeadingIcon(
    kind: AlbumMetaLeadingIconKind,
    modifier: Modifier = Modifier,
    appearance: AlbumMetaAppearance = AlbumMetaAppearance.Default,
    iconSize: Dp = 14.dp,
) {
    val colorScheme = AsmrTheme.colorScheme
    val (iconRes, tone) = when (kind) {
        AlbumMetaLeadingIconKind.Club -> R.drawable.ic_album_meta_club to AlbumMetaTone.Circle
        AlbumMetaLeadingIconKind.Cv -> R.drawable.ic_album_meta_cv to AlbumMetaTone.CvLabel
        AlbumMetaLeadingIconKind.Tags -> R.drawable.ic_album_meta_tags to AlbumMetaTone.Tag
    }
    val tint = albumMetaPalette(tone, colorScheme, appearance).content

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
    appearance: AlbumMetaAppearance = AlbumMetaAppearance.Default,
    leadingIcon: AlbumMetaLeadingIconKind? = null,
) {
    val colorScheme = AsmrTheme.colorScheme
    val palette = albumMetaPalette(tone, colorScheme, appearance)
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
                appearance = appearance,
                iconSize = if (text.isBlank()) 14.dp else 12.dp,
            )
        }
        if (text.isNotBlank()) {
            Text(
                text = text,
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
    appearance: AlbumMetaAppearance,
): AlbumMetaPalette {
    if (appearance == AlbumMetaAppearance.OnImage) {
        val primaryContainer = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.36f else 0.30f)
        val primarySoftContainer = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.24f else 0.20f)
        val primaryContent = if (colorScheme.isDark) {
            Color.White.copy(alpha = 0.96f)
        } else {
            colorScheme.textPrimary.copy(alpha = 0.88f)
        }
        val primaryBorder = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.52f else 0.42f)
        return when (tone) {
            AlbumMetaTone.Rj -> AlbumMetaPalette(
                container = primaryContainer,
                content = primaryContent,
                border = primaryBorder,
            )
            AlbumMetaTone.Circle -> AlbumMetaPalette(
                container = primarySoftContainer,
                content = primaryContent,
                border = primaryBorder.copy(alpha = 0.78f),
            )
            AlbumMetaTone.CvLabel -> AlbumMetaPalette(
                container = primarySoftContainer,
                content = primaryContent,
                border = primaryBorder.copy(alpha = 0.72f),
            )
            AlbumMetaTone.CvValue -> AlbumMetaPalette(
                container = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.18f else 0.16f),
                content = primaryContent,
                border = primaryBorder.copy(alpha = 0.56f),
            )
            AlbumMetaTone.Tag -> AlbumMetaPalette(
                container = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.18f else 0.14f),
                content = primaryContent.copy(alpha = 0.92f),
                border = primaryBorder.copy(alpha = 0.42f),
            )
        }
    }

    return when (tone) {
        AlbumMetaTone.Rj -> AlbumMetaPalette(
            container = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.38f else 0.28f),
            content = colorScheme.primary,
            border = colorScheme.primary.copy(alpha = 0.36f),
        )
        AlbumMetaTone.Circle -> AlbumMetaPalette(
            container = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.1f else 0.06f),
            content = colorScheme.primary,
            border = colorScheme.primary.copy(alpha = 0.14f),
        )
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
