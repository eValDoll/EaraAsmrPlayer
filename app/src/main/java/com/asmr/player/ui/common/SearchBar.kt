package com.asmr.player.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.asmr.player.ui.theme.AsmrTheme
import kotlin.math.roundToInt

private data class SearchBarTextToolbarMenuState(
    val rect: Rect,
    val onCopyRequested: (() -> Unit)?,
    val onPasteRequested: (() -> Unit)?,
    val onCutRequested: (() -> Unit)?,
    val onSelectAllRequested: (() -> Unit)?
)

private class SearchBarPopupTextToolbar : TextToolbar {
    var menuState by mutableStateOf<SearchBarTextToolbarMenuState?>(null)
        private set

    override val status: TextToolbarStatus
        get() = if (menuState == null) TextToolbarStatus.Hidden else TextToolbarStatus.Shown

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        menuState = SearchBarTextToolbarMenuState(
            rect = rect,
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested
        )
    }

    override fun hide() {
        menuState = null
    }
}

private class SearchBarTextToolbarPositionProvider(
    private val composeView: android.view.View,
    private val rect: Rect,
    private val marginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val locationInWindow = IntArray(2)
        composeView.getLocationInWindow(locationInWindow)

        val rectLeft = locationInWindow[0] + rect.left.roundToInt()
        val rectRight = locationInWindow[0] + rect.right.roundToInt()
        val rectTop = locationInWindow[1] + rect.top.roundToInt()
        val rectBottom = locationInWindow[1] + rect.bottom.roundToInt()
        val desiredX = ((rectLeft + rectRight) / 2f - popupContentSize.width / 2f).roundToInt()
        val desiredY = if (rectTop - popupContentSize.height - marginPx >= 0) {
            rectTop - popupContentSize.height - marginPx
        } else {
            rectBottom + marginPx
        }
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(
            x = desiredX.coerceIn(0, maxX),
            y = desiredY.coerceIn(0, maxY)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBarSelectionToolbarPopup(
    toolbar: SearchBarPopupTextToolbar
) {
    val menuState = toolbar.menuState ?: return
    val composeView = LocalView.current
    val density = LocalDensity.current
    val marginPx = with(density) { 8.dp.roundToPx() }
    val popupPositionProvider = remember(composeView, menuState.rect, marginPx) {
        SearchBarTextToolbarPositionProvider(
            composeView = composeView,
            rect = menuState.rect,
            marginPx = marginPx
        )
    }
    val colorScheme = AsmrTheme.colorScheme
    val containerColor = lerp(
        colorScheme.surface,
        colorScheme.primarySoft,
        if (colorScheme.isDark) 0.08f else 0.14f
    ).copy(alpha = if (colorScheme.isDark) 0.98f else 0.99f)
        .compositeOver(colorScheme.background)
    val borderColor = if (colorScheme.isDark) {
        Color.White.copy(alpha = 0.18f)
    } else {
        colorScheme.primaryStrong.copy(alpha = 0.18f)
    }
    val dividerColor = if (colorScheme.isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        colorScheme.primaryStrong.copy(alpha = 0.12f)
    }

    fun runAction(action: (() -> Unit)?) {
        action?.invoke()
        toolbar.hide()
    }

    val actions = buildList {
        menuState.onCopyRequested?.let { add("复制" to it) }
        menuState.onPasteRequested?.let { add("粘贴" to it) }
        menuState.onCutRequested?.let { add("剪切" to it) }
        menuState.onSelectAllRequested?.let { add("全选" to it) }
    }

    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = toolbar::hide,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = containerColor,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions.forEachIndexed { index, (label, action) ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .width(1.dp)
                                .background(dividerColor)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { runAction(action) }
                            .padding(horizontal = 16.dp, vertical = 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.textPrimary
                        )
                    }
                }
            }
        }
    }
}

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
    val fieldClickInteractionSource = remember { MutableInteractionSource() }
    val popupTextToolbar = remember { SearchBarPopupTextToolbar() }
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
                CompositionLocalProvider(LocalTextToolbar provides popupTextToolbar) {
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
                            .onFocusChanged { focusState ->
                                if (!focusState.hasFocus) {
                                    popupTextToolbar.hide()
                                }
                            }
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
                }
                if (readOnly && onFieldClick != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = fieldClickInteractionSource,
                                indication = null,
                                onClick = onFieldClick
                            )
                    )
                }
            }
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(6.dp))
                trailingIcon()
            }
        }

        SearchBarSelectionToolbarPopup(toolbar = popupTextToolbar)
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val containerBaseColor = if (isDark) {
        lerp(colorScheme.surface, colorScheme.primarySoft, 0.05f)
    } else {
        lerp(colorScheme.surface, colorScheme.primarySoft, 0.08f)
    }
    val inactiveContainerColor = containerBaseColor.copy(alpha = if (isDark) 0.93f else 0.95f)
        .compositeOver(colorScheme.background)
    val activeContainerColor = colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.12f)
        .compositeOver(inactiveContainerColor)
    val containerColor = if (active) activeContainerColor else inactiveContainerColor
    val inactiveBorderColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        colorScheme.primaryStrong.copy(alpha = 0.14f)
    }
    val borderColor = if (active) colorScheme.primary.copy(alpha = 0.46f) else inactiveBorderColor
    val iconTint = if (active) colorScheme.primary else colorScheme.onSurfaceVariant
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
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}
