package com.asmr.player.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.util.SubtitleEntry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class NowPlayingMultilineLyricsTest {
    @get:Rule val compose = createComposeRule()

    private val firstCue = SubtitleEntry(0, 1000, "短句")
    private val longCue = SubtitleEntry(1000, 2000, "这是一条需要完整阅读的长字幕，换行后仍应保留所有文字。".repeat(30))
    private val current = mutableStateOf(firstCue)

    @Test
    fun longCueWrapsWithoutEllipsisAndDoesNotMoveSiblingControls() {
        setContent()
        val controls = compose.onNodeWithTag("controls").getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("multiline_lyrics").getUnclippedBoundsInRoot()
        compose.runOnIdle { current.value = longCue }
        compose.waitForIdle()
        assertEquals(controls, compose.onNodeWithTag("controls").getUnclippedBoundsInRoot())
        assertEquals(viewport, compose.onNodeWithTag("multiline_lyrics").getUnclippedBoundsInRoot())
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("multiline_lyrics_text", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().lineCount > 3)
        assertFalse(layouts.single().isLineEllipsized(layouts.single().lineCount - 1))
        assertEquals(longCue.text.length, layouts.single().getLineEnd(layouts.single().lineCount - 1))
    }

    @Test
    fun compactViewportAndLargeSystemFontKeepLongTextScrollable() {
        current.value = longCue
        setContent(compact = true, fontScale = 1.6f)
        compose.onNodeWithTag("multiline_lyrics_text", useUnmergedTree = true).assertTextEquals(longCue.text)
        val scroll = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        scroll.performTouchInput { swipeUp() }
        assertTrue(scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
    }

    @Test
    fun nextCueWithIdenticalTextResetsScrollToStart() {
        current.value = longCue
        setContent()
        val scroll = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        scroll.performTouchInput { swipeUp() }
        assertTrue(scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        compose.runOnIdle { current.value = longCue.copy(startMs = 2000, endMs = 3000) }
        compose.waitForIdle()
        val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(0f, range.value(), 0.1f)
    }

    private fun setContent(compact: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                AsmrPlayerTheme {
                    Column(Modifier.width(320.dp).height(if (compact) 180.dp else 320.dp)) {
                        Text("封面", Modifier.height(48.dp))
                        NowPlayingMultilineLyrics(
                            text = current.value.text,
                            cueKey = current.value,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp, lineHeight = 31.sp),
                            colors = rememberLyricReadableColors(MaterialTheme.colorScheme.primary),
                            centered = true,
                            interactionEnabled = true,
                            onOpenLyrics = {},
                            modifier = Modifier.weight(1f)
                        )
                        Text("播放控件", Modifier.height(56.dp).testTag("controls"))
                    }
                }
            }
        }
    }
}
