/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.asmr.player.ui.library

import androidx.compose.foundation.text.modifiers.TextStringSimpleElement
import androidx.compose.foundation.text.selection.LocalSelectionRegistrar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import com.asmr.player.ui.library.prepared.LocalPreparedParagraphSession
import com.asmr.player.ui.library.prepared.PreparedParagraphOccurrence
import com.asmr.player.ui.library.prepared.PreparedTextStringElement

/**
 * 复用 Foundation 1.7.8 文字布局与语义，省去 BasicText 添加的内层绘制缓存。
 * 调用方的圆角裁剪、波纹、点击及长按 modifier 保持原顺序。
 */
@Composable
internal fun AlbumInlineText(
    text: String,
    preparedOccurrence: PreparedParagraphOccurrence? = null,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    // 这些调用使用 AnnotatedString/SelectionController 路径，不改其选择行为。
    if (LocalSelectionRegistrar.current !== null || onTextLayout != null) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = style,
        )
        return
    }
    require(minLines > 0 && maxLines > 0) { "both minLines and maxLines must be greater than zero" }
    require(minLines <= maxLines) { "minLines must be less than or equal to maxLines" }
    val textColor = color.takeOrElse {
        style.color.takeOrElse { LocalContentColor.current }
    }
    val resolvedStyle = style.merge(
        color = textColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign ?: TextAlign.Unspecified,
        lineHeight = lineHeight,
        fontFamily = fontFamily,
        textDecoration = textDecoration,
        fontStyle = fontStyle,
        letterSpacing = letterSpacing,
    )
    val resolver = LocalFontFamilyResolver.current
    val session = LocalPreparedParagraphSession.current?.takeIf {
        preparedOccurrence != null && minLines == 1 && it.fontFamilyResolver === resolver
    }
    val textElement = if (session !== null) {
        PreparedTextStringElement(
            occurrence = checkNotNull(preparedOccurrence),
            text = text,
            style = resolvedStyle,
            fontFamilyResolver = resolver,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            color = null,
            preparedSession = session,
        )
    } else {
        TextStringSimpleElement(
            text = text,
            style = resolvedStyle,
            fontFamilyResolver = resolver,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            color = null,
        )
    }
    Layout(
        modifier = modifier.then(textElement),
        measurePolicy = InlineTextEmptyMeasurePolicy,
    )
}

// 与 1.7.8 BasicText 的 EmptyMeasurePolicy 相同；TextStringSimpleNode 会传入固定尺寸。
private object InlineTextEmptyMeasurePolicy : MeasurePolicy {
    private val placementBlock: Placeable.PlacementScope.() -> Unit = {}

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult = layout(
        constraints.maxWidth,
        constraints.maxHeight,
        placementBlock = placementBlock,
    )
}
