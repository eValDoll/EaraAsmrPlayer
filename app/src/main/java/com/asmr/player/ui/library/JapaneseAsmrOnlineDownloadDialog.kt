package com.asmr.player.ui.library

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
        message = if (missingFileCount == 1) "源文件已丢失，是否下载在线音源？" else
            "其中 $missingFileCount 个音频的源文件已丢失，是否下载这些音频的在线音源？",
        actions = listOf(
            FlatDialogAction("取消", onCancel),
            FlatDialogAction("下载在线音源", onConfirm, tone = FlatDialogActionTone.Primary),
        ),
        onDismissRequest = onCancel,
    )
}
