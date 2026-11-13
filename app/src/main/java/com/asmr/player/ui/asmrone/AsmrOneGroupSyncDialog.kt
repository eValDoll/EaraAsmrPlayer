package com.asmr.player.ui.asmrone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asmr.player.R
import com.asmr.player.data.local.db.dao.AlbumGroupStatsRow
import com.asmr.player.i18n.appString
import com.asmr.player.ui.common.FlatActionDialog
import com.asmr.player.ui.common.FlatDialogAction
import com.asmr.player.ui.common.FlatDialogActionTone
import com.asmr.player.ui.common.collectAsStateWhileActive
import com.asmr.player.ui.theme.AsmrTheme

@Composable
internal fun AsmrOneGroupSyncDialog(
    group: AlbumGroupStatsRow,
    viewModel: AsmrOneViewModel,
    onDismiss: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val account by viewModel.account.collectAsStateWhileActive(true)
    val snapshot by viewModel.playlists.collectAsStateWhileActive(true)
    val busy by viewModel.busy.collectAsStateWhileActive(true)
    val owned = snapshot.takeIf { it.account == account.name }?.items.orEmpty().filter { it.user_name == account.name }
    var targetId by remember(group.id, account.name) { mutableStateOf<String?>(null) }
    var name by remember(group.id, account.name) { mutableStateOf(group.name.take(100)) }
    var chosen by remember(group.id, account.name) { mutableStateOf(false) }
    val target = owned.firstOrNull { it.id == targetId }
    val options = listOf(appString(R.string.cloud_group_new_list)) + owned.map(::cloudPlaylistName)
    val selectedIndex = if (targetId == null) 0 else owned.indexOfFirst { it.id == targetId } + 1
    LaunchedEffect(group.id, account.name, account.authenticated) {
        if (account.authenticated) viewModel.loadSyncTargets()
    }
    LaunchedEffect(owned, chosen) {
        if (!chosen) targetId = owned.singleOrNull { it.name == group.name }?.id
    }
    FlatActionDialog(
        message = appString(R.string.cloud_sync_group_title, group.name),
        onDismissRequest = onDismiss,
        actions = listOf(
            FlatDialogAction(appString(R.string.ui_cancel), onDismiss),
            FlatDialogAction(appString(R.string.cloud_sync), {
                viewModel.syncGroup(group.id, name, target, onDismiss)
            }, FlatDialogActionTone.Primary, enabled = account.authenticated && !busy &&
                (if (targetId == null) name.isNotBlank() else target != null)),
        ),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!account.ready) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(24.dp),
                    color = AsmrTheme.colorScheme.primary, strokeWidth = 2.dp)
            } else if (!account.authenticated) {
                CloudLoginButton({ onDismiss(); onOpenLogin() }, Modifier.align(Alignment.CenterHorizontally))
            } else {
                CloudSelector(options[selectedIndex.coerceIn(options.indices)], options, {
                    chosen = true
                    targetId = if (it == 0) null else owned[it - 1].id
                }, Modifier.fillMaxWidth(), selectedIndex = selectedIndex, enabled = !busy)
                if (targetId == null) CloudTextField(name, { chosen = true; name = it.take(100) }, appString(R.string.cloud_playlist_name), enabled = !busy)
                if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(24.dp),
                    color = AsmrTheme.colorScheme.primary, strokeWidth = 2.dp)
            }
        }
    }
}
