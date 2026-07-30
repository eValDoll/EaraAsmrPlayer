package com.asmr.player.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class AlbumMetaChipsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun headerLabels_doNotEllipsizeWithLargeFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                AsmrPlayerTheme {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AlbumHeaderCvFlow(cvText = "声优甲")
                        AlbumHeaderTagsFlow(tags = listOf("治愈"))
                    }
                }
            }
        }

        assertTextDoesNotOverflow("声优")
        assertTextDoesNotOverflow("标签")
    }

    private fun assertTextDoesNotOverflow(text: String) {
        val textLayoutResults = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(textLayoutResults)
        }
        assertFalse(textLayoutResults.single().hasVisualOverflow)
    }
}
