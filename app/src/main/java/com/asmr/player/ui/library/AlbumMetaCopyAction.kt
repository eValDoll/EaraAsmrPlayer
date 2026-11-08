package com.asmr.player.ui.library

import com.asmr.player.i18n.appString
import com.asmr.player.R

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.asmr.player.util.MessageManager

@Composable
internal fun rememberAlbumMetaCopyAction(
    messageManager: MessageManager,
): (String, String) -> Unit {
    val clipboard = LocalClipboardManager.current
    return remember(clipboard, messageManager) {
        { label: String, value: String ->
            val normalizedValue = value.trim()
            if (normalizedValue.isBlank()) {
                Unit
            } else {
                clipboard.setText(AnnotatedString(normalizedValue))
                messageManager.showSuccess(appString(R.string.ui_copied, label))
            }
        }
    }
}
