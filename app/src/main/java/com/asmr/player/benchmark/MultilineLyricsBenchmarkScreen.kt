package com.asmr.player.benchmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.asmr.player.ui.player.NowPlayingLyricsPreview
import com.asmr.player.ui.player.rememberLyricReadableColors
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.data.settings.FloatingLyricsSettings
import com.asmr.player.service.FloatingLyricsView
import com.asmr.player.util.SubtitleEntry
import kotlinx.coroutines.delay

@Composable
internal fun MultilineLyricsBenchmarkScreen(floating: Boolean = false) {
    val lyrics = remember {
        listOf(
            "短字幕。",
            "在固定区域中完整显示这句话，文字会自动换行，播放按钮与封面的位置保持稳定。",
            "超长字幕可以在固定区域内上下滑动阅读，字幕切换不会改变封面和播放控件的位置。".repeat(24)
        ).mapIndexed { index, text -> SubtitleEntry(index * 1200L, (index + 1) * 1200L, text) }
    }
    var cue by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var compact by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(running, lifecycle) {
        if (running) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(1200)
                cue = (cue + 1) % lyrics.size
            }
        }
    }
    val theme = AsmrTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(if (floating) "悬浮多行字幕基准场景" else "多行字幕基准场景", style = MaterialTheme.typography.titleLarge)
        Row {
            TextButton(
                onClick = { running = !running },
                colors = ButtonDefaults.textButtonColors(contentColor = theme.primaryStrong)
            ) { Text(if (running) "停止切句" else "开始切句") }
            TextButton(
                onClick = { compact = !compact },
                colors = ButtonDefaults.textButtonColors(contentColor = theme.primaryStrong)
            ) { Text(if (compact) "标准高度" else "紧凑高度") }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(if (compact) 72.dp else 220.dp)) {
            if (floating) {
                key(compact) {
                    AndroidView(
                        factory = { context ->
                            FloatingLyricsView(context).apply {
                                applySettings(FloatingLyricsSettings(size = if (compact) 32f else 16f), multiline = true)
                            }
                        },
                        update = { it.updateLine(lyrics[cue].text, lyrics[cue]) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                NowPlayingLyricsPreview(
                    lyrics = lyrics,
                    currentPosition = lyrics[cue].startMs,
                    onOpenLyrics = {},
                    colors = rememberLyricReadableColors(theme.primaryStrong),
                    multilineEnabled = true,
                    highlightFontSizeSp = if (compact) 36f else 24f,
                    centered = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("固定播放控件", Modifier.fillMaxWidth().height(64.dp).background(theme.primarySoft))
    }
}
