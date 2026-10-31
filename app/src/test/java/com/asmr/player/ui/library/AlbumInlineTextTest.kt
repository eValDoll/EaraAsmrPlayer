@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.asmr.player.ui.library

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.OwnedLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.ui.createEaraComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33], qualifiers = "w500dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalFoundationApi::class)
class AlbumInlineTextTest {
    @get:Rule val compose = createEaraComposeRule()

    private val baselines = mutableMapOf<String, Pair<Int, Int>>()

    @Test
    fun geometryAndResolvedStyle_matchMaterialAcrossUpdatesAndEnvironment() {
        val initial = TextCase()
        val cases = listOf(
            initial,
            initial.copy(contentColor = Color.Green),
            initial.copy(style = TextStyle(color = Color.Red, fontSize = 13.sp)),
            initial.copy(
                text = "繁體中文 日本語 العربية English 123456789",
                color = Color.Blue,
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 0.4.sp,
                    textDecoration = TextDecoration.Underline,
                ),
                density = Density(1.25f, 1.6f),
                direction = LayoutDirection.Rtl,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                width = 135.dp,
            ),
            initial.copy(
                text = "第一行\nSecond line\nالعربية\n第四行",
                maxLines = 3,
                minLines = 2,
                style = TextStyle(fontSize = 14.sp, lineHeight = 24.sp, textAlign = TextAlign.End),
            ),
            initial.copy(text = "This long line must ellipsize", softWrap = false, width = 65.dp),
            initial.copy(text = "", minLines = 2, maxLines = 2),
            initial,
        )
        val current = mutableStateOf(initial)
        compose.setContent { TextPair(current.value) }
        cases.forEach { case ->
            compose.runOnIdle { current.value = case }
            assertPairGeometry()
            val expectedColor = when {
                case.color != Color.Unspecified -> case.color
                case.style.color != Color.Unspecified -> case.style.color
                else -> case.contentColor
            }
            assertEquals(expectedColor, textLayout(CANDIDATE).layoutInput.style.color)
        }
    }

    @Test
    fun actualLayerTopology_removesOnlyInnerLayerAndRetainsRoundedClip() {
        compose.setContent { TextPair(TextCase()) }
        val originalLayers = layers(ORIGINAL)
        val candidateLayers = layers(CANDIDATE)
        val originalSize = compose.onNodeWithTag(ORIGINAL).fetchSemanticsNode().size
        val candidateSize = compose.onNodeWithTag(CANDIDATE).fetchSemanticsNode().size
        compose.runOnIdle {
            assertEquals(originalLayers.size - 1, candidateLayers.size)
            assertTrue("调用方圆角层仍需存在", candidateLayers.isNotEmpty())
            listOf(originalLayers to originalSize, candidateLayers to candidateSize).forEach { (layers, size) ->
                val roundedLayers = layers.filter { !it.isInLayer(Offset(0.1f, 0.1f)) }
                assertEquals("每个文字保留原来的一个圆角裁剪层", 1, roundedLayers.size)
                assertTrue(roundedLayers.single().isInLayer(Offset(size.width / 2f, size.height / 2f)))
            }
        }
    }

    @Test
    fun originalClipAndCombinedClick_preservePointerAndSemanticActions() {
        val clicks = mutableMapOf<String, Int>()
        val longClicks = mutableMapOf<String, Int>()
        compose.setContent {
            TextPair(
                TextCase(),
                onClick = { tag -> clicks[tag] = clicks.getOrDefault(tag, 0) + 1 },
                onLongClick = { tag -> longClicks[tag] = longClicks.getOrDefault(tag, 0) + 1 },
            )
        }
        listOf(ORIGINAL, CANDIDATE).forEach { tag ->
            val node = compose.onNodeWithTag(tag)
            node.assertHasClickAction()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
            node.performTouchInput { click() }
            node.performTouchInput { longClick() }
            compose.runOnIdle {
                assertEquals(1, clicks[tag])
                assertEquals(1, longClicks[tag])
            }
        }
        assertPairGeometry()
    }

    @Test
    fun systemTextSubstitution_keepsOriginalActionsAndMeasuredGeometry() {
        val text = mutableStateOf("Original CV")
        compose.setContent { TextPair(TextCase(text = text.value)) }
        val originalSize = compose.onNodeWithTag(CANDIDATE).fetchSemanticsNode().size
        listOf(ORIGINAL, CANDIDATE).forEach { tag ->
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetTextSubstitution) {
                assertTrue(it(AnnotatedString("翻译后的名字更长")))
            }
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.ShowTextSubstitution) {
                assertTrue(it(true))
            }
        }
        assertPairGeometry()
        assertEquals(
            AnnotatedString("翻译后的名字更长"),
            compose.onNodeWithTag(CANDIDATE).fetchSemanticsNode().config[SemanticsProperties.TextSubstitution],
        )
        assertTrue(compose.onNodeWithTag(CANDIDATE).fetchSemanticsNode().config[SemanticsProperties.IsShowingTextSubstitution])
        listOf(ORIGINAL, CANDIDATE).forEach { tag ->
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.ShowTextSubstitution) {
                assertTrue(it(false))
            }
        }
        assertPairGeometry()
        assertEquals(originalSize, compose.onNodeWithTag(CANDIDATE).fetchSemanticsNode().size)
        listOf(ORIGINAL, CANDIDATE).forEach { tag ->
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.ClearTextSubstitution) {
                assertTrue(it())
            }
            assertFalse(compose.onNodeWithTag(tag).fetchSemanticsNode().config.contains(SemanticsProperties.TextSubstitution))
        }
        compose.runOnIdle { text.value = "New CV 新名字" }
        assertPairGeometry()
        assertEquals("New CV 新名字", textLayout(CANDIDATE).layoutInput.text.text)
    }

    @Test
    fun selectionAndLayoutCallback_useMaterialPathWithoutLosingCallback() {
        val selection = mutableStateOf(true)
        val callback = mutableStateOf(false)
        val callbacks = mutableMapOf<String, TextLayoutResult>()
        compose.setContent {
            val content: @Composable () -> Unit = {
                TextPair(
                    TextCase(),
                    onTextLayout = if (callback.value) {
                        { tag, result -> callbacks[tag] = result }
                    } else null,
                )
            }
            if (selection.value) SelectionContainer(content = content) else content()
        }
        assertPairGeometry()
        assertEquals(layers(ORIGINAL).size, layers(CANDIDATE).size)
        compose.runOnIdle {
            selection.value = false
            callback.value = true
        }
        assertPairGeometry()
        compose.runOnIdle {
            assertNotNull(callbacks[ORIGINAL])
            assertNotNull(callbacks[CANDIDATE])
            assertEquals(callbacks[ORIGINAL]!!.size, callbacks[CANDIDATE]!!.size)
        }
        assertEquals(layers(ORIGINAL).size, layers(CANDIDATE).size)
        compose.runOnIdle { callback.value = false }
        assertPairGeometry()
        assertEquals(layers(ORIGINAL).size - 1, layers(CANDIDATE).size)
    }

    @Composable
    private fun TextPair(
        case: TextCase,
        onClick: (String) -> Unit = {},
        onLongClick: (String) -> Unit = {},
        onTextLayout: ((String, TextLayoutResult) -> Unit)? = null,
    ) {
        MaterialTheme {
            CompositionLocalProvider(
                LocalDensity provides case.density,
                LocalLayoutDirection provides case.direction,
                LocalContentColor provides case.contentColor,
                LocalTextStyle provides case.style,
            ) {
                Column(Modifier.padding(24.dp)) {
                    listOf(ORIGINAL, CANDIDATE).forEach { tag ->
                        Box(Modifier.width(case.width).height(160.dp).padding(bottom = 16.dp)) {
                            val modifier = Modifier
                                .testTag(tag)
                                .clip(RoundedCornerShape(4.dp))
                                .combinedClickable(onClick = { onClick(tag) }, onLongClick = { onLongClick(tag) })
                                .padding(2.dp)
                                .onGloballyPositioned {
                                    baselines[tag] = it[FirstBaseline] to it[LastBaseline]
                                }
                            val callback: ((TextLayoutResult) -> Unit)? = onTextLayout?.let { listener ->
                                { result -> listener(tag, result) }
                            }
                            if (tag == ORIGINAL) {
                                Text(
                                    text = case.text,
                                    color = case.color,
                                    modifier = modifier,
                                    maxLines = case.maxLines,
                                    minLines = case.minLines,
                                    softWrap = case.softWrap,
                                    overflow = case.overflow,
                                    onTextLayout = callback,
                                )
                            } else {
                                AlbumInlineText(
                                    text = case.text,
                                    color = case.color,
                                    modifier = modifier,
                                    maxLines = case.maxLines,
                                    minLines = case.minLines,
                                    softWrap = case.softWrap,
                                    overflow = case.overflow,
                                    onTextLayout = callback,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            assertTrue(it(results))
        }
        return results.single()
    }

    private fun assertPairGeometry() {
        val expected = textLayout(ORIGINAL)
        val actual = textLayout(CANDIDATE)
        val expectedInput = expected.layoutInput
        val actualInput = actual.layoutInput
        assertEquals(expectedInput.text, actualInput.text)
        assertEquals(expectedInput.style, actualInput.style)
        assertEquals(expectedInput.placeholders, actualInput.placeholders)
        assertEquals(expectedInput.maxLines, actualInput.maxLines)
        assertEquals(expectedInput.softWrap, actualInput.softWrap)
        assertEquals(expectedInput.overflow, actualInput.overflow)
        assertEquals(expectedInput.constraints, actualInput.constraints)
        assertEquals(expectedInput.layoutDirection, actualInput.layoutDirection)
        assertTrue(expectedInput.fontFamilyResolver === actualInput.fontFamilyResolver)
        assertEquals(expectedInput.density.density, actualInput.density.density, 0f)
        assertEquals(expectedInput.density.fontScale, actualInput.density.fontScale, 0f)
        assertEquals(expected.size, actual.size)
        assertEquals(expected.firstBaseline, actual.firstBaseline, 0f)
        assertEquals(expected.lastBaseline, actual.lastBaseline, 0f)
        assertEquals(expected.lineCount, actual.lineCount)
        repeat(expected.lineCount) { line ->
            assertEquals(expected.getLineLeft(line), actual.getLineLeft(line), 0f)
            assertEquals(expected.getLineRight(line), actual.getLineRight(line), 0f)
            assertEquals(expected.isLineEllipsized(line), actual.isLineEllipsized(line))
        }
        val original = compose.onNodeWithTag(ORIGINAL).fetchSemanticsNode()
        val candidate = compose.onNodeWithTag(CANDIDATE).fetchSemanticsNode()
        assertEquals(original.size, candidate.size)
        compose.runOnIdle { assertEquals(baselines[ORIGINAL], baselines[CANDIDATE]) }
    }

    private fun layers(tag: String): List<OwnedLayer> {
        val layers = mutableListOf<OwnedLayer>()
        compose.onNodeWithTag(tag).fetchSemanticsNode().layoutInfo.getModifierInfo().forEach { info ->
            val layer = info.extra as? OwnedLayer
            if (layer !== null && layers.none { it === layer }) layers.add(layer)
        }
        return layers
    }

    private data class TextCase(
        val text: String = "CV 标签 mixed text",
        val style: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        val color: Color = Color.Unspecified,
        val contentColor: Color = Color.Magenta,
        val density: Density = Density(1f),
        val direction: LayoutDirection = LayoutDirection.Ltr,
        val width: Dp = 180.dp,
        val maxLines: Int = 1,
        val minLines: Int = 1,
        val softWrap: Boolean = true,
        val overflow: TextOverflow = TextOverflow.Ellipsis,
    )

    private companion object {
        const val ORIGINAL = "material_text"
        const val CANDIDATE = "album_inline_text"
    }
}
