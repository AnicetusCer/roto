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
import org.schooldinners.data.MenuLoadResult
import org.schooldinners.data.MenuPreferencesDataSource
import org.schooldinners.data.MenuRepository
import org.schooldinners.data.MenuSelection
import org.schooldinners.data.MenuSourceType
import org.schooldinners.domain.DayMenuResult
import org.schooldinners.domain.getMenuForDate

data class MenuUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val todayMenu: DayMenuResult? = null,
    val tomorrowMenu: DayMenuResult? = null,
    val selectedSourceLabel: String = "Bundled sample",
    val coverageMessage: String? = null,
    val usingCustomSelection: Boolean = false,
    val previewOptions: List<PreviewOption> = emptyList(),
    val activePreview: PreviewOption? = null
)

data class PreviewOption(
    val id: String,
    val label: String,
    val menuResult: DayMenuResult
)

class MenuViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = MenuRepository(application)
    private val preferences = MenuPreferencesDataSource(application)

    private val _uiState = MutableStateFlow(MenuUiState())
    val uiState: StateFlow<MenuUiState> = _uiState

    private var currentSelection: MenuSelection? = null
    private var previewSelectionId: String? = null

    init {
        viewModelScope.launch {
            preferences.menuSelectionFlow.collectLatest { selection ->
                currentSelection = selection
                previewSelectionId = null
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

    fun selectPreview(optionId: String) {
        viewModelScope.launch {
            previewSelectionId = optionId
            val option = _uiState.value.previewOptions.find { it.id == optionId }
            _uiState.update { state ->
                state.copy(activePreview = option)
            }
        }
    }

    fun clearPreviewSelection() {
        viewModelScope.launch {
            previewSelectionId = null
            _uiState.update { state ->
                state.copy(activePreview = null)
            }
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
                    result = result,
                    selectionUri = preferredUri,
                    selectionLabel = preferredLabel,
                    today = today,
                    tomorrow = tomorrow,
                    warningOverride = null
                )
            }
            .onFailure { primaryError ->
                if (preferredUri != null) {
                    repository.loadMenu(null)
                        .onSuccess { fallback ->
                            updateStateWithMenu(
                            result = fallback,
                            selectionUri = null,
                            selectionLabel = null,
                            today = today,
                            tomorrow = tomorrow,
                            warningOverride = primaryError.message
                                ?: "Couldn't read the selected file. Showing the bundled menu instead."
                        )
                    }
                    .onFailure { fallbackError ->
                        _uiState.emit(
                            MenuUiState(
                                    isLoading = false,
                                    error = fallbackError.message
                                        ?: primaryError.message
                                        ?: "Failed to load menu data."
                                )
                            )
                        }
                } else {
                    _uiState.emit(
                        MenuUiState(
                            isLoading = false,
                            error = primaryError.message ?: "Failed to load menu data."
                        )
                    )
                }
            }
    }

    private suspend fun updateStateWithMenu(
        result: MenuLoadResult,
        selectionUri: Uri?,
        selectionLabel: String?,
        today: LocalDate,
        tomorrow: LocalDate,
        warningOverride: String?
    ) {
        val menuData = result.data
        val todayMenu = getMenuForDate(menuData, today)
        val tomorrowMenu = getMenuForDate(menuData, tomorrow)

        val previewOptions = buildPreviewOptions(menuData, today)
        val activePreview = previewOptions.find { it.id == previewSelectionId }
        if (activePreview == null) {
            previewSelectionId = null
        }

        val errorMessage = when {
            warningOverride != null -> warningOverride
            todayMenu == null && tomorrowMenu == null ->
                "No menu found for today or tomorrow. When your school releases the next rota, use the AI Instructions to refresh SchoolNomNomsMenu.json."
            else -> null
        }

        val coverageMessage = computeCoverageMessage(menuData, today, tomorrow)

        val label = when (result.sourceType) {
            MenuSourceType.EXTERNAL_SELECTION ->
                selectionLabel ?: selectionUri?.lastPathSegment ?: "Custom menu"
            MenuSourceType.SCOPED_DOWNLOADS ->
                "Downloads (app folder)"
            MenuSourceType.BUNDLED_SAMPLE ->
                "Bundled sample"
        }

        _uiState.emit(
            MenuUiState(
                isLoading = false,
                error = errorMessage,
                todayMenu = todayMenu,
                tomorrowMenu = tomorrowMenu,
                selectedSourceLabel = label,
                coverageMessage = coverageMessage,
                usingCustomSelection = result.sourceType == MenuSourceType.EXTERNAL_SELECTION,
                previewOptions = previewOptions,
                activePreview = activePreview
            )
        )
    }

    private fun computeCoverageMessage(
        menuData: MenuData,
        today: LocalDate,
        tomorrow: LocalDate
    ): String? {
        val allMondays = menuData.cycle.weeks
            .flatMap { week -> week.weekCommencing.mapNotNull(::parseIsoDateOrNull) }
        val earliestMonday = allMondays.minOrNull()
        val latestMonday = allMondays.maxOrNull()
        val latestFriday = latestMonday?.plusDays(4)

        return when {
            earliestMonday != null && today.isBefore(earliestMonday) ->
                "This rota starts on $earliestMonday. You're looking before the published weeks."
            latestFriday != null && tomorrow.isAfter(latestFriday) ->
                "This rota only covers up to $latestFriday. Check if a new menu is available."
            else -> null
        }
    }

    private fun buildPreviewOptions(menuData: MenuData, today: LocalDate): List<PreviewOption> {
        val mondays = menuData.cycle.weeks
            .flatMap { week -> week.weekCommencing.mapNotNull(::parseIsoDateOrNull) }
            .sorted()
        if (mondays.isEmpty()) return emptyList()

        val pastMonday = mondays.filter { !it.isAfter(today) }.maxOrNull()
        val futureMonday = mondays.filter { !it.isBefore(today) }.minOrNull()

        val options = mutableListOf<PreviewOption>()
        pastMonday?.let { monday ->
            getMenuForDate(menuData, monday)?.let { result ->
                options += PreviewOption(
                    id = "past_${monday}",
                    label = "View week starting $monday",
                    menuResult = result
                )
            }
        }
        futureMonday?.takeIf { it != pastMonday }?.let { monday ->
            getMenuForDate(menuData, monday)?.let { result ->
                options += PreviewOption(
                    id = "future_${monday}",
                    label = "Preview upcoming week starting $monday",
                    menuResult = result
                )
            }
        }
        return options
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
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        }.getOrNull()
}
