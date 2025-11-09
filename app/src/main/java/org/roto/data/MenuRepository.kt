package org.roto.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.annotation.VisibleForTesting
import java.io.File
import kotlinx.serialization.SerializationException

enum class MenuSourceType {
    EXTERNAL_SELECTION,
    SCOPED_DOWNLOADS
}

data class MenuLoadResult(
    val data: RotoData,
    val sourceType: MenuSourceType
)

class MenuRepository(
    private val context: Context,
    private val downloadsFileName: String = DEFAULT_DOWNLOADS_FILE_NAME
) {

    fun loadMenu(preferredUri: Uri?, allowDownloadsFallback: Boolean = true): Result<MenuLoadResult> =
        runCatching {
            val (rawJson, sourceType) = when {
                preferredUri != null -> readExternalFile(preferredUri)?.let { it to MenuSourceType.EXTERNAL_SELECTION }
                    ?: throw IllegalStateException("Unable to read the selected rota file.")
                allowDownloadsFallback -> readDownloadsMenu()?.let { it to MenuSourceType.SCOPED_DOWNLOADS }
                    ?: throw IllegalStateException("No rota file found. Choose a file manually or place one in the app's downloads folder.")
                else -> throw IllegalStateException("No rota file selected. Load one in the app first.")
            }
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
                sourceType = sourceType
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

@VisibleForTesting
internal fun readFirstExisting(files: List<File>): String? =
    files.firstNotNullOfOrNull { file ->
        runCatching {
            if (file.exists() && file.canRead()) file.readText() else null
        }.getOrNull()
    }
