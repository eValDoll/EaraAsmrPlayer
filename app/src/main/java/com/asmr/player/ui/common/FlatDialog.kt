package com.asmr.player.ui.common

import com.asmr.player.i18n.appString
import com.asmr.player.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.asmr.player.ui.theme.AsmrTheme

enum class FlatDialogActionTone {
    Neutral,
    Primary,
    Danger,
}

data class FlatDialogAction(
    val text: String,
    val onClick: () -> Unit,
    val tone: FlatDialogActionTone = FlatDialogActionTone.Neutral,
    val enabled: Boolean = true,
    val leadingIcon: (@Composable () -> Unit)? = null,
)

@Composable
fun FlatActionDialog(
    message: String,
    actions: List<FlatDialogAction>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: (@Composable () -> Unit)? = null,
) {
    if (actions.isEmpty()) return

    val colorScheme = AsmrTheme.colorScheme
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Column(
            modifier = modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colorScheme.surface)
                .padding(top = 22.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                content?.invoke()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                actions.forEach { action ->
                    TextButton(
                        onClick = action.onClick,
                        enabled = action.enabled,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = when (action.tone) {
                                FlatDialogActionTone.Neutral -> colorScheme.textSecondary
                                FlatDialogActionTone.Primary -> colorScheme.primary
                                FlatDialogActionTone.Danger -> colorScheme.danger
                            },
                            disabledContentColor = colorScheme.textTertiary.copy(alpha = 0.55f),
                        ),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            action.leadingIcon?.let { icon ->
                                icon()
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = action.text,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlatTextFieldDialog(
    message: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = value.trim().isNotBlank(),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colorScheme = AsmrTheme.colorScheme
    FlatActionDialog(
        message = message,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        actions = listOf(
            FlatDialogAction(appString(R.string.ui_cancel), onDismissRequest),
            FlatDialogAction(
                text = confirmText,
                onClick = onConfirm,
                tone = FlatDialogActionTone.Primary,
                enabled = confirmEnabled,
            ),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colorScheme.background.copy(alpha = 0.8f)),
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = keyboardOptions,
                placeholder = {
                    Text(
                        text = placeholder,
                        color = colorScheme.textTertiary,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colorScheme.textPrimary,
                    unfocusedTextColor = colorScheme.textPrimary,
                    cursorColor = colorScheme.primary,
                ),
            )
        }
    }
}
