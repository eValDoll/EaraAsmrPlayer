package com.asmr.player.ui.common

import com.asmr.player.i18n.appString
import com.asmr.player.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.asmr.player.data.settings.AsmrPreset
import com.asmr.player.data.settings.EqualizerPresets
import com.asmr.player.data.settings.EqualizerSettings
import com.asmr.player.data.settings.SceneEffectPresets
import com.asmr.player.ui.theme.AsmrTheme
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerPanel(
    settings: EqualizerSettings,
    customPresets: List<AsmrPreset>,
    onSettingsChanged: (EqualizerSettings) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (AsmrPreset) -> Unit,
    playbackSpeed: Float? = null,
    playbackPitch: Float? = null,
    onPlaybackSpeedChanged: ((Float) -> Unit)? = null,
    onPlaybackPitchChanged: ((Float) -> Unit)? = null,
    onPlaybackParametersChanged: ((Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val sliderInactiveTrackColor = colorScheme.primarySoft.copy(alpha = if (colorScheme.isDark) 0.34f else 0.42f)
    val sliderDisabledActiveTrackColor = materialColorScheme.onSurfaceVariant.copy(alpha = if (colorScheme.isDark) 0.40f else 0.34f)
    val sliderDisabledInactiveTrackColor = materialColorScheme.onSurfaceVariant.copy(alpha = if (colorScheme.isDark) 0.30f else 0.24f)
    val sliderDisabledThumbColor = materialColorScheme.onSurfaceVariant.copy(alpha = if (colorScheme.isDark) 0.50f else 0.44f)
    val moduleCardElevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    val moduleCardContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.45f).compositeOver(materialColorScheme.surface)
    val sliderColors = SliderDefaults.colors(
        thumbColor = colorScheme.primary,
        activeTrackColor = colorScheme.primary,
        inactiveTrackColor = sliderInactiveTrackColor,
        activeTickColor = colorScheme.primary.copy(alpha = 0f),
        inactiveTickColor = materialColorScheme.outlineVariant.copy(alpha = 0f),
        disabledActiveTrackColor = sliderDisabledActiveTrackColor,
        disabledInactiveTrackColor = sliderDisabledInactiveTrackColor,
        disabledThumbColor = sliderDisabledThumbColor
    )
    val allPresets = remember(customPresets) {
        EqualizerPresets.DefaultPresets + customPresets
    }
    val scenePresets = remember { SceneEffectPresets.All }
    
    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var activeTipKey by remember { mutableStateOf<String?>(null) }
    val freqLabels = remember { listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k") }
    val updatePlaybackParameters: (Float, Float) -> Unit = remember(
        onPlaybackParametersChanged,
        onPlaybackSpeedChanged,
        onPlaybackPitchChanged
    ) {
        { speed: Float, pitch: Float ->
            if (onPlaybackParametersChanged != null) {
                onPlaybackParametersChanged(speed, pitch)
            } else {
                onPlaybackSpeedChanged?.invoke(speed)
                onPlaybackPitchChanged?.invoke(pitch)
            }
        }
    }
    val updatePlaybackSpeed: (Float) -> Unit = remember(
        onPlaybackParametersChanged,
        onPlaybackSpeedChanged,
        playbackPitch
    ) {
        { speed: Float ->
            if (onPlaybackParametersChanged != null && playbackPitch != null) {
                onPlaybackParametersChanged(speed, playbackPitch)
            } else {
                onPlaybackSpeedChanged?.invoke(speed)
            }
        }
    }
    val updatePlaybackPitch: (Float) -> Unit = remember(
        onPlaybackParametersChanged,
        onPlaybackPitchChanged,
        playbackSpeed
    ) {
        { pitch: Float ->
            if (onPlaybackParametersChanged != null && playbackSpeed != null) {
                onPlaybackParametersChanged(playbackSpeed, pitch)
            } else {
                onPlaybackPitchChanged?.invoke(pitch)
            }
        }
    }

    @Composable
    fun InfoTip(key: String, title: String, text: String) {
        val density = LocalDensity.current
        val expanded = activeTipKey == key
        val offset = with(density) { IntOffset(0, 26.dp.roundToPx()) }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            Box {
                IconButton(
                    onClick = { activeTipKey = if (expanded) null else key },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (expanded) {
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = offset,
                        onDismissRequest = { activeTipKey = null },
                        properties = PopupProperties(
                            focusable = true,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true
                        )
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            tonalElevation = 6.dp,
                            shadowElevation = 10.dp,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun VerticalSlider(
        value: Float,
        onValueChange: (Float) -> Unit,
        valueRange: ClosedFloatingPointRange<Float>,
        enabled: Boolean,
        modifier: Modifier = Modifier
    ) {
        val density = LocalDensity.current
        val min = valueRange.start
        val max = valueRange.endInclusive
        val range = (max - min).coerceAtLeast(1e-6f)
        val trackColor = if (enabled) sliderInactiveTrackColor.copy(alpha = 0.85f) else sliderDisabledInactiveTrackColor
        val activeColor = if (enabled) colorScheme.primary else sliderDisabledActiveTrackColor
        val thumbColor = if (enabled) colorScheme.primary else sliderDisabledThumbColor
        val centerColor = sliderInactiveTrackColor.copy(alpha = 0.65f)
        val trackWidthPx = with(density) { 6.dp.toPx() }
        val thumbRadiusPx = with(density) { 9.dp.toPx() }
        val centerMarkHalfPx = with(density) { 10.dp.toPx() }

        fun yToValue(y: Float, height: Float): Float {
            val h = height.coerceAtLeast(1f)
            val frac = (1f - (y / h)).coerceIn(0f, 1f)
            return (min + frac * range).coerceIn(min, max)
        }

        val gestureModifier = if (enabled) {
            modifier
                .pointerInput(min, max) {
                    detectTapGestures { offset ->
                        onValueChange(yToValue(offset.y, size.height.toFloat()))
                    }
                }
                .pointerInput(min, max) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            onValueChange(yToValue(change.position.y, size.height.toFloat()))
                        }
                    )
                }
        } else {
            modifier
        }

        Canvas(modifier = gestureModifier) {
            val w = size.width
            val h = size.height
            val x = w / 2f
            val centerY = h / 2f
            val coerced = value.coerceIn(min, max)
            val frac = (coerced - min) / range
            val thumbY = (h - frac * h).coerceIn(0f, h)

            drawLine(
                color = trackColor,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = trackWidthPx,
                cap = StrokeCap.Round
            )
            drawLine(
                color = centerColor,
                start = Offset((x - centerMarkHalfPx).coerceAtLeast(0f), centerY),
                end = Offset((x + centerMarkHalfPx).coerceAtMost(w), centerY),
                strokeWidth = trackWidthPx,
                cap = StrokeCap.Round
            )
            drawLine(
                color = activeColor,
                start = Offset(x, centerY),
                end = Offset(x, thumbY),
                strokeWidth = trackWidthPx,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = thumbColor,
                radius = thumbRadiusPx,
                center = Offset(x, thumbY)
            )
        }
    }

    fun normalizedLevels(): List<Int> {
        val src = settings.bandLevels
        if (src.size == 10) return src
        val out = MutableList(10) { 0 }
        for (i in 0 until minOf(10, src.size)) out[i] = src[i]
        return out
    }


    // Local state to track editing values - completely independent from settings
    var editingLevels by remember { mutableStateOf(normalizedLevels()) }
    
    // Only sync when user explicitly selects a preset (not when we change to "自定义")
    val currentPresetName = settings.presetName
    LaunchedEffect(currentPresetName) {
        // When preset changes to something other than "自定义", update our local state
        if (currentPresetName != "自定义") {
            editingLevels = normalizedLevels()
        }
    }

    fun updateLevel(index: Int, value: Int) {
        val newLevels = editingLevels.toMutableList()
        newLevels[index] = value
        editingLevels = newLevels
        onSettingsChanged(settings.copy(enabled = true, bandLevels = newLevels, presetName = "自定义"))
    }

    fun updateBalance(value: Float) {
        onSettingsChanged(
            settings.copy(
                stereoEnabled = true,
                balance = value.coerceIn(-1f, 1f),
                orbitEnabled = false,
                orbitAzimuthDeg = 0f
            )
        )
    }

    fun updateChannelMode(mode: Int) {
        onSettingsChanged(settings.copy(stereoEnabled = true, channelMode = mode.coerceIn(0, 3)))
    }

    fun updateVolumeThreshold(
        enabled: Boolean? = null,
        mode: Int? = null,
        minDb: Float? = null,
        maxDb: Float? = null,
        loudnessTargetDb: Float? = null
    ) {
        val nextEnabled = enabled ?: settings.volumeThresholdEnabled
        val nextMode = (mode ?: settings.volumeThresholdMode).coerceIn(0, 1)
        var nextMin = minDb ?: settings.volumeThresholdMinDb
        var nextMax = maxDb ?: settings.volumeThresholdMaxDb
        val nextLoudnessTargetDb = (loudnessTargetDb ?: settings.volumeLoudnessTargetDb).coerceIn(-60f, 0f)
        nextMin = nextMin.coerceIn(-60f, 0f)
        nextMax = nextMax.coerceIn(-60f, 0f)
        if (nextMin >= nextMax) {
            if (minDb != null && maxDb == null) {
                nextMin = nextMax - 1f
            } else {
                nextMax = nextMin + 1f
            }
        }
        onSettingsChanged(
            settings.copy(
                volumeThresholdEnabled = nextEnabled,
                volumeThresholdMode = nextMode,
                volumeThresholdMinDb = nextMin,
                volumeThresholdMaxDb = nextMax,
                volumeLoudnessTargetDb = nextLoudnessTargetDb
            )
        )
    }

    fun updateSceneEffect(
        enabled: Boolean? = null,
        presetId: String? = null,
        amount: Int? = null
    ) {
        onSettingsChanged(
            settings.copy(
                sceneEffectEnabled = enabled ?: settings.sceneEffectEnabled,
                sceneEffectPresetId = presetId ?: settings.sceneEffectPresetId,
                sceneEffectAmount = (amount ?: settings.sceneEffectAmount).coerceIn(0, 100)
            )
        )
    }

    @Composable
    fun ModuleCard(
        title: String,
        enabled: Boolean,
        onEnabledChange: (Boolean) -> Unit,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        onReset: () -> Unit,
        resetEnabled: Boolean = enabled,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = moduleCardContainerColor),
            elevation = moduleCardElevation
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                        modifier = Modifier.scale(0.85f)
                    )
                    TextButton(onClick = onReset, enabled = resetEnabled) { Text(androidx.compose.ui.res.stringResource(R.string.ui_reset)) }
                    IconButton(onClick = { onExpandedChange(!expanded) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null
                        )
                    }
                }
                if (expanded) {
                    Column(
                        modifier = Modifier.alpha(if (enabled) 1f else 0.45f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = content
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(androidx.compose.ui.res.stringResource(R.string.ui_sound_effector), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        val vtEnabled = settings.volumeThresholdEnabled
        val vtMode = settings.volumeThresholdMode
        val defaultVtMode = 1
        val defaultVtMinDb = -24f
        val defaultVtMaxDb = -6f
        val defaultVtTargetDb = -18f
        ModuleCard(
            title = appString(R.string.ui_volume_threshold),
            enabled = vtEnabled,
            onEnabledChange = { updateVolumeThreshold(enabled = it) },
            expanded = settings.volumeThresholdExpanded,
            onExpandedChange = { onSettingsChanged(settings.copy(volumeThresholdExpanded = it)) },
            onReset = { updateVolumeThreshold(mode = defaultVtMode, minDb = defaultVtMinDb, maxDb = defaultVtMaxDb, loudnessTargetDb = defaultVtTargetDb) },
            resetEnabled = vtEnabled ||
                settings.volumeThresholdMode != defaultVtMode ||
                settings.volumeThresholdMinDb != defaultVtMinDb ||
                settings.volumeThresholdMaxDb != defaultVtMaxDb ||
                settings.volumeLoudnessTargetDb != defaultVtTargetDb
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(androidx.compose.ui.res.stringResource(R.string.ui_model), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                InfoTip(
                    key = "vt_mode",
                    title = appString(R.string.ui_model),
                    text = appString(R.string.ui_loudness_equalization_pulls_the_overall_loudness_towards_the)
                )
            }
            val modeChipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorScheme.primary.copy(alpha = 0.18f),
                selectedLabelColor = colorScheme.primary,
                selectedLeadingIconColor = colorScheme.primary
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to appString(R.string.ui_loudness_equalization), 0 to appString(R.string.ui_threshold)).forEach { (m, label) ->
                    FilterChip(
                        selected = vtMode == m,
                        onClick = { updateVolumeThreshold(mode = m) },
                        label = { Text(label) },
                        enabled = true,
                        colors = modeChipColors,
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = vtMode == m,
                            borderColor = colorScheme.primary.copy(alpha = 0.22f),
                            selectedBorderColor = colorScheme.primary
                        )
                    )
                }
            }

            if (vtMode == 1) {
                Column {
                    Row {
                        Text(androidx.compose.ui.res.stringResource(R.string.ui_target_loudness), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        InfoTip(
                            key = "vt_target",
                            title = appString(R.string.ui_target_loudness),
                            text = appString(R.string.ui_gradually_pull_the_playback_loudness_towards_the_target)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(String.format("%.0f dBFS", settings.volumeLoudnessTargetDb), style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = settings.volumeLoudnessTargetDb,
                        onValueChange = { updateVolumeThreshold(loudnessTargetDb = it) },
                        valueRange = -36f..-6f,
                        enabled = vtEnabled,
                        colors = sliderColors
                    )
                }
            } else {
                Column {
                    Row {
                        Text(androidx.compose.ui.res.stringResource(R.string.ui_minimum_threshold), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        InfoTip(
                            key = "vt_min",
                            title = appString(R.string.ui_minimum_threshold),
                            text = appString(R.string.ui_when_the_volume_is_lower_than_this_value)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(String.format("%.0f dBFS", settings.volumeThresholdMinDb), style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = settings.volumeThresholdMinDb,
                        onValueChange = { updateVolumeThreshold(minDb = it) },
                        valueRange = -60f..0f,
                        enabled = vtEnabled,
                        colors = sliderColors
                    )
                }

                Column {
                    Row {
                        Text(androidx.compose.ui.res.stringResource(R.string.ui_maximum_threshold), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        InfoTip(
                            key = "vt_max",
                            title = appString(R.string.ui_maximum_threshold),
                            text = appString(R.string.ui_when_the_volume_is_higher_than_this_value)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(String.format("%.0f dBFS", settings.volumeThresholdMaxDb), style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = settings.volumeThresholdMaxDb,
                        onValueChange = { updateVolumeThreshold(maxDb = it) },
                        valueRange = -60f..0f,
                        enabled = vtEnabled,
                        colors = sliderColors
                    )
                }
            }
        }

        val sceneEnabled = settings.sceneEffectEnabled
        val activeScenePreset = remember(settings.sceneEffectPresetId) {
            SceneEffectPresets.resolve(settings.sceneEffectPresetId)
        }
        ModuleCard(
            title = appString(R.string.ui_scene_effects),
            enabled = sceneEnabled,
            onEnabledChange = { updateSceneEffect(enabled = it) },
            expanded = settings.sceneEffectExpanded,
            onExpandedChange = { onSettingsChanged(settings.copy(sceneEffectExpanded = it)) },
            onReset = {
                updateSceneEffect(
                    presetId = SceneEffectPresets.DefaultPresetId,
                    amount = SceneEffectPresets.DefaultAmount
                )
            },
            resetEnabled = sceneEnabled ||
                settings.sceneEffectPresetId != SceneEffectPresets.DefaultPresetId ||
                settings.sceneEffectAmount != SceneEffectPresets.DefaultAmount
        ) {
            var sceneExpanded by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ui_scene_preset), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(
                        key = "scene_preset",
                        title = appString(R.string.ui_scene_effects),
                        text = appString(R.string.ui_simulate_common_spaces_or_transmission_media_through_band)
                    )
                }
                ExposedDropdownMenuBox(
                    expanded = sceneExpanded,
                    onExpandedChange = { if (sceneEnabled) sceneExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = appString(R.string.ui_default),
                        style = MaterialTheme.typography.labelSmall,
                        color = materialColorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = materialColorScheme.surface.copy(alpha = 0.55f),
                        contentColor = materialColorScheme.onSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = materialColorScheme.outlineVariant.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable(enabled = sceneEnabled) { sceneExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeScenePreset.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.widthIn(min = 0.dp, max = 124.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = activeScenePreset.description,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = materialColorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = sceneExpanded)
                        }
                    }
                    MaterialTheme(
                        colorScheme = materialColorScheme.copy(
                            surface = moduleCardContainerColor,
                            surfaceVariant = moduleCardContainerColor
                        )
                    ) {
                        DropdownMenu(
                            expanded = sceneExpanded,
                            onDismissRequest = { sceneExpanded = false },
                            modifier = Modifier
                                .exposedDropdownSize(matchTextFieldWidth = true)
                                .background(moduleCardContainerColor, RoundedCornerShape(14.dp))
                        ) {
                            scenePresets.forEachIndexed { index, preset ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        thickness = 0.5.dp,
                                        color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = preset.label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(min = 0.dp, max = 124.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = preset.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = materialColorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                                            )
                                        }
                                    },
                                    onClick = {
                                        updateSceneEffect(enabled = true, presetId = preset.id)
                                        sceneExpanded = false
                                    },
                                    enabled = sceneEnabled,
                                    colors = MenuDefaults.itemColors(
                                        textColor = materialColorScheme.onSurface,
                                        leadingIconColor = materialColorScheme.onSurface,
                                        trailingIconColor = materialColorScheme.onSurface,
                                        disabledTextColor = materialColorScheme.onSurface.copy(alpha = 0.38f),
                                        disabledLeadingIconColor = materialColorScheme.onSurface.copy(alpha = 0.38f),
                                        disabledTrailingIconColor = materialColorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                )
                            }
                        }
                    }
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(androidx.compose.ui.res.stringResource(R.string.ui_effect_intensity), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        InfoTip(
                            key = "scene_amount",
                            title = appString(R.string.ui_effect_intensity),
                            text = appString(R.string.ui_the_higher_the_intensity_the_more_obvious_the)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${settings.sceneEffectAmount}%", style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = settings.sceneEffectAmount.toFloat(),
                        onValueChange = { updateSceneEffect(enabled = true, amount = it.toInt()) },
                        valueRange = 0f..100f,
                        enabled = sceneEnabled,
                        colors = sliderColors
                    )
                }
            }
        }

        if (
            playbackSpeed != null &&
            playbackPitch != null &&
            (onPlaybackParametersChanged != null || (onPlaybackSpeedChanged != null && onPlaybackPitchChanged != null))
        ) {
            val speedPitchEnabled = settings.speedPitchEnabled
            ModuleCard(
                title = appString(R.string.ui_variable_speed_and_pitch),
                enabled = speedPitchEnabled,
                onEnabledChange = { enabled ->
                    onSettingsChanged(settings.copy(speedPitchEnabled = enabled))
                    if (!enabled) {
                        updatePlaybackParameters(1f, 1f)
                    }
                },
                expanded = settings.speedPitchExpanded,
                onExpandedChange = { onSettingsChanged(settings.copy(speedPitchExpanded = it)) },
                onReset = {
                    updatePlaybackParameters(1f, 1f)
                },
                resetEnabled = speedPitchEnabled
            ) {
                Column {
                    Row {
                        Text(androidx.compose.ui.res.stringResource(R.string.ui_play_speed), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        InfoTip(
                            key = "playback_speed",
                            title = appString(R.string.ui_play_speed),
                            text = appString(R.string.ui_the_bigger_it_is_the_faster_it_plays)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(String.format("%.2fx", playbackSpeed), style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = playbackSpeed,
                        onValueChange = updatePlaybackSpeed,
                        valueRange = 0.5f..2f,
                        enabled = speedPitchEnabled,
                        colors = sliderColors
                    )
                }

                Column {
                    Row {
                        Text(androidx.compose.ui.res.stringResource(R.string.ui_tone), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        InfoTip(
                            key = "playback_pitch",
                            title = appString(R.string.ui_tone),
                            text = appString(R.string.ui_the_larger_the_pitch_the_higher_the_pitch)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(String.format("%.2fx", playbackPitch), style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = playbackPitch,
                        onValueChange = updatePlaybackPitch,
                        valueRange = 0.5f..2f,
                        enabled = speedPitchEnabled,
                        colors = sliderColors
                    )
                }
            }
        }

        val stereoEnabled = settings.stereoEnabled
        ModuleCard(
            title = appString(R.string.ui_stereo),
            enabled = stereoEnabled,
            onEnabledChange = { onSettingsChanged(settings.copy(stereoEnabled = it)) },
            expanded = settings.stereoExpanded,
            onExpandedChange = { onSettingsChanged(settings.copy(stereoExpanded = it)) },
            onReset = {
                onSettingsChanged(
                    settings.copy(
                        stereoEnabled = true,
                        orbitDistance = 5f,
                        orbitAzimuthDeg = 0f,
                        orbitEnabled = false,
                        orbitSpeed = 25f,
                        balance = 0f,
                        channelMode = 0
                    )
                )
            },
            resetEnabled = stereoEnabled
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ui_sound_source_distance), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(
                        key = "st_distance",
                        title = appString(R.string.ui_sound_source_distance),
                        text = appString(R.string.ui_the_sound_will_be_softer_and_slightly_attenuated)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(String.format("%.1f", settings.orbitDistance), style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = settings.orbitDistance,
                    onValueChange = { onSettingsChanged(settings.copy(stereoEnabled = true, orbitDistance = it)) },
                    valueRange = 0f..10f,
                    enabled = stereoEnabled,
                    colors = sliderColors
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ui_sound_source_direction), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(
                        key = "st_azimuth",
                        title = appString(R.string.ui_sound_source_direction),
                        text = appString(R.string.ui_0_is_centered_90_is_to_the_right)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(String.format("%.0f°", settings.orbitAzimuthDeg), style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = settings.orbitAzimuthDeg.coerceIn(0f, 360f),
                    onValueChange = {
                        val deg = if (it >= 360f) 0f else it
                        onSettingsChanged(
                            settings.copy(
                                stereoEnabled = true,
                                orbitAzimuthDeg = deg,
                                orbitEnabled = false,
                                balance = 0f
                            )
                        )
                    },
                    valueRange = 0f..360f,
                    enabled = stereoEnabled && !settings.orbitEnabled,
                    colors = sliderColors
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(androidx.compose.ui.res.stringResource(R.string.ui_sound_source_automatically_surrounds), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                InfoTip(
                    key = "st_orbit",
                    title = appString(R.string.ui_sound_source_automatically_surrounds),
                    text = appString(R.string.ui_when_turned_on_the_sound_and_image_will)
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.orbitEnabled,
                    onCheckedChange = { onSettingsChanged(settings.copy(stereoEnabled = true, orbitEnabled = it, balance = 0f)) },
                    enabled = stereoEnabled,
                    modifier = Modifier.scale(0.85f)
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ui_orbital_speed), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(
                        key = "st_speed",
                        title = appString(R.string.ui_orbital_speed),
                        text = appString(R.string.ui_the_higher_the_speed_the_faster_the_sound)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(String.format("%.0f°/s", settings.orbitSpeed), style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = settings.orbitSpeed,
                    onValueChange = { onSettingsChanged(settings.copy(stereoEnabled = true, orbitSpeed = it)) },
                    valueRange = 0f..50f,
                    enabled = stereoEnabled && settings.orbitEnabled,
                    colors = sliderColors
                )
            }

            val panActive = settings.orbitEnabled || settings.orbitAzimuthDeg != 0f
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ui_left_and_right_balance), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(
                        key = "ch_balance",
                        title = appString(R.string.ui_left_and_right_balance),
                        text = appString(R.string.ui_shift_the_overall_sound_to_the_left_or)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    val balText = when {
                        settings.balance < -0.05f -> appString(R.string.ui_left_bias, (settings.balance * -100).toInt())
                        settings.balance > 0.05f -> appString(R.string.ui_rightward, (settings.balance * 100).toInt())
                        else -> appString(R.string.ui_center)
                    }
                    Text(balText, style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = settings.balance,
                    onValueChange = { updateBalance(it) },
                    valueRange = -1f..1f,
                    enabled = stereoEnabled && !panActive,
                    colors = sliderColors
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(androidx.compose.ui.res.stringResource(R.string.ui_channel_mode), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                InfoTip(
                    key = "ch_mode",
                    title = appString(R.string.ui_channel_mode),
                    text = appString(R.string.ui_invert_swap_left_and_right_channels_clone_copy)
                )
            }
            val channelModeChipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorScheme.primary.copy(alpha = 0.18f),
                selectedLabelColor = colorScheme.primary,
                selectedLeadingIconColor = colorScheme.primary
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    0 to appString(R.string.ui_normal),
                    1 to appString(R.string.ui_reverse),
                    2 to appString(R.string.ui_clone_l_r),
                    3 to appString(R.string.ui_clone_r_l)
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.channelMode == mode,
                        onClick = { updateChannelMode(mode) },
                        label = { Text(label) },
                        enabled = stereoEnabled,
                        colors = channelModeChipColors,
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = stereoEnabled,
                            selected = settings.channelMode == mode,
                            borderColor = colorScheme.primary.copy(alpha = 0.22f),
                            selectedBorderColor = colorScheme.primary
                        )
                    )
                }
            }
        }

        val eqEnabled = settings.enabled
        ModuleCard(
            title = appString(R.string.ui_equalizer),
            enabled = eqEnabled,
            onEnabledChange = { onSettingsChanged(settings.copy(enabled = it, presetName = "自定义")) },
            expanded = settings.equalizerExpanded,
            onExpandedChange = { onSettingsChanged(settings.copy(equalizerExpanded = it)) },
            onReset = {
                onSettingsChanged(
                    settings.copy(
                        enabled = true,
                        bandLevels = List(10) { 0 },
                        presetName = "默认"
                    )
                )
            },
            resetEnabled = eqEnabled
        ) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (eqEnabled) expanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = appString(R.string.ui_default),
                    style = MaterialTheme.typography.labelSmall,
                    color = materialColorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = materialColorScheme.surface.copy(alpha = 0.55f),
                    contentColor = materialColorScheme.onSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = materialColorScheme.outlineVariant.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable(enabled = eqEnabled) { expanded = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = equalizerPresetLabel(settings.presetName),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                }
                MaterialTheme(
                    colorScheme = materialColorScheme.copy(
                        surface = moduleCardContainerColor,
                        surfaceVariant = moduleCardContainerColor
                    )
                ) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .exposedDropdownSize(matchTextFieldWidth = true)
                            .background(moduleCardContainerColor, RoundedCornerShape(14.dp))
                    ) {
                        allPresets.forEachIndexed { index, preset ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    thickness = 0.5.dp,
                                    color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (preset.isCustom) preset.name else equalizerPresetLabel(preset.name))
                                        if (preset.isCustom) {
                                            Spacer(modifier = Modifier.weight(1f))
                                            IconButton(onClick = { onDeletePreset(preset) }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    val levels = preset.bandLevels.let { src ->
                                        val out = MutableList(10) { 0 }
                                        for (i in 0 until minOf(10, src.size)) out[i] = src[i]
                                        out
                                    }
                                    onSettingsChanged(
                                        settings.copy(
                                            enabled = true,
                                            bandLevels = levels,
                                            presetName = preset.name
                                        )
                                    )
                                    expanded = false
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = materialColorScheme.onSurface,
                                    leadingIconColor = materialColorScheme.onSurface,
                                    trailingIconColor = materialColorScheme.onSurface,
                                    disabledTextColor = materialColorScheme.onSurface.copy(alpha = 0.38f),
                                    disabledLeadingIconColor = materialColorScheme.onSurface.copy(alpha = 0.38f),
                                    disabledTrailingIconColor = materialColorScheme.onSurface.copy(alpha = 0.38f)
                                ),
                                enabled = eqEnabled
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(androidx.compose.ui.res.stringResource(R.string.ui_frequency_band_adjustment), style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                InfoTip(
                    key = "eq_bands",
                    title = appString(R.string.ui_frequency_band_adjustment),
                    text = appString(R.string.ui_each_slider_corresponds_to_a_center_frequency_band)
                )
            }
            // val levels = normalizedLevels()  // 不再使用，改用 currentLevels
            val scrollState = rememberScrollState()
            val sliderHeight = 230.dp
            val sliderThickness = 44.dp
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    for (i in 0 until 10) {
                        val db = (editingLevels[i] / 100f)
                        val dbText = if (abs(db) < 0.01f) "0 dB" else "${db.toInt()} dB"
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(freqLabels[i], style = MaterialTheme.typography.labelSmall)
                            Box(
                                modifier = Modifier
                                    .height(sliderHeight)
                                    .width(sliderThickness),
                                contentAlignment = Alignment.Center
                            ) {
                                VerticalSlider(
                                    value = editingLevels[i].toFloat(),
                                    onValueChange = { updateLevel(i, it.toInt()) },
                                    valueRange = -1500f..1500f,
                                    enabled = eqEnabled,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(dbText, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (scrollState.canScrollForward) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(36.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        moduleCardContainerColor.copy(alpha = 0f),
                                        moduleCardContainerColor
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = materialColorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                }
                if (scrollState.canScrollBackward) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(36.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        moduleCardContainerColor,
                                        moduleCardContainerColor.copy(alpha = 0f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = materialColorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                }
            }

            Button(
                onClick = { showSaveDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = eqEnabled
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(androidx.compose.ui.res.stringResource(R.string.ui_save_as_custom_preset))
            }
        }
    }

    if (showSaveDialog) {
        FlatTextFieldDialog(
            onDismissRequest = { showSaveDialog = false },
            message = appString(R.string.ui_please_enter_a_custom_preset_name),
            value = newPresetName,
            onValueChange = { newPresetName = it },
            placeholder = appString(R.string.ui_default_name),
            confirmText = appString(R.string.ui_save),
            confirmEnabled = newPresetName.trim().isNotBlank(),
            onConfirm = {
                if (newPresetName.isNotBlank()) {
                    onSavePreset(newPresetName.trim())
                    showSaveDialog = false
                    newPresetName = ""
                }
            },
        )
    }
}

private fun equalizerPresetLabel(name: String): String = when (name) {
    "默认" -> appString(R.string.ui_default_c8d09c)
    "自定义" -> appString(R.string.ui_custom)
    "清亮 (Bright)" -> appString(R.string.eq_bright)
    "低音增强 (Bass Boost)" -> appString(R.string.eq_bass_boost)
    "拍打-清脆 (Tapping-Crisp)" -> appString(R.string.eq_tapping_crisp)
    "拍打-沉闷 (Tapping-Dull)" -> appString(R.string.eq_tapping_dull)
    "人声增强 (Vocal)" -> appString(R.string.eq_vocal)
    else -> name
}
