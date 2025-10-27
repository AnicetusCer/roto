package org.schooldinners.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schooldinners.data.MenuData
import org.schooldinners.data.MenuPreferencesDataSource
import org.schooldinners.data.MenuRepository
import org.schooldinners.data.MenuSelection
import org.schooldinners.data.MenuSourceType
import org.schooldinners.domain.DayMenuResult
import org.schooldinners.domain.getMenuForDate

data class MenuUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasMenuData: Boolean = false,
    val todayMenu: DayMenuResult? = null,
    val tomorrowMenu: DayMenuResult? = null,
    val selectedSourceLabel: String = "No menu selected",
    val usingCustomSelection: Boolean = false,
    val coverageStatus: CoverageStatus? = null,
    val weekMenus: List<WeekMenu> = emptyList(),
    val selectedWeekMenu: WeekMenu? = null
)

data class CoverageStatus(
    val type: CoverageType,
    val message: String
)

enum class CoverageType { FUTURE, PAST }

data class WeekMenu(
    val id: String,
    val title: String,
    val startDate: LocalDate,
    val days: List<WeekMenuDay>
)

data class WeekMenuDay(
    val dayOfWeek: DayOfWeek,
    val date: LocalDate,
    val menu: DayMenuResult?
)

class MenuViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = MenuRepository(application)
    private val preferences = MenuPreferencesDataSource(application)

    private val _uiState = MutableStateFlow(MenuUiState())
    val uiState: StateFlow<MenuUiState> = _uiState

    private var currentSelection: MenuSelection? = null
    private var currentMenuData: MenuData? = null
    private var selectedWeekId: String? = null

    init {
        viewModelScope.launch {
            preferences.menuSelectionFlow.collectLatest { selection ->
                currentSelection = selection
                selectedWeekId = null
                performLoad(selection)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            performLoad(currentSelection, isManualRefresh = true)
        }
    }

    fun onExternalFileChosen(context: Context, uri: Uri) {
        viewModelScope.launch {
            val label = resolveDisplayName(context, uri)
            preferences.saveMenuSelection(uri.toString(), label)
        }
    }

    fun clearMenuSelection() {
        viewModelScope.launch {
            preferences.clearMenuSelection()
        }
    }

    fun selectWeek(weekId: String) {
        selectedWeekId = weekId
        val week = _uiState.value.weekMenus.find { it.id == weekId }
        _uiState.update { state ->
            state.copy(selectedWeekMenu = week)
        }
    }

    fun clearSelectedWeek() {
        selectedWeekId = null
        _uiState.update { state ->
            state.copy(selectedWeekMenu = null)
        }
    }

    private suspend fun performLoad(
        selection: MenuSelection?,
        isManualRefresh: Boolean = false
    ) {
        _uiState.emit(
            _uiState.value.copy(
                isLoading = true,
                error = if (isManualRefresh) null else _uiState.value.error
            )
        )

        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val preferredUri = selection?.uriString?.let(Uri::parse)
        val preferredLabel = selection?.displayName

        repository.loadMenu(preferredUri)
            .onSuccess { result ->
                updateStateWithMenu(
                    menuData = result.data,
                    sourceType = result.sourceType,
                    selectionUri = preferredUri,
                    selectionLabel = preferredLabel,
                    today = today,
                    tomorrow = tomorrow,
                    messageOverride = null
                )
            }
            .onFailure { primaryError ->
                if (preferredUri != null) {
                    repository.loadMenu(null)
                        .onSuccess { fallback ->
                            updateStateWithMenu(
                                menuData = fallback.data,
                                sourceType = fallback.sourceType,
                                selectionUri = null,
                                selectionLabel = null,
                                today = today,
                                tomorrow = tomorrow,
                                messageOverride = primaryError.message
                                    ?: "Couldn't read the selected file. Showing the app downloads menu instead."
                            )
                        }
                        .onFailure { fallbackError ->
                            emitLoadError(
                                message = fallbackError.message
                                    ?: primaryError.message
                                    ?: "Failed to load menu data.",
                                selectionLabel = preferredLabel ?: "Custom menu",
                                customSelection = true
                            )
                        }
                } else {
                    emitLoadError(
                        message = primaryError.message
                            ?: "No menu JSON found. Save it as ${MenuRepository.DEFAULT_DOWNLOADS_FILE_NAME} or choose a file.",
                        selectionLabel = "No menu selected",
                        customSelection = false
                    )
                }
            }
    }

    private suspend fun emitLoadError(
        message: String,
        selectionLabel: String,
        customSelection: Boolean
    ) {
        currentMenuData = null
        selectedWeekId = null
        _uiState.emit(
            MenuUiState(
                isLoading = false,
                error = message,
                hasMenuData = false,
                selectedSourceLabel = selectionLabel,
                usingCustomSelection = customSelection
            )
        )
    }

    private suspend fun updateStateWithMenu(
        menuData: MenuData,
        sourceType: MenuSourceType,
        selectionUri: Uri?,
        selectionLabel: String?,
        today: LocalDate,
        tomorrow: LocalDate,
        messageOverride: String?
    ) {
        currentMenuData = menuData

        val todayMenu = getMenuForDate(menuData, today)
        val tomorrowMenu = getMenuForDate(menuData, tomorrow)

        val weekMenus = buildWeekMenus(menuData)
        val coverageStatus = computeCoverageStatus(weekMenus, today, tomorrow)

        val activeWeek = weekMenus.find { it.id == selectedWeekId }
        if (activeWeek == null) {
            selectedWeekId = null
        }

        val label = when (sourceType) {
            MenuSourceType.EXTERNAL_SELECTION -> selectionLabel ?: selectionUri?.lastPathSegment ?: "Custom menu"
            MenuSourceType.SCOPED_DOWNLOADS -> "Downloads (app folder)"
        }

        val errorMessage = messageOverride

        _uiState.emit(
            MenuUiState(
                isLoading = false,
                error = errorMessage,
                hasMenuData = true,
                todayMenu = todayMenu,
                tomorrowMenu = tomorrowMenu,
                selectedSourceLabel = label,
                usingCustomSelection = sourceType == MenuSourceType.EXTERNAL_SELECTION,
                coverageStatus = coverageStatus,
                weekMenus = weekMenus,
                selectedWeekMenu = activeWeek
            )
        )
    }

    private fun buildWeekMenus(menuData: MenuData): List<WeekMenu> {
        val weeks = mutableListOf<WeekMenu>()
        menuData.cycle.weeks.forEach { week ->
            week.weekCommencing.mapNotNull(::parseIsoDateOrNull).forEach { monday ->
                val days = (0..4).map { offset ->
                    val date = monday.plusDays(offset.toLong())
                    val dayOfWeek = DayOfWeek.MONDAY.plus(offset.toLong())
                    WeekMenuDay(
                        dayOfWeek = dayOfWeek,
                        date = date,
                        menu = getMenuForDate(menuData, date)
                    )
                }
                val id = "${week.weekId}_${monday}"
                val title = "${week.weekId} · WC $monday"
                weeks += WeekMenu(
                    id = id,
                    title = title,
                    startDate = monday,
                    days = days
                )
            }
        }
        return weeks.sortedBy { it.startDate }
    }

    private fun computeCoverageStatus(
        weekMenus: List<WeekMenu>,
        today: LocalDate,
        tomorrow: LocalDate
    ): CoverageStatus? {
        if (weekMenus.isEmpty()) return null
        val earliest = weekMenus.first().startDate
        val latest = weekMenus.last().startDate.plusDays(4)

        return when {
            today.isBefore(earliest) -> CoverageStatus(
                type = CoverageType.FUTURE,
                message = "Menus begin on $earliest. Browse upcoming weeks below."
            )
            tomorrow.isAfter(latest) -> CoverageStatus(
                type = CoverageType.PAST,
                message = "Menus run up to $latest. Browse earlier weeks below."
            )
            else -> null
        }
    }

    private fun parseIsoDateOrNull(value: String): LocalDate? =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }

    private fun resolveDisplayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
}
