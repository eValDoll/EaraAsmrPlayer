package com.asmr.player.nas

import android.util.Xml
import com.asmr.player.data.remote.useDownloadResponse
import java.io.InputStream
import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import okio.source
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class WebDavLocation(address: String, val username: String) {
    val root: HttpUrl = address.trim().toHttpUrl().let { url ->
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null)
        if (url.encodedPath.endsWith('/')) url else url.newBuilder().addPathSegment("").build()
    }

    val id: String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(("$root\n$username").toByteArray()).take(16).joinToString("") { "%02x".format(it) }

    fun url(path: String): HttpUrl {
        if (path.isEmpty()) return root
        val builder = root.newBuilder()
        path.split('/').forEach {
            require(it.isNotEmpty() && it != "." && it != ".." && '\\' !in it && '\u0000' !in it)
            builder.addPathSegment(it)
        }
        return builder.build()
    }
}

internal data class DavEntry(val path: String, val directory: Boolean, val size: Long, val modified: Long) {
    val name get() = path.substringAfterLast('/')
}

internal class WebDavHttpException(val status: Int) : IOException("WebDAV HTTP $status")

internal class WebDavClient(val location: WebDavLocation, password: String) {
    val authorization = location.username.takeIf { it.isNotEmpty() }?.let { Credentials.basic(it, password, Charsets.UTF_8) }

    private fun request(url: HttpUrl) = Request.Builder().url(url).apply {
        authorization?.let { header("Authorization", it) }
    }

    private fun execute(request: Request): Response {
        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw WebDavHttpException(response.code)
        }
        return response
    }

    fun get(path: String, range: String? = null): Response = execute(request(location.url(path)).apply {
        range?.let { header("Range", it) }
    }.build())

    fun createFile(path: String) {
        val create = request(location.url(path)).header("If-None-Match", "*").put(ByteArray(0).toRequestBody())
        execute(create.build()).close()
    }

    suspend fun put(path: String, body: RequestBody) {
        uploadHttp.newCall(request(location.url(path)).put(body).build()).useDownloadResponse { response ->
            if (!response.isSuccessful) throw WebDavHttpException(response.code)
        }
    }

    /** A lost PUT response leaves publication uncertain. Only reuse the remote file if its contents match. */
    suspend fun matchesContent(path: String, openLocal: () -> InputStream): Boolean =
        http.newCall(request(location.url(path)).build()).useDownloadResponse { response ->
            if (!response.isSuccessful) throw WebDavHttpException(response.code)
            fun digest(source: okio.BufferedSource): okio.ByteString {
                val hash = HashingSink.sha256(blackholeSink())
                source.readAll(hash)
                return hash.hash
            }
            val remote = digest(checkNotNull(response.body).source())
            openLocal().source().buffer().use { digest(it) == remote }
        }

    fun mkdir(path: String) {
        execute(request(location.url(path)).method("MKCOL", null).build()).close()
    }

    fun ensureDownloadDirectory() {
        try {
            check(stat(LibraryWorkspace.DOWNLOADS).directory)
        } catch (missing: WebDavHttpException) {
            if (missing.status != 404) throw missing
            mkdir(LibraryWorkspace.DOWNLOADS)
        }
    }

    fun delete(path: String) {
        require(path.isNotBlank())
        execute(request(location.url(path)).delete().build()).close()
    }

    fun stat(path: String): DavEntry = properties(path, false).single { it.path == path }

    fun list(path: String): List<DavEntry> = properties(path, true).filter { it.path != path }

    private fun properties(path: String, children: Boolean): List<DavEntry> {
        val url = location.url(path)
        val collectionUrl = if (url.encodedPath.endsWith('/')) url else url.newBuilder().addPathSegment("").build()
        val queryUrl = if (children) collectionUrl else url
        val propfind = request(queryUrl).header("Depth", if (children) "1" else "0")
            .method("PROPFIND", propertyRequest).build()
        val initial = http.newCall(propfind).execute()
        val redirect = initial.header("Location")?.let(queryUrl::resolve)
        val result = if (initial.isRedirect && redirect == collectionUrl && queryUrl != collectionUrl) {
            initial.close()
            execute(propfind.newBuilder().url(collectionUrl).build())
        } else initial
        return result.use { response ->
            if (response.code != 207) throw WebDavHttpException(response.code)
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(checkNotNull(response.body).byteStream(), null)
            }
            val entries = mutableListOf<DavEntry>()
            if (parser.nextTag() != XmlPullParser.START_TAG || parser.name != "multistatus" || parser.namespace != DAV) {
                throw IOException("Invalid WebDAV multistatus")
            }
            while (parser.nextTag() == XmlPullParser.START_TAG) {
                if (parser.name == "response" && parser.namespace == DAV) {
                    val entry = readResponse(parser, collectionUrl, path)
                    if (entry != null) entries += entry
                } else parser.skipElement()
            }
            if (entries.none { it.path == path && (!children || it.directory) }) throw IOException("Incomplete WebDAV listing")
            entries.distinctBy { it.path }
        }
    }

    private fun readResponse(parser: XmlPullParser, base: HttpUrl, parent: String): DavEntry? {
        var href: String? = null
        var directory: Boolean? = null
        var size = 0L
        var modified = 0L
        while (parser.nextTag() == XmlPullParser.START_TAG) {
            when (parser.namespace to parser.name) {
                DAV to "href" -> href = parser.nextText()
                DAV to "status" -> if (!success(parser.nextText())) throw IOException("Incomplete WebDAV response")
                DAV to "propstat" -> {
                    var ok = false
                    var isDir: Boolean? = null
                    var length: Long? = null
                    var date: Long? = null
                    while (parser.nextTag() == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "status" -> ok = success(parser.nextText())
                            "prop" -> while (parser.nextTag() == XmlPullParser.START_TAG) {
                                when (parser.name) {
                                    "resourcetype" -> {
                                        isDir = false
                                        while (parser.nextTag() == XmlPullParser.START_TAG) {
                                            if (parser.name == "collection" && parser.namespace == DAV) isDir = true
                                            parser.skipElement()
                                        }
                                    }
                                    "getcontentlength" -> length = parser.nextText().toLongOrNull()
                                    "getlastmodified" -> date = parseDate(parser.nextText())
                                    else -> parser.skipElement()
                                }
                            }
                            else -> parser.skipElement()
                        }
                    }
                    if (ok) {
                        directory = isDir ?: directory
                        size = length ?: size
                        modified = date ?: modified
                    }
                }
                else -> parser.skipElement()
            }
        }
        val resolved = href?.let(base::resolve) ?: throw IOException("Invalid WebDAV href")
        val root = location.root
        if (resolved.scheme != root.scheme || resolved.host != root.host || resolved.port != root.port ||
            resolved.username.isNotEmpty() || resolved.password.isNotEmpty()) return null
        val segments = resolved.pathSegments.dropLastWhile(String::isEmpty)
        val prefix = root.pathSegments.dropLastWhile(String::isEmpty)
        if (segments.take(prefix.size) != prefix) return null
        val relative = segments.drop(prefix.size)
        if (relative.any { it.isEmpty() || '/' in it || '\\' in it || it == "." || it == ".." }) return null
        val path = relative.joinToString("/")
        if (path != parent && path.substringBeforeLast('/', "") != parent) return null
        return DavEntry(path, directory ?: throw IOException("Missing WebDAV resource type"), size, modified)
    }

    companion object {
        private const val DAV = "DAV:"
        val http = OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
            .followRedirects(false).followSslRedirects(false).build()
        // A NAS may still be flushing the file after the request body has been sent.
        private val uploadHttp = http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
        private val propertyRequest = """<D:propfind xmlns:D="DAV:"><D:prop><D:resourcetype/><D:getcontentlength/><D:getlastmodified/></D:prop></D:propfind>"""
            .toRequestBody("application/xml; charset=utf-8".toMediaType())
        private fun success(status: String) = status.split(' ').getOrNull(1)?.toIntOrNull() in 200..299
        private fun parseDate(value: String) = runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(value)?.time
        }.getOrNull()
    }
}

private fun XmlPullParser.skipElement() {
    val start = depth
    while (next() != XmlPullParser.END_TAG || depth != start) {
        if (eventType == XmlPullParser.END_DOCUMENT) throw IOException("Truncated WebDAV response")
    }
}
