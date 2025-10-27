package org.schooldinners.ui

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
import androidx.compose.material.icons.filled.Error
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
import org.schooldinners.domain.DayMenuResult
import org.schooldinners.data.MenuRepository

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
    val introText = "You need to provide this app with your school's menu in a json formated file."

    when {
        state.isLoading -> LoadingState(modifier)
        state.error != null -> SetupState(
            instructionsIntro = introText,
            onChooseFile = onChooseFile,
            onCopyInstructions = onCopyInstructions,
            showClear = state.selectedSourceLabel != "No menu selected",
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
            showClear = state.selectedSourceLabel != "No menu selected",
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
        Text("Loading menu…")
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
            text = "Don't worry! we're going to get AI to make the file for you.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Simpily copy the AI prompt below, paste it into your favourite AI provider, your AI should then ask you to provide your current school menu (in any format it can read; PDF, photo, text etc). The AI will read it and produce a JSON formatted version of your menu that this app can then read.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Button(onClick = onCopyInstructions) {
            Text("Copy AI Instructions")
        }
        Text(
            text = "When the AI gives you the JSON formated file, save it to your phones download folder, open the app and then select it as the current menu. Repeat these steps when a new menu is released.",
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "School Nom Noms",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        SourceControls(
            selectedSourceLabel = state.selectedSourceLabel,
            onChooseFile = onChooseFile,
            showClear = true,
            onClearMenu = onClearMenu
        )

        state.coverageStatus?.let {
            InfoCard(it.message)
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Tomorrow",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            state.tomorrowMenu?.let {
                MenuCard(title = friendlyDate(it), menu = it)
            } ?: Text("No menu recorded for tomorrow.")

            Text(
                text = "Today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            state.todayMenu?.let {
                MenuCard(title = friendlyDate(it), menu = it)
            } ?: Text("No menu recorded for today.")
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
            Text("Refresh")
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
            text = "Menu source: $selectedSourceLabel",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onChooseFile) { Text("Upload menu (JSON)") }
            if (showClear) {
                TextButton(onClick = onClearMenu) { Text("Clear menu") }
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
            Text("• schema_version: \"0.2\"", style = MaterialTheme.typography.bodySmall)
            Text("• school_name: your school name", style = MaterialTheme.typography.bodySmall)
            Text("• notes: optional list of text lines", style = MaterialTheme.typography.bodySmall)
            Text("• cycle.weeks: list of week objects", style = MaterialTheme.typography.bodySmall)
            Text("  – week_id: label such as Week 1", style = MaterialTheme.typography.bodySmall)
            Text("  – week_commencing: list of Monday dates (YYYY-MM-DD)", style = MaterialTheme.typography.bodySmall)
            Text("  – days: entries for monday…friday with main/alt_hot/deli_option/dessert", style = MaterialTheme.typography.bodySmall)
            Text("Save the file exactly as the AI returns it (with braces and quotes) and upload it using the button above.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    menu: DayMenuResult,
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
            MealRow(label = "Main", value = menu.menu.main)
            MealRow(label = "Alt / Veg", value = menu.menu.altHot)
            MealRow(label = "Deli", value = menu.menu.deliOption)
            MealRow(label = "Dessert", value = menu.menu.dessert)
            if (menu.notes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Notes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    menu.notes.forEach { note ->
                        Text("• $note", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(
                text = "Week ${menu.weekId} · WC ${menu.weekCommencing}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MealRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
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
            text = "Browse weeks",
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
            weekMenu.days.forEach { day ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val dayName = day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    Text(
                        text = "$dayName · ${day.date}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    day.menu?.let { menu ->
                        MealRow(label = "Main", value = menu.menu.main)
                        MealRow(label = "Alt / Veg", value = menu.menu.altHot)
                        MealRow(label = "Deli", value = menu.menu.deliOption)
                        MealRow(label = "Dessert", value = menu.menu.dessert)
                    } ?: Text("No menu recorded for this day.")
                }
            }
        }
    }
}

private fun friendlyDate(result: DayMenuResult): String {
    val dayName = result.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$dayName ${result.date}"
}
