package com.asmr.player.ui.library

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

internal const val ALBUM_HEADER_LEADING_ICON_TAG = "album_header_leading_icon"

@Composable
internal fun AlbumHeaderActionContent(
    label: String,
    icon: ImageVector?,
    trailingIcon: ImageVector?,
    color: Color,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color,
                    modifier = Modifier.layoutId("leading").testTag(ALBUM_HEADER_LEADING_ICON_TAG))
            }
            Text(label, style = textStyle, color = color, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.layoutId("label"))
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, tint = color,
                    modifier = Modifier.layoutId("trailing"))
            }
        }
    ) { measurables, constraints ->
        val text = measurables.first { it.layoutId == "label" }
        val leading = measurables.firstOrNull { it.layoutId == "leading" }
        val trailing = measurables.firstOrNull { it.layoutId == "trailing" }
        val iconSize = 17.dp.roundToPx().coerceAtMost(constraints.maxHeight)
        val gap = 4.dp.roundToPx()
        val trailingWidth = if (trailing != null) iconSize + gap else 0
        val preferredTextWidth = text.maxIntrinsicWidth(Constraints.Infinity)
        val preferredWidth = preferredTextWidth + trailingWidth +
            if (leading != null) iconSize + gap + 2 * 7.dp.roundToPx() else 2 * 5.dp.roundToPx()
        val width = constraints.constrainWidth(preferredWidth)
        // Decide in measurement, without an overflow-triggered recomposition.
        val showLeading = leading != null && preferredWidth <= width
        val padding = (if (showLeading) 7.dp else 5.dp).roundToPx()
        val leadingWidth = if (showLeading) iconSize + gap else 0
        val textPlaceable = text.measure(Constraints(
            maxWidth = (width - 2 * padding - leadingWidth - trailingWidth).coerceAtLeast(0),
            maxHeight = constraints.maxHeight,
        ))
        val iconConstraints = Constraints.fixed(iconSize, iconSize)
        val leadingPlaceable = if (showLeading) leading?.measure(iconConstraints) else null
        val trailingPlaceable = trailing?.measure(iconConstraints)
        val height = constraints.constrainHeight(maxOf(textPlaceable.height,
            leadingPlaceable?.height ?: 0, trailingPlaceable?.height ?: 0))
        val contentWidth = leadingWidth + textPlaceable.width + trailingWidth
        layout(width, height) {
            var x = (width - contentWidth) / 2
            leadingPlaceable?.let {
                it.placeRelative(x, (height - it.height) / 2)
                x += leadingWidth
            }
            textPlaceable.placeRelative(x, (height - textPlaceable.height) / 2)
            trailingPlaceable?.placeRelative(x + textPlaceable.width + gap, (height - trailingPlaceable.height) / 2)
        }
    }
}
