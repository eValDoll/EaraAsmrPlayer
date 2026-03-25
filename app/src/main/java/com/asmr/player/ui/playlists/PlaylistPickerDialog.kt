package com.asmr.player.ui.playlists

import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.theme.AsmrTheme
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun PlaylistPickerScreen(
    windowSizeClass: WindowSizeClass,
    mediaId: String,
    uri: String,
    title: String,
    artist: String,
    artworkUri: String,
    albumId: Long = 0L,
    trackId: Long = 0L,
    rjCode: String = "",
    onBack: () -> Unit,
    embeddedInDialog: Boolean = false,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val userPlaylists = remember(playlists) { playlists.filter { it.category == "user" } }
    val colorScheme = AsmrTheme.colorScheme
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var createName by rememberSaveable { mutableStateOf("") }
    val canCreate = remember(createName) { createName.trim().isNotBlank() }

    val item = remember(mediaId, uri, title, artist, artworkUri, albumId, trackId, rjCode) {
        buildPlaylistAddMediaItem(
            mediaId = mediaId,
            uri = uri,
            title = title,
            artist = artist,
            artworkUri = artworkUri,
            albumId = albumId,
            trackId = trackId,
            rjCode = rjCode
        )
    }

    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = if (isCompact) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "选择列表",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.textPrimary
                )

                if (embeddedInDialog) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onBack,
                            colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
                        ) {
                            Text("关闭")
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("新建列表名称") }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    TextButton(
                        onClick = {
                            val trimmed = createName.trim()
                            if (trimmed.isBlank()) return@TextButton
                            viewModel.createPlaylist(trimmed)
                            createName = ""
                        },
                        enabled = canCreate,
                        colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
                    ) {
                        Text("创建")
                    }
                }
            }

            if (userPlaylists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    Text("暂无可选列表", color = colorScheme.textSecondary)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().thinScrollbar(listState),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(userPlaylists, key = { it.id }) { playlist ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colorScheme.surface.copy(alpha = 0.5f))
                                .clickable {
                                    scope.launch {
                                        viewModel.addItemToPlaylist(playlist.id, item)
                                    }
                                    onBack()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.padding(bottom = 12.dp)) }
                }
            }
        }
    }
}

private fun buildPlaylistAddMediaItem(
    mediaId: String,
    uri: String,
    title: String,
    artist: String,
    artworkUri: String,
    albumId: Long = 0L,
    trackId: Long = 0L,
    rjCode: String = ""
): androidx.media3.common.MediaItem {
    android.util.Log.d(
        "PlaylistPicker",
        "buildPlaylistAddMediaItem - mediaId: $mediaId, uri: $uri, title: $title, artist: $artist, artworkUri: $artworkUri, albumId: $albumId, trackId: $trackId, rjCode: $rjCode"
    )

    val metadata = androidx.media3.common.MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setArtworkUri(artworkUri.takeIf { it.isNotBlank() }?.toUri())
        .setExtras(
            android.os.Bundle().apply {
                if (albumId > 0L) putLong("album_id", albumId)
                if (trackId > 0L) putLong("track_id", trackId)
                if (rjCode.isNotBlank()) putString("rj_code", rjCode)
            }
        )
        .build()

    val mimeType = guessMimeType(uri)
    val mediaItem = androidx.media3.common.MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(toPlayableUriForPlaylist(uri))
        .setMimeType(mimeType)
        .setMediaMetadata(metadata)
        .build()

    android.util.Log.d(
        "PlaylistPicker",
        "buildPlaylistAddMediaItem - created MediaItem with extras: ${mediaItem.mediaMetadata.extras}, artworkUri: ${mediaItem.mediaMetadata.artworkUri}"
    )

    return mediaItem
}

private fun toPlayableUriForPlaylist(raw: String): Uri {
    val trimmed = raw.trim()
    if (trimmed.startsWith("content://", ignoreCase = true)) {
        return Uri.parse(repairDocumentUri(trimmed))
    }
    if (
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("file://", ignoreCase = true)
    ) {
        return Uri.parse(trimmed)
    }
    if (trimmed.startsWith("/")) {
        return Uri.fromFile(File(trimmed))
    }
    return Uri.parse(trimmed)
}

private fun repairDocumentUri(raw: String): String {
    val u = runCatching { Uri.parse(raw) }.getOrNull() ?: return raw
    val segs = u.pathSegments ?: return raw
    val docIndex = segs.indexOf("document")
    if (docIndex < 0) return raw
    if (segs.size <= docIndex + 2) return raw
    val docId = segs.subList(docIndex + 1, segs.size).joinToString("/")
    val encodedDocId = Uri.encode(docId)
    val encodedPath = "/" + segs.take(docIndex + 1).joinToString("/") + "/" + encodedDocId
    return u.buildUpon().encodedPath(encodedPath).build().toString()
}

private fun guessMimeType(path: String): String? {
    val trimmed = path.trim()
    val ext = trimmed.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        else -> null
    }
}
