package com.asmr.player.benchmark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.player.CoverArtworkBackground
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.ThemeMode
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

    AsmrPlayerTheme(mode = if (dark) ThemeMode.Dark else ThemeMode.Light) {
        val colors = AsmrTheme.colorScheme
        Box(Modifier.fillMaxSize().background(colors.background)) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (visible) 1f else 0f }) {
                CoverArtworkBackground(
                    artworkModel = covers.getOrNull(coverIndex),
                    enabled = true,
                    clarity = clarity,
                    isPlaying = playing,
                    isActive = visible
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
                Row {
                    TextButton(onClick = { playing = !playing }, colors = buttonColors) {
                        Text(if (playing) "暂停流光" else "继续流光")
                    }
                    TextButton(onClick = { visible = !visible }, colors = buttonColors) {
                        Text(if (visible) "隐藏背景" else "显示背景")
                    }
                }
            }
        }
    }
}

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
