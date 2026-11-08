package com.asmr.player.benchmark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Trace
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.asmr.player.data.settings.PlayerBackgroundMode
import com.asmr.player.ui.player.CoverArtworkBackground
import com.asmr.player.ui.player.playerSurfaceColors
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.DefaultBrandPrimaryDark
import com.asmr.player.ui.theme.DefaultBrandPrimaryLight
import com.asmr.player.ui.theme.HuePalette
import com.asmr.player.ui.theme.ThemeMode
import com.asmr.player.ui.theme.coverThemeSeedColorFromBitmap
import com.asmr.player.ui.theme.deriveHuePalette
import com.asmr.player.ui.theme.neutralPaletteForMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 只使用内存封面，不连接播放服务或修改设备上的媒体库与设置。 */
@Composable
internal fun FlowingBackgroundBenchmarkScreen() {
    val covers by produceState<List<Bitmap>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.Default) {
            listOf(createFlowingBackgroundSample(false), createFlowingBackgroundSample(true))
        }
    }
    var dark by remember { mutableStateOf(true) }
    var coverIndex by remember { mutableIntStateOf(0) }
    var clarity by remember { mutableFloatStateOf(0.5f) }
    var playing by remember { mutableStateOf(true) }
    var visible by remember { mutableStateOf(true) }
    var backgroundMode by remember { mutableStateOf(PlayerBackgroundMode.Flowing) }
    var coverEnabled by remember { mutableStateOf(true) }
    var dynamicEnabled by remember { mutableStateOf(true) }
    var themeExtractionEnabled by remember { mutableStateOf(false) }
    val mode = if (dark) ThemeMode.Dark else ThemeMode.Light
    val artwork = covers.getOrNull(coverIndex)
    val coverTheme by produceState<BenchmarkCoverTheme?>(
        initialValue = null,
        key1 = themeExtractionEnabled,
        key2 = artwork,
        key3 = mode
    ) {
        if (!themeExtractionEnabled || artwork == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            val fallback = if (mode.isDark) DefaultBrandPrimaryDark else DefaultBrandPrimaryLight
            val seed = run {
                Trace.beginSection("theme.cover.extract")
                try {
                    coverThemeSeedColorFromBitmap(artwork, fallback)
                } finally {
                    Trace.endSection()
                }
            }
            BenchmarkCoverTheme(
                artwork = artwork,
                mode = mode,
                hue = deriveHuePalette(
                    primary = seed,
                    mode = mode,
                    neutral = neutralPaletteForMode(mode),
                    fallbackOnPrimary = if (mode.isDark) Color.White else Color.Black
                )
            )
        }
    }

    AsmrPlayerTheme(mode = mode, hue = coverTheme?.hue.takeIf { themeExtractionEnabled }) {
        val appColors = AsmrTheme.colorScheme
        val colors = remember(appColors, backgroundMode, coverEnabled) {
            playerSurfaceColors(appColors, backgroundMode, coverEnabled)
        }
        Box(Modifier.fillMaxSize().background(colors.background)) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (visible) 1f else 0f }) {
                CoverArtworkBackground(
                    artworkModel = artwork,
                    backgroundMode = backgroundMode,
                    coverEnabled = coverEnabled,
                    clarity = clarity,
                    isPlaying = playing,
                    isActive = visible,
                    dynamicEnabled = dynamicEnabled,
                    overlayBaseColor = colors.background,
                    isDark = colors.isDark
                )
            }
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 40.dp).semantics {
                    if (covers.isNotEmpty()) contentDescription = "benchmark-ready:flowing_background"
                }
            ) {
                val buttonColors = ButtonDefaults.textButtonColors(contentColor = colors.textPrimary)
                Row {
                    TextButton(onClick = { dark = !dark }, colors = buttonColors) {
                        Text(if (dark) "浅色主题" else "深色主题")
                    }
                    TextButton(onClick = { coverIndex = 1 - coverIndex }, colors = buttonColors) {
                        Text("切换封面")
                    }
                }
                Row {
                    for (value in PlayerBackgroundMode.entries) {
                        TextButton(
                            onClick = { backgroundMode = value },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (backgroundMode == value) colors.primaryStrong else colors.textSecondary
                            )
                        ) {
                            Text(when (value) {
                                PlayerBackgroundMode.Theme -> "主题色背景"
                                PlayerBackgroundMode.Solid -> "纯色背景"
                                PlayerBackgroundMode.Flowing -> "流光背景"
                            })
                        }
                    }
                }
                if (backgroundMode == PlayerBackgroundMode.Theme) {
                    Row {
                        for (value in listOf(0f, 0.5f, 1f)) {
                            TextButton(
                                onClick = { clarity = value },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (clarity == value) colors.primaryStrong else colors.textSecondary
                                )
                            ) {
                                Text("透明度 ${(value * 100).toInt()}%")
                            }
                        }
                    }
                    TextButton(onClick = { coverEnabled = !coverEnabled }, colors = buttonColors) {
                        Text(if (coverEnabled) "关闭封面" else "开启封面")
                    }
                } else if (backgroundMode == PlayerBackgroundMode.Flowing) {
                    TextButton(onClick = { dynamicEnabled = !dynamicEnabled }, colors = buttonColors) {
                        Text(if (dynamicEnabled) "关闭动态流光" else "开启动态流光")
                    }
                }
                Row {
                    TextButton(onClick = { playing = !playing }, colors = buttonColors) {
                        Text(if (playing) "暂停流光" else "继续流光")
                    }
                    TextButton(onClick = { visible = !visible }, colors = buttonColors) {
                        Text(if (visible) "隐藏背景" else "显示背景")
                    }
                }
                TextButton(
                    onClick = { themeExtractionEnabled = !themeExtractionEnabled },
                    colors = buttonColors,
                    modifier = Modifier.semantics {
                        if (themeExtractionEnabled && coverTheme?.artwork === artwork && coverTheme?.mode == mode) {
                            contentDescription = "benchmark-ready:cover-theme:$coverIndex"
                        }
                    }
                ) {
                    Text(if (themeExtractionEnabled) "关闭主题取色" else "封面主题取色")
                }
            }
        }
    }
}

private data class BenchmarkCoverTheme(
    val artwork: Bitmap,
    val mode: ThemeMode,
    val hue: HuePalette
)

private fun createFlowingBackgroundSample(alternate: Boolean): Bitmap {
    val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val colors = if (alternate) {
        intArrayOf(0xFF122A43.toInt(), 0xFF54776F.toInt(), 0xFFEBB955.toInt())
    } else {
        intArrayOf(0xFF27123F.toInt(), 0xFFB23B63.toInt(), 0xFFF3B58B.toInt())
    }
    paint.shader = LinearGradient(0f, 0f, 256f, 256f, colors, null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 256f, 256f, paint)
    paint.shader = RadialGradient(
        76f, 168f, 116f,
        intArrayOf(if (alternate) 0xFF80C9B9.toInt() else 0xFFD8A8EB.toInt(), 0x00000000),
        null, Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, 256f, 256f, paint)
    return bitmap
}
