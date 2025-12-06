package org.roto.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import org.roto.data.ThemeMode
import org.roto.data.ThemeOption
import org.roto.data.ThemePreferencesDataSource

data class ThemeUiState(
    val option: ThemeOption = ThemeOption.SYSTEM,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val applySystemPadding: Boolean = true
)

class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = ThemePreferencesDataSource(application)

    val themeState = combine(
        preferences.themeOptionFlow,
        preferences.themeModeFlow,
        preferences.applySystemPaddingFlow
    ) { option, mode, padding ->
        ThemeUiState(option = option, mode = mode, applySystemPadding = padding)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeUiState()
        )

    fun setTheme(option: ThemeOption) {
        viewModelScope.launch {
            preferences.saveTheme(option)
        }
    }

    fun setMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferences.saveMode(mode)
        }
    }

    fun setApplySystemPadding(apply: Boolean) {
        viewModelScope.launch {
            preferences.saveApplySystemPadding(apply)
        }
    }
}
