package org.roto.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeOption { SYSTEM, LIGHT, DARK, FOREST, SUNSET, OCEAN, BLOSSOM, MIDNIGHT, SAND }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

val Context.themePreferencesDataStore by preferencesDataStore(name = "theme_preferences")

class ThemePreferencesDataSource(private val context: Context) {

    private object Keys {
        val THEME_OPTION = stringPreferencesKey("theme_option")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val themeOptionFlow: Flow<ThemeOption> =
        context.themePreferencesDataStore.data.map { prefs ->
            prefs[Keys.THEME_OPTION]
                ?.let { runCatching { ThemeOption.valueOf(it) }.getOrNull() }
                ?: ThemeOption.SYSTEM
        }

    val themeModeFlow: Flow<ThemeMode> =
        context.themePreferencesDataStore.data.map { prefs ->
            prefs[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM
        }

    suspend fun saveTheme(option: ThemeOption) {
        context.themePreferencesDataStore.edit { prefs ->
            prefs[Keys.THEME_OPTION] = option.name
        }
    }

    suspend fun saveMode(mode: ThemeMode) {
        context.themePreferencesDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }
}
