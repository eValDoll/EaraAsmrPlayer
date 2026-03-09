package com.asmr.player.ui.dlsite

import android.content.Context
import androidx.lifecycle.ViewModel
import com.asmr.player.data.remote.download.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DlsitePlayViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager
) : ViewModel() {
    fun enqueueDownload(rjCode: String, url: String, suggestedFileName: String?) {
        val rj = rjCode.trim().ifBlank { "dlsite" }
        val baseDir = File(context.getExternalFilesDir(null), "albums")
        val targetDir = File(baseDir, rj)
        val fileName = (suggestedFileName?.trim().takeIf { !it.isNullOrBlank() } ?: "download.bin")
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
        downloadManager.enqueueDownload(
            url = url,
            fileName = fileName,
            targetDir = targetDir.absolutePath,
            taskRootDir = targetDir.absolutePath,
            relativePath = fileName,
            tags = listOf("album:$rj")
        )
    }
}
