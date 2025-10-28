package org.roto.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.TextStyle
import java.util.Locale
import org.roto.domain.DayDataSource
import org.roto.domain.DayResult
import org.roto.domain.SlotEntry

@Composable
fun MenuRoot(
    viewModel: MenuViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // not fatal; we'll rely on transient access
            }
            viewModel.onExternalFileChosen(context, uri)
        }
    }

    MenuScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onChooseFile = { openDocumentLauncher.launch(arrayOf("application/json", "text/plain")) },
        onCopyInstructions = {
            clipboard.setText(AnnotatedString(viewModel.getAiInstructions()))
        },
        onClearMenu = viewModel::clearMenuSelection,
        onSelectWeek = viewModel::selectWeek,
        onClearWeek = viewModel::clearSelectedWeek,
        modifier = modifier
    )
}

@Composable
fun MenuScreen(
    state: MenuUiState,
    onRefresh: () -> Unit,
    onChooseFile: () -> Unit,
    onCopyInstructions: () -> Unit,
    onClearMenu: () -> Unit,
    onSelectWeek: (String) -> Unit,
    onClearWeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    val introText = "Roto needs your rota data. Paste JSON if you already have it, or tap 'Copy AI Instructions' to build it from a PDF or photo. Roto never uploads anything."
    val hasSelection = state.selectedSourceLabel != "No rota selected"

    when {
        state.isLoading -> LoadingState(modifier)
        state.error != null -> SetupState(
            instructionsIntro = introText,
            onChooseFile = onChooseFile,
            onCopyInstructions = onCopyInstructions,
            showClear = hasSelection,
            onClearMenu = onClearMenu,
            sourceLabel = state.selectedSourceLabel,
            extraMessage = state.error,
            modifier = modifier
        )
        state.hasMenuData -> MenuContent(
            state = state,
            onRefresh = onRefresh,
            onChooseFile = onChooseFile,
            onClearMenu = onClearMenu,
            onSelectWeek = onSelectWeek,
            onClearWeek = onClearWeek,
            modifier = modifier
        )
        else -> SetupState(
            instructionsIntro = introText,
            onChooseFile = onChooseFile,
            onCopyInstructions = onCopyInstructions,
            showClear = hasSelection,
            onClearMenu = onClearMenu,
            sourceLabel = state.selectedSourceLabel,
            extraMessage = null,
            modifier = modifier
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Loading rota…")
    }
}

@Composable
private fun SetupState(
    instructionsIntro: String,
    onChooseFile: () -> Unit,
    onCopyInstructions: () -> Unit,
    showClear: Boolean,
    onClearMenu: () -> Unit,
    sourceLabel: String,
    extraMessage: String?,
    modifier: Modifier = Modifier
) {
    var showFormatDetails by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SourceControls(
            selectedSourceLabel = sourceLabel,
            onChooseFile = onChooseFile,
            showClear = showClear,
            onClearMenu = onClearMenu
        )
        Text(
            text = instructionsIntro,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Copy the AI prompt below, paste it into your preferred assistant, and share your rota (PDF, photo, or text). The AI turns it into JSON for Roto.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Button(onClick = onCopyInstructions) {
            Text("Copy AI Instructions")
        }
        Text(
            text = "When the AI replies with JSON, save it to your phone (Downloads is ideal) and load it with the button above. Repeat whenever the rota changes.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = { showFormatDetails = !showFormatDetails }) {
            Text(if (showFormatDetails) "Hide JSON Schema" else "Show JSON Schema")
        }
        if (showFormatDetails) {
            FormatInfoCard()
        }
        extraMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MenuContent(
    state: MenuUiState,
    onRefresh: () -> Unit,
    onChooseFile: () -> Unit,
    onClearMenu: () -> Unit,
    onSelectWeek: (String) -> Unit,
    onClearWeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appTitle = state.rotaName.ifBlank { "Roto" }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = appTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        SourceControls(
            selectedSourceLabel = state.selectedSourceLabel,
            onChooseFile = onChooseFile,
            showClear = state.selectedSourceLabel != "No rota selected",
            onClearMenu = onClearMenu
        )

        state.coverageStatus?.let {
            InfoCard(it.message)
        }

        if (state.globalNotes.isNotEmpty()) {
            NotesColumn(title = "Notes", notes = state.globalNotes)
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Tomorrow's Rota",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            state.tomorrowMenu?.let {
                MenuCard(title = friendlyDate(it), menu = it)
            } ?: Text("No rota recorded for tomorrow.")

            Text(
                text = "Today's Rota",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            state.todayMenu?.let {
                MenuCard(title = friendlyDate(it), menu = it)
            } ?: Text("No rota recorded for today.")
        }

        if (state.weekMenus.isNotEmpty()) {
            BrowseWeeksSection(
                weekMenus = state.weekMenus,
                selectedWeekMenu = state.selectedWeekMenu,
                onSelectWeek = onSelectWeek,
                onClearWeek = onClearWeek
            )
        }

        Button(onClick = onRefresh) {
            Text("Refresh rota")
        }
    }
}

@Composable
private fun SourceControls(
    selectedSourceLabel: String,
    onChooseFile: () -> Unit,
    showClear: Boolean,
    onClearMenu: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Rota source: $selectedSourceLabel",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onChooseFile) { Text("Load rota (JSON)") }
            if (showClear) {
                TextButton(onClick = onClearMenu) { Text("Clear rota") }
            }
        }
    }
}

@Composable
private fun InfoCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = Icons.Filled.Info, contentDescription = null)
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NotesColumn(title: String, notes: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            notes.forEach { note ->
                Text("• $note", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FormatInfoCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("JSON structure overview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("• schema_version: \"0.3\"", style = MaterialTheme.typography.bodySmall)
            Text("• school_name: label shown on the home screen (e.g. your school name)", style = MaterialTheme.typography.bodySmall)
            Text("• notes: optional list of global messages", style = MaterialTheme.typography.bodySmall)
            Text("• cycle.weeks[]: repeating week patterns", style = MaterialTheme.typography.bodySmall)
            Text("  – week_id: label such as \"Week 1\" or \"Red Week\"", style = MaterialTheme.typography.bodySmall)
            Text("  – week_commencing: Monday dates (YYYY-MM-DD) when this pattern starts", style = MaterialTheme.typography.bodySmall)
            Text("  – days: keys like monday…sunday (or ISO dates) with a day definition", style = MaterialTheme.typography.bodySmall)
            Text("    • day.slots[] → { label, text, tags[] } listed top to bottom", style = MaterialTheme.typography.bodySmall)
            Text("    • day.notes[] → optional extra lines for that day", style = MaterialTheme.typography.bodySmall)
            Text("• overrides{\"2025-03-01\"}: optional single-day changes with closed/reason/slots/notes", style = MaterialTheme.typography.bodySmall)
            Text("Save the JSON exactly as returned and load it via the button above.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    menu: DayResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            DayDetails(menu = menu, showWeekMetadata = true)
        }
    }
}

@Composable
private fun DayDetails(
    menu: DayResult,
    showWeekMetadata: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (menu.isClosed) {
            Text(
                text = "Closed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
            menu.closedReason?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            menu.closedReason?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            if (menu.slots.isNotEmpty()) {
                menu.slots.forEach { slot -> SlotRow(slot) }
            } else {
                Text("No rota slots recorded.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (menu.notes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Notes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                menu.notes.forEach { note ->
                    Text("• $note", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (showWeekMetadata) {
            val metadata = buildList {
                menu.weekId?.let { add("Pattern: $it") }
                menu.weekCommencing?.let { add("WC $it") }
                add("Source: ${formatSource(menu.source)}")
            }
            if (metadata.isNotEmpty()) {
                Text(
                    text = metadata.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SlotRow(slot: SlotEntry, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = slot.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(text = slot.text, style = MaterialTheme.typography.bodyMedium)
        if (slot.tags.isNotEmpty()) {
            Text(
                text = "Tags: ${slot.tags.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

private fun formatSource(source: DayDataSource): String =
    when (source) {
        DayDataSource.ROTATION -> "Rotation"
        DayDataSource.OVERRIDE -> "Override"
    }

@Composable
private fun BrowseWeeksSection(
    weekMenus: List<WeekMenu>,
    selectedWeekMenu: WeekMenu?,
    onSelectWeek: (String) -> Unit,
    onClearWeek: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Browse rota weeks",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        weekMenus.forEach { week ->
            Button(onClick = { onSelectWeek(week.id) }) {
                Text(week.title)
            }
        }
        selectedWeekMenu?.let { week ->
            WeekMenuCard(week)
            TextButton(onClick = onClearWeek) { Text("Hide week view") }
        }
    }
}

@Composable
private fun WeekMenuCard(weekMenu: WeekMenu) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = weekMenu.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            weekMenu.days.forEachIndexed { index, day ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val headerText = day.menu?.formattedDate ?: run {
                        val dayName = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                        "$dayName · ${day.date}"
                    }
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    day.menu?.let {
                        DayDetails(menu = it, showWeekMetadata = false)
                    } ?: Text("No rota recorded for this day.", style = MaterialTheme.typography.bodyMedium)
                }
                if (index != weekMenu.days.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun friendlyDate(result: DayResult): String = result.formattedDate
