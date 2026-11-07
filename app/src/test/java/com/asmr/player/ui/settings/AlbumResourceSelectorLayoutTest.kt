package com.asmr.player.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.data.remote.crawler.AlbumResourceSource
import com.asmr.player.ui.createEaraComposeRule
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h780dp-port-mdpi")
class AlbumResourceSelectorLayoutTest {
    @get:Rule val compose = createEaraComposeRule()

    @Test
    fun selectedSourceStaysBesideArrowWhenSwitchingToShorterName() {
        compose.setContent {
            AsmrPlayerTheme {
                var selected by remember { mutableStateOf(AlbumResourceSource.JapaneseAsmr.name) }
                SettingsDropdownSelector(
                    label = "资源站点", value = selected,
                    options = AlbumResourceSource.entries.associate { it.name to it.label },
                    onSelect = { selected = it }, modifier = Modifier.widthIn(min = 154.dp),
                    showLabel = false, flatTrigger = true, singleLineOptions = true,
                    valueTextAlign = TextAlign.End, tagPrefix = "album_resource_source"
                )
            }
        }
        val trigger = compose.onNodeWithTag("album_resource_source_资源站点")
        trigger.performClick()
        compose.onNodeWithText("asmr.one").performClick()
        val text = compose.onNodeWithText("asmr.one", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse(layout.hasVisualOverflow)
        val glyphRight = text.fetchSemanticsNode().boundsInRoot.left + layout.getLineRight(0)
        assertEquals("选中文字与箭头间只保留规定间距", 36f, trigger.fetchSemanticsNode().boundsInRoot.right - glyphRight, 1f)
    }
}
