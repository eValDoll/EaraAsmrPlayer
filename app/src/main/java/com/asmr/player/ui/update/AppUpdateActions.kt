package com.asmr.player.ui.update

import com.asmr.player.i18n.appString
import com.asmr.player.R

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.asmr.player.BuildConfig
import com.asmr.player.data.remote.update.UpdateRelease
import java.io.File

sealed interface AppUpdateInstallResult {
    data object Started : AppUpdateInstallResult
    data object PermissionRequired : AppUpdateInstallResult
    data object FileInvalid : AppUpdateInstallResult
    data class Failed(val message: String) : AppUpdateInstallResult
}

fun launchDownloadedApkInstall(context: Context, apkPath: String): AppUpdateInstallResult {
    val apkFile = File(apkPath)
    if (!apkFile.exists() || apkFile.length() <= 0L) {
        return AppUpdateInstallResult.FileInvalid
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            AppUpdateInstallResult.PermissionRequired
        }.getOrElse { e ->
            AppUpdateInstallResult.Failed(e.message?.trim().orEmpty().ifBlank { appString(R.string.ui_unable_to_open_installation_authorization_page) })
        }
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        context.startActivity(intent)
        AppUpdateInstallResult.Started
    }.getOrElse { e ->
        AppUpdateInstallResult.Failed(e.message?.trim().orEmpty().ifBlank { appString(R.string.ui_unable_to_open_system_installer) })
    }
}

fun openUpdateReleasePage(context: Context, release: UpdateRelease): Boolean {
    val releaseUrl = release.htmlUrl.trim().ifBlank {
        "https://github.com/${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases/latest"
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
