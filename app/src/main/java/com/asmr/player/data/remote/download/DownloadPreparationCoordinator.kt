package com.asmr.player.data.remote.download

import com.asmr.player.i18n.appString
import com.asmr.player.R

import android.util.Log
import com.asmr.player.util.MessageManager
import com.asmr.player.util.ChapterMediaReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadPreparationCoordinator internal constructor(
    private val preflight: JapaneseAsmrDownloadPreflight,
    private val messageManager: MessageManager,
    private val enqueueBatch: suspend (DownloadBatchRequest) -> EnqueueDownloadBatchResult,
    private val scope: CoroutineScope,
    private val confirmationDispatcher: CoroutineDispatcher,
) {
    @Inject constructor(
        preflight: JapaneseAsmrDownloadPreflight,
        downloadManager: DownloadManager,
        messageManager: MessageManager,
    ) : this(preflight, messageManager, downloadManager::enqueueBatch,
        CoroutineScope(SupervisorJob() + Dispatchers.IO), Dispatchers.Main.immediate)

    private val preparing = AtomicBoolean(false)
    private val _onlineAudioConfirmation = MutableStateFlow<List<String>?>(null)
    val onlineAudioConfirmation = _onlineAudioConfirmation.asStateFlow()
    private var pendingConfirmation: CompletableDeferred<Boolean>? = null

    fun enqueue(request: DownloadBatchRequest, onFinished: (Boolean) -> Unit = {}) {
        scope.launch {
            val needsCheck = request.items.any { ChapterMediaReference.parse(it.url)?.isHls == true }
            if (needsCheck && !preparing.compareAndSet(false, true)) {
                withContext(confirmationDispatcher) { onFinished(false) }
                return@launch
            }
            var accepted = false
            try {
                if (needsCheck) messageManager.showInfo(appString(R.string.ui_checking_source_files))
                val prepared = if (needsCheck) preflight.prepare(request, ::awaitConfirmation) else request
                if (prepared == null) return@launch
                when (val result = enqueueBatch(prepared)) {
                    is EnqueueDownloadBatchResult.Accepted -> {
                        accepted = true
                        messageManager.showInfo(appString(R.string.ui_joining_download_queue_item, result.itemCount))
                    }
                    EnqueueDownloadBatchResult.DirectoryUnavailable -> messageManager.showError(appString(R.string.ui_the_download_directory_is_unavailable_please_select_again))
                    EnqueueDownloadBatchResult.TaskBlocked -> messageManager.showInfo(appString(R.string.ui_the_selected_files_are_already_in_the_download))
                }
            } catch (_: TimeoutCancellationException) {
                messageManager.showError(appString(R.string.ui_source_file_check_timed_out_try_again))
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                Log.w("JPDownload", "source file check failed", error)
                messageManager.showError(appString(R.string.ui_unable_to_check_the_source_file_try_again))
            } catch (error: Exception) {
                Log.e("JPDownload", "download preparation failed", error)
                messageManager.showError(appString(R.string.ui_unable_to_create_download_try_again))
            } finally {
                if (needsCheck) preparing.set(false)
                withContext(NonCancellable + confirmationDispatcher) { onFinished(accepted) }
            }
        }
    }

    private suspend fun awaitConfirmation(files: List<String>): Boolean {
        val answer = CompletableDeferred<Boolean>()
        withContext(confirmationDispatcher) {
            pendingConfirmation = answer
            _onlineAudioConfirmation.value = files
        }
        return try {
            answer.await()
        } finally {
            withContext(NonCancellable + confirmationDispatcher) {
                if (pendingConfirmation === answer) {
                    pendingConfirmation = null
                    _onlineAudioConfirmation.value = null
                }
            }
        }
    }

    fun confirmOnlineAudio() {
        pendingConfirmation?.complete(true)
    }

    fun cancelOnlineAudio() {
        pendingConfirmation?.complete(false)
    }
}
