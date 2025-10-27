package org.schooldinners.data

import android.content.Context
import android.os.Environment
import androidx.annotation.VisibleForTesting
import java.io.File

class MenuRepository(
    private val context: Context,
    private val assetName: String = DEFAULT_ASSET_NAME,
    private val downloadsFileName: String = DEFAULT_DOWNLOADS_FILE_NAME
) {

    fun loadMenu(): Result<MenuData> =
        runCatching {
            val rawJson = readDownloadsMenu() ?: readBundledAsset()
            MenuJsonParser.parse(rawJson)
        }

    private fun readDownloadsMenu(): String? {
        val candidateFiles = buildList {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { add(File(it, downloadsFileName)) }
            context.getExternalFilesDir(null)?.let { add(File(it, downloadsFileName)) }
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { add(File(it, downloadsFileName)) }
        }.distinct()
        return readFirstExisting(candidateFiles)
    }

    private fun readBundledAsset(): String =
        context.assets.open(assetName).use { stream ->
            stream.bufferedReader().readText()
        }

    companion object {
        const val DEFAULT_DOWNLOADS_FILE_NAME = "SchoolNomNomsMenu.json"
        const val DEFAULT_ASSET_NAME = "wetherby_st_james_n3_nov25_menu.json"
    }
}

@VisibleForTesting
internal fun readFirstExisting(files: List<File>): String? =
    files.firstNotNullOfOrNull { file ->
        runCatching {
            if (file.exists() && file.canRead()) file.readText() else null
        }.getOrNull()
    }
