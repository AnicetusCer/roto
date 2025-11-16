package org.roto.ui

import android.app.Application
import android.content.Context
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
import org.roto.data.MenuSelectionType
import org.roto.data.RemoteMenuFetcher
import org.roto.data.RemoteSourceStatus
import org.roto.data.RotoData
import org.roto.domain.DayResult
import org.roto.domain.getMenuForDate
import org.roto.widget.RotoTodayWidget

data class SetupMessage(val text: String, val isError: Boolean)

data class SampleCopyPrompt(
    val locationHint: String,
    val pendingSelection: MenuSelection?
)

private data class SampleCopyOutcome(
    val locationHint: String,
    val selection: MenuSelection?
)

private data class MirrorFileResult(
    val uriString: String,
    val displayName: String,
    val locationHint: String
)

data class MenuUiState(
    val isLoading: Boolean = false,
    val setupMessage: SetupMessage? = null,
    val sampleCopyPrompt: SampleCopyPrompt? = null,
    val hasMenuData: Boolean = false,
    val rotaName: String = "",
    val todayMenu: DayResult? = null,
    val tomorrowMenu: DayResult? = null,
    val selectedSourceLabel: String = "No rota selected",
    val selectedSourceType: MenuSelectionType? = null,
    val remoteStatus: RemoteStatusUi? = null,
    val remoteUrl: String? = null,
    val coverageStatus: CoverageStatus? = null,
    val weekMenus: List<WeekMenu> = emptyList(),
    val selectedWeekMenu: WeekMenu? = null,
    val globalNotes: List<String> = emptyList()
)

data class CoverageStatus(
    val type: CoverageType,
    val message: String
)

data class RemoteStatusUi(
    val url: String,
    val lastSyncedEpochMillis: Long,
    val isUsingCache: Boolean
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

    private fun writeJsonToDownloads(app: Application, targetName: String, contents: String): MirrorFileResult {
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/Roto/"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = app.contentResolver
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(targetName, relativePath)
            )
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, targetName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Unable to create shared rota file in Downloads.")
            resolver.openOutputStream(uri, "wt")?.use { output ->
                output.writer().use { writer ->
                    writer.write(contents)
                }
            } ?: throw IllegalStateException("Unable to open Downloads output stream.")
            MirrorFileResult(
                uriString = uri.toString(),
                displayName = targetName,
                locationHint = "Downloads/Roto/$targetName"
            )
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IllegalStateException("Public Downloads directory unavailable.")
            val rotoDir = File(downloadsDir, "Roto").apply { if (!exists()) mkdirs() }
            val target = File(rotoDir, targetName)
            target.writeText(contents)
            MediaScannerConnection.scanFile(app, arrayOf(target.absolutePath), arrayOf("application/json"), null)
            MirrorFileResult(
                uriString = Uri.fromFile(target).toString(),
                displayName = targetName,
                locationHint = "Downloads/Roto/$targetName"
            )
        }
    }

    fun applySampleSelection(selection: MenuSelection) {
        viewModelScope.launch {
            persistSelection(selection)
            refreshWidgets()
        }
    }

    private fun copySample(app: Application, sampleName: String): SampleCopyOutcome {
        val targetName = sampleName.substringAfterLast('/')
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/Roto/"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = app.contentResolver
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(targetName, relativePath)
            )
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, targetName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Unable to create entry in Downloads.")
            app.assets.open("sample_rotas/$sampleName").use { input ->
                resolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                } ?: throw IllegalStateException("Unable to open Downloads output stream.")
            }
            SampleCopyOutcome(
                locationHint = "Downloads/Roto/$targetName",
                selection = MenuSelection(
                    type = MenuSelectionType.LOCAL_FILE,
                    reference = uri.toString(),
                    displayName = targetName
                )
            )
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IllegalStateException("Public Downloads directory unavailable.")
            val rotoDir = File(downloadsDir, "Roto").apply { if (!exists()) mkdirs() }
            val target = File(rotoDir, targetName)
            app.assets.open("sample_rotas/$sampleName").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            MediaScannerConnection.scanFile(app, arrayOf(target.absolutePath), arrayOf("application/json"), null)
            SampleCopyOutcome(
                locationHint = "Downloads/Roto/$targetName",
                selection = MenuSelection(
                    type = MenuSelectionType.LOCAL_FILE,
                    reference = Uri.fromFile(target).toString(),
                    displayName = targetName
                )
            )
        }
    }

    private suspend fun refreshWidgets() {
        runCatching {
            RotoTodayWidget.refreshAll(getApplication())
        }
    }

    private suspend fun persistSelection(selection: MenuSelection) {
        when (selection.type) {
            MenuSelectionType.LOCAL_FILE -> preferences.saveLocalSelection(selection.reference, selection.displayName)
            MenuSelectionType.REMOTE_LINK -> {
                val remoteUrl = selection.remoteUrl ?: return
                preferences.saveRemoteSelection(selection.reference, selection.displayName, remoteUrl)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            performLoad(currentSelection, isManualRefresh = true)
        }
    }

    fun onAppForeground() {
        val selection = currentSelection
        if (selection?.type == MenuSelectionType.REMOTE_LINK) {
            refresh()
        }
    }

    fun onExternalFileChosen(context: Context, uri: Uri) {
        viewModelScope.launch {
            val label = resolveDisplayName(context, uri)
            preferences.saveLocalSelection(uri.toString(), label)
            refreshWidgets()
        }
    }

    fun submitSharedLink(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(setupMessage = SetupMessage("Enter a shared link to continue.", true)) }
            return
        }
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || scheme !in setOf("https", "http")) {
            _uiState.update { it.copy(setupMessage = SetupMessage("Shared links must start with https://", true)) }
            return
        }
        viewModelScope.launch {
            val app = getApplication<Application>()
            val fetcher = RemoteMenuFetcher(app)
            _uiState.update { it.copy(setupMessage = SetupMessage("Downloading shared link…", false)) }
            val fetchOutcome = withContext(Dispatchers.IO) {
                runCatching { fetcher.fetch(trimmed, forceNetwork = true) }
            }
            fetchOutcome.onSuccess { remote ->
                val remoteUrl = remote.status.url
                val fileName = deriveRemoteFileName(remoteUrl)
                val mirror = withContext(Dispatchers.IO) {
                    writeJsonToDownloads(app, fileName, remote.rawJson)
                }
                preferences.saveRemoteSelection(mirror.uriString, mirror.displayName, remoteUrl)
                _uiState.update {
                    it.copy(
                        setupMessage = SetupMessage("Saved shared rota to ${mirror.locationHint}.", false)
                    )
                }
                refreshWidgets()
            }.onFailure { error ->
                val reason = error.message ?: "Unknown error"
                _uiState.update {
                    it.copy(
                        setupMessage = SetupMessage("Couldn't download shared rota: $reason", true)
                    )
                }
            }
        }
    }

    fun getAiInstructions(): String = aiInstructionsText

    fun copySampleToDownloads(sampleName: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    copySample(app, sampleName)
                }
            }
            result.onSuccess { outcome ->
                _uiState.update { state ->
                    state.copy(
                        setupMessage = SetupMessage("Copied sample to ${outcome.locationHint}. Load it now or pick it later.", false),
                        sampleCopyPrompt = SampleCopyPrompt(
                            locationHint = outcome.locationHint,
                            pendingSelection = outcome.selection
                        )
                    )
                }
                Toast.makeText(app, "Sample copied to ${outcome.locationHint}", Toast.LENGTH_SHORT).show()
            }.onFailure { throwable ->
                val reason = throwable.localizedMessage ?: "Unknown error"
                _uiState.update { state ->
                    state.copy(
                        setupMessage = SetupMessage("Couldn't copy sample: $reason", true),
                        sampleCopyPrompt = null
                    )
                }
                Toast.makeText(app, "Couldn't copy sample: $reason", Toast.LENGTH_SHORT).show()
            }
        }
    }


    fun dismissSampleCopyPrompt() {
        _uiState.update { state ->
            if (state.sampleCopyPrompt == null) state else state.copy(sampleCopyPrompt = null)
        }
    }

    fun clearMenuSelection() {
        viewModelScope.launch {
            preferences.clearMenuSelection()
            currentSelection = null
            selectedWeekId = null
            _uiState.emit(MenuUiState())
            refreshWidgets()
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
        val preferredLabel = selection?.displayName

        val primaryResult = withContext(Dispatchers.IO) {
            repository.loadMenu(
                selection = selection,
                forceRemoteRefresh = selection?.type == MenuSelectionType.REMOTE_LINK
            )
        }
        primaryResult.onSuccess { result ->
            updateStateWithMenu(
                menuData = result.data,
                selection = selection,
                selectionLabel = preferredLabel,
                today = today,
                tomorrow = tomorrow,
                messageOverride = null,
                remoteStatus = result.remoteStatus
            )
        }.onFailure { primaryError ->
            val remoteMessage = buildFallbackRemoteMessage(primaryError.message)
            if (selection != null && selection.type == MenuSelectionType.REMOTE_LINK) {
                val cachedResult = withContext(Dispatchers.IO) {
                    repository.loadMenu(
                        selection = selection,
                        allowDownloadsFallback = false,
                        forceRemoteRefresh = false
                    ).getOrNull()
                }
                cachedResult?.let { cached ->
                    updateStateWithMenu(
                        menuData = cached.data,
                        selection = selection,
                        selectionLabel = preferredLabel,
                        today = today,
                        tomorrow = tomorrow,
                        messageOverride = remoteMessage,
                        remoteStatus = cached.remoteStatus?.copy(isFromCache = true)
                    )
                } ?: emitLoadError(
                    message = remoteMessage,
                    selectionLabel = preferredLabel ?: "Shared link",
                    selectionType = selection.type,
                    remoteUrl = selection.remoteUrl
                )
            } else if (selection != null) {
                val fallbackResult = withContext(Dispatchers.IO) {
                    repository.loadMenu(null)
                }
                fallbackResult.onSuccess { fallback ->
                    updateStateWithMenu(
                        menuData = fallback.data,
                        selection = selection,
                        selectionLabel = preferredLabel,
                        today = today,
                        tomorrow = tomorrow,
                        messageOverride = primaryError.message
                            ?: "Couldn't load the selected rota. Showing the app downloads copy instead.",
                        remoteStatus = null
                    )
                }.onFailure { fallbackError ->
                    emitLoadError(
                        message = fallbackError.message
                            ?: primaryError.message
                            ?: "Failed to load rota data.",
                        selectionLabel = preferredLabel ?: "Custom rota",
                        selectionType = selection.type,
                        remoteUrl = selection.remoteUrl
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
        selectionLabel: String,
        selectionType: MenuSelectionType? = null,
        remoteUrl: String? = null
    ) {
        selectedWeekId = null
        _uiState.emit(
            MenuUiState(
                isLoading = false,
                setupMessage = SetupMessage(message, true),
                hasMenuData = false,
                selectedSourceLabel = selectionLabel,
                selectedSourceType = selectionType,
                remoteStatus = null,
                remoteUrl = remoteUrl
            )
        )
        refreshWidgets()
    }

    private suspend fun updateStateWithMenu(
        menuData: RotoData,
        selection: MenuSelection?,
        selectionLabel: String?,
        today: LocalDate,
        tomorrow: LocalDate,
        messageOverride: String?,
        remoteStatus: RemoteSourceStatus?
    ) {
        val todayMenu = getMenuForDate(menuData, today)
        val tomorrowMenu = getMenuForDate(menuData, tomorrow)

        val weekMenus = buildWeekMenus(menuData)
        val coverageStatus = computeCoverageStatus(menuData, weekMenus, today, tomorrow)

        val activeWeek = weekMenus.find { it.id == selectedWeekId }
        if (activeWeek == null) {
            selectedWeekId = null
        }

        val label = selectionLabel ?: buildDefaultLabel(selection)

        val message = messageOverride?.let { SetupMessage(it, true) }
            ?: selection?.takeIf { it.type == MenuSelectionType.REMOTE_LINK }?.let {
                fallbackMessageForRemote(remoteStatus)
            }

        val remoteUi = remoteStatus?.let {
            RemoteStatusUi(
                url = it.url,
                lastSyncedEpochMillis = it.lastSyncedEpochMillis,
                isUsingCache = it.isFromCache
            )
        }
        val remoteUrl = selection?.remoteUrl

        _uiState.emit(
            MenuUiState(
                isLoading = false,
                setupMessage = message,
                hasMenuData = true,
                rotaName = menuData.rotaName,
                todayMenu = todayMenu,
                tomorrowMenu = tomorrowMenu,
                selectedSourceLabel = label,
                selectedSourceType = selection?.type,
                remoteStatus = remoteUi,
                remoteUrl = remoteUrl,
                coverageStatus = coverageStatus,
                weekMenus = weekMenus,
                selectedWeekMenu = activeWeek,
                globalNotes = menuData.notes
            )
        )
        refreshWidgets()
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
                val dayResults = (0..6).map { delta ->
                    val date = monday.plusDays(delta.toLong())
                    WeekMenuDay(
                        date = date,
                        menu = getMenuForDate(menuData, date)
                    )
                }
                if (dayResults.all { it.menu == null }) return@mapNotNull null

                val anchorResult = dayResults.firstNotNullOfOrNull { it.menu } ?: return@mapNotNull null
                val weekId = anchorResult.weekId ?: "Week"
                WeekMenu(
                    id = "${weekId}_$monday",
                    title = buildWeekTitle(anchorResult, monday),
                    startDate = monday,
                    endDate = monday.plusDays(6),
                    days = dayResults
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

    private fun deriveRemoteLabel(uri: Uri): String {
        val candidate = uri.lastPathSegment?.takeIf { it.isNotBlank() }
            ?: uri.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: uri.host
        return candidate ?: "Shared link"
    }

    private fun deriveRemoteFileName(url: String): String {
        val sanitized = url.substringAfterLast('/').substringBefore('?').ifBlank { "RemoteRota.json" }
        return if (sanitized.endsWith(".json")) sanitized else "$sanitized.json"
    }

    private fun buildDefaultLabel(selection: MenuSelection?): String =
        when (selection?.type) {
            MenuSelectionType.LOCAL_FILE -> runCatching { Uri.parse(selection.reference).lastPathSegment }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: selection?.displayName
                ?: "Chosen rota"
            MenuSelectionType.REMOTE_LINK -> selection.displayName
                ?: runCatching { Uri.parse(selection.reference).host }.getOrNull()
                    ?.let { "$it link" }
                ?: "Shared link"
            null -> "Chosen rota"
        }
}

private fun fallbackMessageForRemote(status: RemoteSourceStatus?): SetupMessage? {
    if (status == null || !status.isFromCache) return null
    val formatted = formatTimestamp(status.lastSyncedEpochMillis)
    return SetupMessage(
        text = "Shared link unreachable. Showing the last downloaded copy from $formatted.",
        isError = true
    )
}

private fun formatTimestamp(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")
    return formatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}

private fun buildFallbackRemoteMessage(sourceMessage: String?): String =
    sourceMessage?.takeIf { it.isNotBlank() }
        ?: "Shared link unreachable. Showing the last downloaded copy."
