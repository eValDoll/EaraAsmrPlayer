package com.asmr.player.ui.asmrone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.R
import com.asmr.player.data.remote.api.AsmrCloudPlaylist
import com.asmr.player.data.remote.api.AsmrCloudWork
import com.asmr.player.domain.model.Album
import com.asmr.player.i18n.appString
import com.asmr.player.ui.common.FlatActionDialog
import com.asmr.player.ui.common.FlatDialogAction
import com.asmr.player.ui.common.ThemedDropdownMenuPositionProvider
import com.asmr.player.ui.common.collectAsStateWhileActive
import com.asmr.player.ui.theme.AsmrTheme

fun cloudPlaylistName(playlist: AsmrCloudPlaylist): String = when (playlist.name) {
    AsmrCloudPlaylist.LIKED -> appString(R.string.cloud_liked)
    AsmrCloudPlaylist.MARKED -> appString(R.string.cloud_marked)
    else -> playlist.name.orEmpty()
}

@Composable
internal fun CloudSelector(value: String, options: List<String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
    selectedIndex: Int = options.indexOf(value), enabled: Boolean = true) {
    val colors = AsmrTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    val shape = RoundedCornerShape(8.dp)
    val density = LocalDensity.current
    val position = remember(density) { ThemedDropdownMenuPositionProvider(with(density) { 4.dp.roundToPx() }, with(density) { 12.dp.roundToPx() }) }
    Box(modifier) {
        Row(Modifier.fillMaxWidth().clip(shape).background(colors.surface)
            .border(1.dp, colors.textTertiary.copy(alpha = 0.22f), shape).clickable(enabled = enabled) { expanded = true }
            .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, Modifier.weight(1f), color = if (enabled) colors.textPrimary else colors.textTertiary, style = MaterialTheme.typography.bodyMedium)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = if (enabled) colors.textSecondary else colors.textTertiary)
        }
        if (expanded && enabled) Popup(popupPositionProvider = position, onDismissRequest = { expanded = false }, properties = PopupProperties(focusable = true)) {
            LazyColumn(Modifier.widthIn(min = 180.dp, max = 320.dp).heightIn(max = 360.dp).clip(shape)
                .background(colors.surface).border(1.dp, colors.textTertiary.copy(alpha = 0.22f), shape),
                contentPadding = PaddingValues(vertical = 4.dp)) {
                itemsIndexed(options) { index, label ->
                    val selected = index == selectedIndex
                    Row(Modifier.fillMaxWidth().background(if (selected) colors.primarySoft else colors.surface)
                        .clickable { expanded = false; onSelect(index) }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), color = if (selected) colors.primary else colors.textPrimary)
                        if (selected) Icon(Icons.Rounded.Check, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun CloudTextField(value: String, onValueChange: (String) -> Unit, label: String, password: Boolean = false, enabled: Boolean = true) {
    val colors = AsmrTheme.colorScheme
    OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        keyboardOptions = if (password) KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary,
            cursorColor = colors.primary, focusedBorderColor = colors.primary, unfocusedBorderColor = colors.textTertiary.copy(alpha = 0.35f),
            focusedLabelColor = colors.primary, unfocusedLabelColor = colors.textSecondary,
            disabledTextColor = colors.textTertiary, disabledLabelColor = colors.textTertiary,
            disabledBorderColor = colors.textTertiary.copy(alpha = 0.2f)))
}

@Composable
internal fun AsmrOneAccountContent(viewModel: AsmrOneViewModel, onLoggedIn: () -> Unit) {
    val isActive = true
    val account by viewModel.account.collectAsStateWhileActive(isActive)
    val busy by viewModel.busy.collectAsStateWhileActive(isActive)
    val operations by viewModel.operations.collectAsStateWhileActive(isActive)
    val colors = AsmrTheme.colorScheme
    var name by remember(account.name) { mutableStateOf(account.name) }
    var password by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { password = "" } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("asmr_account_section")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ASMR.one", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            if (busy || !account.ready) CircularProgressIndicator(Modifier.size(20.dp), color = colors.primary, strokeWidth = 2.dp)
            else if (account.authenticated) IconButton(onClick = viewModel::refresh) {
                Icon(if (operations.any { it.account == account.name && it.blocked }) Icons.Rounded.SyncProblem else Icons.Rounded.Sync,
                    appString(R.string.cloud_sync), tint = colors.primary)
            }
        }
        if (account.authenticated) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(account.name, Modifier.weight(1f), color = colors.textPrimary)
                TextButton(onClick = viewModel::logout, enabled = !busy, colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.textSecondary, disabledContentColor = colors.textTertiary)) { Text(appString(R.string.cloud_logout)) }
            }
        } else if (account.ready) {
            CloudTextField(name, { name = it }, appString(R.string.cloud_username), enabled = !busy)
            CloudTextField(password, { password = it }, appString(R.string.cloud_password), password = true, enabled = !busy)
            Button(onClick = { viewModel.login(name, password) { password = ""; onLoggedIn() } }, enabled = !busy && name.isNotBlank() && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary,
                    disabledContainerColor = colors.primarySoft, disabledContentColor = colors.textTertiary),
                shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.End).testTag("asmr_login")) {
                Text(appString(if (account.expired) R.string.cloud_login_again else R.string.cloud_login))
            }
        }
    }
}

@Composable
fun CloudSourceToggle(cloud: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AsmrTheme.colorScheme
    val source = appString(if (cloud) R.string.cloud_remote else R.string.cloud_local)
    IconButton(onClick = onToggle,
        modifier = modifier.testTag("asmr_source_toggle").semantics { stateDescription = source }) {
        Icon(
            imageVector = if (cloud) Icons.Rounded.CloudQueue else Icons.Rounded.Smartphone,
            contentDescription = appString(R.string.cloud_switch_source),
            tint = colors.primary,
        )
    }
}

@Composable
internal fun CloudLoginButton(onOpenLogin: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AsmrTheme.colorScheme
    Button(onClick = onOpenLogin, modifier = modifier.testTag("asmr_login_entry"), shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)) {
        Text(appString(R.string.cloud_login_to_site))
    }
}

@Composable
internal fun CloudMembershipContent(
    work: AsmrCloudWork,
    onOpenLogin: () -> Unit,
    viewModel: AsmrOneViewModel,
    modifier: Modifier = Modifier,
) {
    val account by viewModel.account.collectAsStateWhileActive(true)
    val loading by viewModel.membershipLoading.collectAsStateWhileActive(true)
    val memberships by viewModel.memberships.collectAsStateWhileActive(true)
    val resolvedWork by viewModel.membershipWork.collectAsStateWhileActive(true)
    val saving by viewModel.savingMemberships.collectAsStateWhileActive(true)
    val operations by viewModel.operations.collectAsStateWhileActive(true)
    val colors = AsmrTheme.colorScheme
    LaunchedEffect(work.id, work.source_id, account.name, account.authenticated) {
        if (account.authenticated) viewModel.openMembership(work)
    }
    DisposableEffect(Unit) { onDispose { viewModel.clearMembership() } }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!account.authenticated) {
            CloudLoginButton(onOpenLogin, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(work.title.orEmpty(), Modifier.weight(1f), color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                IconButton(onClick = { viewModel.openMembership(work) }, enabled = !loading) {
                    Icon(Icons.Rounded.Sync, appString(R.string.cloud_sync), tint = colors.primary)
                }
            }
            if (loading) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = colors.primary, strokeWidth = 2.dp)
            } else memberships.filter { it.user_name == account.name }.forEach { playlist ->
                val target = resolvedWork ?: work
                val checked = playlist.exist == true
                val isSaving = playlist.id in saving
                val pending = operations.firstOrNull { it.account == account.name && it.listId == playlist.id && it.workId == target.id }
                Row(Modifier.fillMaxWidth().clickable(enabled = target.id > 0 && !isSaving) { viewModel.setMembership(playlist, target, !checked) }
                    .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TriStateCheckbox(state = when (playlist.exist) {
                        true -> ToggleableState.On; false -> ToggleableState.Off; null -> ToggleableState.Indeterminate
                    }, onClick = { viewModel.setMembership(playlist, target, !checked) }, enabled = target.id > 0 && !isSaving,
                        colors = CheckboxDefaults.colors(checkedColor = colors.primary, uncheckedColor = colors.textSecondary,
                            checkmarkColor = colors.onPrimary, disabledUncheckedColor = colors.textTertiary,
                            disabledCheckedColor = colors.primarySoft, disabledIndeterminateColor = colors.primarySoft))
                    Text(cloudPlaylistName(playlist), color = colors.textPrimary, modifier = Modifier.weight(1f))
                    if (isSaving) CircularProgressIndicator(Modifier.padding(8.dp).size(20.dp), color = colors.primary, strokeWidth = 2.dp)
                    else if (pending != null) Icon(
                        if (pending.blocked) Icons.Rounded.SyncProblem else Icons.Rounded.Sync,
                        appString(if (pending.blocked) R.string.cloud_operation_failed else R.string.cloud_saved_pending),
                        tint = if (pending.blocked) colors.danger else colors.primary,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun CloudMembershipDialog(work: AsmrCloudWork, onDismiss: () -> Unit, viewModel: AsmrOneViewModel, onOpenLogin: () -> Unit) {
    FlatActionDialog(message = appString(R.string.cloud_add_to_playlist), onDismissRequest = onDismiss,
        actions = listOf(FlatDialogAction(appString(R.string.ui_close), onDismiss))) {
        CloudMembershipContent(work, { onDismiss(); onOpenLogin() }, viewModel, Modifier.heightIn(max = 360.dp))
    }
}

@Composable
fun AsmrOneAlbumSaveContent(album: Album, workId: Long?, onOpenLogin: () -> Unit, viewModel: AsmrOneViewModel = hiltViewModel()) {
    val work = remember(workId, album) { AsmrCloudWork(id = workId ?: album.asmrOneWorkId?.toLong() ?: 0,
        title = album.title, source_id = album.rjCode.ifBlank { album.workId }, mainCoverUrl = album.coverUrl) }
    CloudMembershipContent(work, onOpenLogin, viewModel, Modifier.fillMaxSize())
}
