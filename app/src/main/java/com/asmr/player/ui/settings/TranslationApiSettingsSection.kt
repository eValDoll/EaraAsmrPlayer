package com.asmr.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.asmr.player.subtitle.CustomReasoningEffort
import com.asmr.player.subtitle.CustomThinkingMode
import com.asmr.player.subtitle.TranslationProvider
import com.asmr.player.subtitle.formatDeepSeekTokenTotal
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.launch

@Composable
internal fun TranslationApiSettingsSection(
    state: TranslationApiUiState,
    onProviderSelected: (TranslationProvider) -> Unit,
    onSaveCustom: (String, String, String, Int, CustomThinkingMode, CustomReasoningEffort) -> Unit,
    deepSeekContent: @Composable () -> Unit,
    customOptions: @Composable () -> Unit,
    onLoadModels: suspend (String, String) -> List<String>? = { _, _ -> null },
    customTotalTokens: Long = 0L,
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
            label = "翻译服务", value = provider.name, options = options,
            onSelect = {
                provider = TranslationProvider.valueOf(it)
                apiKey = ""
                if (provider == TranslationProvider.DEEPSEEK || state.settings.customKeyConfigured) {
                    onProviderSelected(provider)
                }
            },
            modifier = Modifier.fillMaxWidth(), enabled = enabled, tagPrefix = "translation_api"
        )
        if (provider == TranslationProvider.DEEPSEEK) {
            deepSeekContent()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("API 配置", modifier = Modifier.weight(1f), color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (state.settings.customKeyConfigured) {
                    Text("Token ${formatDeepSeekTokenTotal(customTotalTokens)}", color = colors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.testTag("custom_api_token_total"))
                }
            }
            TranslationSettingsTextField(
                label = "API 地址", value = baseUrl, onValueChange = { baseUrl = it },
                placeholder = "https://api.example.com/v1", enabled = enabled,
                keyboardType = KeyboardType.Uri, tag = "custom_api_url"
            )
            TranslationSettingsTextField(
                label = "API Key", value = apiKey, onValueChange = { apiKey = it },
                placeholder = if (state.settings.customKeyConfigured) "已配置" else "sk-…",
                enabled = enabled, keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation(), tag = "custom_api_key"
            )
            key(baseUrl, apiKey) {
                TranslationSettingsModelField(
                    model = model, onModelChanged = { model = it }, enabled = enabled,
                    canLoad = baseUrl.isNotBlank() && (apiKey.isNotBlank() || state.settings.customKeyConfigured),
                    loadInitially = state.settings.customKeyConfigured && apiKey.isBlank() &&
                        baseUrl == state.settings.customBaseUrl,
                    onLoad = { onLoadModels(baseUrl, apiKey) }
                )
            }
            HorizontalDivider(color = colors.primaryStrong.copy(alpha = 0.12f))
            Text("生成设置", color = colors.textPrimary, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            TranslationSettingsTextField(
                label = "最大输出 Token", value = maxOutputTokens, onValueChange = { maxOutputTokens = it },
                enabled = enabled, keyboardType = KeyboardType.Number,
                isError = parsedMaxOutputTokens == null, tag = "custom_api_max_output_tokens",
                errorMessage = "请输入正整数"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsDropdownSelector(
                    label = "思考模式", value = thinkingMode.name, options = thinkingOptions,
                    onSelect = { thinkingMode = CustomThinkingMode.valueOf(it) },
                    modifier = Modifier.weight(1f), enabled = enabled, tagPrefix = "custom_api_thinking"
                )
                SettingsDropdownSelector(
                    label = "思考强度", value = reasoningEffort.name, options = effortOptions,
                    onSelect = { reasoningEffort = CustomReasoningEffort.valueOf(it) },
                    modifier = Modifier.weight(1f), enabled = enabled && thinkingMode == CustomThinkingMode.ENABLED,
                    tagPrefix = "custom_api_effort"
                )
            }
            customOptions()
            FilledTonalButton(
                onClick = {
                    parsedMaxOutputTokens?.let { onSaveCustom(baseUrl, model, apiKey, it, thinkingMode, reasoningEffort) }
                },
                enabled = enabled && baseUrl.isNotBlank() && model.isNotBlank() &&
                    (apiKey.isNotBlank() || state.settings.customKeyConfigured) && parsedMaxOutputTokens != null,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.primarySoft,
                    contentColor = if (colors.isDark) colors.onPrimaryContainer else colors.primaryStrong,
                    disabledContainerColor = colors.primarySoft.copy(alpha = 0.4f),
                    disabledContentColor = colors.textTertiary
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("custom_api_save")
            ) {
                if (state.saving) EaraLogoLoadingIndicator(size = 18.dp)
                else Text("保存", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
internal fun TranslationSettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    errorMessage: String = "输入无效",
    showLabel: Boolean = true,
) {
    val colors = AsmrTheme.colorScheme
    val fieldColor = lerp(colors.surface, colors.primarySoft, if (colors.isDark) 0.18f else 0.24f)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val borderColor = when {
        !enabled -> colors.primaryStrong.copy(alpha = 0.16f)
        isError -> MaterialTheme.colorScheme.error
        focused -> colors.primaryStrong
        else -> colors.primaryStrong.copy(alpha = 0.22f)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showLabel) Text(label, color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
        BasicTextField(
            value = value, onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (enabled) colors.textPrimary else colors.textTertiary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true, enabled = enabled,
            visualTransformation = visualTransformation,
            interactionSource = interactions,
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { input ->
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, color = colors.textTertiary, style = MaterialTheme.typography.bodyMedium)
                    }
                    input()
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clip(shape).background(fieldColor).border(1.dp, borderColor, shape)
                .semantics {
                    contentDescription = label
                    if (isError) error(errorMessage)
                }
                .testTag(tag)
        )
    }
}

private const val ManualModelOption = "\u0000manual"

@Composable
internal fun TranslationSettingsModelField(
    model: String,
    onModelChanged: (String) -> Unit,
    enabled: Boolean,
    canLoad: Boolean,
    loadInitially: Boolean,
    onLoad: suspend () -> List<String>?,
    tagPrefix: String = "custom_api",
    allowManual: Boolean = true,
) {
    val colors = AsmrTheme.colorScheme
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var initialLoadAttempted by remember { mutableStateOf(false) }
    val currentLoad by rememberUpdatedState(onLoad)
    val currentModel by rememberUpdatedState(model)
    val currentModelChanged by rememberUpdatedState(onModelChanged)
    suspend fun load() {
        if (loading) return
        loading = true
        try {
            currentLoad()?.takeIf { it.isNotEmpty() }?.let { result ->
                models = result
                manual = false
                if (currentModel.isBlank()) currentModelChanged(result.first())
            }
        } finally { loading = false }
    }
    LaunchedEffect(enabled, loadInitially) {
        if (enabled && loadInitially && !initialLoadAttempted) {
            initialLoadAttempted = true
            load()
        }
    }
    val options = remember(models, model, allowManual) {
        buildMap {
            if (model !in models) put(model, model.ifBlank { "选择模型" })
            models.forEach { put(it, it) }
            if (allowManual) put(ManualModelOption, "手动输入")
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("模型", color = colors.textSecondary, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f))
            IconButton(
                onClick = { scope.launch { load() } }, enabled = enabled && canLoad && !loading,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent, contentColor = colors.primaryStrong,
                    disabledContainerColor = Color.Transparent, disabledContentColor = colors.textTertiary
                ),
                modifier = Modifier.size(32.dp).testTag("${tagPrefix}_models_refresh")
            ) {
                if (loading) EaraLogoLoadingIndicator(size = 18.dp)
                else Icon(Icons.Rounded.Refresh, contentDescription = "拉取模型", modifier = Modifier.size(20.dp))
            }
        }
        if (allowManual && (models.isEmpty() || manual)) {
            TranslationSettingsTextField(
                label = "模型", value = model, onValueChange = onModelChanged,
                placeholder = "模型名称", enabled = enabled, tag = "${tagPrefix}_model", showLabel = false
            )
        } else {
            SettingsDropdownSelector(
                label = "模型", value = model, options = options,
                onSelect = { if (it == ManualModelOption) manual = true else onModelChanged(it) },
                modifier = Modifier.fillMaxWidth(), enabled = enabled,
                tagPrefix = "${tagPrefix}_model", showLabel = false,
                lazyOptions = true,
            )
        }
    }
}
