package com.asmr.player.ui.settings

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.settings.DeepSeekTranslationSettings
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.ThemeMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w1280dp-h800dp")
@OptIn(ExperimentalMaterial3Api::class)
class DeepSeekTranslationSettingsSectionTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
        }
    }).around(compose)

    @Test
    fun tabletPlaceholder_fitsInsideInputBounds() {
        compose.setContent {
            AsmrPlayerTheme(mode = ThemeMode.Light) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeepSeekTranslationSettingsSection(
                        state = DeepSeekApiKeyUiState(), settings = DeepSeekTranslationSettings(), apiKeyInput = "",
                        compact = false, segmentedButtonColors = SegmentedButtonDefaults.colors(),
                        onApiKeyInputChanged = {}, onSave = {}, onThinkingEnabledChanged = {},
                        onReasoningEffortChanged = {}, onFinalPolishEnabledChanged = {}
                    )
                }
            }
        }
        val field = compose.onNodeWithTag("deepseek_api_key_input").getUnclippedBoundsInRoot()
        val placeholder = compose.onNodeWithText("API Key（sk-…）", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(field.bottom - field.top >= 48.dp)
        assertTrue(placeholder.top >= field.top)
        assertTrue(placeholder.bottom <= field.bottom)
    }

    @Test
    fun fetchedDeepSeekModels_canBeSelectedAndSavedModelRemainsSelected() {
        val model = mutableStateOf("deepseek-flash")
        val keyState = mutableStateOf(DeepSeekApiKeyUiState())
        val settingsLoading = mutableStateOf(true)
        var modelRequests = 0
        compose.setContent {
            AsmrPlayerTheme(mode = ThemeMode.Dark) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeepSeekTranslationSettingsSection(
                        state = keyState.value, settings = DeepSeekTranslationSettings(), apiKeyInput = "",
                        compact = false, segmentedButtonColors = SegmentedButtonDefaults.colors(),
                        onApiKeyInputChanged = {}, onSave = {}, onThinkingEnabledChanged = {},
                        onReasoningEffortChanged = {}, onFinalPolishEnabledChanged = {},
                        model = model.value, onModelSelected = { model.value = it },
                        modelSaving = settingsLoading.value,
                        onLoadModels = { modelRequests++; listOf("deepseek-flash", "deepseek-v4-pro") }
                    )
                }
            }
        }
        compose.runOnIdle {
            assertEquals(0, modelRequests)
            keyState.value = DeepSeekApiKeyUiState(configured = true)
            settingsLoading.value = false
        }
        compose.onNodeWithTag("deepseek_model_模型").performClick()
        compose.onNodeWithTag("deepseek_model_option_模型_deepseek-flash").assertIsSelected()
        compose.onNodeWithTag("deepseek_model_option_模型_deepseek-v4-pro").performClick()
        assertEquals("deepseek-v4-pro", model.value)
        compose.onNodeWithTag("deepseek_model_模型").assertTextContains("deepseek-v4-pro")
        compose.onNodeWithText("手动输入").assertDoesNotExist()
        compose.runOnIdle {
            settingsLoading.value = true
        }
        compose.runOnIdle {
            settingsLoading.value = false
        }
        compose.runOnIdle { assertEquals(1, modelRequests) }
    }

}
