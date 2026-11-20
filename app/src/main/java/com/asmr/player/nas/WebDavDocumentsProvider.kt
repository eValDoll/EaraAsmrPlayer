package com.asmr.player.nas

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.Executors

internal object ActiveWebDav {
    @Volatile private var cached: WebDavClient? = null

    fun client(context: Context): WebDavClient = cached ?: synchronized(this) {
        cached ?: checkNotNull(LibraryWorkspace.location).let {
            WebDavClient(it, LibraryWorkspace.savedPassword(context, it))
        }.also { cached = it }
    }
}

/** Internal SAF bridge for the existing album scanner, subtitles, cover loader and download importer. */
class WebDavDocumentsProvider : DocumentsProvider() {
    private val client get() = ActiveWebDav.client(checkNotNull(context))
    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?) = MatrixCursor(projection ?: arrayOf("root_id"))

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val path = LibraryWorkspace.path(documentId)
        return rows(projection, listOf(client.stat(path)))
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val path = LibraryWorkspace.path(parentDocumentId)
        val entries = client.list(path)
        return rows(projection, entries)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = LibraryWorkspace.path(parentDocumentId)
        val child = LibraryWorkspace.path(documentId)
        return child != parent && (parent.isEmpty() || child.startsWith("$parent/"))
    }

    private fun rows(columns: Array<out String>?, entries: List<DavEntry>): MatrixCursor {
        val projection = columns ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS)
        return MatrixCursor(projection, entries.size).apply {
            entries.forEach { entry ->
                addRow(projection.map { column ->
                    when (column) {
                        Document.COLUMN_DOCUMENT_ID -> LibraryWorkspace.documentId(entry.path)
                        Document.COLUMN_DISPLAY_NAME -> entry.name
                        Document.COLUMN_MIME_TYPE -> if (entry.directory) Document.MIME_TYPE_DIR else
                            MimeTypeMap.getSingleton().getMimeTypeFromExtension(entry.name.substringAfterLast('.').lowercase())
                                ?: "application/octet-stream"
                        Document.COLUMN_SIZE -> entry.size
                        Document.COLUMN_LAST_MODIFIED -> entry.modified
                        Document.COLUMN_FLAGS -> if (entry.directory) Document.FLAG_DIR_SUPPORTS_CREATE else 0
                        else -> null
                    }
                }.toTypedArray())
            }
        }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        require(displayName.isNotEmpty() && '/' !in displayName)
        val path = listOf(LibraryWorkspace.path(parentDocumentId), displayName).filter(String::isNotEmpty).joinToString("/")
        if (mimeType == Document.MIME_TYPE_DIR) client.mkdir(path)
        else client.put(path, ByteArray(0).toRequestBody(), newFile = true)
        return LibraryWorkspace.documentId(path)
    }

    override fun deleteDocument(documentId: String) = client.delete(LibraryWorkspace.path(documentId))

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException(mode)
        signal?.throwIfCanceled()
        val path = LibraryWorkspace.path(documentId)
        val (reader, writer) = ParcelFileDescriptor.createReliablePipe()
        readers.execute {
            try {
                client.get(path).use { response ->
                    val input = checkNotNull(response.body).byteStream()
                    val output = ParcelFileDescriptor.AutoCloseOutputStream(writer)
                    input.copyTo(output)
                    output.close()
                }
            } catch (error: Exception) {
                try { writer.closeWithError(error.message ?: "WebDAV") } catch (_: IOException) { }
            }
        }
        return reader
    }

    private companion object { val readers = Executors.newFixedThreadPool(4) }
}
