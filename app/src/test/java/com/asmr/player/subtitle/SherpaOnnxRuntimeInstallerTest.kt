package com.asmr.player.subtitle

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SherpaOnnxRuntimeInstallerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `installs only the pinned libraries as read only files`() {
        val onnx = "onnx-runtime".toByteArray()
        val jni = "sherpa-jni".toByteArray()
        val archive = createArchive(
            "arm64-v8a/libonnxruntime.so" to onnx,
            "arm64-v8a/libsherpa-onnx-jni.so" to jni
        )
        val descriptor = descriptor(archive, onnx, jni)
        val destination = File(temporaryFolder.root, "runtime/arm64-v8a")

        SherpaOnnxRuntimeInstaller.install(archive, destination, descriptor)

        val onnxFile = File(destination, "libonnxruntime.so")
        val jniFile = File(destination, "libsherpa-onnx-jni.so")
        assertArrayEquals(onnx, onnxFile.readBytes())
        assertArrayEquals(jni, jniFile.readBytes())
        assertFalse(onnxFile.canWrite())
        assertFalse(jniFile.canWrite())
        assertTrue(SherpaOnnxRuntimeInstaller.isInstalled(destination, descriptor))
        makeWritable(destination)
    }

    @Test
    fun `rejects an archive containing an unexpected entry`() {
        val onnx = "onnx-runtime".toByteArray()
        val jni = "sherpa-jni".toByteArray()
        val archive = createArchive(
            "arm64-v8a/libonnxruntime.so" to onnx,
            "arm64-v8a/libsherpa-onnx-jni.so" to jni,
            "../unexpected.so" to byteArrayOf(1)
        )
        val descriptor = descriptor(archive, onnx, jni)
        val destination = File(temporaryFolder.root, "runtime/arm64-v8a")

        val failure = runCatching {
            SherpaOnnxRuntimeInstaller.install(archive, destination, descriptor)
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("未知文件"))
        assertFalse(destination.exists())
    }

    @Test
    fun `rejects a library whose pinned digest does not match`() {
        val onnx = "onnx-runtime".toByteArray()
        val jni = "sherpa-jni".toByteArray()
        val archive = createArchive(
            "arm64-v8a/libonnxruntime.so" to onnx,
            "arm64-v8a/libsherpa-onnx-jni.so" to jni
        )
        val validDescriptor = descriptor(archive, onnx, jni)
        val descriptor = validDescriptor.copy(
            libraries = validDescriptor.libraries.map { library ->
                if (library.fileName == "libsherpa-onnx-jni.so") {
                    library.copy(sha256 = "0".repeat(64))
                } else {
                    library
                }
            }
        )
        val destination = File(temporaryFolder.root, "runtime/arm64-v8a")

        val failure = runCatching {
            SherpaOnnxRuntimeInstaller.install(archive, destination, descriptor)
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("校验失败"))
        assertFalse(destination.exists())
    }

    private fun descriptor(
        archive: File,
        onnx: ByteArray,
        jni: ByteArray
    ) = SherpaOnnxRuntimeDescriptor(
        version = "test",
        abi = "arm64-v8a",
        url = "https://example.com/runtime.zip",
        archiveFileName = archive.name,
        archiveBytes = archive.length(),
        archiveSha256 = sha256(archive),
        libraries = listOf(
            SherpaOnnxRuntimeLibrary(
                archivePath = "arm64-v8a/libonnxruntime.so",
                fileName = "libonnxruntime.so",
                bytes = onnx.size.toLong(),
                sha256 = sha256(onnx)
            ),
            SherpaOnnxRuntimeLibrary(
                archivePath = "arm64-v8a/libsherpa-onnx-jni.so",
                fileName = "libsherpa-onnx-jni.so",
                bytes = jni.size.toLong(),
                sha256 = sha256(jni)
            )
        )
    )

    private fun createArchive(vararg entries: Pair<String, ByteArray>): File {
        val archive = temporaryFolder.newFile("runtime-${System.nanoTime()}.zip")
        ZipOutputStream(archive.outputStream()).use { output ->
            entries.forEach { (name, data) ->
                output.putNextEntry(ZipEntry(name))
                output.write(data)
                output.closeEntry()
            }
        }
        return archive
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun makeWritable(directory: File) {
        directory.walkBottomUp().forEach { file -> file.setWritable(true, false) }
    }
}
