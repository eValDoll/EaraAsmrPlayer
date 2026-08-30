package com.asmr.player.ui.common

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.asmr.player.cache.CacheImageModel
import com.asmr.player.util.Formatting
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class PreviewImageSaveRequest(
    val key: String,
    val title: String,
    val imageModel: Any?,
    val openPathOrUrl: String
)

internal data class PreviewImageSaveSource(
    val location: String,
    val headers: Map<String, String>
)

internal data class PreparedExternalPreviewImage(
    val uri: Uri,
    val mimeType: String
)

internal fun resolvePreviewImageSaveSource(request: PreviewImageSaveRequest): PreviewImageSaveSource {
    val cacheModel = request.imageModel as? CacheImageModel
    val modelData = cacheModel?.data ?: request.imageModel
    val modelLocation = when (modelData) {
        is Uri -> modelData.toString()
        is File -> modelData.absolutePath
        is String -> modelData
        else -> ""
    }.trim()
    return PreviewImageSaveSource(
        location = modelLocation.ifBlank { request.openPathOrUrl.trim() },
        headers = cacheModel?.headers.orEmpty()
    )
}

internal fun buildPreviewImageDisplayName(
    title: String,
    sourceLocation: String,
    mimeType: String,
    fallbackId: Long = System.currentTimeMillis()
): String {
    val sourceName = runCatching { Uri.parse(sourceLocation).lastPathSegment.orEmpty() }
        .getOrDefault("")
        .substringAfterLast('/')
        .substringAfterLast('\\')
    val safeTitle = Formatting.sanitizeFilename(title.substringAfterLast('/').substringAfterLast('\\'))
        .trim()
        .trim('.')
    val safeSourceName = Formatting.sanitizeFilename(sourceName).trim().trim('.')
    val candidate = safeTitle.ifBlank { safeSourceName }.ifBlank { "Eara_$fallbackId" }
    val candidateExtension = candidate.substringAfterLast('.', "").lowercase()
    val sourceExtension = sourceLocation.substringBefore('?').substringAfterLast('.', "").lowercase()
    val extension = when {
        imageMimeTypeForExtension(candidateExtension) != null -> candidateExtension
        imageMimeTypeForExtension(sourceExtension) != null -> sourceExtension
        else -> imageExtensionForMimeType(mimeType).orEmpty()
    }.let { if (it.equals("jpeg", ignoreCase = true)) "jpg" else it.lowercase() }
        .ifBlank { "jpg" }
    val baseName = if (imageMimeTypeForExtension(candidateExtension) != null) {
        candidate.substringBeforeLast('.').ifBlank { "Eara_$fallbackId" }
    } else {
        candidate
    }
    return "$baseName.$extension"
}

internal suspend fun savePreviewImageToGallery(
    context: Context,
    request: PreviewImageSaveRequest,
    httpClient: OkHttpClient
): Uri = withContext(Dispatchers.IO) {
    val source = resolvePreviewImageSaveSource(request)
    if (source.location.isBlank()) throw IOException("图片来源为空")
    withPreviewImageInput(context, source, httpClient) { input, sourceMimeType ->
        val mimeType = normalizeImageMimeType(sourceMimeType)
            ?: imageMimeTypeForLocation(source.location)
            ?: "image/jpeg"
        val displayName = buildPreviewImageDisplayName(
            title = request.title,
            sourceLocation = source.location,
            mimeType = mimeType
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            savePreviewImageWithMediaStore(context, input, displayName, mimeType)
        } else {
            savePreviewImageToLegacyPictures(context, input, displayName, mimeType)
        }
    }
}

internal suspend fun preparePreviewImageForExternalOpen(
    context: Context,
    request: PreviewImageSaveRequest,
    httpClient: OkHttpClient,
    contentUriForFile: (Context, File) -> Uri = ::previewImageContentUriForFile
): PreparedExternalPreviewImage = withContext(Dispatchers.IO) {
    val source = resolvePreviewImageSaveSource(request)
    if (source.location.isBlank()) throw IOException("图片来源为空")

    when {
        source.location.startsWith("content://", ignoreCase = true) -> {
            val uri = Uri.parse(source.location)
            PreparedExternalPreviewImage(
                uri = uri,
                mimeType = normalizeImageMimeType(context.contentResolver.getType(uri))
                    ?: imageMimeTypeForLocation(source.location)
                    ?: "image/*"
            )
        }
        source.location.startsWith("file://", ignoreCase = true) -> {
            val path = Uri.parse(source.location).path ?: throw IOException("图片路径无效")
            prepareLocalPreviewImageForExternalOpen(context, File(path), contentUriForFile)
        }
        source.location.startsWith("http://", ignoreCase = true) ||
            source.location.startsWith("https://", ignoreCase = true) -> {
            stageRemotePreviewImage(context, request, source, httpClient, contentUriForFile)
        }
        else -> prepareLocalPreviewImageForExternalOpen(context, File(source.location), contentUriForFile)
    }
}

private fun prepareLocalPreviewImageForExternalOpen(
    context: Context,
    file: File,
    contentUriForFile: (Context, File) -> Uri
): PreparedExternalPreviewImage {
    if (!file.isFile) throw java.io.FileNotFoundException(file.absolutePath)
    return PreparedExternalPreviewImage(
        uri = contentUriForFile(context, file),
        mimeType = imageMimeTypeForLocation(file.name) ?: "image/*"
    )
}

private fun stageRemotePreviewImage(
    context: Context,
    request: PreviewImageSaveRequest,
    source: PreviewImageSaveSource,
    httpClient: OkHttpClient,
    contentUriForFile: (Context, File) -> Uri
): PreparedExternalPreviewImage {
    return withPreviewImageInput(context, source, httpClient) { input, sourceMimeType ->
        val mimeType = normalizeImageMimeType(sourceMimeType)
            ?: imageMimeTypeForLocation(source.location)
            ?: "image/jpeg"
        val displayName = buildPreviewImageDisplayName(
            title = request.title,
            sourceLocation = source.location,
            mimeType = mimeType
        )
        val cacheDirectory = File(context.cacheDir, PREVIEW_IMAGE_SHARE_CACHE_DIRECTORY)
        if (!cacheDirectory.isDirectory && !cacheDirectory.mkdirs()) {
            throw IOException("无法创建图片分享缓存")
        }
        cleanupExpiredPreviewImageShares(cacheDirectory)

        val sourceIdentity = buildString {
            append(request.key)
            append('\u0000')
            append(source.location)
            source.headers.toSortedMap().forEach { (name, value) ->
                append('\u0000')
                append(name)
                append('=')
                append(value)
            }
        }
        val target = File(
            cacheDirectory,
            "${Integer.toHexString(sourceIdentity.hashCode())}_$displayName"
        )
        val temporary = File(cacheDirectory, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use { output -> input.copyTo(output) }
            if (target.exists() && !target.delete()) {
                throw IOException("无法更新图片分享缓存")
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } catch (throwable: Throwable) {
            temporary.delete()
            throw throwable
        }
        PreparedExternalPreviewImage(
            uri = contentUriForFile(context, target),
            mimeType = mimeType
        )
    }
}

private fun previewImageContentUriForFile(context: Context, file: File): Uri {
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

private fun cleanupExpiredPreviewImageShares(directory: File) {
    val expiresBefore = System.currentTimeMillis() - PREVIEW_IMAGE_SHARE_CACHE_MAX_AGE_MS
    directory.listFiles()?.forEach { file ->
        if (file.isFile && file.lastModified() < expiresBefore) file.delete()
    }
}

private const val PREVIEW_IMAGE_SHARE_CACHE_DIRECTORY = "shared_preview_images"
private const val PREVIEW_IMAGE_SHARE_CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1_000L

private fun <T> withPreviewImageInput(
    context: Context,
    source: PreviewImageSaveSource,
    httpClient: OkHttpClient,
    block: (InputStream, String?) -> T
): T {
    val location = source.location
    return when {
        location.startsWith("http://", ignoreCase = true) || location.startsWith("https://", ignoreCase = true) -> {
            val requestBuilder = Request.Builder().url(location)
            source.headers.forEach { (name, value) -> requestBuilder.header(name, value) }
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("图片下载失败：HTTP ${response.code}")
                val body = response.body ?: throw IOException("图片下载结果为空")
                body.byteStream().use { input ->
                    block(input, body.contentType()?.toString())
                }
            }
        }
        location.startsWith("content://", ignoreCase = true) -> {
            val uri = Uri.parse(location)
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("无法读取图片")
            input.use { block(it, context.contentResolver.getType(uri)) }
        }
        location.startsWith("file://", ignoreCase = true) -> {
            val path = Uri.parse(location).path ?: throw IOException("图片路径无效")
            FileInputStream(File(path)).use { block(it, imageMimeTypeForLocation(path)) }
        }
        else -> {
            FileInputStream(File(location)).use { block(it, imageMimeTypeForLocation(location)) }
        }
    }
}

private fun savePreviewImageWithMediaStore(
    context: Context,
    input: InputStream,
    displayName: String,
    mimeType: String
): Uri {
    val resolver = context.contentResolver
    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Eara")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(collection, values) ?: throw IOException("无法创建相册文件")
    return try {
        resolver.openOutputStream(uri, "w")?.use { output -> input.copyTo(output) }
            ?: throw IOException("无法写入相册文件")
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null
        )
        uri
    } catch (throwable: Throwable) {
        resolver.delete(uri, null, null)
        throw throwable
    }
}

@Suppress("DEPRECATION")
private fun savePreviewImageToLegacyPictures(
    context: Context,
    input: InputStream,
    displayName: String,
    mimeType: String
): Uri {
    val directory = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "Eara"
    )
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建相册目录")
    val target = uniqueLegacyGalleryFile(directory, displayName)
    try {
        FileOutputStream(target).use { output -> input.copyTo(output) }
    } catch (throwable: Throwable) {
        target.delete()
        throw throwable
    }
    MediaScannerConnection.scanFile(
        context,
        arrayOf(target.absolutePath),
        arrayOf(mimeType),
        null
    )
    return Uri.fromFile(target)
}

private fun uniqueLegacyGalleryFile(directory: File, displayName: String): File {
    val initial = File(directory, displayName)
    if (!initial.exists()) return initial
    val extension = displayName.substringAfterLast('.', "")
    val baseName = displayName.substringBeforeLast('.', displayName)
    var suffix = 2
    while (true) {
        val candidateName = if (extension.isBlank()) "$baseName ($suffix)" else "$baseName ($suffix).$extension"
        val candidate = File(directory, candidateName)
        if (!candidate.exists()) return candidate
        suffix += 1
    }
}

private fun normalizeImageMimeType(value: String?): String? {
    return value
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.startsWith("image/") }
}

private fun imageMimeTypeForLocation(location: String): String? {
    val extension = location.substringBefore('?').substringAfterLast('.', "").lowercase()
    return imageMimeTypeForExtension(extension)
}

private fun imageMimeTypeForExtension(extension: String): String? {
    if (extension.isBlank()) return null
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?.takeIf { it.startsWith("image/") }
        ?: when (extension.lowercase()) {
            "jpg", "jpeg", "jfif" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "avif" -> "image/avif"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            else -> null
        }
}

private fun imageExtensionForMimeType(mimeType: String): String? {
    return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        ?: when (mimeType.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/avif" -> "avif"
            "image/svg+xml" -> "svg"
            "image/x-icon" -> "ico"
            else -> null
        }
}
