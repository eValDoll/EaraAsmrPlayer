package com.asmr.player.cache

import android.content.Context
import android.graphics.Bitmap
import android.os.Trace
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.unit.IntSize
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.ErrorResult
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ImageLoaderFacade(
    private val context: Context,
    okHttpClient: OkHttpClient
) {
    private val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .okHttpClient { okHttpClient }
        .memoryCache(null)
        .diskCache(null)
        .build()

    suspend fun loadBitmap(model: Any, size: IntSize?): Bitmap = withContext(Dispatchers.IO) {
        Trace.beginSection("img.coilExec")
        try {
            val request = buildRequest(model, size)
            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                Trace.beginSection("img.toBitmap")
                try {
                    result.drawable.toBitmap().also { it.prepareToDraw() }
                } finally {
                    Trace.endSection()
                }
            } else {
                val url = when (model) {
                    is CacheImageModel -> model.data as? String
                    is String -> model
                    else -> null
                }
                if (result is ErrorResult && url != null) {
                    val parsed = url.toHttpUrlOrNull()
                    val host = parsed?.host.orEmpty()
                    val path = parsed?.encodedPath.orEmpty()
                    Log.w("ImageLoaderFacade", "image load failed host=$host path=$path", result.throwable)
                }
                throw IllegalStateException("Image load failed: ${result::class.java.simpleName}")
            }
        } finally {
            Trace.endSection()
        }
    }

    private fun buildRequest(model: Any, size: IntSize?): ImageRequest {
        val b = ImageRequest.Builder(context)
            .data(
                when (model) {
                    is CacheImageModel -> model.data
                    else -> model
                }
            )
            .allowHardware(false)
        val headers = (model as? CacheImageModel)?.headers.orEmpty()
        if (headers.isNotEmpty()) {
            headers.forEach { (k, v) -> b.addHeader(k, v) }
        }
        if (size != null && size.width > 0 && size.height > 0) {
            b.size(size.width, size.height)
        }
        return b.build()
    }
}

