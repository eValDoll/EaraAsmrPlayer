package com.asmr.player.ui.settings

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.subtitle.TranslationApiSettings
import com.asmr.player.subtitle.TranslationProvider
import com.asmr.player.subtitle.CustomThinkingMode
import com.asmr.player.subtitle.CustomReasoningEffort
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w411dp-h891dp")
class TranslationApiSettingsSectionTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
        }
    }).around(compose)

    @Test
    fun newCustomProfile_requiresFieldsAndDoesNotActivateUntilSave() {
        var selected: TranslationProvider? = null
        var saved: List<Any>? = null
        compose.setContent {
            AsmrPlayerTheme(mode = ThemeMode.Light) {
                TranslationApiSettingsSection(
                    TranslationApiUiState(loaded = true),
                    onProviderSelected = { selected = it },
                    onSaveCustom = { url, model, key, limit, mode, effort -> saved = listOf(url, model, key, limit, mode, effort) },
                    deepSeekContent = { Text("DeepSeek 配置") }, customOptions = {}
                )
            }
        }
        compose.onNodeWithText("DeepSeek 配置").assertExists()
        compose.onNodeWithTag("translation_api_翻译服务").performClick()
        compose.onNodeWithTag("translation_api_option_翻译服务_DEEPSEEK").assertIsSelected()
        compose.onNodeWithTag("translation_api_option_翻译服务_CUSTOM").performClick()
        assertNull(selected)
        compose.onNodeWithText("DeepSeek 配置").assertDoesNotExist()
        compose.onNodeWithTag("custom_api_max_output_tokens").assertTextContains("32768")
        compose.onNodeWithTag("custom_api_save").assertIsNotEnabled()
        compose.onNodeWithTag("custom_api_url").performTextInput("https://example.com/v1")
        compose.onNodeWithTag("custom_api_model").performTextInput("vendor/model")
        compose.onNodeWithTag("custom_api_save").assertIsNotEnabled()
        compose.onNodeWithTag("custom_api_key").performTextInput("custom-key")
        compose.onNodeWithTag("custom_api_save").assertIsEnabled().performClick()
        assertEquals(listOf("https://example.com/v1", "vendor/model", "custom-key", 32_768,
            CustomThinkingMode.FOLLOW_SERVER, CustomReasoningEffort.HIGH), saved)
    }

    @Test
    fun savedCustomProfile_canReuseKeyAndSwitchBackToDeepSeek() {
        var selected: TranslationProvider? = null
        val state = mutableStateOf(TranslationApiUiState(
            settings = TranslationApiSettings(TranslationProvider.CUSTOM, "https://example.com/v1", "custom-model", true),
            loaded = true
        ))
        compose.setContent {
            AsmrPlayerTheme(mode = ThemeMode.Dark) {
                TranslationApiSettingsSection(state.value, { selected = it }, { _, _, _, _, _, _ -> }, { Text("DeepSeek 配置") }, {})
            }
        }
        compose.onNodeWithTag("custom_api_save").assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(saving = true) }
        compose.onNodeWithTag("custom_api_save").assertIsNotEnabled()
        compose.onNodeWithTag("custom_api_url").assertIsNotEnabled()
        compose.onNodeWithTag("custom_api_max_output_tokens").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(saving = false) }
        compose.onNodeWithTag("translation_api_翻译服务").performClick()
        compose.onNodeWithTag("translation_api_option_翻译服务_DEEPSEEK").performClick()
        assertEquals(TranslationProvider.DEEPSEEK, selected)
        compose.onNodeWithText("DeepSeek 配置").assertExists()
    }

    @Test
    fun outputLimit_requiresPositiveIntegerAndSavesTheEditedValue() {
        var savedLimit: Int? = null
        val state = TranslationApiUiState(
            settings = TranslationApiSettings(TranslationProvider.CUSTOM, "https://example.com/v1", "model", true, 65_536),
            loaded = true
        )
        compose.setContent {
            AsmrPlayerTheme(mode = ThemeMode.Dark) {
                TranslationApiSettingsSection(state, {}, { _, _, _, limit, _, _ -> savedLimit = limit }, {}, {})
            }
        }
        val input = compose.onNodeWithTag("custom_api_max_output_tokens")
        input.assertTextContains("65536")
        listOf("", "0", "-1", "2147483648", "abc").forEach { value ->
            input.performTextReplacement(value)
            compose.onNodeWithTag("custom_api_save").assertIsNotEnabled()
        }
        input.performTextReplacement("100000")
        compose.onNodeWithTag("custom_api_save").assertIsEnabled().performClick()
        assertEquals(100_000, savedLimit)
    }

    @Test
    fun thinkingModeAndEffort_saveExplicitChoicesAndKeepEffortWhenDisabled() {
        var saved: Pair<CustomThinkingMode, CustomReasoningEffort>? = null
        val state = TranslationApiUiState(
            settings = TranslationApiSettings(TranslationProvider.CUSTOM, "https://example.com/v1", "model", true),
            loaded = true
        )
        compose.setContent {
            AsmrPlayerTheme(mode = ThemeMode.Light) {
                TranslationApiSettingsSection(state, {}, { _, _, _, _, mode, effort -> saved = mode to effort }, {}, {})
            }
        }
        compose.onNodeWithTag("custom_api_effort_思考强度").assertIsNotEnabled()
        compose.onNodeWithTag("custom_api_thinking_思考模式").performClick()
        compose.onNodeWithTag("custom_api_thinking_option_思考模式_ENABLED").performClick()
        compose.onNodeWithTag("custom_api_effort_思考强度").assertIsEnabled().performClick()
        compose.onNodeWithTag("custom_api_effort_option_思考强度_XHIGH").performClick()
        compose.onNodeWithTag("custom_api_save").performClick()
        assertEquals(CustomThinkingMode.ENABLED to CustomReasoningEffort.XHIGH, saved)
        compose.onNodeWithTag("custom_api_thinking_思考模式").performClick()
        compose.onNodeWithTag("custom_api_thinking_option_思考模式_DISABLED").performClick()
        compose.onNodeWithTag("custom_api_effort_思考强度").assertIsNotEnabled()
        compose.onNodeWithTag("custom_api_save").performClick()
        assertEquals(CustomThinkingMode.DISABLED to CustomReasoningEffort.XHIGH, saved)
    }
}
