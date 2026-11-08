package com.asmr.player.ui.library

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.ui.createEaraComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = com.asmr.player.LocalizedTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlbumHeaderActionContentTest {
    @get:Rule val compose = createEaraComposeRule()

    @Test fun narrowLanguageButtonDropsIconBeforeEllipsizingAndRestoresItWhenWidened() {
        val width = mutableStateOf(60.dp)
        compose.setContent {
            AlbumHeaderActionContent(
                label = "CHS", icon = Icons.Rounded.Translate, trailingIcon = null,
                color = Color.White, textStyle = TextStyle(fontSize = 16.sp),
                modifier = Modifier.width(width.value).height(40.dp)
            )
        }
        assertFullLabel("CHS")
        compose.onNodeWithTag(ALBUM_HEADER_LEADING_ICON_TAG, useUnmergedTree = true).assertIsNotDisplayed()
        compose.runOnIdle { width.value = 100.dp }
        assertFullLabel("CHS")
        compose.onNodeWithTag(ALBUM_HEADER_LEADING_ICON_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun largerTextDropsLeadingIconWhileReservingDropdownArrow() {
        val fontSize = mutableStateOf(12.sp)
        compose.setContent {
            AlbumHeaderActionContent(
                label = "CHT", icon = Icons.Rounded.Translate, trailingIcon = Icons.Rounded.ArrowDropDown,
                color = Color.White, textStyle = TextStyle(fontSize = fontSize.value),
                modifier = Modifier.width(90.dp).height(40.dp)
            )
        }
        assertFullLabel("CHT")
        compose.onNodeWithTag(ALBUM_HEADER_LEADING_ICON_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { fontSize.value = 22.sp }
        assertFullLabel("CHT")
        compose.onNodeWithTag(ALBUM_HEADER_LEADING_ICON_TAG, useUnmergedTree = true).assertIsNotDisplayed()
    }

    private fun assertFullLabel(label: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertFalse("The label was truncated", results.single().isLineEllipsized(0))
        val result = results.single()
        // The paragraph reserves the maximum width while Text wraps its glyphs;
        // compare the actual line bounds rather than the paragraph container.
        assertEquals(label.length, result.getLineEnd(0))
        assertTrue("The label was clipped on the right", result.getLineRight(0) <= result.size.width + 0.01f)
        assertTrue("The label was clipped on the left", result.getLineLeft(0) >= -0.01f)
        assertFalse("The label was clipped vertically", result.didOverflowHeight)
    }
}
