package com.asmr.player.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.service.PlaybackState
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleIndexFinder
import kotlinx.coroutines.launch

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun LyricsScreen(
    lyrics: List<SubtitleEntry>,
    playbackState: PlaybackState,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val currentPosition = (playbackState as? PlaybackState.Playing)?.position ?: 0L
    
    val indexFinder = remember(lyrics) { SubtitleIndexFinder(lyrics) }
    val activeIndex = remember(currentPosition, indexFinder) { indexFinder.findActiveIndex(currentPosition) }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            coroutineScope.launch {
                // 横屏时增加偏移量，使活跃歌词更靠近顶部
                listState.animateScrollToItem(activeIndex, scrollOffset = if (isLandscape) -50 else -200)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (lyrics.isEmpty()) {
            Text(text = "暂无歌词", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = if (isLandscape) 100.dp else 200.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(
                    items = lyrics,
                    key = { _, entry -> entry.startMs },
                    contentType = { _, _ -> "lyricLine" }
                ) { index, entry ->
                    val isActive = index == activeIndex
                    Text(
                        text = entry.text,
                        modifier = Modifier
                            .padding(vertical = if (isLandscape) 4.dp else 8.dp, horizontal = 16.dp)
                            .fillMaxWidth(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = if (isActive) {
                                if (isLandscape) 20.sp else 24.sp
                            } else {
                                if (isLandscape) 16.sp else 18.sp
                            },
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            lineHeight = if (isLandscape) 26.sp else 32.sp
                        ),
                        color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
