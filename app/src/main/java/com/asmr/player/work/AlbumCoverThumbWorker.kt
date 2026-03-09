package com.asmr.player.work

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.asmr.player.cache.CacheImageModel
import com.asmr.player.cache.ImageCacheEntryPoint
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.util.DlsiteAntiHotlink
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.io.FileOutputStream

class AlbumCoverThumbWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val albumId = inputData.getLong(KEY_ALBUM_ID, 0L)
        if (albumId <= 0L) return Result.failure()

        val database = AppDatabaseProvider.get(applicationContext)
        val albumDao = database.albumDao()
        val entity = albumDao.getAlbumById(albumId) ?: return Result.success()

        val source = entity.coverPath.takeIf { it.isNotBlank() && it != "null" } ?: entity.coverUrl
        if (source.isBlank()) return Result.success()

        val sourceHash = source.trim().hashCode().toString()
        val dir = File(applicationContext.filesDir, "album_thumbs")
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, "a_${albumId}_${sourceHash}_v$THUMB_VERSION.jpg")

        if (entity.coverThumbPath == target.absolutePath && target.exists() && target.length() > 0L) {
            return Result.success()
        }

        val manager = EntryPointAccessors.fromApplication(applicationContext, ImageCacheEntryPoint::class.java).imageCacheManager()
        val model: Any = run {
            val headers = if (source.startsWith("http", ignoreCase = true)) DlsiteAntiHotlink.headersForImageUrl(source) else emptyMap()
            if (headers.isEmpty()) source else CacheImageModel(data = source, headers = headers, keyTag = "dlsite")
        }
        val bitmap = manager.loadImage(model = model, size = IntSize(THUMB_SIZE_PX, THUMB_SIZE_PX)).asAndroidBitmap()
        val thumb = centerCropSquare(bitmap, THUMB_SIZE_PX)

        FileOutputStream(target).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        val updated = entity.copy(coverThumbPath = target.absolutePath)
        albumDao.updateAlbum(updated)
        return Result.success()
    }

    private fun centerCropSquare(src: Bitmap, size: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val side = minOf(w, h)
        val left = (w - side) / 2
        val top = (h - side) / 2
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            src,
            Rect(left, top, left + side, top + side),
            Rect(0, 0, size, size),
            paint
        )
        return out
    }

    companion object {
        const val KEY_ALBUM_ID = "albumId"
        private const val THUMB_VERSION = 2
        private const val THUMB_SIZE_PX = 640
    }
}
