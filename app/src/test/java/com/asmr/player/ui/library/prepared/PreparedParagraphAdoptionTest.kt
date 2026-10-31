@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.asmr.player.ui.library.prepared

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.asmr.player.ui.createEaraComposeRule
import com.asmr.player.ui.library.AlbumInlineText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33], qualifiers = "w500dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreparedParagraphAdoptionTest {
    @get:Rule val compose = createEaraComposeRule()

    @Test
    fun actualAdoption_keepsCurrentUiStyleGeometryAndTextSubstitution() {
        val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val executor = QueuedParagraphExecutor()
        val session = PreparedParagraphSession(resolver, executor)
        val initial = paragraphRequest(resolver)
        val text = mutableStateOf(initial.text)
        val color = mutableStateOf(Color.Blue)
        val visible = mutableStateOf(true)
        session.setAheadWindow(listOf(initial))
        executor.runNext()
        assertEquals(1, session.stats().ready)
        try {
            compose.setContent {
                CompositionLocalProvider(
                    LocalPreparedParagraphSession provides session,
                    LocalFontFamilyResolver provides resolver,
                    LocalDensity provides Density(1f, 1f),
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    Column {
                        UnconstrainedText {
                            Text(
                                text.value,
                                modifier = Modifier.testTag("reference"),
                                style = initial.layoutStyle.value,
                                color = color.value,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (visible.value) UnconstrainedText {
                            AlbumInlineText(
                                text.value,
                                preparedOccurrence = initial.occurrence,
                                modifier = Modifier.testTag("prepared"),
                                style = initial.layoutStyle.value,
                                color = color.value,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            compose.waitForIdle()
            assertEquals(1L, session.stats().adopted)
            assertEquals(0L, session.stats().staleAdoptions)
            assertPairGeometryAndStyle()

            compose.runOnIdle { color.value = Color.Green }
            assertPairGeometryAndStyle()
            assertEquals(1L, session.stats().adopted)

            compose.onNodeWithTag("prepared").performSemanticsAction(SemanticsActions.SetTextSubstitution) {
                assertTrue(it(AnnotatedString("系统替换文本")))
            }
            compose.onNodeWithTag("prepared").performSemanticsAction(SemanticsActions.ShowTextSubstitution) {
                assertTrue(it(true))
            }
            compose.waitForIdle()
            compose.onNodeWithTag("prepared").performSemanticsAction(SemanticsActions.ClearTextSubstitution) {
                assertTrue(it())
            }
            assertPairGeometryAndStyle()

            val replacement = initial.copy(text = "更新后的标签 日本語")
            session.setAheadWindow(listOf(replacement))
            executor.runNext()
            compose.runOnIdle { text.value = replacement.text }
            assertPairGeometryAndStyle()
            assertEquals(2L, session.stats().adopted)
            assertEquals(0L, session.stats().staleAdoptions)
            assertEquals(1, session.stats().leased)

            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            assertEquals("Detached text must release its occurrence", 0, session.stats().leased)
            executor.runNext()
            compose.runOnIdle { visible.value = true }
            assertPairGeometryAndStyle()
            assertEquals(1, session.stats().leased)
            assertEquals(3L, session.stats().adopted)
        } finally {
            session.close()
        }
    }

    private fun assertPairGeometryAndStyle() {
        val reference = textLayout("reference")
        val prepared = textLayout("prepared")
        assertEquals(reference.layoutInput.text, prepared.layoutInput.text)
        assertEquals(reference.layoutInput.style, prepared.layoutInput.style)
        assertEquals(reference.layoutInput.constraints, prepared.layoutInput.constraints)
        assertEquals(reference.size, prepared.size)
        assertEquals(reference.firstBaseline, prepared.firstBaseline, 0f)
        assertEquals(reference.lastBaseline, prepared.lastBaseline, 0f)
        assertEquals(reference.lineCount, prepared.lineCount)
        repeat(reference.lineCount) {
            assertEquals(reference.getLineLeft(it), prepared.getLineLeft(it), 0f)
            assertEquals(reference.getLineRight(it), prepared.getLineRight(it), 0f)
            assertEquals(reference.isLineEllipsized(it), prepared.isLineEllipsized(it))
        }
    }

    private fun textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            assertTrue(it(results))
        }
        return results.single()
    }
}

@Composable
private fun UnconstrainedText(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, _ ->
        val child = measurables.single().measure(Constraints())
        layout(child.width, child.height) { child.place(0, 0) }
    }
}
