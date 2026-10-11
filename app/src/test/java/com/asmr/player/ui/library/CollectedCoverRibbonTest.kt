package com.asmr.player.ui.library

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CollectedCoverRibbonTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun allSourceLabelsFitTheExistingRibbon() {
        val labels = listOf("收录", "asmr.one", "jp-asmr")
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            controller.get().setContent {
                AsmrPlayerTheme {
                    Column { labels.forEach { CollectedCoverRibbon(it) } }
                }
            }
            labels.forEach { label ->
                val layouts = mutableListOf<TextLayoutResult>()
                composeRule.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                    it(layouts)
                }
                assertTrue("Missing label layout: $label", layouts.isNotEmpty())
                assertFalse("Ribbon label was clipped: $label", layouts.single().hasVisualOverflow)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
