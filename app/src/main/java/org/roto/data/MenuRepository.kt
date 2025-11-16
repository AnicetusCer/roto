package org.roto.data

import android.content.Context
import android.os.Environment
import androidx.annotation.VisibleForTesting
import java.io.File
import kotlinx.serialization.SerializationException
import android.net.Uri

enum class MenuSourceType {
    EXTERNAL_SELECTION,
    SCOPED_DOWNLOADS,
    REMOTE_LINK
}

data class MenuLoadResult(
    val data: RotoData,
    val sourceType: MenuSourceType,
    val remoteStatus: RemoteSourceStatus? = null
)

data class RemoteSourceStatus(
    val url: String,
    val lastSyncedEpochMillis: Long,
    val isFromCache: Boolean
)

class MenuRepository(
    private val context: Context,
    private val remoteFetcher: RemoteMenuFetcher = RemoteMenuFetcher(context),
    private val downloadsFileName: String = DEFAULT_DOWNLOADS_FILE_NAME
) {

    fun loadMenu(
        selection: MenuSelection?,
        allowDownloadsFallback: Boolean = true,
        forceRemoteRefresh: Boolean = false
    ): Result<MenuLoadResult> =
        runCatching {
            val rawResult = when (selection?.type) {
                MenuSelectionType.LOCAL_FILE -> {
                    val uri = runCatching { Uri.parse(selection.reference) }.getOrNull()
                        ?: throw IllegalStateException("Invalid file reference. Choose the rota file again.")
                    val json = readExternalFile(uri) ?: throw IllegalStateException("Unable to read the selected rota file.")
                    RawMenuResult(
                        rawJson = json,
                        sourceType = MenuSourceType.EXTERNAL_SELECTION
                    )
                }
                MenuSelectionType.REMOTE_LINK -> {
                    val uri = runCatching { Uri.parse(selection.reference) }.getOrNull()
                        ?: throw IllegalStateException("Invalid mirror reference. Reload the shared link.")
                    val remoteUrl = selection.remoteUrl ?: throw IllegalStateException("Missing shared link URL.")
                    val shouldSync = forceRemoteRefresh
                    if (shouldSync) {
                        val fetchResult = runCatching { remoteFetcher.fetch(remoteUrl, forceNetwork = true) }.getOrNull()
                        if (fetchResult != null) {
                            if (!context.writeTextToUri(uri, fetchResult.rawJson)) {
                                throw IllegalStateException("Couldn't update the shared rota file.")
                            }
                            RawMenuResult(
                                rawJson = fetchResult.rawJson,
                                sourceType = MenuSourceType.REMOTE_LINK,
                                remoteStatus = fetchResult.status
                            )
                        } else {
                            val cached = readExternalFile(uri)
                                ?: throw IllegalStateException("Shared link unreachable and no saved copy found.")
                            RawMenuResult(
                                rawJson = cached,
                                sourceType = MenuSourceType.REMOTE_LINK
                            )
                        }
                    } else {
                        val cached = readExternalFile(uri)
                            ?: throw IllegalStateException("Shared link file missing. Refresh to download it again.")
                        RawMenuResult(
                            rawJson = cached,
                            sourceType = MenuSourceType.REMOTE_LINK
                        )
                    }
                }
                null -> {
                    if (!allowDownloadsFallback) {
                        throw IllegalStateException("No rota file selected. Load one in the app first.")
                    }
                    val json = readDownloadsMenu()
                        ?: throw IllegalStateException("No rota file found. Choose a file manually or place one in the app's downloads folder.")
                    RawMenuResult(
                        rawJson = json,
                        sourceType = MenuSourceType.SCOPED_DOWNLOADS
                    )
                }
            }
            val (rawJson, sourceType, remoteStatus) = rawResult
            val parsed = try {
                RotoJsonParser.parse(rawJson)
            } catch (e: SerializationException) {
                throw IllegalStateException(
                    "That file couldn't be understood. Make sure it matches the rota JSON examples or regenerate it with the helper prompt.",
                    e
                )
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException(e.message ?: "Unsupported rota schema. Please regenerate the file.", e)
            }
            val validationIssues = RotoValidator.validate(parsed)
            if (validationIssues.isNotEmpty()) {
                val bulletList = validationIssues.joinToString(separator = "\n• ", prefix = "• ")
                throw IllegalStateException(
                    "The rota file is missing some required details:\n$bulletList\nPlease fix these and try again."
                )
            }
            MenuLoadResult(
                data = parsed,
                sourceType = sourceType,
                remoteStatus = remoteStatus
            )
        }

    private fun readExternalFile(uri: Uri): String? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            }
        }.getOrNull()

    private fun readDownloadsMenu(): String? {
        val candidateFiles = buildList {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { add(File(it, downloadsFileName)) }
            context.getExternalFilesDir(null)?.let { add(File(it, downloadsFileName)) }
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { add(File(it, downloadsFileName)) }
        }.distinct()
        return readFirstExisting(candidateFiles)
    }

    companion object {
        const val DEFAULT_DOWNLOADS_FILE_NAME = "RotoRota.json"
    }
}

private data class RawMenuResult(
    val rawJson: String,
    val sourceType: MenuSourceType,
    val remoteStatus: RemoteSourceStatus? = null
)

@VisibleForTesting
internal fun readFirstExisting(files: List<File>): String? =
    files.firstNotNullOfOrNull { file ->
        runCatching {
            if (file.exists() && file.canRead()) file.readText() else null
        }.getOrNull()
    }

private fun Context.writeTextToUri(uri: Uri, text: String): Boolean =
    runCatching {
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.writer().use { writer ->
                    writer.write(text)
                }
                true
            } ?: false
        } else {
            val filePath = uri.path ?: return@runCatching false
            val targetFile = File(filePath)
            targetFile.writeText(text)
            true
        }
    }.getOrDefault(false)
