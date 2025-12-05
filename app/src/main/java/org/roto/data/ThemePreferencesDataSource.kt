package org.roto.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeOption { SYSTEM, LIGHT, DARK, FOREST, SUNSET, OCEAN, BLOSSOM, MIDNIGHT, SAND }

val Context.themePreferencesDataStore by preferencesDataStore(name = "theme_preferences")

class ThemePreferencesDataSource(private val context: Context) {

    private object Keys {
        val THEME_OPTION = stringPreferencesKey("theme_option")
    }

    val themeOptionFlow: Flow<ThemeOption> =
        context.themePreferencesDataStore.data.map { prefs ->
            prefs[Keys.THEME_OPTION]
                ?.let { runCatching { ThemeOption.valueOf(it) }.getOrNull() }
                ?: ThemeOption.SYSTEM
        }

    suspend fun saveTheme(option: ThemeOption) {
        context.themePreferencesDataStore.edit { prefs ->
            prefs[Keys.THEME_OPTION] = option.name
        }
    }
}
