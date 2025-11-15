package org.roto.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request

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

    fun fetch(url: String): RemoteFetchResult {
        val cacheFile = cacheFileFor(url)
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Roto/1.0")
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
                    url = url,
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
                    url = url,
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
}
