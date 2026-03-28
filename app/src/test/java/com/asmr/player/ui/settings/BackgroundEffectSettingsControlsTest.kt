package com.asmr.player.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.data.settings.BackgroundEffectType
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundEffectSettingsControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun effectSelector_usesSingleRowDropdownAndUpdatesDisplayedValue() {
        composeRule.setContent {
            var enabled by mutableStateOf(false)
            var type by mutableStateOf(BackgroundEffectType.Flow)

            AsmrPlayerTheme {
                BackgroundEffectSettingsControls(
                    backgroundEffectEnabled = enabled,
                    backgroundEffectType = type,
                    onBackgroundEffectEnabledChange = { enabled = it },
                    onBackgroundEffectTypeChange = { type = it }
                )
            }
        }

        composeRule.onNodeWithTag(BACKGROUND_EFFECT_TYPE_ROW_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, BACKGROUND_EFFECT_TYPE_ROW_TAG)
        )
        composeRule.onNodeWithTag(BACKGROUND_EFFECT_VALUE_TAG).assertTextEquals("无")

        composeRule.onNodeWithTag(BACKGROUND_EFFECT_TYPE_ROW_TAG).performClick()
        composeRule.onNodeWithText("光点").performClick()
        composeRule.onNodeWithTag(BACKGROUND_EFFECT_VALUE_TAG).assertTextEquals("光点")

        composeRule.onNodeWithTag(BACKGROUND_EFFECT_TYPE_ROW_TAG).performClick()
        composeRule.onNodeWithText("无").performClick()
        composeRule.onNodeWithTag(BACKGROUND_EFFECT_VALUE_TAG).assertTextEquals("无")
    }
}
