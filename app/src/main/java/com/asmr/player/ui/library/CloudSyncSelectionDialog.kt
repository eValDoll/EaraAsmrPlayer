package com.asmr.player.ui.library

import android.util.Log
import android.webkit.CookieManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.asmr.player.cache.CacheImageModel
import com.asmr.player.cache.ImageCacheEntryPoint
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncCandidate
import com.asmr.player.ui.common.AsmrShimmerPlaceholder
import com.asmr.player.ui.common.CvChipsSingleLine
import com.asmr.player.ui.common.DiscPlaceholder
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.DlsiteAntiHotlink
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val CLOUD_SYNC_SELECTION_DIALOG_TAG = "cloud_sync_selection_dialog"
internal const val CLOUD_SYNC_SELECTION_PROGRESS_TAG = "cloud_sync_selection_progress"
internal const val CLOUD_SYNC_SELECTION_LIST_TAG = "cloud_sync_selection_list"
internal val CLOUD_SYNC_SELECTION_SECTION_SPACING = 8.dp
internal val CLOUD_SYNC_SELECTION_ROW_SPACING = 8.dp
internal val CLOUD_SYNC_SELECTION_ROW_HEIGHT = 86.dp
internal val CLOUD_SYNC_SELECTION_LIST_HEIGHT =
    (CLOUD_SYNC_SELECTION_ROW_HEIGHT * 3) + (CLOUD_SYNC_SELECTION_ROW_SPACING * 2)

internal data class CloudSyncSelectionDialogState(
    val albumTitle: String,
    val candidates: List<DlsiteCloudSyncCandidate>,
    val currentPosition: Int? = null,
    val totalCount: Int? = null
) {
    val progressLabel: String?
        get() = if ((currentPosition ?: 0) > 0 && (totalCount ?: 0) > 0) {
            "待确认 ${currentPosition} / ${totalCount}"
        } else {
            null
        }
}

internal data class CloudSyncCandidateCoverSource(
    val label: String,
    val url: String
)

private data class CloudSyncCandidateCoverRequest(
    val label: String,
    val url: String,
    val model: Any
)

private enum class CloudSyncCandidateCoverLoadState {
    Loading,
    Success,
    Error
}

@Composable
internal fun CloudSyncSelectionDialog(
    state: CloudSyncSelectionDialogState,
    onSelect: (String) -> Unit,
    onCancel: () -> Unit,
    onIgnoreAll: (() -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 540.dp)
                .testTag(CLOUD_SYNC_SELECTION_DIALOG_TAG),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (colorScheme.isDark) 0.28f else 0.42f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.97f else 0.985f))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = colorScheme.primarySoft.copy(alpha = if (colorScheme.isDark) 0.16f else 0.88f),
                    border = BorderStroke(
                        1.dp,
                        colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.2f else 0.14f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "云同步候选：${state.candidates.size} 个疑似结果(点击对应作品以确认同步)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 16.sp
                            ),
                            color = colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.albumTitle.ifBlank { "当前专辑" },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 20.sp
                            ),
                            color = colorScheme.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f))
                Spacer(modifier = Modifier.height(CLOUD_SYNC_SELECTION_SECTION_SPACING))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CLOUD_SYNC_SELECTION_LIST_HEIGHT)
                        .testTag(CLOUD_SYNC_SELECTION_LIST_TAG),
                    verticalArrangement = Arrangement.spacedBy(CLOUD_SYNC_SELECTION_ROW_SPACING)
                ) {
                    items(state.candidates, key = { it.workno }) { candidate ->
                        CloudSyncSelectionCandidateRow(
                            candidate = candidate,
                            onClick = { onSelect(candidate.workno) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(CLOUD_SYNC_SELECTION_SECTION_SPACING))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (onIgnoreAll != null) {
                            OutlinedButton(
                                onClick = onIgnoreAll,
                                border = BorderStroke(
                                    1.dp,
                                    color = colorScheme.textSecondary.copy(alpha = 0.28f)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colorScheme.textSecondary
                                )
                            ) {
                                Text("忽略全部")
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        state.progressLabel?.let { progress ->
                            Text(
                                text = progress,
                                modifier = Modifier.testTag(CLOUD_SYNC_SELECTION_PROGRESS_TAG),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colorScheme.textSecondary
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        FilledTonalButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = colorScheme.primarySoft.copy(alpha = if (colorScheme.isDark) 0.24f else 1f),
                                contentColor = colorScheme.primary
                            )
                        ) {
                            Text(if (onIgnoreAll != null) "取消当前" else "取消")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudSyncSelectionCandidateRow(
    candidate: DlsiteCloudSyncCandidate,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val coverShape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(CLOUD_SYNC_SELECTION_ROW_HEIGHT)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.62f else 0.5f),
        border = BorderStroke(
            1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (colorScheme.isDark) 0.16f else 0.28f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(84.dp),
                shape = coverShape,
                color = colorScheme.surface.copy(alpha = 0.72f)
            ) {
                CloudSyncSelectionCandidateCover(
                    candidate = candidate,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 19.sp
                    ),
                    color = colorScheme.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (candidate.cv.isNotBlank()) {
                    CvChipsSingleLine(
                        cvText = candidate.cv,
                        modifier = Modifier.fillMaxWidth(),
                        showLabel = true
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudSyncSelectionCandidateCover(
    candidate: DlsiteCloudSyncCandidate,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, ImageCacheEntryPoint::class.java).imageCacheManager()
    }
    val requests = remember(candidate.coverUrl, candidate.workno) {
        buildCloudSyncCandidateCoverRequests(candidate)
    }
    var measuredSize by remember { mutableStateOf<IntSize?>(null) }
    var attemptIndex by remember(requests) { mutableIntStateOf(0) }
    var image by remember(requests) { mutableStateOf<ImageBitmap?>(null) }
    var loadState by remember(requests) {
        mutableStateOf(
            if (requests.isEmpty()) CloudSyncCandidateCoverLoadState.Error else CloudSyncCandidateCoverLoadState.Loading
        )
    }
    var failureLogged by remember(requests) { mutableStateOf(false) }

    LaunchedEffect(requests, measuredSize, attemptIndex) {
        val size = measuredSize ?: return@LaunchedEffect
        val request = requests.getOrNull(attemptIndex) ?: return@LaunchedEffect
        try {
            loadState = CloudSyncCandidateCoverLoadState.Loading
            image = null
            val loaded = withTimeoutOrNull(15_000) {
                manager.loadImage(model = request.model, size = size)
            } ?: throw IllegalStateException("cover load timeout")
            image = loaded
            loadState = CloudSyncCandidateCoverLoadState.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val nextIndex = attemptIndex + 1
            if (nextIndex < requests.size) {
                attemptIndex = nextIndex
            } else {
                loadState = CloudSyncCandidateCoverLoadState.Error
                if (!failureLogged) {
                    failureLogged = true
                    Log.w(
                        "CloudSyncSelectionDialog",
                        "cover load failed workno=${candidate.workno} sources=${
                            requests.joinToString(" | ") { summarizeCloudSyncCandidateCoverSource(it.label, it.url) }
                        }",
                        e
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier.onSizeChanged { size ->
            if (size.width > 0 && size.height > 0) {
                measuredSize = IntSize(size.width, size.height)
            }
        }
    ) {
        when {
            image != null -> {
                androidx.compose.foundation.Image(
                    painter = BitmapPainter(image!!),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center
                )
            }

            loadState == CloudSyncCandidateCoverLoadState.Loading -> {
                AsmrShimmerPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0
                )
            }

            else -> {
                DiscPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0
                )
            }
        }
    }
}

internal fun resolveCloudSyncCandidateCoverSources(candidate: DlsiteCloudSyncCandidate): List<CloudSyncCandidateCoverSource> {
    val canonical = dlsiteCoverUrlForWorkno(candidate.workno)
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let { CloudSyncCandidateCoverSource(label = "canonical", url = it) }
    val raw = normalizeCloudSyncCandidateRawCoverUrl(candidate.coverUrl)
        .takeIf { it.isNotBlank() }
        ?.let { CloudSyncCandidateCoverSource(label = "search", url = it) }
    return listOfNotNull(canonical, raw).distinctBy { it.url }
}

private fun buildCloudSyncCandidateCoverRequests(candidate: DlsiteCloudSyncCandidate): List<CloudSyncCandidateCoverRequest> {
    val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull()
    return resolveCloudSyncCandidateCoverSources(candidate).map { source ->
        val baseHeaders = if (source.url.startsWith("http", ignoreCase = true)) {
            DlsiteAntiHotlink.headersForImageUrl(source.url)
        } else {
            emptyMap()
        }
        val cookie = cookieManager?.getCookie(source.url).orEmpty()
            .ifBlank { cookieManager?.getCookie(NetworkHeaders.REFERER_DLSITE).orEmpty() }
        val headers = buildMap {
            putAll(baseHeaders)
            if (cookie.isNotBlank()) put("Cookie", cookie)
        }
        val model = if (headers.isEmpty()) {
            source.url
        } else {
            CacheImageModel(data = source.url, headers = headers, keyTag = "dlsite")
        }
        CloudSyncCandidateCoverRequest(
            label = source.label,
            url = source.url,
            model = model
        )
    }
}

internal fun normalizeCloudSyncCandidateRawCoverUrl(raw: String): String {
    val trimmed = raw.trim()
    return when {
        trimmed.isBlank() -> ""
        trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/modpub/") || trimmed.startsWith("/images2/") -> "https://img.dlsite.jp$trimmed"
        trimmed.startsWith("/") -> "https://www.dlsite.com$trimmed"
        else -> trimmed
    }
}

private fun summarizeCloudSyncCandidateCoverSource(label: String, url: String): String {
    val parsed = url.toHttpUrlOrNull()
    val summary = if (parsed != null) {
        "${parsed.host}${parsed.encodedPath}"
    } else {
        url.take(160)
    }
    return "$label:$summary"
}

private fun dlsiteCoverUrlForWorkno(raw: String): String {
    val clean = Regex("""RJ\d+""", RegexOption.IGNORE_CASE).find(raw)?.value?.uppercase().orEmpty()
    val digits = clean.removePrefix("RJ")
    val num = digits.toLongOrNull() ?: return ""
    val group = ((num + 999L) / 1000L) * 1000L
    val folder = "RJ${group.toString().padStart(digits.length, '0')}"
    return "https://img.dlsite.jp/modpub/images2/work/doujin/$folder/${clean}_img_main.jpg"
}
