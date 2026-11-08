package com.asmr.player.subtitle

import com.asmr.player.i18n.appString
import com.asmr.player.R

import android.content.Context
import android.os.Build
import com.asmr.player.BuildConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

internal data class SherpaOnnxRuntimeLibrary(
    val archivePath: String,
    val fileName: String,
    val bytes: Long,
    val sha256: String
)

internal data class SherpaOnnxRuntimeDescriptor(
    val version: String,
    val abi: String,
    val url: String,
    val archiveFileName: String,
    val archiveBytes: Long,
    val archiveSha256: String,
    val libraries: List<SherpaOnnxRuntimeLibrary>
) {
    val extractedBytes: Long = libraries.sumOf(SherpaOnnxRuntimeLibrary::bytes)
}

internal object SherpaOnnxRuntimeRelease {
    val current: SherpaOnnxRuntimeDescriptor
        get() = SherpaOnnxRuntimeDescriptor(
            version = "1.13.2",
            abi = "arm64-v8a",
            url = BuildConfig.SUBTITLE_RUNTIME_URL,
            archiveFileName = "sherpa-onnx-runtime-1.13.2-android-arm64-v8a.zip",
            archiveBytes = 11_311_151L,
            archiveSha256 = "bfa564c5da27a7ab734d4c788cafd7c95c1e4934e02056be24358532d3d33c2e",
            libraries = listOf(
                SherpaOnnxRuntimeLibrary(
                    archivePath = "arm64-v8a/libonnxruntime.so",
                    fileName = "libonnxruntime.so",
                    bytes = 25_831_632L,
                    sha256 = "4d2318b3849abb8862133d3068fc7e807ed8b2671cc6d83657fff2fcb9e1caad"
                ),
                SherpaOnnxRuntimeLibrary(
                    archivePath = "arm64-v8a/libsherpa-onnx-jni.so",
                    fileName = "libsherpa-onnx-jni.so",
                    bytes = 4_623_192L,
                    sha256 = "fc072f201dc1923ee98b594eb61c796b538ef087f7f18d08dcfdf0565167a8bd"
                )
            )
        )
}

internal class SherpaOnnxRuntimeRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    val descriptor: SherpaOnnxRuntimeDescriptor = SherpaOnnxRuntimeRelease.current

    fun isInstalled(): Boolean = SherpaOnnxRuntimeInstaller.isInstalled(
        directory = runtimeDirectory(),
        descriptor = descriptor
    )

    fun requireInstalledDirectory(): File = runtimeDirectory().takeIf {
        SherpaOnnxRuntimeInstaller.isInstalled(it, descriptor)
    } ?: throw IllegalStateException(RUNTIME_REQUIRED_MESSAGE)

    internal fun ensureDownloadDirectory(): File = downloadDirectory().apply {
        check(exists() || mkdirs()) { appString(R.string.ui_unable_to_create_subtitle_runtime_download_directory) }
    }

    internal fun partialArchiveFile(): File = File(
        ensureDownloadDirectory(),
        "${descriptor.archiveFileName}.part"
    )

    internal fun downloadedArchiveBytes(): Long = partialArchiveFile()
        .length()
        .coerceIn(0L, descriptor.archiveBytes)

    internal fun deletePartialArchive(): Boolean {
        val partial = File(downloadDirectory(), "${descriptor.archiveFileName}.part")
        return !partial.exists() || partial.delete()
    }

    internal fun installDownloadedArchive() {
        val archive = partialArchiveFile()
        try {
            SherpaOnnxRuntimeInstaller.install(
                archive = archive,
                destinationDirectory = runtimeDirectory(),
                descriptor = descriptor
            )
        } catch (error: Throwable) {
            archive.delete()
            throw error
        }
        check(archive.delete()) { appString(R.string.ui_unable_to_clean_subtitle_runtime_archive) }
        downloadDirectory().takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete()
    }

    private fun runtimeDirectory(): File = File(
        File(File(appContext.filesDir, RUNTIME_ROOT_DIRECTORY_NAME), descriptor.version),
        descriptor.abi
    )

    private fun downloadDirectory(): File = File(
        appContext.filesDir,
        RUNTIME_DOWNLOAD_DIRECTORY_NAME
    )

    companion object {
        val RUNTIME_REQUIRED_MESSAGE: String get() = appString(R.string.ui_please_download_the_japanese_subtitle_component_in_settings)
        private const val RUNTIME_ROOT_DIRECTORY_NAME = "subtitle-runtimes"
        private const val RUNTIME_DOWNLOAD_DIRECTORY_NAME = "subtitle-runtime-downloads"

        @Volatile
        private var instance: SherpaOnnxRuntimeRepository? = null

        fun get(context: Context): SherpaOnnxRuntimeRepository {
            return instance ?: synchronized(this) {
                instance ?: SherpaOnnxRuntimeRepository(context).also { instance = it }
            }
        }
    }
}

internal object SherpaOnnxRuntimeInstaller {
    fun isInstalled(
        directory: File,
        descriptor: SherpaOnnxRuntimeDescriptor
    ): Boolean = directory.isDirectory && descriptor.libraries.all { library ->
        val file = File(directory, library.fileName)
        file.isFile && file.length() == library.bytes && !file.canWrite()
    }

    fun install(
        archive: File,
        destinationDirectory: File,
        descriptor: SherpaOnnxRuntimeDescriptor
    ) {
        requirePinnedFile(archive, descriptor.archiveBytes, descriptor.archiveSha256)
        val parent = destinationDirectory.parentFile
            ?: throw IllegalStateException(appString(R.string.ui_the_subtitle_runtime_directory_is_invalid))
        check(parent.exists() || parent.mkdirs()) { appString(R.string.ui_unable_to_create_subtitles_runtime_directory) }
        val stagingDirectory = File(parent, "${destinationDirectory.name}.installing")
        if (stagingDirectory.exists()) {
            check(stagingDirectory.deleteRecursively()) { appString(R.string.ui_unable_to_clean_old_subtitles_runtime_temp_directory) }
        }
        check(stagingDirectory.mkdir()) { appString(R.string.ui_unable_to_create_subtitle_runtime_temporary_directory) }

        try {
            extractPinnedLibraries(archive, stagingDirectory, descriptor)
            check(isInstalled(stagingDirectory, descriptor)) { appString(R.string.ui_the_installation_result_is_invalid_when_subtitles_are) }
            if (destinationDirectory.exists()) {
                check(destinationDirectory.deleteRecursively()) { appString(R.string.ui_unable_to_replace_old_subtitle_runtime) }
            }
            check(stagingDirectory.renameTo(destinationDirectory)) { appString(R.string.ui_unable_to_save_subtitle_runtime) }
        } catch (error: Throwable) {
            stagingDirectory.deleteRecursively()
            throw error
        }
    }

    fun verifyForLoading(
        directory: File,
        descriptor: SherpaOnnxRuntimeDescriptor
    ) {
        check(isInstalled(directory, descriptor)) { appString(R.string.ui_subtitles_are_missing_or_still_writable_during_runtime) }
        descriptor.libraries.forEach { library ->
            requirePinnedFile(File(directory, library.fileName), library.bytes, library.sha256)
        }
    }

    private fun extractPinnedLibraries(
        archive: File,
        stagingDirectory: File,
        descriptor: SherpaOnnxRuntimeDescriptor
    ) {
        val expectedByPath = descriptor.libraries.associateBy(SherpaOnnxRuntimeLibrary::archivePath)
        val extractedPaths = mutableSetOf<String>()
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                check(!entry.isDirectory) { appString(R.string.ui_the_subtitle_runtime_compressed_package_contains_redundant_directories) }
                val library = expectedByPath[entry.name]
                    ?: throw IllegalStateException(appString(R.string.ui_the_subtitle_runtime_archive_contains_unknown_files, entry.name))
                check(extractedPaths.add(entry.name)) { appString(R.string.ui_subtitle_runtime_archive_contains_duplicate_files, entry.name) }
                val outputFile = File(stagingDirectory, library.fileName)
                check(outputFile.canonicalFile.parentFile == stagingDirectory.canonicalFile) {
                    appString(R.string.ui_invalid_file_path_when_running_subtitles)
                }
                writePinnedLibrary(input, outputFile, library)
                input.closeEntry()
            }
        }
        check(extractedPaths == expectedByPath.keys) { appString(R.string.ui_the_subtitle_runtime_compressed_package_is_missing_necessary) }
    }

    private fun writePinnedLibrary(
        input: ZipInputStream,
        outputFile: File,
        library: SherpaOnnxRuntimeLibrary
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        var writtenBytes = 0L
        FileOutputStream(outputFile).use { output ->
            val buffer = ByteArray(FILE_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                writtenBytes += read.toLong()
                check(writtenBytes <= library.bytes) { appString(R.string.ui_file_size_exception_when_running_subtitles, library.fileName) }
                output.write(buffer, 0, read)
                digest.update(buffer, 0, read)
            }
            output.flush()
            output.fd.sync()
        }
        check(writtenBytes == library.bytes) { appString(R.string.ui_incomplete_subtitle_runtime_file, library.fileName) }
        check(digest.toHex().equals(library.sha256, ignoreCase = true)) {
            appString(R.string.ui_file_verification_failed_when_running_subtitles, library.fileName)
        }
        outputFile.setReadable(true, false)
        outputFile.setExecutable(true, false)
        check(outputFile.setWritable(false, false) && !outputFile.canWrite()) {
            appString(R.string.ui_unable_to_make_subtitle_runtime_file_read_only, library.fileName)
        }
    }

    private fun requirePinnedFile(file: File, expectedBytes: Long, expectedSha256: String) {
        check(file.isFile && file.length() == expectedBytes) { appString(R.string.ui_file_size_verification_failed, file.name) }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(FILE_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        check(digest.toHex().equals(expectedSha256, ignoreCase = true)) {
            appString(R.string.ui_file_verification_failed, file.name)
        }
    }

    private fun MessageDigest.toHex(): String = digest().joinToString("") { byte ->
        "%02x".format(byte)
    }

    private const val FILE_BUFFER_BYTES = 1024 * 1024
}

internal object SherpaOnnxNativeLoader {
    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val repository = SherpaOnnxRuntimeRepository.get(context)
            val descriptor = repository.descriptor
            check(Build.SUPPORTED_ABIS.any { it == descriptor.abi }) {
                appString(R.string.ui_the_current_device_does_not_support_the_subtitle, descriptor.abi)
            }
            val directory = repository.requireInstalledDirectory()
            SherpaOnnxRuntimeInstaller.verifyForLoading(directory, descriptor)
            descriptor.libraries.forEach { library ->
                System.load(File(directory, library.fileName).absolutePath)
            }
            loaded = true
        }
    }
}
