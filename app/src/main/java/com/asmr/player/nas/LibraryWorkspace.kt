package com.asmr.player.nas

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.asmr.player.data.settings.KeystorePasswordStore

/** A process keeps its workspace for its whole lifetime, including already injected DAOs and services. */
internal object LibraryWorkspace {
    var location: WebDavLocation? = null
        private set
    val nas get() = location != null
    val authority = com.asmr.player.BuildConfig.APPLICATION_ID + ".webdav"
    const val DOWNLOADS = ".eara-downloads"
    private const val TAG_PREFIX = "eara.library:"
    private fun prefs(context: Context) = context.getSharedPreferences("library_workspace", Context.MODE_PRIVATE)

    fun initialize(context: Context) {
        val prefs = prefs(context)
        location = if (prefs.getBoolean("nas", false)) savedLocation(context) else null
    }

    fun savedLocation(context: Context): WebDavLocation? = prefs(context).let { prefs ->
        prefs.getString("address", null)?.let { WebDavLocation(it, prefs.getString("username", "").orEmpty()) }
    }

    fun select(context: Context, next: WebDavLocation?, password: String = "") {
        val editor = prefs(context).edit().putBoolean("nas", next != null)
        if (next != null) {
            passwordStore(context, next).save(password)
            editor.putString("address", next.root.toString()).putString("username", next.username)
        }
        check(editor.commit())
    }

    fun savedPassword(context: Context, location: WebDavLocation) = passwordStore(context, location).read()
    private fun passwordStore(context: Context, location: WebDavLocation) =
        KeystorePasswordStore.forWebDav(context, location.id)

    fun name(local: String): String = location?.let { "nas_${it.id}_$local" } ?: local
    fun owns(uri: Uri): Boolean = uri.scheme == "content" && uri.authority == authority
    fun tree(path: String): Uri = DocumentsContract.buildTreeDocumentUri(authority, documentId(path))
    fun documentId(path: String): String = "${checkNotNull(location).id}:$path"
    fun path(id: String): String {
        val prefix = documentId("")
        require(id.startsWith(prefix))
        return id.removePrefix(prefix)
    }
    fun path(uri: Uri): String = path(DocumentsContract.getDocumentId(uri))

    val workerTag get() = TAG_PREFIX + (location?.id ?: "local")
    fun ownsWork(tags: Set<String>) = tags.firstOrNull { it.startsWith(TAG_PREFIX) }
        ?.let { it == workerTag } ?: !nas
}

internal inline fun <reified W : ListenableWorker> libraryWorkRequest() =
    OneTimeWorkRequestBuilder<W>().addTag(LibraryWorkspace.workerTag)

internal class LibraryWorkerFactory : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? {
        if (workerClassName == com.asmr.player.subtitle.SubtitleModelDownloadWorker::class.java.name ||
            LibraryWorkspace.ownsWork(workerParameters.tags)) return null
        return object : Worker(appContext, workerParameters) {
            override fun doWork() = Result.retry()
        }
    }
}
