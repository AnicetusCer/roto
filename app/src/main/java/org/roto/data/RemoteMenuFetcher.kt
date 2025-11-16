package org.roto.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.CacheControl

data class RemoteFetchResult(
    val rawJson: String,
    val status: RemoteSourceStatus
)

class RemoteMenuFetcher(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val cacheDir: File by lazy {
        File(context.filesDir, "remote_rotas").apply { if (!exists()) mkdirs() }
    }

    fun fetch(originalUrl: String, forceNetwork: Boolean = false): RemoteFetchResult {
        val normalizedUrl = normalizeUrl(originalUrl)
        val cacheFile = cacheFileFor(normalizedUrl)
        return try {
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "Roto/1.0")
                .apply {
                    if (forceNetwork) {
                        cacheControl(CacheControl.FORCE_NETWORK)
                        header("Cache-Control", "no-cache")
                        header("Pragma", "no-cache")
                    }
                }
                .build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} downloading shared rota.")
                }
                response.body?.string() ?: throw IOException("Empty response from shared rota.")
            }
            cacheFile.writeText(body)
            cacheFile.setLastModified(System.currentTimeMillis())
            RemoteFetchResult(
                rawJson = body,
                status = RemoteSourceStatus(
                    url = normalizedUrl,
                    lastSyncedEpochMillis = cacheFile.lastModified(),
                    isFromCache = false
                )
            )
        } catch (error: Exception) {
            if (!cacheFile.exists()) {
                val reason = error.message ?: "Network error"
                throw IllegalStateException("Couldn't download shared rota: $reason", error)
            }
            val cachedJson = cacheFile.readText()
            RemoteFetchResult(
                rawJson = cachedJson,
                status = RemoteSourceStatus(
                    url = normalizedUrl,
                    lastSyncedEpochMillis = cacheFile.lastModified(),
                    isFromCache = true
                )
            )
        }
    }

    private fun cacheFileFor(url: String): File =
        File(cacheDir, "rota_${url.toSha256()}.json")

    private fun String.toSha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun normalizeUrl(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val host = uri.host?.lowercase() ?: return url
        if (host != "gist.github.com") return url

        val segments = uri.pathSegments
        if (segments.size < 2) return url
        val author = segments[0]
        val gistId = segments[1]
        val remaining = if (segments.size > 2) {
            segments.subList(2, segments.size).joinToString("/")
        } else ""

        return buildString {
            append("https://gist.githubusercontent.com/")
            append(author)
            append('/')
            append(gistId)
            append("/raw")
            if (remaining.isNotEmpty()) {
                append('/')
                append(remaining)
            }
        }
    }
}
