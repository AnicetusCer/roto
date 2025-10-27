package org.schooldinners.data

import android.content.Context
class MenuRepository(
    private val context: Context,
    private val assetName: String = "wetherby_st_james_n3_nov25_menu.json"
) {

    /**
     * Loads the bundled JSON asset and parses it into [MenuData].
     * Future revisions can swap this for persisted storage or user-provided files.
     */
    fun loadBundledMenu(): Result<MenuData> =
        runCatching {
            val rawJson = context.assets.open(assetName).use { stream ->
                stream.bufferedReader().readText()
            }
            MenuJsonParser.parse(rawJson)
        }
}
