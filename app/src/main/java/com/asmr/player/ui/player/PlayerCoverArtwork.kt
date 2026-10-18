package com.asmr.player.ui.player

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.asmr.player.cache.CachePolicy
import com.asmr.player.cache.ImageCacheEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun rememberPlayerCoverBitmap(
    artworkModel: Any?,
    enabled: Boolean = true
): State<Bitmap?> {
    val bitmap = remember {
        mutableStateOf(
            when (artworkModel) {
                is Bitmap -> artworkModel.takeUnless { it.isRecycled }
                is ImageBitmap -> artworkModel.asAndroidBitmap()
                else -> null
            }
        )
    }
    val loadedModel = remember { mutableStateOf<Any?>(null) }
    val context = LocalContext.current.applicationContext
    LaunchedEffect(artworkModel, enabled) {
        if (!enabled) return@LaunchedEffect
        when (artworkModel) {
            null -> {
                bitmap.value = null
                loadedModel.value = null
                return@LaunchedEffect
            }
            is Bitmap -> {
                bitmap.value = artworkModel.takeUnless { it.isRecycled }
                return@LaunchedEffect
            }
            is ImageBitmap -> {
                bitmap.value = artworkModel.asAndroidBitmap()
                return@LaunchedEffect
            }
        }
        val model = artworkModel ?: return@LaunchedEffect
        if (loadedModel.value == model && bitmap.value != null) return@LaunchedEffect
        val manager = EntryPointAccessors.fromApplication(context, ImageCacheEntryPoint::class.java)
            .imageCacheManager()
        bitmap.value = try {
            withTimeoutOrNull(15_000L) {
                manager.loadImage(model, IntSize(256, 256), CachePolicy.DEFAULT)
                    .asAndroidBitmap()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (bitmap.value != null) loadedModel.value = model
    }
    return bitmap
}
