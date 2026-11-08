package com.asmr.player.cache

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Trace
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.asmr.player.performance.UiFrameWorkCoordinator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        // Android 15 起不再发送这些级别，但旧系统仍会回调对应数值。
        private const val TrimMemoryRunningLowCompat = 10
        private const val TrimMemoryRunningCriticalCompat = 15
        private const val TrimMemoryCompleteCompat = 80

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

    private data class InFlightKey(
        val cacheKey: String,
        val cachePolicy: CachePolicy,
    )

    private data class MemoryEntry(
        val dataKey: String,
        val fullKey: String,
    )

    class MemoryPreview internal constructor(
        val bitmap: ImageBitmap,
        internal val dataKey: String,
        internal val sourceKey: String,
    )

    private val scope = CoroutineScope(SupervisorJob() + decodeDispatcher)
    private val inFlight = ConcurrentHashMap<InFlightKey, InFlightLoad>()
    // 远程推荐封面在直连较慢时会长时间占用加载许可。独立的本地通道保证文件图片
    // 不会被网络请求饿死，横屏同时展示推荐列表时也能立即开始读取。
    private val loadGate = ImageLoadGate(
        remotePermits = config.loadParallelism,
        localPermits = config.loadParallelism,
    )
    private val preloadSemaphore = Semaphore(config.preloadParallelism)
    private val diskWriteSemaphore = Semaphore(1)
    private val memoryStateLock = Any()
    private var memoryGeneration = 0L

    // 来源身份不读取文件元数据，组合阶段只查内存；后台仍按文件版本验证精确缓存键。
    private val sourceToLatestMemoryEntry = mutableMapOf<String, MemoryEntry>()
    private val fullKeyToMemorySource = mutableMapOf<String, String>()

    init {
        memoryCache.setRemovalListener(::onMemoryEntryRemoved)
        initializedInstance = this
    }

    private fun currentMemoryGeneration(): Long = synchronized(memoryStateLock) {
        memoryGeneration
    }

    private fun getMemory(fullKey: String, dataKey: String, sourceKey: String): Bitmap? = synchronized(memoryStateLock) {
        val previousEntry = sourceToLatestMemoryEntry[sourceKey]
        if (previousEntry != null && previousEntry.dataKey != dataKey) {
            removeMemoryEntry(sourceKey)
        }
        memoryCache.get(fullKey)?.also {
            recordMemoryEntry(sourceKey, dataKey, fullKey)
        }
    }

    private fun putMemory(
        fullKey: String,
        dataKey: String,
        sourceKey: String,
        bmp: Bitmap,
        expectedGeneration: Long,
    ) = synchronized(memoryStateLock) {
        if (memoryGeneration != expectedGeneration) return@synchronized
        memoryCache.put(fullKey, bmp)
        // 大于整个 LRU 容量的图片会在 put 中立即被淘汰，不能为它保留占位索引。
        if (memoryCache.get(fullKey) === bmp) {
            recordMemoryEntry(sourceKey, dataKey, fullKey)
        }
    }

    // 以下索引操作均持有 memoryStateLock，每个来源只为当前占位保留一条反向映射。
    private fun recordMemoryEntry(sourceKey: String, dataKey: String, fullKey: String) {
        val previous = sourceToLatestMemoryEntry[sourceKey]
        if (previous?.dataKey == dataKey && previous.fullKey == fullKey) return
        previous?.let { fullKeyToMemorySource.remove(it.fullKey) }
        sourceToLatestMemoryEntry[sourceKey] = MemoryEntry(dataKey, fullKey)
        fullKeyToMemorySource[fullKey] = sourceKey
    }

    private fun removeMemoryEntry(sourceKey: String) {
        val entry = sourceToLatestMemoryEntry.remove(sourceKey) ?: return
        fullKeyToMemorySource.remove(entry.fullKey)
    }

    private fun onMemoryEntryRemoved(fullKey: String) = synchronized(memoryStateLock) {
        val sourceKey = fullKeyToMemorySource.remove(fullKey) ?: return@synchronized
        if (sourceToLatestMemoryEntry[sourceKey]?.fullKey == fullKey) {
            sourceToLatestMemoryEntry.remove(sourceKey)
        }
    }

    /**
     * 按图片数据检索任意尺寸的位图，同时验证本地文件元数据。应在后台调用。
     * 命中则可立即作为占位显示，避免详情页等待网络重新请求同一张封面。
     * 未命中返回 null。
     */
    fun peekAnySize(model: Any): ImageBitmap? {
        val dataKey = CacheKeyFactory.createDataKey(appContext, model, config.cacheVersion)
        val sourceKey = CacheKeyFactory.createMemoryLookupKey(appContext, model, config.cacheVersion)
        return peekMemory(sourceKey, dataKey)?.bitmap
    }

    /** 同步、无 IO 的即时占位查找；调用方随后按精确尺寸和文件版本加载。 */
    fun peekAnySizeInMemory(model: Any): MemoryPreview? {
        val sourceKey = CacheKeyFactory.createMemoryLookupKey(appContext, model, config.cacheVersion)
        return peekMemory(sourceKey, expectedDataKey = null)
    }

    /** 占位的文件版本独立于 LRU 生存期，淘汰缓存不会使已经显示的有效图片失效。 */
    suspend fun isMemoryPreviewValid(model: Any, preview: MemoryPreview): Boolean {
        // 不带本地文件元数据的来源两种键相同，无需为远程封面增加一次后台调度。
        if (preview.dataKey == preview.sourceKey) {
            return CacheKeyFactory.createMemoryLookupKey(appContext, model, config.cacheVersion) == preview.dataKey
        }
        return withContext(decodeDispatcher) {
            CacheKeyFactory.createDataKey(appContext, model, config.cacheVersion) == preview.dataKey
        }
    }

    private fun peekMemory(sourceKey: String, expectedDataKey: String?): MemoryPreview? {
        return synchronized(memoryStateLock) {
            val entry = sourceToLatestMemoryEntry[sourceKey] ?: return@synchronized null
            if (expectedDataKey != null && expectedDataKey != entry.dataKey) {
                removeMemoryEntry(sourceKey)
                return@synchronized null
            }
            val bitmap = memoryCache.get(entry.fullKey)
            if (bitmap == null) {
                removeMemoryEntry(sourceKey)
            }
            bitmap?.let { MemoryPreview(it.asImageBitmap(), entry.dataKey, sourceKey) }
        }
    }

    suspend fun loadImage(
        model: Any,
        size: IntSize?,
        cachePolicy: CachePolicy = CachePolicy.DEFAULT
    ): ImageBitmap = withContext(decodeDispatcher) {
        Trace.beginSection("img.load")
        try {
            loadImageInternal(model, size, cachePolicy)
        } finally {
            Trace.endSection()
        }
    }

    private suspend fun loadImageInternal(
        model: Any,
        size: IntSize?,
        cachePolicy: CachePolicy,
    ): ImageBitmap {
        val key = CacheKeyFactory.createKey(appContext, model, size, config.cacheVersion)
        val dataKey = CacheKeyFactory.createDataKey(appContext, model, config.cacheVersion)
        val sourceKey = CacheKeyFactory.createMemoryLookupKey(appContext, model, config.cacheVersion)
        val memoryGenerationAtStart = currentMemoryGeneration()

        if (cachePolicy.readMemory) {
            Trace.beginSection("img.mem")
            val cached = getMemory(key, dataKey, sourceKey)
            if (cached != null) {
                stats.onMemoryHit()
                Trace.endSection()
                return cached.asImageBitmap()
            }
            stats.onMemoryMiss()
            Trace.endSection()
        }

        val inFlightKey = InFlightKey(key, cachePolicy)
        val sharedLoad = inFlight.compute(inFlightKey) { _, existing ->
            val selected = existing?.takeUnless { it.deferred.isCancelled } ?: InFlightLoad(
                deferred = scope.async(start = CoroutineStart.LAZY) {
                    val result = try {
                        val lateMemoryHit = if (cachePolicy.readMemory) {
                            getMemory(key, dataKey, sourceKey)
                        } else {
                            null
                        }
                        if (lateMemoryHit != null) {
                            stats.onMemoryHit()
                            Result.success(lateMemoryHit.asImageBitmap())
                        } else {
                            val diskBitmap = loadImageFromDisk(
                                key = key,
                                dataKey = dataKey,
                                sourceKey = sourceKey,
                                cachePolicy = cachePolicy,
                                memoryGenerationAtStart = memoryGenerationAtStart,
                            )
                            if (diskBitmap != null) {
                                Result.success(diskBitmap)
                            } else {
                                Result.success(loadGate.withPermit(model) {
                                    stats.onNetworkFetch()
                                    Trace.beginSection("img.net")
                                    val bmp = try {
                                        loaderFacade.loadBitmap(model, size)
                                    } finally {
                                        Trace.endSection()
                                    }
                                    stats.onDecode()
                                    if (cachePolicy.writeMemory) {
                                        putMemory(key, dataKey, sourceKey, bmp, memoryGenerationAtStart)
                                    }
                                    if (cachePolicy.writeDisk) {
                                        writeDiskCacheAsync(key, bmp)
                                    }
                                    bmp.asImageBitmap()
                                })
                            }
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
            inFlight.computeIfPresent(inFlightKey) { _, current ->
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
    }

    private suspend fun loadImageFromDisk(
        key: String,
        dataKey: String,
        sourceKey: String,
        cachePolicy: CachePolicy,
        memoryGenerationAtStart: Long,
    ): ImageBitmap? {
        if (!cachePolicy.readDisk) return null
        Trace.beginSection("img.disk")
        try {
            val entry = diskCache.get(key)
            if (entry == null) {
                stats.onDiskMiss()
                return null
            }
            stats.onDiskHit()
            Trace.beginSection("img.decodeDisk")
            val bmp = try {
                decodeBytes(entry.bytes)
            } finally {
                Trace.endSection()
            }
            if (cachePolicy.writeMemory) {
                putMemory(key, dataKey, sourceKey, bmp, memoryGenerationAtStart)
            }
            return bmp.asImageBitmap()
        } finally {
            Trace.endSection()
        }
    }

    suspend fun loadImageFromCache(
        model: Any,
        size: IntSize?,
        cachePolicy: CachePolicy = CachePolicy.DEFAULT
    ): ImageBitmap? = withContext(decodeDispatcher) {
        val key = CacheKeyFactory.createKey(appContext, model, size, config.cacheVersion)
        val dataKey = CacheKeyFactory.createDataKey(appContext, model, config.cacheVersion)
        val sourceKey = CacheKeyFactory.createMemoryLookupKey(appContext, model, config.cacheVersion)
        val memoryGenerationAtStart = currentMemoryGeneration()

        if (cachePolicy.readMemory) {
            val cached = getMemory(key, dataKey, sourceKey)
            if (cached != null) {
                stats.onMemoryHit()
                return@withContext cached.asImageBitmap()
            }
            stats.onMemoryMiss()
        }

        if (cachePolicy.readDisk) {
            val entry = diskCache.get(key)
            if (entry != null) {
                stats.onDiskHit()
                val bmp = decodeBytes(entry.bytes)
                if (cachePolicy.writeMemory) {
                    putMemory(key, dataKey, sourceKey, bmp, memoryGenerationAtStart)
                }
                return@withContext bmp.asImageBitmap()
            }
            stats.onDiskMiss()
        }
        null
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
                    preloadModel(model = m, size = size)
                }
            }
        }
    }

    fun preload(scope: CoroutineScope, models: List<Any>): Job {
        return preload(scope, models, size = null)
    }

    fun preload(
        scope: CoroutineScope,
        models: List<Any>,
        size: IntSize?,
        maxConcurrency: Int? = null,
    ): Job {
        return scope.launch(decodeDispatcher) {
            val requestSemaphore = Semaphore(
                permits = (maxConcurrency ?: models.size).coerceAtLeast(1)
            )
            coroutineScope {
                models.forEach { model ->
                    launch {
                        requestSemaphore.withPermit {
                            preloadSemaphore.withPermit {
                                preloadModel(model = model, size = size)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun preloadModel(model: Any, size: IntSize?) {
        try {
            loadImage(model = model, size = size, cachePolicy = CachePolicy.CACHE_WARMUP)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // 预取失败不影响可见图片按正常路径重试。
        }
    }

    fun updateDiskMaxSizeBytes(maxSizeBytes: Long) {
        diskCache.updateMaxSizeBytes(maxSizeBytes)
    }

    fun diskSizeBytes(): Long = diskCache.sizeBytes()

    fun clearCaches() {
        synchronized(memoryStateLock) {
            memoryGeneration += 1L
            memoryCache.clear()
            sourceToLatestMemoryEntry.clear()
            fullKeyToMemorySource.clear()
        }
        diskCache.clear()
    }

    fun onTrimMemory(level: Int) {
        val maxSize = memoryCache.maxSizeBytes()
        val targetSize = when {
            level >= TrimMemoryCompleteCompat -> 0
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> maxSize / 4
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> maxSize / 2
            level >= TrimMemoryRunningCriticalCompat -> maxSize / 4
            level >= TrimMemoryRunningLowCompat -> maxSize / 2
            else -> return
        }
        synchronized(memoryStateLock) {
            memoryGeneration += 1L
            memoryCache.trimToSize(targetSize)
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
        // 先占用唯一写槽，滚动突发期间其余图片留在内存缓存即可，避免为每张图片都
        // 创建一个持有 Bitmap、等待静默窗口的协程。
        if (!diskWriteSemaphore.tryAcquire()) return
        scope.launch {
            // WEBP lossless 压缩会持续占用 CPU 与内存带宽；等滚动/转场帧完全安静后再做，
            // 图片已先写入内存缓存，因此不会延迟可见内容。
            try {
                UiFrameWorkCoordinator.awaitFrameQuiet()
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
