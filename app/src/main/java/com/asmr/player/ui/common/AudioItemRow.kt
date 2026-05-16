package com.asmr.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Unspecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor

internal data class AudioItemMenuAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val iconTint: Color = Unspecified,
    val testTag: String? = null,
    val showDividerBefore: Boolean = false
)

internal val AudioItemTrailingSpacing = 4.dp
internal val AudioItemSubtitleStampSpacing = 4.dp
internal val AudioItemMenuButtonSize = 36.dp
internal val AudioItemMenuIconSize = 18.dp
internal val AudioItemTrailingEndPadding = 2.dp

@Composable
internal fun AudioItemRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable (() -> Unit))? = null,
    showSubtitleStamp: Boolean = false,
    subtitleStampModifier: Modifier = Modifier,
    subtitleStampTestTag: String? = null,
    menuButtonTestTag: String? = null,
    actions: List<AudioItemMenuAction> = emptyList(),
    trailingContent: (@Composable (RowScope.() -> Unit))? = null,
    titleTextStyle: TextStyle? = null,
    subtitleTextStyle: TextStyle? = null,
    titleColor: Color = Unspecified,
    subtitleColor: Color = Unspecified,
    fixedTrailingSubtitle: String = "",
    showClickIndication: Boolean = true
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    val interactionSource = remember { MutableInteractionSource() }
    val resolvedTitleStyle = titleTextStyle ?: MaterialTheme.typography.bodyLarge
    val resolvedSubtitleStyle = subtitleTextStyle ?: MaterialTheme.typography.bodySmall
    val resolvedTitleColor = if (titleColor != Unspecified) titleColor else colorScheme.textPrimary
    val resolvedSubtitleColor = if (subtitleColor != Unspecified) subtitleColor else colorScheme.textTertiary

    val clickableModifier = when {
        onClick == null -> Modifier
        showClickIndication -> Modifier.clickable(onClick = onClick)
        else -> Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    }

    ListItem(
        headlineContent = {
            Text(
                text = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = resolvedTitleStyle,
                color = resolvedTitleColor
            )
        },
        supportingContent = {
            when {
                subtitle.isNotBlank() && fixedTrailingSubtitle.isNotBlank() -> {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = resolvedSubtitleStyle,
                            color = resolvedSubtitleColor,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = fixedTrailingSubtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            textAlign = TextAlign.End,
                            style = resolvedSubtitleStyle,
                            color = resolvedSubtitleColor,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                subtitle.isNotBlank() -> {
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = resolvedSubtitleStyle,
                        color = resolvedSubtitleColor
                    )
                }
                fixedTrailingSubtitle.isNotBlank() -> {
                    Text(
                        text = fixedTrailingSubtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        style = resolvedSubtitleStyle,
                        color = resolvedSubtitleColor
                    )
                }
            }
        },
        leadingContent = leadingContent,
        trailingContent = {
            val showMenu = actions.isNotEmpty()
            val showTrailing = showSubtitleStamp || trailingContent != null || showMenu
            if (!showTrailing) return@ListItem

            var expanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.padding(end = AudioItemTrailingEndPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AudioItemTrailingSpacing)
            ) {
                if (showSubtitleStamp) {
                    val stampModifier = if (subtitleStampTestTag != null) {
                        subtitleStampModifier.testTag(subtitleStampTestTag)
                    } else {
                        subtitleStampModifier
                    }
                    SubtitleStamp(modifier = stampModifier)
                }

                trailingContent?.invoke(this)

                if (showMenu) {
                    Box {
                        IconButton(
                            onClick = { expanded = true },
                            modifier = if (menuButtonTestTag != null) {
                                Modifier
                                    .size(AudioItemMenuButtonSize)
                                    .testTag(menuButtonTestTag)
                            } else {
                                Modifier.size(AudioItemMenuButtonSize)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(AudioItemMenuIconSize)
                            )
                        }
                        MaterialTheme(
                            colorScheme = materialColorScheme.copy(
                                surface = dynamicContainerColor,
                                surfaceContainer = dynamicContainerColor
                            )
                        ) {
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(dynamicContainerColor)
                            ) {
                                actions.forEach { action ->
                                    if (action.showDividerBefore) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            thickness = 0.5.dp,
                                            color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                                        )
                                    }

                                    DropdownMenuItem(
                                        text = { Text(action.label) },
                                        modifier = if (action.testTag != null) {
                                            Modifier.testTag(action.testTag)
                                        } else {
                                            Modifier
                                        },
                                        onClick = {
                                            expanded = false
                                            action.onClick()
                                        },
                                        leadingIcon = action.icon?.let { icon ->
                                            {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (action.iconTint != Unspecified) {
                                                        action.iconTint
                                                    } else {
                                                        colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.then(clickableModifier)
    )
}
