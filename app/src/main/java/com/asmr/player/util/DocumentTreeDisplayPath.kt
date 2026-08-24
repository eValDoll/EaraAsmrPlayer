package com.asmr.player.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract

private const val EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY = "com.android.externalstorage.documents"

internal fun documentTreeDisplayPath(context: Context, uriString: String): String {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return uriString
    if (uri.scheme != "content") return uri.path?.takeIf(String::isNotBlank) ?: uriString

    val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }
        .getOrNull()
        .orEmpty()
    if (treeDocumentId.isBlank()) return Uri.decode(uriString)
    if (uri.authority != EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY) {
        return treeDocumentId.replaceFirst(':', '/')
    }
    if (treeDocumentId.startsWith("raw:")) {
        return treeDocumentId.removePrefix("raw:")
    }

    val volumeId = treeDocumentId.substringBefore(':')
    val relativePath = treeDocumentId.substringAfter(':', "")
        .replace('\\', '/')
        .trim('/')
    val volumeRoot = when {
        volumeId.equals("primary", ignoreCase = true) -> {
            Environment.getExternalStorageDirectory().absolutePath
        }

        else -> resolveStorageVolumeRoot(context, volumeId) ?: "/storage/$volumeId"
    }
    return if (relativePath.isBlank()) {
        volumeRoot
    } else {
        "${volumeRoot.trimEnd('/')}/$relativePath"
    }
}

private fun resolveStorageVolumeRoot(context: Context, volumeId: String): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val storageManager = context.getSystemService(StorageManager::class.java) ?: return null
    return storageManager.storageVolumes
        .firstOrNull { volume -> volume.uuid?.equals(volumeId, ignoreCase = true) == true }
        ?.directory
        ?.absolutePath
}
