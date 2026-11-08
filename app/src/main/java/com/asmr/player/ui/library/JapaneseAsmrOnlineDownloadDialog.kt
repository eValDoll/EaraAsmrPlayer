package com.asmr.player.ui.library

import com.asmr.player.i18n.appString
import com.asmr.player.R

import androidx.compose.runtime.Composable
import com.asmr.player.ui.common.FlatActionDialog
import com.asmr.player.ui.common.FlatDialogAction
import com.asmr.player.ui.common.FlatDialogActionTone

@Composable
internal fun JapaneseAsmrOnlineDownloadDialog(
    missingFileCount: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    FlatActionDialog(
        message = if (missingFileCount == 1) appString(R.string.ui_the_source_file_is_missing_download_the_online) else
            appString(R.string.ui_source_files_for_tracks_are_missing_download_their, missingFileCount),
        actions = listOf(
            FlatDialogAction(appString(R.string.ui_cancel), onCancel),
            FlatDialogAction(appString(R.string.ui_download_online_audio), onConfirm, tone = FlatDialogActionTone.Primary),
        ),
        onDismissRequest = onCancel,
    )
}
