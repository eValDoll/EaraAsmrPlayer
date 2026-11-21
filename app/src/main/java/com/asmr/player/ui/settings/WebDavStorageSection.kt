package com.asmr.player.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.asmr.player.R
import com.asmr.player.nas.LibraryWorkspace
import com.asmr.player.nas.WebDavClient
import com.asmr.player.nas.WebDavHttpException
import com.asmr.player.nas.WebDavLocation
import com.asmr.player.ui.common.FlatActionDialog
import com.asmr.player.ui.common.FlatDialogAction
import com.asmr.player.ui.common.FlatDialogActionTone
import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WebDavStorageSection(onSwitch: (WebDavLocation?, String) -> Unit) {
    val colors = AsmrTheme.colorScheme
    var edit by remember { mutableStateOf(false) }
    Text(stringResource(R.string.storage_mode), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to stringResource(R.string.storage_local), true to "NAS · WebDAV").forEach { (nas, label) ->
            FilterChip(
                selected = nas == LibraryWorkspace.nas,
                onClick = { if (nas) edit = true else if (LibraryWorkspace.nas) onSwitch(null, "") },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surface, labelColor = colors.textSecondary,
                    selectedContainerColor = colors.primarySoft, selectedLabelColor = colors.primaryStrong,
                ),
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = nas == LibraryWorkspace.nas,
                    borderColor = colors.textTertiary, selectedBorderColor = colors.primaryStrong),
            )
        }
    }
    if (edit) WebDavConnectionDialog(onDismiss = { edit = false }, onSwitch = { location, password ->
        edit = false
        onSwitch(location, password)
    })
}

private class FolderListing(val client: WebDavClient, val password: String, val path: String, val folders: List<String>)

@Composable
private fun WebDavConnectionDialog(onDismiss: () -> Unit, onSwitch: (WebDavLocation, String) -> Unit) {
    val context = LocalContext.current
    val colors = AsmrTheme.colorScheme
    val scope = rememberCoroutineScope()
    val saved = remember { LibraryWorkspace.savedLocation(context) }
    var address by remember { mutableStateOf(saved?.root?.toString().orEmpty()) }
    var username by remember { mutableStateOf(saved?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var listing by remember { mutableStateOf<FolderListing?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun browse(path: String, previous: FolderListing? = null) {
        busy = true
        scope.launch {
            try {
                listing = withContext(Dispatchers.IO) {
                    val location = previous?.client?.location ?: WebDavLocation(address, username.trim())
                    val secret = previous?.password ?: if (password.isEmpty() && location.id == saved?.id)
                        LibraryWorkspace.savedPassword(context, location) else password
                    val client = previous?.client ?: WebDavClient(location, secret)
                    FolderListing(client, secret, path, client.list(path)
                        .filter { it.directory && !it.name.startsWith('.') }.map { it.path }.sorted())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val status = (error as? WebDavHttpException)?.message
                Toast.makeText(context, status ?: context.getString(R.string.webdav_connection_failed), Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }
    val current = listing
    FlatActionDialog(
        message = "NAS · WebDAV", onDismissRequest = onDismiss,
        actions = listOf(
            FlatDialogAction(stringResource(R.string.ui_cancel), onDismiss),
            FlatDialogAction(
                stringResource(if (current == null) R.string.webdav_browse else R.string.webdav_use_folder),
                {
                    if (current == null) browse("") else {
                        val selected = WebDavLocation(current.client.location.url(current.path).toString(), current.client.location.username)
                        onSwitch(selected, current.password)
                    }
                }, FlatDialogActionTone.Primary, enabled = !busy && address.isNotBlank(),
            ),
        ),
    ) {
        if (current == null) {
            val fields = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary,
                focusedBorderColor = colors.primaryStrong, unfocusedBorderColor = colors.textTertiary,
                focusedLabelColor = colors.primaryStrong, unfocusedLabelColor = colors.textSecondary, cursorColor = colors.primaryStrong,
            )
            OutlinedTextField(address, { address = it }, singleLine = true, enabled = !busy, colors = fields,
                label = { Text(stringResource(R.string.webdav_address)) })
            OutlinedTextField(username, { username = it }, singleLine = true, enabled = !busy, colors = fields,
                label = { Text(stringResource(R.string.ui_username)) })
            OutlinedTextField(password, { password = it }, singleLine = true, enabled = !busy, colors = fields,
                visualTransformation = PasswordVisualTransformation(), label = { Text(stringResource(R.string.ui_password)) })
        } else {
            Text(current.client.location.url(current.path).toString(), color = colors.textSecondary)
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                item {
                    Text("..", Modifier.fillMaxWidth().clickable(enabled = !busy) {
                        if (current.path.isEmpty()) listing = null else browse(current.path.substringBeforeLast('/', ""), current)
                    }.padding(12.dp), color = colors.primaryStrong)
                }
                items(current.folders, key = { it }) { folder ->
                    Text(folder.substringAfterLast('/'), Modifier.fillMaxWidth().clickable(enabled = !busy) {
                        browse(folder, current)
                    }.padding(12.dp), color = colors.textPrimary)
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primaryStrong, trackColor = colors.primarySoft)
    }
}
