package org.roto.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class MenuSelectionType { LOCAL_FILE, REMOTE_LINK }

data class MenuSelection(
    val type: MenuSelectionType,
    val reference: String,
    val displayName: String?
)

val Context.menuPreferencesDataStore by preferencesDataStore(name = "menu_preferences")

class MenuPreferencesDataSource(private val context: Context) {

    private object Keys {
        val MENU_URI = stringPreferencesKey("menu_uri")
        val MENU_URI_LABEL = stringPreferencesKey("menu_uri_label")
        val MENU_TYPE = stringPreferencesKey("menu_type")
    }

    val menuSelectionFlow: Flow<MenuSelection?> =
        context.menuPreferencesDataStore.data.map { prefs ->
            val reference = prefs[Keys.MENU_URI] ?: return@map null
            val label = prefs[Keys.MENU_URI_LABEL]
            val typeValue = prefs[Keys.MENU_TYPE]
            val type = typeValue?.let { runCatching { MenuSelectionType.valueOf(it) }.getOrNull() }
                ?: MenuSelectionType.LOCAL_FILE
            MenuSelection(
                type = type,
                reference = reference,
                displayName = label
            )
        }

    suspend fun saveLocalSelection(uriString: String, displayName: String?) {
        context.menuPreferencesDataStore.edit { prefs ->
            prefs[Keys.MENU_TYPE] = MenuSelectionType.LOCAL_FILE.name
            prefs[Keys.MENU_URI] = uriString
            updateLabel(prefs, displayName)
        }
    }

    suspend fun saveRemoteSelection(url: String, displayName: String?) {
        context.menuPreferencesDataStore.edit { prefs ->
            prefs[Keys.MENU_TYPE] = MenuSelectionType.REMOTE_LINK.name
            prefs[Keys.MENU_URI] = url
            updateLabel(prefs, displayName)
        }
    }

    suspend fun clearMenuSelection() {
        context.menuPreferencesDataStore.edit { prefs ->
            prefs.remove(Keys.MENU_URI)
            prefs.remove(Keys.MENU_URI_LABEL)
            prefs.remove(Keys.MENU_TYPE)
        }
    }

    private fun updateLabel(prefs: MutablePreferences, displayName: String?) {
        if (displayName != null) {
            prefs[Keys.MENU_URI_LABEL] = displayName
        } else {
            prefs.remove(Keys.MENU_URI_LABEL)
        }
    }

}
