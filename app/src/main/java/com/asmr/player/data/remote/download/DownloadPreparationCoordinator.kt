package com.asmr.player.data.remote.download

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
                if (needsCheck) messageManager.showInfo("正在检查源文件")
                val prepared = if (needsCheck) preflight.prepare(request, ::awaitConfirmation) else request
                if (prepared == null) return@launch
                when (val result = enqueueBatch(prepared)) {
                    is EnqueueDownloadBatchResult.Accepted -> {
                        accepted = true
                        messageManager.showInfo("正在加入下载队列（${result.itemCount}项）")
                    }
                    EnqueueDownloadBatchResult.DirectoryUnavailable -> messageManager.showError("下载目录不可用，请重新选择或重置为默认目录")
                    EnqueueDownloadBatchResult.TaskBlocked -> messageManager.showInfo("所选文件已在下载任务中")
                }
            } catch (_: TimeoutCancellationException) {
                messageManager.showError("源文件检查超时，请重试")
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                Log.w("JPDownload", "source file check failed", error)
                messageManager.showError("无法确认源文件状态，请稍后重试")
            } catch (error: Exception) {
                Log.e("JPDownload", "download preparation failed", error)
                messageManager.showError("创建下载任务失败，请重试")
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
