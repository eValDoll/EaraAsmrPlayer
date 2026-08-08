package com.asmr.player.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.data.settings.DeepSeekReasoningEffort
import com.asmr.player.data.settings.DeepSeekTranslationSettings
import com.asmr.player.subtitle.DeepSeekAccountState
import com.asmr.player.subtitle.DeepSeekBalance
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterial3Api::class)
class DeepSeekTranslationSettingsSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unconfiguredKey_showsSaveWithoutCheckAndUsesCompactHeight() {
        composeRule.setContent {
            AsmrPlayerTheme {
                Column {
                    DeepSeekTranslationSettingsSection(
                        state = DeepSeekApiKeyUiState(configured = false),
                        settings = DeepSeekTranslationSettings(),
                        apiKeyInput = "sk-test",
                        compact = true,
                        segmentedButtonColors = SegmentedButtonDefaults.colors(),
                        onApiKeyInputChanged = {},
                        onSave = {},
                        onThinkingEnabledChanged = {},
                        onReasoningEffortChanged = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("保存").assertExists()
        composeRule.onNodeWithContentDescription("API Key 已配置").assertDoesNotExist()
        val bounds = composeRule.onNodeWithTag("deepseek_api_key_input")
            .getUnclippedBoundsInRoot()
        val actualHeight = bounds.bottom - bounds.top
        assertEquals(48f, actualHeight.value, 0.5f)
    }

    @Test
    fun configuredKey_showsReplaceAndDisablesEffortWhenThinkingIsOff() {
        composeRule.setContent {
            AsmrPlayerTheme {
                Column {
                    DeepSeekTranslationSettingsSection(
                        state = DeepSeekApiKeyUiState(configured = true),
                        accountState = DeepSeekAccountState(
                            totalTokens = 12_345L,
                            balances = listOf(DeepSeekBalance("CNY", "8.50")),
                            balanceAvailable = true
                        ),
                        settings = DeepSeekTranslationSettings(
                            thinkingEnabled = false,
                            reasoningEffort = DeepSeekReasoningEffort.MAX
                        ),
                        apiKeyInput = "",
                        compact = true,
                        segmentedButtonColors = SegmentedButtonDefaults.colors(),
                        onApiKeyInputChanged = {},
                        onSave = {},
                        onThinkingEnabledChanged = {},
                        onReasoningEffortChanged = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("替换").assertExists()
        composeRule.onNodeWithText("Token 12.3K · 余额 ¥8.5").assertExists()
        composeRule.onNodeWithContentDescription("API Key 已配置").assertExists()
        composeRule.onNodeWithTag("deepseek_reasoning_high").assertIsNotEnabled()
        composeRule.onNodeWithTag("deepseek_reasoning_max").assertIsNotEnabled()
    }
}
