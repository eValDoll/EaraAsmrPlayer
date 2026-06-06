package com.asmr.player.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    readOnly: Boolean = false,
    onFieldClick: (() -> Unit)? = null,
    inputTestTag: String? = null,
) {
    val colorScheme = AsmrTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isDark = colorScheme.isDark
    val containerBaseColor = if (isDark) {
        lerp(colorScheme.surface, colorScheme.primarySoft, 0.05f)
    } else {
        lerp(colorScheme.surface, colorScheme.primarySoft, 0.08f)
    }
    val containerColor = containerBaseColor.copy(alpha = if (isDark) 0.93f else 0.95f)
        .compositeOver(colorScheme.background)
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        colorScheme.primaryStrong.copy(alpha = 0.14f)
    }
    val textColor = colorScheme.textPrimary
    val placeholderColor = colorScheme.textTertiary.copy(alpha = if (isDark) 0.74f else 0.68f)
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }
    
    Box(
        modifier = modifier
            .height(48.dp)
            .shadow(
                elevation = if (isDark) 12.dp else 8.dp,
                shape = CircleShape,
                spotColor = if (isDark) Color.Black.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.25f),
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.25f)
            )
            .then(
                Modifier.border(
                    width = 1.dp,
                    color = borderColor,
                    shape = CircleShape
                )
            )
            .background(
                color = containerColor,
                shape = CircleShape
            )
            .then(
                if (onFieldClick != null) {
                    Modifier.semantics {
                        onClick {
                            onFieldClick()
                            true
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (textFieldValue.text.isEmpty()) {
                    AnimatedContent(
                        targetState = placeholder,
                        transitionSpec = {
                            (
                                fadeIn(animationSpec = tween(durationMillis = 260)) +
                                    slideInVertically(
                                        animationSpec = tween(durationMillis = 260),
                                        initialOffsetY = { height -> height / 2 }
                                    )
                                ) togetherWith (
                                fadeOut(animationSpec = tween(durationMillis = 220)) +
                                    slideOutVertically(
                                        animationSpec = tween(durationMillis = 220),
                                        targetOffsetY = { height -> -height / 2 }
                                    )
                                ) using SizeTransform(clip = false)
                        },
                        label = "searchPlaceholder"
                    ) { animatedPlaceholder ->
                        Text(
                            text = animatedPlaceholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = placeholderColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { nextValue ->
                        textFieldValue = nextValue
                        if (nextValue.text != value) {
                            onValueChange(nextValue.text)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (inputTestTag != null) {
                                Modifier.testTag(inputTestTag)
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (onFieldClick != null) {
                                Modifier.semantics {
                                    onClick {
                                        onFieldClick()
                                        true
                                    }
                                }
                            } else {
                                Modifier
                            }
                        ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                    singleLine = true,
                    readOnly = readOnly,
                    cursorBrush = SolidColor(colorScheme.primary),
                    interactionSource = interactionSource,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions
                )
                if (readOnly && onFieldClick != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(onClick = onFieldClick)
                    )
                }
            }
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(6.dp))
                trailingIcon()
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val containerBaseColor = if (isDark) {
        lerp(colorScheme.surface, colorScheme.primarySoft, 0.05f)
    } else {
        lerp(colorScheme.surface, colorScheme.primarySoft, 0.08f)
    }
    val containerColor = containerBaseColor.copy(alpha = if (isDark) 0.93f else 0.95f)
        .compositeOver(colorScheme.background)
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        colorScheme.primaryStrong.copy(alpha = 0.14f)
    }
    Box(
        modifier = modifier
            .size(50.dp)
            .shadow(
                elevation = if (isDark) 12.dp else 8.dp,
                shape = CircleShape,
                spotColor = if (isDark) Color.Black.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.25f),
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.25f)
            )
            .then(
                Modifier.border(
                    width = 1.dp,
                    color = borderColor,
                    shape = CircleShape
                )
            )
            .background(containerColor, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}
