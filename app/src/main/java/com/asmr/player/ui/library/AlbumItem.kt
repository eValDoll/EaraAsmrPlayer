package com.asmr.player.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.asmr.player.domain.model.Album
import com.asmr.player.cache.CacheImageModel
import com.asmr.player.ui.common.AsmrAsyncImage
 import com.asmr.player.ui.common.CoverContentRow
import com.asmr.player.ui.common.CvChipsFlow
import com.asmr.player.ui.common.CvChipsSingleLine
import com.asmr.player.ui.common.AsmrShimmerPlaceholder
import com.asmr.player.ui.common.rememberDominantColor
import com.asmr.player.util.DlsiteAntiHotlink

import com.asmr.player.ui.theme.AsmrTheme

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AlbumItem(
    album: Album,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    emptyCoverUseShimmer: Boolean = false
) {
    val colorScheme = AsmrTheme.colorScheme
    val shape = remember { RoundedCornerShape(16.dp) }
    val coverShape = remember { RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 0.dp, bottomEnd = 0.dp) }
    val data = album.coverThumbPath.takeIf { it.isNotBlank() && it.contains("_v2") }
        .orEmpty()
        .ifBlank { album.coverPath }
        .ifEmpty { album.coverUrl }
    val imageModel = remember(data) {
        val headers = if (data.startsWith("http", ignoreCase = true)) DlsiteAntiHotlink.headersForImageUrl(data) else emptyMap()
        if (headers.isEmpty()) data else CacheImageModel(data = data, headers = headers, keyTag = "dlsite")
    }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val listItemHeight = (screenWidthDp.dp * 0.24f).coerceIn(112.dp, 140.dp)
    val coverSize = listItemHeight

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .background(colorScheme.surface.copy(alpha = 0.5f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        CoverContentRow(
            coverWidth = coverSize,
            minHeight = coverSize,
            spacing = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(listItemHeight),
            cover = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    if (emptyCoverUseShimmer) {
                        AsmrAsyncImage(
                            model = imageModel,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholderCornerRadius = 0,
                            modifier = Modifier.fillMaxSize().clip(coverShape),
                            empty = { m -> AsmrShimmerPlaceholder(modifier = m, cornerRadius = 0) },
                        )
                    } else {
                        AsmrAsyncImage(
                            model = imageModel,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholderCornerRadius = 0,
                            modifier = Modifier.fillMaxSize().clip(coverShape),
                        )
                    }
                }
            },
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(top = 4.dp, bottom = 4.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom)
                ) {
                    val rj = album.rjCode.ifBlank { album.workId }
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (rj.isNotBlank()) {
                            Text(
                                text = rj,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary,
                                modifier = Modifier
                                    .background(colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (album.circle.isNotBlank()) {
                            Text(
                                text = album.circle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.primary.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (album.cv.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        CvChipsSingleLine(
                            cvText = album.cv,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    val statsText = remember(
                        album.ratingValue,
                        album.ratingCount,
                        album.dlCount,
                        album.priceJpy,
                        album.releaseDate
                    ) {
                        buildString {
                            val rv = album.ratingValue
                            if (rv != null && rv > 0.0) {
                                append("★")
                                append(String.format("%.1f", rv))
                                if (album.ratingCount > 0) append("(${album.ratingCount})")
                            }
                            if (album.dlCount > 0) {
                                if (isNotEmpty()) append(" · ")
                                append("DL ${album.dlCount}")
                            }
                            if (album.priceJpy > 0) {
                                if (isNotEmpty()) append(" · ")
                                append("¥${album.priceJpy}")
                            }
                            if (album.releaseDate.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(album.releaseDate)
                            }
                        }
                    }
                    if (statsText.isNotBlank()) {
                        Text(
                            text = statsText,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (album.tags.isNotEmpty()) {
                        val scrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clipToBounds()
                                .horizontalScroll(scrollState),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            album.tags.forEach { tag ->
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.primary.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .widthIn(max = 200.dp)
                                        .background(colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            },
        )
        
        if (album.hasAsmrOne) {
            CollectedStamp(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    emptyCoverUseShimmer: Boolean = false
) {
    val colorScheme = AsmrTheme.colorScheme
    val shape = remember { RoundedCornerShape(20.dp) }
    val coverShape = remember { RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp) }
    val data = album.coverThumbPath.takeIf { it.isNotBlank() && it.contains("_v2") }
        .orEmpty()
        .ifBlank { album.coverPath }
        .ifEmpty { album.coverUrl }
    val imageModel = remember(data) {
        val headers = if (data.startsWith("http", ignoreCase = true)) DlsiteAntiHotlink.headersForImageUrl(data) else emptyMap()
        if (headers.isEmpty()) data else CacheImageModel(data = data, headers = headers, keyTag = "dlsite")
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(colorScheme.surface.copy(alpha = 0.3f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            if (emptyCoverUseShimmer) {
                AsmrAsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholderCornerRadius = 0,
                    modifier = Modifier.fillMaxSize().clip(coverShape),
                    empty = { m -> AsmrShimmerPlaceholder(modifier = m, cornerRadius = 0) },
                )
            } else {
                AsmrAsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholderCornerRadius = 0,
                    modifier = Modifier.fillMaxSize().clip(coverShape),
                )
            }
            
            val rj = album.rjCode.ifBlank { album.workId }
            if (rj.isNotBlank()) {
                Text(
                    text = rj,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            if (album.releaseDate.isNotBlank()) {
                Text(
                    text = album.releaseDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            if (album.hasAsmrOne) {
                CollectedStamp(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }
        
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.textPrimary,
                overflow = TextOverflow.Clip
            )
            
            if (album.circle.isNotBlank()) {
                Text(
                    text = album.circle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            CvChipsFlow(cvText = album.cv)

            val statsText = remember(album.ratingValue, album.ratingCount, album.priceJpy) {
                buildString {
                    val rv = album.ratingValue
                    if (rv != null && rv > 0.0) {
                        append("★")
                        append(String.format("%.1f", rv))
                        if (album.ratingCount > 0) append("(${album.ratingCount})")
                    }
                    if (album.priceJpy > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("¥${album.priceJpy}")
                    }
                }
            }
            if (statsText.isNotBlank()) {
                Text(
                    text = statsText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (album.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    album.tags.forEach { tag ->
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier
                                .background(colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectedStamp(modifier: Modifier = Modifier) {
    val dangerColor = AsmrTheme.colorScheme.danger
    Box(
        modifier = modifier
            .rotate(15f)
            .background(dangerColor.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "收录",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
