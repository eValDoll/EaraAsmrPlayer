package com.asmr.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.asmr.player.subtitle.TranslationProvider
import com.asmr.player.subtitle.CustomThinkingMode
import com.asmr.player.subtitle.CustomReasoningEffort
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.theme.AsmrTheme

@Composable
internal fun TranslationApiSettingsSection(
    state: TranslationApiUiState,
    onProviderSelected: (TranslationProvider) -> Unit,
    onSaveCustom: (String, String, String, Int, CustomThinkingMode, CustomReasoningEffort) -> Unit,
    deepSeekContent: @Composable () -> Unit,
    customOptions: @Composable () -> Unit
) {
    val colors = AsmrTheme.colorScheme
    var provider by rememberSaveable { mutableStateOf(state.settings.provider) }
    var baseUrl by rememberSaveable { mutableStateOf(state.settings.customBaseUrl) }
    var model by rememberSaveable { mutableStateOf(state.settings.customModel) }
    var maxOutputTokens by rememberSaveable { mutableStateOf(state.settings.customMaxOutputTokens.toString()) }
    var thinkingMode by rememberSaveable { mutableStateOf(state.settings.customThinkingMode) }
    var reasoningEffort by rememberSaveable { mutableStateOf(state.settings.customReasoningEffort) }
    var apiKey by remember { mutableStateOf("") }
    LaunchedEffect(state.loaded, state.saveVersion) {
        if (state.loaded) {
            provider = state.settings.provider
            baseUrl = state.settings.customBaseUrl
            model = state.settings.customModel
            maxOutputTokens = state.settings.customMaxOutputTokens.toString()
            thinkingMode = state.settings.customThinkingMode
            reasoningEffort = state.settings.customReasoningEffort
            apiKey = ""
        }
    }
    val enabled = state.loaded && !state.saving
    val parsedMaxOutputTokens = maxOutputTokens.toIntOrNull()?.takeIf { it > 0 }
    val options = remember { TranslationProvider.entries.associate { it.name to it.label } }
    val thinkingOptions = remember { CustomThinkingMode.entries.associate { it.name to it.label } }
    val effortOptions = remember { CustomReasoningEffort.entries.associate { it.name to it.wireValue } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsDropdownSelector(
            label = "翻译服务",
            value = provider.name,
            options = options,
            onSelect = {
                provider = TranslationProvider.valueOf(it)
                apiKey = ""
                if (provider == TranslationProvider.DEEPSEEK || state.settings.customKeyConfigured) {
                    onProviderSelected(provider)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            tagPrefix = "translation_api"
        )
        if (provider == TranslationProvider.DEEPSEEK) {
            deepSeekContent()
        } else {
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                disabledTextColor = colors.textTertiary,
                cursorColor = colors.primary,
                focusedBorderColor = colors.primaryStrong,
                unfocusedBorderColor = colors.primaryStrong.copy(alpha = 0.3f),
                disabledBorderColor = colors.primaryStrong.copy(alpha = 0.16f),
                focusedLabelColor = colors.primaryStrong,
                unfocusedLabelColor = colors.textSecondary,
                disabledLabelColor = colors.textTertiary,
                focusedPlaceholderColor = colors.textTertiary,
                unfocusedPlaceholderColor = colors.textTertiary,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                disabledContainerColor = colors.surface
            )
            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it },
                label = { Text("API 地址") },
                placeholder = { Text("https://api.example.com/v1") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true, enabled = enabled, colors = fieldColors,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("custom_api_url")
            )
            OutlinedTextField(
                value = model, onValueChange = { model = it },
                label = { Text("模型名称") },
                singleLine = true, enabled = enabled, colors = fieldColors,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("custom_api_model")
            )
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = { if (state.settings.customKeyConfigured) Text("已配置") },
                singleLine = true, enabled = enabled, colors = fieldColors,
                shape = RoundedCornerShape(10.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("custom_api_key")
            )
            OutlinedTextField(
                value = maxOutputTokens,
                onValueChange = { maxOutputTokens = it },
                label = { Text("最大输出 Token") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, enabled = enabled, colors = fieldColors,
                isError = parsedMaxOutputTokens == null,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("custom_api_max_output_tokens")
            )
            SettingsDropdownSelector(
                label = "思考模式",
                value = thinkingMode.name,
                options = thinkingOptions,
                onSelect = { thinkingMode = CustomThinkingMode.valueOf(it) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                tagPrefix = "custom_api_thinking"
            )
            SettingsDropdownSelector(
                label = "思考强度",
                value = reasoningEffort.name,
                options = effortOptions,
                onSelect = { reasoningEffort = CustomReasoningEffort.valueOf(it) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && thinkingMode == CustomThinkingMode.ENABLED,
                tagPrefix = "custom_api_effort"
            )
            FilledTonalButton(
                onClick = {
                    parsedMaxOutputTokens?.let { onSaveCustom(baseUrl, model, apiKey, it, thinkingMode, reasoningEffort) }
                },
                enabled = enabled && baseUrl.isNotBlank() && model.isNotBlank() &&
                    (apiKey.isNotBlank() || state.settings.customKeyConfigured) && parsedMaxOutputTokens != null,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.primarySoft,
                    contentColor = if (colors.isDark) colors.onPrimaryContainer else colors.primaryStrong,
                    disabledContainerColor = colors.primarySoft.copy(alpha = 0.4f),
                    disabledContentColor = colors.textTertiary
                ),
                modifier = Modifier.height(48.dp).testTag("custom_api_save")
            ) {
                if (state.saving) EaraLogoLoadingIndicator(size = 18.dp)
                else Text("保存", style = MaterialTheme.typography.labelLarge)
            }
            customOptions()
        }
    }
}
