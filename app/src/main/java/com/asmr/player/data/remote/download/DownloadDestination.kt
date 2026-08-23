package com.asmr.player.data.remote.download

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.edit
import com.asmr.player.data.settings.SettingsKeys
import com.asmr.player.data.settings.settingsDataStore
import com.asmr.player.util.documentTreeDisplayPath
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

sealed interface DownloadDestination {
    val root: String
    val label: String
    val displayPath: String

    data class Default(
        override val root: String,
        override val label: String = "默认目录（应用专用）",
        override val displayPath: String = root,
    ) : DownloadDestination

    data class DocumentTree(
        override val root: String,
        override val label: String,
        override val displayPath: String = root,
    ) : DownloadDestination
}

data class DownloadStorageEntry(
    val reference: String,
    val relativePath: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
)

@Singleton
class DownloadDestinationStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val destination: Flow<DownloadDestination> = context.settingsDataStore.data.map { preferences ->
        val treeUri = preferences[SettingsKeys.DOWNLOAD_DIRECTORY_TREE_URI].orEmpty().trim()
        if (treeUri.isBlank()) {
            defaultDestination()
        } else {
            DownloadDestination.DocumentTree(
                root = treeUri,
                label = preferences[SettingsKeys.DOWNLOAD_DIRECTORY_LABEL]
                    .orEmpty()
                    .ifBlank { "自定义目录" },
                displayPath = documentTreeDisplayPath(context, treeUri),
            )
        }
    }

    fun defaultDestination(): DownloadDestination.Default = DownloadDestination.Default(
        root = File(context.getExternalFilesDir(null), "albums").absolutePath,
    )

    suspend fun current(): DownloadDestination = destination.first()

    suspend fun set(destination: DownloadDestination) {
        context.settingsDataStore.edit { preferences ->
            when (destination) {
                is DownloadDestination.Default -> {
                    preferences.remove(SettingsKeys.DOWNLOAD_DIRECTORY_TREE_URI)
                    preferences.remove(SettingsKeys.DOWNLOAD_DIRECTORY_LABEL)
                }

                is DownloadDestination.DocumentTree -> {
                    preferences[SettingsKeys.DOWNLOAD_DIRECTORY_TREE_URI] = destination.root
                    preferences[SettingsKeys.DOWNLOAD_DIRECTORY_LABEL] = destination.label
                }
            }
        }
    }
}

@Singleton
class DownloadStorageGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver: ContentResolver
        get() = context.contentResolver

    fun isDocumentReference(reference: String): Boolean = reference.startsWith("content://")

    fun hasPersistedWritePermission(treeUri: String): Boolean {
        val uri = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return false
        return resolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission && permission.isWritePermission
        }
    }

    fun takePersistablePermission(uri: Uri): Boolean = runCatching {
        resolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        true
    }.getOrDefault(false)

    fun displayName(uri: Uri): String {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return runCatching {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use ""
                val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (index >= 0) cursor.getString(index).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("").ifBlank { uri.lastPathSegment.orEmpty().ifBlank { "自定义目录" } }
    }

    suspend fun resolveDirectory(root: String, relativePath: String): String = withContext(Dispatchers.IO) {
        val segments = relativePath.replace('\\', '/').split('/').filter { it.isNotBlank() }
        require(segments.none { it == "." || it == ".." }) { "下载相对路径无效" }
        if (!isDocumentReference(root)) {
            var current = File(root)
            segments.forEach { segment -> current = File(current, segment) }
            check(current.exists() || current.mkdirs()) { "无法创建下载目录" }
            return@withContext current.absolutePath
        }

        var current = normalizeDocumentReference(root)
        segments.forEach { segment ->
            current = findChild(current, segment, directoryOnly = true)
                ?: DocumentsContract.createDocument(
                    resolver,
                    Uri.parse(current),
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    segment,
                )?.toString()
                ?: error("无法创建下载目录")
        }
        current
    }

    suspend fun findFile(directory: String, name: String): String? = withContext(Dispatchers.IO) {
        require(name.isNotBlank() && '/' !in name && '\\' !in name) { "下载文件名无效" }
        if (!isDocumentReference(directory)) {
            return@withContext File(directory, name).takeIf { it.isFile }?.absolutePath
        }
        findChild(directory, name, directoryOnly = false)
    }

    suspend fun ensureFile(directory: String, name: String, mimeType: String): String = withContext(Dispatchers.IO) {
        require(name.isNotBlank() && '/' !in name && '\\' !in name) { "下载文件名无效" }
        if (!isDocumentReference(directory)) {
            val file = File(directory, name)
            file.parentFile?.mkdirs()
            return@withContext file.absolutePath
        }
        findChild(directory, name, directoryOnly = false)?.let { return@withContext it }
        val created = DocumentsContract.createDocument(
            resolver,
            Uri.parse(directory),
            mimeType,
            name,
        ) ?: error("无法在下载目录创建文件")
        val exactDocument = if (displayName(created) == name) {
            created
        } else {
            runCatching { DocumentsContract.renameDocument(resolver, created, name) }
                .getOrNull()
                ?: created
        }
        if (displayName(exactDocument) != name) {
            runCatching { DocumentsContract.deleteDocument(resolver, exactDocument) }
            error("文档提供器修改了下载文件名")
        }
        exactDocument.toString()
    }

    fun exists(reference: String): Boolean {
        if (reference.isBlank()) return false
        if (!isDocumentReference(reference)) return File(reference).exists()
        return runCatching {
            resolver.query(
                Uri.parse(reference),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
            )?.use { it.moveToFirst() } == true
        }.getOrDefault(false)
    }

    fun size(reference: String): Long {
        if (reference.isBlank()) return 0L
        if (!isDocumentReference(reference)) return File(reference).length().coerceAtLeast(0L)
        return runCatching {
            resolver.query(
                Uri.parse(reference),
                arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use 0L
                val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
            } ?: 0L
        }.getOrDefault(0L).coerceAtLeast(0L)
    }

    fun openInput(reference: String): InputStream {
        return if (isDocumentReference(reference)) {
            resolver.openInputStream(Uri.parse(reference)) ?: error("无法读取下载文件")
        } else {
            File(reference).inputStream()
        }
    }

    fun openOutput(reference: String): OutputStream {
        return if (isDocumentReference(reference)) {
            resolver.openOutputStream(Uri.parse(reference), "wt") ?: error("无法写入下载文件")
        } else {
            File(reference).outputStream()
        }
    }

    fun walk(root: String): List<DownloadStorageEntry> {
        if (!isDocumentReference(root)) {
            val rootFile = File(root)
            if (!rootFile.isDirectory) return emptyList()
            return rootFile.walkTopDown().drop(1).map { file ->
                DownloadStorageEntry(
                    reference = file.absolutePath,
                    relativePath = file.relativeTo(rootFile).path.replace('\\', '/'),
                    displayName = file.name,
                    mimeType = if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "",
                    sizeBytes = if (file.isFile) file.length().coerceAtLeast(0L) else 0L,
                    isDirectory = file.isDirectory,
                )
            }.toList()
        }

        val out = mutableListOf<DownloadStorageEntry>()
        fun visit(parentReference: String, parentRelativePath: String) {
            queryChildren(parentReference).forEach { entry ->
                val relativePath = if (parentRelativePath.isBlank()) {
                    entry.displayName
                } else {
                    "$parentRelativePath/${entry.displayName}"
                }
                val resolved = entry.copy(relativePath = relativePath)
                out += resolved
                if (resolved.isDirectory) visit(resolved.reference, relativePath)
            }
        }
        visit(normalizeDocumentReference(root), "")
        return out
    }

    fun delete(reference: String): Boolean {
        if (reference.isBlank()) return false
        return if (isDocumentReference(reference)) {
            runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(reference)) }.getOrDefault(false)
        } else {
            val file = File(reference)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
    }

    fun isSameOrDescendant(reference: String, root: String): Boolean {
        if (reference.isBlank() || root.isBlank()) return false
        if (!isDocumentReference(reference) && !isDocumentReference(root)) {
            val candidate = runCatching { File(reference).canonicalFile }.getOrNull() ?: return false
            val ancestor = runCatching { File(root).canonicalFile }.getOrNull() ?: return false
            return candidate == ancestor || candidate.path.startsWith(ancestor.path.trimEnd(File.separatorChar) + File.separator)
        }
        if (!isDocumentReference(reference) || !isDocumentReference(root)) return false
        val candidate = documentIdentity(reference) ?: return false
        val ancestor = documentIdentity(root) ?: return false
        if (candidate.first != ancestor.first) return false
        return candidate.second == ancestor.second || candidate.second.startsWith(ancestor.second.trimEnd('/') + "/")
    }

    fun stableIdentity(reference: String): String {
        if (!isDocumentReference(reference)) {
            return runCatching { File(reference).canonicalPath }.getOrDefault(File(reference).absolutePath)
        }
        val identity = documentIdentity(reference) ?: return reference
        return "${identity.first}:${identity.second}"
    }

    private fun normalizeDocumentReference(reference: String): String {
        val uri = Uri.parse(reference)
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull().orEmpty()
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull().orEmpty().ifBlank { treeId }
        return if (treeId.isNotBlank() && documentId.isNotBlank()) {
            DocumentsContract.buildDocumentUriUsingTree(uri, documentId).toString()
        } else {
            reference
        }
    }

    private fun findChild(parentReference: String, name: String, directoryOnly: Boolean): String? {
        return queryChildren(parentReference).firstOrNull { entry ->
            entry.displayName == name && (!directoryOnly || entry.isDirectory)
        }?.reference
    }

    private fun queryChildren(parentReference: String): List<DownloadStorageEntry> {
        val parentUri = Uri.parse(parentReference)
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(parentUri) }.getOrNull().orEmpty()
        val parentId = runCatching { DocumentsContract.getDocumentId(parentUri) }.getOrNull().orEmpty().ifBlank { treeId }
        if (parentId.isBlank()) return emptyList()
        val treeUri = if (treeId.isNotBlank()) {
            DocumentsContract.buildTreeDocumentUri(parentUri.authority, treeId)
        } else {
            parentUri
        }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        return resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val result = mutableListOf<DownloadStorageEntry>()
            while (cursor.moveToNext()) {
                val childName = cursor.getString(nameIndex).orEmpty()
                val mime = cursor.getString(mimeIndex).orEmpty()
                result += DownloadStorageEntry(
                    reference = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex)).toString(),
                    relativePath = childName,
                    displayName = childName,
                    mimeType = mime,
                    sizeBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L,
                    isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                )
            }
            result
        }.orEmpty()
    }

    private fun documentIdentity(reference: String): Pair<String, String>? {
        val uri = runCatching { Uri.parse(reference) }.getOrNull() ?: return null
        val authority = uri.authority ?: return null
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return null
        return authority to id.replace('\\', '/').trimEnd('/')
    }
}
