package com.asmr.player.ui.library


import com.asmr.player.translation.translatedPageText

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
import com.asmr.player.data.remote.scraper.resolveRecommendedWorkCoverUrl
import com.asmr.player.util.DlsiteWorkNo
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.cache.CacheImageModel
import com.asmr.player.util.DlsiteAntiHotlink
import android.webkit.CookieManager

import androidx.compose.ui.draw.clip
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.theme.AsmrTheme

@Composable
internal fun DlsiteRecommendationsBlocks(
    recommendations: DlsiteRecommendations,
    onOpenAlbumByRj: (String, DlsiteRecommendedWork?) -> Unit
) {
    if (recommendations.circleWorks.isEmpty() && 
        recommendations.sameVoiceWorks.isEmpty() && 
        recommendations.alsoBoughtWorks.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DlsiteRecommendationsBlock(
            title = "社团作品一览",
            items = recommendations.circleWorks,
            onOpenAlbumByRj = onOpenAlbumByRj
        )
        DlsiteRecommendationsBlock(
            title = "同声优作品",
            items = recommendations.sameVoiceWorks,
            onOpenAlbumByRj = onOpenAlbumByRj
        )
        DlsiteRecommendationsBlock(
            title = "购买了此作品的人也购买了这些作品",
            items = recommendations.alsoBoughtWorks,
            onOpenAlbumByRj = onOpenAlbumByRj
        )
    }
}

@Composable
internal fun AlbumDetailSectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colorScheme = AsmrTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.35.sp
                ),
                color = colorScheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                colorScheme.primary.copy(alpha = 0.82f),
                                colorScheme.primary.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        actions()
    }
}

@Composable
private fun DlsiteRecommendationsBlock(
    title: String,
    items: List<DlsiteRecommendedWork>,
    onOpenAlbumByRj: (String, DlsiteRecommendedWork?) -> Unit
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AlbumDetailSectionHeading(title = title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(
                items = items.take(30),
                key = { index, work ->
                    val rj = sanitizeWorkNo(work.rjCode).ifBlank { work.rjCode }
                    "recommendation:$rj:$index"
                }
            ) { _, w ->
                val rj = sanitizeWorkNo(w.rjCode).ifBlank { w.rjCode }
                DlsiteRecommendedWorkCard(work = w, displayRj = rj, onClick = { onOpenAlbumByRj(rj, w) })
            }
        }
    }
}

@Composable
internal fun DlsiteRecommendedWorkCard(
    work: DlsiteRecommendedWork,
    displayRj: String,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val coverModel = remember(work.coverUrl, displayRj) {
        resolveRecommendedWorkCoverUrl(displayRj, work.coverUrl)
    }
    val imageModel = remember(coverModel) {
        val s = coverModel.toString()
        val baseHeaders = if (s.startsWith("http", ignoreCase = true)) DlsiteAntiHotlink.headersForImageUrl(s) else emptyMap()
        if (baseHeaders.isNotEmpty()) {
            val cookieManager = CookieManager.getInstance()
            val cookie = cookieManager.getCookie(s).orEmpty().ifBlank { cookieManager.getCookie(NetworkHeaders.REFERER_DLSITE).orEmpty() }
            val headers = buildMap {
                putAll(baseHeaders)
                if (cookie.isNotBlank()) put("Cookie", cookie)
            }
            CacheImageModel(data = s, headers = headers, keyTag = "dlsite")
        } else {
            coverModel
        }
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp,
        color = colorScheme.surface.copy(alpha = 0.35f),
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                AsmrAsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = { m ->
                        Box(
                            modifier = m
                                .fillMaxSize()
                                .background(colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = null,
                                tint = colorScheme.textTertiary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    },
                )
                val ribbon = work.ribbon?.trim().orEmpty()
                if (ribbon.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colorScheme.primary.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = ribbon,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = translatedPageText(work.title).ifBlank { displayRj },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.textPrimary
                )
                Text(
                    text = displayRj,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.textTertiary
                )
            }
        }
    }
}

private fun sanitizeWorkNo(raw: String): String {
    return DlsiteWorkNo.extractWorkNo(raw)
}
