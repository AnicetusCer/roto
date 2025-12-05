package org.roto.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class MenuSelectionType { LOCAL_FILE, REMOTE_LINK }

data class MenuSelection(
    val type: MenuSelectionType,
    val reference: String,
    val displayName: String?,
    val remoteUrl: String? = null
)

@Serializable
data class RecentRota(
    val type: MenuSelectionType,
    val reference: String,
    val displayName: String?,
    val remoteUrl: String? = null,
    val lastOpenedEpochMillis: Long = System.currentTimeMillis()
)

val Context.menuPreferencesDataStore by preferencesDataStore(name = "menu_preferences")

class MenuPreferencesDataSource(private val context: Context) {

    companion object {
        const val DEFAULT_RECENT_LIMIT = 5
    }

    private object Keys {
        val MENU_URI = stringPreferencesKey("menu_uri")
        val MENU_URI_LABEL = stringPreferencesKey("menu_uri_label")
        val MENU_TYPE = stringPreferencesKey("menu_type")
        val REMOTE_URL = stringPreferencesKey("remote_url")
        val RECENT_LIST = stringPreferencesKey("recent_list")
        val RECENT_LIMIT = intPreferencesKey("recent_limit")
    }

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    val menuSelectionFlow: Flow<MenuSelection?> =
        context.menuPreferencesDataStore.data.map { prefs ->
            val reference = prefs[Keys.MENU_URI] ?: return@map null
            val label = prefs[Keys.MENU_URI_LABEL]
            val typeValue = prefs[Keys.MENU_TYPE]
            val type = typeValue?.let { runCatching { MenuSelectionType.valueOf(it) }.getOrNull() }
                ?: MenuSelectionType.LOCAL_FILE
            val remoteUrl = prefs[Keys.REMOTE_URL]
            if (type == MenuSelectionType.REMOTE_LINK && remoteUrl.isNullOrBlank()) {
                return@map null
            }
            MenuSelection(
                type = type,
                reference = reference,
                displayName = label,
                remoteUrl = remoteUrl
            )
        }

    val recentRotasFlow: Flow<List<RecentRota>> =
        context.menuPreferencesDataStore.data.map { prefs ->
            val limit = prefs[Keys.RECENT_LIMIT] ?: DEFAULT_RECENT_LIMIT
            val raw = prefs[Keys.RECENT_LIST] ?: return@map emptyList()
            val decoded = runCatching { json.decodeFromString<List<RecentRota>>(raw) }.getOrElse { emptyList() }
            decoded.sortedByDescending { it.lastOpenedEpochMillis }.take(limit)
        }

    val recentLimitFlow: Flow<Int> =
        context.menuPreferencesDataStore.data.map { prefs ->
            prefs[Keys.RECENT_LIMIT] ?: DEFAULT_RECENT_LIMIT
        }

    suspend fun saveLocalSelection(uriString: String, displayName: String?) {
        context.menuPreferencesDataStore.edit { prefs ->
            prefs[Keys.MENU_TYPE] = MenuSelectionType.LOCAL_FILE.name
            prefs[Keys.MENU_URI] = uriString
            updateLabel(prefs, displayName)
            prefs.remove(Keys.REMOTE_URL)
            addRecentEntry(
                prefs,
                RecentRota(
                    type = MenuSelectionType.LOCAL_FILE,
                    reference = uriString,
                    displayName = displayName
                )
            )
        }
    }

    suspend fun saveRemoteSelection(uriString: String, displayName: String?, remoteUrl: String) {
        context.menuPreferencesDataStore.edit { prefs ->
            prefs[Keys.MENU_TYPE] = MenuSelectionType.REMOTE_LINK.name
            prefs[Keys.MENU_URI] = uriString
            updateLabel(prefs, displayName)
            prefs[Keys.REMOTE_URL] = remoteUrl
            addRecentEntry(
                prefs,
                RecentRota(
                    type = MenuSelectionType.REMOTE_LINK,
                    reference = uriString,
                    displayName = displayName,
                    remoteUrl = remoteUrl
                )
            )
        }
    }

    suspend fun clearMenuSelection() {
        context.menuPreferencesDataStore.edit { prefs ->
            prefs.remove(Keys.MENU_URI)
            prefs.remove(Keys.MENU_URI_LABEL)
            prefs.remove(Keys.MENU_TYPE)
            prefs.remove(Keys.REMOTE_URL)
        }
    }

    suspend fun setRecentLimit(limit: Int) {
        context.menuPreferencesDataStore.edit { prefs ->
            prefs[Keys.RECENT_LIMIT] = limit
            val current = loadRecents(prefs)
            prefs[Keys.RECENT_LIST] = json.encodeToString(current.take(limit))
        }
    }

    suspend fun clearRecentRotas() {
        context.menuPreferencesDataStore.edit { prefs ->
            prefs.remove(Keys.RECENT_LIST)
        }
    }

    private fun updateLabel(prefs: MutablePreferences, displayName: String?) {
        if (displayName != null) {
            prefs[Keys.MENU_URI_LABEL] = displayName
        } else {
            prefs.remove(Keys.MENU_URI_LABEL)
        }
    }

    private fun addRecentEntry(prefs: MutablePreferences, entry: RecentRota) {
        val limit = prefs[Keys.RECENT_LIMIT] ?: DEFAULT_RECENT_LIMIT
        val existing = loadRecents(prefs)
            .filterNot { it.type == entry.type && (it.reference == entry.reference || (!entry.remoteUrl.isNullOrBlank() && entry.remoteUrl == it.remoteUrl)) }
        val updated = (listOf(entry) + existing).take(limit)
        prefs[Keys.RECENT_LIST] = json.encodeToString(updated)
    }

    private fun loadRecents(prefs: MutablePreferences): List<RecentRota> {
        val raw = prefs[Keys.RECENT_LIST] ?: return emptyList()
        return runCatching { json.decodeFromString<List<RecentRota>>(raw) }.getOrElse { emptyList() }
    }

}
