package com.asmr.player.ui.common

import com.asmr.player.i18n.appString
import com.asmr.player.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.asmr.player.data.settings.AppProxyMode
import com.asmr.player.data.settings.NetworkRouteSettings
import com.asmr.player.data.settings.isValidManualProxy
import com.asmr.player.data.settings.normalizeDnsServerAddress
import com.asmr.player.ui.theme.AsmrTheme

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NetworkRouteSettingsSection(
    settings: NetworkRouteSettings,
    onUseSystemProxy: () -> Unit,
    onAdvancedProxyApplied: (AppProxyMode, String, Int, Boolean, String, String) -> Unit,
    onUseSystemDns: () -> Unit,
    onCustomDnsServerApplied: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = colorScheme.surface.copy(alpha = 0.18f),
        labelColor = colorScheme.textSecondary,
        selectedContainerColor = colorScheme.primarySoft,
        selectedLabelColor = if (colorScheme.isDark) {
            colorScheme.onPrimaryContainer
        } else {
            colorScheme.primaryStrong
        }
    )
    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colorScheme.textPrimary,
        unfocusedTextColor = colorScheme.textPrimary,
        focusedBorderColor = colorScheme.primaryStrong,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
        focusedLabelColor = colorScheme.primaryStrong,
        unfocusedLabelColor = colorScheme.textSecondary,
        cursorColor = colorScheme.primaryStrong
    )
    val primaryButtonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = colorScheme.primarySoft,
        contentColor = if (colorScheme.isDark) colorScheme.onPrimaryContainer else colorScheme.primaryStrong
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
        Text(
            text = appString(R.string.ui_proxy_settings),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.textPrimary
        )
        var proxyModeDraft by rememberSaveable { mutableStateOf(settings.proxyMode) }
        var proxyHostDraft by rememberSaveable { mutableStateOf(settings.proxyHost) }
        var proxyPortDraft by rememberSaveable {
            mutableStateOf(settings.proxyPort.takeIf { it > 0 }?.toString().orEmpty())
        }
        var proxyAuthenticationEnabledDraft by rememberSaveable {
            mutableStateOf(settings.proxyAuthenticationEnabled)
        }
        var proxyUsernameDraft by rememberSaveable { mutableStateOf(settings.proxyUsername) }
        var proxyPasswordDraft by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(
            settings.proxyMode,
            settings.proxyHost,
            settings.proxyPort,
            settings.proxyAuthenticationEnabled,
            settings.proxyUsername,
            settings.proxyCredentialVersion
        ) {
            proxyModeDraft = settings.proxyMode
            proxyHostDraft = settings.proxyHost
            proxyPortDraft = settings.proxyPort.takeIf { it > 0 }?.toString().orEmpty()
            proxyAuthenticationEnabledDraft = settings.proxyAuthenticationEnabled
            proxyUsernameDraft = settings.proxyUsername
            proxyPasswordDraft = ""
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppProxyMode.entries.forEach { mode ->
                FilterChip(
                    selected = proxyModeDraft == mode,
                    onClick = {
                        proxyModeDraft = mode
                        if (mode == AppProxyMode.SYSTEM) onUseSystemProxy()
                    },
                    label = { Text(proxyModeLabel(mode)) },
                    colors = chipColors
                )
            }
        }
        if (proxyModeDraft != AppProxyMode.SYSTEM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = proxyHostDraft,
                    onValueChange = { proxyHostDraft = it },
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_host_or_ip)) },
                    placeholder = { Text("127.0.0.1") },
                    singleLine = true,
                    colors = inputColors,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = proxyPortDraft,
                    onValueChange = { value -> proxyPortDraft = value.filter(Char::isDigit).take(5) },
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_port)) },
                    placeholder = { Text("7890") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = inputColors,
                    modifier = Modifier.width(112.dp)
                )
            }
            FilterChip(
                selected = proxyAuthenticationEnabledDraft,
                onClick = {
                    proxyAuthenticationEnabledDraft = !proxyAuthenticationEnabledDraft
                    if (!proxyAuthenticationEnabledDraft) proxyPasswordDraft = ""
                },
                label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_account_password_authentication)) },
                colors = chipColors
            )
            if (proxyAuthenticationEnabledDraft) {
                OutlinedTextField(
                    value = proxyUsernameDraft,
                    onValueChange = { proxyUsernameDraft = it },
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_username)) },
                    singleLine = true,
                    colors = inputColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = proxyPasswordDraft,
                    onValueChange = { proxyPasswordDraft = it },
                    label = {
                        Text(if (settings.proxyPasswordConfigured) appString(R.string.ui_password_saved) else appString(R.string.ui_password))
                    },
                    supportingText = {
                        Text(
                            if (settings.proxyPasswordConfigured) {
                                appString(R.string.ui_leave_blank_to_continue_using_encrypted_saved_passwords)
                            } else {
                                appString(R.string.ui_the_password_will_be_encrypted_and_saved_locally)
                            }
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = inputColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            val proxyPort = proxyPortDraft.toIntOrNull() ?: 0
            val credentialsValid = !proxyAuthenticationEnabledDraft ||
                (
                    proxyUsernameDraft.isNotBlank() &&
                        (proxyPasswordDraft.isNotEmpty() || settings.proxyPasswordConfigured)
                    )
            FilledTonalButton(
                onClick = {
                    onAdvancedProxyApplied(
                        proxyModeDraft,
                        proxyHostDraft,
                        proxyPort,
                        proxyAuthenticationEnabledDraft,
                        proxyUsernameDraft,
                        proxyPasswordDraft
                    )
                },
                enabled = isValidManualProxy(proxyModeDraft, proxyHostDraft, proxyPort) && credentialsValid,
                colors = primaryButtonColors,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.ui_apply_advanced_proxy))
            }
        }
        Text(
            text = appString(R.string.ui_current_agent, currentProxyLabel(settings)),
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textTertiary
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
        Text(
            text = appString(R.string.ui_dns_service),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.textPrimary
        )
        var customDnsServerDraft by rememberSaveable { mutableStateOf(settings.customDnsServer) }
        LaunchedEffect(settings.customDnsServer) {
            customDnsServerDraft = settings.customDnsServer
        }
        OutlinedTextField(
            value = customDnsServerDraft,
            onValueChange = { customDnsServerDraft = it },
            label = { Text(androidx.compose.ui.res.stringResource(R.string.ui_dns_server_ip)) },
            placeholder = { Text("223.5.5.5") },
            singleLine = true,
            colors = inputColors,
            modifier = Modifier.fillMaxWidth()
        )
        FilledTonalButton(
            onClick = { onCustomDnsServerApplied(customDnsServerDraft) },
            enabled = normalizeDnsServerAddress(customDnsServerDraft) != null,
            colors = primaryButtonColors,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(androidx.compose.ui.res.stringResource(R.string.ui_apply_dns))
        }
        TextButton(
            onClick = onUseSystemDns,
            colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primaryStrong),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(androidx.compose.ui.res.stringResource(R.string.ui_clear_settings_and_use_system_dns))
        }
        Text(
            text = appString(R.string.ui_current_dns, currentDnsLabel(settings)),
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textTertiary
        )
    }
}

internal fun proxyModeLabel(mode: AppProxyMode): String = when (mode) {
    AppProxyMode.SYSTEM -> appString(R.string.ui_system_vpn)
    AppProxyMode.HTTP -> "HTTP"
    AppProxyMode.SOCKS5 -> "SOCKS5"
}

private fun currentProxyLabel(settings: NetworkRouteSettings): String {
    return if (settings.proxyMode == AppProxyMode.SYSTEM) {
        proxyModeLabel(AppProxyMode.SYSTEM)
    } else {
        buildString {
            append("${proxyModeLabel(settings.proxyMode)} ${settings.proxyHost}:${settings.proxyPort}")
            if (settings.proxyAuthenticationEnabled) append(appString(R.string.ui_account_password))
        }
    }
}

private fun currentDnsLabel(settings: NetworkRouteSettings): String {
    return settings.activeDnsServerAddresses.takeIf { it.isNotEmpty() }?.joinToString() ?: appString(R.string.ui_system_dns)
}
