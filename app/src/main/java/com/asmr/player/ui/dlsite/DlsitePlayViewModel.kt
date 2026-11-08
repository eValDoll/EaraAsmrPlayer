package com.asmr.player.ui.dlsite

import com.asmr.player.i18n.appString
import com.asmr.player.R

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
                    messageManager.showError(appString(R.string.ui_the_download_directory_is_unavailable_please_select_again))
                }
                EnqueueDownloadBatchResult.TaskBlocked -> {
                    messageManager.showInfo(appString(R.string.ui_there_is_already_a_download_task_for_the))
                }
            }
        }
    }
}
