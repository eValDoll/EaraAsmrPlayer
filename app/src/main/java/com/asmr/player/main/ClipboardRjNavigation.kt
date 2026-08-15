package com.asmr.player

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.ui.common.FlatActionDialog
import com.asmr.player.ui.common.FlatDialogAction
import com.asmr.player.ui.common.FlatDialogActionTone
import com.asmr.player.util.DlsiteWorkNo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun extractClipboardRjCode(text: CharSequence?): String {
    return DlsiteWorkNo.extractWorkNo(text?.toString().orEmpty())
}

private data class ClipboardSnapshot(
    val text: String,
    val eventKey: String
)

private data class ClipboardRjPrompt(
    val rjCode: String,
    val eventKey: String
)

internal fun clipboardEventKey(
    text: String,
    timestampMillis: Long,
    legacyChangeGeneration: Long = 0L
): String {
    val contentMarker = "${text.length}:${text.hashCode()}"
    return if (timestampMillis > 0L) {
        "timestamp:$timestampMillis:$contentMarker"
    } else {
        "legacy:$legacyChangeGeneration:$contentMarker"
    }
}

internal fun shouldShowClipboardRjPrompt(
    detectedRj: String,
    eventKey: String,
    lastHandledEventKey: String?,
    currentPromptEventKey: String?
): Boolean {
    if (detectedRj.isBlank() || eventKey.isBlank()) return false
    return eventKey != lastHandledEventKey && eventKey != currentPromptEventKey
}

private fun readPrimaryClipboardSnapshot(
    clipboard: ClipboardManager,
    legacyChangeGeneration: Long
): ClipboardSnapshot? {
    val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return null
    if (clip.itemCount == 0) return null
    val text = clip.getItemAt(0).text?.toString().orEmpty()
    val timestampMillis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        clip.description.timestamp
    } else {
        0L
    }
    return ClipboardSnapshot(
        text = text,
        eventKey = clipboardEventKey(text, timestampMillis, legacyChangeGeneration)
    )
}

@Composable
internal fun ClipboardRjNavigationPrompt(
    enabled: Boolean,
    settingsDataStore: SettingsDataStore,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }
    val rootView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    var prompt by remember { mutableStateOf<ClipboardRjPrompt?>(null) }
    var lastHandledEventThisSession by remember { mutableStateOf<String?>(null) }
    var shouldInspectOnResume by remember { mutableStateOf(true) }
    var inspectionJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(lifecycleOwner, clipboard, rootView, settingsDataStore, enabled) {
        var legacyExternalChangeGeneration = 0L

        fun markEventHandled(eventKey: String) {
            if (eventKey.isBlank()) return
            lastHandledEventThisSession = eventKey
            scope.launch {
                settingsDataStore.setLastHandledClipboardEvent(eventKey)
            }
        }

        fun inspectClipboard() {
            val clipboardManager = clipboard ?: return
            val snapshot = readPrimaryClipboardSnapshot(
                clipboard = clipboardManager,
                legacyChangeGeneration = legacyExternalChangeGeneration
            ) ?: return
            val detectedRj = extractClipboardRjCode(snapshot.text)
            if (!shouldShowClipboardRjPrompt(
                    detectedRj = detectedRj,
                    eventKey = snapshot.eventKey,
                    lastHandledEventKey = lastHandledEventThisSession,
                    currentPromptEventKey = prompt?.eventKey
                )
            ) {
                return
            }

            inspectionJob?.cancel()
            inspectionJob = scope.launch {
                val persistedEventKey = settingsDataStore.lastHandledClipboardEvent.first()
                if (!shouldShowClipboardRjPrompt(
                        detectedRj = detectedRj,
                        eventKey = snapshot.eventKey,
                        lastHandledEventKey = persistedEventKey,
                        currentPromptEventKey = prompt?.eventKey
                    ) || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    return@launch
                }

                lastHandledEventThisSession = snapshot.eventKey
                prompt = ClipboardRjPrompt(
                    rjCode = detectedRj,
                    eventKey = snapshot.eventKey
                )
                inspectionJob = null
                settingsDataStore.setLastHandledClipboardEvent(snapshot.eventKey)
            }
        }

        val observeClipboardWhileBackground = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
        var observingClipboard = false
        val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            inspectionJob?.cancel()
            inspectionJob = null
            val isAppForeground = rootView.hasWindowFocus() &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (isAppForeground) {
                clipboard
                    ?.let { readPrimaryClipboardSnapshot(it, legacyExternalChangeGeneration) }
                    ?.eventKey
                    ?.let(::markEventHandled)
            } else {
                legacyExternalChangeGeneration += 1L
                shouldInspectOnResume = true
            }
        }
        fun startObservingClipboard() {
            if (clipboard == null || observingClipboard) return
            clipboard.addPrimaryClipChangedListener(clipboardListener)
            observingClipboard = true
        }
        fun stopObservingClipboard() {
            if (clipboard == null || !observingClipboard) return
            clipboard.removePrimaryClipChangedListener(clipboardListener)
            observingClipboard = false
        }

        fun inspectAfterExternalEntryIfReady() {
            if (!enabled ||
                !shouldInspectOnResume ||
                !rootView.hasWindowFocus() ||
                !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                return
            }
            shouldInspectOnResume = false
            inspectClipboard()
        }

        val windowFocusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus &&
                enabled &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                inspectAfterExternalEntryIfReady()
                startObservingClipboard()
            } else {
                if (!hasFocus && enabled) {
                    shouldInspectOnResume = true
                    inspectionJob?.cancel()
                    inspectionJob = null
                }
                if (!observeClipboardWhileBackground) {
                    stopObservingClipboard()
                }
            }
        }
        val viewTreeObserver = rootView.viewTreeObserver
        viewTreeObserver.addOnWindowFocusChangeListener(windowFocusListener)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    shouldInspectOnResume = true
                    inspectionJob?.cancel()
                    inspectionJob = null
                    if (!observeClipboardWhileBackground) {
                        stopObservingClipboard()
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    shouldInspectOnResume = true
                    inspectionJob?.cancel()
                }

                Lifecycle.Event.ON_RESUME -> {
                    inspectAfterExternalEntryIfReady()
                    if (rootView.hasWindowFocus()) {
                        startObservingClipboard()
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inspectAfterExternalEntryIfReady()
        if (observeClipboardWhileBackground ||
            (rootView.hasWindowFocus() && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        ) {
            startObservingClipboard()
        }

        onDispose {
            stopObservingClipboard()
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.removeOnWindowFocusChangeListener(windowFocusListener)
            } else {
                rootView.viewTreeObserver.removeOnWindowFocusChangeListener(windowFocusListener)
            }
            lifecycleOwner.lifecycle.removeObserver(observer)
            inspectionJob?.cancel()
            if (enabled && prompt == null) {
                shouldInspectOnResume = true
            }
        }
    }

    prompt?.takeIf { enabled }?.let { currentPrompt ->
        val rjCode = currentPrompt.rjCode
        FlatActionDialog(
            message = "检测到剪贴板中的 $rjCode，是否快速跳转至该作品的专辑详情？",
            onDismissRequest = { prompt = null },
            actions = listOf(
                FlatDialogAction(
                    text = "取消",
                    onClick = { prompt = null }
                ),
                FlatDialogAction(
                    text = "立即跳转",
                    tone = FlatDialogActionTone.Primary,
                    onClick = {
                        prompt = null
                        currentOnNavigate(rjCode)
                    }
                )
            )
        )
    }
}
