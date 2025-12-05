package org.roto.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.roto.data.ThemeOption
import org.roto.data.ThemePreferencesDataSource

class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = ThemePreferencesDataSource(application)

    val themeOption = preferences.themeOptionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeOption.SYSTEM
        )

    fun setTheme(option: ThemeOption) {
        viewModelScope.launch {
            preferences.saveTheme(option)
        }
    }
}
