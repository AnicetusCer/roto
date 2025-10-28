package org.roto.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.TextStyle
import java.util.Locale
import org.roto.R
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
    val introText = "Add your rota so Roto can show what’s happening each day. If you already have the file, load it below. Otherwise, use the helper prompt to create one from an existing schedule, for example a photo, pdf, spreadsheet etc."
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
    var showSamples by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val sampleFiles = remember(context) {
        context.assets.list("sample_rotas")?.sorted()?.toList() ?: emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_roto),
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )
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
            text = "Don’t have a file yet? Copy the helper prompt, paste it into your favourite AI assistant, and share your existing schedule file or a clear photo AI can read. It will hand you back a little rota file you can save.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Button(onClick = onCopyInstructions) {
            Text("Copy helper prompt")
        }
        Text(
            text = "When the assistant replies, save the file (Downloads is ideal) and tap “Load rota file” above. You can replace or clear it whenever plans change.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = { showFormatDetails = !showFormatDetails }) {
            Text(if (showFormatDetails) "Hide JSON Schema" else "Show JSON Schema")
        }
        if (showFormatDetails) {
            FormatInfoCard()
        }
        if (sampleFiles.isNotEmpty()) {
            TextButton(onClick = { showSamples = !showSamples }) {
                Text(if (showSamples) "Hide sample rota names" else "Show sample rota names")
            }
            if (showSamples) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Need an example? Try one of these files bundled with the app:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        sampleFiles.forEach { name ->
                            Text("• $name", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
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
        Image(
            painter = painterResource(id = R.drawable.logo_roto),
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .align(Alignment.CenterHorizontally)
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
            Button(onClick = onChooseFile) { Text("Load rota file") }
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
    val exampleJson = """
        {
          "schema_version": "0.3",
          "school_name": "Your Rota Name",
          "notes": ["Optional reminders"],
          "cycle": {
            "repeat": {
              "start_date": "2025-09-01",
              "start_week_id": "Week 1"
            },
            "weeks": [
              {
                "week_id": "Week 1",
                "week_commencing": ["2025-09-01"],
                "days": {
                  "monday": {
                    "slots": [
                      { "label": "Option 1", "text": "Activity or meal name" },
                      { "label": "Option 2", "text": "Alternative option", "tags": ["optional tag"] }
                    ],
                    "notes": ["Optional day-specific note"]
                  }
                }
              }
            ]
          },
          "overrides": {
            "2025-10-20": {
              "closed": true,
              "reason": "No rota today (holiday)"
            }
          }
        }
    """.trimIndent()

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
            Text("What the rota file looks like", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            SelectionContainer {
                Text(
                    text = exampleJson,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "Save the file exactly as shown (tech folks call it JSON) and load it with the button above.",
                style = MaterialTheme.typography.bodySmall
            )
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
