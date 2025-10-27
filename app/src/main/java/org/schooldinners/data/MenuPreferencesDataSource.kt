package org.schooldinners.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class MenuSelection(
    val uriString: String,
    val displayName: String?
)

val Context.menuPreferencesDataStore by preferencesDataStore(name = "menu_preferences")

class MenuPreferencesDataSource(private val context: Context) {

    private object Keys {
        val MENU_URI = stringPreferencesKey("menu_uri")
        val MENU_URI_LABEL = stringPreferencesKey("menu_uri_label")
    }

    val menuSelectionFlow: Flow<MenuSelection?> =
        context.menuPreferencesDataStore.data.map { prefs ->
            val uri = prefs[Keys.MENU_URI] ?: return@map null
            val label = prefs[Keys.MENU_URI_LABEL]
            MenuSelection(uriString = uri, displayName = label)
        }

    suspend fun saveMenuSelection(uriString: String, displayName: String?) {
        context.menuPreferencesDataStore.edit { prefs ->
            prefs[Keys.MENU_URI] = uriString
            if (displayName != null) {
                prefs[Keys.MENU_URI_LABEL] = displayName
            } else {
                prefs.remove(Keys.MENU_URI_LABEL)
            }
        }
    }

}
