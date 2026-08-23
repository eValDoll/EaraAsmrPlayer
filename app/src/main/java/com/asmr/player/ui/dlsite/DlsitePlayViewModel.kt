package com.asmr.player.ui.dlsite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.remote.download.DownloadBatchRequest
import com.asmr.player.data.remote.download.EnqueueDownloadBatchResult
import com.asmr.player.data.remote.download.DownloadManager
import com.asmr.player.data.remote.download.RelativeDownloadItem
import com.asmr.player.util.MessageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel
class DlsitePlayViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
    private val messageManager: MessageManager,
) : ViewModel() {
    fun enqueueDownload(rjCode: String, url: String, suggestedFileName: String?) {
        val rj = rjCode.trim().ifBlank { "dlsite" }
        val fileName = (suggestedFileName?.trim().takeIf { !it.isNullOrBlank() } ?: "download.bin")
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
        viewModelScope.launch(Dispatchers.IO) {
            when (
                downloadManager.enqueueBatch(
                    DownloadBatchRequest(
                        albumDirectoryName = rj,
                        logicalTaskKey = "album:$rj",
                        items = listOf(RelativeDownloadItem(url = url, relativePath = fileName)),
                        albumWorkId = rj,
                        albumRjCode = rj,
                    ),
                )
            ) {
                is EnqueueDownloadBatchResult.Accepted -> Unit
                EnqueueDownloadBatchResult.DirectoryUnavailable -> {
                    messageManager.showError("下载目录不可用，请重新选择或重置为默认目录")
                }
                EnqueueDownloadBatchResult.TaskBlocked -> {
                    messageManager.showInfo("相同作品已有下载任务正在处理")
                }
            }
        }
    }
}
