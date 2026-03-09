package com.asmr.player.ui.common

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Save
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
    
    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var activeTipKey by remember { mutableStateOf<String?>(null) }
    val freqLabels = remember { listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k") }

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
                        imageVector = Icons.Default.Info,
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

    fun updateLevel(index: Int, value: Int) {
        val newLevels = normalizedLevels().toMutableList()
        newLevels[index] = value
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
                    TextButton(onClick = onReset, enabled = resetEnabled) { Text("重置") }
                    IconButton(onClick = { onExpandedChange(!expanded) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
        Text("音效器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        val vtEnabled = settings.volumeThresholdEnabled
        val vtMode = settings.volumeThresholdMode
        val defaultVtMode = 1
        val defaultVtMinDb = -24f
        val defaultVtMaxDb = -6f
        val defaultVtTargetDb = -18f
        ModuleCard(
            title = "音量阈值",
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
                Text("模式", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                InfoTip(
                    key = "vt_mode",
                    title = "模式",
                    text = "响度均衡：把整体响度拉向目标值，适合不同音频之间的音量统一；阈值：把响度限制在一个区间，更像自动增益+压制。"
                )
            }
            val modeChipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorScheme.primary.copy(alpha = 0.18f),
                selectedLabelColor = colorScheme.primary,
                selectedLeadingIconColor = colorScheme.primary
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "响度均衡", 0 to "阈值").forEach { (m, label) ->
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
                        Text("目标响度", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        InfoTip(
                            key = "vt_target",
                            title = "目标响度",
                            text = "把播放响度逐步拉向该目标值（变化更平滑，更适合不同音频之间的统一）。建议范围：-24 到 -14。"
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
                        Text("最小阈值", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        InfoTip(
                            key = "vt_min",
                            title = "最小阈值",
                            text = "当音量低于该值时，会自动放大到接近该阈值。"
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
                        Text("最大阈值", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        InfoTip(
                            key = "vt_max",
                            title = "最大阈值",
                            text = "当音量高于该值时，会自动压制到不超过该阈值。"
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

        if (playbackSpeed != null && playbackPitch != null && onPlaybackSpeedChanged != null && onPlaybackPitchChanged != null) {
            val speedPitchEnabled = settings.speedPitchEnabled
            ModuleCard(
                title = "变速变调",
                enabled = speedPitchEnabled,
                onEnabledChange = { enabled ->
                    onSettingsChanged(settings.copy(speedPitchEnabled = enabled))
                    if (!enabled) {
                        onPlaybackSpeedChanged(1f)
                        onPlaybackPitchChanged(1f)
                    }
                },
                expanded = settings.speedPitchExpanded,
                onExpandedChange = { onSettingsChanged(settings.copy(speedPitchExpanded = it)) },
                onReset = {
                    onPlaybackSpeedChanged(1f)
                    onPlaybackPitchChanged(1f)
                },
                resetEnabled = speedPitchEnabled
            ) {
                Column {
                    Row {
                        Text("播放速度", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        InfoTip(
                            key = "playback_speed",
                            title = "播放速度",
                            text = "越大播放越快（影响节奏与时长）。"
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(String.format("%.2fx", playbackSpeed), style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = playbackSpeed,
                        onValueChange = { onPlaybackSpeedChanged(it) },
                        valueRange = 0.5f..2f,
                        enabled = speedPitchEnabled,
                        colors = sliderColors
                    )
                }

                Column {
                    Row {
                        Text("音调", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        InfoTip(
                            key = "playback_pitch",
                            title = "音调",
                            text = "越大音高越高、越小音高越低（不一定改变播放时长）。"
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(String.format("%.2fx", playbackPitch), style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = playbackPitch,
                        onValueChange = { onPlaybackPitchChanged(it) },
                        valueRange = 0.5f..2f,
                        enabled = speedPitchEnabled,
                        colors = sliderColors
                    )
                }
            }
        }

        val stereoEnabled = settings.stereoEnabled
        ModuleCard(
            title = "立体声",
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
                    Text("声源距离", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(
                        key = "st_distance",
                        title = "声源距离",
                        text = "距离越远，声音会更柔和并略有衰减。"
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
                    Text("声源方向", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(
                        key = "st_azimuth",
                        title = "声源方向",
                        text = "0°为居中，90°偏右，270°偏左。"
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
                Text("声源自动环绕", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                InfoTip(
                    key = "st_orbit",
                    title = "声源自动环绕",
                    text = "开启后，声像会按速度自动移动。"
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
                    Text("环绕速度", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(
                        key = "st_speed",
                        title = "环绕速度",
                        text = "速度越大，声像移动越快。"
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
                    Text("左右平衡", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoTip(
                        key = "ch_balance",
                        title = "左右平衡",
                        text = "把整体声音偏向左或右声道。与“声源方向/自动环绕”互斥。"
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    val balText = when {
                        settings.balance < -0.05f -> "左偏 ${(settings.balance * -100).toInt()}%"
                        settings.balance > 0.05f -> "右偏 ${(settings.balance * 100).toInt()}%"
                        else -> "居中"
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
                Text("声道模式", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                InfoTip(
                    key = "ch_mode",
                    title = "声道模式",
                    text = "反转：交换左右声道；克隆：把一侧声道复制到另一侧。"
                )
            }
            val channelModeChipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorScheme.primary.copy(alpha = 0.18f),
                selectedLabelColor = colorScheme.primary,
                selectedLeadingIconColor = colorScheme.primary
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    0 to "正常",
                    1 to "反转",
                    2 to "克隆 L→R",
                    3 to "克隆 R→L"
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
            title = "均衡器",
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
                    text = "预设",
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
                            text = settings.presetName,
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
                        allPresets.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(preset.name)
                                        if (preset.isCustom) {
                                            Spacer(modifier = Modifier.weight(1f))
                                            IconButton(onClick = { onDeletePreset(preset) }) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
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
                Text("频段调节", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                InfoTip(
                    key = "eq_bands",
                    title = "频段调节",
                    text = "每个滑条对应一个中心频段。向上提升、向下削减该频段的能量。"
                )
            }
            val levels = normalizedLevels()
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
                        val db = (levels[i] / 100f)
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
                                    value = levels[i].toFloat(),
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
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
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
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存为自定义预设")
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存预设") },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("预设名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            onSavePreset(newPresetName)
                            showSaveDialog = false
                            newPresetName = ""
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
