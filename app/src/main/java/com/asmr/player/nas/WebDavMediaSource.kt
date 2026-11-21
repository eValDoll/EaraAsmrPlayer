package com.asmr.player.nas

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.IOException

/** Seekable input for Android's metadata and subtitle decoders, including API 24 and 25. */
internal class WebDavMediaSource(private val client: WebDavClient, private val path: String) : MediaDataSource() {
    private val length by lazy { client.stat(path).size }
    private val block = ByteArray(128 * 1024)
    private var start = -1L
    private var count = 0

    override fun getSize() = length
    override fun close() = Unit

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size == 0) return 0
        if (position < 0 || position >= length) return -1
        if (position < start || position >= start + count) {
            val wanted = minOf(block.size.toLong(), length - position).toInt()
            client.get(path, "bytes=$position-${position + wanted - 1}").use { response ->
                if (position != 0L && response.code != 206) throw IOException("WebDAV byte ranges unavailable")
                val input = checkNotNull(response.body).byteStream()
                count = 0
                while (count < wanted) {
                    val read = input.read(block, count, wanted - count)
                    if (read == -1) break
                    count += read
                }
                start = position
            }
        }
        val index = (position - start).toInt()
        val copied = minOf(size, count - index)
        if (copied <= 0) return -1
        block.copyInto(buffer, offset, index, index + copied)
        return copied
    }
}

internal fun MediaMetadataRetriever.setLibrarySource(context: Context, uri: Uri) {
    if (LibraryWorkspace.owns(uri)) setDataSource(WebDavMediaSource(ActiveWebDav.client(context), LibraryWorkspace.path(uri)))
    else setDataSource(context, uri)
}

internal fun MediaExtractor.setLibrarySource(context: Context, uri: Uri) {
    if (LibraryWorkspace.owns(uri)) setDataSource(WebDavMediaSource(ActiveWebDav.client(context), LibraryWorkspace.path(uri)))
    else setDataSource(context, uri, null)
}
