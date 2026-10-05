package com.asmr.player.ui.settings

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.subtitle.TranslationApiSettings
import com.asmr.player.subtitle.TranslationProvider
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
@Config(application = Application::class, sdk = [28])
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
        var saved: List<String>? = null
        compose.setContent {
            AsmrPlayerTheme(mode = ThemeMode.Light) {
                TranslationApiSettingsSection(
                    TranslationApiUiState(loaded = true),
                    onProviderSelected = { selected = it },
                    onSaveCustom = { url, model, key -> saved = listOf(url, model, key) },
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
        compose.onNodeWithTag("custom_api_save").assertIsNotEnabled()
        compose.onNodeWithTag("custom_api_url").performTextInput("https://example.com/v1")
        compose.onNodeWithTag("custom_api_model").performTextInput("vendor/model")
        compose.onNodeWithTag("custom_api_save").assertIsNotEnabled()
        compose.onNodeWithTag("custom_api_key").performTextInput("custom-key")
        compose.onNodeWithTag("custom_api_save").assertIsEnabled().performClick()
        assertEquals(listOf("https://example.com/v1", "vendor/model", "custom-key"), saved)
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
                TranslationApiSettingsSection(state.value, { selected = it }, { _, _, _ -> }, { Text("DeepSeek 配置") }, {})
            }
        }
        compose.onNodeWithTag("custom_api_save").assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(saving = true) }
        compose.onNodeWithTag("custom_api_save").assertIsNotEnabled()
        compose.onNodeWithTag("custom_api_url").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(saving = false) }
        compose.onNodeWithTag("translation_api_翻译服务").performClick()
        compose.onNodeWithTag("translation_api_option_翻译服务_DEEPSEEK").performClick()
        assertEquals(TranslationProvider.DEEPSEEK, selected)
        compose.onNodeWithText("DeepSeek 配置").assertExists()
    }
}
