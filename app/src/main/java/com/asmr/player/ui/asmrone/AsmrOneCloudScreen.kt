package com.asmr.player.ui.asmrone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.R
import com.asmr.player.data.remote.api.AsmrCloudPlaylist
import com.asmr.player.data.remote.api.AsmrCloudWork
import com.asmr.player.data.repository.AsmrOneAccountRepository
import com.asmr.player.domain.model.Album
import com.asmr.player.i18n.appString
import com.asmr.player.ui.common.*
import com.asmr.player.ui.library.AlbumItem
import com.asmr.player.ui.theme.AsmrTheme
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

internal fun cloudPlaylistId(input: String): String? = runCatching {
    val value = input.trim()
    val raw = if (!value.contains("://")) value else {
        val uri = URI(value)
        require(uri.scheme == "https" && uri.host in setOf("asmr.one", "www.asmr.one", "asmr-100.com", "www.asmr-100.com",
            "asmr-200.com", "www.asmr-200.com", "asmr-300.com", "www.asmr-300.com"))
        require(uri.path == "/playlist" || uri.path.startsWith("/playlist/"))
        uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("id=") }
            ?.substringAfter('=')?.let { URLDecoder.decode(it, "UTF-8") } ?: uri.path.substringAfterLast('/')
    }
    UUID.fromString(raw).toString().also { require(it.equals(raw, ignoreCase = true)) }
}.getOrNull()

@Composable
fun AsmrOneCloudScreen(
    isActive: Boolean,
    onAlbumClick: (Album) -> Unit,
    scrollToTopSignal: Long = 0,
    onOpenLogin: () -> Unit = {},
    viewModel: AsmrOneViewModel = hiltViewModel()
) {
    val account by viewModel.account.collectAsStateWhileActive(isActive)
    val playlistSnapshot by viewModel.playlists.collectAsStateWhileActive(isActive)
    val playlists = playlistSnapshot.takeIf { it.account == account.name }?.items.orEmpty()
    val selectedId by viewModel.selectedId.collectAsStateWhileActive(isActive)
    val contentSnapshot by viewModel.content.collectAsStateWhileActive(isActive)
    val content = contentSnapshot.takeIf { it.account == account.name && it.listId == selectedId }
        ?: com.asmr.player.data.repository.AsmrCloudContent()
    val busy by viewModel.busy.collectAsStateWhileActive(isActive)
    val operations by viewModel.operations.collectAsStateWhileActive(isActive)
    val colors = AsmrTheme.colorScheme
    var creating by remember { mutableStateOf(false) }
    var subscribing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AsmrCloudPlaylist?>(null) }
    var deleting by remember { mutableStateOf<AsmrCloudPlaylist?>(null) }
    var membershipWork by remember { mutableStateOf<AsmrCloudWork?>(null) }
    var progressWork by remember { mutableStateOf<AsmrCloudWork?>(null) }
    val reviews = selectedId?.startsWith(AsmrOneAccountRepository.REVIEW_PREFIX) == true
    val selected = playlists.firstOrNull { it.id == selectedId }
    val state = key(selectedId) { rememberLazyListState() }
    BackHandler(enabled = isActive && selectedId != null) { viewModel.select(null) }
    LaunchedEffect(isActive, account.name, account.expired, selectedId) {
        if (isActive && account.authenticated) {
            if (selectedId == null) viewModel.refresh() else viewModel.load(selectedId!!)
        }
    }
    LaunchedEffect(scrollToTopSignal) { if (scrollToTopSignal != 0L) state.smoothScrollToTop() }
    LaunchedEffect(isActive, state) { if (!isActive) state.stopScroll(MutatePriority.PreventUserInput) }

    Column(Modifier.fillMaxSize().testTag("asmr_cloud_page")) {
        if (!account.authenticated) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.TopCenter) { CloudLoginButton(onOpenLogin) }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectedId == null) {
                    Row(
                        Modifier.weight(1f).heightIn(min = 48.dp)
                            .clickable { viewModel.select(AsmrOneAccountRepository.REVIEW_PREFIX + "all") }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.History, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Text(appString(R.string.cloud_listening_history), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    IconButton(onClick = { viewModel.select(null) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, appString(R.string.cloud_back), tint = colors.textPrimary)
                    }
                    Text(if (reviews) appString(R.string.cloud_listening_history) else selected?.let(::cloudPlaylistName).orEmpty(),
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (busy) CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), color = colors.primary, strokeWidth = 2.dp)
                else IconButton(onClick = viewModel::refresh) {
                    Icon(if (operations.any { it.account == account.name && it.blocked }) Icons.Rounded.SyncProblem else Icons.Rounded.Sync,
                        appString(R.string.cloud_sync), tint = colors.primary)
                }
                if (selectedId == null) {
                    IconButton(onClick = { subscribing = true }, enabled = !busy) {
                        Icon(Icons.Rounded.Link, appString(R.string.cloud_subscribe), tint = colors.textSecondary)
                    }
                    IconButton(onClick = { creating = true }, enabled = !busy) {
                        Icon(Icons.Rounded.Add, appString(R.string.cloud_create), tint = colors.primary)
                    }
                }
            }
            if (reviews) {
                val values = listOf("all") + AsmrOneAccountRepository.LISTENING_PROGRESS
                val labels = listOf(appString(R.string.cloud_all)) + progressLabels()
                CloudSelector(labels[values.indexOf(selectedId!!.removePrefix(AsmrOneAccountRepository.REVIEW_PREFIX)).coerceAtLeast(0)],
                    labels, { viewModel.select(AsmrOneAccountRepository.REVIEW_PREFIX + values[it]) },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }
            LazyColumn(state = state, modifier = Modifier.fillMaxSize(), flingBehavior = rememberCalmScrollableFlingBehavior(),
                contentPadding = PaddingValues(bottom = LocalBottomOverlayPadding.current + 88.dp)) {
                if (selectedId == null) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(Modifier.fillMaxWidth().clickable { viewModel.select(playlist.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            AsmrAsyncImage(model = playlist.mainCoverUrl, contentDescription = null,
                                modifier = Modifier.size(54.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop, placeholderCornerRadius = 12)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cloudPlaylistName(playlist), color = colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(appString(R.string.cloud_work_count, playlist.works_count), color = colors.textTertiary, style = MaterialTheme.typography.bodySmall)
                            }
                            if (playlist.user_name == account.name && !playlist.isSystem) {
                                IconButton(onClick = { renaming = playlist }, modifier = Modifier.size(34.dp), enabled = !busy) {
                                    Icon(Icons.Rounded.Edit, appString(R.string.cloud_rename), tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { deleting = playlist }, modifier = Modifier.size(34.dp), enabled = !busy) {
                                    Icon(Icons.Rounded.Delete, appString(R.string.cloud_delete), tint = colors.danger, modifier = Modifier.size(18.dp))
                                }
                            } else if (playlist.user_name != account.name) {
                                IconButton(onClick = { viewModel.unsubscribe(playlist) }, enabled = !busy) {
                                    Icon(Icons.Rounded.Favorite, appString(R.string.cloud_unsubscribe), tint = colors.primary)
                                }
                            }
                        }
                    }
                } else {
                    items(content.rows, key = { it.work.id }) { row ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AlbumItem(row.album, onClick = { onAlbumClick(row.album) }, modifier = Modifier.weight(1f),
                                showCollectedIndicator = false)
                            Column(Modifier.padding(end = 4.dp)) {
                                IconButton(onClick = { membershipWork = row.work }) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, appString(R.string.cloud_add_to_playlist), tint = colors.primary)
                                }
                                if (reviews) IconButton(onClick = { progressWork = row.work }, enabled = !busy) {
                                    Icon(Icons.Rounded.History, appString(R.string.cloud_listening_status), tint = colors.textSecondary)
                                } else if (selected?.user_name == account.name) IconButton(onClick = { viewModel.setMembership(selected, row.work, false) }) {
                                    Icon(Icons.Rounded.Remove, appString(R.string.cloud_remove_work), tint = colors.textSecondary)
                                }
                            }
                        }
                    }
                    if (content.rows.isEmpty() && !busy) item("empty") {
                        Text(appString(R.string.cloud_empty), Modifier.padding(24.dp), color = colors.textSecondary)
                    }
                    if (content.hasMore) item("more") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { viewModel.load(selectedId!!, content.nextPage) }, enabled = !busy,
                                colors = ButtonDefaults.textButtonColors(contentColor = colors.primary, disabledContentColor = colors.textTertiary)) {
                                Text(appString(R.string.cloud_load_more))
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) CloudCreateDialog(onDismiss = { creating = false }, onCreate = { name, privacy -> creating = false; viewModel.create(name, privacy) })
    renaming?.let { playlist ->
        var name by remember(playlist.id) { mutableStateOf(playlist.name.orEmpty()) }
        FlatTextFieldDialog(message = appString(R.string.cloud_rename), value = name, onValueChange = { name = it.take(100) },
            placeholder = appString(R.string.cloud_playlist_name), confirmText = appString(R.string.ui_save),
            onConfirm = { renaming = null; viewModel.rename(playlist, name) }, onDismissRequest = { renaming = null })
    }
    deleting?.let { playlist ->
        FlatActionDialog(message = appString(R.string.cloud_delete_confirm, cloudPlaylistName(playlist)), onDismissRequest = { deleting = null },
            actions = listOf(FlatDialogAction(appString(R.string.ui_cancel), { deleting = null }),
                FlatDialogAction(appString(R.string.ui_delete), { deleting = null; viewModel.delete(playlist) }, FlatDialogActionTone.Danger)))
    }
    if (subscribing) {
        var link by remember { mutableStateOf("") }
        FlatTextFieldDialog(message = appString(R.string.cloud_subscribe), value = link, onValueChange = { link = it },
            placeholder = appString(R.string.cloud_playlist_link), confirmText = appString(R.string.cloud_subscribe),
            confirmEnabled = cloudPlaylistId(link) != null,
            onConfirm = { cloudPlaylistId(link)?.let { subscribing = false; viewModel.subscribe(it) } }, onDismissRequest = { subscribing = false })
    }
    membershipWork?.let { work -> CloudMembershipDialog(work, { membershipWork = null }, viewModel, onOpenLogin) }
    progressWork?.let { work ->
        val labels = progressLabels()
        var index by remember(work.id) { mutableIntStateOf(AsmrOneAccountRepository.LISTENING_PROGRESS.indexOf(work.progress).coerceAtLeast(0)) }
        FlatActionDialog(message = appString(R.string.cloud_listening_status), onDismissRequest = { progressWork = null },
            actions = listOf(FlatDialogAction(appString(R.string.ui_cancel), { progressWork = null }),
                FlatDialogAction(appString(R.string.ui_save), { progressWork = null; viewModel.progress(work, AsmrOneAccountRepository.LISTENING_PROGRESS[index]) }, FlatDialogActionTone.Primary))) {
            CloudSelector(labels[index], labels, { index = it }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CloudCreateDialog(onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var privacy by remember { mutableIntStateOf(0) }
    val options = listOf(appString(R.string.cloud_private), appString(R.string.cloud_unlisted), appString(R.string.cloud_public))
    FlatActionDialog(message = appString(R.string.cloud_create), onDismissRequest = onDismiss,
        actions = listOf(FlatDialogAction(appString(R.string.ui_cancel), onDismiss),
            FlatDialogAction(appString(R.string.ui_create), { onCreate(name.trim(), privacy) }, FlatDialogActionTone.Primary, enabled = name.isNotBlank()))) {
        CloudTextField(name, { name = it.take(100) }, appString(R.string.cloud_playlist_name))
        CloudSelector(options[privacy], options, { privacy = it }, Modifier.fillMaxWidth())
    }
}

private fun progressLabels() = listOf(R.string.cloud_progress_marked, R.string.cloud_progress_listening,
    R.string.cloud_progress_listened, R.string.cloud_progress_replay, R.string.cloud_progress_postponed).map { appString(it) }
