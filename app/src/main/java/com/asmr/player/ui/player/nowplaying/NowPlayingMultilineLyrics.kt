package com.asmr.player.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asmr.player.util.SubtitleEntry

// 保留原有字幕区尺寸，移除操作栏后全部用于正文；尺寸仍不依赖当前字幕的行数。
internal fun multilineLyricsReserveHeight(availableHeight: Dp, lineHeight: Dp): Dp =
    (lineHeight * 3 + 44.dp).coerceAtMost(availableHeight * 0.45f).coerceAtLeast(0.dp)

private data class MultilineCue(val key: SubtitleEntry?, val text: String)

@Composable
internal fun NowPlayingMultilineLyrics(
    text: String,
    cueKey: SubtitleEntry?,
    style: TextStyle,
    colors: LyricReadableColors,
    centered: Boolean,
    interactionEnabled: Boolean,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cue = remember(cueKey, text) { MultilineCue(cueKey, text) }
    Crossfade(
        targetState = cue,
        animationSpec = tween(440),
        label = "multilineLyricCue",
        modifier = modifier.fillMaxSize().clipToBounds().testTag("multiline_lyrics")
    ) { displayedCue ->
        key(displayedCue.key, displayedCue.text) {
            // 每条字幕有独立滚动位置，切句和拖动进度后都从句首显示。
            val scrollState = rememberScrollState()
            Column(
                Modifier.fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        val scrollRange = scrollState.maxValue
                        if (scrollRange > 0 && size.height > 0f) {
                            val thumbHeight = (size.height * size.height / (size.height + scrollRange))
                                .coerceAtLeast(16.dp.toPx()).coerceAtMost(size.height)
                            drawRoundRect(
                                color = colors.accentEmphasis.copy(alpha = 0.45f),
                                topLeft = Offset(
                                    size.width - 2.dp.toPx(),
                                    (size.height - thumbHeight) * scrollState.value / scrollRange
                                ),
                                size = Size(2.dp.toPx(), thumbHeight),
                                cornerRadius = CornerRadius(1.dp.toPx())
                            )
                        }
                    }
                    .verticalScroll(scrollState, enabled = interactionEnabled)
                    .clickable(enabled = interactionEnabled, onClick = onOpenLyrics)
                    .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayedCue.text,
                    style = style,
                    color = colors.activeText,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().testTag("multiline_lyrics_text")
                )
            }
        }
    }
}
