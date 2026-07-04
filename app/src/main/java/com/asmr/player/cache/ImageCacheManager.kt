package com.asmr.player.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Trace
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

class ImageCacheManager(
    private val appContext: Context,
    private val config: CacheConfig,
    private val memoryCache: MemoryCache,
    private val diskCache: DiskCache,
    private val loaderFacade: ImageLoaderFacade,
    private val stats: CacheStats,
    private val decodeDispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<Result<ImageBitmap>>>()
    private val loadSemaphore = Semaphore(config.loadParallelism)
    private val preloadSemaphore = Semaphore(config.preloadParallelism)

    // 数据键(忽略尺寸) -> 最近一次写入内存的完整缓存键。
    // 用于跨尺寸即时复用同一张图片：列表已加载的小图可作为详情大图的瞬时占位。
    private val dataKeyToLatestFullKey = ConcurrentHashMap<String, String>()

    private fun putMemory(fullKey: String, dataKey: String, bmp: Bitmap) {
        memoryCache.put(fullKey, bmp)
        dataKeyToLatestFullKey[dataKey] = fullKey
    }

    /**
     * 同步、仅内存：按图片数据(忽略尺寸)检索任意一张已缓存的位图。
     * 命中则可立即作为占位显示，避免详情页等待网络重新请求同一张封面。
     * 未命中返回 null。
     */
    fun peekAnySize(model: Any): ImageBitmap? {
        val dataKey = CacheKeyFactory.createDataKey(appContext, model, config.cacheVersion)
        val fullKey = dataKeyToLatestFullKey[dataKey] ?: return null
        return memoryCache.get(fullKey)?.asImageBitmap()
    }

    suspend fun loadImage(
        model: Any,
        size: IntSize?,
        cachePolicy: CachePolicy = CachePolicy.DEFAULT
    ): ImageBitmap {
        Trace.beginSection("img.load")
        try {
        val key = CacheKeyFactory.createKey(appContext, model, size, config.cacheVersion)
        val dataKey = CacheKeyFactory.createDataKey(appContext, model, config.cacheVersion)

        if (cachePolicy.readMemory) {
            Trace.beginSection("img.mem")
            val cached = memoryCache.get(key)
            if (cached != null) {
                stats.onMemoryHit()
                dataKeyToLatestFullKey[dataKey] = key
                Trace.endSection()
                return cached.asImageBitmap()
            }
            stats.onMemoryMiss()
            Trace.endSection()
        }

        if (cachePolicy.readDisk) {
            Trace.beginSection("img.disk")
            val entry = withContext(Dispatchers.IO) { diskCache.get(key) }
            if (entry != null) {
                stats.onDiskHit()
                Trace.beginSection("img.decodeDisk")
                val bmp = decodeBytes(entry.bytes)
                Trace.endSection()
                if (cachePolicy.writeMemory) putMemory(key, dataKey, bmp)
                Trace.endSection()
                return bmp.asImageBitmap()
            }
            stats.onDiskMiss()
            Trace.endSection()
        }

        val existing = inFlight[key]
        if (existing != null) {
            return existing.await().getOrThrow()
        }

        val created = scope.async {
            runCatching {
                loadSemaphore.withPermit {
                    stats.onNetworkFetch()
                    Trace.beginSection("img.net")
                    val bmp = try {
                        loaderFacade.loadBitmap(model, size)
                    } finally {
                        Trace.endSection()
                    }
                    stats.onDecode()
                    if (cachePolicy.writeMemory) {
                        putMemory(key, dataKey, bmp)
                    }
                    if (cachePolicy.writeDisk) {
                        writeDiskCacheAsync(key, bmp)
                    }
                    bmp.asImageBitmap()
                }
            }.also {
                if (config.logStats) {
                    val s = stats.snapshot()
                    Log.d("ImageCacheManager", "stats mem=${s.memoryHitRate} disk=${s.diskHitRate} net=${s.networkFetches} dec=${s.decodeCount}")
                }
            }
        }
        inFlight[key] = created
        created.invokeOnCompletion { inFlight.remove(key, created) }
        return created.await().getOrThrow()
        } finally {
            Trace.endSection()
        }
    }

    suspend fun loadImageFromCache(
        model: Any,
        size: IntSize?,
        cachePolicy: CachePolicy = CachePolicy.DEFAULT
    ): ImageBitmap? {
        val key = CacheKeyFactory.createKey(appContext, model, size, config.cacheVersion)
        val dataKey = CacheKeyFactory.createDataKey(appContext, model, config.cacheVersion)

        if (cachePolicy.readMemory) {
            val cached = memoryCache.get(key)
            if (cached != null) {
                stats.onMemoryHit()
                dataKeyToLatestFullKey[dataKey] = key
                return cached.asImageBitmap()
            }
            stats.onMemoryMiss()
        }

        if (cachePolicy.readDisk) {
            val entry = withContext(Dispatchers.IO) { diskCache.get(key) }
            if (entry != null) {
                stats.onDiskHit()
                val bmp = decodeBytes(entry.bytes)
                if (cachePolicy.writeMemory) putMemory(key, dataKey, bmp)
                return bmp.asImageBitmap()
            }
            stats.onDiskMiss()
        }
        return null
    }

    fun preload(models: List<Any>) {
        if (models.isEmpty()) return
        preload(models, size = null)
    }

    fun preload(models: List<Any>, size: IntSize?) {
        if (models.isEmpty()) return
        models.forEach { m ->
            scope.launch {
                preloadSemaphore.withPermit {
                    runCatching { loadImageFromCache(model = m, size = size, cachePolicy = CachePolicy.CACHE_WARMUP) }
                }
            }
        }
    }

    fun preload(scope: CoroutineScope, models: List<Any>): Job {
        return preload(scope, models, size = null)
    }

    fun preload(scope: CoroutineScope, models: List<Any>, size: IntSize?): Job {
        return scope.launch(Dispatchers.IO) {
            models.forEach { m ->
                preloadSemaphore.withPermit {
                    runCatching { loadImageFromCache(model = m, size = size, cachePolicy = CachePolicy.CACHE_WARMUP) }
                }
            }
        }
    }

    fun statsSnapshot(): CacheStats.Snapshot = stats.snapshot()

    private suspend fun decodeBytes(bytes: ByteArray): Bitmap = withContext(decodeDispatcher) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Disk cache decode failed")
    }

    private suspend fun encodeBitmapForDisk(bitmap: Bitmap): ByteArray = withContext(decodeDispatcher) {
        Trace.beginSection("img.encode")
        try {
            val out = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                Bitmap.CompressFormat.PNG
            }
            bitmap.compress(format, 100, out)
            out.toByteArray()
        } finally {
            Trace.endSection()
        }
    }

    private fun writeDiskCacheAsync(key: String, bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        scope.launch {
            runCatching {
                val bytes = encodeBitmapForDisk(bitmap)
                diskCache.put(
                    key,
                    DiskCache.Entry(
                        bytes = bytes,
                        width = width,
                        height = height
                    )
                )
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ImageCacheEntryPoint {
    fun imageCacheManager(): ImageCacheManager
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun rememberCachedImage(
    model: Any,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
): Painter {
    val ctx = LocalContext.current.applicationContext
    val manager = remember(ctx) {
        EntryPointAccessors.fromApplication(ctx, ImageCacheEntryPoint::class.java).imageCacheManager()
    }
    val state: MutableState<Painter> = remember(model) { mutableStateOf(ColorPainter(androidx.compose.ui.graphics.Color.Transparent)) }
    LaunchedEffect(model) {
        runCatching {
            val img = manager.loadImage(model = model, size = null, cachePolicy = CachePolicy.DEFAULT)
            state.value = BitmapPainter(img)
        }
    }
    return state.value
}
