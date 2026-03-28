package com.asmr.player.ui.library

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.cache.CacheImageModel
import com.asmr.player.data.remote.dlsite.DlsiteLanguageEdition
import com.asmr.player.ui.dlsite.DlsitePlayViewModel
import com.asmr.player.util.DlsiteAntiHotlink
import com.asmr.player.util.SmartSortKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import com.asmr.player.ui.common.rememberDominantColor
import com.asmr.player.ui.common.SubtitleStamp
import com.asmr.player.ui.common.DiscPlaceholder
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.AsmrShimmerPlaceholder
import com.asmr.player.ui.common.CvChipsFlow
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.collapsibleHeaderUiState
import com.asmr.player.ui.common.rememberCollapsibleHeaderState
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.util.Formatting
import com.asmr.player.util.MessageManager
import com.asmr.player.util.RemoteSubtitleSource

@Composable
internal fun AlbumDescription(album: Album) {
    val colorScheme = AsmrTheme.colorScheme
    val processedText = remember(album.description) {
        album.description
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
    val paragraphs = remember(processedText) {
        processedText.split(Regex("\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val defaultMaxLines = 10
    var expanded by rememberSaveable(album.id, album.rjCode, album.workId) { mutableStateOf(false) }
    val shouldCollapse = remember(paragraphs) { paragraphs.size > defaultMaxLines }
    val visible = remember(paragraphs, expanded, shouldCollapse) {
        if (!shouldCollapse || expanded) paragraphs else paragraphs.take(defaultMaxLines)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        color = colorScheme.surface.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "简介",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.textPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                if (shouldCollapse) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起" else "展开", color = colorScheme.primary)
                    }
                }
            }
            Divider(color = colorScheme.textTertiary.copy(alpha = 0.25f))
            if (paragraphs.isEmpty()) {
                Text("暂无介绍", color = colorScheme.textTertiary)
            } else {
                visible.forEach { p ->
                    val trimmed = p.trimStart()
                    val isBullet = trimmed.startsWith("・") || trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.startsWith("★")
                    if (isBullet) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colorScheme.primary.copy(alpha = 0.8f))
                            )
                            Text(
                                text = trimmed.trimStart('・', '-', '•', '★', ' '),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 22.sp,
                                color = colorScheme.textSecondary
                            )
                        }
                    } else {
                        Text(
                            text = p,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = colorScheme.textSecondary
                        )
                    }
                }
                if (shouldCollapse && !expanded) {
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
internal fun DlsiteRecommendationsBlocks(
    recommendations: DlsiteRecommendations,
    onOpenAlbumByRj: (String) -> Unit
) {
    if (recommendations.circleWorks.isEmpty() && 
        recommendations.sameVoiceWorks.isEmpty() && 
        recommendations.alsoBoughtWorks.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
private fun DlsiteRecommendationsBlock(
    title: String,
    items: List<DlsiteRecommendedWork>,
    onOpenAlbumByRj: (String) -> Unit
) {
    if (items.isEmpty()) return
    val colorScheme = AsmrTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.textPrimary
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items.take(30), key = { sanitizeRj(it.rjCode).ifBlank { it.rjCode } }) { w ->
                val rj = sanitizeRj(w.rjCode).ifBlank { w.rjCode }
                DlsiteRecommendedWorkCard(work = w, displayRj = rj, onClick = { onOpenAlbumByRj(rj) })
            }
        }
    }
}

@Composable
private fun DlsiteRecommendedWorkCard(
    work: DlsiteRecommendedWork,
    displayRj: String,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val coverModel = remember(work.coverUrl, displayRj) {
        work.coverUrl.takeIf { it.isNotBlank() } ?: dlsiteCoverUrlForRj(displayRj)
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
        shape = RoundedCornerShape(14.dp),
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
                                imageVector = Icons.Default.Image,
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
                    text = work.title.ifBlank { displayRj },
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

private fun sanitizeRj(raw: String): String {
    return Regex("""RJ\d+""", RegexOption.IGNORE_CASE).find(raw)?.value?.uppercase().orEmpty()
}

private fun dlsiteCoverUrlForRj(rj: String): String {
    val clean = sanitizeRj(rj)
    val digits = clean.removePrefix("RJ")
    val num = digits.toLongOrNull() ?: return ""
    val group = ((num + 999L) / 1000L) * 1000L
    val padded = group.toString().padStart(digits.length, '0')
    val folder = "RJ$padded"
    return "https://img.dlsite.jp/modpub/images2/work/doujin/$folder/${clean}_img_main.jpg"
}

@Composable
internal fun AlbumTracks(album: Album, onTrackClick: (Track) -> Unit) {
    val groupedTracks = album.getGroupedTracks()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val ids = remember(album.tracks) { album.tracks.map { it.id }.filter { it > 0L }.distinct() }
    val subtitleTrackIds by produceState(
        initialValue = emptySet<Long>(),
        key1 = ids
    ) {
        value = withContext(Dispatchers.IO) {
            if (ids.isEmpty()) emptySet() else AppDatabaseProvider.get(context).trackDao().getTrackIdsWithSubtitles(ids).toSet()
        }
    }
    val remoteSubtitleTrackIds by produceState(
        initialValue = emptySet<Long>(),
        key1 = ids
    ) {
        value = withContext(Dispatchers.IO) {
            if (ids.isEmpty()) emptySet() else AppDatabaseProvider.get(context).remoteSubtitleSourceDao()
                .getTrackIdsWithRemoteSources(ids)
                .toSet()
        }
    }
    
    if (groupedTracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无曲目")
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().thinScrollbar(listState),
            contentPadding = PaddingValues(bottom = LocalBottomOverlayPadding.current)
        ) {
            groupedTracks.forEach { (group, tracks) ->
                if (group.isNotEmpty()) {
                    item(
                        key = "group:$group",
                        contentType = "groupHeader"
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = group,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                itemsIndexed(
                    items = tracks,
                    key = { index, track ->
                        if (track.id > 0L) track.id else "${track.path}#$index"
                    },
                    contentType = { _, _ -> "trackRow" }
                ) { index, track ->
                    val showStamp = track.id > 0L && (subtitleTrackIds.contains(track.id) || remoteSubtitleTrackIds.contains(track.id))
                    TrackItem(track = track, showSubtitleStamp = showStamp, onClick = { onTrackClick(track) })
                    if (index < tracks.size - 1) {
                        HorizontalDivider(
                             modifier = Modifier.padding(horizontal = 16.dp),
                             thickness = 0.5.dp,
                             color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                         )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TrackItem(
    track: Track,
    showSubtitleStamp: Boolean = false,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    ListItem(
        headlineContent = { 
            Text(
                track.title, 
                maxLines = 2, 
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.textPrimary
            ) 
        },
        supportingContent = { 
            val isOnline = remember(track.path) { track.path.trim().startsWith("http", ignoreCase = true) }
            val durationText = Formatting.formatTrackSeconds(track.duration)
            Text(
                when {
                    isOnline && durationText.isNotBlank() -> "在线 · $durationText"
                    isOnline -> "在线"
                    durationText.isNotBlank() -> durationText
                    else -> "在线播放"
                },
                color = colorScheme.textTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colorScheme.primary)
        },
        trailingContent = {
            if (showSubtitleStamp) {
                SubtitleStamp(modifier = Modifier.padding(end = 8.dp))
            }
            if (onAddToPlaylist != null) {
                IconButton(onClick = onAddToPlaylist) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun OnlineTrackRow(
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    onAddToQueue: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onManageTags: (() -> Unit)?
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    ListItem(
        headlineContent = {
            Text(
                title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.textPrimary
            )
        },
        supportingContent = {
            Text(
                subtitle,
                color = colorScheme.textTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colorScheme.primary)
        },
        trailingContent = {
            val showMenu = onAddToQueue != null || onAddToPlaylist != null || onManageTags != null
            if (!showMenu) return@ListItem
            var expanded by rememberSaveable { mutableStateOf(false) }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                }
                MaterialTheme(
                    colorScheme = materialColorScheme.copy(
                        surface = dynamicContainerColor,
                        surfaceContainer = dynamicContainerColor
                    )
                ) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(dynamicContainerColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("播放") },
                            onClick = {
                                onPlay()
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colorScheme.primary)
                            }
                        )
                        if (onAddToQueue != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = 0.5.dp,
                                color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            DropdownMenuItem(
                                text = { Text("添加到播放队列") },
                                onClick = {
                                    onAddToQueue.invoke()
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                                }
                            )
                        }
                        if (onAddToPlaylist != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = 0.5.dp,
                                color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            DropdownMenuItem(
                                text = { Text("添加到我的列表") },
                                onClick = {
                                    onAddToPlaylist.invoke()
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                                }
                            )
                        }
                        if (onManageTags != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = 0.5.dp,
                                color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            DropdownMenuItem(
                                text = { Text("标签管理") },
                                onClick = {
                                    onManageTags.invoke()
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                                }
                            )
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onPlay)
    )
}

