package com.asmr.player.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.asmr.player.nas.LibraryWorkspace
import com.asmr.player.util.isOnlineTrackPath

@UnstableApi
class RoutingPlaybackDataSource(
    private val upstream: DataSource,
    private val cached: DataSource,
    private val shouldUseCache: (Uri) -> Boolean = { uri -> isOnlineTrackPath(uri.toString()) },
    private val webDav: DataSource? = null,
) : DataSource {
    private var activeDataSource: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
        cached.addTransferListener(transferListener)
        webDav?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val selected = when {
            webDav != null && LibraryWorkspace.owns(dataSpec.uri) -> webDav
            shouldUseCache(dataSpec.uri) -> cached
            else -> upstream
        }
        activeDataSource = selected
        return selected.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val current = activeDataSource ?: throw IllegalStateException("DataSource is not opened")
        return current.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = activeDataSource?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        activeDataSource?.responseHeaders ?: emptyMap()

    override fun close() {
        val current = activeDataSource
        activeDataSource = null
        current?.close()
    }

    @UnstableApi
    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val cachedFactory: DataSource.Factory,
        private val webDavFactory: DataSource.Factory? = null,
        private val shouldUseCache: (Uri) -> Boolean = { uri -> isOnlineTrackPath(uri.toString()) }
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return RoutingPlaybackDataSource(
                upstream = upstreamFactory.createDataSource(),
                cached = cachedFactory.createDataSource(),
                webDav = webDavFactory?.createDataSource(),
                shouldUseCache = shouldUseCache
            )
        }
    }
}
