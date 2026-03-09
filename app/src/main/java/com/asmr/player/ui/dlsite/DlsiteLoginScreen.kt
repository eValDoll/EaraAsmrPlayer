package com.asmr.player.ui.dlsite

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun DlsiteLoginScreen(
    windowSizeClass: WindowSizeClass,
    onDone: () -> Unit,
    viewModel: DlsiteLoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showDlsiteCookie by remember { mutableStateOf(false) }
    var showPlayCookie by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoggedIn) {
        if (!uiState.isLoggedIn) {
            showDlsiteCookie = false
            showPlayCookie = false
        }
    }

    // 屏幕尺寸判断
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter // 仅用于平板适配：居中显示内容
    ) {
        Column(
            modifier = if (isCompact) {
                Modifier.fillMaxSize()
            } else {
                // 仅用于平板适配：限制内容区域最大宽度并填充可用空间
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 600.dp) // 登录页可以更窄一点
                    .fillMaxWidth()
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = {
                    viewModel.clear()
                }, enabled = uiState.isLoggedIn) { Text("退出登录") }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = {
                    onDone()
                }, enabled = uiState.isLoggedIn) {
                    Text("完成")
                }
            }
    
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isLoggedIn) {
                    Text("当前凭证", style = MaterialTheme.typography.titleMedium)

                    CredentialBlock(
                        title = "dlsite.com Cookie",
                        value = uiState.dlsiteCookie,
                        expiresAtMs = uiState.dlsiteExpiresAtMs,
                        revealed = showDlsiteCookie,
                        onToggleRevealed = { showDlsiteCookie = !showDlsiteCookie }
                    )
                    CredentialBlock(
                        title = "play.dlsite.com Cookie",
                        value = uiState.playCookie,
                        expiresAtMs = uiState.playExpiresAtMs,
                        revealed = showPlayCookie,
                        onToggleRevealed = { showPlayCookie = !showPlayCookie }
                    )
                } else {
                    OutlinedTextField(
                        value = loginId,
                        onValueChange = { loginId = it },
                        label = { Text("账号（邮箱）") },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.login(loginId, password) },
                        enabled = !uiState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterVertically), strokeWidth = 2.dp)
                        } else {
                            Text("登录")
                        }
                    }
                }
                if (uiState.message.isNotBlank()) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CredentialBlock(
    title: String,
    value: String,
    expiresAtMs: Long?,
    revealed: Boolean,
    onToggleRevealed: () -> Unit
) {
    val display = if (revealed) value else maskSecret(value)
    val expiresText = if (value.isBlank()) {
        "-"
    } else {
        expiresAtMs?.let { formatEpochMs(it) } ?: "未知"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            SelectionContainer {
                OutlinedTextField(
                    value = display,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 6,
                    trailingIcon = {
                        IconButton(onClick = onToggleRevealed, enabled = value.isNotBlank()) {
                            Icon(
                                imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (revealed) "隐藏" else "显示"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text("过期时间：$expiresText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun maskSecret(value: String): String {
    if (value.isBlank()) return ""
    val keepHead = 3
    val keepTail = 3
    if (value.length <= keepHead + keepTail) return "*".repeat(value.length)
    val head = value.take(keepHead)
    val tail = value.takeLast(keepTail)
    return buildString {
        append(head)
        append("*".repeat(value.length - keepHead - keepTail))
        append(tail)
    }
}

private fun formatEpochMs(epochMs: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }
    return formatter.format(Date(epochMs))
}
