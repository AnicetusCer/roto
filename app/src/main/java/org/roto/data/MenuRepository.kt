package org.roto.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.annotation.VisibleForTesting
import java.io.File

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

    fun loadMenu(preferredUri: Uri?): Result<MenuLoadResult> =
        runCatching {
            val (rawJson, sourceType) = when {
                preferredUri != null -> readExternalFile(preferredUri)?.let { it to MenuSourceType.EXTERNAL_SELECTION }
                    ?: throw IllegalStateException("Unable to read the selected rota file.")
                else -> readDownloadsMenu()?.let { it to MenuSourceType.SCOPED_DOWNLOADS }
                    ?: throw IllegalStateException("No rota JSON found. Save it as $downloadsFileName in the app downloads folder or choose a file manually.")
            }
            MenuLoadResult(
                data = RotoJsonParser.parse(rawJson),
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
