package com.asmr.player.cache

import android.content.ComponentCallbacks2
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ImageCacheManager(
    private val appContext: Context,
    private val config: CacheConfig,
    private val memoryCache: MemoryCache,
    private val diskCache: DiskCache,
    private val loaderFacade: ImageLoaderFacade,
    private val stats: CacheStats,
    private val decodeDispatcher: CoroutineDispatcher
) {
    companion object {
        @Volatile
        private var initializedInstance: ImageCacheManager? = null

        fun trimMemoryIfInitialized(level: Int) {
            initializedInstance?.onTrimMemory(level)
        }
    }

    private data class InFlightLoad(
        val deferred: Deferred<Result<ImageBitmap>>,
        val waiters: AtomicInteger = AtomicInteger(0),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, InFlightLoad>()
    private val loadSemaphore = Semaphore(config.loadParallelism)
    private val preloadSemaphore = Semaphore(config.preloadParallelism)
    private val diskWriteSemaphore = Semaphore(1)
    private val memoryStateLock = Any()
    private var memoryGeneration = 0L

    // 数据键(忽略尺寸) -> 最近一次写入内存的完整缓存键。
    // 用于跨尺寸即时复用同一张图片：列表已加载的小图可作为详情大图的瞬时占位。
    private val dataKeyToLatestFullKey = mutableMapOf<String, String>()

    init {
        initializedInstance = this
    }

    private fun currentMemoryGeneration(): Long = synchronized(memoryStateLock) {
        memoryGeneration
    }

    private fun getMemory(fullKey: String, dataKey: String): Bitmap? = synchronized(memoryStateLock) {
        memoryCache.get(fullKey)?.also {
            dataKeyToLatestFullKey[dataKey] = fullKey
        }
    }

    private fun putMemory(
        fullKey: String,
        dataKey: String,
        bmp: Bitmap,
        expectedGeneration: Long,
    ) = synchronized(memoryStateLock) {
        if (memoryGeneration != expectedGeneration) return@synchronized
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
        return synchronized(memoryStateLock) {
            val fullKey = dataKeyToLatestFullKey[dataKey] ?: return@synchronized null
            val bitmap = memoryCache.get(fullKey)
            if (bitmap == null) {
                dataKeyToLatestFullKey.remove(dataKey)
            }
            bitmap?.asImageBitmap()
        }
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
        val memoryGenerationAtStart = currentMemoryGeneration()

        if (cachePolicy.readMemory) {
            Trace.beginSection("img.mem")
            val cached = getMemory(key, dataKey)
            if (cached != null) {
                stats.onMemoryHit()
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
                if (cachePolicy.writeMemory) {
                    putMemory(key, dataKey, bmp, memoryGenerationAtStart)
                }
                Trace.endSection()
                return bmp.asImageBitmap()
            }
            stats.onDiskMiss()
            Trace.endSection()
        }

        val sharedLoad = inFlight.compute(key) { _, existing ->
            val selected = existing?.takeUnless { it.deferred.isCancelled } ?: InFlightLoad(
                deferred = scope.async(start = CoroutineStart.LAZY) {
                    val result = try {
                        val lateMemoryHit = if (cachePolicy.readMemory) {
                            getMemory(key, dataKey)
                        } else {
                            null
                        }
                        if (lateMemoryHit != null) {
                            stats.onMemoryHit()
                            Result.success(lateMemoryHit.asImageBitmap())
                        } else {
                            Result.success(loadSemaphore.withPermit {
                                stats.onNetworkFetch()
                                Trace.beginSection("img.net")
                                val bmp = try {
                                    loaderFacade.loadBitmap(model, size)
                                } finally {
                                    Trace.endSection()
                                }
                                stats.onDecode()
                                if (cachePolicy.writeMemory) {
                                    putMemory(key, dataKey, bmp, memoryGenerationAtStart)
                                }
                                if (cachePolicy.writeDisk) {
                                    writeDiskCacheAsync(key, bmp)
                                }
                                bmp.asImageBitmap()
                            })
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                    if (config.logStats) {
                        val s = stats.snapshot()
                        Log.d("ImageCacheManager", "stats mem=${s.memoryHitRate} disk=${s.diskHitRate} net=${s.networkFetches} dec=${s.decodeCount}")
                    }
                    result
                }
            )
            selected.waiters.incrementAndGet()
            selected
        } ?: error("Unable to create image load")
        sharedLoad.deferred.start()
        try {
            return sharedLoad.deferred.await().getOrThrow()
        } finally {
            var orphanedLoad: InFlightLoad? = null
            inFlight.computeIfPresent(key) { _, current ->
                if (current !== sharedLoad) {
                    current
                } else if (current.waiters.decrementAndGet() == 0) {
                    orphanedLoad = current
                    null
                } else {
                    current
                }
            }
            orphanedLoad?.deferred?.takeIf { it.isActive }?.cancel()
        }
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
        val memoryGenerationAtStart = currentMemoryGeneration()

        if (cachePolicy.readMemory) {
            val cached = getMemory(key, dataKey)
            if (cached != null) {
                stats.onMemoryHit()
                return cached.asImageBitmap()
            }
            stats.onMemoryMiss()
        }

        if (cachePolicy.readDisk) {
            val entry = withContext(Dispatchers.IO) { diskCache.get(key) }
            if (entry != null) {
                stats.onDiskHit()
                val bmp = decodeBytes(entry.bytes)
                if (cachePolicy.writeMemory) {
                    putMemory(key, dataKey, bmp, memoryGenerationAtStart)
                }
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
                    runCatching { loadImage(model = m, size = size, cachePolicy = CachePolicy.CACHE_WARMUP) }
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
                    runCatching { loadImage(model = m, size = size, cachePolicy = CachePolicy.CACHE_WARMUP) }
                }
            }
        }
    }

    fun statsSnapshot(): CacheStats.Snapshot = stats.snapshot()

    fun onTrimMemory(level: Int) {
        val maxSize = memoryCache.maxSizeBytes()
        val targetSize = when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> 0
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> maxSize / 4
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> maxSize / 2
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> maxSize / 4
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> maxSize / 2
            else -> return
        }
        synchronized(memoryStateLock) {
            memoryGeneration += 1L
            memoryCache.trimToSize(targetSize)
            val retainedKeys = memoryCache.snapshotKeys()
            val iterator = dataKeyToLatestFullKey.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value !in retainedKeys) {
                    iterator.remove()
                }
            }
        }
    }

    private suspend fun decodeBytes(bytes: ByteArray): Bitmap = withContext(decodeDispatcher) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?.also { it.prepareToDraw() }
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
            // 磁盘缓存是尽力写入；存储繁忙时跳过，避免滚动突发请求堆积压缩和 I/O。
            if (!diskWriteSemaphore.tryAcquire()) return@launch
            try {
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
            } finally {
                diskWriteSemaphore.release()
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
