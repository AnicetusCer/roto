package org.roto.ui

import android.app.Application
import android.content.Context
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import org.roto.data.MenuPreferencesDataSource
import org.roto.data.MenuRepository
import org.roto.data.MenuSelection
import org.roto.data.RotoData
import org.roto.domain.DayResult
import org.roto.domain.getMenuForDate

data class SetupMessage(val text: String, val isError: Boolean)

data class MenuUiState(
    val isLoading: Boolean = false,
    val setupMessage: SetupMessage? = null,
    val hasMenuData: Boolean = false,
    val rotaName: String = "",
    val todayMenu: DayResult? = null,
    val tomorrowMenu: DayResult? = null,
    val selectedSourceLabel: String = "No rota selected",
    val coverageStatus: CoverageStatus? = null,
    val weekMenus: List<WeekMenu> = emptyList(),
    val selectedWeekMenu: WeekMenu? = null,
    val globalNotes: List<String> = emptyList()
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
    val endDate: LocalDate,
    val days: List<WeekMenuDay>
)

data class WeekMenuDay(
    val date: LocalDate,
    val menu: DayResult?
)

class MenuViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = MenuRepository(application)
    private val preferences = MenuPreferencesDataSource(application)
    private val aiInstructionsText: String =
        application.assets.open("ai_llm_instructions.txt").bufferedReader().use { it.readText() }

    private val _uiState = MutableStateFlow(MenuUiState())
    val uiState: StateFlow<MenuUiState> = _uiState

    private var currentSelection: MenuSelection? = null
    private var currentRotoData: RotoData? = null
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

    fun getAiInstructions(): String = aiInstructionsText

    fun copySampleToDownloads(sampleName: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val targetName = sampleName.substringAfterLast('/')
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val resolver = app.contentResolver
                        // Remove any previous copy with the same display name inside our relative path.
                        resolver.delete(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                            arrayOf(targetName, Environment.DIRECTORY_DOWNLOADS + "/Roto/")
                        )
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, targetName)
                            put(MediaStore.Downloads.MIME_TYPE, "application/json")
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Roto")
                        }
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                            ?: throw IllegalStateException("Unable to create entry in Downloads.")
                        app.assets.open("sample_rotas/$sampleName").use { input ->
                            resolver.openOutputStream(uri)?.use { output ->
                                input.copyTo(output)
                            } ?: throw IllegalStateException("Unable to open Downloads output stream.")
                        }
                        "Downloads/Roto/$targetName"
                    } else {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            ?: throw IllegalStateException("Public Downloads directory unavailable.")
                        val rotoDir = File(downloadsDir, "Roto").apply { if (!exists()) mkdirs() }
                        val target = File(rotoDir, targetName)
                        app.assets.open("sample_rotas/$sampleName").use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        MediaScannerConnection.scanFile(app, arrayOf(target.absolutePath), arrayOf("application/json"), null)
                        target.absolutePath
                    }
                }
            }
            result.onSuccess { location ->
                _uiState.update { state ->
                    state.copy(setupMessage = SetupMessage("Copied sample to $location. Tap 'Load rota file' to open it.", false))
                }
                Toast.makeText(app, "Sample copied to $location", Toast.LENGTH_SHORT).show()
            }.onFailure { throwable ->
                val reason = throwable.localizedMessage ?: "Unknown error"
                _uiState.update { state ->
                    state.copy(setupMessage = SetupMessage("Couldn't copy sample: $reason", true))
                }
                Toast.makeText(app, "Couldn't copy sample: $reason", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearMenuSelection() {
        viewModelScope.launch {
            preferences.clearMenuSelection()
            currentSelection = null
            currentRotoData = null
            selectedWeekId = null
            _uiState.emit(MenuUiState())
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
                setupMessage = if (isManualRefresh) null else _uiState.value.setupMessage
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
                                selectionUri = null,
                                selectionLabel = null,
                                today = today,
                                tomorrow = tomorrow,
                                messageOverride = primaryError.message
                                    ?: "Couldn't read the selected file. Showing the app downloads rota instead."
                            )
                        }
                        .onFailure { fallbackError ->
                            emitLoadError(
                                message = fallbackError.message
                                    ?: primaryError.message
                                    ?: "Failed to load rota data.",
                                selectionLabel = preferredLabel ?: "Custom rota"
                            )
                        }
                } else {
                    emitLoadError(
                        message = primaryError.message
                            ?: "Pick the latest rota file. If you need one, copy the helper prompt and ask your favourite assistant to build it.",
                        selectionLabel = "No rota selected"
                    )
                }
            }
    }

    private suspend fun emitLoadError(
        message: String,
        selectionLabel: String
    ) {
        currentRotoData = null
        selectedWeekId = null
        _uiState.emit(
            MenuUiState(
                isLoading = false,
                setupMessage = SetupMessage(message, true),
                hasMenuData = false,
                selectedSourceLabel = selectionLabel
            )
        )
    }

    private suspend fun updateStateWithMenu(
        menuData: RotoData,
        selectionUri: Uri?,
        selectionLabel: String?,
        today: LocalDate,
        tomorrow: LocalDate,
        messageOverride: String?
    ) {
        currentRotoData = menuData

        val todayMenu = getMenuForDate(menuData, today)
        val tomorrowMenu = getMenuForDate(menuData, tomorrow)

        val weekMenus = buildWeekMenus(menuData)
        val coverageStatus = computeCoverageStatus(menuData, weekMenus, today, tomorrow)

        val activeWeek = weekMenus.find { it.id == selectedWeekId }
        if (activeWeek == null) {
            selectedWeekId = null
        }

        val label = selectionLabel ?: selectionUri?.lastPathSegment ?: "Chosen rota"

        val message = messageOverride?.let { SetupMessage(it, true) }

        _uiState.emit(
            MenuUiState(
                isLoading = false,
                setupMessage = message,
                hasMenuData = true,
                rotaName = menuData.rotaName,
                todayMenu = todayMenu,
                tomorrowMenu = tomorrowMenu,
                selectedSourceLabel = label,
                coverageStatus = coverageStatus,
                weekMenus = weekMenus,
                selectedWeekMenu = activeWeek,
                globalNotes = menuData.notes
            )
        )
    }

    private fun buildWeekMenus(menuData: RotoData): List<WeekMenu> {
        val mondays = mutableSetOf<LocalDate>()
        menuData.cycle.weeks.forEach { week ->
            week.weekCommencing.mapNotNull(::parseIsoDateOrNull).forEach(mondays::add)
        }

        menuData.cycle.repeat?.let { repeat ->
            val todayMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            for (offset in -1..5) {
                mondays.add(todayMonday.plusWeeks(offset.toLong()))
            }
            parseIsoDateOrNull(repeat.startDate)?.let { mondays.add(it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) }
        }

        return mondays
            .sorted()
            .mapNotNull { monday ->
                val baseResult = getMenuForDate(menuData, monday) ?: return@mapNotNull null
                val days = (0..6).map { delta ->
                    val date = monday.plusDays(delta.toLong())
                    WeekMenuDay(
                        date = date,
                        menu = getMenuForDate(menuData, date)
                    )
                }
                if (days.all { it.menu == null }) return@mapNotNull null

                val weekId = baseResult.weekId ?: "Week"
                WeekMenu(
                    id = "${weekId}_$monday",
                    title = buildWeekTitle(baseResult, monday),
                    startDate = monday,
                    endDate = monday.plusDays(6),
                    days = days
                )
            }
    }

    private fun computeCoverageStatus(
        menuData: RotoData,
        weekMenus: List<WeekMenu>,
        today: LocalDate,
        tomorrow: LocalDate
    ): CoverageStatus? {
        if (menuData.cycle.repeat != null) return null
        if (weekMenus.isEmpty()) return null
        val earliest = weekMenus.first().startDate
        val latest = weekMenus.maxOf { it.endDate }

        return when {
            today.isBefore(earliest) -> CoverageStatus(
                type = CoverageType.FUTURE,
                message = "New rota begins on $earliest. Browse upcoming weeks below."
            )
            tomorrow.isAfter(latest) -> CoverageStatus(
                type = CoverageType.PAST,
                message = "This rota is old. Rotas ran until $latest. They can still be browsed below."
            )
            else -> null
        }
    }

    private fun buildWeekTitle(result: DayResult, monday: LocalDate): String =
        result.weekId?.let { "$it · WC $monday" } ?: "WC $monday"

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
