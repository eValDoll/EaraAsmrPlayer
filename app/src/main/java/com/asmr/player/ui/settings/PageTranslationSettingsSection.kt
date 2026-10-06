package com.asmr.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.asmr.player.translation.PageTranslationLanguages
import com.asmr.player.translation.PageTranslationSettings
import com.asmr.player.translation.rememberPageTranslationServices
import com.asmr.player.ui.common.ThemedDropdownMenuPositionProvider
import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

@Composable
internal fun PageTranslationSettingsSection(isActive: Boolean = true) {
    val services = rememberPageTranslationServices()
    val preferences = remember(services) { services.preferences() }
    val messages = remember(services) { services.messages() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val settings by produceState(PageTranslationSettings(), preferences, isActive, lifecycle) {
        if (isActive) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            preferences.settings.collect { value = it }
        }
    }
    val scope = rememberCoroutineScope()
    val colors = AsmrTheme.colorScheme
    var clearing by remember { mutableStateOf(false) }
    fun save(action: suspend () -> Unit) {
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { messages.showError("保存失败，请稍后重试") }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("页面翻译", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            IconButton(
                enabled = !clearing,
                onClick = {
                    clearing = true
                    scope.launch {
                        try { services.translations().clearCache(); messages.showSuccess("页面翻译缓存已清空") }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { messages.showError("清空失败，请稍后重试") }
                        finally { clearing = false }
                    }
                },
                modifier = Modifier.size(40.dp).semantics {
                    contentDescription = "清空页面翻译缓存"
                    if (clearing) stateDescription = "正在清空"
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent, contentColor = colors.primary,
                    disabledContainerColor = Color.Transparent, disabledContentColor = colors.textSecondary,
                ),
            ) {
                if (clearing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.primary,
                        trackColor = Color.Transparent, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
        }
        PageTranslationLanguageSelector("目标语言", settings.target, { value -> save { preferences.setTarget(value) } }, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("自动翻译页面", modifier = Modifier.weight(1f), color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
            Switch(checked = settings.automatic, onCheckedChange = { value -> save { preferences.setAutomatic(value) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.surface, checkedTrackColor = colors.primary,
                    checkedBorderColor = colors.primary, uncheckedThumbColor = colors.onSurfaceVariant,
                    uncheckedTrackColor = colors.surfaceVariant, uncheckedBorderColor = colors.onSurfaceVariant,
                ))
        }
    }
}

@Composable
internal fun PageTranslationLanguageSelector(
    label: String,
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDropdownSelector(label, value, PageTranslationLanguages, onSelect, modifier, tagPrefix = "page_translation")
}

@Composable
internal fun SettingsDropdownSelector(
    label: String,
    value: String,
    options: Map<String, String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tagPrefix: String = "settings_dropdown",
    showLabel: Boolean = true,
    flatTrigger: Boolean = false,
    lazyOptions: Boolean = false,
) {
    val colors = AsmrTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val shape = RoundedCornerShape(10.dp)
    val fieldColor = lerp(colors.surface, colors.primarySoft, if (colors.isDark) 0.18f else 0.24f)
    val menuColor = lerp(colors.surface, colors.primarySoft, if (colors.isDark) 0.24f else 0.46f)
    val selectedColor = lerp(colors.surface, colors.primarySoft, if (colors.isDark) 0.52f else 0.88f)
    val menuPosition = remember(density) {
        ThemedDropdownMenuPositionProvider(with(density) { 4.dp.roundToPx() }, with(density) { 8.dp.roundToPx() })
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showLabel) Text(label, color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth().onSizeChanged { anchorWidth = it.width }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (flatTrigger) 36.dp else 48.dp)
                    .clip(shape)
                    .then(if (flatTrigger) Modifier else Modifier
                        .background(fieldColor)
                        .border(1.dp, colors.primaryStrong.copy(alpha = if (expanded) 0.7f else 0.22f), shape))
                    .clickable(enabled = enabled, role = Role.DropdownList) { expanded = !expanded }
                    .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                    .testTag("${tagPrefix}_$label")
                    .padding(horizontal = if (flatTrigger) 8.dp else 12.dp, vertical = if (flatTrigger) 6.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(options.getValue(value), modifier = Modifier.weight(1f),
                    style = if (flatTrigger) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = if (enabled) colors.textPrimary else colors.textTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(if (expanded) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                    contentDescription = null, tint = if (enabled) colors.primaryStrong else colors.textTertiary, modifier = Modifier.size(20.dp))
            }
            if (expanded && enabled && anchorWidth > 0) {
                Popup(
                    popupPositionProvider = menuPosition,
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    val menuModifier = Modifier
                        .width(with(density) { anchorWidth.toDp() })
                        .heightIn(max = 320.dp)
                        .clip(shape)
                        .background(menuColor)
                        .border(1.dp, colors.primaryStrong.copy(alpha = 0.24f), shape)
                        .testTag("${tagPrefix}_menu_$label")
                        .selectableGroup()
                    val optionContent: @Composable (String, String) -> Unit = { code, name ->
                        val selected = code == value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) selectedColor else Color.Transparent)
                                .selectable(selected = selected, role = Role.RadioButton) {
                                    expanded = false
                                    onSelect(code)
                                }
                                .testTag("${tagPrefix}_option_${label}_$code")
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(name, modifier = Modifier.weight(1f),
                                style = if (flatTrigger) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                color = if (selected) colors.primaryStrong else colors.textPrimary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            if (selected) Icon(Icons.Rounded.Check, contentDescription = null,
                                tint = colors.primaryStrong, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (lazyOptions) {
                        val entries = remember(options) { options.entries.toList() }
                        LazyColumn(modifier = menuModifier, contentPadding = PaddingValues(4.dp)) {
                            items(entries, key = { it.key }) { optionContent(it.key, it.value) }
                        }
                    } else {
                        Column(menuModifier.verticalScroll(rememberScrollState()).padding(4.dp)) {
                            options.forEach { (code, name) -> optionContent(code, name) }
                        }
                    }
                }
            }
        }
    }
}
