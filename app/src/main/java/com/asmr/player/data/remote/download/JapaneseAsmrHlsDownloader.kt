@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.asmr.player.data.remote.download

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.playback.PlaybackMediaCache
import com.asmr.player.util.ChapterMediaReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/** Exports only this chapter; compressed audio is remuxed without playing it through a speaker. */
internal suspend fun downloadJapaneseAsmrHls(
    context: Context,
    client: OkHttpClient,
    chapter: ChapterMediaReference,
    output: File,
    onProgress: suspend (outputBytes: Long, networkBytes: Long) -> Unit,
) = withContext(Dispatchers.IO) {
    require(output.extension.equals("m4a", ignoreCase = true)) { "在线播放音频需要保存为 M4A" }
    val partial = File(output.parentFile, output.name + ".hls.part")
    if (partial.exists() && !partial.delete()) throw IOException("无法重建音频临时文件")
    val networkBytes = AtomicLong()
    val transferListener = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
            if (isNetwork) networkBytes.addAndGet(bytesTransferred.toLong())
        }
    }
    val upstream = OkHttpDataSource.Factory(client)
        .setUserAgent(NetworkHeaders.USER_AGENT)
        .setDefaultRequestProperties(mapOf(
            "Referer" to "https://japaneseasmr.com/",
            NetworkHeaders.HEADER_SILENT_IO_ERROR to NetworkHeaders.SILENT_IO_ERROR_ON,
        ))
        .setTransferListener(transferListener)
    val dataSource = CacheDataSource.Factory()
        .setCache(PlaybackMediaCache.getInstance(context))
        .setUpstreamDataSourceFactory(upstream)
        .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    val thread = HandlerThread("eara-hls-download", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    val dispatcher = Handler(thread.looper).asCoroutineDispatcher("eara-hls-download")
    var transformer: Transformer? = null
    try {
        withContext(dispatcher) {
            val completion = CompletableDeferred<ExportResult>()
            val clipping = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(chapter.startMs)
                .apply { chapter.endMs?.let(::setEndPositionMs) }.build()
            val item = EditedMediaItem.Builder(MediaItem.Builder()
                .setUri(chapter.streamUrl).setMimeType(MimeTypes.APPLICATION_M3U8)
                .setClippingConfiguration(clipping).build())
                .setRemoveVideo(true).build()
            transformer = Transformer.Builder(context)
                .setLooper(thread.looper)
                .setMaxDelayBetweenMuxerSamplesMs(60_000L)
                .setAssetLoaderFactory(ExoPlayerAssetLoader.Factory(
                    context, DefaultDecoderFactory.Builder(context).build(), Clock.DEFAULT,
                    DefaultMediaSourceFactory(dataSource),
                ))
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        completion.complete(exportResult)
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        completion.completeExceptionally(IOException("在线播放音频下载失败", exportException))
                    }
                }).build()
            val composition = Composition.Builder(EditedMediaItemSequence.Builder(item).build())
                .setTransmuxAudio(true).build()
            checkNotNull(transformer).start(composition, partial.absolutePath)
            while (!completion.isCompleted) {
                delay(1_000L)
                withContext(Dispatchers.IO) { onProgress(partial.length(), networkBytes.getAndSet(0L)) }
            }
            val result = completion.await()
            check(result.audioEncoderName == null) { "在线播放音频不应重新编码" }
            check(result.audioConversionProcess == ExportResult.CONVERSION_PROCESS_TRANSMUXED) { "没有可用的在线播放音频" }
        }
        ensureActive()
        onProgress(partial.length(), networkBytes.getAndSet(0L))
        if (partial.length() <= 0L || !partial.renameTo(output)) throw IOException("无法保存在线播放音频")
    } finally {
        withContext(NonCancellable) {
            withContext(dispatcher) { transformer?.cancel() }
            thread.quitSafely()
            thread.join()
            partial.delete()
        }
    }
}
