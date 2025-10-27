package org.schooldinners.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.schooldinners.data.MenuRepository
import org.schooldinners.domain.DayMenuResult
import org.schooldinners.domain.getMenuForDate

data class MenuUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val todayMenu: DayMenuResult? = null,
    val tomorrowMenu: DayMenuResult? = null
)

class MenuViewModel(
    application: Application,
    private val repository: MenuRepository = MenuRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MenuUiState())
    val uiState: StateFlow<MenuUiState> = _uiState

    init {
        loadMenus()
    }

    fun refresh() {
        loadMenus()
    }

    private fun loadMenus() {
        viewModelScope.launch {
            _uiState.value = MenuUiState(isLoading = true)
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            repository.loadBundledMenu()
                .onSuccess { menuData ->
                    val todayMenu = getMenuForDate(menuData, today)
                    val tomorrowMenu = getMenuForDate(menuData, tomorrow)

                    if (tomorrowMenu == null && todayMenu == null) {
                        _uiState.value = MenuUiState(
                            isLoading = false,
                            error = "No menu found for today or tomorrow. Check your JSON file."
                        )
                    } else {
                        _uiState.value = MenuUiState(
                            isLoading = false,
                            todayMenu = todayMenu,
                            tomorrowMenu = tomorrowMenu
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.value = MenuUiState(
                        isLoading = false,
                        error = error.message ?: "Failed to load menu data."
                    )
                }
        }
    }
}
